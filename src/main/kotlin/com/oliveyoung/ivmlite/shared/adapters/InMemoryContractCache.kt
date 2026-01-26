package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.config.CacheConfig
import com.oliveyoung.ivmlite.shared.ports.CacheStats
import com.oliveyoung.ivmlite.shared.ports.ContractCachePort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * InMemory Contract Cache (RFC-IMPL-010 Phase C-1)
 *
 * LRU + TTL 기반 캐시 구현.
 * - Thread-safe (Mutex 기반 동기화)
 * - TTL 만료 시 자동 eviction
 * - maxSize 초과 시 LRU eviction
 * - negative caching 금지
 *
 * @param config 캐시 설정
 * @param clock 시간 제공자 (테스트용 주입 가능)
 */
class InMemoryContractCache(
    private val config: CacheConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : ContractCachePort {

    private data class CacheEntry(
        val value: Any,
        val expiresAt: Long,
    )

    // LinkedHashMap: accessOrder=true → LRU 순서 유지
    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > config.maxSize
        }
    }

    private val mutex = Mutex()

    // 통계
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val evictionCount = AtomicLong(0)

    override suspend fun <T : Any> get(key: String, type: KClass<T>): T? {
        if (!config.enabled) {
            missCount.incrementAndGet()
            return null
        }

        return mutex.withLock {
            val entry = cache[key]

            when {
                entry == null -> {
                    missCount.incrementAndGet()
                    null
                }
                entry.expiresAt <= clock() -> {
                    // TTL 만료 → eviction
                    cache.remove(key)
                    evictionCount.incrementAndGet()
                    missCount.incrementAndGet()
                    null
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    try {
                        val casted = type.java.cast(entry.value) as T
                        hitCount.incrementAndGet()
                        casted
                    } catch (_: ClassCastException) {
                        // 타입 불일치 → miss 처리
                        missCount.incrementAndGet()
                        null
                    }
                }
            }
        }
    }

    override suspend fun <T : Any> put(key: String, value: T) {
        if (!config.enabled) return

        val expiresAt = clock() + config.ttlMs
        val entry = CacheEntry(value, expiresAt)

        mutex.withLock {
            val isNewKey = !cache.containsKey(key)
            val willEvict = isNewKey && cache.size >= config.maxSize
            cache[key] = entry
            if (willEvict) {
                evictionCount.incrementAndGet()
            }
        }
    }

    override suspend fun invalidate(key: String) {
        mutex.withLock {
            cache.remove(key)
        }
    }

    override suspend fun invalidateAll() {
        mutex.withLock {
            cache.clear()
        }
    }

    override fun stats(): CacheStats {
        return CacheStats(
            hits = hitCount.get(),
            misses = missCount.get(),
            evictions = evictionCount.get(),
            size = cache.size,
        )
    }

    /**
     * 테스트용: 캐시 엔트리 개수 조회
     */
    fun size(): Int = cache.size

    /**
     * 테스트용: 특정 키가 캐시에 존재하는지 확인 (TTL 무시)
     */
    fun containsKey(key: String): Boolean = cache.containsKey(key)
}
