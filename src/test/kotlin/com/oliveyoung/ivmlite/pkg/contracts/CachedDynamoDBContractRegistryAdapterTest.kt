package com.oliveyoung.ivmlite.pkg.contracts

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.adapters.InMemoryContractCache
import com.oliveyoung.ivmlite.shared.config.CacheConfig
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * DynamoDBContractRegistryAdapter + Cache 통합 테스트 (RFC-IMPL-010 Phase C-1)
 *
 * 테스트 항목:
 * 1. 캐시 hit → DynamoDB 호출 0회
 * 2. 캐시 miss → DynamoDB 호출 1회 + 캐시 저장
 * 3. TTL 만료 → 재조회 + 캐시 갱신
 * 4. null/error 결과 캐싱 방지 (negative caching 금지)
 * 5. 캐시 무효화 후 재조회
 */
class CachedDynamoDBContractRegistryAdapterTest : StringSpec({

    val tableName = "test-contract-registry"

    fun attr(value: String): AttributeValue = AttributeValue.builder().s(value).build()

    fun createMockClient(
        responseItem: Map<String, AttributeValue>?,
        callCounter: AtomicLong? = null,
    ): DynamoDbAsyncClient {
        val mockClient = mockk<DynamoDbAsyncClient>()
        every { mockClient.getItem(any<GetItemRequest>()) } answers {
            callCounter?.incrementAndGet()
            CompletableFuture.completedFuture(
                GetItemResponse.builder()
                    .item(responseItem ?: emptyMap())
                    .build(),
            )
        }
        return mockClient
    }

    fun createChangeSetResponseItem(): Map<String, AttributeValue> {
        val dataJson = """{
            "identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"},
            "payload": {"externalizationPolicy": {"thresholdBytes": 50000}},
            "fanout": {"enabled": true}
        }"""
        return mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )
    }

    // ==================== 캐시 hit 테스트 ====================

    "캐시 hit → DynamoDB 호출 0회" {
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(createChangeSetResponseItem(), callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        // 첫 번째 호출 → DynamoDB 조회 + 캐시 저장
        val result1 = adapter.loadChangeSetContract(ref)
        result1.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        callCounter.get() shouldBe 1

        // 두 번째 호출 → 캐시 hit (DynamoDB 호출 없음)
        val result2 = adapter.loadChangeSetContract(ref)
        result2.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        callCounter.get() shouldBe 1 // 여전히 1

        // 세 번째 호출 → 캐시 hit
        val result3 = adapter.loadChangeSetContract(ref)
        result3.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        callCounter.get() shouldBe 1 // 여전히 1

        // 캐시 통계 검증
        cache.stats().hits shouldBe 2
        cache.stats().misses shouldBe 1
    }

    "캐시 miss → DynamoDB 호출 1회 + 캐시 저장" {
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(createChangeSetResponseItem(), callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        callCounter.get() shouldBe 1
        cache.stats().misses shouldBe 1
        cache.size() shouldBe 1
    }

    // ==================== TTL 만료 테스트 ====================

    "TTL 만료 후 → 재조회 + 캐시 갱신" {
        class TestClock {
            private val time = AtomicLong(1000L)
            fun now(): Long = time.get()
            fun advance(ms: Long) = time.addAndGet(ms)
        }

        val clock = TestClock()
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(createChangeSetResponseItem(), callCounter)
        val cache = InMemoryContractCache(CacheConfig(ttlMs = 1000), clock::now)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        // 첫 번째 호출 → DynamoDB 조회
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 1

        // TTL 내 → 캐시 hit
        clock.advance(500)
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 1

        // TTL 만료 → 재조회
        clock.advance(600) // 총 1100ms
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 2
    }

    // ==================== Negative caching 금지 ====================

    "NotFound → 캐싱하지 않음 (negative caching 금지)" {
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(null, callCounter) // 빈 응답 → NotFound
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("not-exists", SemVer.parse("1.0.0"))

        // 첫 번째 호출 → NotFound
        val result1 = adapter.loadChangeSetContract(ref)
        result1.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result1 as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
        callCounter.get() shouldBe 1

        // 두 번째 호출 → 다시 DynamoDB 조회 (캐싱 안됨)
        val result2 = adapter.loadChangeSetContract(ref)
        result2.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        callCounter.get() shouldBe 2

        // 캐시에 저장 안됨
        cache.size() shouldBe 0
    }

    "에러 응답 → 캐싱하지 않음" {
        val callCounter = AtomicLong(0)
        // kind 누락 → ContractError
        val invalidItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            // kind 누락
            "status" to attr("ACTIVE"),
            "data" to attr("{}"),
        )
        val mockClient = createMockClient(invalidItem, callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        // 첫 번째 호출 → ContractError
        val result1 = adapter.loadChangeSetContract(ref)
        result1.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        callCounter.get() shouldBe 1

        // 두 번째 호출 → 다시 DynamoDB 조회
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 2

        // 캐시에 저장 안됨
        cache.size() shouldBe 0
    }

    // ==================== 캐시 무효화 ====================

    "캐시 invalidate 후 → 재조회" {
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(createChangeSetResponseItem(), callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))
        val cacheKey = "CHANGESET:changeset.v1@1.0.0"

        // 첫 번째 호출 → 캐시 저장
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 1

        // 캐시 무효화
        cache.invalidate(cacheKey)

        // 재조회 → DynamoDB 호출
        adapter.loadChangeSetContract(ref)
        callCounter.get() shouldBe 2
    }

    "캐시 invalidateAll 후 → 모든 contract 재조회" {
        val callCounter = AtomicLong(0)

        // JoinSpec 응답 준비
        val joinSpecData = """{
            "constraints": {"maxJoinDepth": 3},
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 5000,
                    "contractRef": {"id": "inverted-index.v1", "version": "1.0.0"}
                }
            }
        }"""
        val joinSpecItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(joinSpecData),
        )

        val mockClient = mockk<DynamoDbAsyncClient>()
        var responseItem: Map<String, AttributeValue> = createChangeSetResponseItem()

        every { mockClient.getItem(any<GetItemRequest>()) } answers {
            callCounter.incrementAndGet()
            val request = firstArg<GetItemRequest>()
            val id = request.key()["id"]?.s()
            val item = if (id == "changeset.v1") createChangeSetResponseItem() else joinSpecItem
            CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build(),
            )
        }

        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)

        // 두 contract 조회 → 캐시 저장
        adapter.loadChangeSetContract(ContractRef("changeset.v1", SemVer.parse("1.0.0")))
        adapter.loadJoinSpecContract(ContractRef("join-spec.v1", SemVer.parse("1.0.0")))
        callCounter.get() shouldBe 2
        cache.size() shouldBe 2

        // invalidateAll
        cache.invalidateAll()
        cache.size() shouldBe 0

        // 재조회 → 모두 DynamoDB 호출
        adapter.loadChangeSetContract(ContractRef("changeset.v1", SemVer.parse("1.0.0")))
        adapter.loadJoinSpecContract(ContractRef("join-spec.v1", SemVer.parse("1.0.0")))
        callCounter.get() shouldBe 4
    }

    // ==================== 캐시 없이 동작 ====================

    "캐시 null → 기존 동작 (매번 DynamoDB 호출)" {
        val callCounter = AtomicLong(0)
        val mockClient = createMockClient(createChangeSetResponseItem(), callCounter)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache = null)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        // 매 호출마다 DynamoDB 조회
        repeat(3) {
            adapter.loadChangeSetContract(ref)
        }

        callCounter.get() shouldBe 3
    }

    // ==================== 다양한 Contract 타입 캐싱 ====================

    "JoinSpecContract 캐싱" {
        val callCounter = AtomicLong(0)
        val dataJson = """{
            "constraints": {"maxJoinDepth": 3},
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 5000,
                    "contractRef": {"id": "inverted-index.v1", "version": "1.0.0"}
                }
            }
        }"""
        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )
        val mockClient = createMockClient(responseItem, callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        // 첫 번째 호출
        val result1 = adapter.loadJoinSpecContract(ref)
        result1.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()

        // 두 번째 호출 → 캐시 hit
        val result2 = adapter.loadJoinSpecContract(ref)
        result2.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()

        callCounter.get() shouldBe 1
        cache.stats().hits shouldBe 1
    }

    "InvertedIndexContract 캐싱" {
        val callCounter = AtomicLong(0)
        val dataJson = """{
            "keySpec": {
                "pkPattern": "INV#{ref_type}#{ref_value}",
                "skPattern": "TARGET#{target_type}#{target_id}"
            }
        }"""
        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )
        val mockClient = createMockClient(responseItem, callCounter)
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        // 첫 번째 호출
        adapter.loadInvertedIndexContract(ref)

        // 두 번째 호출 → 캐시 hit
        adapter.loadInvertedIndexContract(ref)

        callCounter.get() shouldBe 1
    }

    // ==================== 동일 ref 결과 일관성 ====================

    "캐시 hit 결과 == DynamoDB 조회 결과 (결정성)" {
        val mockClient = createMockClient(createChangeSetResponseItem())
        val cache = InMemoryContractCache(CacheConfig())
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result1 = adapter.loadChangeSetContract(ref) as ContractRegistryPort.Result.Ok
        val result2 = adapter.loadChangeSetContract(ref) as ContractRegistryPort.Result.Ok

        result1.value shouldBe result2.value
        result1.value.meta.id shouldBe "changeset.v1"
        result1.value.entityKeyFormat shouldBe "{ENTITY_TYPE}#{tenantId}#{entityId}"
    }
})
