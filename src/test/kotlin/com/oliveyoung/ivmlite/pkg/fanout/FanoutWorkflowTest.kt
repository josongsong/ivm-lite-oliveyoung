package com.oliveyoung.ivmlite.pkg.fanout

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.IndexSpec
import com.oliveyoung.ivmlite.pkg.contracts.domain.JoinCardinality
import com.oliveyoung.ivmlite.pkg.contracts.domain.JoinSpec as ContractJoinSpec
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceBuildRules
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutResultStatus
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutWorkflow
import com.oliveyoung.ivmlite.pkg.fanout.domain.CircuitBreakerAction
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJobStatus
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutPriority
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * RFC-IMPL-012: FanoutWorkflow 테스트
 *
 * TDD 방식으로 모든 시나리오 커버:
 * 1. 기본 fanout 시나리오
 * 2. 대량 fanout + batching
 * 3. Circuit breaker
 * 4. 빈 fanout / disabled / error handling
 * 5. 의존성 체인 (A→B→C)
 * 6. Edge/Corner cases
 */
class FanoutWorkflowTest : StringSpec({

    val tenantId = TenantId("test-tenant")

    // ==================== 1. 기본 fanout 시나리오 ====================

    "기본 fanout - Brand 업데이트 시 Product 재슬라이싱" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // Product들이 Brand를 참조하는 Inverted Index 세팅
        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
            EntityKey("PRODUCT#test-tenant#P002") to 1L,
            EntityKey("PRODUCT#test-tenant#P003") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.status shouldBe FanoutResultStatus.SUCCESS
        fanoutResult.processedCount shouldBe 3

        // SlicingWorkflow가 3번 호출되었는지 확인
        coVerify(exactly = 3) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "fanout 없음 - 참조하는 downstream 없을 때" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // Inverted Index에 데이터 없음

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test-tenant#BR999"),
            upstreamVersion = 1L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.totalAffected shouldBe 0
        fanoutResult.processedCount shouldBe 0

        // SlicingWorkflow가 호출되지 않아야 함
        coVerify(exactly = 0) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    // ==================== 2. 대량 fanout + batching ====================

    "대량 fanout - 배치 처리 확인" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // 150개 Product가 Brand 참조
        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val products = (1..150).map { i ->
            EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(4, '0')}") to i.toLong()
        }
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, products)

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        // 배치 크기 50으로 설정
        val config = FanoutConfig(
            batchSize = 50,
            batchDelay = 10.milliseconds,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.processedCount shouldBe 150

        // SlicingWorkflow가 150번 호출되었는지 확인
        coVerify(exactly = 150) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "배치 수 계산 테스트" {
        val config = FanoutConfig(batchSize = 100)

        config.calculateBatchCount(0) shouldBe 0
        config.calculateBatchCount(1) shouldBe 1
        config.calculateBatchCount(100) shouldBe 1
        config.calculateBatchCount(101) shouldBe 2
        config.calculateBatchCount(250) shouldBe 3
    }

    // ==================== 3. Circuit Breaker ====================

    "Circuit Breaker - SKIP 모드" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // 15000개 Product (maxFanout 초과)
        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val products = (1..15000).map { i ->
            EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(5, '0')}") to i.toLong()
        }
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, products)

        val config = FanoutConfig(
            maxFanout = 10000,
            circuitBreakerAction = CircuitBreakerAction.SKIP,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.dependencyResults.first().status shouldBe FanoutJobStatus.SKIPPED
        fanoutResult.skippedCount shouldBe 15000

        // SlicingWorkflow가 호출되지 않아야 함
        coVerify(exactly = 0) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "Circuit Breaker - ERROR 모드" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val products = (1..5000).map { i ->
            EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(5, '0')}") to i.toLong()
        }
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, products)

        val config = FanoutConfig(
            maxFanout = 1000,
            circuitBreakerAction = CircuitBreakerAction.ERROR,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.dependencyResults.first().status shouldBe FanoutJobStatus.FAILED
        fanoutResult.failedCount shouldBe 5000
    }

    "Circuit Breaker 트리거 여부 테스트" {
        val config = FanoutConfig(maxFanout = 10000)

        config.shouldTripCircuitBreaker(9999) shouldBe false
        config.shouldTripCircuitBreaker(10000) shouldBe false
        config.shouldTripCircuitBreaker(10001) shouldBe true
    }

    // ==================== 4. 빈 fanout / disabled / error handling ====================

    "Fanout disabled - config.enabled=false" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val config = FanoutConfig(enabled = false)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test-tenant#BR001"),
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.status shouldBe FanoutResultStatus.SKIPPED
        fanoutResult.message shouldBe "Fanout disabled"

        coVerify(exactly = 0) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "중복 제거 (Deduplication) - 같은 엔티티 연속 요청" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val config = FanoutConfig(
            deduplicationWindow = 5.seconds,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // When - 첫 번째 요청
        val result1 = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // 짧은 간격으로 두 번째 요청
        val result2 = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 3L,
        )

        // Then
        result1.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        (result1 as FanoutWorkflow.Result.Ok).value.processedCount shouldBe 1

        result2.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        (result2 as FanoutWorkflow.Result.Ok).value.status shouldBe FanoutResultStatus.SKIPPED
        (result2 as FanoutWorkflow.Result.Ok).value.message shouldBe "Duplicate within deduplication window"

        // SlicingWorkflow가 1번만 호출되어야 함
        coVerify(exactly = 1) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "SlicingWorkflow 실패 시 부분 실패 처리" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
            EntityKey("PRODUCT#test-tenant#P002") to 1L,
            EntityKey("PRODUCT#test-tenant#P003") to 1L,
        ))

        // P002만 실패하도록 설정
        coEvery { slicingWorkflow.execute(tenantId, EntityKey("PRODUCT#test-tenant#P001"), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())
        coEvery { slicingWorkflow.execute(tenantId, EntityKey("PRODUCT#test-tenant#P002"), any(), any()) } returns
            SlicingWorkflow.Result.Err(DomainError.ValidationError("field", "test error"))
        coEvery { slicingWorkflow.execute(tenantId, EntityKey("PRODUCT#test-tenant#P003"), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.status shouldBe FanoutResultStatus.PARTIAL_FAILURE
        fanoutResult.processedCount shouldBe 2
        fanoutResult.failedCount shouldBe 1
    }

    // ==================== 5. 의존성 추론 테스트 ====================

    "의존성 추론 - RuleSet에서 join 관계 파악" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.inferDependencies("brand")

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val dependencies = (result as FanoutWorkflow.Result.Ok).value
        dependencies.shouldHaveSize(1)
        dependencies[0].upstreamEntityType shouldBe "brand"
        dependencies[0].downstreamEntityType shouldBe "product"
        dependencies[0].indexType shouldBe "product_by_brand"
    }

    "의존성 없음 - upstream을 참조하는 downstream 없을 때" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When - 어떤 것도 참조하지 않는 타입
        val result = workflow.inferDependencies("unknown_type")

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val dependencies = (result as FanoutWorkflow.Result.Ok).value
        dependencies.shouldBeEmpty()
    }

    // ==================== 6. Edge/Corner Cases ====================

    "빈 tenantId 처리" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = TenantId(""),
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND##BR001"),
            upstreamVersion = 1L,
        )

        // Then - 빈 tenantId도 처리 가능해야 함
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
    }

    "version 0 처리" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test-tenant#BR001"),
            upstreamVersion = 0L,
        )

        // Then
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
    }

    // ==================== 7. Metrics 테스트 ====================

    "Metrics 수집 확인" {
        // Given
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
            EntityKey("PRODUCT#test-tenant#P002") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // When
        workflow.clearDeduplicationCache()  // 이전 테스트 영향 제거
        workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // Then
        val metrics = workflow.getMetrics()
        (metrics.totalFanoutCount > 0) shouldBe true
        (metrics.successCount > 0) shouldBe true
    }

    // ==================== 8. FanoutConfig 테스트 ====================

    "FanoutConfig 기본값 확인" {
        val config = FanoutConfig.DEFAULT

        config.enabled shouldBe true
        config.batchSize shouldBe 100
        config.maxFanout shouldBe 10_000
        config.circuitBreakerAction shouldBe CircuitBreakerAction.SKIP
        config.priority shouldBe FanoutPriority.NORMAL
    }

    "FanoutConfig HIGH_THROUGHPUT 프리셋" {
        val config = FanoutConfig.HIGH_THROUGHPUT

        config.batchSize shouldBe 500
        config.maxFanout shouldBe 100_000
        config.maxConcurrentFanouts shouldBe 50
    }

    "FanoutConfig CONSERVATIVE 프리셋" {
        val config = FanoutConfig.CONSERVATIVE

        config.batchSize shouldBe 50
        config.maxFanout shouldBe 1_000
        config.circuitBreakerAction shouldBe CircuitBreakerAction.ERROR
    }

    // ==================== 9. Priority 테스트 ====================

    "FanoutPriority weight 확인" {
        FanoutPriority.CRITICAL.weight shouldBe 100
        FanoutPriority.HIGH.weight shouldBe 75
        FanoutPriority.NORMAL.weight shouldBe 50
        FanoutPriority.LOW.weight shouldBe 25
        FanoutPriority.BACKGROUND.weight shouldBe 10
    }

    "FanoutPriority fromWeight 변환" {
        FanoutPriority.fromWeight(100) shouldBe FanoutPriority.CRITICAL
        FanoutPriority.fromWeight(80) shouldBe FanoutPriority.HIGH
        FanoutPriority.fromWeight(50) shouldBe FanoutPriority.NORMAL
        FanoutPriority.fromWeight(30) shouldBe FanoutPriority.LOW
        FanoutPriority.fromWeight(5) shouldBe FanoutPriority.BACKGROUND
    }

    // ==================== 10. RetryConfig 테스트 ====================

    "RetryConfig 지연 계산" {
        val config = com.oliveyoung.ivmlite.pkg.fanout.domain.RetryConfig(
            maxAttempts = 3,
            baseDelay = 1.seconds,
            backoffMultiplier = 2.0,
            maxDelay = 10.seconds,
        )

        config.calculateDelay(0).inWholeMilliseconds shouldBe 0
        config.calculateDelay(1).inWholeMilliseconds shouldBe 1000
        config.calculateDelay(2).inWholeMilliseconds shouldBe 2000
        config.calculateDelay(3).inWholeMilliseconds shouldBe 4000
        config.calculateDelay(10).inWholeMilliseconds shouldBe 10000  // maxDelay 적용
    }
})

