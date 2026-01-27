package com.oliveyoung.ivmlite.integration

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
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutResult
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutResultStatus
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutWorkflow
import com.oliveyoung.ivmlite.pkg.fanout.domain.CircuitBreakerAction
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutDependency
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexBuilder
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

/**
 * RFC-IMPL-013: InvertedIndex → Fanout 통합 테스트 (InMemory)
 *
 * NOTE: 이건 실제 DB를 사용하지 않는 통합 테스트입니다.
 * 실제 DynamoDB E2E 테스트는 DynamoDbE2ETest를 참조하세요.
 *
 * ## 테스트 흐름
 * 1. Product 슬라이스 생성 시 InvertedIndexBuilder가 역방향 인덱스 생성
 * 2. Brand 엔티티 변경 시 FanoutWorkflow가 역방향 인덱스를 조회
 * 3. 연관된 Product들이 재슬라이싱됨 (mock)
 *
 * ## 핵심 검증
 * - IndexSpec.references로 정의된 FK 관계 파싱
 * - InvertedIndexBuilder의 역방향 인덱스 생성 로직
 * - FanoutWorkflow의 조회 키 형식
 */
class InvertedIndexFanoutIntegrationTest : StringSpec({

    val tenantId = TenantId("test-tenant")
    val builder = InvertedIndexBuilder()

    // ==================== E2E 시나리오 1: references 기반 역방향 인덱스 생성 및 조회 ====================

    "통합: Product 슬라이싱 → 역방향 인덱스 생성 → Brand 변경 → Product 재슬라이싱" {
        // ===== Step 1: Product 슬라이스 생성 시 역방향 인덱스 생성 =====
        val productSlice = SliceRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#test-tenant#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"productId":"P001","brandId":"BR001","title":"Test Product"}""",
            hash = "hash_p001",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        // references가 있는 IndexSpec → 역방향 인덱스 자동 생성
        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",  // FK 참조 → product_by_brand 역방향 인덱스 생성
                maxFanout = 10000,
            ),
        )

        // InvertedIndexBuilder로 인덱스 생성
        val generatedIndexes = builder.build(productSlice, indexSpecs)

        // 검증: 정방향 + 역방향 = 2개
        generatedIndexes shouldHaveSize 2

        val forwardIndex = generatedIndexes.find { it.indexType == "brand" }!!
        forwardIndex.indexValue shouldBe "br001"

        val reverseIndex = generatedIndexes.find { it.indexType == "product_by_brand" }!!
        reverseIndex.indexValue shouldBe "br001"
        reverseIndex.targetEntityKey shouldBe EntityKey("PRODUCT#test-tenant#P001")
        reverseIndex.refEntityKey shouldBe EntityKey("BRAND#test-tenant#br001")

        // ===== Step 2: Inverted Index Repository에 저장 =====
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(generatedIndexes)

        // ===== Step 3: Brand 변경 시 FanoutWorkflow가 역방향 인덱스 조회 =====
        val contractRegistry = createMockContractRegistryWithReferences()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = FanoutConfig.DEFAULT,
        )

        // Brand 변경 이벤트 발생
        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 4: 검증 - Product가 재슬라이싱되었는지 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.status shouldBe FanoutResultStatus.SUCCESS

        // SlicingWorkflow가 P001에 대해 정확히 1번 호출되었는지 검증 (atLeast 대신 exactly)
        coVerify(exactly = 1) {
            slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P001"),
                version = any(),
                ruleSetRef = any(),
            )
        }
        
        // 다른 엔티티는 호출되지 않았는지 검증
        coVerify(exactly = 0) {
            slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P002"),
                version = any(),
                ruleSetRef = any(),
            )
        }
    }

    "통합: 여러 Product가 동일 Brand 참조 → Brand 변경 시 모두 재슬라이싱" {
        // ===== Step 1: 여러 Product 슬라이스 생성 =====
        val products = listOf(
            SliceRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P001"),
                version = 1L,
                sliceType = SliceType.CORE,
                data = """{"productId":"P001","brandId":"BR001"}""",
                hash = "hash_p001",
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            ),
            SliceRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P002"),
                version = 1L,
                sliceType = SliceType.CORE,
                data = """{"productId":"P002","brandId":"BR001"}""",  // 동일 Brand
                hash = "hash_p002",
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            ),
            SliceRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P003"),
                version = 1L,
                sliceType = SliceType.CORE,
                data = """{"productId":"P003","brandId":"BR001"}""",  // 동일 Brand
                hash = "hash_p003",
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            ),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brandId", references = "BRAND"),
        )

        // 모든 Product에 대해 인덱스 생성
        val allIndexes = products.flatMap { builder.build(it, indexSpecs) }

        // 역방향 인덱스 3개 생성됨
        val reverseIndexes = allIndexes.filter { it.indexType == "product_by_brand" }
        reverseIndexes shouldHaveSize 3

        // ===== Step 2: Repository에 저장 =====
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(allIndexes)

        // ===== Step 3: Fanout 실행 =====
        val contractRegistry = createMockContractRegistryWithReferences()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 4: 검증 - 3개 Product 모두 재슬라이싱 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.totalAffected shouldBe 3

        // 각 Product에 대해 정확히 1번씩 호출되었는지 검증
        listOf("P001", "P002", "P003").forEach { productId ->
            coVerify(exactly = 1) {
                slicingWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = EntityKey("PRODUCT#test-tenant#$productId"),
                    version = any(),
                    ruleSetRef = any(),
                )
            }
        }
        
        // 총 3번 호출되었는지 검증
        coVerify(exactly = 3) {
            slicingWorkflow.execute(any(), any(), any(), any())
        }
    }

    "통합: references 없는 IndexSpec → 역방향 인덱스 없음 → Fanout 없음" {
        // ===== Step 1: references 없이 슬라이스 생성 =====
        val productSlice = SliceRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#test-tenant#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"productId":"P001","tag":"summer"}""",
            hash = "hash_p001",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        // references 없음 → 정방향 인덱스만 생성
        val indexSpecs = listOf(
            IndexSpec(type = "tag", selector = "$.tag"),  // references 없음
        )

        val generatedIndexes = builder.build(productSlice, indexSpecs)

        // 정방향만 1개
        generatedIndexes shouldHaveSize 1
        generatedIndexes[0].indexType shouldBe "tag"

        // ===== Step 2: Repository에 저장 =====
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(generatedIndexes)

        // ===== Step 3: Tag 변경 시 Fanout 시도 =====
        val contractRegistry = createMockContractRegistryWithTagOnly()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        // TAG 엔티티 변경 (references가 없으므로 의존성 없음)
        val tagKey = EntityKey("TAG#test-tenant#summer")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "TAG",
            upstreamEntityKey = tagKey,
            upstreamVersion = 2L,
        )

        // ===== Step 4: 검증 - Fanout 없음 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.totalAffected shouldBe 0

        // SlicingWorkflow 호출 없음 (4개 파라미터: tenantId, entityKey, version, ruleSetRef)
        coVerify(exactly = 0) { slicingWorkflow.execute(any(), any(), any(), any()) }
    }

    "통합: 배열 FK (categoryIds) → 여러 역방향 인덱스 생성" {
        // ===== Step 1: 여러 카테고리를 참조하는 Product =====
        val productSlice = SliceRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#test-tenant#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"productId":"P001","categoryIds":["CAT001","CAT002"]}""",
            hash = "hash_p001",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "category",
                selector = "$.categoryIds[*]",
                references = "CATEGORY",
            ),
        )

        val generatedIndexes = builder.build(productSlice, indexSpecs)

        // 2 값 × (정방향 + 역방향) = 4개
        generatedIndexes shouldHaveSize 4

        val reverseIndexes = generatedIndexes.filter { it.indexType == "product_by_category" }
        reverseIndexes shouldHaveSize 2

        // 각 카테고리에 대한 역방향 인덱스 확인
        val refEntityKeys = reverseIndexes.map { it.refEntityKey.value }.sorted()
        refEntityKeys shouldBe listOf("CATEGORY#test-tenant#cat001", "CATEGORY#test-tenant#cat002")

        // ===== Step 2: Repository에 저장 =====
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(generatedIndexes)

        // ===== Step 3: CAT001 변경 시 P001 재슬라이싱 =====
        val contractRegistry = createMockContractRegistryWithCategory()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        coEvery { slicingWorkflow.execute(any(), any(), any(), any()) } returns
            SlicingWorkflow.Result.Ok(emptyList())

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val categoryKey = EntityKey("CATEGORY#test-tenant#CAT001")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "CATEGORY",
            upstreamEntityKey = categoryKey,
            upstreamVersion = 2L,
        )

        // ===== Step 4: 검증 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.totalAffected shouldBe 1

        // 정확히 1번 호출되었는지 검증
        coVerify(exactly = 1) {
            slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P001"),
                version = any(),
                ruleSetRef = any(),
            )
        }
        
        // 다른 엔티티는 호출되지 않았는지 검증
        coVerify(exactly = 0) {
            slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P002"),
                version = any(),
                ruleSetRef = any(),
            )
        }
    }

    "통합: maxFanout 설정이 IndexSpec에서 FanoutDependency로 전달됨" {
        // indexes만 있는 RuleSet 생성 (joins 없음 → indexes가 유일한 의존성)
        val contractRegistry = createMockContractRegistryWithIndexesOnly()

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = InMemoryInvertedIndexRepository(),
            slicingWorkflow = mockk(relaxed = true),
        )

        // 의존성 추론
        val result = fanoutWorkflow.inferDependencies("BRAND")

        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<List<FanoutDependency>>>()
        val dependencies = (result as FanoutWorkflow.Result.Ok<List<FanoutDependency>>).value

        // indexes에서 references="BRAND" 발견
        dependencies.size shouldBe 1
        dependencies[0].indexType shouldBe "product_by_brand"
        
        // RFC-IMPL-013: maxFanout이 IndexSpec에서 FanoutDependency로 전달되는지 검증
        // createMockContractRegistryWithIndexesOnly()에서 maxFanout = 10000 설정
        dependencies[0].maxFanout shouldBe 10000
    }

    "통합: maxFanout 초과 시 Circuit Breaker 동작 (SKIP)" {
        // ===== Step 1: maxFanout보다 많은 Product 생성 =====
        val maxFanout = 5  // 작은 값으로 설정
        val productCount = 10  // maxFanout 초과
        
        val products = (1..productCount).map { i ->
            SliceRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(3, '0')}"),
                version = 1L,
                sliceType = SliceType.CORE,
                data = """{"productId":"P$i","brandId":"BR001"}""",
                hash = "hash_p$i",
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            )
        }

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
                maxFanout = maxFanout,  // 작은 값
            ),
        )

        val allIndexes = products.flatMap { builder.build(it, indexSpecs) }
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(allIndexes)

        // ===== Step 2: Circuit Breaker 동작 확인 =====
        val contractRegistry = createMockContractRegistryWithMaxFanout(maxFanout)
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = FanoutConfig(
                maxFanout = maxFanout,
                circuitBreakerAction = CircuitBreakerAction.SKIP,  // SKIP 동작
            ),
        )

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 3: 검증 - Circuit Breaker 발동으로 SKIP됨 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        
        // SKIP 상태 확인
        fanoutResult.totalAffected shouldBe productCount
        fanoutResult.processedCount shouldBe 0
        fanoutResult.skippedCount shouldBe productCount
        fanoutResult.failedCount shouldBe 0
        
        // Dependency 결과 확인 (모든 dependency가 SKIPPED면 전체도 SKIPPED)
        fanoutResult.dependencyResults.isNotEmpty() shouldBe true
        val dependencyResult = fanoutResult.dependencyResults.first()
        dependencyResult.status shouldBe com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJobStatus.SKIPPED
        dependencyResult.skippedCount shouldBe productCount
        dependencyResult.processedCount shouldBe 0
        
        // 전체 결과 상태는 SUCCESS (failedCount가 0이면 SUCCESS)
        // SKIPPED는 전체가 0일 때만 사용되므로, 여기서는 SUCCESS
        fanoutResult.status shouldBe FanoutResultStatus.SUCCESS
        
        // SlicingWorkflow는 호출되지 않아야 함 (SKIP)
        coVerify(exactly = 0) {
            slicingWorkflow.execute(any(), any(), any(), any())
        }
    }

    "통합: maxFanout 초과 시 Circuit Breaker 동작 (ERROR)" {
        // ===== Step 1: maxFanout보다 많은 Product 생성 =====
        val maxFanout = 3
        val productCount = 5
        
        val products = (1..productCount).map { i ->
            SliceRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#test-tenant#P${i.toString().padStart(3, '0')}"),
                version = 1L,
                sliceType = SliceType.CORE,
                data = """{"productId":"P$i","brandId":"BR001"}""",
                hash = "hash_p$i",
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            )
        }

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
                maxFanout = maxFanout,
            ),
        )

        val allIndexes = products.flatMap { builder.build(it, indexSpecs) }
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(allIndexes)

        // ===== Step 2: Circuit Breaker ERROR 동작 확인 =====
        val contractRegistry = createMockContractRegistryWithMaxFanout(maxFanout)
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = FanoutConfig(
                maxFanout = maxFanout,
                circuitBreakerAction = CircuitBreakerAction.ERROR,  // ERROR 동작
            ),
        )

        val brandKey = EntityKey("BRAND#test-tenant#BR001")
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",
            upstreamEntityKey = brandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 3: 검증 - Circuit Breaker 발동으로 FAILED됨 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        
        // FAILED 상태 확인
        fanoutResult.totalAffected shouldBe productCount
        fanoutResult.processedCount shouldBe 0
        fanoutResult.failedCount shouldBe productCount
        fanoutResult.skippedCount shouldBe 0
        
        // Dependency 결과 확인
        fanoutResult.dependencyResults.isNotEmpty() shouldBe true
        val dependencyResult = fanoutResult.dependencyResults.first()
        dependencyResult.status shouldBe com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJobStatus.FAILED
        dependencyResult.failedCount shouldBe productCount
        dependencyResult.processedCount shouldBe 0
        
        // 전체 결과 상태는 PARTIAL_FAILURE 또는 FAILED (totalFailed > 0이면 PARTIAL_FAILURE)
        // 모든 dependency가 FAILED면 PARTIAL_FAILURE
        (fanoutResult.status == FanoutResultStatus.FAILED || fanoutResult.status == FanoutResultStatus.PARTIAL_FAILURE) shouldBe true
        
        // SlicingWorkflow는 호출되지 않아야 함 (FAILED)
        coVerify(exactly = 0) {
            slicingWorkflow.execute(any(), any(), any(), any())
        }
    }

    "통합: 빈 결과 케이스 - 존재하지 않는 Brand 변경 시 Fanout 없음" {
        // ===== Step 1: Product는 있지만 다른 Brand 참조 =====
        val productSlice = SliceRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#test-tenant#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"productId":"P001","brandId":"BR001"}""",  // BR001 참조
            hash = "hash_p001",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        val indexes = builder.build(productSlice, indexSpecs)
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(indexes)

        // ===== Step 2: 존재하지 않는 Brand 변경 =====
        val contractRegistry = createMockContractRegistryWithReferences()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val nonExistentBrandKey = EntityKey("BRAND#test-tenant#BR999")  // 존재하지 않는 Brand
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenantId,
            upstreamEntityType = "BRAND",
            upstreamEntityKey = nonExistentBrandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 3: 검증 - 빈 결과 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.totalAffected shouldBe 0
        fanoutResult.processedCount shouldBe 0
        
        // SlicingWorkflow 호출 없음
        coVerify(exactly = 0) {
            slicingWorkflow.execute(any(), any(), any(), any())
        }
    }

    "통합: 여러 tenantId 격리 - 다른 tenant의 Brand 변경 시 영향 없음" {
        // ===== Step 1: tenant1의 Product 생성 =====
        val tenant1 = TenantId("tenant1")
        val tenant2 = TenantId("tenant2")
        
        val productSlice = SliceRecord(
            tenantId = tenant1,
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"productId":"P001","brandId":"BR001"}""",
            hash = "hash_p001",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        val indexes = builder.build(productSlice, indexSpecs)
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        invertedIndexRepo.putAllIdempotent(indexes)

        // ===== Step 2: tenant2의 Brand 변경 (다른 tenant) =====
        val contractRegistry = createMockContractRegistryWithReferences()
        val slicingWorkflow = mockk<SlicingWorkflow>(relaxed = true)

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
        )

        val tenant2BrandKey = EntityKey("BRAND#tenant2#BR001")  // 다른 tenant
        val result = fanoutWorkflow.onEntityChange(
            tenantId = tenant2,  // 다른 tenant
            upstreamEntityType = "BRAND",
            upstreamEntityKey = tenant2BrandKey,
            upstreamVersion = 2L,
        )

        // ===== Step 3: 검증 - tenant 격리로 영향 없음 =====
        result.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<FanoutResult>>()
        val fanoutResult = (result as FanoutWorkflow.Result.Ok<FanoutResult>).value
        fanoutResult.totalAffected shouldBe 0  // 다른 tenant이므로 영향 없음
        
        // SlicingWorkflow 호출 없음
        coVerify(exactly = 0) {
            slicingWorkflow.execute(any(), any(), any(), any())
        }
    }
})

// ==================== Helper Functions ====================

/**
 * RFC-IMPL-013: references 필드가 있는 RuleSet 생성
 */
private fun createMockContractRegistryWithReferences(): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULESET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "PRODUCT",
        impactMap = mapOf(SliceType.CORE to listOf("/brandId", "/title")),
        joins = listOf(
            ContractJoinSpec(
                sourceSlice = SliceType.CORE,
                targetEntity = "BRAND",
                joinPath = "/brandId",
                cardinality = JoinCardinality.MANY_TO_ONE,
            ),
        ),
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = emptyList(),
            ),
        ),
        indexes = listOf(
            // RFC-IMPL-013: references 필드 포함
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
                maxFanout = 10000,
            ),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

private fun createMockContractRegistryWithTagOnly(): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULESET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "PRODUCT",
        impactMap = mapOf(SliceType.CORE to listOf("/tag")),
        joins = emptyList(),
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = emptyList(),
            ),
        ),
        indexes = listOf(
            // references 없음 → 정방향 인덱스만
            IndexSpec(type = "tag", selector = "$.tag"),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

private fun createMockContractRegistryWithIndexesOnly(): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULESET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "PRODUCT",
        impactMap = mapOf(SliceType.CORE to listOf("/brandId")),
        joins = emptyList(),  // joins 없음 → indexes만 사용
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = emptyList(),
            ),
        ),
        indexes = listOf(
            // RFC-IMPL-013: references 필드 포함, maxFanout 명시
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
                maxFanout = 10000,  // 명시적 설정
            ),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

private fun createMockContractRegistryWithMaxFanout(maxFanout: Int): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULESET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "PRODUCT",
        impactMap = mapOf(SliceType.CORE to listOf("/brandId")),
        joins = emptyList(),
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = emptyList(),
            ),
        ),
        indexes = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
                maxFanout = maxFanout,  // 테스트용 작은 값
            ),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

private fun createMockContractRegistryWithCategory(): ContractRegistryPort {
    val registry = mockk<ContractRegistryPort>()

    val ruleSet = RuleSetContract(
        meta = ContractMeta(
            kind = "RULESET",
            id = "ruleset.core.v1",
            version = SemVer.parse("1.0.0"),
            status = ContractStatus.ACTIVE,
        ),
        entityType = "PRODUCT",
        impactMap = mapOf(SliceType.CORE to listOf("/categoryIds")),
        joins = emptyList(),
        slices = listOf(
            SliceDefinition(
                type = SliceType.CORE,
                buildRules = SliceBuildRules.PassThrough(listOf("*")),
                joins = emptyList(),
            ),
        ),
        indexes = listOf(
            IndexSpec(
                type = "category",
                selector = "$.categoryIds[*]",
                references = "CATEGORY",
                maxFanout = 50000,
            ),
        ),
    )

    coEvery { registry.loadRuleSetContract(any()) } returns
        ContractRegistryPort.Result.Ok(ruleSet)

    return registry
}

private inline fun <reified T> Any.shouldBeInstanceOf(): T {
    if (this !is T) {
        throw AssertionError("Expected ${T::class.simpleName} but was ${this::class.simpleName}")
    }
    return this
}
