package com.oliveyoung.ivmlite.sdk.ops

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
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
        filter: ProcessFilter.() -> Unit = {}
    ): ProcessResult {
        val filterConfig = ProcessFilter().apply(filter)
        val repo = outboxRepository
            ?: return ProcessResult.failure("OutboxRepository not configured")

        val workflow = slicingWorkflow
            ?: return ProcessResult.failure("SlicingWorkflow not configured")

        val shipWf = shipWorkflow
            ?: return ProcessResult.failure("ShipWorkflow not configured")

        return try {
            // PENDING 항목 조회
            val pendingEntries = when (val result = repo.findPending(limit)) {
                is OutboxRepositoryPort.Result.Ok -> result.value
                is OutboxRepositoryPort.Result.Err -> {
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
                try {
                    // Note: claim()은 여러 개를 한 번에 claim하는 메서드이므로,
                    // 여기서는 이미 찾은 PENDING 항목을 바로 처리합니다.

                    // Process
                    val processResult = processEntry(entry, workflow, shipWf)
                    when (processResult) {
                        is ProcessEntryResult.Success -> {
                            // Mark as processed
                            when (val markResult = repo.markProcessed(listOf(entry.id))) {
                                is OutboxRepositoryPort.Result.Ok -> {
                                    processed++
                                    details.add(ProcessDetail(
                                        entryId = entry.id.toString(),
                                        eventType = entry.eventType,
                                        success = true
                                    ))
                                }
                                is OutboxRepositoryPort.Result.Err -> {
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
                                error = processResult.error
                            ))
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing entry ${entry.id}", e)
                    failed++
                    details.add(ProcessDetail(
                        entryId = entry.id.toString(),
                        eventType = entry.eventType,
                        success = false,
                        error = e.message ?: "Unknown error"
                    ))
                }
            }

            ProcessResult.success(processed, failed, details)
        } catch (e: Exception) {
            logger.error("Error in processOutbox", e)
            ProcessResult.failure("Unexpected error: ${e.message}")
        }
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

        return try {
            // 1. 해당 ID의 Outbox 항목 조회
            val entry = when (val result = repo.findById(id)) {
                is OutboxRepositoryPort.Result.Ok -> result.value
                is OutboxRepositoryPort.Result.Err -> {
                    return ProcessResult.failure("Entry not found: ${result.error}")
                }
            }

            // 2. PENDING 상태가 아니면 에러
            if (entry.status.name != "PENDING") {
                return ProcessResult.failure("Entry is not PENDING: ${entry.status}")
            }

            // 3. 처리
            val processResult = processEntry(entry, workflow, shipWf)
            when (processResult) {
                is ProcessEntryResult.Success -> {
                    // Mark as processed
                    when (val markResult = repo.markProcessed(listOf(entry.id))) {
                        is OutboxRepositoryPort.Result.Ok -> {
                            ProcessResult.success(1, 0, listOf(
                                ProcessDetail(
                                    entryId = entry.id.toString(),
                                    eventType = entry.eventType,
                                    success = true
                                )
                            ))
                        }
                        is OutboxRepositoryPort.Result.Err -> {
                            logger.error("Failed to mark entry ${entry.id} as processed: ${markResult.error}")
                            ProcessResult.failure("Failed to mark as processed: ${markResult.error}")
                        }
                    }
                }
                is ProcessEntryResult.Failure -> {
                    // Mark as failed
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
        } catch (e: Exception) {
            logger.error("Error in processOneById", e)
            ProcessResult.failure("Unexpected error: ${e.message}")
        }
    }

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

        val shipWf = shipWorkflow
            ?: return ReprocessResult.failure(entityKey, "ShipWorkflow not configured")

        val sliceRepo = sliceRepository
            ?: return ReprocessResult.failure(entityKey, "SliceRepository not configured")

        return try {
            val resolvedVersion = version ?: run {
                // 최신 버전 조회 (첫 번째 slice의 version 사용)
                when (val result = sliceRepo.getLatestVersion(
                    TenantId(tenantId),
                    EntityKey(entityKey)
                )) {
                    is SliceRepositoryPort.Result.Ok -> {
                        result.value.firstOrNull()?.version
                            ?: return ReprocessResult.failure(entityKey, "No slices found for entity")
                    }
                    is SliceRepositoryPort.Result.Err -> {
                        return ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                    }
                }
            }

            // 1. Slice 재생성
            val slicingResult = workflow.executeAuto(
                tenantId = TenantId(tenantId),
                entityKey = EntityKey(entityKey),
                version = resolvedVersion
            )

            val stages = mutableListOf<StageResult>()

            when (slicingResult) {
                is SlicingWorkflow.Result.Ok -> {
                    stages.add(StageResult(
                        stage = "Slice",
                        success = true,
                        slicesCreated = slicingResult.value.size
                    ))

                    // 2. Ship (모든 Sink로)
                    // TODO: SinkRule 기반으로 자동 Ship
                    // 현재는 수동으로 Ship 호출 필요
                }
                is SlicingWorkflow.Result.Err -> {
                    return ReprocessResult.failure(
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
        } catch (e: Exception) {
            logger.error("Error in reprocess", e)
            ReprocessResult.failure(entityKey, "Unexpected error: ${e.message}")
        }
    }

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

        return try {
            val resolvedVersion = version ?: run {
                when (val result = sliceRepo.getLatestVersion(
                    TenantId(tenantId),
                    EntityKey(entityKey)
                )) {
                    is SliceRepositoryPort.Result.Ok -> {
                        result.value.firstOrNull()?.version
                            ?: return ReprocessResult.failure(entityKey, "No slices found for entity")
                    }
                    is SliceRepositoryPort.Result.Err -> {
                        return ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                    }
                }
            }

            val slicingResult = workflow.executeAuto(
                tenantId = TenantId(tenantId),
                entityKey = EntityKey(entityKey),
                version = resolvedVersion
            )

            when (slicingResult) {
                is SlicingWorkflow.Result.Ok -> {
                    ReprocessResult.success(
                        entityKey = entityKey,
                        stages = listOf(StageResult(
                            stage = "Slice",
                            success = true,
                            slicesCreated = slicingResult.value.size
                        ))
                    )
                }
                is SlicingWorkflow.Result.Err -> {
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
        } catch (e: Exception) {
            logger.error("Error in reslice", e)
            ReprocessResult.failure(entityKey, "Unexpected error: ${e.message}")
        }
    }

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

        return try {
            val resolvedVersion = version ?: run {
                when (val result = sliceRepo.getLatestVersion(
                    TenantId(tenantId),
                    EntityKey(entityKey)
                )) {
                    is SliceRepositoryPort.Result.Ok -> {
                        result.value.firstOrNull()?.version
                            ?: return ReprocessResult.failure(entityKey, "No slices found for entity")
                    }
                    is SliceRepositoryPort.Result.Err -> {
                        return ReprocessResult.failure(entityKey, "Failed to get latest version: ${result.error}")
                    }
                }
            }

            val sinks = sinkTypes ?: listOf("opensearch", "personalize") // 기본값

            val shipResult = shipWf.executeToMultipleSinks(
                tenantId = TenantId(tenantId),
                entityKey = EntityKey(entityKey),
                version = resolvedVersion,
                sinkTypes = sinks
            )

            when (shipResult) {
                is ShipWorkflow.Result.Ok -> {
                    ReprocessResult.success(
                        entityKey = entityKey,
                        stages = listOf(StageResult(
                            stage = "Ship",
                            success = true,
                            sinksShipped = shipResult.value.successCount
                        ))
                    )
                }
                is ShipWorkflow.Result.Err -> {
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
        } catch (e: Exception) {
            logger.error("Error in reship", e)
            ReprocessResult.failure(entityKey, "Unexpected error: ${e.message}")
        }
    }

    // ===== Private Helpers =====

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
                            is SlicingWorkflow.Result.Ok -> ProcessEntryResult.Success
                            is SlicingWorkflow.Result.Err -> ProcessEntryResult.Failure(result.error.toString())
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
                            is ShipWorkflow.Result.Ok -> ProcessEntryResult.Success
                            is ShipWorkflow.Result.Err -> ProcessEntryResult.Failure(result.error.toString())
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
