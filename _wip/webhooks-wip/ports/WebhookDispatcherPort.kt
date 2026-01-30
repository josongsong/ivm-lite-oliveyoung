package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload

/**
 * 웹훅 디스패처 포트
 *
 * 웹훅 HTTP 전송을 담당하는 추상화.
 * SOTA급 구현: Circuit Breaker, Rate Limiter, Exponential Backoff 포함.
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
     * 테스트 전송 (재시도 없이 1회만)
     */
    suspend fun testDispatch(webhook: Webhook, payload: WebhookEventPayload): DispatchResult

    /**
     * 특정 웹훅의 Circuit Breaker 상태 조회
     */
    fun getCircuitState(webhookId: java.util.UUID): CircuitState

    /**
     * Circuit Breaker 강제 리셋
     */
    fun resetCircuit(webhookId: java.util.UUID)

    /**
     * 전체 통계
     */
    fun getDispatcherStats(): DispatcherStats
}

/**
 * 전송 결과
 */
sealed class DispatchResult {
    /**
     * 성공
     */
    data class Success(
        val statusCode: Int,
        val responseBody: String?,
        val responseHeaders: Map<String, String>,
        val latencyMs: Int
    ) : DispatchResult()

    /**
     * 실패 (재시도 가능)
     */
    data class Failure(
        val statusCode: Int?,
        val errorMessage: String,
        val responseBody: String?,
        val latencyMs: Int?,
        val isRetryable: Boolean
    ) : DispatchResult()

    /**
     * Circuit Breaker가 열려있음
     */
    data object CircuitOpen : DispatchResult()

    /**
     * Rate Limit 초과
     */
    data object RateLimited : DispatchResult()

    /**
     * 타임아웃
     */
    data class Timeout(val timeoutMs: Long) : DispatchResult()

    fun isSuccess(): Boolean = this is Success

    fun toStatusCode(): Int? = when (this) {
        is Success -> statusCode
        is Failure -> statusCode
        else -> null
    }

    fun toLatencyMs(): Int? = when (this) {
        is Success -> latencyMs
        is Failure -> latencyMs
        else -> null
    }

    fun toErrorMessage(): String? = when (this) {
        is Failure -> errorMessage
        is CircuitOpen -> "Circuit breaker is open"
        is RateLimited -> "Rate limit exceeded"
        is Timeout -> "Request timeout after ${timeoutMs}ms"
        else -> null
    }
}

/**
 * Circuit Breaker 상태
 */
enum class CircuitState {
    /** 정상 (요청 허용) */
    CLOSED,
    /** 열림 (요청 거부) */
    OPEN,
    /** 반열림 (테스트 요청 허용) */
    HALF_OPEN
}

/**
 * 디스패처 통계
 */
data class DispatcherStats(
    val totalDispatched: Long,
    val successCount: Long,
    val failureCount: Long,
    val circuitOpenCount: Long,
    val rateLimitedCount: Long,
    val timeoutCount: Long,
    val activeCircuitBreakers: Int,
    val openCircuitBreakers: Int
)
