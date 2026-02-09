package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * DOC-001 E2E Test
 *
 * DOC-001 Flat Catalog 스키마 기반 E2E 테스트:
 * - DOC-001 구조의 실제 fixture 데이터
 * - ruleset.product.doc001.v1 RuleSet 사용
 * - PRODUCT_* View 사용
 * - 6개 SliceType (CORE, PRICE, INVENTORY, MEDIA, CATEGORY, INDEX) 검증
 */
class Doc001E2ETest : StringSpec(init@{

    tags(IntegrationTag)

    // ==================== 실제 컴포넌트 Setup ====================

    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()

    // 실제 LocalYaml Contract Registry
    val contractRegistry = LocalYamlContractRegistryAdapter()

    // JoinExecutor (실제 JOIN 실행)
    val joinExecutor = JoinExecutor(rawDataRepo)

    // SlicingEngine (실제 Contract 기반)
    val slicingEngine = DefaultSlicingEngineAdapter(SlicingEngine(contractRegistry, joinExecutor))

    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())

    // 실제 Workflow 구성
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
    val queryViewWorkflow = QueryViewWorkflow(sliceRepo, contractRegistry)

    afterEach {
        rawDataRepo.clear()
        outboxRepo.clear()
        sliceRepo.clear()
        invertedIndexRepo.clear()
    }

    // ==================== DOC-001 Fixture Data ====================

    // DOC-001 구조의 상품 데이터 fixture (필수 필드 포함)
    val doc001FixtureV1 = """
    {
        "_meta": {
            "schemaVersion": 3,
            "savedAt": "2026-01-16T10:30:00Z",
            "clientInfo": {
                "userAgent": "Mozilla/5.0",
                "appVersion": "1.2.3"
            }
        },
        "_audit": {
            "createdBy": "EMP001234",
            "createdAt": "2026-01-15T09:00:00Z",
            "updatedBy": "EMP001234",
            "updatedAt": "2026-01-16T10:30:00Z"
        },
        "masterInfo": {
            "gtin": "8809123456789",
            "gdsCd": "GDS00012345",
            "gdsNm": "에센스 토너 200ml",
            "gdsEngNm": "Essence Toner 200ml",
            "standardCategory": {
                "large": {
                    "code": "10",
                    "name": "스킨케어"
                },
                "medium": {
                    "code": "1010",
                    "name": "토너/스킨"
                },
                "small": {
                    "code": "101010",
                    "name": "토너"
                }
            },
            "brand": {
                "code": "BRD00123",
                "krName": "설화수",
                "enName": "Sulwhasoo"
            },
            "flags": {
                "dermoYn": false,
                "premBrndYn": true,
                "ebGdsYn": false,
                "onlineExcluGdsYn": true,
                "harmgdsYn": false,
                "selBanYn": false,
                "medapYn": false,
                "infnSelImpsYn": false,
                "poutTlmtDdNumYn": false,
                "medicalDeviceYn": false
            },
            "gdsStatNm": "정상",
            "buyTypNm": "직매입",
            "gdsRegYmd": "20260115",
            "md": {
                "empNo": "EMP001234",
                "name": "홍길동"
            },
            "scm": {
                "empNo": "EMP005678",
                "name": "김철수"
            },
            "shelfLifeManageYn": true,
            "shelfLifeAvailableDays": 180,
            "clearanceYn": false,
            "disposalAllowed": true,
            "returnAllowed": true
        },
        "onlineInfo": {
            "prdtNo": "1234567890",
            "prdtName": "에센스 토너 200ml",
            "onlinePrdtName": "[특가] 에센스 토너 200ml",
            "prdtStatCode": "NORMAL",
            "prdtStatCodeName": "정상",
            "sellStatCode": "SALE",
            "sellStatCodeName": "판매중",
            "displayYn": "Y",
            "prdtGbnCode": "SINGLE",
            "prdtGbnCodeName": "단품",
            "orderQuantity": {
                "min": 1,
                "max": 10,
                "increaseUnit": 1
            },
            "orderLimits": {
                "brandMin": "",
                "brandMax": "",
                "classMin": "",
                "classMax": ""
            },
            "appExcluPrdtYn": false
        },
        "options": [
            {
                "gdsCd": "GDS00012345-01",
                "gdsNm": "200ml",
                "sellStatCode": "SALE",
                "rprstYn": 1,
                "sortSeq": 1,
                "dispYn": "Y",
                "mrgnRt": 35.5,
                "nrmlAmt": 35000,
                "gdsCostUprc": 15000,
                "gdsStkoutUprc": 0,
                "gdsSelprcUprc": 32000,
                "dcSelprcUprc": 28000,
                "gpRate": 20.5
            }
        ],
        "displayCategories": [
            {
                "sclsCtgrNo": "CAT001234"
            }
        ],
        "thumbnailImages": [
            {
                "index": 0,
                "path": "/images/product/abc.jpg",
                "fullUrl": "https://cdn.example.com/images/product/abc.jpg",
                "originalName": "product_main.jpg",
                "typeCode": "MAIN",
                "seq": 0
            }
        ],
        "videoInfo": {
            "exposureType": "MAIN",
            "entries": [
                {
                    "videoId": "abc123xyz"
                }
            ]
        },
        "emblemInfo": {
            "cleanBeautyYn": true,
            "crueltyFreeYn": true,
            "dermaTestedYn": false,
            "glutenFreeYn": false,
            "parabenFreeYn": true,
            "veganYn": false
        },
        "additionalInfo": {
            "srchKeyWordText": "토너,스킨,보습,수분"
        },
        "reservationSale": {
            "rsvCheckYn": false,
            "restrictShipmentYn": false
        }
    }
    """.trimIndent()

    val tenantId = TenantId("oliveyoung")
    val entityKey = EntityKey("PRODUCT#oliveyoung#1234567890")

    // ==================== DOC-001 E2E 시나리오 ====================

    "DOC-001: Contract 로딩 - ruleset.product.doc001.v1" {
        val ref = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.0.0"))
        val result = contractRegistry.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val ruleSet = (result as Result.Ok).value

        ruleSet.meta.id shouldBe "ruleset.product.doc001.v1"
        ruleSet.entityType shouldBe "PRODUCT"

        // 6개 SliceType 검증 (CORE, PRICE, INVENTORY, MEDIA, CATEGORY, INDEX)
        ruleSet.slices.size shouldBe 6
        ruleSet.slices.map { it.type } shouldBe listOf(
            SliceType.CORE,
            SliceType.PRICE,
            SliceType.INVENTORY,
            SliceType.MEDIA,
            SliceType.CATEGORY,
            SliceType.INDEX
        )
    }

    "DOC-001: Contract 로딩 - view.product.core.v1" {
        val ref = ContractRef("view.product.core.v1", SemVer.parse("1.0.0"))
        val result = contractRegistry.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val viewDef = (result as Result.Ok).value

        viewDef.meta.id shouldBe "view.product.core.v1"
        viewDef.requiredSlices shouldBe listOf(SliceType.CORE)
        viewDef.missingPolicy.name shouldBe "FAIL_CLOSED"
    }

    "DOC-001: Ingest → Slice 생성 (6개 SliceType)" {
        // Step 1: Ingest
        val ingestResult = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "raw.product.doc001.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = doc001FixtureV1,
        )
        ingestResult.shouldBeInstanceOf<Result.Ok<*>>()

        // Step 2: Slicing (DOC-001 RuleSet 사용)
        val doc001RuleSetRef = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.0.0"))
        val sliceResult = slicingWorkflow.execute(tenantId, entityKey, 1L, doc001RuleSetRef)
        sliceResult.shouldBeInstanceOf<Result.Ok<*>>()

        // Step 3: 생성된 Slice 검증 (6개)
        val sliceKeys = (sliceResult as Result.Ok).value
        sliceKeys.size shouldBe 6 // CORE, PRICE, INVENTORY, MEDIA, CATEGORY, INDEX

        // CORE Slice 내용 검증
        val coreKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.CORE)
        val coreResult = sliceRepo.batchGet(tenantId, listOf(coreKey))
        coreResult.shouldBeInstanceOf<Result.Ok<*>>()
        val coreSlice = (coreResult as Result.Ok).value.first()

        coreSlice.sliceType shouldBe SliceType.CORE
        coreSlice.data shouldContain "설화수"
        coreSlice.data shouldContain "에센스 토너"
        coreSlice.ruleSetId shouldBe "ruleset.product.doc001.v1"

        // PRICE Slice 검증
        val priceKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.PRICE)
        val priceResult = sliceRepo.batchGet(tenantId, listOf(priceKey))
        priceResult.shouldBeInstanceOf<Result.Ok<*>>()
        val priceSlice = (priceResult as Result.Ok).value.first()
        priceSlice.sliceType shouldBe SliceType.PRICE
        priceSlice.data shouldContain "options"

        // INVENTORY Slice 검증
        val inventoryKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.INVENTORY)
        val inventoryResult = sliceRepo.batchGet(tenantId, listOf(inventoryKey))
        inventoryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val inventorySlice = (inventoryResult as Result.Ok).value.first()
        inventorySlice.sliceType shouldBe SliceType.INVENTORY

        // MEDIA Slice 검증
        val mediaKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.MEDIA)
        val mediaResult = sliceRepo.batchGet(tenantId, listOf(mediaKey))
        mediaResult.shouldBeInstanceOf<Result.Ok<*>>()
        val mediaSlice = (mediaResult as Result.Ok).value.first()
        mediaSlice.sliceType shouldBe SliceType.MEDIA

        // CATEGORY Slice 검증
        val categoryKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.CATEGORY)
        val categoryResult = sliceRepo.batchGet(tenantId, listOf(categoryKey))
        categoryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val categorySlice = (categoryResult as Result.Ok).value.first()
        categorySlice.sliceType shouldBe SliceType.CATEGORY

        // INDEX Slice 검증
        val indexKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.INDEX)
        val indexResult = sliceRepo.batchGet(tenantId, listOf(indexKey))
        indexResult.shouldBeInstanceOf<Result.Ok<*>>()
        val indexSlice = (indexResult as Result.Ok).value.first()
        indexSlice.sliceType shouldBe SliceType.INDEX
    }

    "DOC-001: Query View - PRODUCT_CORE" {
        // Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "raw.product.doc001.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = doc001FixtureV1,
        )

        val doc001RuleSetRef = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.0.0"))
        slicingWorkflow.execute(tenantId, entityKey, 1L, doc001RuleSetRef)

        // Query PRODUCT_CORE View
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "view.product.core.v1",
            entityKey = entityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val response = (queryResult as Result.Ok).value
        response.data shouldContain "설화수"
        response.data shouldContain "에센스 토너"
    }

    "DOC-001: Query View - PRODUCT_DETAIL (복합 View)" {
        // Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "raw.product.doc001.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = doc001FixtureV1,
        )

        val doc001RuleSetRef = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.0.0"))
        slicingWorkflow.execute(tenantId, entityKey, 1L, doc001RuleSetRef)

        // Query PRODUCT_DETAIL View
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "view.product.detail.v1",
            entityKey = entityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val response = (queryResult as Result.Ok).value
        response.data shouldContain "설화수"
        response.data shouldContain "에센스 토너"
    }

    "DOC-001: Query View - PRODUCT_SEARCH (복합 View)" {
        // Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "raw.product.doc001.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = doc001FixtureV1,
        )

        val doc001RuleSetRef = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.0.0"))
        slicingWorkflow.execute(tenantId, entityKey, 1L, doc001RuleSetRef)

        // Query PRODUCT_SEARCH View
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "view.product.search.v1",
            entityKey = entityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val response = (queryResult as Result.Ok).value
        response.data shouldContain "설화수"
    }
})
