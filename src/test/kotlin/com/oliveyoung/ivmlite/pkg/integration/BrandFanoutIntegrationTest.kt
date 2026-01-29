package com.oliveyoung.ivmlite.pkg.integration

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.InMemoryChangeSetRepository
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutWorkflow
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * RFC-IMPL-016: Brand RefIndexSlice 통합 테스트
 *
 * ## 테스트 시나리오
 * 1. Brand RawData 수집 → Brand CORE/ENRICHED 슬라이스 생성
 * 2. Product RawData 수집 (Brand 참조) → Product CORE 슬라이스 + 역인덱스 생성
 * 3. Brand 변경 → FanoutWorkflow 실행 → 연관 Product 재슬라이싱
 * 4. 역인덱스로 영향받은 Product 조회 → 재슬라이싱 확인
 *
 * ## 검증 사항
 * - Brand ruleset.brand.v1.yaml 로드 성공
 * - Brand CORE/ENRICHED 슬라이스 생성
 * - Product ruleset.v1.yaml의 indexes.brand.references: BRAND 동작
 * - 역인덱스 "product_by_brand" 생성
 * - Fanout 시 역인덱스 조회 → Product 재슬라이싱
 * - No deadcode, No stub!
 */
class BrandFanoutIntegrationTest : StringSpec({

    // ==================== Setup ====================

    fun createTestComponents(): TestComponents {
        val rawDataRepo = InMemoryRawDataRepository()
        val outboxRepo = InMemoryOutboxRepository()
        val sliceRepo = InMemorySliceRepository()
        val invertedIndexRepo = InMemoryInvertedIndexRepository()
        val changeSetRepo = InMemoryChangeSetRepository()
        val contractRegistry = LocalYamlContractRegistryAdapter()

        val joinExecutor = JoinExecutor(rawDataRepo)
        val slicingEngine = DefaultSlicingEngineAdapter(SlicingEngine(contractRegistry, joinExecutor))
        val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
        val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())

        val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)
        val slicingWorkflow = SlicingWorkflow(
            rawDataRepo,
            sliceRepo,
            slicingEngine,
            invertedIndexRepo,
            changeSetBuilder,
            impactCalculator,
            contractRegistry,
        )

        val fanoutWorkflow = FanoutWorkflow(
            contractRegistry = contractRegistry,
            invertedIndexRepo = invertedIndexRepo,
            slicingWorkflow = slicingWorkflow,
            config = FanoutConfig.DEFAULT.copy(
                batchSize = 10,
                maxFanout = 100,
            ),
        )

        return TestComponents(
            contractRegistry = contractRegistry,
            ingestWorkflow = ingestWorkflow,
            slicingWorkflow = slicingWorkflow,
            fanoutWorkflow = fanoutWorkflow,
            rawDataRepo = rawDataRepo,
            sliceRepo = sliceRepo,
            invertedIndexRepo = invertedIndexRepo,
        )
    }

    // ==================== 1. Brand Ruleset 로드 및 슬라이싱 ====================

    "Brand ruleset 로드 성공" {
        val components = createTestComponents()
        val brandRulesetRef = ContractRef("brand.v1", SemVer.parse("1.0.0"))

        val result = runBlocking {
            components.contractRegistry.loadRuleSetContract(brandRulesetRef)
        }

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val ruleset = (result as ContractRegistryPort.Result.Ok).value
        ruleset.entityType shouldBe "BRAND"
        ruleset.slices.size shouldBe 2  // CORE + JOINED
        ruleset.slices.map { it.type }.toSet() shouldBe setOf(SliceType.CORE, SliceType.JOINED)
    }

    "Brand RawData → CORE/ENRICHED 슬라이스 생성" {
        val components = createTestComponents()
        val tenantId = TenantId("tenant1")
        val brandKey = EntityKey("BRAND#tenant1#roundlab")

        // Given: Brand RawData 수집
        val brandPayload = """
            {
                "brandId": "roundlab",
                "brandName": "라운드랩",
                "brandDesc": "피부과학 브랜드",
                "logoUrl": "https://cdn.example.com/roundlab.png",
                "status": "ACTIVE",
                "ranking": 1,
                "categoryId": "cosmetics"
            }
        """.trimIndent()

        val ingestResult = runBlocking {
            components.ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                schemaId = "entity.brand.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = brandPayload,
            )
        }

        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<Unit>>()

        // When: 슬라이싱 실행
        val sliceResult = runBlocking {
            components.slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                ruleSetRef = ContractRef("brand.v1", SemVer.parse("1.0.0")),
            )
        }

        // Then: CORE + JOINED 슬라이스 생성 확인
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val slicesResult = runBlocking {
            components.sliceRepo.getByVersion(tenantId, brandKey, 1L)
        }

        slicesResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok<*>>()
        val slices = (slicesResult as com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok).value

        slices.size shouldBeGreaterThan 0
        val sliceTypes = slices.map { it.sliceType }.toSet()
        sliceTypes shouldBe setOf(SliceType.CORE, SliceType.JOINED)

        // CORE slice 검증
        val coreSlice = slices.first { it.sliceType == SliceType.CORE }
        coreSlice.data.contains("roundlab") shouldBe true
        coreSlice.data.contains("라운드랩") shouldBe true
    }

    // ==================== 2. Product 슬라이싱 → 역인덱스 생성 ====================

    "Product 슬라이싱 → product_by_brand 역인덱스 생성" {
        val components = createTestComponents()
        val tenantId = TenantId("tenant1")

        // Given: Product RawData 수집 (Brand 참조)
        val productKeys = listOf(
            EntityKey("PRODUCT#tenant1#prod001"),
            EntityKey("PRODUCT#tenant1#prod002"),
            EntityKey("PRODUCT#tenant1#prod003"),
        )

        for ((index, productKey) in productKeys.withIndex()) {
            val productPayload = """
                {
                    "productId": "prod00${index + 1}",
                    "title": "제품${index + 1}",
                    "brand": "roundlab",
                    "price": ${(index + 1) * 10000},
                    "categoryId": "cosmetics"
                }
            """.trimIndent()

            runBlocking {
                components.ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    schemaId = "entity.product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = productPayload,
                )

                // 슬라이싱 실행 (ruleset.core.v1 사용)
                components.slicingWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0")),
                )
            }
        }

        // When: 역인덱스 조회 (product_by_brand)
        val indexType = "product_by_brand"
        val indexValue = "roundlab"  // lowercase entityId

        val queryResult = runBlocking {
            components.invertedIndexRepo.queryByIndexType(
                tenantId = tenantId,
                indexType = indexType,
                indexValue = indexValue,
                limit = 100,
            )
        }

        // Then: 3개 Product가 조회되어야 함
        queryResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort.Result.Ok<*>>()
        val queryData = (queryResult as com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort.Result.Ok).value
        queryData.entries.size shouldBeExactly 3
        queryData.entries.map { it.entityKey }.toSet() shouldBe productKeys.toSet()
    }

    // ==================== 3. Brand 변경 → Fanout → Product 재슬라이싱 ====================

    "Brand 변경 → FanoutWorkflow → 연관 Product 재슬라이싱" {
        val components = createTestComponents()
        val tenantId = TenantId("tenant1")
        val brandKey = EntityKey("BRAND#tenant1#roundlab")

        // Given: Brand + Product 초기 데이터 생성
        runBlocking {
            // Brand 생성
            components.ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                schemaId = "entity.brand.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """
                    {
                        "brandId": "roundlab",
                        "brandName": "라운드랩",
                        "brandDesc": "피부과학 브랜드",
                        "status": "ACTIVE"
                    }
                """.trimIndent(),
            )

            components.slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                ruleSetRef = ContractRef("brand.v1", SemVer.parse("1.0.0")),
            )

            // Product 3개 생성 (Brand 참조)
            for (i in 1..3) {
                val productKey = EntityKey("PRODUCT#tenant1#prod00$i")
                components.ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    schemaId = "entity.product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = """
                        {
                            "productId": "prod00$i",
                            "title": "제품$i",
                            "brand": "roundlab",
                            "price": ${i * 10000}
                        }
                    """.trimIndent(),
                )

                components.slicingWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0")),
                )
            }
        }

        // When: Brand 변경 → Fanout 실행
        val fanoutResult = runBlocking {
            components.fanoutWorkflow.onEntityChange(
                tenantId = tenantId,
                upstreamEntityType = "BRAND",
                upstreamEntityKey = brandKey,
                upstreamVersion = 2L,
            )
        }

        // Then: Fanout 성공 + 3개 Product 재슬라이싱
        fanoutResult.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val result = (fanoutResult as FanoutWorkflow.Result.Ok).value
        result.totalAffected shouldBeExactly 3
        result.processedCount shouldBeExactly 3
        result.failedCount shouldBeExactly 0

        // Product 버전 업데이트 확인 (version 2로 재슬라이싱됨)
        val updatedSlicesResult = runBlocking {
            components.sliceRepo.getByVersion(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#tenant1#prod001"),
                version = 2L,
            )
        }
        updatedSlicesResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok<*>>()
        val updatedSlices = (updatedSlicesResult as com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok).value
        updatedSlices.size shouldBeGreaterThan 0
    }

    // ==================== 4. Circuit Breaker 테스트 ====================

    "Brand Fanout Circuit Breaker 동작" {
        val components = createTestComponents()
        val tenantId = TenantId("tenant1")
        val brandKey = EntityKey("BRAND#tenant1#popular-brand")

        // Given: Brand 1개 + Product 150개 (circuit breaker 임계치 100 초과)
        runBlocking {
            components.ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                schemaId = "entity.brand.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"brandId": "popular-brand", "brandName": "인기브랜드"}""",
            )

            components.slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = brandKey,
                version = 1L,
                ruleSetRef = ContractRef("brand.v1", SemVer.parse("1.0.0")),
            )

            // Product 150개 생성
            for (i in 1..150) {
                val productKey = EntityKey("PRODUCT#tenant1#prod${i.toString().padStart(4, '0')}")
                components.ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    schemaId = "entity.product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = """{"productId": "prod${i.toString().padStart(4, '0')}", "title": "제품$i", "brand": "popular-brand", "price": $i}""",
                )

                components.slicingWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0")),
                )
            }
        }

        // When: Brand 변경 → Fanout (circuit breaker 발동)
        val fanoutResult = runBlocking {
            components.fanoutWorkflow.onEntityChange(
                tenantId = tenantId,
                upstreamEntityType = "BRAND",
                upstreamEntityKey = brandKey,
                upstreamVersion = 2L,
            )
        }

        // Then: Circuit breaker 발동 → SKIPPED
        fanoutResult.shouldBeInstanceOf<FanoutWorkflow.Result.Ok<*>>()
        val result = (fanoutResult as FanoutWorkflow.Result.Ok).value
        result.totalAffected shouldBeExactly 150
        result.skippedCount shouldBeExactly 150  // Circuit breaker로 스킵됨
        result.processedCount shouldBeExactly 0
    }
})

// ==================== Helper Data Class ====================

private data class TestComponents(
    val contractRegistry: ContractRegistryPort,
    val ingestWorkflow: IngestWorkflow,
    val slicingWorkflow: SlicingWorkflow,
    val fanoutWorkflow: FanoutWorkflow,
    val rawDataRepo: InMemoryRawDataRepository,
    val sliceRepo: InMemorySliceRepository,
    val invertedIndexRepo: InMemoryInvertedIndexRepository,
)
