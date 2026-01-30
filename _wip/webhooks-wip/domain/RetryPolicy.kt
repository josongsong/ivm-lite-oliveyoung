package com.oliveyoung.ivmlite.pkg.webhooks.domain

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 재시도 정책 (지수 백오프)
 *
 * @property maxRetries 최대 재시도 횟수
 * @property initialDelayMs 초기 대기 시간 (밀리초)
 * @property maxDelayMs 최대 대기 시간 (밀리초)
 * @property multiplier 백오프 배수
 * @property jitterFactor 지터 팩터 (±10%이면 0.1)
 */
@Serializable
data class RetryPolicy(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60_000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0 and 1" }
    }

    /**
     * n번째 재시도의 대기 시간 계산 (지터 포함)
     *
     * @param attempt 시도 횟수 (0부터 시작)
     * @return 대기 시간 (밀리초)
     */
    fun calculateDelay(attempt: Int): Long {
        if (attempt <= 0) return 0

        // 지수 백오프: initialDelay * (multiplier ^ attempt)
        val baseDelay = initialDelayMs * multiplier.pow(attempt - 1)
        val cappedDelay = min(baseDelay.toLong(), maxDelayMs)

        // 지터 추가: ±jitterFactor 범위의 랜덤 값
        val jitter = (cappedDelay * jitterFactor * Random.nextDouble(-1.0, 1.0)).toLong()

        return (cappedDelay + jitter).coerceIn(0, maxDelayMs)
    }

    /**
     * 재시도 가능 여부
     */
    fun canRetry(currentAttempt: Int): Boolean = currentAttempt < maxRetries

    companion object {
        val DEFAULT = RetryPolicy()

        /** 공격적인 재시도 (짧은 간격, 많은 횟수) */
        val AGGRESSIVE = RetryPolicy(
            maxRetries = 10,
            initialDelayMs = 500,
            maxDelayMs = 30_000,
            multiplier = 1.5
        )

        /** 보수적인 재시도 (긴 간격, 적은 횟수) */
        val CONSERVATIVE = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 5000,
            maxDelayMs = 120_000,
            multiplier = 3.0
        )
    }
}
