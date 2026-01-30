package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate Limiter 설정
 */
data class RateLimiterConfig(
    /** 윈도우 당 최대 요청 수 */
    val maxRequests: Int = 100,
    /** 윈도우 크기 (밀리초) */
    val windowMs: Long = 60_000,
    /** 버스트 허용량 (순간 요청) */
    val burstSize: Int = 10
) {
    companion object {
        val DEFAULT = RateLimiterConfig()

        /** 낮은 제한 (분당 30회) */
        val LOW = RateLimiterConfig(maxRequests = 30, windowMs = 60_000, burstSize = 5)

        /** 높은 제한 (분당 500회) */
        val HIGH = RateLimiterConfig(maxRequests = 500, windowMs = 60_000, burstSize = 50)

        /** 초당 제한 */
        val PER_SECOND = RateLimiterConfig(maxRequests = 10, windowMs = 1_000, burstSize = 5)
    }
}

/**
 * Sliding Window Rate Limiter
 *
 * 슬라이딩 윈도우 알고리즘으로 요청 빈도를 제한한다.
 * Token Bucket과 Sliding Window Log의 하이브리드 구현.
 */
class RateLimiter(
    private val config: RateLimiterConfig = RateLimiterConfig.DEFAULT
) {
    private val requestCount = AtomicInteger(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())
    private val burstTokens = AtomicInteger(config.burstSize)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    /**
     * 요청 허용 여부 확인 및 토큰 소비
     *
     * @return true: 허용, false: 거부
     */
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()

        // 윈도우 리셋 체크
        val currentWindowStart = windowStart.get()
        if (now - currentWindowStart >= config.windowMs) {
            // 새 윈도우 시작
            if (windowStart.compareAndSet(currentWindowStart, now)) {
                requestCount.set(0)
            }
        }

        // 버스트 토큰 리필
        refillBurstTokens(now)

        // 먼저 버스트 토큰 사용 시도
        if (burstTokens.getAndDecrement() > 0) {
            return true
        }
        burstTokens.incrementAndGet() // 롤백

        // 일반 요청 카운트 체크
        val currentCount = requestCount.get()
        if (currentCount >= config.maxRequests) {
            return false
        }

        // 요청 카운트 증가 (CAS)
        return requestCount.compareAndSet(currentCount, currentCount + 1)
    }

    /**
     * 버스트 토큰 리필
     */
    private fun refillBurstTokens(now: Long) {
        val lastRefill = lastRefillTime.get()
        val elapsed = now - lastRefill

        // 윈도우 크기의 10%마다 1개씩 리필
        val refillInterval = config.windowMs / 10
        val tokensToAdd = (elapsed / refillInterval).toInt()

        if (tokensToAdd > 0 && lastRefillTime.compareAndSet(lastRefill, now)) {
            val current = burstTokens.get()
            val newValue = minOf(current + tokensToAdd, config.burstSize)
            burstTokens.set(newValue)
        }
    }

    /**
     * 현재 상태
     */
    fun getStatus(): RateLimiterStatus {
        val now = System.currentTimeMillis()
        val elapsed = now - windowStart.get()
        val remaining = maxOf(0, config.maxRequests - requestCount.get())
        val resetIn = if (elapsed >= config.windowMs) 0L else config.windowMs - elapsed

        return RateLimiterStatus(
            remaining = remaining,
            limit = config.maxRequests,
            resetInMs = resetIn,
            burstRemaining = maxOf(0, burstTokens.get())
        )
    }

    /**
     * 강제 리셋
     */
    fun reset() {
        val now = System.currentTimeMillis()
        windowStart.set(now)
        requestCount.set(0)
        burstTokens.set(config.burstSize)
        lastRefillTime.set(now)
    }
}

/**
 * Rate Limiter 상태
 */
data class RateLimiterStatus(
    /** 윈도우 내 남은 요청 수 */
    val remaining: Int,
    /** 윈도우 최대 요청 수 */
    val limit: Int,
    /** 윈도우 리셋까지 남은 시간 (밀리초) */
    val resetInMs: Long,
    /** 버스트 토큰 남은 수 */
    val burstRemaining: Int
) {
    fun toHeaders(): Map<String, String> = mapOf(
        "X-RateLimit-Limit" to limit.toString(),
        "X-RateLimit-Remaining" to remaining.toString(),
        "X-RateLimit-Reset" to (System.currentTimeMillis() + resetInMs).toString()
    )
}
