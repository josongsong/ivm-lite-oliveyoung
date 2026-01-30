package com.oliveyoung.ivmlite.pkg.webhooks.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import com.oliveyoung.ivmlite.pkg.webhooks.domain.RetryPolicy
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryFilter
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryStats
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatchResult
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDispatcherPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookStats
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.time.Instant
import java.util.UUID

/**
 * 웹훅 서비스
 *
 * 웹훅 CRUD 및 테스트 전송 기능 제공.
 */
class WebhookService(
    private val webhookRepo: WebhookRepositoryPort,
    private val deliveryRepo: WebhookDeliveryRepositoryPort,
    private val dispatcher: WebhookDispatcherPort
) {
    /**
     * 웹훅 생성
     */
    suspend fun createWebhook(request: CreateWebhookRequest): Either<DomainError, Webhook> = either {
        // 이름 중복 체크
        val existing = webhookRepo.findByName(request.name).getOrNull()
        ensure(existing == null) {
            DomainError.ValidationError("Webhook with name '${request.name}' already exists")
        }

        // 이벤트 파싱
        val events = request.events.map { eventName ->
            WebhookEvent.fromString(eventName)
                ?: raise(DomainError.ValidationError("Unknown event type: $eventName"))
        }.toSet()

        ensure(events.isNotEmpty()) {
            DomainError.ValidationError("At least one event type is required")
        }

        val webhook = Webhook.create(
            name = request.name,
            url = request.url,
            events = events,
            filters = request.filters,
            headers = request.headers,
            payloadTemplate = request.payloadTemplate,
            retryPolicy = request.retryPolicy ?: RetryPolicy.DEFAULT,
            secretToken = request.secretToken
        )

        when (val result = webhookRepo.save(webhook)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 웹훅 수정
     */
    suspend fun updateWebhook(id: UUID, request: UpdateWebhookRequest): Either<DomainError, Webhook> = either {
        val existing = when (val result = webhookRepo.findById(id)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        } ?: raise(DomainError.NotFoundError("Webhook not found: $id"))

        // 이름 중복 체크 (다른 웹훅과)
        if (request.name != null && request.name != existing.name) {
            val nameExists = webhookRepo.findByName(request.name).getOrNull()
            ensure(nameExists == null) {
                DomainError.ValidationError("Webhook with name '${request.name}' already exists")
            }
        }

        // 이벤트 파싱
        val events = request.events?.map { eventName ->
            WebhookEvent.fromString(eventName)
                ?: raise(DomainError.ValidationError("Unknown event type: $eventName"))
        }?.toSet()

        val updated = existing.copy(
            name = request.name ?: existing.name,
            url = request.url ?: existing.url,
            events = events ?: existing.events,
            filters = request.filters ?: existing.filters,
            headers = request.headers ?: existing.headers,
            payloadTemplate = request.payloadTemplate ?: existing.payloadTemplate,
            isActive = request.isActive ?: existing.isActive,
            retryPolicy = request.retryPolicy ?: existing.retryPolicy,
            secretToken = request.secretToken ?: existing.secretToken,
            updatedAt = Instant.now()
        )

        when (val result = webhookRepo.save(updated)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 웹훅 삭제
     */
    suspend fun deleteWebhook(id: UUID): Either<DomainError, Boolean> = either {
        when (val result = webhookRepo.delete(id)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 웹훅 조회
     */
    suspend fun getWebhook(id: UUID): Either<DomainError, Webhook?> = either {
        when (val result = webhookRepo.findById(id)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 전체 웹훅 조회
     */
    suspend fun listWebhooks(activeOnly: Boolean = false): Either<DomainError, List<Webhook>> = either {
        val result = if (activeOnly) {
            webhookRepo.findAllActive()
        } else {
            webhookRepo.findAll()
        }

        when (result) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 테스트 전송
     */
    suspend fun testWebhook(id: UUID): Either<DomainError, TestResult> = either {
        val webhook = when (val result = webhookRepo.findById(id)) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        } ?: raise(DomainError.NotFoundError("Webhook not found: $id"))

        // 테스트 페이로드 생성
        val testPayload = WebhookEventPayload(
            eventType = webhook.events.first(),
            context = mapOf(
                "tenantId" to "test",
                "entityType" to "TEST_ENTITY",
                "entityKey" to "test-key"
            ),
            data = mapOf(
                "test" to true,
                "message" to "This is a test webhook delivery"
            )
        )

        val dispatchResult = dispatcher.testDispatch(webhook, testPayload)

        // 테스트 전송 기록 저장
        val delivery = WebhookDelivery.create(
            webhookId = webhook.id,
            eventType = testPayload.eventType,
            eventPayload = testPayload.toMap(),
            requestBody = testPayload.toMap().toString()
        ).let { d ->
            when (dispatchResult) {
                is DispatchResult.Success -> d.markSuccess(
                    responseStatus = dispatchResult.statusCode,
                    responseBody = dispatchResult.responseBody,
                    responseHeaders = dispatchResult.responseHeaders,
                    latencyMs = dispatchResult.latencyMs
                )
                is DispatchResult.Failure -> d.markFailed(
                    errorMessage = dispatchResult.errorMessage,
                    responseStatus = dispatchResult.statusCode,
                    responseBody = dispatchResult.responseBody,
                    latencyMs = dispatchResult.latencyMs
                )
                is DispatchResult.Timeout -> d.markFailed(
                    errorMessage = "Request timed out after ${dispatchResult.timeoutMs}ms"
                )
                is DispatchResult.CircuitOpen -> d.markCircuitOpen()
                is DispatchResult.RateLimited -> d.markRateLimited()
            }
        }

        deliveryRepo.save(delivery)

        TestResult(
            success = dispatchResult.isSuccess(),
            statusCode = dispatchResult.toStatusCode(),
            latencyMs = dispatchResult.toLatencyMs(),
            errorMessage = dispatchResult.toErrorMessage(),
            delivery = delivery
        )
    }

    /**
     * 웹훅 통계
     */
    suspend fun getStats(): Either<DomainError, WebhookStats> = either {
        when (val result = webhookRepo.getStats()) {
            is WebhookRepositoryPort.Result.Ok -> result.value
            is WebhookRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 전송 기록 조회
     */
    suspend fun getDeliveries(webhookId: UUID, limit: Int = 50): Either<DomainError, List<WebhookDelivery>> = either {
        when (val result = deliveryRepo.findByWebhookId(webhookId, limit)) {
            is WebhookDeliveryRepositoryPort.Result.Ok -> result.value
            is WebhookDeliveryRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 전송 기록 상세 조회
     */
    suspend fun getDelivery(id: UUID): Either<DomainError, WebhookDelivery?> = either {
        when (val result = deliveryRepo.findById(id)) {
            is WebhookDeliveryRepositoryPort.Result.Ok -> result.value
            is WebhookDeliveryRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 최근 전송 기록 조회
     */
    suspend fun getRecentDeliveries(limit: Int = 50): Either<DomainError, List<WebhookDelivery>> = either {
        when (val result = deliveryRepo.findRecent(limit)) {
            is WebhookDeliveryRepositoryPort.Result.Ok -> result.value
            is WebhookDeliveryRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 전송 통계 조회
     */
    suspend fun getDeliveryStats(webhookId: UUID? = null): Either<DomainError, DeliveryStats> = either {
        when (val result = deliveryRepo.getStats(webhookId)) {
            is WebhookDeliveryRepositoryPort.Result.Ok -> result.value
            is WebhookDeliveryRepositoryPort.Result.Err -> raise(result.error)
        }
    }

    /**
     * 지원 이벤트 목록
     */
    fun getSupportedEvents(): List<EventInfo> {
        return WebhookEvent.entries.map { event ->
            EventInfo(
                name = event.name,
                description = event.description,
                category = event.category.name
            )
        }
    }

    /**
     * Circuit Breaker 상태 조회
     */
    fun getCircuitState(webhookId: UUID): String {
        return dispatcher.getCircuitState(webhookId).name
    }

    /**
     * Circuit Breaker 리셋
     */
    fun resetCircuit(webhookId: UUID) {
        dispatcher.resetCircuit(webhookId)
    }
}

// ==================== DTOs ====================

data class CreateWebhookRequest(
    val name: String,
    val url: String,
    val events: List<String>,
    val filters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val payloadTemplate: String? = null,
    val retryPolicy: RetryPolicy? = null,
    val secretToken: String? = null
)

data class UpdateWebhookRequest(
    val name: String? = null,
    val url: String? = null,
    val events: List<String>? = null,
    val filters: Map<String, String>? = null,
    val headers: Map<String, String>? = null,
    val payloadTemplate: String? = null,
    val isActive: Boolean? = null,
    val retryPolicy: RetryPolicy? = null,
    val secretToken: String? = null
)

data class TestResult(
    val success: Boolean,
    val statusCode: Int?,
    val latencyMs: Int?,
    val errorMessage: String?,
    val delivery: WebhookDelivery
)

data class EventInfo(
    val name: String,
    val description: String,
    val category: String
)
