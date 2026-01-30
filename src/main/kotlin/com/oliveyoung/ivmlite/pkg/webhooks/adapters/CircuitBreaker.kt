package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker
 *
 * 장애 전파 차단을 위한 Circuit Breaker 패턴 구현.
 *
 * 상태 전이:
 * - CLOSED → OPEN: 연속 실패 횟수가 failureThreshold 도달
 * - OPEN → HALF_OPEN: cooldownMs 경과 후
 * - HALF_OPEN → CLOSED: 연속 성공 횟수가 successThreshold 도달
 * - HALF_OPEN → OPEN: 실패 발생
 */
class CircuitBreaker(private val config: Config = Config()) {

    enum class State { CLOSED, OPEN, HALF_OPEN }

    data class Config(
        val failureThreshold: Int = 5,
        val successThreshold: Int = 3,
        val cooldownMs: Long = 30_000
    )

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)

    /**
     * Circuit이 열려있는지 확인 (요청 차단 여부)
     */
    fun isOpen(): Boolean {
        when (state.get()) {
            State.OPEN -> {
                // 쿨다운 경과 시 HALF_OPEN으로 전환
                if (System.currentTimeMillis() - lastFailureTime.get() > config.cooldownMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0)
                    }
                    return false
                }
                return true
            }
            else -> return false
        }
    }

    /**
     * 성공 기록
     */
    fun recordSuccess() {
        when (state.get()) {
            State.HALF_OPEN -> {
                if (successCount.incrementAndGet() >= config.successThreshold) {
                    state.set(State.CLOSED)
                    failureCount.set(0)
                    successCount.set(0)
                }
            }
            State.CLOSED -> {
                failureCount.set(0)
            }
            else -> { }
        }
    }

    /**
     * 실패 기록
     */
    fun recordFailure() {
        lastFailureTime.set(System.currentTimeMillis())

        when (state.get()) {
            State.HALF_OPEN -> {
                state.set(State.OPEN)
                failureCount.set(config.failureThreshold)
            }
            State.CLOSED -> {
                if (failureCount.incrementAndGet() >= config.failureThreshold) {
                    state.set(State.OPEN)
                }
            }
            else -> { }
        }
    }

    /**
     * Circuit 상태 리셋
     */
    fun reset() {
        state.set(State.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        lastFailureTime.set(0)
    }

    /**
     * 현재 상태 조회
     */
    fun getState(): State = state.get()

    /**
     * 실패 횟수 조회
     */
    fun getFailureCount(): Int = failureCount.get()

    /**
     * 성공 횟수 조회 (HALF_OPEN 상태에서만 의미있음)
     */
    fun getSuccessCount(): Int = successCount.get()

    /**
     * 마지막 실패 시간 조회
     */
    fun getLastFailureTime(): Long? =
        lastFailureTime.get().takeIf { it > 0 }
}
