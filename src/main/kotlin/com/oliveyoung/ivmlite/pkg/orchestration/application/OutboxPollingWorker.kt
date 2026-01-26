package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxEventTypes
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
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
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

/**
 * Outbox Polling Worker (RFC-IMPL Phase B-2)
 *
 * Transactional Outbox 패턴의 Consumer.
 * PostgreSQL Outbox 테이블을 Polling하여 SlicingWorkflow를 트리거한다.
 *
 * v1: Coroutine 기반 Polling
 * v2: Debezium CDC로 교체 (포트 동일, 어댑터만 교체)
 *
 * 특징:
 * - Exponential backoff with jitter (에러 발생 시)
 * - Graceful shutdown (SIGTERM 처리)
 * - Batch 처리 (throughput 최적화)
 * - 메트릭 수집
 */
class OutboxPollingWorker(
    private val outboxRepo: OutboxRepositoryPort,
    private val slicingWorkflow: SlicingWorkflow,
    private val config: WorkerConfig,
    private val eventHandler: EventHandler = DefaultEventHandler(),
    private val tracer: Tracer = io.opentelemetry.api.OpenTelemetry.noop().getTracer("outbox"),
) {
    private val logger = LoggerFactory.getLogger(OutboxPollingWorker::class.java)

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
        val pending = when (val r = outboxRepo.findPending(config.batchSize)) {
            is OutboxRepositoryPort.Result.Ok -> r.value
            is OutboxRepositoryPort.Result.Err -> {
                logger.error("Failed to fetch pending entries: {}", r.error)
                throw ProcessingException("Failed to fetch pending entries: ${r.error}")
            }
        }

        if (pending.isEmpty()) {
            return 0
        }

        logger.debug("Processing {} pending entries", pending.size)

        val processed = mutableListOf<UUID>()
        val failed = mutableListOf<Pair<UUID, String>>()

        for (entry in pending) {
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

        // 일괄 상태 업데이트
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

    private fun parseRawDataIngestedPayload(json: String): RawDataIngestedPayload {
        return try {
            Json.decodeFromString<RawDataIngestedPayload>(json)
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
