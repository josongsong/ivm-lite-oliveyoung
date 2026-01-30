package com.oliveyoung.ivmlite.apps.admin.routes

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.webhooks.application.CreateWebhookRequest
import com.oliveyoung.ivmlite.pkg.webhooks.application.UpdateWebhookRequest
import com.oliveyoung.ivmlite.pkg.webhooks.application.WebhookEventHandler
import com.oliveyoung.ivmlite.pkg.webhooks.application.WebhookService
import com.oliveyoung.ivmlite.pkg.webhooks.domain.RetryPolicy
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Webhook Routes (웹훅 관리 API)
 *
 * GET    /webhooks              - 목록
 * POST   /webhooks              - 생성
 * GET    /webhooks/{id}         - 상세
 * PUT    /webhooks/{id}         - 수정
 * DELETE /webhooks/{id}         - 삭제
 * POST   /webhooks/{id}/test    - 테스트 전송
 * GET    /webhooks/{id}/deliveries - 전송 기록
 * GET    /webhooks/stats        - 통계
 * GET    /webhooks/events       - 지원 이벤트 목록
 * GET    /webhooks/deliveries/recent - 최근 전송
 */
fun Route.webhookRoutes() {
    val webhookService by inject<WebhookService>()
    val eventHandler by inject<WebhookEventHandler>()

    /**
     * GET /webhooks
     * 웹훅 목록 조회
     */
    get("/webhooks") {
        val activeOnly = call.request.queryParameters["active"]?.toBoolean() ?: false

        when (val result = webhookService.listWebhooks(activeOnly)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, WebhookListResponse(
                    webhooks = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "WEBHOOK_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * POST /webhooks
     * 웹훅 생성
     */
    post("/webhooks") {
        val request = call.receive<CreateWebhookApiRequest>()

        val createRequest = CreateWebhookRequest(
            name = request.name,
            url = request.url,
            events = request.events,
            filters = request.filters ?: emptyMap(),
            headers = request.headers ?: emptyMap(),
            payloadTemplate = request.payloadTemplate,
            retryPolicy = request.retryPolicy?.toDomain(),
            secretToken = request.secretToken
        )

        when (val result = webhookService.createWebhook(createRequest)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.Created, result.value.toResponse())
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(code = "CREATE_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * GET /webhooks/stats
     * 웹훅 통계
     */
    get("/webhooks/stats") {
        when (val webhookStats = webhookService.getStats()) {
            is Either.Right -> {
                when (val deliveryStats = webhookService.getDeliveryStats()) {
                    is Either.Right -> {
                        val handlerStats = eventHandler.getStats()
                        call.respond(HttpStatusCode.OK, WebhookStatsResponse(
                            webhooks = WebhookStatsDto(
                                total = webhookStats.value.totalCount,
                                active = webhookStats.value.activeCount,
                                inactive = webhookStats.value.inactiveCount
                            ),
                            deliveries = DeliveryStatsDto(
                                total = deliveryStats.value.totalCount,
                                success = deliveryStats.value.successCount,
                                failed = deliveryStats.value.failedCount,
                                retrying = deliveryStats.value.retryingCount,
                                successRate = deliveryStats.value.successRate,
                                avgLatencyMs = deliveryStats.value.avgLatencyMs,
                                today = deliveryStats.value.todayCount
                            ),
                            handler = HandlerStatsDto(
                                isRunning = handlerStats.isRunning,
                                eventsReceived = handlerStats.eventsReceived,
                                eventsDispatched = handlerStats.eventsDispatched,
                                eventsDropped = handlerStats.eventsDropped
                            )
                        ))
                    }
                    is Either.Left -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiError(code = "STATS_ERROR", message = deliveryStats.value.toString())
                        )
                    }
                }
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "STATS_ERROR", message = webhookStats.value.toString())
                )
            }
        }
    }

    /**
     * GET /webhooks/events
     * 지원 이벤트 목록
     */
    get("/webhooks/events") {
        val events = webhookService.getSupportedEvents()
        call.respond(HttpStatusCode.OK, EventListResponse(
            events = events.map { EventDto(it.name, it.description, it.category) }
        ))
    }

    /**
     * GET /webhooks/deliveries/recent
     * 최근 전송 기록
     */
    get("/webhooks/deliveries/recent") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        when (val result = webhookService.getRecentDeliveries(limit)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, DeliveryListResponse(
                    deliveries = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "DELIVERY_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * GET /webhooks/{id}
     * 웹훅 상세 조회
     */
    get("/webhooks/{id}") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@get
        }

        when (val result = webhookService.getWebhook(id)) {
            is Either.Right -> {
                val webhook = result.value
                if (webhook != null) {
                    call.respond(HttpStatusCode.OK, webhook.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Webhook not found"))
                }
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "WEBHOOK_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * PUT /webhooks/{id}
     * 웹훅 수정
     */
    put("/webhooks/{id}") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@put
        }

        val request = call.receive<UpdateWebhookApiRequest>()

        val updateRequest = UpdateWebhookRequest(
            name = request.name,
            url = request.url,
            events = request.events,
            filters = request.filters,
            headers = request.headers,
            payloadTemplate = request.payloadTemplate,
            isActive = request.isActive,
            retryPolicy = request.retryPolicy?.toDomain(),
            secretToken = request.secretToken
        )

        when (val result = webhookService.updateWebhook(id, updateRequest)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(code = "UPDATE_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * DELETE /webhooks/{id}
     * 웹훅 삭제
     */
    delete("/webhooks/{id}") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@delete
        }

        when (val result = webhookService.deleteWebhook(id)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to result.value,
                    "message" to if (result.value) "Webhook deleted" else "Webhook not found"
                ))
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "DELETE_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * POST /webhooks/{id}/test
     * 테스트 전송
     */
    post("/webhooks/{id}/test") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@post
        }

        when (val result = webhookService.testWebhook(id)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, TestResultResponse(
                    success = result.value.success,
                    statusCode = result.value.statusCode,
                    latencyMs = result.value.latencyMs,
                    errorMessage = result.value.errorMessage,
                    deliveryId = result.value.delivery.id.toString()
                ))
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(code = "TEST_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * GET /webhooks/{id}/deliveries
     * 웹훅별 전송 기록
     */
    get("/webhooks/{id}/deliveries") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@get
        }

        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        when (val result = webhookService.getDeliveries(id, limit)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, DeliveryListResponse(
                    deliveries = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is Either.Left -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "DELIVERY_ERROR", message = result.value.toString())
                )
            }
        }
    }

    /**
     * GET /webhooks/{id}/circuit
     * Circuit Breaker 상태
     */
    get("/webhooks/{id}/circuit") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@get
        }

        call.respond(HttpStatusCode.OK, mapOf(
            "state" to webhookService.getCircuitState(id)
        ))
    }

    /**
     * POST /webhooks/{id}/circuit/reset
     * Circuit Breaker 리셋
     */
    post("/webhooks/{id}/circuit/reset") {
        val id = call.parameters["id"]?.let {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        } ?: run {
            call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid webhook ID"))
            return@post
        }

        webhookService.resetCircuit(id)
        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "message" to "Circuit breaker reset",
            "state" to webhookService.getCircuitState(id)
        ))
    }
}

