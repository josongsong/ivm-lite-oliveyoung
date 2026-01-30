package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.ports.CircuitState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker 설정
 */
data class CircuitBreakerConfig(
    /** 이 횟수만큼 연속 실패하면 OPEN */
    val failureThreshold: Int = 5,
    /** HALF_OPEN 상태에서 이 횟수만큼 성공하면 CLOSED */
    val successThreshold: Int = 3,
    /** OPEN 상태 유지 시간 (밀리초) */
    val cooldownMs: Long = 60_000,
    /** 슬라이딩 윈도우 크기 (밀리초) - 이 시간 내의 실패만 카운트 */
    val windowMs: Long = 120_000
) {
    companion object {
        val DEFAULT = CircuitBreakerConfig()

        val AGGRESSIVE = CircuitBreakerConfig(
            failureThreshold = 3,
            successThreshold = 2,
            cooldownMs = 30_000
        )

        val CONSERVATIVE = CircuitBreakerConfig(
            failureThreshold = 10,
            successThreshold = 5,
            cooldownMs = 120_000
        )
    }
}

/**
 * Circuit Breaker 구현
 *
 * 연속 실패 시 회로를 열어 장애 전파를 방지한다.
 * - CLOSED: 정상 상태, 모든 요청 허용
 * - OPEN: 회로 열림, 모든 요청 거부
 * - HALF_OPEN: 테스트 상태, 일부 요청 허용하여 복구 확인
 */
class CircuitBreaker(
    private val config: CircuitBreakerConfig = CircuitBreakerConfig.DEFAULT
) {
    private val state = AtomicReference(CircuitState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val lastStateChangeTime = AtomicLong(System.currentTimeMillis())

    /**
     * 현재 상태
     */
    fun getState(): CircuitState = state.get()

    /**
     * 요청 허용 여부 확인
     *
     * OPEN 상태에서 쿨다운이 지나면 HALF_OPEN으로 전환
     */
    fun isAllowed(): Boolean {
        return when (state.get()) {
            CircuitState.CLOSED -> true
            CircuitState.HALF_OPEN -> true
            CircuitState.OPEN -> {
                val now = System.currentTimeMillis()
                if (now - lastFailureTime.get() > config.cooldownMs) {
                    // 쿨다운 경과 → HALF_OPEN으로 전환
                    if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                        successCount.set(0)
                        lastStateChangeTime.set(now)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * 성공 기록
     */
    fun recordSuccess() {
        when (state.get()) {
            CircuitState.CLOSED -> {
                // 윈도우 내 실패 카운트 리셋
                failureCount.set(0)
            }
            CircuitState.HALF_OPEN -> {
                val count = successCount.incrementAndGet()
                if (count >= config.successThreshold) {
                    // 충분한 성공 → CLOSED로 복구
                    if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                        failureCount.set(0)
                        successCount.set(0)
                        lastStateChangeTime.set(System.currentTimeMillis())
                    }
                }
            }
            CircuitState.OPEN -> {
                // OPEN 상태에서 성공은 무시 (이론적으로 발생 안 함)
            }
        }
    }

    /**
     * 실패 기록
     */
    fun recordFailure() {
        val now = System.currentTimeMillis()
        lastFailureTime.set(now)

        when (state.get()) {
            CircuitState.CLOSED -> {
                val count = failureCount.incrementAndGet()
                if (count >= config.failureThreshold) {
                    // 임계치 초과 → OPEN으로 전환
                    if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                        lastStateChangeTime.set(now)
                    }
                }
            }
            CircuitState.HALF_OPEN -> {
                // 테스트 실패 → 다시 OPEN으로
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                    successCount.set(0)
                    lastStateChangeTime.set(now)
                }
            }
            CircuitState.OPEN -> {
                // 이미 OPEN
            }
        }
    }

    /**
     * 강제 리셋 (CLOSED로)
     */
    fun reset() {
        state.set(CircuitState.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        lastStateChangeTime.set(System.currentTimeMillis())
    }

    /**
     * 통계
     */
    fun getStats(): CircuitBreakerStats = CircuitBreakerStats(
        state = state.get(),
        failureCount = failureCount.get(),
        successCount = successCount.get(),
        lastFailureTime = lastFailureTime.get().takeIf { it > 0 },
        lastStateChangeTime = lastStateChangeTime.get(),
        config = config
    )
}

/**
 * Circuit Breaker 통계
 */
data class CircuitBreakerStats(
    val state: CircuitState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Long?,
    val lastStateChangeTime: Long,
    val config: CircuitBreakerConfig
)
