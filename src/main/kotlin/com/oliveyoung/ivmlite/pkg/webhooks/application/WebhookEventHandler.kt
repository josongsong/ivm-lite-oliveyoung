package com.oliveyoung.ivmlite.pkg.webhooks.application

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Webhook Event Handler
 *
 * 파이프라인 이벤트 수신 → 웹훅 트리거 → 비동기 전송.
 * Worker Pool 패턴으로 동시 처리.
 */
class WebhookEventHandler(
    private val webhookRepo: WebhookRepositoryPort,
    private val deliveryRepo: WebhookDeliveryRepositoryPort,
    private val dispatcher: WebhookDispatcherPort,
    private val config: Config = Config()
) {
    data class Config(
        val workerCount: Int = 4,
        val channelCapacity: Int = 1000
    )

    private data class DispatchJob(
        val webhookId: java.util.UUID,
        val payload: WebhookEventPayload,
        val filters: Map<String, String>
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<DispatchJob>(config.channelCapacity)
    private val isRunning = AtomicBoolean(false)

    // 통계 카운터
    private val eventsReceived = AtomicLong(0)
    private val eventsDispatched = AtomicLong(0)
    private val eventsDropped = AtomicLong(0)

    /**
     * 이벤트 핸들러 시작
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            repeat(config.workerCount) { workerId ->
                scope.launch {
                    processJobs(workerId)
                }
            }
        }
    }

    /**
     * 이벤트 발행 (파이프라인에서 호출)
     */
    suspend fun publishEvent(
        event: WebhookEvent,
        data: Map<String, String>,
        metadata: Map<String, String> = emptyMap(),
        filters: Map<String, String> = emptyMap()
    ) {
        eventsReceived.incrementAndGet()

        val payload = WebhookEventPayload.create(
            event = event,
            data = data,
            metadata = metadata
        )

        // 이벤트를 구독하는 웹훅 조회
        val webhooks = webhookRepo.findByEvent(event)

        // 각 웹훅에 대해 dispatch job 생성
        val matchingWebhooks = webhooks.filter { it.shouldTrigger(event, filters) }
        matchingWebhooks.forEach { webhook ->
            val sent = channel.trySend(DispatchJob(webhook.id, payload, filters))
            if (sent.isSuccess) {
                eventsDispatched.incrementAndGet()
            } else {
                eventsDropped.incrementAndGet()
            }
        }
    }

    /**
     * 편의 메서드: Slice Created 이벤트
     */
    suspend fun onSliceCreated(
        sliceId: String,
        entityType: String,
        entityId: String,
        version: Long
    ) {
        publishEvent(
            event = WebhookEvent.SLICE_CREATED,
            data = mapOf(
                "sliceId" to sliceId,
                "entityType" to entityType,
                "entityId" to entityId,
                "version" to version.toString()
            ),
            filters = mapOf("entityType" to entityType)
        )
    }

    /**
     * 편의 메서드: View Changed 이벤트
     */
    suspend fun onViewChanged(
        viewName: String,
        entityId: String,
        changeType: String
    ) {
        publishEvent(
            event = WebhookEvent.VIEW_CHANGED,
            data = mapOf(
                "viewName" to viewName,
                "entityId" to entityId,
                "changeType" to changeType
            ),
            filters = mapOf("viewName" to viewName)
        )
    }

    /**
     * 편의 메서드: Sink Shipped 이벤트
     */
    suspend fun onSinkShipped(
        sinkType: String,
        destination: String,
        recordCount: Int
    ) {
        publishEvent(
            event = WebhookEvent.SINK_SHIPPED,
            data = mapOf(
                "sinkType" to sinkType,
                "destination" to destination,
                "recordCount" to recordCount.toString()
            ),
            filters = mapOf("sinkType" to sinkType)
        )
    }

    /**
     * 편의 메서드: Error 이벤트
     */
    suspend fun onError(
        source: String,
        message: String,
        stackTrace: String? = null
    ) {
        publishEvent(
            event = WebhookEvent.ERROR,
            data = mapOf(
                "source" to source,
                "message" to message,
                "stackTrace" to (stackTrace ?: "")
            ),
            filters = mapOf("source" to source)
        )
    }

    private suspend fun processJobs(workerId: Int) {
        for (job in channel) {
            try {
                processJob(job)
            } catch (e: Exception) {
                // 개별 job 처리 실패는 다른 job에 영향 없이 계속 진행
                println("[Worker-$workerId] Error processing job: ${e.message}")
            }
        }
    }

    private suspend fun processJob(job: DispatchJob) {
        val webhook = webhookRepo.findById(job.webhookId) ?: return
        val startTime = System.currentTimeMillis()

        // 초기 delivery 레코드 생성
        var delivery = WebhookDelivery(
            webhookId = webhook.id,
            eventType = WebhookEvent.valueOf(job.payload.event),
            eventPayload = job.payload.toJson(),
            status = DeliveryStatus.PENDING
        )
        delivery = deliveryRepo.save(delivery)

        // 전송 시도
        val result = dispatcher.dispatch(webhook, job.payload)

        // 결과에 따라 delivery 업데이트
        delivery = when (result) {
            is DispatchResult.Success -> {
                delivery.markSuccess(
                    responseStatus = result.statusCode,
                    responseBody = result.responseBody,
                    latencyMs = result.latencyMs
                )
            }
            is DispatchResult.Failed -> {
                if (result.retryable && webhook.retryPolicy.canRetry(delivery.attemptCount)) {
                    val nextDelay = webhook.retryPolicy.calculateDelay(delivery.attemptCount)
                    delivery.scheduleRetry(
                        errorMessage = result.errorMessage,
                        responseStatus = result.statusCode,
                        nextRetryAt = Instant.now().plusMillis(nextDelay)
                    )
                } else {
                    delivery.markFailed(
                        errorMessage = result.errorMessage,
                        responseStatus = result.statusCode
                    )
                }
            }
            is DispatchResult.CircuitOpen -> delivery.markCircuitOpen()
            is DispatchResult.RateLimited -> delivery.markRateLimited()
        }

        deliveryRepo.save(delivery)
    }

    /**
     * 핸들러 통계 조회
     */
    fun getStats(): HandlerStats = HandlerStats(
        isRunning = isRunning.get(),
        eventsReceived = eventsReceived.get(),
        eventsDispatched = eventsDispatched.get(),
        eventsDropped = eventsDropped.get()
    )

    /**
     * 이벤트 핸들러 종료
     */
    fun stop() {
        isRunning.set(false)
        channel.close()
    }

    /**
     * 핸들러 통계
     */
    data class HandlerStats(
        val isRunning: Boolean,
        val eventsReceived: Long,
        val eventsDispatched: Long,
        val eventsDropped: Long
    )
}
