package com.oliveyoung.ivmlite.sdk.execution

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * DeployExecutor 에러 경로 테스트
 *
 * 커버리지 대상:
 * - 미지원 EntityInput 타입
 * - ContractResolver 실패 (존재하지 않는 entityType)
 * - executeAsync 실패 시 Either.Left 반환
 * - ingestOnly 실패 경로
 * - explain 메서드
 */
class DeployExecutorErrorTest : DescribeSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()

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
        sinkRuleRegistry = InMemorySinkRuleRegistry()
    )

    val contractResolver = EntityContractResolver(contractRegistry)
    val executor = DeployExecutor(orchestrator, contractResolver)

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("executeSync 에러 경로") {

        it("미지원 EntityInput 타입 시 실패 반환") {
            val unknownInput = GenericEntityInput(
                tenantId = "test",
                entityType = "unknown_entity",
                data = emptyMap()
            )

            val result = executor.executeSync(unknownInput)

            result.success shouldBe false
            result.entityKey shouldBe "unknown"
            result.error shouldNotBe null
        }

        it("존재하지 않는 entityType으로 ContractResolver 실패") {
            // product/brand/category 외의 entityType을 가진 ProductInput으로는 테스트 불가
            // → 미지원 EntityInput 타입 테스트로 커버
            val badInput = GenericEntityInput(
                tenantId = "test",
                entityType = "nonexistent_type",
                data = emptyMap()
            )

            val result = executor.executeSync(badInput)

            result.success shouldBe false
            result.error shouldNotBe null
        }

        it("ProductInput with optional fields") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-FULL",
                name = "Full Product",
                price = 50000,
                currency = "USD",
                category = "electronics",
                brand = "TestBrand",
                attributes = mapOf(
                    "color" to "red",
                    "weight" to 1.5,
                    "active" to true,
                    "count" to 10
                )
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-FULL"
        }
    }

    describe("executeAsync 에러 경로") {

        it("미지원 EntityInput 시 Either.Left 반환") {
            val unknownInput = GenericEntityInput(
                tenantId = "test",
                entityType = "bad_type",
                data = emptyMap()
            )

            val result = executor.executeAsync(unknownInput)

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
            val error = (result as Either.Left).value
            error.shouldBeInstanceOf<DomainError.StorageError>()
        }

        it("성공 시 DeployJob의 jobId 형식 검증") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-JOB",
                name = "Job Test",
                price = 10000
            )

            val result = executor.executeAsync(input)

            result.shouldBeInstanceOf<Either.Right<*>>()
            val job = (result as Either.Right).value
            job.jobId shouldContain "deploy-"
            job.state shouldBe DeployState.DONE
        }
    }

    describe("ingestOnly 에러 경로") {

        it("미지원 EntityInput 시 실패 IngestResult 반환") {
            val unknownInput = GenericEntityInput(
                tenantId = "test",
                entityType = "nope",
                data = emptyMap()
            )

            val result = executor.ingestOnly(unknownInput)

            result.success shouldBe false
            result.entityKey shouldBe "unknown"
            result.error shouldNotBe null
        }

        it("정상 ingestOnly 후 version 유효성 검증") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-VER",
                name = "Version Test",
                price = 3000
            )

            val result = executor.ingestOnly(input)

            result.success shouldBe true
            result.version shouldNotBe 0L
        }
    }

    describe("explain") {

        it("product entityType에 대한 DeployPlan 반환") {
            val plan = executor.explain("product", "product:SKU-001")

            plan.entityKey shouldBe "product:SKU-001"
            plan.entityType shouldBe "product"
            plan.rules.size shouldBe 1
        }

        it("brand entityType에 대한 DeployPlan 반환") {
            val plan = executor.explain("brand", "brand:B001")

            plan.entityKey shouldBe "brand:B001"
            plan.entityType shouldBe "brand"
        }

        it("존재하지 않는 entityType에 대한 explain은 unknown 반환") {
            val plan = executor.explain("nonexistent", "nonexistent:X")

            plan.entityKey shouldBe "nonexistent:X"
            plan.entityType shouldBe "nonexistent"
            plan.rules shouldBe listOf("unknown")
            plan.slices shouldBe emptyList()
            plan.views shouldBe emptyList()
        }
    }
})
