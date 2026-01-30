package com.oliveyoung.ivmlite.pkg.webhooks.application

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatchResult
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDispatcherPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 웹훅 이벤트 핸들러
 *
 * 파이프라인에서 발생하는 이벤트를 수신하여 해당 웹훅에 전송한다.
 * 비동기 채널 기반으로 백프레셔를 지원한다.
 */
class WebhookEventHandler(
    private val webhookRepo: WebhookRepositoryPort,
    private val deliveryRepo: WebhookDeliveryRepositoryPort,
    private val dispatcher: WebhookDispatcherPort,
    private val config: EventHandlerConfig = EventHandlerConfig.DEFAULT
) {
    private val logger = LoggerFactory.getLogger(WebhookEventHandler::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 이벤트 채널 (백프레셔)
    private val eventChannel = Channel<DispatchJob>(config.channelCapacity)

    // 통계
    private val eventsReceived = AtomicLong(0)
    private val eventsDispatched = AtomicLong(0)
    private val eventsDropped = AtomicLong(0)

    @Volatile
    private var isRunning = false

    /**
     * 핸들러 시작
     */
    fun start() {
        if (isRunning) {
            logger.warn("WebhookEventHandler is already running")
            return
        }

        isRunning = true
        logger.info("Starting WebhookEventHandler with {} workers", config.workerCount)

        // 워커 풀 시작
        repeat(config.workerCount) { workerId ->
            scope.launch {
                processEvents(workerId)
            }
        }
    }

    /**
     * 핸들러 중지
     */
    fun stop() {
        isRunning = false
        eventChannel.close()
        logger.info("WebhookEventHandler stopped")
    }

    /**
     * 이벤트 발행 (비동기)
     *
     * 파이프라인의 각 단계에서 이 메서드를 호출하여 이벤트를 발행한다.
     */
    suspend fun emit(payload: WebhookEventPayload) {
        if (!isRunning) {
            logger.warn("WebhookEventHandler is not running, event dropped: {}", payload.eventType)
            eventsDropped.incrementAndGet()
            return
        }

        eventsReceived.incrementAndGet()

        // 해당 이벤트를 구독하는 웹훅 조회
        val webhooks = when (val result = webhookRepo.findByEvent(payload.eventType)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> {
                logger.error("Failed to find webhooks for event {}: {}", payload.eventType, result.error)
                return
            }
        }

        if (webhooks.isEmpty()) {
            return
        }

        // 필터 조건 매칭하여 전송 대상 결정
        val matchingWebhooks = webhooks.filter { webhook ->
            webhook.shouldTrigger(payload.eventType, payload.context)
        }

        // 각 웹훅에 대해 전송 작업 생성
        matchingWebhooks.forEach { webhook ->
            val job = DispatchJob(webhook, payload)

            // 채널에 전송 (용량 초과 시 대기)
            val sent = eventChannel.trySend(job)
            if (sent.isFailure) {
                logger.warn("Event channel full, dropping event for webhook {}", webhook.id)
                eventsDropped.incrementAndGet()
            }
        }
    }

    /**
     * 동기 이벤트 발행 (즉시 처리)
     */
    suspend fun emitSync(payload: WebhookEventPayload): List<DispatchJobResult> {
        eventsReceived.incrementAndGet()

        val webhooks = when (val result = webhookRepo.findByEvent(payload.eventType)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> {
                logger.error("Failed to find webhooks for event {}: {}", payload.eventType, result.error)
                return emptyList()
            }
        }

        val matchingWebhooks = webhooks.filter { webhook ->
            webhook.shouldTrigger(payload.eventType, payload.context)
        }

        return matchingWebhooks.map { webhook ->
            processJob(DispatchJob(webhook, payload))
        }
    }

    /**
     * 워커 이벤트 처리 루프
     */
    private suspend fun processEvents(workerId: Int) {
        logger.debug("Worker {} started", workerId)

        for (job in eventChannel) {
            try {
                processJob(job)
                eventsDispatched.incrementAndGet()
            } catch (e: Exception) {
                logger.error("Worker {} failed to process job: {}", workerId, e.message, e)
            }
        }

        logger.debug("Worker {} stopped", workerId)
    }

    /**
     * 개별 전송 작업 처리
     */
    private suspend fun processJob(job: DispatchJob): DispatchJobResult {
        val webhook = job.webhook
        val payload = job.payload

        // 전송 기록 생성
        var delivery = WebhookDelivery.create(
            webhookId = webhook.id,
            eventType = payload.eventType,
            eventPayload = payload.toMap()
        )

        // HTTP 전송
        val result = dispatcher.dispatch(webhook, payload)

        // 결과에 따라 전송 기록 업데이트
        delivery = when (result) {
            is DispatchResult.Success -> {
                delivery.markSuccess(
                    responseStatus = result.statusCode,
                    responseBody = result.responseBody,
                    responseHeaders = result.responseHeaders,
                    latencyMs = result.latencyMs
                )
            }
            is DispatchResult.Failure -> {
                if (result.isRetryable) {
                    delivery.scheduleRetry(
                        retryPolicy = webhook.retryPolicy,
                        errorMessage = result.errorMessage,
                        responseStatus = result.statusCode,
                        responseBody = result.responseBody,
                        latencyMs = result.latencyMs
                    )
                } else {
                    delivery.markFailed(
                        errorMessage = result.errorMessage,
                        responseStatus = result.statusCode,
                        responseBody = result.responseBody,
                        latencyMs = result.latencyMs
                    )
                }
            }
            is DispatchResult.Timeout -> {
                delivery.scheduleRetry(
                    retryPolicy = webhook.retryPolicy,
                    errorMessage = "Request timed out after ${result.timeoutMs}ms"
                )
            }
            is DispatchResult.CircuitOpen -> {
                delivery.markCircuitOpen()
            }
            is DispatchResult.RateLimited -> {
                delivery.markRateLimited()
            }
        }

        // 전송 기록 저장
        deliveryRepo.save(delivery)

        return DispatchJobResult(
            webhookId = webhook.id,
            webhookName = webhook.name,
            success = result.isSuccess(),
            statusCode = result.toStatusCode(),
            latencyMs = result.toLatencyMs(),
            errorMessage = result.toErrorMessage()
        )
    }

    /**
     * 통계 조회
     */
    fun getStats(): EventHandlerStats = EventHandlerStats(
        isRunning = isRunning,
        eventsReceived = eventsReceived.get(),
        eventsDispatched = eventsDispatched.get(),
        eventsDropped = eventsDropped.get(),
        queueSize = if (isRunning) eventChannel.toString() else "closed",
        workerCount = config.workerCount,
        dispatcherStats = dispatcher.getDispatcherStats()
    )
}

/**
 * 이벤트 핸들러 설정
 */
data class EventHandlerConfig(
    /** 워커 수 */
    val workerCount: Int = 4,
    /** 채널 용량 */
    val channelCapacity: Int = 1000
) {
    companion object {
        val DEFAULT = EventHandlerConfig()
    }
}

/**
 * 전송 작업
 */
data class DispatchJob(
    val webhook: Webhook,
    val payload: WebhookEventPayload
)

/**
 * 전송 작업 결과
 */
data class DispatchJobResult(
    val webhookId: java.util.UUID,
    val webhookName: String,
    val success: Boolean,
    val statusCode: Int?,
    val latencyMs: Int?,
    val errorMessage: String?
)

/**
 * 이벤트 핸들러 통계
 */
data class EventHandlerStats(
    val isRunning: Boolean,
    val eventsReceived: Long,
    val eventsDispatched: Long,
    val eventsDropped: Long,
    val queueSize: String,
    val workerCount: Int,
    val dispatcherStats: com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatcherStats
)
