package com.oliveyoung.ivmlite.pkg.webhooks.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import com.oliveyoung.ivmlite.pkg.webhooks.domain.RetryPolicy
import com.oliveyoung.ivmlite.pkg.webhooks.ports.CircuitState
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryFilter
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatchResult
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDispatcherPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Webhook Service
 *
 * 웹훅 CRUD 및 테스트 비즈니스 로직.
 */
class WebhookService(
    private val webhookRepo: WebhookRepositoryPort,
    private val deliveryRepo: WebhookDeliveryRepositoryPort,
    private val dispatcher: WebhookDispatcherPort
) {
    sealed class WebhookError {
        data class NotFound(val id: UUID) : WebhookError()
        data class ValidationError(val message: String) : WebhookError()
        data class StorageError(val message: String) : WebhookError()
    }

    /**
     * 웹훅 목록 조회
     */
    suspend fun listWebhooks(activeOnly: Boolean = false): Either<WebhookError, List<Webhook>> = try {
        either {
            if (activeOnly) webhookRepo.findActive() else webhookRepo.findAll()
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 웹훅 생성
     */
    suspend fun createWebhook(request: CreateWebhookRequest): Either<WebhookError, Webhook> = either {
        ensure(request.name.isNotBlank()) { WebhookError.ValidationError("name must not be blank") }
        ensure(request.url.isNotBlank()) { WebhookError.ValidationError("url must not be blank") }
        ensure(request.events.isNotEmpty()) { WebhookError.ValidationError("events must not be empty") }

        val events = request.events.mapNotNull { WebhookEvent.fromString(it) }.toSet()
        ensure(events.isNotEmpty()) { WebhookError.ValidationError("No valid events provided") }

        val webhook = Webhook(
            name = request.name,
            url = request.url,
            events = events,
            filters = request.filters,
            headers = request.headers,
            payloadTemplate = request.payloadTemplate,
            retryPolicy = request.retryPolicy ?: RetryPolicy.DEFAULT,
            secretToken = request.secretToken
        )

        val result = webhookRepo.save(webhook)
        when (result) {
            is WebhookRepositoryPort.Result.Ok -> result.webhook
            is WebhookRepositoryPort.Result.Error -> raise(WebhookError.StorageError(result.message))
        }
    }

    /**
     * 웹훅 조회
     */
    suspend fun getWebhook(id: UUID): Either<WebhookError, Webhook?> = try {
        either {
            webhookRepo.findById(id)
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 웹훅 수정
     */
    suspend fun updateWebhook(id: UUID, request: UpdateWebhookRequest): Either<WebhookError, Webhook> = either {
        val existing = webhookRepo.findById(id) ?: raise(WebhookError.NotFound(id))

        val events = request.events?.mapNotNull { WebhookEvent.fromString(it) }?.toSet()

        val updated = existing.update(
            name = request.name,
            url = request.url,
            events = events,
            filters = request.filters,
            headers = request.headers,
            payloadTemplate = request.payloadTemplate,
            isActive = request.isActive,
            retryPolicy = request.retryPolicy,
            secretToken = request.secretToken
        )

        val result = webhookRepo.save(updated)
        when (result) {
            is WebhookRepositoryPort.Result.Ok -> result.webhook
            is WebhookRepositoryPort.Result.Error -> raise(WebhookError.StorageError(result.message))
        }
    }

    /**
     * 웹훅 삭제
     */
    suspend fun deleteWebhook(id: UUID): Either<WebhookError, Boolean> = try {
        either {
            webhookRepo.delete(id)
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 웹훅 통계
     */
    suspend fun getStats(): Either<WebhookError, WebhookStats> = try {
        either {
            val all = webhookRepo.findAll()
            WebhookStats(
                totalCount = all.size,
                activeCount = all.count { it.isActive },
                inactiveCount = all.count { !it.isActive }
            )
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 전송 통계
     */
    suspend fun getDeliveryStats(): Either<WebhookError, DeliveryStats> = try {
        either {
            val stats = deliveryRepo.getOverallStats()
            val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val todayDeliveries = deliveryRepo.findByFilter(DeliveryFilter(fromDate = today))

            DeliveryStats(
                totalCount = stats.total,
                successCount = stats.success,
                failedCount = stats.failed,
                retryingCount = stats.retrying,
                successRate = stats.successRate,
                avgLatencyMs = if (stats.averageLatencyMs > 0) stats.averageLatencyMs else null,
                todayCount = todayDeliveries.size.toLong()
            )
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 웹훅 테스트 전송
     */
    suspend fun testWebhook(id: UUID): Either<WebhookError, TestResult> = either {
        val webhook = webhookRepo.findById(id) ?: raise(WebhookError.NotFound(id))

        val testPayload = WebhookEventPayload.create(
            event = WebhookEvent.SLICE_CREATED,
            data = mapOf(
                "sliceId" to "test-slice-id",
                "entityType" to "TEST",
                "entityId" to "test-entity-123",
                "version" to "1"
            ),
            metadata = mapOf("test" to "true")
        )

        // 테스트용 delivery 레코드 생성
        val delivery = WebhookDelivery(
            webhookId = webhook.id,
            eventType = WebhookEvent.SLICE_CREATED,
            eventPayload = testPayload.toJson()
        )

        val result = dispatcher.testDispatch(webhook, testPayload)

        val savedDelivery = when (result) {
            is DispatchResult.Success -> {
                deliveryRepo.save(delivery.markSuccess(
                    responseStatus = result.statusCode,
                    responseBody = result.responseBody,
                    latencyMs = result.latencyMs
                ))
            }
            is DispatchResult.Failed -> {
                deliveryRepo.save(delivery.markFailed(
                    errorMessage = result.errorMessage,
                    responseStatus = result.statusCode
                ))
            }
            is DispatchResult.CircuitOpen -> {
                deliveryRepo.save(delivery.markCircuitOpen())
            }
            is DispatchResult.RateLimited -> {
                deliveryRepo.save(delivery.markRateLimited())
            }
        }

        TestResult(
            success = result is DispatchResult.Success,
            statusCode = when (result) {
                is DispatchResult.Success -> result.statusCode
                is DispatchResult.Failed -> result.statusCode
                else -> null
            },
            latencyMs = when (result) {
                is DispatchResult.Success -> result.latencyMs
                is DispatchResult.Failed -> result.latencyMs
                else -> null
            },
            errorMessage = when (result) {
                is DispatchResult.Failed -> result.errorMessage
                is DispatchResult.CircuitOpen -> "Circuit breaker is open"
                is DispatchResult.RateLimited -> "Rate limited"
                else -> null
            },
            delivery = savedDelivery
        )
    }

    /**
     * 전송 기록 조회
     */
    suspend fun getDeliveries(webhookId: UUID, limit: Int = 50): Either<WebhookError, List<WebhookDelivery>> = try {
        either {
            deliveryRepo.findByWebhookId(webhookId, limit)
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 최근 전송 기록
     */
    suspend fun getRecentDeliveries(limit: Int = 50): Either<WebhookError, List<WebhookDelivery>> = try {
        either {
            deliveryRepo.findByFilter(DeliveryFilter(limit = limit))
        }
    } catch (e: Exception) {
        Either.Left(WebhookError.StorageError(e.message ?: "Unknown error"))
    }

    /**
     * 지원하는 이벤트 목록
     */
    fun getSupportedEvents(): List<EventInfo> =
        WebhookEvent.entries.map { event ->
            EventInfo(
                name = event.name,
                description = event.description,
                category = event.category
            )
        }

    /**
     * Circuit Breaker 상태 조회
     */
    fun getCircuitState(webhookId: UUID): CircuitState =
        dispatcher.getCircuitState(webhookId)

    /**
     * Circuit Breaker 리셋
     */
    fun resetCircuit(webhookId: UUID) =
        dispatcher.resetCircuit(webhookId)

    // ===== Data Classes =====

    data class WebhookStats(
        val totalCount: Int,
        val activeCount: Int,
        val inactiveCount: Int
    )

    data class DeliveryStats(
        val totalCount: Long,
        val successCount: Long,
        val failedCount: Long,
        val retryingCount: Long,
        val successRate: Double,
        val avgLatencyMs: Double?,
        val todayCount: Long
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
}

/**
 * 웹훅 생성 요청
 */
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

/**
 * 웹훅 수정 요청
 */
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
