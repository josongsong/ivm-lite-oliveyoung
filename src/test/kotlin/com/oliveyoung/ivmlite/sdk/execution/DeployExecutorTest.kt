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
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DeployExecutorTest : DescribeSpec({

    tags(com.oliveyoung.ivmlite.integration.IntegrationTag)

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
        sinkRuleRegistry = InMemorySinkRuleRegistry(),
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val contractResolver = EntityContractResolver(contractRegistry)
    val executor = DeployExecutor(orchestrator, contractResolver)

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("executeSync") {

        it("ProductInput 처리 성공") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-001",
                name = "Test Product",
                price = 29000,
                category = "skincare"
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-001"
            result.error shouldBe null
        }

        it("BrandInput 처리 성공") {
            val input = BrandInput(
                tenantId = "test-tenant",
                brandId = "brand-001",
                name = "Test Brand"
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "brand:brand-001"
        }

        it("CategoryInput 처리 성공") {
            val input = CategoryInput(
                tenantId = "test-tenant",
                categoryId = "cat-001",
                name = "Test Category",
                depth = 1,
                displayOrder = 0
            )

            val result = executor.executeSync(input)

            result.success shouldBe true
            result.entityKey shouldBe "category:cat-001"
        }

        it("SinkEvent가 자동 발행됨") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-SINK",
                name = "Sink Test",
                price = 10000
            )

            executor.executeSync(input)

            sinkEventRepo.size() shouldBe 1
        }
    }

    describe("executeAsync") {

        it("성공 시 DeployJob 반환") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-ASYNC",
                name = "Async Test",
                price = 15000
            )

            val result = executor.executeAsync(input)

            result.shouldBeInstanceOf<Either.Right<*>>()
            val job = (result as Either.Right).value
            job.entityKey shouldBe "product:SKU-ASYNC"
            job.state shouldBe DeployState.DONE
        }
    }

    describe("ingestOnly") {

        it("성공 시 IngestResult 반환") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-INGEST",
                name = "Ingest Only",
                price = 5000
            )

            val result = executor.ingestOnly(input)

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-INGEST"
            result.error shouldBe null
        }
    }
})
