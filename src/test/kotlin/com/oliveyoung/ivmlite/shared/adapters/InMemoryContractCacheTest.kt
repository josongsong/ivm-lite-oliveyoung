package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.config.CacheConfig
import com.oliveyoung.ivmlite.shared.ports.ContractCachePort
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

/**
 * InMemoryContractCache SOTA 테스트 (RFC-IMPL-010 Phase C-1)
 *
 * 테스트 항목:
 * 1. 캐시 hit/miss 기본 동작
 * 2. TTL 만료 → 자동 eviction
 * 3. maxSize 초과 → LRU eviction
 * 4. 캐시 비활성화 시 동작
 * 5. invalidate / invalidateAll
 * 6. 통계 (hits, misses, evictions)
 * 7. negative caching 금지
 */
class InMemoryContractCacheTest : StringSpec({

    data class TestContract(val id: String, val value: Int)

    // 테스트용 시계 (조작 가능)
    class TestClock {
        private val time = AtomicLong(1000L)
        fun now(): Long = time.get()
        fun advance(ms: Long) = time.addAndGet(ms)
        fun set(ms: Long) = time.set(ms)
    }

    // ==================== 기본 동작 ====================

    "캐시 miss → null 반환" {
        val cache = InMemoryContractCache(CacheConfig())
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")

        val result = cache.get(key, TestContract::class)

        result shouldBe null
        cache.stats().misses shouldBe 1
        cache.stats().hits shouldBe 0
    }

    "캐시 put → get → hit" {
        val cache = InMemoryContractCache(CacheConfig())
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        val result = cache.get(key, TestContract::class)

        result shouldBe contract
        cache.stats().hits shouldBe 1
        cache.stats().misses shouldBe 0
    }

    "동일 키로 여러 번 get → 모두 hit" {
        val cache = InMemoryContractCache(CacheConfig())
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        repeat(5) {
            cache.get(key, TestContract::class)
        }

        cache.stats().hits shouldBe 5
    }

    // ==================== TTL 테스트 ====================

    "TTL 만료 전 → hit" {
        val clock = TestClock()
        val config = CacheConfig(ttlMs = 1000)
        val cache = InMemoryContractCache(config, clock::now)
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        clock.advance(999) // TTL 직전
        val result = cache.get(key, TestContract::class)

        result shouldBe contract
        cache.stats().hits shouldBe 1
    }

    "TTL 만료 후 → miss + eviction" {
        val clock = TestClock()
        val config = CacheConfig(ttlMs = 1000)
        val cache = InMemoryContractCache(config, clock::now)
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        clock.advance(1001) // TTL 초과
        val result = cache.get(key, TestContract::class)

        result shouldBe null
        cache.stats().misses shouldBe 1
        cache.stats().evictions shouldBe 1
        cache.containsKey(key) shouldBe false
    }

    "TTL 정확히 만료 시점 → miss" {
        val clock = TestClock()
        val config = CacheConfig(ttlMs = 1000)
        val cache = InMemoryContractCache(config, clock::now)
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        clock.advance(1000) // TTL 정확히 만료
        val result = cache.get(key, TestContract::class)

        result shouldBe null // expiresAt <= now 이면 만료
    }

    // ==================== LRU Eviction ====================

    "maxSize 초과 → LRU eviction (오래된 항목 제거)" {
        val config = CacheConfig(maxSize = 3)
        val cache = InMemoryContractCache(config)

        // 3개 저장
        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))
        cache.put("key3", TestContract("3", 3))

        cache.size() shouldBe 3

        // 4번째 저장 → key1 eviction
        cache.put("key4", TestContract("4", 4))

        cache.size() shouldBe 3
        cache.containsKey("key1") shouldBe false
        cache.containsKey("key2") shouldBe true
        cache.containsKey("key3") shouldBe true
        cache.containsKey("key4") shouldBe true
    }

    "LRU: 최근 접근한 항목은 유지" {
        val config = CacheConfig(maxSize = 3)
        val cache = InMemoryContractCache(config)

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))
        cache.put("key3", TestContract("3", 3))

        // key1 접근 → LRU 순서 변경 (key1이 가장 최근)
        cache.get("key1", TestContract::class)

        // key4 저장 → key2 eviction (key1은 최근 접근으로 유지)
        cache.put("key4", TestContract("4", 4))

        cache.containsKey("key1") shouldBe true
        cache.containsKey("key2") shouldBe false
        cache.containsKey("key3") shouldBe true
        cache.containsKey("key4") shouldBe true
    }

    // ==================== 캐시 비활성화 ====================

    "enabled=false → put 무시, get 항상 miss" {
        val config = CacheConfig(enabled = false)
        val cache = InMemoryContractCache(config)
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        val result = cache.get(key, TestContract::class)

        result shouldBe null
        cache.size() shouldBe 0
        cache.stats().misses shouldBe 1
    }

    // ==================== Invalidation ====================

    "invalidate → 특정 키만 제거" {
        val cache = InMemoryContractCache(CacheConfig())

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))

        cache.invalidate("key1")

        cache.containsKey("key1") shouldBe false
        cache.containsKey("key2") shouldBe true
    }

    "invalidateAll → 전체 캐시 제거" {
        val cache = InMemoryContractCache(CacheConfig())

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))
        cache.put("key3", TestContract("3", 3))

        cache.invalidateAll()

        cache.size() shouldBe 0
    }

    // ==================== 통계 ====================

    "stats - hitRate 계산" {
        val cache = InMemoryContractCache(CacheConfig())
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        // 1 miss
        cache.get(key, TestContract::class)
        cache.put(key, contract)

        // 3 hits
        repeat(3) {
            cache.get(key, TestContract::class)
        }

        val stats = cache.stats()
        stats.hits shouldBe 3
        stats.misses shouldBe 1
        stats.hitRate shouldBe 0.75
    }

    "stats - hitRate 0/0 → 0.0" {
        val cache = InMemoryContractCache(CacheConfig())

        cache.stats().hitRate shouldBe 0.0
    }

    // ==================== 키 생성 ====================

    "ContractCachePort.key 형식 검증" {
        val key = ContractCachePort.key("CHANGESET", "changeset.v1", "1.0.0")

        key shouldBe "CHANGESET:changeset.v1@1.0.0"
    }

    // ==================== 타입 안전성 ====================

    "다른 타입으로 get → null (타입 불일치)" {
        data class OtherContract(val name: String)

        val cache = InMemoryContractCache(CacheConfig())
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        val result = cache.get(key, OtherContract::class)

        // UNCHECKED_CAST 때문에 런타임에서 ClassCastException 발생 가능
        // 실제 사용 시에는 올바른 타입으로 호출해야 함
        // 여기서는 as? 때문에 null 반환
        result shouldBe null
    }

    // ==================== 동시성 테스트 ====================

    "동시 접근 시 데이터 무결성 유지" {
        val cache = InMemoryContractCache(CacheConfig(maxSize = 100))

        runBlocking {
            coroutineScope {
                val jobs = (1..100).map { i ->
                    async {
                        val key = "key$i"
                        cache.put(key, TestContract(key, i))
                        cache.get(key, TestContract::class)
                    }
                }
                jobs.awaitAll()
            }
        }

        // 모든 100개 항목이 저장되어야 함
        cache.size() shouldBe 100
        cache.stats().hits shouldBe 100
    }

    // ==================== 경계값 테스트 ====================

    "maxSize=1 → 항상 최신 항목만 유지" {
        val config = CacheConfig(maxSize = 1)
        val cache = InMemoryContractCache(config)

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))

        cache.size() shouldBe 1
        cache.containsKey("key1") shouldBe false
        cache.containsKey("key2") shouldBe true
    }

    "ttlMs=0 → 즉시 만료" {
        val clock = TestClock()
        val config = CacheConfig(ttlMs = 0)
        val cache = InMemoryContractCache(config, clock::now)
        val key = ContractCachePort.key("TEST", "test.v1", "1.0.0")
        val contract = TestContract("test.v1", 42)

        cache.put(key, contract)
        // 시간 변화 없이도 만료
        val result = cache.get(key, TestContract::class)

        result shouldBe null
    }

    // ==================== 추가 검증 테스트 ====================

    "LRU eviction 시 evictionCount 정확히 증가" {
        val config = CacheConfig(maxSize = 2)
        val cache = InMemoryContractCache(config)

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))
        cache.stats().evictions shouldBe 0

        // 3번째 put → 1회 eviction
        cache.put("key3", TestContract("3", 3))
        cache.stats().evictions shouldBe 1

        // 4번째 put → 2회 eviction
        cache.put("key4", TestContract("4", 4))
        cache.stats().evictions shouldBe 2
    }

    "같은 키 덮어쓰기 → eviction 미발생" {
        val config = CacheConfig(maxSize = 2)
        val cache = InMemoryContractCache(config)

        cache.put("key1", TestContract("1", 1))
        cache.put("key2", TestContract("2", 2))

        // 같은 키로 덮어쓰기 → eviction 아님
        cache.put("key1", TestContract("1-updated", 100))

        cache.size() shouldBe 2
        cache.stats().evictions shouldBe 0
        cache.get("key1", TestContract::class)?.value shouldBe 100
    }

    "타입 불일치 → miss 카운트 증가" {
        data class OtherContract(val name: String)

        val cache = InMemoryContractCache(CacheConfig())
        val key = "type-mismatch-key"

        cache.put(key, TestContract("test", 42))

        // 잘못된 타입으로 조회
        cache.get(key, OtherContract::class)

        cache.stats().hits shouldBe 0
        cache.stats().misses shouldBe 1
    }

    "동시 접근 - 같은 키에 대한 경쟁 조건" {
        val cache = InMemoryContractCache(CacheConfig(maxSize = 100))
        val key = "concurrent-key"

        runBlocking {
            coroutineScope {
                // 100개 코루틴이 같은 키에 동시 쓰기
                val writeJobs = (1..100).map { i ->
                    async {
                        cache.put(key, TestContract(key, i))
                    }
                }
                writeJobs.awaitAll()
            }

            coroutineScope {
                // 100개 코루틴이 같은 키에 동시 읽기
                val readJobs = (1..100).map {
                    async {
                        cache.get(key, TestContract::class)
                    }
                }
                val results: List<TestContract?> = readJobs.awaitAll()

                // 모든 읽기가 동일한 값 반환
                val first = results.first()
                results.forEach { r -> r shouldBe first }
            }
        }

        cache.size() shouldBe 1
        cache.stats().hits shouldBe 100
    }
})
