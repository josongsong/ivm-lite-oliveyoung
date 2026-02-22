package com.oliveyoung.ivmlite.sdk.domain

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
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * EntityActions 테스트
 *
 * deploy(), deployAsync(), explain() 검증
 * ProductActions, BrandActions, CategoryActions 포함
 */
class EntityActionsTest : DescribeSpec({

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
        sinkRuleRegistry = InMemorySinkRuleRegistry()
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val executor = DeployExecutor(orchestrator, contractResolver)
    val config = IvmClientConfig()

    // 테스트용 EntityActions 구현체
    class TestProductActions(
        input: ProductInput,
        config: IvmClientConfig,
        executor: DeployExecutor?
    ) : EntityActions<ProductInput>(input, config, executor) {
        override fun buildEntityKey(): String = "product:${input.sku}"
    }

    class TestBrandActions(
        input: BrandInput,
        config: IvmClientConfig,
        executor: DeployExecutor?
    ) : EntityActions<BrandInput>(input, config, executor) {
        override fun buildEntityKey(): String = "brand:${input.brandId}"
    }

    class TestCategoryActions(
        input: CategoryInput,
        config: IvmClientConfig,
        executor: DeployExecutor?
    ) : EntityActions<CategoryInput>(input, config, executor) {
        override fun buildEntityKey(): String = "category:${input.categoryId}"
    }

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("ProductActions") {

        it("deploy() → 성공") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-EA-001",
                name = "Entity Actions Product",
                price = 29000,
                category = "skincare"
            )
            val actions = TestProductActions(input, config, executor)

            val result = actions.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-EA-001"
            result.error shouldBe null
        }

        it("deployAsync() → Either.Right<DeployJob>") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-EA-ASYNC",
                name = "Async Product",
                price = 15000
            )
            val actions = TestProductActions(input, config, executor)

            val result = actions.deployAsync()

            result.shouldBeInstanceOf<Either.Right<DeployJob>>()
            val job = (result as Either.Right).value
            job.entityKey shouldBe "product:SKU-EA-ASYNC"
        }

        it("explain() → DeployPlan 반환") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-EXPLAIN",
                name = "Explain Product",
                price = 10000
            )
            val actions = TestProductActions(input, config, executor)

            val plan = actions.explain()

            plan.entityKey shouldBe "product:SKU-EXPLAIN"
            plan.entityType shouldBe "product"
            plan.slices.shouldNotBeEmpty()
            plan.views.shouldNotBeEmpty()
            plan.rules.shouldNotBeEmpty()
        }
    }

    describe("BrandActions") {

        it("deploy() → 성공") {
            val input = BrandInput(
                tenantId = "test-tenant",
                brandId = "brand-ea-001",
                name = "Test Brand"
            )
            val actions = TestBrandActions(input, config, executor)

            val result = actions.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "brand:brand-ea-001"
        }

        it("explain() → entityType=brand") {
            val input = BrandInput(
                tenantId = "test-tenant",
                brandId = "brand-explain",
                name = "Explain Brand"
            )
            val actions = TestBrandActions(input, config, executor)

            val plan = actions.explain()

            plan.entityType shouldBe "brand"
            plan.entityKey shouldBe "brand:brand-explain"
        }
    }

    describe("CategoryActions") {

        it("deploy() → 성공") {
            val input = CategoryInput(
                tenantId = "test-tenant",
                categoryId = "cat-ea-001",
                name = "Test Category",
                depth = 1,
                displayOrder = 0
            )
            val actions = TestCategoryActions(input, config, executor)

            val result = actions.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "category:cat-ea-001"
        }
    }

    describe("executor 미설정 시 에러") {

        it("deploy() → IllegalStateException") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-NO-EXEC",
                name = "No Executor",
                price = 5000
            )
            val actions = TestProductActions(input, config, executor = null)

            shouldThrow<IllegalStateException> {
                actions.deploy()
            }
        }

        it("deployAsync() → Either.Left<DomainError.ConfigError>") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-NO-EXEC-ASYNC",
                name = "No Executor Async",
                price = 5000
            )
            val actions = TestProductActions(input, config, executor = null)

            val result = actions.deployAsync()

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
        }

        it("explain() → executor 불필요, 항상 성공") {
            val input = ProductInput(
                tenantId = "test-tenant",
                sku = "SKU-EXPLAIN-NOEXEC",
                name = "Explain No Executor",
                price = 5000
            )
            val actions = TestProductActions(input, config, executor = null)

            val plan = actions.explain()

            plan.entityKey shouldBe "product:SKU-EXPLAIN-NOEXEC"
            plan shouldNotBe null
        }
    }
})
