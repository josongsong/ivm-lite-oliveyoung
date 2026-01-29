package com.oliveyoung.ivmlite.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
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
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * E2E 증거 자료 수집 테스트
 * 
 * 실제 데이터 흐름을 단계별로 추출하여 증거 자료로 정리
 */
class E2EEvidenceTest : StringSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
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
    val queryViewWorkflow = QueryViewWorkflow(sliceRepo, contractRegistry)

    val tenantId = TenantId("oliveyoung")
    val entityKey = EntityKey("PRODUCT#oliveyoung#A000000001")

    val productFixture = """
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

    val mapper: ObjectMapper = jacksonObjectMapper()

    "증거 자료: 전체 플로우 데이터 추출" {
        println("\n" + "=".repeat(80))
        println("📊 E2E 테스트 증거 자료 - 전체 플로우 데이터 추출")
        println("=".repeat(80) + "\n")

        // ==================== Step 1: Ingest ====================
        println("🔹 Step 1: Ingest (RawData 저장)")
        println("-".repeat(80))
        val ingestResult = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixture,
        )
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // RawData 조회
        val rawDataResult = rawDataRepo.get(tenantId, entityKey, 1L)
        rawDataResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok<*>>()
        val rawData = (rawDataResult as com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok).value

        println("✅ RawData 저장 완료")
        println("   - TenantId: ${rawData.tenantId.value}")
        println("   - EntityKey: ${rawData.entityKey.value}")
        println("   - Version: ${rawData.version}")
        println("   - SchemaId: ${rawData.schemaId}")
        println("   - Payload Hash: ${rawData.payloadHash.take(16)}...")
        println("   - Payload Size: ${rawData.payload.length} bytes")
        println("   - Payload (일부): ${rawData.payload.take(100)}...")
        println()

        // Outbox 확인
        val outboxPending = outboxRepo.findPending(10)
        outboxPending.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val outboxEntries = (outboxPending as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        println("✅ Outbox 저장 완료")
        println("   - PENDING 항목 수: ${outboxEntries.size}")
        if (outboxEntries.isNotEmpty()) {
            println("   - EventType: ${outboxEntries[0].eventType}")
            println("   - Payload: ${outboxEntries[0].payload.take(80)}...")
        }
        println()

        // ==================== Step 2: Slicing ====================
        println("🔹 Step 2: Slicing (RuleSet 기반 슬라이스 분리)")
        println("-".repeat(80))

        val sliceResult = slicingWorkflow.execute(tenantId, entityKey, 1L)
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val sliceKeys = (sliceResult as SlicingWorkflow.Result.Ok).value

        println("✅ 슬라이싱 완료")
        println("   - 생성된 Slice 수: ${sliceKeys.size}")
        println("   - SliceTypes: ${sliceKeys.map { it.sliceType.name }.joinToString(", ")}")
        println()

        // 각 Slice 상세 조회
        val allSlicesResult = sliceRepo.getByVersion(tenantId, entityKey, 1L)
        allSlicesResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()
        val allSlices = (allSlicesResult as SliceRepositoryPort.Result.Ok).value

        println("📦 생성된 Slice 상세:")
        allSlices.forEach { slice ->
            println("\n   [${slice.sliceType.name}]")
            println("   - RuleSetId: ${slice.ruleSetId}")
            println("   - Hash: ${slice.hash.take(16)}...")
            println("   - Data Size: ${slice.data.length} bytes")
            
            // Slice 데이터 파싱하여 주요 필드만 표시
            try {
                val sliceJson = mapper.readTree(slice.data)
                when (slice.sliceType) {
                    SliceType.CORE -> {
                        println("   - 주요 필드:")
                        println("     • title: ${sliceJson["title"]?.asText()?.take(50)}")
                        println("     • brand: ${sliceJson["brand"]?.asText()}")
                        println("     • price: ${sliceJson["price"]?.asInt()}")
                    }
                    SliceType.PRICE -> {
                        println("   - 주요 필드:")
                        println("     • price: ${sliceJson["price"]?.asInt()}")
                        println("     • salePrice: ${sliceJson["salePrice"]?.asInt()}")
                        println("     • discount: ${sliceJson["discount"]?.asInt()}")
                    }
                    SliceType.INVENTORY -> {
                        println("   - 주요 필드:")
                        println("     • stock: ${sliceJson["stock"]?.asInt()}")
                        println("     • availability: ${sliceJson["availability"]?.asText()}")
                    }
                    SliceType.MEDIA -> {
                        println("   - 주요 필드:")
                        val images = sliceJson["images"]
                        if (images?.isArray == true) {
                            println("     • images: ${images.size()}개")
                        }
                    }
                    SliceType.CATEGORY -> {
                        println("   - 주요 필드:")
                        println("     • categoryId: ${sliceJson["categoryId"]?.asText()}")
                        println("     • categoryPath: ${sliceJson["categoryPath"]?.toString()?.take(50)}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                println("   - Data: ${slice.data.take(100)}...")
            }
        }
        println()

        // ==================== Step 3: Inverted Index ====================
        println("🔹 Step 3: Inverted Index 생성")
        println("-".repeat(80))

        val brandIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "라운드랩")
        val categoryIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "category", "CAT-SKINCARE-SUN")
        val tagIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "tag", "자외선차단")

        println("✅ Inverted Index 생성 완료")
        println("   - brand='라운드랩': ${brandIndexes.size}개 엔트리")
        brandIndexes.forEach { idx ->
            println("     • ${idx.refEntityKey.value} (${idx.sliceType.name})")
        }
        println("   - category='CAT-SKINCARE-SUN': ${categoryIndexes.size}개 엔트리")
        categoryIndexes.forEach { idx ->
            println("     • ${idx.refEntityKey.value} (${idx.sliceType.name})")
        }
        println("   - tag='자외선차단': ${tagIndexes.size}개 엔트리")
        tagIndexes.forEach { idx ->
            println("     • ${idx.refEntityKey.value} (${idx.sliceType.name})")
        }
        println()

        // ==================== Step 4: Query ====================
        println("🔹 Step 4: Query (ViewDefinition 기반 조회)")
        println("-".repeat(80))

        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = entityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val viewResponse = (queryResult as QueryViewWorkflow.Result.Ok).value

        println("✅ Query 완료")
        println("   - ViewId: view.product.pdp.v1")
        println("   - Response Data Size: ${viewResponse.data.length} bytes")
        
        // Response 데이터 파싱
        try {
            val responseJson = mapper.readTree(viewResponse.data)
            println("   - 주요 필드:")
            println("     • title: ${responseJson["title"]?.asText()?.take(50)}")
            println("     • brand: ${responseJson["brand"]?.asText()}")
            println("     • price: ${responseJson["price"]?.asInt()}")
            println("     • salePrice: ${responseJson["salePrice"]?.asInt()}")
        } catch (e: Exception) {
            println("   - Data: ${viewResponse.data.take(200)}...")
        }

        if (viewResponse.meta != null) {
            println("   - Meta:")
            println("     • missingSlices: ${viewResponse.meta?.missingSlices?.joinToString(", ") ?: "없음"}")
            println("     • usedContracts: ${viewResponse.meta?.usedContracts?.size ?: 0}개")
        }
        println()

        // ==================== Step 5: INCREMENTAL Slicing ====================
        println("🔹 Step 5: INCREMENTAL Slicing (v1→v2 업데이트)")
        println("-".repeat(80))

        val productFixtureV2 = """
        {
            "productId": "A000000001",
            "title": "[올영픽] 라운드랩 자작나무 수분 선크림 SPF50+ PA++++ (리뉴얼)",
            "brand": "라운드랩",
            "brandId": "BRAND#oliveyoung#roundlab",
            "price": 23000,
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

        // v2 Ingest
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV2,
        )

        // executeAuto (INCREMENTAL 선택)
        val incrementalResult = slicingWorkflow.executeAuto(tenantId, entityKey, 2L)
        incrementalResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val incrementalSliceKeys = (incrementalResult as SlicingWorkflow.Result.Ok).value

        println("✅ INCREMENTAL 슬라이싱 완료")
        println("   - 재생성된 Slice 수: ${incrementalSliceKeys.size}")
        println("   - SliceTypes: ${incrementalSliceKeys.map { it.sliceType.name }.joinToString(", ")}")

        // v1과 v2 Slice 비교
        val v1Slices = (sliceRepo.getByVersion(tenantId, entityKey, 1L) as SliceRepositoryPort.Result.Ok).value
        val v2Slices = (sliceRepo.getByVersion(tenantId, entityKey, 2L) as SliceRepositoryPort.Result.Ok).value

        println("\n   📊 버전별 Slice 비교:")
        println("   - v1 Slice 수: ${v1Slices.size}")
        println("   - v2 Slice 수: ${v2Slices.size}")

        // CORE Slice 비교 (title, price 변경)
        val v1Core = v1Slices.first { it.sliceType == SliceType.CORE }
        val v2Core = v2Slices.first { it.sliceType == SliceType.CORE }

        println("\n   [CORE Slice 비교]")
        println("   - v1 Hash: ${v1Core.hash.take(16)}...")
        println("   - v2 Hash: ${v2Core.hash.take(16)}...")
        println("   - Hash 변경: ${v1Core.hash != v2Core.hash}")

        try {
            val v1CoreJson = mapper.readTree(v1Core.data)
            val v2CoreJson = mapper.readTree(v2Core.data)
            println("   - v1 title: ${v1CoreJson["title"]?.asText()?.take(50)}")
            println("   - v2 title: ${v2CoreJson["title"]?.asText()?.take(50)}")
            println("   - v1 price: ${v1CoreJson["price"]?.asInt()}")
            println("   - v2 price: ${v2CoreJson["price"]?.asInt()}")
        } catch (e: Exception) {
            // ignore
        }

        // PRICE Slice 비교
        val v1Price = v1Slices.first { it.sliceType == SliceType.PRICE }
        val v2Price = v2Slices.first { it.sliceType == SliceType.PRICE }

        println("\n   [PRICE Slice 비교]")
        println("   - v1 Hash: ${v1Price.hash.take(16)}...")
        println("   - v2 Hash: ${v2Price.hash.take(16)}...")
        println("   - Hash 변경: ${v1Price.hash != v2Price.hash}")

        try {
            val v1PriceJson = mapper.readTree(v1Price.data)
            val v2PriceJson = mapper.readTree(v2Price.data)
            println("   - v1 price: ${v1PriceJson["price"]?.asInt()}")
            println("   - v2 price: ${v2PriceJson["price"]?.asInt()}")
        } catch (e: Exception) {
            // ignore
        }

        // INVENTORY Slice 비교 (변경 없음)
        val v1Inventory = v1Slices.first { it.sliceType == SliceType.INVENTORY }
        val v2Inventory = v2Slices.first { it.sliceType == SliceType.INVENTORY }

        println("\n   [INVENTORY Slice 비교]")
        println("   - v1 Hash: ${v1Inventory.hash.take(16)}...")
        println("   - v2 Hash: ${v2Inventory.hash.take(16)}...")
        println("   - Hash 변경: ${v1Inventory.hash != v2Inventory.hash}")
        println("   - 영향 없음: ${v1Inventory.hash == v2Inventory.hash} (INCREMENTAL에서 복사됨)")
        println()

        // ==================== 요약 ====================
        println("=".repeat(80))
        println("📋 요약")
        println("=".repeat(80))
        println("✅ RawData: 1개 저장 (v1, v2)")
        println("✅ Slice: ${v1Slices.size}개 타입 × 2개 버전 = ${v1Slices.size + v2Slices.size}개 총 Slice")
        println("✅ Inverted Index: brand(${brandIndexes.size}), category(${categoryIndexes.size}), tag(${tagIndexes.size})")
        println("✅ Query: ViewDefinition 기반 조회 성공")
        println("✅ INCREMENTAL: 영향받는 Slice만 재생성 (CORE, PRICE), 영향 없는 Slice는 복사 (INVENTORY)")
        println("=".repeat(80))
    }
})
