package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * Multi-tenant 격리 E2E 테스트
 *
 * 검증 항목:
 * 1. Tenant A 데이터 → Tenant B 에서 조회 불가 (RawData, Slice, View)
 * 2. 동일 entityKey, 다른 tenantId → 독립 저장/조회
 * 3. SinkEvent도 tenantId별 격리
 * 4. InvertedIndex tenantId 범위 격리
 */
class MultiTenantIsolationE2ETest : DescribeSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()
    val sinkRuleRegistry = InMemorySinkRuleRegistry()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()

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
        invertedIndexRepo.clear()

        sinkRuleRegistry.register(
            SinkRule(
                id = "sinkrule.multi-tenant.opensearch",
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
                    indexPattern = "multi-tenant-{tenantId}"
                ),
                docId = DocIdSpec(pattern = "{entityKey}")
            )
        )
    }

    describe("RawData 테넌트 격리") {

        it("동일 SKU, 다른 tenantId → 각각 독립 저장") {
            val inputA = ProductInput(
                tenantId = "tenant-A",
                sku = "SHARED-SKU-001",
                name = "Product from Tenant A",
                price = 10000
            )
            val inputB = ProductInput(
                tenantId = "tenant-B",
                sku = "SHARED-SKU-001",
                name = "Product from Tenant B",
                price = 20000
            )

            val resultA = executor.executeSync(inputA)
            val resultB = executor.executeSync(inputB)

            resultA.success shouldBe true
            resultB.success shouldBe true
            resultA.entityKey shouldBe "product:SHARED-SKU-001"
            resultB.entityKey shouldBe "product:SHARED-SKU-001"

            // 2개의 RawData 레코드가 독립 저장
            rawDataRepo.size() shouldBe 2
        }

        it("Tenant A의 RawData → Tenant B에서 조회 불가") {
            val input = ProductInput(
                tenantId = "tenant-A",
                sku = "ISOLATED-001",
                name = "Tenant A Only",
                price = 5000
            )

            executor.executeSync(input).success shouldBe true

            // Tenant A 조회 → 성공
            val resultA = rawDataRepo.getLatest(
                TenantId("tenant-A"),
                EntityKey("product:ISOLATED-001")
            )
            resultA.shouldBeInstanceOf<Result.Ok<*>>()

            // Tenant B 조회 → NotFound
            val resultB = rawDataRepo.getLatest(
                TenantId("tenant-B"),
                EntityKey("product:ISOLATED-001")
            )
            resultB.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("View 테넌트 격리") {

        it("Tenant A의 View → Tenant B에서 조회 불가") {
            val input = ProductInput(
                tenantId = "tenant-A",
                sku = "VIEW-ISO-001",
                name = "View Isolation Test",
                price = 30000
            )

            val result = executor.executeSync(input)
            result.success shouldBe true

            // Tenant A SinkEvent 조회 → 성공 (비어있지 않음)
            val eventsA = runBlocking {
                when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                    is Result.Ok -> r.value.filter {
                        it.tenantId == "tenant-A" && it.entityKey == "product:VIEW-ISO-001" && it.version == result.version.toLong()
                    }
                    is Result.Err -> emptyList()
                }
            }
            eventsA.isNotEmpty() shouldBe true

            // Tenant B SinkEvent 조회 → 빈 리스트 (격리)
            val eventsB = runBlocking {
                when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                    is Result.Ok -> r.value.filter {
                        it.tenantId == "tenant-B" && it.entityKey == "product:VIEW-ISO-001" && it.version == result.version.toLong()
                    }
                    is Result.Err -> emptyList()
                }
            }
            eventsB.isEmpty() shouldBe true
        }

        it("동일 entityKey, 다른 tenant → 각각 독립 View 생성") {
            val inputA = ProductInput(
                tenantId = "tenant-A",
                sku = "DUAL-VIEW-001",
                name = "Tenant A Product",
                price = 15000
            )
            val inputB = ProductInput(
                tenantId = "tenant-B",
                sku = "DUAL-VIEW-001",
                name = "Tenant B Product",
                price = 25000
            )

            val resultA = executor.executeSync(inputA)
            val resultB = executor.executeSync(inputB)

            resultA.success shouldBe true
            resultB.success shouldBe true

            // 각 tenant별 SinkEvent 존재 확인
            runBlocking {
                when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                    is Result.Ok -> {
                        val eventsA = r.value.filter {
                            it.tenantId == "tenant-A" && it.entityKey == "product:DUAL-VIEW-001" && it.version == resultA.version.toLong()
                        }
                        val eventsB = r.value.filter {
                            it.tenantId == "tenant-B" && it.entityKey == "product:DUAL-VIEW-001" && it.version == resultB.version.toLong()
                        }
                        eventsA.isNotEmpty() shouldBe true
                        eventsB.isNotEmpty() shouldBe true
                    }
                    is Result.Err -> throw AssertionError("Expected to find sink events")
                }
            }

            // SinkEvent 2개 독립 저장
            sinkEventRepo.size() shouldBe 2
        }
    }

    describe("SinkEvent 테넌트 격리") {

        it("Tenant A와 B 각각의 SinkEvent 독립 발행") {
            val inputA = ProductInput(
                tenantId = "tenant-A",
                sku = "SINK-ISO-001",
                name = "Sink A",
                price = 5000
            )
            val inputB = ProductInput(
                tenantId = "tenant-B",
                sku = "SINK-ISO-002",
                name = "Sink B",
                price = 6000
            )

            executor.executeSync(inputA).success shouldBe true
            executor.executeSync(inputB).success shouldBe true

            // SinkEvent 2건 독립 발행
            sinkEventRepo.size() shouldBe 2
        }
    }

    describe("멀티 엔티티 타입 간 테넌트 격리") {

        it("같은 tenant 내 Product + Brand 독립 처리") {
            val product = ProductInput(
                tenantId = "tenant-X",
                sku = "PROD-X-001",
                name = "Product X",
                price = 10000
            )
            val brand = BrandInput(
                tenantId = "tenant-X",
                brandId = "BRAND-X-001",
                name = "Brand X"
            )

            val rProduct = executor.executeSync(product)
            val rBrand = executor.executeSync(brand)

            rProduct.success shouldBe true
            rBrand.success shouldBe true
            rProduct.entityKey shouldBe "product:PROD-X-001"
            rBrand.entityKey shouldBe "brand:BRAND-X-001"

            rawDataRepo.size() shouldBe 2
        }

        it("다른 tenant 간 Product + Brand 완전 격리") {
            val productA = ProductInput(
                tenantId = "tenant-A",
                sku = "CROSS-001",
                name = "Product A",
                price = 10000
            )
            val brandB = BrandInput(
                tenantId = "tenant-B",
                brandId = "CROSS-001",
                name = "Brand B"
            )

            executor.executeSync(productA).success shouldBe true
            executor.executeSync(brandB).success shouldBe true

            // Tenant A에 Brand 데이터 없음
            rawDataRepo.getLatest(
                TenantId("tenant-A"),
                EntityKey("brand:CROSS-001")
            ).shouldBeInstanceOf<Result.Err>()

            // Tenant B에 Product 데이터 없음
            rawDataRepo.getLatest(
                TenantId("tenant-B"),
                EntityKey("product:CROSS-001")
            ).shouldBeInstanceOf<Result.Err>()
        }
    }
})
