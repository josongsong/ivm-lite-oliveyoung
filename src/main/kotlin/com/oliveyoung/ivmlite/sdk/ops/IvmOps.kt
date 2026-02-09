package com.oliveyoung.ivmlite.sdk.ops

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.Result as SharedResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * IvmOps - Admin Operations DSL
 *
 * Admin 앱에서 사용하는 운영 작업들을 제공합니다.
 * **중요**: SDK는 외부 클라이언트용이고, Admin은 내부 앱이므로
 * orchestration 레이어를 직접 호출합니다.
 *
 * 사용 예시:
 * ```kotlin
 * // Outbox 처리
 * Ivm.ops.processOutbox(limit = 10)
 *
 * // 특정 엔티티 재처리
 * Ivm.ops.reslice("PRODUCT:SKU-001")
 *
 * // Worker 제어
 * Ivm.ops.worker.start()
 * ```
 */
object IvmOps {
    private val logger = LoggerFactory.getLogger(IvmOps::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // 의존성 (Admin 초기화 시 주입)
    internal var slicingWorkflow: SlicingWorkflow? = null
    internal var shipWorkflow: ShipWorkflow? = null
    internal var outboxRepository: OutboxRepositoryPort? = null
    internal var outboxPollingWorker: OutboxPollingWorker? = null
    internal var sliceRepository: SliceRepositoryPort? = null

    /**
     * IvmContext에서 Ops 의존성 초기화 (권장)
     *
     * @example
     * ```kotlin
     * IvmOps.initialize(context)
     * ```
     */
    fun initialize(context: com.oliveyoung.ivmlite.sdk.IvmContext) {
        this.slicingWorkflow = context.slicingWorkflow
        this.shipWorkflow = context.shipWorkflow
        this.outboxRepository = context.outboxRepository
        this.outboxPollingWorker = context.worker
        this.sliceRepository = context.sliceRepository
    }

    val worker: WorkerControl get() = WorkerControl(outboxPollingWorker)

    /**
     * Outbox 처리 (동기 실행)
     *
     * Admin에서 수동으로 Outbox 항목을 처리합니다.
     * Orchestration 레이어를 직접 호출하여 동기 처리합니다.
     *
     * @param limit 처리할 최대 항목 수
     * @param filter 필터 옵션
     */
    suspend fun processOutbox(
        limit: Int = 100,
        @Suppress("UNUSED_PARAMETER") filter: ProcessFilter.() -> Unit = {}
    ): ProcessResult {
        // TODO: filterConfig 필터링 로직 구현 시 활성화
        // val filterConfig = ProcessFilter().apply(filter)
        val repo = outboxRepository
            ?: return ProcessResult.failure("OutboxRepository not configured")

        val workflow = slicingWorkflow
            ?: return ProcessResult.failure("SlicingWorkflow not configured")

        val shipWf = shipWorkflow
            ?: return ProcessResult.failure("ShipWorkflow not configured")

        // PENDING 항목 조회
        val pendingEntries = when (val result = repo.findPending(limit)) {
            is SharedResult.Ok -> result.value
            is SharedResult.Err -> {
                return ProcessResult.failure("Failed to find pending entries: ${result.error}")
            }
        }

        if (pendingEntries.isEmpty()) {
            return ProcessResult.empty()
        }

        var processed = 0
        var failed = 0
        val details = mutableListOf<ProcessDetail>()

        // 각 항목 처리 (이미 PENDING 상태이므로 바로 처리)
        pendingEntries.forEach { entry ->
            val entryResult = processEntrySafe(entry, workflow, shipWf)
            when (entryResult) {
                is ProcessEntryResult.Success -> {
                    when (val markResult = repo.markProcessed(listOf(entry.id))) {
                        is SharedResult.Ok -> {
                            processed++
                            details.add(ProcessDetail(
                                entryId = entry.id.toString(),
                                eventType = entry.eventType,
                                success = true
                            ))
                        }
                        is SharedResult.Err -> {
                            logger.error("Failed to mark entry ${entry.id} as processed: ${markResult.error}")
                            failed++
                        }
                    }
                }
                is ProcessEntryResult.Failure -> {
                    failed++
                    details.add(ProcessDetail(
                        entryId = entry.id.toString(),
                        eventType = entry.eventType,
                        success = false,
                        error = entryResult.error
                    ))
                }
            }
        }

        return ProcessResult.success(processed, failed, details)
    }

    /**
     * 개별 Outbox 항목 처리 (ID로)
     *
     * Admin UI에서 특정 항목을 직접 처리할 때 사용합니다.
     *
     * @param id Outbox 항목 ID (UUID)
     */
    suspend fun processOneById(id: java.util.UUID): ProcessResult {
        val repo = outboxRepository
            ?: return ProcessResult.failure("OutboxRepository not configured")

        val workflow = slicingWorkflow
            ?: return ProcessResult.failure("SlicingWorkflow not configured")

        val shipWf = shipWorkflow
            ?: return ProcessResult.failure("ShipWorkflow not configured")

        return processOneByIdInternal(id, repo, workflow, shipWf)
    }

    private suspend fun processOneByIdInternal(
        id: java.util.UUID,
        repo: OutboxRepositoryPort,
        workflow: SlicingWorkflow,
        shipWf: ShipWorkflow
    ): ProcessResult = SharedResult.catch {
        // 1. 해당 ID의 Outbox 항목 조회
        val entry = when (val result = repo.findById(id)) {
            is SharedResult.Ok -> result.value
            is SharedResult.Err -> {
                return@catch ProcessResult.failure("Entry not found: ${result.error}")
            }
        }

        // 2. PENDING 상태가 아니면 에러
        if (entry.status.name != "PENDING") {
            return@catch ProcessResult.failure("Entry is not PENDING: ${entry.status}")
        }

        // 3. 처리
        val processResult = processEntrySafe(entry, workflow, shipWf)
        when (processResult) {
            is ProcessEntryResult.Success -> {
                when (val markResult = repo.markProcessed(listOf(entry.id))) {
                    is SharedResult.Ok -> {
                        ProcessResult.success(1, 0, listOf(
                            ProcessDetail(
                                entryId = entry.id.toString(),
                                eventType = entry.eventType,
                                success = true
                            )
                        ))
                    }
                    is SharedResult.Err -> {
                        logger.error("Failed to mark entry ${entry.id} as processed: ${markResult.error}")
                        ProcessResult.failure("Failed to mark as processed: ${markResult.error}")
                    }
                }
            }
            is ProcessEntryResult.Failure -> {
                repo.markFailed(entry.id, processResult.error)
                ProcessResult.success(0, 1, listOf(
                    ProcessDetail(
                        entryId = entry.id.toString(),
                        eventType = entry.eventType,
                        success = false,
                        error = processResult.error
                    )
                ))
            }
        }
    }.fold(
        onErr = { error ->
            logger.error("Error in processOneById: ${error.message}")
            ProcessResult.failure("Unexpected error: ${error.message}")
        },
        onOk = { it }
    )

    /**
     * 특정 엔티티 재처리 (전체 파이프라인)
     */
    suspend fun reprocess(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): ReprocessResult {
        val workflow = slicingWorkflow
            ?: return ReprocessResult.failure(entityKey, "SlicingWorkflow not configured")

        // TODO: Ship 자동 연계 구현 시 활성화
        // val shipWf = shipWorkflow
        //     ?: return ReprocessResult.failure(entityKey, "ShipWorkflow not configured")

        val sliceRepo = sliceRepository
            ?: return ReprocessResult.failure(entityKey, "SliceRepository not configured")

        return reprocessInternal(tenantId, entityKey, version, workflow, sliceRepo)
    }

    private suspend fun reprocessInternal(
        tenantId: String,
        entityKey: String,
        version: Long?,
        workflow: SlicingWorkflow,
        sliceRepo: SliceRepositoryPort
    ): ReprocessResult = SharedResult.catch {
        val resolvedVersion = version ?: run {
            when (val result = sliceRepo.getLatestVersion(
                TenantId(tenantId),
                EntityKey(entityKey)
            )) {
                is SharedResult.Ok -> {
                    result.value.firstOrNull()?.version
                        ?: return@catch ReprocessResult.failure(entityKey, "No slices found for entity")
                }
                is SharedResult.Err -> {
                    return@catch ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                }
            }
        }

        val slicingResult = workflow.executeAuto(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            version = resolvedVersion
        )

        val stages = mutableListOf<StageResult>()

        when (slicingResult) {
            is SharedResult.Ok -> {
                stages.add(StageResult(
                    stage = "Slice",
                    success = true,
                    slicesCreated = slicingResult.value.size
                ))
            }
            is SharedResult.Err -> {
                return@catch ReprocessResult.failure(
                    entityKey = entityKey,
                    error = "Slicing failed: ${slicingResult.error}",
                    stages = listOf(StageResult(
                        stage = "Slice",
                        success = false,
                        error = slicingResult.error.toString()
                    ))
                )
            }
        }

        ReprocessResult.success(entityKey, stages)
    }.fold(
        onErr = { error ->
            logger.error("Error in reprocess: ${error.message}")
            ReprocessResult.failure(entityKey, "Unexpected error: ${error.message}")
        },
        onOk = { it }
    )

    /**
     * Slice만 재생성
     */
    suspend fun reslice(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): ReprocessResult {
        val workflow = slicingWorkflow
            ?: return ReprocessResult.failure(entityKey, "SlicingWorkflow not configured")

        val sliceRepo = sliceRepository
            ?: return ReprocessResult.failure(entityKey, "SliceRepository not configured")

        return resliceInternal(tenantId, entityKey, version, workflow, sliceRepo)
    }

    private suspend fun resliceInternal(
        tenantId: String,
        entityKey: String,
        version: Long?,
        workflow: SlicingWorkflow,
        sliceRepo: SliceRepositoryPort
    ): ReprocessResult = SharedResult.catch {
        val resolvedVersion = version ?: run {
            when (val result = sliceRepo.getLatestVersion(
                TenantId(tenantId),
                EntityKey(entityKey)
            )) {
                is SharedResult.Ok -> {
                    result.value.firstOrNull()?.version
                        ?: return@catch ReprocessResult.failure(entityKey, "No slices found for entity")
                }
                is SharedResult.Err -> {
                    return@catch ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                }
            }
        }

        val slicingResult = workflow.executeAuto(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            version = resolvedVersion
        )

        when (slicingResult) {
            is SharedResult.Ok -> {
                ReprocessResult.success(
                    entityKey = entityKey,
                    stages = listOf(StageResult(
                        stage = "Slice",
                        success = true,
                        slicesCreated = slicingResult.value.size
                    ))
                )
            }
            is SharedResult.Err -> {
                ReprocessResult.failure(
                    entityKey = entityKey,
                    error = "Slicing failed: ${slicingResult.error}",
                    stages = listOf(StageResult(
                        stage = "Slice",
                        success = false,
                        error = slicingResult.error.toString()
                    ))
                )
            }
        }
    }.fold(
        onErr = { error ->
            logger.error("Error in reslice: ${error.message}")
            ReprocessResult.failure(entityKey, "Unexpected error: ${error.message}")
        },
        onOk = { it }
    )

    /**
     * Ship만 재실행
     */
    suspend fun reship(
        tenantId: String,
        entityKey: String,
        version: Long? = null,
        sinkTypes: List<String>? = null
    ): ReprocessResult {
        val shipWf = shipWorkflow
            ?: return ReprocessResult.failure(entityKey, "ShipWorkflow not configured")

        val sliceRepo = sliceRepository
            ?: return ReprocessResult.failure(entityKey, "SliceRepository not configured")

        return reshipInternal(tenantId, entityKey, version, sinkTypes, shipWf, sliceRepo)
    }

    private suspend fun reshipInternal(
        tenantId: String,
        entityKey: String,
        version: Long?,
        sinkTypes: List<String>?,
        shipWf: ShipWorkflow,
        sliceRepo: SliceRepositoryPort
    ): ReprocessResult = SharedResult.catch {
        val resolvedVersion = version ?: run {
            when (val result = sliceRepo.getLatestVersion(
                TenantId(tenantId),
                EntityKey(entityKey)
            )) {
                is SharedResult.Ok -> {
                    result.value.firstOrNull()?.version
                        ?: return@catch ReprocessResult.failure(entityKey, "No slices found for entity")
                }
                is SharedResult.Err -> {
                    return@catch ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                }
            }
        }

        val sinks = sinkTypes ?: listOf("opensearch", "personalize")

        val shipResult = shipWf.executeToMultipleSinks(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            version = resolvedVersion,
            sinkTypes = sinks
        )

        when (shipResult) {
            is SharedResult.Ok -> {
                ReprocessResult.success(
                    entityKey = entityKey,
                    stages = listOf(StageResult(
                        stage = "Ship",
                        success = true,
                        sinksShipped = shipResult.value.successCount
                    ))
                )
            }
            is SharedResult.Err -> {
                ReprocessResult.failure(
                    entityKey = entityKey,
                    error = "Ship failed: ${shipResult.error}",
                    stages = listOf(StageResult(
                        stage = "Ship",
                        success = false,
                        error = shipResult.error.toString()
                    ))
                )
            }
        }
    }.fold(
        onErr = { error ->
            logger.error("Error in reship: ${error.message}")
            ReprocessResult.failure(entityKey, "Unexpected error: ${error.message}")
        },
        onOk = { it }
    )

    // ===== Private Helpers =====

    /**
     * 예외를 안전하게 처리하는 processEntry 래퍼
     * try-catch 대신 Result.catch 패턴 사용
     */
    private suspend fun processEntrySafe(
        entry: OutboxEntry,
        workflow: SlicingWorkflow,
        shipWf: ShipWorkflow
    ): ProcessEntryResult = SharedResult.catch {
        processEntry(entry, workflow, shipWf)
    }.fold(
        onErr = { error ->
            logger.error("Error processing entry ${entry.id}: ${error.message}")
            ProcessEntryResult.Failure(error.message ?: "Unknown error")
        },
        onOk = { it }
    )

    private suspend fun processEntry(
        entry: OutboxEntry,
        workflow: SlicingWorkflow,
        shipWf: ShipWorkflow
    ): ProcessEntryResult {
        return when (entry.aggregateType.name) {
            "RAW_DATA" -> {
                when (entry.eventType) {
                    "RawDataIngested", "CompileRequested" -> {
                        val payload = parseCompilePayload(entry.payload)
                        val result = workflow.executeAuto(
                            tenantId = TenantId(payload.tenantId),
                            entityKey = EntityKey(payload.entityKey),
                            version = payload.version
                        )
                        when (result) {
                            is SharedResult.Ok -> ProcessEntryResult.Success
                            is SharedResult.Err -> ProcessEntryResult.Failure(result.error.toString())
                        }
                    }
                    else -> ProcessEntryResult.Failure("Unknown event type: ${entry.eventType}")
                }
            }
            "SLICE" -> {
                when (entry.eventType) {
                    "ShipRequested" -> {
                        val payload = parseShipPayload(entry.payload)
                        val result = shipWf.execute(
                            tenantId = TenantId(payload.tenantId),
                            entityKey = EntityKey(payload.entityKey),
                            version = payload.version,
                            sinkType = payload.sink ?: "opensearch"
                        )
                        when (result) {
                            is SharedResult.Ok -> ProcessEntryResult.Success
                            is SharedResult.Err -> ProcessEntryResult.Failure(result.error.toString())
                        }
                    }
                    else -> ProcessEntryResult.Failure("Unknown event type: ${entry.eventType}")
                }
            }
            else -> ProcessEntryResult.Failure("Unknown aggregate type: ${entry.aggregateType}")
        }
    }

    private fun parseCompilePayload(payloadJson: String): CompilePayload {
        val jsonObj = json.parseToJsonElement(payloadJson).jsonObject
        return CompilePayload(
            tenantId = jsonObj["tenantId"]?.jsonPrimitive?.content ?: "",
            entityKey = jsonObj["entityKey"]?.jsonPrimitive?.content ?: "",
            version = jsonObj["version"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        )
    }

    private fun parseShipPayload(payloadJson: String): ShipPayload {
        val jsonObj = json.parseToJsonElement(payloadJson).jsonObject
        return ShipPayload(
            tenantId = jsonObj["tenantId"]?.jsonPrimitive?.content ?: "",
            entityKey = jsonObj["entityKey"]?.jsonPrimitive?.content ?: "",
            version = jsonObj["version"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            sink = jsonObj["sink"]?.jsonPrimitive?.content
        )
    }

    // ===== Data Classes =====

    private data class CompilePayload(
        val tenantId: String,
        val entityKey: String,
        val version: Long
    )

    private data class ShipPayload(
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val sink: String?
    )

    private sealed class ProcessEntryResult {
        object Success : ProcessEntryResult()
        data class Failure(val error: String) : ProcessEntryResult()
    }
}
