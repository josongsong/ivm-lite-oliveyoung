package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.ports.SingleFlightPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM 로컬 single-flight with LRU eviction + TTL.
 *
 * 메모리 누수 방지:
 * - TTL: 5분 후 자동 만료
 * - LRU: 최대 10,000개 제한, 초과 시 오래된 것 삭제
 * - 매 100번째 호출마다 정리 실행 (성능 최적화)
 */
class InProcessSingleFlight(
    private val maxSize: Int = 10_000,
    private val ttlMs: Long = 5 * 60 * 1000L,  // 5분
    private val cleanupInterval: Int = 100
) : SingleFlightPort {

    private class MutexEntry(
        val mutex: Mutex,
        @Volatile var lastAccessedAt: Long = System.currentTimeMillis()
    ) {
        fun touch() {
            lastAccessedAt = System.currentTimeMillis()
        }

        fun isExpired(ttlMs: Long): Boolean =
            System.currentTimeMillis() - lastAccessedAt > ttlMs
    }

    private val locks = ConcurrentHashMap<String, MutexEntry>()
    private val callCounter = AtomicLong(0)
    private val cleanupLock = java.util.concurrent.locks.ReentrantLock()

    override suspend fun <T> run(key: String, block: suspend () -> T): T {
        // 주기적 정리 (논블로킹)
        if (callCounter.incrementAndGet() % cleanupInterval == 0L) {
            tryCleanup()
        }

        val entry = locks.compute(key) { _, existing ->
            if (existing != null && !existing.isExpired(ttlMs)) {
                existing.touch()
                existing
            } else {
                MutexEntry(Mutex())
            }
        }!!

        return entry.mutex.withLock { block() }
    }

    private fun tryCleanup() {
        // 다른 스레드가 정리 중이면 스킵 (경쟁 방지)
        if (!cleanupLock.tryLock()) return
        try {
            cleanup()
        } finally {
            cleanupLock.unlock()
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()

        // 1. TTL 만료 항목 제거
        locks.entries.removeIf { (_, entry) ->
            now - entry.lastAccessedAt > ttlMs
        }

        // 2. LRU 초과 시 오래된 항목 제거
        if (locks.size > maxSize) {
            val toRemove = locks.entries
                .sortedBy { it.value.lastAccessedAt }
                .take(locks.size - maxSize)
                .map { it.key }

            toRemove.forEach { locks.remove(it) }
        }
    }

    // 테스트/모니터링용
    fun size(): Int = locks.size
    fun clear() = locks.clear()
}
