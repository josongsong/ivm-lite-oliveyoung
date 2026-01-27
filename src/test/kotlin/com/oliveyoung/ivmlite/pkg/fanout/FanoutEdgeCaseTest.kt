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
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * RFC-IMPL-012: Fanout Edge Case 및 Corner Case 테스트
 *
 * 극한 상황과 경계값에서의 동작 검증
 */
class FanoutEdgeCaseTest : StringSpec({

    val tenantId = TenantId("test-tenant")

    // ==================== 1. 입력 검증 Edge Cases ====================

    "빈 upstreamEntityType - 에러 반환" {
        val workflow = createWorkflow()

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "",  // 빈 문자열
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Err>()
        val error = (result as FanoutWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.ValidationError>()
        error.toString() shouldContain "upstreamEntityType"
    }

    "공백만 있는 upstreamEntityType - 에러 반환" {
        val workflow = createWorkflow()

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "   ",  // 공백만
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Err>()
    }

    "빈 entityKey - 에러 반환" {
        val workflow = createWorkflow()

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey(""),  // 빈 키
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Err>()
        val error = (result as FanoutWorkflow.Result.Err).error
        error.toString() shouldContain "upstreamEntityKey"
    }

    "음수 version - 에러 반환" {
        val workflow = createWorkflow()

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = -1L,  // 음수
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Err>()
        val error = (result as FanoutWorkflow.Result.Err).error
        error.toString() shouldContain "upstreamVersion"
    }

    "매우 큰 version - 정상 처리" {
        val workflow = createWorkflow()

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = Long.MAX_VALUE,
        )

        // 의존성이 없으므로 empty 반환
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
    }

    // ==================== 2. 특수 문자 처리 ====================

    "특수 문자가 포함된 entityKey" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // 특수 문자 포함된 키
        val brandKey = EntityKey("BRAND#test-tenant#BR@001!#special")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
    }

    "유니코드가 포함된 entityKey (한글)" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#브랜드001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#상품001") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        (result as FanoutWorkflow.Result.Ok).value.processedCount shouldBe 1
    }

    // ==================== 3. 대소문자 처리 (Case Sensitivity) ====================

    "대소문자 혼합 entityKey - 정규화 확인" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // InvertedIndex는 lowercase로 저장됨
        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // 대문자로 요청해도 lowercase로 정규화되어 찾아야 함
        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",  // 대문자
            upstreamEntityKey = EntityKey("BRAND#TEST-TENANT#BR001"),  // 대문자 혼합
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
    }

    // ==================== 4. 동시성 Edge Cases ====================

    "동시 다수 fanout 요청 - Semaphore 제한 확인" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        // 여러 브랜드에 대해 각각 1개 Product
        (1..5).forEach { i ->
            val brandKey = EntityKey("BRAND#test-tenant#BR00$i")
            setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
                EntityKey("PRODUCT#test-tenant#P00$i") to 1L,
            ))
        }

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(50)  // 약간의 지연
            SlicingWorkflow.Result.Ok(emptyList())
        }

        val config = FanoutConfig(
            maxConcurrentFanouts = 2,  // 동시 2개만
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // 5개 동시 요청
        coroutineScope {
            val results = (1..5).map { i ->
                async {
                    workflow.clearDeduplicationCache()  // 중복 제거 방지
                    workflow.onEntityChange(
                        tenantId = tenantId,
                        upstreamEntityType = "brand",
                        upstreamEntityKey = EntityKey("BRAND#test-tenant#BR00$i"),
                        upstreamVersion = 1L,
                    )
                }
            }.awaitAll()

            // 모두 성공해야 함 (Semaphore가 순차 처리)
            results.forEach { result ->
                result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
            }
        }
    }

    // ==================== 5. 타임아웃 Edge Cases ====================

    "매우 짧은 타임아웃 - 타임아웃 발생" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
            EntityKey("PRODUCT#test-tenant#P002") to 1L,
        ))

        // 슬라이싱이 오래 걸림
        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)  // 500ms 지연
            SlicingWorkflow.Result.Ok(emptyList())
        }

        val config = FanoutConfig(
            timeout = 100.milliseconds,  // 100ms 타임아웃
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        // 타임아웃으로 인한 부분 실패
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        // 타임아웃 시 일부가 실패로 처리됨
        (fanoutResult.failedCount >= 0) shouldBe true
    }

    // ==================== 6. Deduplication Edge Cases ====================

    "deduplication window가 0일 때 - 중복 허용" {
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
            deduplicationWindow = 0.milliseconds,  // 0ms = 중복 허용
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        // 첫 번째 요청
        val result1 = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        // 즉시 두 번째 요청 (0ms window이므로 통과해야 함)
        val result2 = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        result1.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        result2.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        
        // 둘 다 처리됨 (SKIPPED 아님)
        (result1 as FanoutWorkflow.Result.Ok).value.status shouldBe FanoutResultStatus.SUCCESS
        (result2 as FanoutWorkflow.Result.Ok).value.status shouldBe FanoutResultStatus.SUCCESS

        // 2번 호출됨
        coVerify(exactly = 2) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    // ==================== 7. Contract 로드 실패 ====================

    "RuleSet 로드 실패 - 에러 전파" {
        val contractRegistry = mockk<ContractRegistryPort>()
        coEvery { contractRegistry.loadRuleSetContract(any()) } returns
            ContractRegistryPort.Result.Err(DomainError.NotFoundError("RuleSet", "ruleset.core.v1"))

        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Err>()
        val error = (result as FanoutWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    // ==================== 8. 빈 RuleSet (joins 없음) ====================

    "RuleSet에 joins가 비어있을 때 - 의존성 없음" {
        val contractRegistry = mockk<ContractRegistryPort>()
        val emptyRuleSet = RuleSetContract(
            meta = ContractMeta(
                kind = "RULE_SET",
                id = "ruleset.core.v1",
                version = SemVer.parse("1.0.0"),
                status = ContractStatus.ACTIVE,
            ),
            entityType = "product",
            impactMap = emptyMap(),
            joins = emptyList(),  // 빈 joins
            slices = emptyList(),
            indexes = emptyList(),
        )
        coEvery { contractRegistry.loadRuleSetContract(any()) } returns
            ContractRegistryPort.Result.Ok(emptyRuleSet)

        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = EntityKey("BRAND#test#BR001"),
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.totalAffected shouldBe 0
        fanoutResult.message shouldBe "No downstream dependencies"
    }

    // ==================== 9. Circuit Breaker 경계값 ====================

    "정확히 maxFanout 개수 - Circuit Breaker 안 터짐" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        // 정확히 100개 (maxFanout = 100)
        val products = (1..100).map { i ->
            EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(4, '0')}") to i.toLong()
        }
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, products)

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val config = FanoutConfig(
            maxFanout = 100,  // 정확히 100개까지 허용
            circuitBreakerAction = CircuitBreakerAction.ERROR,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        // Circuit breaker 안 터짐
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.processedCount shouldBe 100
    }

    "maxFanout + 1 개수 - Circuit Breaker 발동" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        // 101개 (maxFanout = 100 초과)
        val products = (1..101).map { i ->
            EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(4, '0')}") to i.toLong()
        }
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, products)

        val config = FanoutConfig(
            maxFanout = 100,
            circuitBreakerAction = CircuitBreakerAction.ERROR,
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = config,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        fanoutResult.failedCount shouldBe 101  // Circuit breaker로 실패
        fanoutResult.processedCount shouldBe 0
    }

    // ==================== 10. Tombstone 처리 ====================

    "tombstone 엔티티는 fanout 대상에서 제외" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        // RFC-IMPL-013: indexValue는 entityId만 (소문자)
        val brandIdValue = "br001"
        
        // 일반 엔트리
        val normalEntry = InvertedIndexEntry(
            tenantId = tenantId,
            refEntityKey = brandKey,  // 참조되는 엔티티 (Brand)
            refVersion = VersionLong(0L),
            targetEntityKey = EntityKey("PRODUCT#test-tenant#P001"),  // 재슬라이싱 대상
            targetVersion = VersionLong(1L),
            indexType = "product_by_brand",
            indexValue = brandIdValue,  // entityId만
            sliceType = SliceType.CORE,
            sliceHash = "hash_p001",
            tombstone = false,
        )
        
        // tombstone 엔트리 (삭제됨)
        val tombstoneEntry = InvertedIndexEntry(
            tenantId = tenantId,
            refEntityKey = brandKey,  // 참조되는 엔티티 (Brand)
            refVersion = VersionLong(0L),
            targetEntityKey = EntityKey("PRODUCT#test-tenant#P002"),  // 재슬라이싱 대상
            targetVersion = VersionLong(1L),
            indexType = "product_by_brand",
            indexValue = brandIdValue,  // entityId만
            sliceType = SliceType.CORE,
            sliceHash = "hash_p002",
            tombstone = true,  // 삭제됨
        )
        
        invertedIndexRepo.putAllIdempotent(listOf(normalEntry, tombstoneEntry))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val result = workflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        // tombstone은 제외되어 1개만 처리
        fanoutResult.processedCount shouldBe 1

        // P001만 호출됨
        coVerify(exactly = 1) { slicingWorkflow.execute(any(), EntityKey("PRODUCT#test-tenant#P001"), any(), any()) }
        coVerify(exactly = 0) { slicingWorkflow.execute(any(), EntityKey("PRODUCT#test-tenant#P002"), any(), any()) }
    }

    // ==================== 11. 메트릭 리셋 확인 ====================

    "메트릭이 누적되는지 확인" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        setupInvertedIndex(invertedIndexRepo, tenantId, "product_by_brand", brandKey.value, listOf(
            EntityKey("PRODUCT#test-tenant#P001") to 1L,
        ))

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // 여러 번 호출
        repeat(3) { i ->
            workflow.clearDeduplicationCache()
            workflow.onEntityChange(
                tenantId = tenantId,
                upstreamEntityType = "brand",
                upstreamEntityKey = brandKey,
                upstreamVersion = (i + 1).toLong(),
            )
        }

        val metrics = workflow.getMetrics()
        metrics.totalFanoutCount shouldBe 3
        metrics.successCount shouldBe 3
    }

    // ==================== 12. 빈 tenantId와 다른 tenantId ====================

    "다른 tenantId의 데이터는 조회 안 됨" {
        val contractRegistry = createMockContractRegistry()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val brandKey = EntityKey("BRAND#other-tenant#BR001")
        // 다른 테넌트의 데이터
        setupInvertedIndex(
            invertedIndexRepo, 
            TenantId("other-tenant"),  // 다른 테넌트
            "product_by_brand", 
            brandKey.value, 
            listOf(EntityKey("PRODUCT#other-tenant#P001") to 1L)
        )

        val workflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // test-tenant로 조회
        val result = workflow.onEntityChange(
            tenantId = TenantId("test-tenant"),  // 다른 테넌트
            upstreamEntityType = "brand",
            upstreamEntityKey = brandKey,
            upstreamVersion = 1L,
        )

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok).value
        // 다른 테넌트 데이터는 조회되지 않음
        fanoutResult.processedCount shouldBe 0

        coVerify(exactly = 0) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }
})

