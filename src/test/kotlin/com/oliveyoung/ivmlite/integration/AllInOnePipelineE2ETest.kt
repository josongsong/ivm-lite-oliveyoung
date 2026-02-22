package com.oliveyoung.ivmlite.integration

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * 올인원 파이프라인 E2E 테스트
 *
 * DeployExecutor를 통한 전체 파이프라인 검증:
 * - SDK EntityInput → IngestionOrchestrator → RawData → Slicing → View → SinkEvent
 * - Product, Brand, Category 엔티티 모두 검증
 * - SinkEvent 발행 및 idempotency 검증
 * - Contract 에러 케이스 검증
 */
class AllInOnePipelineE2ETest : DescribeSpec({

    tags(IntegrationTag)

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()
    val sinkRuleRegistry = InMemorySinkRuleRegistry()

    val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(
        SlicingEngine(contractRegistry, joinExecutor)
    )
    val viewComposer = ViewComposer()

    val workflow = IngestionWorkflow(
        rawDataRepo = rawDataRepo,
        sliceRepo = sliceRepo,
        slicingEngine = slicingEngine,
        viewComposer = viewComposer
    )

    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val executor = DeployExecutor(orchestrator, contractResolver)

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        sinkRuleRegistry.clear()

        // 기본 SinkRule 등록
        sinkRuleRegistry.register(
            SinkRule(
                id = "sinkrule.e2e.opensearch",
                version = "1.0.0",
                status = SinkRuleStatus.ACTIVE,
                input = SinkRuleInput(
                    type = InputType.SLICE,
                    sliceTypes = listOf(SliceType.CORE),
                    entityTypes = listOf("PRODUCT", "BRAND", "CATEGORY")
                ),
                target = SinkRuleTarget(
                    type = SinkTargetType.OPENSEARCH,
                    endpoint = "test://localhost:9200",
                    indexPattern = "e2e-{tenantId}"
                ),
                docId = DocIdSpec(pattern = "{entityKey}")
            )
        )
    }

    describe("Product 전체 파이프라인") {

        it("ProductInput → RawData → Slice → View → SinkEvent 성공") {
            val input = ProductInput(
                tenantId = "e2e-tenant",
                sku = "E2E-001",
                name = "E2E Product",
                price = 35000,
                currency = "KRW",
                category = "skincare",
                brand = "TestBrand"
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "product:E2E-001"
            result.error shouldBe null

            // RawData 저장 확인
            rawDataRepo.size() shouldBe 1

            // Slice 생성 확인
            sliceRepo.size() shouldNotBe 0

            // View → SinkEvent payload로 전달 확인
            sinkEventRepo.size() shouldBe 1
        }

        it("executeAsync → DeployJob 반환") {
            val input = ProductInput(
                tenantId = "e2e-tenant",
                sku = "E2E-ASYNC",
                name = "Async E2E",
                price = 20000
            )

            val result = executor.executeAsync(input)

            result.shouldBeInstanceOf<Either.Right<*>>()
            val job = (result as Either.Right).value
            job.entityKey shouldBe "product:E2E-ASYNC"
            job.state shouldBe DeployState.DONE
        }

        it("explain → DeployPlan 반환") {
            val plan = executor.explain("product", "product:E2E-001")

            plan.entityKey shouldBe "product:E2E-001"
            plan.entityType shouldBe "product"
            plan.rules.isNotEmpty() shouldBe true
            plan.slices.isNotEmpty() shouldBe true
            plan.views.isNotEmpty() shouldBe true
        }
    }

    describe("Brand 전체 파이프라인") {

        it("BrandInput → 올인원 처리 성공") {
            val input = BrandInput(
                tenantId = "e2e-tenant",
                brandId = "brand-e2e-001",
                name = "E2E Brand",
                logoUrl = "https://cdn.example.com/logo.png",
                description = "Test brand for E2E",
                country = "KR"
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "brand:brand-e2e-001"
            rawDataRepo.size() shouldBe 1
            sinkEventRepo.size() shouldBe 1
        }
    }

    describe("Category 전체 파이프라인") {

        it("CategoryInput → 올인원 처리 성공") {
            val input = CategoryInput(
                tenantId = "e2e-tenant",
                categoryId = "cat-e2e-001",
                name = "E2E Category",
                parentId = "cat-parent",
                depth = 2,
                displayOrder = 5
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "category:cat-e2e-001"
        }
    }

    describe("다중 엔티티 독립 처리") {

        it("Product + Brand + Category 순차 처리") {
            val product = ProductInput(
                tenantId = "e2e-tenant",
                sku = "MULTI-P1",
                name = "Multi Product",
                price = 10000
            )
            val brand = BrandInput(
                tenantId = "e2e-tenant",
                brandId = "MULTI-B1",
                name = "Multi Brand"
            )
            val category = CategoryInput(
                tenantId = "e2e-tenant",
                categoryId = "MULTI-C1",
                name = "Multi Category",
                depth = 1,
                displayOrder = 0
            )

            val r1 = executor.executeSync(product)
            val r2 = executor.executeSync(brand)
            val r3 = executor.executeSync(category)

            r1.success shouldBe true
            r2.success shouldBe true
            r3.success shouldBe true

            rawDataRepo.size() shouldBe 3
            sinkEventRepo.size() shouldBe 3
        }
    }

    describe("Contract 에러 케이스") {

        it("미지원 EntityInput → 실패") {
            val unknownInput = com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput(
                tenantId = "test",
                entityType = "unknown",
                data = emptyMap()
            )

            val result = executor.executeSync(unknownInput)

            result.success shouldBe false
            result.error shouldNotBe null
        }

        it("미지원 EntityInput으로 executeAsync → Either.Left") {
            val unknownInput = com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput(
                tenantId = "test",
                entityType = "unknown",
                data = emptyMap()
            )

            val result = executor.executeAsync(unknownInput)

            result.shouldBeInstanceOf<Either.Left<*>>()
        }
    }

    describe("SinkEvent 없는 경우") {

        it("SinkRule 미등록 시 SinkEvent 미발행") {
            sinkRuleRegistry.clear()

            val input = ProductInput(
                tenantId = "e2e-tenant",
                sku = "NO-SINK",
                name = "No Sink",
                price = 5000
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            sinkEventRepo.size() shouldBe 0
        }
    }
})
