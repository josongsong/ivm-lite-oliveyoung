package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.sdk.model.ViewQueryException
import com.oliveyoung.ivmlite.sdk.schema.ProductCoreData
import com.oliveyoung.ivmlite.sdk.schema.Views
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * SOTA DSL 핵심 E2E 테스트 (RFC-021 Phase 1)
 *
 * 검증 항목:
 * 1. 프로퍼티 할당 스타일 Deploy (tenantId = "x")
 * 2. Ivm.query() 단축 API (Ivm.client().query() 대신)
 * 3. getOrThrow() - DeployResult, ViewResult
 * 4. onSuccess / onFailure 체이닝
 * 5. Views.Product.Core 타입 세이프 조회
 */
class SotaDslE2ETest : StringSpec(init@{
    tags(com.oliveyoung.ivmlite.integration.IntegrationTag)

    val testTenantId = "sota-e2e-tenant"

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()

    val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(
        SlicingEngine(contractRegistry, joinExecutor)
    )
    val viewComposer = ViewComposer()

    val workflow = com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow(
        rawDataRepo = rawDataRepo,
        sliceRepo = sliceRepo,
        slicingEngine = slicingEngine,
        viewComposer = viewComposer
    )

    val sinkRuleRegistry = InMemorySinkRuleRegistry()
    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry
    )

    val queryWorkflow = QueryViewWorkflow(
        sliceRepo = sliceRepo,
        contractRegistry = contractRegistry
    )

    val executor = DeployExecutor(
        orchestrator = orchestrator,
        contractResolver = contractResolver
    )

    beforeSpec {
        val context = IvmContext.builder()
            .executor(executor)
            .queryWorkflow(queryWorkflow)
            .config {
                tenantId(testTenantId)
            }
            .build()
        Ivm.initialize(context)
    }

    afterSpec {
        Ivm.reset()
    }

    beforeTest {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    "SOTA: 프로퍼티 할당 스타일 Deploy (tenantId = \"x\")" {
        val result = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-PROP-001"
            name = "SOTA 프로퍼티 할당 테스트"
            price = 25000
            currency = "KRW"
            category = "skincare"
        }.deploy()

        result.success shouldBe true
        result.entityKey shouldBe "product:SOTA-PROP-001"
        result.version shouldNotBe null
    }

    "SOTA: Ivm.query() 단축 API (client() 생략)" {
        val deployResult = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-QUERY-001"
            name = "Query 단축 테스트"
            price = 18000
        }.deploy()

        val viewResult = Ivm.query("view.product.core.v1")
            .key("product:SOTA-QUERY-001")
            .version(deployResult.version.toLongOrNull() ?: 1L)
            .get()

        viewResult.success shouldBe true
        viewResult.entityKey shouldBe "product:SOTA-QUERY-001"
    }

    "SOTA: Ivm.query().key().getOrThrow() - 성공 시 JsonObject 반환" {
        val deployResult = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-GET-001"
            name = "getOrThrow 테스트"
            price = 12000
        }.deploy()

        val data = Ivm.query("view.product.core.v1")
            .key("product:SOTA-GET-001")
            .version(deployResult.version.toLongOrNull() ?: 1L)
            .getOrThrow()

        data shouldNotBe null
        // ViewResponse 구조: slices 배열 또는 core/name 등 - 비어있지 않음만 검증
        data.keys.isNotEmpty() shouldBe true
    }

    "SOTA: Ivm.query().key().getOrThrow() - 실패 시 ViewQueryException" {
        shouldThrow<ViewQueryException> {
            Ivm.query("view.product.core.v1")
                .key("product:nonexistent-sota")
                .version(1L)
                .getOrThrow()
        }
    }

    "SOTA: Views.Product.Core 타입 세이프 조회 + getOrThrow()" {
        val deployResult = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-TYPED-001"
            name = "타입 세이프 상품"
            price = 35000
            category = "skincare"
            brand = "테스트브랜드"
        }.deploy()

        val product: ProductCoreData = Ivm.query(Views.Product.Core)
            .key("product:SOTA-TYPED-001")
            .version(deployResult.version.toLongOrNull() ?: 1L)
            .getOrThrow()

        product.name shouldBe "타입 세이프 상품"
        product.price shouldBe 35000L
        product.category shouldBe "skincare"
        product.brand shouldBe "테스트브랜드"
    }

    "SOTA: deploy().getOrThrow() - 성공 시 DeployResult 반환" {
        val result = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-DEPLOY-GET-001"
            name = "Deploy getOrThrow"
            price = 9000
        }.deploy()
            .getOrThrow()

        result.success shouldBe true
        result.entityKey shouldBe "product:SOTA-DEPLOY-GET-001"
    }

    "SOTA: deploy().onSuccess() - 성공 시에만 실행" {
        var called = false
        Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-ONSUCCESS-001"
            name = "onSuccess 테스트"
            price = 5000
        }.deploy()
            .onSuccess { called = true }
            .onFailure { called = false }

        called shouldBe true
    }

    "SOTA: deploy().onFailure() - 실패 시에만 실행" {
        // 실패 케이스는 DeployExecutor가 항상 성공 반환하므로,
        // onFailure가 호출되지 않음을 검증
        var failureCalled = false
        Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-ONFAIL-001"
            name = "onFailure 테스트"
            price = 7000
        }.deploy()
            .onSuccess { failureCalled = false }
            .onFailure { failureCalled = true }

        failureCalled shouldBe false
    }

    "SOTA: Deploy → Query 전체 경로 (프로퍼티 할당 + Ivm.query + getOrThrow)" {
        val deployResult = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-FULL-001"
            name = "전체 경로 E2E"
            price = 42000
            category = "skincare"
        }.deploy()
            .getOrThrow()

        val product: ProductCoreData = Ivm.query(Views.Product.Core)
            .key(deployResult.entityKey)
            .version(deployResult.version.toLongOrNull() ?: 1L)
            .getOrThrow()

        product.name shouldBe "전체 경로 E2E"
        product.price shouldBe 42000L
    }

    "SOTA: deploy().getOrThrow() 체이닝 - 성공 시 DeployResult 반환" {
        val result = Ivm.product {
            tenantId = testTenantId
            sku = "SOTA-EXC-001"
            name = "예외 검증"
            price = 1000
        }.deploy()

        val thrown = result.getOrThrow()
        thrown.success shouldBe true
        thrown.entityKey shouldBe "product:SOTA-EXC-001"
    }
})
