package com.oliveyoung.ivmlite.pkg.webhooks.domain

import java.time.Instant
import java.util.UUID

/**
 * 웹훅 전송 기록
 *
 * 웹훅 전송의 요청/응답 정보와 상태를 기록한다.
 * 감사 로그 및 재시도 관리에 사용.
 *
 * @property id 고유 식별자
 * @property webhookId 웹훅 ID
 * @property eventType 이벤트 타입
 * @property eventPayload 이벤트 페이로드 (JSON)
 * @property requestHeaders 요청 헤더
 * @property requestBody 요청 본문
 * @property responseStatus HTTP 응답 상태 코드
 * @property responseBody 응답 본문
 * @property responseHeaders 응답 헤더
 * @property latencyMs 전송 소요 시간 (밀리초)
 * @property status 전송 상태
 * @property errorMessage 에러 메시지 (실패 시)
 * @property attemptCount 시도 횟수
 * @property nextRetryAt 다음 재시도 예정 시각
 * @property createdAt 생성 시각
 */
data class WebhookDelivery(
    val id: UUID,
    val webhookId: UUID,
    val eventType: WebhookEvent,
    val eventPayload: Map<String, Any?>,
    val requestHeaders: Map<String, String>? = null,
    val requestBody: String? = null,
    val responseStatus: Int? = null,
    val responseBody: String? = null,
    val responseHeaders: Map<String, String>? = null,
    val latencyMs: Int? = null,
    val status: DeliveryStatus,
    val errorMessage: String? = null,
    val attemptCount: Int = 1,
    val nextRetryAt: Instant? = null,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(attemptCount >= 1) { "attemptCount must be at least 1" }
    }

    /**
     * 성공 여부
     */
    fun isSuccess(): Boolean = status == DeliveryStatus.SUCCESS

    /**
     * 최종 실패 여부
     */
    fun isFailed(): Boolean = status == DeliveryStatus.FAILED

    /**
     * 재시도 가능 여부
     */
    fun isRetryable(): Boolean = status.isRetryable()

    /**
     * HTTP 응답이 성공인지 확인 (2xx)
     */
    fun isHttpSuccess(): Boolean = responseStatus != null && responseStatus in 200..299

    /**
     * HTTP 응답이 클라이언트 에러인지 확인 (4xx)
     */
    fun isClientError(): Boolean = responseStatus != null && responseStatus in 400..499

    /**
     * HTTP 응답이 서버 에러인지 확인 (5xx)
     */
    fun isServerError(): Boolean = responseStatus != null && responseStatus in 500..599

    /**
     * 전송 성공으로 마킹
     */
    fun markSuccess(
        responseStatus: Int,
        responseBody: String?,
        responseHeaders: Map<String, String>?,
        latencyMs: Int
    ): WebhookDelivery = copy(
        status = DeliveryStatus.SUCCESS,
        responseStatus = responseStatus,
        responseBody = responseBody?.take(MAX_RESPONSE_BODY_LENGTH),
        responseHeaders = responseHeaders,
        latencyMs = latencyMs,
        errorMessage = null,
        nextRetryAt = null
    )

    /**
     * 재시도 예약
     */
    fun scheduleRetry(
        retryPolicy: RetryPolicy,
        errorMessage: String?,
        responseStatus: Int? = null,
        responseBody: String? = null,
        latencyMs: Int? = null
    ): WebhookDelivery {
        val nextAttempt = attemptCount + 1
        val delay = retryPolicy.calculateDelay(nextAttempt)

        return copy(
            status = if (retryPolicy.canRetry(attemptCount)) DeliveryStatus.RETRYING else DeliveryStatus.FAILED,
            attemptCount = nextAttempt,
            nextRetryAt = if (retryPolicy.canRetry(attemptCount)) Instant.now().plusMillis(delay) else null,
            errorMessage = errorMessage,
            responseStatus = responseStatus,
            responseBody = responseBody?.take(MAX_RESPONSE_BODY_LENGTH),
            latencyMs = latencyMs
        )
    }

    /**
     * 최종 실패로 마킹
     */
    fun markFailed(
        errorMessage: String?,
        responseStatus: Int? = null,
        responseBody: String? = null,
        latencyMs: Int? = null
    ): WebhookDelivery = copy(
        status = DeliveryStatus.FAILED,
        errorMessage = errorMessage,
        responseStatus = responseStatus,
        responseBody = responseBody?.take(MAX_RESPONSE_BODY_LENGTH),
        latencyMs = latencyMs,
        nextRetryAt = null
    )

    /**
     * Circuit Open으로 스킵됨
     */
    fun markCircuitOpen(): WebhookDelivery = copy(
        status = DeliveryStatus.CIRCUIT_OPEN,
        errorMessage = "Circuit breaker is open",
        nextRetryAt = null
    )

    /**
     * Rate Limited로 스킵됨
     */
    fun markRateLimited(): WebhookDelivery = copy(
        status = DeliveryStatus.RATE_LIMITED,
        errorMessage = "Rate limit exceeded",
        nextRetryAt = Instant.now().plusSeconds(60) // 1분 후 재시도
    )

    companion object {
        const val MAX_RESPONSE_BODY_LENGTH = 10_000

        /**
         * 새 전송 기록 생성 (PENDING 상태)
         */
        fun create(
            webhookId: UUID,
            eventType: WebhookEvent,
            eventPayload: Map<String, Any?>,
            requestHeaders: Map<String, String>? = null,
            requestBody: String? = null
        ): WebhookDelivery = WebhookDelivery(
            id = UUID.randomUUID(),
            webhookId = webhookId,
            eventType = eventType,
            eventPayload = eventPayload,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            status = DeliveryStatus.PENDING,
            createdAt = Instant.now()
        )
    }
}
