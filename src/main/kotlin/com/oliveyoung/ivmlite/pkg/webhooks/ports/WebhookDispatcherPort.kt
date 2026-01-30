package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import java.util.UUID

/**
 * Webhook Dispatcher Port
 *
 * 웹훅 HTTP 전송 인터페이스.
 * SOTA급 Circuit Breaker, Rate Limiter, Exponential Backoff 지원.
 */
interface WebhookDispatcherPort {
    /**
     * 웹훅 전송
     *
     * @param webhook 대상 웹훅
     * @param payload 이벤트 페이로드
     * @return 전송 결과
     */
    suspend fun dispatch(webhook: Webhook, payload: WebhookEventPayload): DispatchResult

    /**
     * 테스트 전송 (Circuit Breaker/Rate Limiter 무시)
     */
    suspend fun testDispatch(webhook: Webhook, payload: WebhookEventPayload): DispatchResult

    /**
     * Circuit Breaker 상태 조회
     */
    fun getCircuitState(webhookId: UUID): CircuitState

    /**
     * Circuit Breaker 리셋
     */
    fun resetCircuit(webhookId: UUID)

    /**
     * Rate Limiter 상태 조회
     */
    fun getRateLimitState(webhookId: UUID): RateLimitState
}

/**
 * 전송 결과
 */
sealed class DispatchResult {
    data class Success(
        val statusCode: Int,
        val responseBody: String?,
        val latencyMs: Int
    ) : DispatchResult()

    data class Failed(
        val statusCode: Int?,
        val errorMessage: String,
        val latencyMs: Int,
        val retryable: Boolean
    ) : DispatchResult()

    data object CircuitOpen : DispatchResult()
    data object RateLimited : DispatchResult()
}

/**
 * Circuit Breaker 상태
 */
data class CircuitState(
    val webhookId: UUID,
    val state: State,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Long?
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }
}

/**
 * Rate Limiter 상태
 */
data class RateLimitState(
    val webhookId: UUID,
    val requestsPerSecond: Int,
    val burstCapacity: Int,
    val availableTokens: Int
)
