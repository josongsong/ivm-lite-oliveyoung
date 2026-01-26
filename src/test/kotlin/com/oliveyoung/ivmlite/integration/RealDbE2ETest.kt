package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * 실제 DB E2E 테스트 (PostgresTestContainer 사용)
 * 
 * 실제 fixture 데이터를 실제 PostgreSQL에 저장하고 검증:
 * - RawData 저장 확인
 * - Slice 생성 확인
 * - Inverted Index 생성 확인
 * - Query 결과 확인
 * - DB 직접 조회로 검증
 */
@EnabledIf(DockerEnabledCondition::class)
class RealDbE2ETest : StringSpec({

    tags(IntegrationTag)

    val dsl: DSLContext = PostgresTestContainer.start()
    
    // 실제 DB Repository 사용
    val rawDataRepo = JooqRawDataRepository(dsl)
    val outboxRepo = JooqOutboxRepository(dsl)
    val sliceRepo = JooqSliceRepository(dsl)
    val invertedIndexRepo = JooqInvertedIndexRepository(dsl)

    // 실제 Contract Registry
    val contractRegistry = LocalYamlContractRegistryAdapter()
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = SlicingEngine(contractRegistry, joinExecutor)

    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

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

    // 실제 fixture 데이터
    val productFixtureV1 = """
    {
        "productId": "A000000001",
        "title": "[올영픽] 라운드랩 자작나무 수분 선크림 SPF50+ PA++++",
        "brand": "라운드랩",
        "brandId": "BRAND#oliveyoung#roundlab",
        "price": 25000,
        "salePrice": 19900,
        "discount": 20,
        "stock": 1500,
        "availability": "IN_STOCK",
        "images": [
            {"url": "https://cdn.oliveyoung.co.kr/img/product/A000000001_01.jpg", "type": "MAIN"},
            {"url": "https://cdn.oliveyoung.co.kr/img/product/A000000001_02.jpg", "type": "DETAIL"}
        ],
        "videos": [],
        "categoryId": "CAT-SKINCARE-SUN",
        "categoryPath": ["스킨케어", "선케어", "선크림"],
        "tags": ["자외선차단", "수분", "민감피부", "자작나무"],
        "promotionIds": ["PROMO-2026-SUMMER"],
        "couponIds": [],
        "reviewCount": 12847,
        "averageRating": 4.8,
        "ingredients": ["정제수", "사이클로펜타실록세인", "에칠헥실메톡시신나메이트"],
        "description": "자작나무 수액으로 촉촉하게 마무리되는 선크림"
    }
    """.trimIndent()

    val tenantId = TenantId("oliveyoung")
    val entityKey = EntityKey("PRODUCT#oliveyoung#A000000001")

    beforeEach {
        // 테스트 전 DB 초기화
        dsl.execute("TRUNCATE TABLE raw_data CASCADE")
        dsl.execute("TRUNCATE TABLE outbox CASCADE")
        dsl.execute("TRUNCATE TABLE slices CASCADE")
        dsl.execute("TRUNCATE TABLE inverted_index CASCADE")
    }

    "E2E: 실제 fixture → DB 저장 → Slice 생성 → Query" {
        // Step 1: Ingest (RawData DB 저장)
        val ingestResult = runBlocking {
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = productFixtureV1,
            )
        }
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // Step 2: DB에서 RawData 확인
        val rawDataCount = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("entity_key").eq("PRODUCT#oliveyoung#A000000001"))
            .and(DSL.field("version").eq(1L))
            .fetchOne(0, Int::class.java)
        rawDataCount shouldBe 1

        // RawData 내용 확인
        val rawData = dsl.select()
            .from(DSL.table("raw_data"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("entity_key").eq("PRODUCT#oliveyoung#A000000001"))
            .fetchOne()
        rawData shouldNotBe null
        val payloadJson = rawData?.get("payload_json", String::class.java) ?: ""
        payloadJson shouldContain "라운드랩"
        payloadJson shouldContain "자작나무 수분 선크림"

        // Step 3: Slicing (Slice DB 저장)
        val sliceResult = runBlocking {
            slicingWorkflow.execute(tenantId, entityKey, 1L)
        }
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // Step 4: DB에서 Slice 확인
        val sliceCount = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("entity_key").eq("PRODUCT#oliveyoung#A000000001"))
            .and(DSL.field("version").eq(1L))
            .fetchOne(0, Int::class.java)
        sliceCount shouldBe 5 // CORE, PRICE, INVENTORY, MEDIA, CATEGORY

        // CORE Slice 내용 확인
        val coreSlice = dsl.select()
            .from(DSL.table("slices"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("entity_key").eq("PRODUCT#oliveyoung#A000000001"))
            .and(DSL.field("version").eq(1L))
            .and(DSL.field("slice_type").eq("CORE"))
            .fetchOne()
        coreSlice shouldNotBe null
        val coreData = coreSlice?.get("data", String::class.java) ?: ""
        coreData shouldContain "라운드랩"
        coreData shouldContain "자작나무 수분 선크림"
        coreSlice?.get("rule_set_id", String::class.java) shouldBe "ruleset.core.v1"

        // Step 5: Inverted Index 확인
        val indexCount = dsl.selectCount()
            .from(DSL.table("inverted_index"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("ref_entity_key").eq("PRODUCT#oliveyoung#A000000001"))
            .fetchOne(0, Int::class.java)
        indexCount shouldBe 6 // brand(1) + category(1) + tag(4)

        // brand 인덱스 확인
        val brandIndex = dsl.select()
            .from(DSL.table("inverted_index"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .and(DSL.field("index_type").eq("brand"))
            .and(DSL.field("index_value").eq("라운드랩"))
            .fetchOne()
        brandIndex shouldNotBe null
        brandIndex?.get("ref_entity_key", String::class.java) shouldBe "PRODUCT#oliveyoung#A000000001"

        // Step 6: Query (ViewDefinition 기반)
        val queryResult = runBlocking {
            queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "view.product.pdp.v1",
                entityKey = entityKey,
                version = 1L,
            )
        }
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as QueryViewWorkflow.Result.Ok).value
        response.data shouldContain "라운드랩"
        response.data shouldContain "자작나무 수분 선크림"
    }

    "E2E: Outbox 저장 확인" {
        // Ingest 실행
        runBlocking {
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = productFixtureV1,
            )
        }

        // Outbox DB 확인
        val outboxCount = dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("aggregate_id").like("%PRODUCT#oliveyoung#A000000001%"))
            .fetchOne(0, Int::class.java)
        outboxCount shouldBe 1

        // Outbox 내용 확인
        val outbox = dsl.select()
            .from(DSL.table("outbox"))
            .where(DSL.field("aggregate_id").like("%PRODUCT#oliveyoung#A000000001%"))
            .fetchOne()
        outbox shouldNotBe null
        outbox?.get("event_type", String::class.java) shouldBe "RAW_DATA_INGESTED"
        outbox?.get("status", String::class.java) shouldBe "PENDING"
    }

    "E2E: 여러 상품 일괄 처리" {
        val products = listOf(
            "A000000001" to productFixtureV1,
            "A000000002" to productFixtureV1.replace("A000000001", "A000000002")
                .replace("라운드랩", "토리든"),
            "A000000003" to productFixtureV1.replace("A000000001", "A000000003")
                .replace("라운드랩", "닥터지"),
        )

        // 일괄 Ingest
        products.forEach { (productId, fixture) ->
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = EntityKey("PRODUCT#oliveyoung#$productId"),
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = fixture,
                )
            }
        }

        // DB에서 확인
        val rawDataCount = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .fetchOne(0, Int::class.java)
        rawDataCount shouldBe 3

        // 일괄 Slicing
        products.forEach { (productId, _) ->
            runBlocking {
                slicingWorkflow.execute(tenantId, EntityKey("PRODUCT#oliveyoung#$productId"), 1L)
            }
        }

        // Slice 확인
        val sliceCount = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("tenant_id").eq("oliveyoung"))
            .fetchOne(0, Int::class.java)
        sliceCount shouldBe 15 // 3개 상품 × 5개 Slice
    }
})