// ==================== Helper Functions ====================

private fun createMockContractRegistry(): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULE_SET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "product",
        impactMap = mapOf(
            SliceType.CORE to listOf("/productId", "/name"),
            SliceType.DERIVED to listOf("/description", "/price"),
        ),
        joins = listOf(
            ContractJoinSpec(
                sourceSlice = SliceType.CORE,
                targetEntity = "brand",
                joinPath = "/brandCode",
                cardinality = JoinCardinality.MANY_TO_ONE,
            ),
            ContractJoinSpec(
                sourceSlice = SliceType.CORE,
                targetEntity = "category",
                joinPath = "/categoryCode",
                cardinality = JoinCardinality.MANY_TO_ONE,
            ),
        ),
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = listOf(
                    com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec(
                        name = "brand",
                        type = com.oliveyoung.ivmlite.pkg.slices.domain.JoinType.LOOKUP,
                        sourceFieldPath = "brandCode",
                        targetEntityType = "brand",
                        targetKeyPattern = "BRAND#{tenantId}#{value}",
                        required = false,
                    ),
                ),
            ),
        ),
        indexes = listOf(
            IndexSpec(type = "product_by_brand", selector = "$.brandCode"),
            IndexSpec(type = "product_by_category", selector = "$.categoryCode"),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

/**
 * RFC-IMPL-013: 역방향 인덱스 셋업 헬퍼
 *
 * 역방향 인덱스의 올바른 형식:
 * - refEntityKey: 참조되는 엔티티 (upstream, 예: BRAND)
 * - targetEntityKey: 참조하는 엔티티 (downstream, 예: PRODUCT)
 * - indexValue: FK 값만 (entityId 부분만, 예: "br001")
 *
 * @param indexValue EntityKey 형식 (예: "BRAND#test-tenant#BR001") - entityId만 추출됨
 * @param entities targetEntityKey 목록 (재슬라이싱 대상)
 */
private suspend fun setupInvertedIndex(
    repo: InMemoryInvertedIndexRepository,
    tenantId: TenantId,
    indexType: String,
    indexValue: String,
    entities: List<Pair<EntityKey, Long>>,
) {
    // indexValue에서 entityId 부분만 추출 (RFC-IMPL-013)
    val normalizedIndexValue = extractEntityIdFromKey(indexValue).lowercase()
    // refEntityKey 구성 (upstream 엔티티)
    val refEntityKey = EntityKey(indexValue)

    val entries = entities.map { (targetKey, version) ->
        InvertedIndexEntry(
            tenantId = tenantId,
            refEntityKey = refEntityKey,  // 참조되는 엔티티 (예: BRAND)
            refVersion = VersionLong(0),  // upstream 버전 (조회 시점에 결정)
            targetEntityKey = targetKey,   // 참조하는 엔티티 (예: PRODUCT) - 재슬라이싱 대상
            targetVersion = VersionLong(version),
            indexType = indexType,
            indexValue = normalizedIndexValue,  // entityId만 (예: "br001")
            sliceType = SliceType.CORE,
            sliceHash = "hash_${targetKey.value}",
            tombstone = false,
        )
    }
    repo.putAllIdempotent(entries)
}

/**
 * EntityKey에서 entityId 부분만 추출
 * 형식: {ENTITY_TYPE}#{tenantId}#{entityId}
 */
private fun extractEntityIdFromKey(key: String): String {
    val parts = key.split("#")
    return if (parts.size >= 3) parts[2] else key
}
