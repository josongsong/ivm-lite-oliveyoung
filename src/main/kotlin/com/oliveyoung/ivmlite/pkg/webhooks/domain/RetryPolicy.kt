package com.oliveyoung.ivmlite.pkg.webhooks.domain

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Retry Policy (Value Object)
 *
 * 웹훅 전송 재시도 정책.
 * 지수 백오프 + 지터를 적용한 SOTA급 재시도 전략.
 */
data class RetryPolicy(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60_000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(initialDelayMs > 0) { "initialDelayMs must be > 0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be in [0.0, 1.0]" }
    }

    /**
     * 지수 백오프 + 지터를 적용한 다음 재시도까지의 대기 시간 계산
     *
     * @param attempt 현재 시도 횟수 (0-indexed)
     * @return 대기 시간 (밀리초)
     */
    fun calculateDelay(attempt: Int): Long {
        if (attempt >= maxRetries) return 0

        // 지수 백오프: initialDelay * multiplier^attempt
        val exponentialDelay = initialDelayMs * multiplier.pow(attempt.toDouble())

        // 최대 지연 시간 제한
        val cappedDelay = min(exponentialDelay, maxDelayMs.toDouble())

        // 지터 적용: ±jitterFactor 범위의 랜덤 변동
        val jitter = cappedDelay * jitterFactor * Random.nextDouble(-1.0, 1.0)

        return (cappedDelay + jitter).toLong().coerceAtLeast(0)
    }

    /**
     * 재시도 가능 여부 확인
     */
    fun canRetry(currentAttempt: Int): Boolean = currentAttempt < maxRetries

    companion object {
        val DEFAULT = RetryPolicy()
        val AGGRESSIVE = RetryPolicy(maxRetries = 10, initialDelayMs = 500, multiplier = 1.5)
        val CONSERVATIVE = RetryPolicy(maxRetries = 3, initialDelayMs = 2000, multiplier = 3.0)
    }
}
