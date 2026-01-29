package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxEventTypes
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

/**
 * Outbox Polling Worker (RFC-IMPL Phase B-2 + RFC-IMPL-013 자동 Ship)
 *
 * Transactional Outbox 패턴의 Consumer.
 * PostgreSQL Outbox 테이블을 Polling하여 SlicingWorkflow를 트리거한다.
 *
 * RFC-IMPL-013: Slicing 완료 후 SinkRule 기반으로 자동 ShipRequested 생성
 *
 * v1: Coroutine 기반 Polling
 * v2: Debezium CDC로 교체 (포트 동일, 어댑터만 교체)
 *
 * 특징:
 * - Exponential backoff with jitter (에러 발생 시)
 * - Graceful shutdown (SIGTERM 처리)
 * - Batch 처리 (throughput 최적화)
 * - 메트릭 수집
 * - 자동 Ship 트리거 (SinkRule 기반)
 */
class OutboxPollingWorker(
    private val outboxRepo: OutboxRepositoryPort,
    private val slicingWorkflow: SlicingWorkflow,
    private val config: WorkerConfig,
    private val eventHandler: EventHandler = DefaultEventHandler(),
    private val tracer: Tracer = io.opentelemetry.api.OpenTelemetry.noop().getTracer("outbox"),
    private val sinkRuleRegistry: SinkRuleRegistryPort? = null,  // RFC-IMPL-013: 자동 Ship
) {
    private val logger = LoggerFactory.getLogger(OutboxPollingWorker::class.java)
    
    // Worker 식별자 (디버깅/모니터링용)
    private val workerId: String = "worker-${UUID.randomUUID().toString().take(8)}"
    
    companion object {
        // PROCESSING 상태로 5분 이상 경과 시 stale로 간주
        const val STALE_TIMEOUT_SECONDS = 300L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private val running = AtomicBoolean(false)
    private val shutdownRequested = AtomicBoolean(false)

    // 메트릭
    private val processedCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)
    private val pollCount = AtomicLong(0)
    private var currentBackoffMs = 0L

    // Shutdown 신호 채널
    private val shutdownChannel = Channel<Unit>(1)

    /**
     * Worker 시작
     *
     * @return true if started, false if already running or disabled
     */
    fun start(): Boolean {
        if (!config.enabled) {
            logger.info("OutboxPollingWorker is disabled by config")
            return false
        }

        if (!running.compareAndSet(false, true)) {
            logger.warn("OutboxPollingWorker is already running")
            return false
        }

        logger.info(
            "Starting OutboxPollingWorker [batchSize={}, pollInterval={}ms, idleInterval={}ms]",
            config.batchSize,
            config.pollIntervalMs,
            config.idlePollIntervalMs,
        )

        pollingJob = scope.launch {
            pollLoop()
        }

        return true
    }

    /**
     * Worker 정지 (Graceful shutdown)
     *
     * @return true if stopped, false if not running
     */
    suspend fun stop(): Boolean {
        if (!running.get()) {
            logger.warn("OutboxPollingWorker is not running")
            return false
        }

        if (!shutdownRequested.compareAndSet(false, true)) {
            logger.warn("Shutdown already requested")
            return false
        }

        logger.info("Stopping OutboxPollingWorker (timeout={}ms)", config.shutdownTimeoutMs)

        // Shutdown 신호 전송
        shutdownChannel.send(Unit)

        // Timeout 내에 종료 대기
        val completed = withTimeoutOrNull(config.shutdownTimeoutMs) {
            pollingJob?.join()
            true
        } ?: false

        if (!completed) {
            logger.warn("Shutdown timeout exceeded, forcing cancellation")
            pollingJob?.cancel()
        }

        running.set(false)
        shutdownRequested.set(false)

        logger.info(
            "OutboxPollingWorker stopped [processed={}, failed={}, polls={}]",
            processedCount.get(),
            failedCount.get(),
            pollCount.get(),
        )

        return true
    }

    /**
     * Worker 실행 상태
     */
    fun isRunning(): Boolean = running.get()

    /**
     * 메트릭 스냅샷
     */
    fun getMetrics(): Metrics = Metrics(
        processed = processedCount.get(),
        failed = failedCount.get(),
        polls = pollCount.get(),
        currentBackoffMs = currentBackoffMs,
        isRunning = running.get(),
    )

    // ==================== Internal ====================

    private suspend fun pollLoop() {
        logger.debug("Poll loop started")

        while (scope.isActive && !shutdownRequested.get()) {
            try {
                val processed = pollAndProcess()
                pollCount.incrementAndGet()

                // Adaptive delay: 처리할 데이터가 있으면 짧게, 없으면 길게
                val delayMs = if (processed > 0) {
                    resetBackoff()
                    config.pollIntervalMs
                } else {
                    config.idlePollIntervalMs
                }

                delay(delayMs)
            } catch (e: CancellationException) {
                logger.debug("Poll loop cancelled")
                throw e
            } catch (e: Exception) {
                logger.error("Poll loop error", e)
                val backoffMs = calculateBackoff()
                logger.info("Backing off for {}ms", backoffMs)
                delay(backoffMs)
            }
        }

        logger.debug("Poll loop ended")
    }

    private suspend fun pollAndProcess(): Int {
        // 먼저 stale PROCESSING 복구 (5분 이상 처리 중인 것)
        recoverStaleEntries()
        
        // RFC-IMPL-013: 특정 AggregateType만 조회 (Worker 분리 지원)
        // topics 또는 aggregateTypes 설정에서 추론
        val aggregateType = config.resolvedAggregateTypes()?.firstOrNull()
        
        // claim: 원자적으로 PENDING → PROCESSING 전환 후 반환
        val claimed = when (val r = outboxRepo.claim(config.batchSize, aggregateType, workerId)) {
            is OutboxRepositoryPort.Result.Ok -> r.value
            is OutboxRepositoryPort.Result.Err -> {
                logger.error("Failed to claim entries: {}", r.error)
                throw ProcessingException("Failed to claim entries: ${r.error}")
            }
        }

        if (claimed.isEmpty()) {
            return 0
        }

        logger.debug("Claimed {} entries for processing", claimed.size)

        val processed = mutableListOf<UUID>()
        val failed = mutableListOf<Pair<UUID, String>>()

        for (entry in claimed) {
            if (shutdownRequested.get()) {
                logger.debug("Shutdown requested, stopping batch processing")
                break
            }

            try {
                processEntry(entry)
                processed.add(entry.id)
            } catch (e: Exception) {
                logger.error("Failed to process entry {}: {}", entry.id, e.message)
                failed.add(entry.id to (e.message ?: "Unknown error"))
            }
        }

        // 일괄 상태 업데이트 (PROCESSING → PROCESSED)
        if (processed.isNotEmpty()) {
            when (val r = outboxRepo.markProcessed(processed)) {
                is OutboxRepositoryPort.Result.Ok -> {
                    processedCount.addAndGet(r.value.toLong())
                    logger.debug("Marked {} entries as processed", r.value)
                }
                is OutboxRepositoryPort.Result.Err -> {
                    logger.error("Failed to mark processed: {}", r.error)
                    throw ProcessingException("Failed to mark processed entries: ${r.error}")
                }
            }
        }

        // PROCESSING → FAILED
        for ((id, reason) in failed) {
            when (val r = outboxRepo.markFailed(id, reason)) {
                is OutboxRepositoryPort.Result.Ok -> {
                    failedCount.incrementAndGet()
                }
                is OutboxRepositoryPort.Result.Err -> {
                    logger.error("Failed to mark failed: {}", id)
                    throw ProcessingException("Failed to mark entry as failed: id=$id, error=${r.error}")
                }
            }
        }

        return processed.size
    }
    
    /**
     * Stale PROCESSING 엔트리 복구
     * 
     * Worker가 죽거나 처리가 너무 오래 걸리는 경우 복구
     */
    private suspend fun recoverStaleEntries() {
        try {
            when (val r = outboxRepo.recoverStaleProcessing(STALE_TIMEOUT_SECONDS)) {
                is OutboxRepositoryPort.Result.Ok -> {
                    if (r.value > 0) {
                        logger.info("Recovered {} stale entries", r.value)
                    }
                }
                is OutboxRepositoryPort.Result.Err -> {
                    logger.warn("Failed to recover stale entries: {}", r.error)
                }
            }
        } catch (e: Exception) {
            logger.warn("Error during stale recovery: {}", e.message)
        }
    }

    private suspend fun processEntry(entry: OutboxEntry) {
        tracer.withSpanSuspend(
            "OutboxWorker.processEntry",
            mapOf(
                "entry_id" to entry.id.toString(),
                "aggregate_type" to entry.aggregateType.name,
                "event_type" to entry.eventType,
            ),
        ) {
            when (entry.aggregateType) {
                AggregateType.RAW_DATA -> processRawDataEvent(entry)
                AggregateType.SLICE -> eventHandler.handleSliceEvent(entry)
                AggregateType.CHANGESET -> eventHandler.handleChangeSetEvent(entry)
                else -> throw ProcessingException("Unknown aggregate type: ${entry.aggregateType} for entry ${entry.id}")
            }
        }
    }

    private suspend fun processRawDataEvent(entry: OutboxEntry) {
        when (entry.eventType) {
            OutboxEventTypes.RAW_DATA_INGESTED -> {
                val payload = parseRawDataIngestedPayload(entry.payload)

                // Payload 버전 로깅 (확장성 추적)
                logger.debug("Processing RawDataIngested: entity={}, payloadVersion={}", 
                    payload.entityKey, payload.payloadVersion)

                // RFC-IMPL-010 GAP-F: 자동으로 FULL/INCREMENTAL 선택
                // 이전 버전이 있으면 INCREMENTAL, 없으면 FULL로 실행
                val result = slicingWorkflow.executeAuto(
                    tenantId = TenantId(payload.tenantId),
                    entityKey = EntityKey(payload.entityKey),
                    version = payload.version,
                )

                when (result) {
                    is SlicingWorkflow.Result.Ok -> {
                        logger.debug(
                            "Slicing completed for {}:{} v{}, slices={}",
                            payload.tenantId,
                            payload.entityKey,
                            payload.version,
                            result.value.size,
                        )
                        
                        // RFC-IMPL-013: Slicing 완료 후 자동으로 ShipRequested outbox 생성
                        autoTriggerShip(
                            tenantId = payload.tenantId,
                            entityKey = payload.entityKey,
                            version = payload.version,
                            sliceKeys = result.value
                        )
                    }
                    is SlicingWorkflow.Result.Err -> {
                        throw ProcessingException("Slicing failed: ${result.error}")
                    }
                }
            }
            else -> {
                throw ProcessingException("Unknown RAW_DATA event type: ${entry.eventType} for entry ${entry.id}")
            }
        }
    }
    
    /**
     * RFC-IMPL-013: Slicing 완료 후 SinkRule 기반 자동 Ship 트리거
     *
     * 1. entityKey에서 entityType 추출
     * 2. SinkRule 조회 (entityType + sliceType 매칭)
     * 3. 매칭되는 각 SinkRule에 대해 ShipRequested outbox 생성
     */
    private suspend fun autoTriggerShip(
        tenantId: String,
        entityKey: String,
        version: Long,
        sliceKeys: List<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.SliceKey>
    ) {
        // SinkRuleRegistry가 없으면 자동 Ship 비활성화
        val registry = sinkRuleRegistry ?: run {
            logger.debug("SinkRuleRegistry not configured, skipping auto ship")
            return
        }
        
        // entityKey에서 entityType 추출 (예: "PRODUCT:sku123" → "PRODUCT")
        val entityType = extractEntityType(entityKey)
        if (entityType == null) {
            logger.warn("Cannot extract entityType from entityKey: {}", entityKey)
            return
        }
        
        // 생성된 각 sliceType에 대해 SinkRule 조회
        val processedSinks = mutableSetOf<String>()
        
        for (sliceKey in sliceKeys) {
            val rulesResult = registry.findByEntityAndSliceType(entityType, sliceKey.sliceType)
            
            when (rulesResult) {
                is SinkRuleRegistryPort.Result.Ok -> {
                    for (rule in rulesResult.value) {
                        // 중복 방지: 같은 sink로 이미 생성했으면 스킵
                        val sinkId = "${rule.target.type}:${rule.id}"
                        if (sinkId in processedSinks) continue
                        processedSinks.add(sinkId)
                        
                        // ShipRequested outbox 생성
                        createShipRequestedOutbox(
                            tenantId = tenantId,
                            entityKey = entityKey,
                            version = version,
                            sinkType = rule.target.type.name.lowercase(),
                            sinkRuleId = rule.id
                        )
                    }
                }
                is SinkRuleRegistryPort.Result.Err -> {
                    logger.warn("Failed to find SinkRules for {}:{}: {}", 
                        entityType, sliceKey.sliceType, rulesResult.error)
                }
            }
        }
        
        if (processedSinks.isNotEmpty()) {
            logger.info("Auto-triggered ship for {}:{} v{} to {} sinks: {}",
                tenantId, entityKey, version, processedSinks.size, processedSinks)
        }
    }
    
    /**
     * entityKey에서 entityType 추출
     * 형식: "ENTITY_TYPE:key" 또는 "prefix-key" (prefix에서 추론)
     */
    private fun extractEntityType(entityKey: String): String? {
        return when {
            entityKey.contains(":") -> entityKey.substringBefore(":")
            entityKey.startsWith("p-") || entityKey.startsWith("product-") -> "PRODUCT"
            entityKey.startsWith("b-") || entityKey.startsWith("brand-") -> "BRAND"
            entityKey.startsWith("c-") || entityKey.startsWith("category-") -> "CATEGORY"
            else -> null
        }
    }
    
    /**
     * ShipRequested outbox 엔트리 생성
     */
    private suspend fun createShipRequestedOutbox(
        tenantId: String,
        entityKey: String,
        version: Long,
        sinkType: String,
        sinkRuleId: String
    ) {
        val shipEntry = OutboxEntry.create(
            aggregateType = AggregateType.SLICE,
            aggregateId = "$tenantId:$entityKey",
            eventType = "ShipRequested",
            payload = buildJsonObject {
                put("payloadVersion", "1.0")
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", version.toString())
                put("sink", sinkType)
                put("sinkRuleId", sinkRuleId)
                put("shipMode", "auto")  // 자동 트리거 표시
            }.toString()
        )
        
        when (val result = outboxRepo.insert(shipEntry)) {
            is OutboxRepositoryPort.Result.Ok -> {
                logger.debug("Created ShipRequested outbox: entity={}, sink={}", entityKey, sinkType)
            }
            is OutboxRepositoryPort.Result.Err -> {
                logger.error("Failed to create ShipRequested outbox: {}", result.error)
                // 실패해도 슬라이싱은 완료되었으므로 에러를 던지지 않음
                // Reconciliation Worker가 나중에 복구할 수 있음
            }
        }
    }

    private fun parseRawDataIngestedPayload(json: String): RawDataIngestedPayload {
        return try {
            // ignoreUnknownKeys = true로 하위 호환성 보장 (payloadVersion 없는 기존 payload도 처리 가능)
            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<RawDataIngestedPayload>(json)
        } catch (e: Exception) {
            throw ProcessingException("Failed to parse payload: ${e.message}")
        }
    }

    private fun calculateBackoff(): Long {
        if (currentBackoffMs == 0L) {
            currentBackoffMs = config.pollIntervalMs
        } else {
            currentBackoffMs = min(
                (currentBackoffMs * config.backoffMultiplier).toLong(),
                config.maxBackoffMs,
            )
        }

        // Jitter 추가 (thundering herd 방지)
        val jitter = (currentBackoffMs * config.jitterFactor * Random.nextDouble()).toLong()
        return currentBackoffMs + jitter
    }

    private fun resetBackoff() {
        currentBackoffMs = 0L
    }

    // ==================== Data Classes ====================

    @Serializable
    data class RawDataIngestedPayload(
        val payloadVersion: String = "1.0",
        val tenantId: String,
        val entityKey: String,
        val version: Long,
    )

    data class Metrics(
        val processed: Long,
        val failed: Long,
        val polls: Long,
        val currentBackoffMs: Long,
        val isRunning: Boolean,
    )

    class ProcessingException(message: String) : RuntimeException(message)

    /**
     * 이벤트 핸들러 인터페이스 (확장 포인트)
     */
    interface EventHandler {
        suspend fun handleSliceEvent(entry: OutboxEntry)
        suspend fun handleChangeSetEvent(entry: OutboxEntry)
    }

    /**
     * 기본 이벤트 핸들러 (no-op, v1.1에서 구현)
     */
    class DefaultEventHandler : EventHandler {
        private val logger = LoggerFactory.getLogger(DefaultEventHandler::class.java)

        override suspend fun handleSliceEvent(entry: OutboxEntry) {
            logger.debug("SliceEvent received (no-op in v1): {}", entry.id)
        }

        override suspend fun handleChangeSetEvent(entry: OutboxEntry) {
            logger.debug("ChangeSetEvent received (no-op in v1): {}", entry.id)
        }
    }
}