// ==================== API Request DTOs ====================

@Serializable
data class CreateWebhookApiRequest(
    val name: String,
    val url: String,
    val events: List<String>,
    val filters: Map<String, String>? = null,
    val headers: Map<String, String>? = null,
    val payloadTemplate: String? = null,
    val retryPolicy: RetryPolicyDto? = null,
    val secretToken: String? = null
)

@Serializable
data class UpdateWebhookApiRequest(
    val name: String? = null,
    val url: String? = null,
    val events: List<String>? = null,
    val filters: Map<String, String>? = null,
    val headers: Map<String, String>? = null,
    val payloadTemplate: String? = null,
    val isActive: Boolean? = null,
    val retryPolicy: RetryPolicyDto? = null,
    val secretToken: String? = null
)

@Serializable
data class RetryPolicyDto(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60000,
    val multiplier: Double = 2.0
) {
    fun toDomain() = RetryPolicy(
        maxRetries = maxRetries,
        initialDelayMs = initialDelayMs,
        maxDelayMs = maxDelayMs,
        multiplier = multiplier
    )
}

// ==================== API Response DTOs ====================

@Serializable
data class WebhookListResponse(
    val webhooks: List<WebhookResponse>,
    val total: Int
)

@Serializable
data class WebhookResponse(
    val id: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val filters: Map<String, String>,
    val headers: Map<String, String>,
    val payloadTemplate: String?,
    val isActive: Boolean,
    val retryPolicy: RetryPolicyDto,
    val secretToken: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class WebhookStatsResponse(
    val webhooks: WebhookStatsDto,
    val deliveries: DeliveryStatsDto,
    val handler: HandlerStatsDto
)

@Serializable
data class WebhookStatsDto(
    val total: Int,
    val active: Int,
    val inactive: Int
)

@Serializable
data class DeliveryStatsDto(
    val total: Long,
    val success: Long,
    val failed: Long,
    val retrying: Long,
    val successRate: Double,
    val avgLatencyMs: Double?,
    val today: Long
)

@Serializable
data class HandlerStatsDto(
    val isRunning: Boolean,
    val eventsReceived: Long,
    val eventsDispatched: Long,
    val eventsDropped: Long
)

@Serializable
data class EventListResponse(
    val events: List<EventDto>
)

@Serializable
data class EventDto(
    val name: String,
    val description: String,
    val category: String
)

@Serializable
data class DeliveryListResponse(
    val deliveries: List<DeliveryResponse>,
    val total: Int
)

@Serializable
data class DeliveryResponse(
    val id: String,
    val webhookId: String,
    val eventType: String,
    val status: String,
    val responseStatus: Int?,
    val latencyMs: Int?,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: String
)

@Serializable
data class TestResultResponse(
    val success: Boolean,
    val statusCode: Int?,
    val latencyMs: Int?,
    val errorMessage: String?,
    val deliveryId: String
)

// ==================== Domain → DTO 변환 ====================

private fun com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook.toResponse() = WebhookResponse(
    id = id.toString(),
    name = name,
    url = url,
    events = events.map { it.name },
    filters = filters,
    headers = headers.mapValues { (k, _) ->
        if (k.lowercase().contains("auth") || k.lowercase().contains("secret")) "***" else headers[k]!!
    },
    payloadTemplate = payloadTemplate,
    isActive = isActive,
    retryPolicy = RetryPolicyDto(
        maxRetries = retryPolicy.maxRetries,
        initialDelayMs = retryPolicy.initialDelayMs,
        maxDelayMs = retryPolicy.maxDelayMs,
        multiplier = retryPolicy.multiplier
    ),
    secretToken = getMaskedSecretToken(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

private fun com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery.toResponse() = DeliveryResponse(
    id = id.toString(),
    webhookId = webhookId.toString(),
    eventType = eventType.name,
    status = status.name,
    responseStatus = responseStatus,
    latencyMs = latencyMs,
    errorMessage = errorMessage,
    attemptCount = attemptCount,
    createdAt = createdAt.toString()
)
