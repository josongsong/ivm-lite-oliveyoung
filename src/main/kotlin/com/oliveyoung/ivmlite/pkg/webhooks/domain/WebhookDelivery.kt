package com.oliveyoung.ivmlite.pkg.webhooks.domain

import java.time.Instant
import java.util.UUID

/**
 * Webhook Delivery Entity
 *
 * 웹훅 전송 기록을 나타내는 도메인 엔티티.
 */
data class WebhookDelivery(
    val id: UUID = UUID.randomUUID(),
    val webhookId: UUID,
    val eventType: WebhookEvent,
    val eventPayload: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseStatus: Int? = null,
    val responseBody: String? = null,
    val latencyMs: Int? = null,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val errorMessage: String? = null,
    val attemptCount: Int = 1,
    val nextRetryAt: Instant? = null,
    val createdAt: Instant = Instant.now()
) {
    /**
     * 전송 성공 처리
     */
    fun markSuccess(
        responseStatus: Int,
        responseBody: String?,
        latencyMs: Int
    ): WebhookDelivery = copy(
        status = DeliveryStatus.SUCCESS,
        responseStatus = responseStatus,
        responseBody = responseBody?.take(10_000), // 10KB 제한
        latencyMs = latencyMs,
        errorMessage = null,
        nextRetryAt = null
    )

    /**
     * 재시도 예약
     */
    fun scheduleRetry(
        errorMessage: String,
        responseStatus: Int?,
        nextRetryAt: Instant
    ): WebhookDelivery = copy(
        status = DeliveryStatus.RETRYING,
        attemptCount = attemptCount + 1,
        errorMessage = errorMessage,
        responseStatus = responseStatus,
        nextRetryAt = nextRetryAt
    )

    /**
     * 최종 실패 처리
     */
    fun markFailed(
        errorMessage: String,
        responseStatus: Int? = null
    ): WebhookDelivery = copy(
        status = DeliveryStatus.FAILED,
        errorMessage = errorMessage,
        responseStatus = responseStatus,
        nextRetryAt = null
    )

    /**
     * Circuit Breaker Open 상태로 차단
     */
    fun markCircuitOpen(): WebhookDelivery = copy(
        status = DeliveryStatus.CIRCUIT_OPEN,
        errorMessage = "Circuit breaker is open",
        nextRetryAt = null
    )

    /**
     * Rate Limit 초과로 차단
     */
    fun markRateLimited(): WebhookDelivery = copy(
        status = DeliveryStatus.RATE_LIMITED,
        errorMessage = "Rate limit exceeded",
        nextRetryAt = null
    )
}
