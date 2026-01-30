package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate Limiter (Token Bucket)
 *
 * 외부 서비스 보호를 위한 Rate Limiter.
 * Token Bucket 알고리즘 + Burst 지원.
 */
class RateLimiter(private val config: Config = Config()) {

    data class Config(
        val requestsPerSecond: Int = 10,
        val burstCapacity: Int = 20
    ) {
        init {
            require(requestsPerSecond > 0) { "requestsPerSecond must be > 0" }
            require(burstCapacity >= requestsPerSecond) { "burstCapacity must be >= requestsPerSecond" }
        }
    }

    private val tokens = AtomicInteger(config.burstCapacity)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    /**
     * 토큰 획득 시도
     *
     * @return true if token acquired, false if rate limited
     */
    fun tryAcquire(): Boolean {
        refillTokens()

        while (true) {
            val current = tokens.get()
            if (current <= 0) return false

            if (tokens.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }

    /**
     * 현재 사용 가능한 토큰 수
     */
    fun availableTokens(): Int {
        refillTokens()
        return tokens.get()
    }

    /**
     * 토큰 리필 (시간 경과에 따라)
     */
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val lastRefill = lastRefillTime.get()
        val elapsedMs = now - lastRefill

        if (elapsedMs <= 0) return

        // 경과 시간에 비례하여 토큰 추가
        val tokensToAdd = (elapsedMs * config.requestsPerSecond / 1000).toInt()
        if (tokensToAdd <= 0) return

        if (lastRefillTime.compareAndSet(lastRefill, now)) {
            while (true) {
                val current = tokens.get()
                val newTokens = minOf(current + tokensToAdd, config.burstCapacity)
                if (tokens.compareAndSet(current, newTokens)) {
                    break
                }
            }
        }
    }

    /**
     * Rate Limiter 리셋
     */
    fun reset() {
        tokens.set(config.burstCapacity)
        lastRefillTime.set(System.currentTimeMillis())
    }

    /**
     * 설정 조회
     */
    fun getConfig(): Config = config
}