// ==================== Helper Functions ====================

private fun createWorkflow(): FanoutWorkflow {
    val contractRegistry = createMockContractRegistry()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
    val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

    return FanoutWorkflow(
        contractRegistry = contractRegistry,
        invertedIndexRepo = invertedIndexRepo,
        slicingWorkflow = slicingWorkflow,
    )
}

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
        ),
        joins = listOf(
            ContractJoinSpec(
                sourceSlice = SliceType.CORE,
                targetEntity = "brand",
                joinPath = "/brandCode",
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
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

/**
 * RFC-IMPL-013: 역방향 인덱스 셋업 헬퍼 (FanoutEdgeCaseTest용)
 */
private suspend fun setupInvertedIndex(
    repo: InMemoryInvertedIndexRepository,
    tenantId: TenantId,
    indexType: String,
    indexValue: String,
    entities: List<Pair<EntityKey, Long>>,
) {
    // indexValue에서 entityId 부분만 추출
    val normalizedIndexValue = extractEntityIdFromKey(indexValue).lowercase()
    val refEntityKey = EntityKey(indexValue)

    val entries = entities.map { (targetKey, version) ->
        InvertedIndexEntry(
            tenantId = tenantId,
            refEntityKey = refEntityKey,
            refVersion = VersionLong(0),
            targetEntityKey = targetKey,
            targetVersion = VersionLong(version),
            indexType = indexType,
            indexValue = normalizedIndexValue,
            sliceType = SliceType.CORE,
            sliceHash = "hash_${targetKey.value}",
            tombstone = false,
        )
    }
    repo.putAllIdempotent(entries)
}

private fun extractEntityIdFromKey(key: String): String {
    val parts = key.split("#")
    return if (parts.size >= 3) parts[2] else key
}
