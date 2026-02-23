package com.oliveyoung.ivmlite.sdk

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
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductBuilder
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * SDK Pipeline E2E Test
 *
 * SDK API(DeployableContext) → DeployExecutor → IngestionOrchestrator
 * → RawData + Slice + View + SinkEvent 전체 파이프라인 검증.
 *
 * 검증 항목:
 * 1. SDK deploy() → DeployResult.success
 * 2. RawData 저장 확인
 * 3. Slice 생성 확인
 * 4. View 생성 확인
 * 5. SinkEvent 생성 확인 (SinkRule 매칭 시)
 * 6. viewDefVersion이 Contract YAML에서 동적 해석되어 전달됨
 * 7. Brand 엔티티도 동일 파이프라인 통과
 * 8. deployAsync() → Either.Right<DeployJob>
 */
class SdkPipelineE2ETest : DescribeSpec({

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

    val sinkRuleRegistry = InMemorySinkRuleRegistry()

    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val executor = DeployExecutor(orchestrator, contractResolver)
    val config = IvmClientConfig()

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("Product deploy() - 프로퍼티 할당 스타일 (RFC-021)") {

        it("tenantId = \"x\" 프로퍼티 할당으로 Deploy 성공") {
            val ctx = DeployableContext(
                ProductBuilder().apply {
                    tenantId = "e2e-tenant"
                    sku = "SKU-PROP-001"
                    name = "프로퍼티 할당 테스트"
                    price = 25000
                    currency = "KRW"
                }.build(),
                config,
                executor
            )

            val result = ctx.deploy()
            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-PROP-001"
        }
    }

    describe("Product deploy() - 전체 파이프라인 E2E") {

        val productInput = ProductInput(
            tenantId = "e2e-tenant",
            sku = "SKU-E2E-001",
            name = "E2E Test Product",
            price = 35000,
            currency = "KRW",
            category = "skincare",
            brand = "olive-young",
            attributes = mapOf(
                "color" to "white",
                "weight" to 250,
                "organic" to true
            )
        )

        it("deploy() 성공 + RawData/Slice/View/SinkEvent 생성 검증") {
            val ctx = DeployableContext(productInput, config, executor)

            // 1. SDK deploy() 호출
            val result = ctx.deploy()

            // 2. DeployResult 검증
            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-E2E-001"
            result.error shouldBe null
            result.version shouldNotBe null

            // 3. RawData 저장 확인
            val tenantId = TenantId("e2e-tenant")
            val entityKey = EntityKey("product:SKU-E2E-001")
            val rawData = rawDataRepo.getLatest(tenantId, entityKey)
            rawData.shouldBeInstanceOf<Result.Ok<*>>()
            rawDataRepo.size() shouldBe 1

            // 4. Slice 생성 확인
            sliceRepo.size() shouldNotBe 0

            // 5. View/SinkEvent 생성 확인 (1 View = 1 SinkEvent)
            sinkEventRepo.size() shouldNotBe 0
        }

        it("deployAsync() 성공 + DeployJob 반환") {
            val ctx = DeployableContext(productInput, config, executor)

            val result = ctx.deployAsync()

            result.shouldBeInstanceOf<Either.Right<DeployJob>>()
            val job = (result as Either.Right).value
            job.entityKey shouldBe "product:SKU-E2E-001"
            job.state shouldBe DeployState.DONE
            job.version shouldNotBe null
        }
    }

    describe("Brand deploy() - 멀티 엔티티 타입 E2E") {

        val brandInput = BrandInput(
            tenantId = "e2e-tenant",
            brandId = "BRAND-E2E-001",
            name = "Olive Young Brand",
            logoUrl = "https://example.com/logo.png",
            description = "Premium Korean Beauty",
            country = "KR"
        )

        it("Brand deploy() → RawData/Slice/View 생성") {
            val ctx = DeployableContext(brandInput, config, executor)

            val result = ctx.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "brand:BRAND-E2E-001"

            rawDataRepo.size() shouldBe 1
            sliceRepo.size() shouldNotBe 0
            sinkEventRepo.size() shouldNotBe 0
        }
    }

    describe("연속 배포 멱등성 검증") {

        val productInput = ProductInput(
            tenantId = "e2e-tenant",
            sku = "SKU-IDEM-001",
            name = "Idempotency Test",
            price = 10000
        )

        it("동일 엔티티 2회 deploy → 모두 성공 (version 다름)") {
            val ctx = DeployableContext(productInput, config, executor)

            val r1 = ctx.deploy()
            val r2 = ctx.deploy()

            r1.success shouldBe true
            r2.success shouldBe true
            // version은 VersionGenerator가 매번 다른 값 생성
            r1.version shouldNotBe r2.version
        }
    }

    describe("viewDefVersion Contract 동적 해석 검증") {

        it("EntityContractResolver가 YAML에서 viewDefVersion을 정확히 해석") {
            // PRODUCT의 viewDefVersion 확인
            val versionResult = contractResolver.resolveViewDefVersion("product")
            versionResult.shouldBeInstanceOf<Either.Right<String>>()
            val version = (versionResult as Either.Right).value
            version shouldNotBe ""
        }

        it("viewDefVersion이 IngestionCommand에 실제 전달되어 ViewComposer에서 사용됨") {
            val productInput = ProductInput(
                tenantId = "e2e-version-test",
                sku = "SKU-VER-001",
                name = "Version Test Product",
                price = 20000
            )

            val ctx = DeployableContext(productInput, config, executor)
            val result = ctx.deploy()

            result.success shouldBe true
            // View가 생성되었으면 viewDefVersion이 정상 전달된 것 (1 View = 1 SinkEvent)
            sinkEventRepo.size() shouldNotBe 0
        }
    }

    describe("DeployExecutor 직접 호출 - ingestOnly") {

        it("ingestOnly → IngestResult 확인") {
            val productInput = ProductInput(
                tenantId = "e2e-direct",
                sku = "SKU-DIRECT-001",
                name = "Direct Ingest Product",
                price = 15000
            )

            val ingestResult = executor.ingestOnly(productInput)

            ingestResult.success shouldBe true
            ingestResult.entityKey shouldBe "product:SKU-DIRECT-001"
            ingestResult.version shouldNotBe 0L
        }
    }
})
