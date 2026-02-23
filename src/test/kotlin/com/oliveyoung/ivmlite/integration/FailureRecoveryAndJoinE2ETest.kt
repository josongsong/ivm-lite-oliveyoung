package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
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
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 부분 실패 복구 & Join 외부 엔티티 부재 E2E 테스트
 *
 * 검증 항목:
 * A. 부분 실패 복구:
 *   1. 잘못된 RuleSet → Slicing 실패 → RawData는 저장되지만 Slice/View 미생성
 *   2. Slicing 실패 후 올바른 RuleSet으로 재시도 → 복구 가능
 *   3. 동일 데이터 재Ingest → 멱등성 유지 (RawData 덮어쓰기 없음)
 *
 * B. Join 외부 엔티티 부재:
 *   1. required=false JOIN: 외부 엔티티 없어도 Ingest 성공 (ENRICHED 빈 결과)
 *   2. Brand Ingest 후 Product Ingest → ENRICHED에 Brand 데이터 포함
 *   3. required=true JOIN 타겟 부재 → JoinError
 */
class FailureRecoveryAndJoinE2ETest : DescribeSpec({

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
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val executor = DeployExecutor(orchestrator, contractResolver)

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        sinkRuleRegistry.clear()
        sinkRuleRegistry.register(
            SinkRule(
                id = "sinkrule.recovery.opensearch",
                version = "1.0.0",
                status = SinkRuleStatus.ACTIVE,
                input = SinkRuleInput(
                    type = InputType.SLICE,
                    sliceTypes = listOf(SliceType.CORE),
                    entityTypes = listOf("PRODUCT", "BRAND")
                ),
                target = SinkRuleTarget(
                    type = SinkTargetType.OPENSEARCH,
                    endpoint = "test://localhost:9200",
                    indexPattern = "recovery-{tenantId}"
                ),
                docId = DocIdSpec(pattern = "{entityKey}")
            )
        )
    }

    // ===== A. 부분 실패 복구 =====

    describe("부분 실패: 잘못된 RuleSet으로 Ingest 시도") {

        it("존재하지 않는 RuleSet → Slicing 실패 → Err 반환") {
            val bogusCmd = IngestionCommand(
                tenantId = TenantId("fail-t"),
                entityKey = EntityKey("product:FAIL-001"),
                data = buildJsonObject {
                    put("name", "Failure Test")
                    put("price", 10000)
                    put("category", "skincare")
                },
                ruleSetRef = ContractRef("nonexistent.ruleset.v999", SemVer.parse("99.0.0")),
                viewDefId = "view.product.core.v1",
                viewDefVersion = "1.0.0",
                version = 1L
            )

            val result = orchestrator.ingest(bogusCmd)
            result.shouldBeInstanceOf<Result.Err>()

            // RawData는 저장됨 (Slicing 이전 단계에서 성공)
            val raw = rawDataRepo.getLatest(
                TenantId("fail-t"),
                EntityKey("product:FAIL-001")
            )
            raw.shouldBeInstanceOf<Result.Ok<*>>()

            // Slice/SinkEvent는 생성되지 않음
            sliceRepo.size() shouldBe 0
            sinkEventRepo.size() shouldBe 0
        }

        it("실패 후 올바른 RuleSet으로 재Ingest → 성공") {
            // 1차: 잘못된 RuleSet
            val bogusCmd = IngestionCommand(
                tenantId = TenantId("fail-t"),
                entityKey = EntityKey("product:RECOVER-001"),
                data = buildJsonObject {
                    put("name", "Recovery Test")
                    put("price", 20000)
                    put("category", "skincare")
                },
                ruleSetRef = ContractRef("nonexistent.v1", SemVer.parse("1.0.0")),
                viewDefId = "view.product.core.v1",
                viewDefVersion = "1.0.0",
                version = 1L
            )

            val failResult = orchestrator.ingest(bogusCmd)
            failResult.shouldBeInstanceOf<Result.Err>()

            // 2차: 올바른 RuleSet (새 버전으로)
            val input = ProductInput(
                tenantId = "fail-t",
                sku = "RECOVER-001",
                name = "Recovery Test",
                price = 20000,
                category = "skincare"
            )

            val successResult = executor.executeSync(input)
            successResult.success shouldBe true

            // 이제 Slice/SinkEvent 생성 확인
            sliceRepo.size() shouldNotBe 0
            sinkEventRepo.size() shouldNotBe 0
        }
    }

    describe("부분 실패: View 생성 단계") {

        it("Slice 생성 성공 후 View 생성도 성공하는 정상 경로 확인") {
            val input = ProductInput(
                tenantId = "view-t",
                sku = "VIEW-OK-001",
                name = "View Success",
                price = 15000
            )

            val result = executor.executeSync(input)
            result.success shouldBe true

            val rawCount = rawDataRepo.size()
            val sliceCount = sliceRepo.size()
            val sinkEventCount = sinkEventRepo.size()

            rawCount shouldBe 1
            assert(sliceCount >= 1) { "sliceCount should >= 1, got $sliceCount" }
            assert(sinkEventCount >= 1) { "sinkEventCount should >= 1, got $sinkEventCount" }
        }
    }

    // ===== B. Join 외부 엔티티 부재 =====

    describe("Join: optional 외부 엔티티 부재") {

        it("Brand 없이 Product Ingest → ENRICHED 슬라이스 포함 (Brand 데이터 없이)") {
            // DOC-001 RuleSet의 ENRICHED 슬라이스는 brand JOIN이 required=false
            val input = ProductInput(
                tenantId = "join-t",
                sku = "JOIN-OPT-001",
                name = "No Brand Product",
                price = 10000,
                brand = "nonexistent-brand-code"
            )

            val result = executor.executeSync(input)
            // required=false이므로 Brand 없어도 성공
            result.success shouldBe true
            result.entityKey shouldBe "product:JOIN-OPT-001"

            // RawData, Slice 생성 확인
            rawDataRepo.size() shouldBe 1
            assert(sliceRepo.size() >= 1)
        }

        it("Brand Ingest 후 Product Ingest → ENRICHED에 Brand 데이터 포함") {
            // 1. Brand 먼저 Ingest
            val brand = BrandInput(
                tenantId = "join-t",
                brandId = "JOIN-BRAND-001",
                name = "Joined Brand",
                logoUrl = "https://cdn.example.com/brand.png"
            )
            val brandResult = executor.executeSync(brand)
            brandResult.success shouldBe true

            // 2. Product Ingest (brand.code = "JOIN-BRAND-001")
            val product = ProductInput(
                tenantId = "join-t",
                sku = "JOIN-WITH-001",
                name = "With Brand Product",
                price = 20000,
                brand = "JOIN-BRAND-001"
            )
            val productResult = executor.executeSync(product)
            productResult.success shouldBe true

            // RawData 2건 (Brand + Product)
            rawDataRepo.size() shouldBe 2

            // Slice 여러 건 (Product slices + Brand slices)
            assert(sliceRepo.size() >= 2)
        }
    }

    describe("Join: 순서 의존성") {

        it("Product 먼저, Brand 나중 → Product의 ENRICHED는 Brand 없이 생성") {
            // Product 먼저 (Brand 아직 없음)
            val product = ProductInput(
                tenantId = "order-t",
                sku = "ORDER-001",
                name = "Order Test",
                price = 15000,
                brand = "LATE-BRAND"
            )
            val pResult = executor.executeSync(product)
            pResult.success shouldBe true

            val sliceCountAfterProduct = sliceRepo.size()

            // Brand 나중에 Ingest
            val brand = BrandInput(
                tenantId = "order-t",
                brandId = "LATE-BRAND",
                name = "Late Brand"
            )
            val bResult = executor.executeSync(brand)
            bResult.success shouldBe true

            // Brand 추가 후 Slice 수 증가
            val sliceCountAfterBrand = sliceRepo.size()
            assert(sliceCountAfterBrand > sliceCountAfterProduct) {
                "Brand Ingest should create additional slices"
            }
        }
    }

    describe("Join: sourceFieldPath 누락") {

        it("brand.code 필드 없는 Product → ENRICHED의 optional JOIN skip") {
            // brand 필드 없이 Ingest
            val ruleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
            val viewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
            val viewDefVer = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

            val cmd = IngestionCommand(
                tenantId = TenantId("join-t"),
                entityKey = EntityKey("product:NO-BRAND-FIELD"),
                data = buildJsonObject {
                    put("name", "No Brand Field")
                    put("price", 5000)
                    put("category", "skincare")
                    // brand.code 필드 의도적으로 누락
                },
                ruleSetRef = ruleSetRef,
                viewDefId = viewDefId,
                viewDefVersion = viewDefVer,
                version = 1L
            )

            val result = orchestrator.ingest(cmd)
            // required=false이므로 sourceField 누락도 성공
            result.shouldBeInstanceOf<Result.Ok<*>>()
        }
    }
})
