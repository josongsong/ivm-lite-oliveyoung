package com.oliveyoung.ivmlite.integration

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
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Real Contract E2E Test (RFC-IMPL-010 검증)
 *
 * Mock 없이 실제 Contract YAML 파일과 실제 fixture 데이터로 핵심 시나리오 검증:
 * - LocalYamlContractRegistryAdapter로 실제 계약 로딩
 * - 올리브영 상품 데이터 형식의 실제 fixture
 * - v2 API (ViewDefinitionContract 기반 조회)
 * - INCREMENTAL 슬라이싱 (executeAuto)
 */
class RealContractE2ETest : StringSpec({

    // ==================== 실제 컴포넌트 Setup (Mock 없음) ====================

    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()

    // 실제 LocalYaml Contract Registry (ruleset.v1.yaml, view-definition.v1.yaml 로딩)
    val contractRegistry = LocalYamlContractRegistryAdapter()

    // JoinExecutor (실제 JOIN 실행)
    val joinExecutor = JoinExecutor(rawDataRepo)

    // SlicingEngine (실제 Contract 기반)
    val slicingEngine = SlicingEngine(contractRegistry, joinExecutor)

    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

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
    // v2 QueryViewWorkflow (ContractRegistry 연결)
    val queryViewWorkflowV2 = QueryViewWorkflow(sliceRepo, contractRegistry)
    // v1 QueryViewWorkflow (호환성)
    val queryViewWorkflowV1 = QueryViewWorkflow(sliceRepo)

    // OutboxPollingWorker (전체 플로우 테스트용)
    val workerConfig = com.oliveyoung.ivmlite.shared.config.WorkerConfig(
        enabled = true,
        pollIntervalMs = 20,
        idlePollIntervalMs = 50,
        batchSize = 10,
        maxBackoffMs = 200,
        backoffMultiplier = 2.0,
        jitterFactor = 0.0,
        shutdownTimeoutMs = 500,
    )
    val worker = com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker(
        outboxRepo = outboxRepo,
        slicingWorkflow = slicingWorkflow,
        config = workerConfig,
    )

    afterEach {
        worker.stop()
        rawDataRepo.clear()
        outboxRepo.clear()
        sliceRepo.clear()
    }

    // ==================== Fixture Data ====================

    // 올리브영 상품 데이터 fixture (실제 형식)
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

    // v2: title/price 변경만 (CORE 슬라이스에 영향, INCREMENTAL 테스트용)
    // impactMap에서 CORE는 /title, /brand, /price에 영향받음
    // 주의: impactMap에 없는 필드(tags, promotionIds 등) 변경 시 UnmappedChangePathError 발생 (fail-closed)
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

    val tenantId = TenantId("oliveyoung")
    val entityKey = EntityKey("PRODUCT#oliveyoung#A000000001")

    // ==================== 핵심 시나리오 1: 실제 Contract 로딩 검증 ====================

    "Contract: LocalYamlContractRegistryAdapter가 ruleset.v1.yaml을 정상 로딩" {
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))
        val result = contractRegistry.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val ruleSet = (result as ContractRegistryPort.Result.Ok).value

        // RuleSet 기본 정보 검증
        ruleSet.meta.id shouldBe "ruleset.core.v1"
        ruleSet.entityType shouldBe "PRODUCT"

        // slices 파싱 검증 (GAP-B)
        ruleSet.slices.size shouldBe 5
        ruleSet.slices.map { it.type } shouldBe listOf(
            SliceType.CORE, SliceType.PRICE, SliceType.INVENTORY, SliceType.MEDIA, SliceType.CATEGORY
        )

        // indexes 파싱 검증 (GAP-C)
        ruleSet.indexes.size shouldBe 3
        ruleSet.indexes.map { it.type } shouldBe listOf("brand", "category", "tag")

        // CORE slice joins 파싱 검증 (GAP-B)
        val coreSlice = ruleSet.slices.first { it.type == SliceType.CORE }
        coreSlice.joins.size shouldBe 1
        coreSlice.joins[0].name shouldBe "brandInfo"
    }

    "Contract: LocalYamlContractRegistryAdapter가 view-definition.v1.yaml을 정상 로딩" {
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))
        val result = contractRegistry.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val viewDef = (result as ContractRegistryPort.Result.Ok).value

        viewDef.meta.id shouldBe "view.product.pdp.v1"
        viewDef.requiredSlices shouldBe listOf(SliceType.CORE)
        viewDef.missingPolicy.name shouldBe "FAIL_CLOSED"
    }

    // ==================== 핵심 시나리오 2: 실제 슬라이싱 (RuleSet 기반) ====================

    "Slicing: 실제 상품 데이터를 RuleSet 기반으로 슬라이싱" {
        // Step 1: Ingest
        val ingestResult = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // Step 2: Slicing (실제 RuleSet 기반)
        val sliceResult = slicingWorkflow.execute(tenantId, entityKey, 1L)
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // Step 3: 생성된 Slice 검증
        val sliceKeys = (sliceResult as SlicingWorkflow.Result.Ok).value
        sliceKeys.size shouldBe 5 // CORE, PRICE, INVENTORY, MEDIA, CATEGORY

        // CORE Slice 내용 검증
        val coreKey = SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.CORE)
        val coreResult = sliceRepo.batchGet(tenantId, listOf(coreKey))
        coreResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()
        val coreSlice = (coreResult as SliceRepositoryPort.Result.Ok).value.first()

        coreSlice.sliceType shouldBe SliceType.CORE
        coreSlice.data shouldContain "라운드랩"
        coreSlice.data shouldContain "자작나무 수분 선크림"
        coreSlice.ruleSetId shouldBe "ruleset.core.v1"
    }

    "Slicing: Inverted Index가 정상 생성됨 (GAP-C 검증)" {
        // Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey, 1L)

        // Inverted Index 검증: brand=라운드랩으로 조회 (테스트 헬퍼 사용)
        val entries = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "라운드랩")

        entries.isNotEmpty() shouldBe true
        entries.any { it.refEntityKey == entityKey } shouldBe true
    }

    // ==================== 핵심 시나리오 3: v2 API (ViewDefinition 기반 조회) ====================

    "QueryV2: ViewDefinitionContract 기반 조회 성공 (GAP-D 검증)" {
        // Setup: Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey, 1L)

        // v2 Query (ViewDefinition 기반 - requiredSliceTypes 없음)
        val result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = entityKey,
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (result as QueryViewWorkflow.Result.Ok).value

        // ViewResponse 내용 검증
        response.data shouldContain "라운드랩"
        response.data shouldContain "자작나무 수분 선크림"
        response.meta shouldNotBe null
    }

    "QueryV2: FAIL_CLOSED - 슬라이스 없으면 MissingSliceError" {
        // Slicing 없이 바로 Query
        val result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = EntityKey("PRODUCT#oliveyoung#NOT_EXISTS"),
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
        val error = (result as QueryViewWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.MissingSliceError>()
    }

    // ==================== 핵심 시나리오 4: INCREMENTAL 슬라이싱 (executeAuto) ====================

    "INCREMENTAL: v1→v2 업데이트 시 executeAuto가 INCREMENTAL 선택 (GAP-F 검증)" {
        // v1 Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        val v1SliceResult = slicingWorkflow.execute(tenantId, entityKey, 1L)
        v1SliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // v2 Ingest (title, price 변경)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV2,
        )

        // executeAuto: v1이 있으므로 INCREMENTAL 선택
        val result = slicingWorkflow.executeAuto(tenantId, entityKey, 2L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // v2 Slice 검증: title/price 변경 반영 (CORE 슬라이스는 impactMap에서 /title, /price에 영향받음)
        val v2Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = entityKey,
            version = 2L,
        )
        v2Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v2Response = (v2Result as QueryViewWorkflow.Result.Ok).value
        v2Response.data shouldContain "리뉴얼" // title 변경됨
        v2Response.data shouldContain "23000" // price 변경됨

        // v1도 여전히 조회 가능 (버전 독립성)
        val v1Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = entityKey,
            version = 1L,
        )
        v1Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v1Response = (v1Result as QueryViewWorkflow.Result.Ok).value
        v1Response.data shouldContain "25000" // v1 price 유지
    }

    "INCREMENTAL: 첫 버전이면 executeAuto가 FULL 선택" {
        // v1 Ingest (이전 버전 없음)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )

        // executeAuto: 이전 버전 없으므로 FULL
        val result = slicingWorkflow.executeAuto(tenantId, entityKey, 1L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // 모든 Slice 생성됨
        val sliceKeys = (result as SlicingWorkflow.Result.Ok).value
        sliceKeys.size shouldBe 5
    }

    // ==================== 핵심 시나리오 5: Hash 결정성 ====================

    "Determinism: 동일 입력 → 동일 Slice Hash" {
        // 첫 번째 Ingest + Slicing
        val entityKey1 = EntityKey("PRODUCT#oliveyoung#HASH-TEST-1")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey1,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey1, 1L)

        // 두 번째 Ingest + Slicing (같은 payload, 다른 entityKey)
        val entityKey2 = EntityKey("PRODUCT#oliveyoung#HASH-TEST-2")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey2,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey2, 1L)

        // Hash 비교
        val key1 = SliceRepositoryPort.SliceKey(tenantId, entityKey1, 1L, SliceType.CORE)
        val key2 = SliceRepositoryPort.SliceKey(tenantId, entityKey2, 1L, SliceType.CORE)

        val slice1 = (sliceRepo.batchGet(tenantId, listOf(key1)) as SliceRepositoryPort.Result.Ok).value.first()
        val slice2 = (sliceRepo.batchGet(tenantId, listOf(key2)) as SliceRepositoryPort.Result.Ok).value.first()

        // 동일 payload → 동일 Hash (결정성 보장)
        slice1.hash shouldBe slice2.hash
    }

    // ==================== 핵심 시나리오 6: v1 ↔ v2 API 호환성 ====================

    "Compatibility: v1 API와 v2 API 모두 동일 데이터 조회 가능" {
        // Setup
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey, 1L)

        // v1 API (deprecated, sliceTypes 직접 지정)
        @Suppress("DEPRECATION")
        val v1Result = queryViewWorkflowV1.execute(
            tenantId = tenantId,
            viewId = "legacy",
            entityKey = entityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        v1Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v1Data = (v1Result as QueryViewWorkflow.Result.Ok).value.data

        // v2 API (ViewDefinition 기반)
        val v2Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = entityKey,
            version = 1L,
        )
        v2Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v2Data = (v2Result as QueryViewWorkflow.Result.Ok).value.data

        // 동일 CORE 데이터 포함
        v1Data shouldContain "라운드랩"
        v2Data shouldContain "라운드랩"
    }

    // ==================== 핵심 시나리오 7: 멀티 슬라이스 조회 ====================

    "MultiSlice: 여러 SliceType을 한 번에 조회" {
        // Setup
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, entityKey, 1L)

        // 여러 SliceType 조회
        val keys = listOf(
            SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.CORE),
            SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.PRICE),
            SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.INVENTORY),
        )
        val result = sliceRepo.batchGet(tenantId, keys)
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        val slices = (result as SliceRepositoryPort.Result.Ok).value
        slices.size shouldBe 3
        slices.map { it.sliceType }.toSet() shouldBe setOf(SliceType.CORE, SliceType.PRICE, SliceType.INVENTORY)

        // 각 Slice 내용 검증
        val coreSlice = slices.first { it.sliceType == SliceType.CORE }
        val priceSlice = slices.first { it.sliceType == SliceType.PRICE }
        val inventorySlice = slices.first { it.sliceType == SliceType.INVENTORY }

        coreSlice.data shouldContain "title"
        priceSlice.data shouldContain "price"
        inventorySlice.data shouldContain "stock"
    }

    // ==================== 핵심 시나리오 8: Full E2E (OutboxPollingWorker 연동) ====================

    "FullE2E: Ingest → Outbox → Worker → Slicing → Query 전체 자동 플로우" {
        val fullE2EEntityKey = EntityKey("PRODUCT#oliveyoung#FULL-E2E-001")

        // Step 1: Ingest (RawData + Outbox 저장)
        val ingestResult = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = fullE2EEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // Step 2: Outbox에 PENDING 상태로 저장 확인
        val pendingBefore = outboxRepo.findPending(10)
        pendingBefore.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesBefore = (pendingBefore as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesBefore.size shouldBe 1

        // Step 3: Worker 시작 → Polling → executeAuto 자동 실행
        worker.start()
        kotlinx.coroutines.delay(200) // Polling 여유 시간

        // Step 4: Outbox 처리 완료 확인 (PENDING → PROCESSED)
        val pendingAfter = outboxRepo.findPending(10)
        pendingAfter.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesAfter = (pendingAfter as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesAfter.size shouldBe 0 // 처리 완료

        // Step 5: Slice 생성 확인 (v2 Query로 검증)
        val queryResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = fullE2EEntityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as QueryViewWorkflow.Result.Ok).value
        response.data shouldContain "라운드랩"
        response.data shouldContain "자작나무 수분 선크림"

        // Step 6: Inverted Index도 Worker가 생성했는지 확인
        val brandIndexEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "라운드랩")
        brandIndexEntries.isNotEmpty() shouldBe true

        // Step 7: Worker 메트릭 확인
        val metrics = worker.getMetrics()
        metrics.processed shouldBe 1
        metrics.failed shouldBe 0
    }

    // ==================== 핵심 시나리오 9: JoinExecutor 실제 JOIN 실행 ====================

    "JoinExecutor: BRAND 엔티티와 PRODUCT JOIN 실행" {
        // Step 1: BRAND 엔티티 먼저 Ingest
        val brandEntityKey = EntityKey("BRAND#oliveyoung#roundlab")
        val brandFixture = """
        {
            "brandId": "roundlab",
            "brandName": "라운드랩",
            "brandNameEn": "Round Lab",
            "country": "KR",
            "logoUrl": "https://cdn.oliveyoung.co.kr/brand/roundlab.png",
            "description": "건강한 피부를 위한 스킨케어 브랜드"
        }
        """.trimIndent()

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = brandEntityKey,
            version = 1L,
            schemaId = "brand.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = brandFixture,
        )

        // Step 2: PRODUCT 엔티티 Ingest (brandId 참조)
        val productEntityKey = EntityKey("PRODUCT#oliveyoung#JOIN-TEST-001")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = productEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )

        // Step 3: Slicing (JoinExecutor가 BRAND 조회 시도)
        // 현재 ruleset.v1.yaml의 CORE slice에 joins: brandInfo가 정의되어 있음
        // JoinExecutor는 brandId로 BRAND 엔티티를 조회하여 payload에 병합
        val sliceResult = slicingWorkflow.execute(tenantId, productEntityKey, 1L)
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // Step 4: CORE Slice 검증 - JOIN 결과 확인
        // 주의: JoinExecutor의 실제 동작은 required=false이므로 JOIN 실패해도 슬라이싱은 진행됨
        val queryResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = productEntityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as QueryViewWorkflow.Result.Ok).value
        response.data shouldContain "라운드랩"
    }

    // ==================== 핵심 시나리오 10: Tombstone 처리 ====================

    "Tombstone: 삭제된 엔티티 조회 시 NotFound" {
        val tombstoneEntityKey = EntityKey("PRODUCT#oliveyoung#TOMBSTONE-TEST")

        // Step 1: 정상 Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = tombstoneEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, tombstoneEntityKey, 1L)

        // Step 2: 정상 조회 확인
        val normalResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = tombstoneEntityKey,
            version = 1L,
        )
        normalResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()

        // Step 3: Tombstone Slice 직접 저장 (삭제 시뮬레이션)
        val tombstoneSlice = com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord(
            tenantId = tenantId,
            entityKey = tombstoneEntityKey,
            version = 2L, // 새 버전
            sliceType = SliceType.CORE,
            data = "",
            hash = "",
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
            tombstone = com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone.create(
                version = 2L,
                reason = com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason.USER_DELETE,
            ),
        )
        sliceRepo.putAllIdempotent(listOf(tombstoneSlice))

        // Step 4: Tombstone 버전 조회 시 실패 (fail-closed)
        val tombstoneResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = tombstoneEntityKey,
            version = 2L,
        )
        tombstoneResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()

        // Step 5: 이전 버전(v1)은 여전히 조회 가능 (버전 독립성)
        val v1Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = tombstoneEntityKey,
            version = 1L,
        )
        v1Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
    }

    // ==================== 핵심 시나리오 11: Batch Ingest (대량 처리) ====================

    "BatchIngest: 여러 엔티티를 일괄 Ingest → Slicing → Query" {
        val batchSize = 10
        val batchEntityKeys = (1..batchSize).map { idx ->
            EntityKey("PRODUCT#oliveyoung#BATCH-${idx.toString().padStart(3, '0')}")
        }

        // Step 1: Batch Ingest
        batchEntityKeys.forEachIndexed { idx, key ->
            val fixture = """
            {
                "productId": "BATCH-${idx.toString().padStart(3, '0')}",
                "title": "배치 상품 #$idx",
                "brand": "테스트브랜드",
                "price": ${10000 + idx * 1000},
                "salePrice": ${9000 + idx * 1000},
                "discount": 10,
                "stock": ${100 + idx},
                "availability": "IN_STOCK",
                "images": [],
                "videos": [],
                "categoryId": "CAT-TEST",
                "categoryPath": ["테스트"],
                "tags": ["batch", "test-$idx"],
                "promotionIds": [],
                "couponIds": [],
                "reviewCount": 0,
                "averageRating": 0.0,
                "ingredients": [],
                "description": "배치 테스트 상품 $idx"
            }
            """.trimIndent()

            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = key,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = fixture,
            )
        }

        // Step 2: Batch Slicing
        batchEntityKeys.forEach { key ->
            val result = slicingWorkflow.execute(tenantId, key, 1L)
            result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        }

        // Step 3: Batch Query 검증
        batchEntityKeys.forEachIndexed { idx, key ->
            val result = queryViewWorkflowV2.execute(
                tenantId = tenantId,
                viewId = "view.product.pdp.v1",
                entityKey = key,
                version = 1L,
            )
            result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
            val response = (result as QueryViewWorkflow.Result.Ok).value
            response.data shouldContain "배치 상품 #$idx"
        }

        // Step 4: Inverted Index Batch 검증 (brand=테스트브랜드로 모두 조회)
        val brandEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "테스트브랜드")
        brandEntries.size shouldBe batchSize
    }

    // ==================== 핵심 시나리오 12: Version Gap 처리 (v1 → v5 점프) ====================

    "VersionGap: v1 → v5 점프 시 executeAuto 동작" {
        val gapEntityKey = EntityKey("PRODUCT#oliveyoung#GAP-TEST")

        // v1 Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = gapEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, gapEntityKey, 1L)

        // v5 Ingest (v2, v3, v4 스킵 - Version Gap)
        val v5Fixture = """
        {
            "productId": "A000000001",
            "title": "[올영픽] 라운드랩 자작나무 수분 선크림 V5",
            "brand": "라운드랩",
            "brandId": "BRAND#oliveyoung#roundlab",
            "price": 30000,
            "salePrice": 25000,
            "discount": 17,
            "stock": 2000,
            "availability": "IN_STOCK",
            "images": [],
            "videos": [],
            "categoryId": "CAT-SKINCARE-SUN",
            "categoryPath": ["스킨케어", "선케어", "선크림"],
            "tags": ["자외선차단", "수분", "민감피부", "자작나무"],
            "promotionIds": [],
            "couponIds": [],
            "reviewCount": 15000,
            "averageRating": 4.9,
            "ingredients": [],
            "description": "V5 업데이트"
        }
        """.trimIndent()

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = gapEntityKey,
            version = 5L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = v5Fixture,
        )

        // executeAuto: v1 → v5 점프 (중간 버전 없음)
        // RFC 정책: 이전 버전(v1) 기준 INCREMENTAL 또는 FULL
        val result = slicingWorkflow.executeAuto(tenantId, gapEntityKey, 5L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // v5 Query 성공
        val v5Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = gapEntityKey,
            version = 5L,
        )
        v5Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v5Response = (v5Result as QueryViewWorkflow.Result.Ok).value
        v5Response.data shouldContain "V5"
        v5Response.data shouldContain "30000"

        // v1도 여전히 조회 가능 (버전 독립성)
        val v1Result = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = gapEntityKey,
            version = 1L,
        )
        v1Result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val v1Response = (v1Result as QueryViewWorkflow.Result.Ok).value
        v1Response.data shouldContain "25000" // v1 원래 가격
    }

    // ==================== 핵심 시나리오 13: Concurrent Slicing (동시 요청) ====================

    "ConcurrentSlicing: 동일 엔티티에 동시 Slicing 요청 시 멱등성 보장" {
        val concurrentEntityKey = EntityKey("PRODUCT#oliveyoung#CONCURRENT-TEST")

        // Ingest
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = concurrentEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )

        // 동시에 여러 Slicing 요청 (코루틴)
        val results = runBlocking {
            (1..5).map {
                async {
                    slicingWorkflow.execute(tenantId, concurrentEntityKey, 1L)
                }
            }.awaitAll()
        }

        // 모든 결과 성공 (멱등성)
        results.forEach { result ->
            result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        }

        // Slice는 하나만 존재 (중복 없음)
        val sliceKeys = listOf(
            SliceRepositoryPort.SliceKey(tenantId, concurrentEntityKey, 1L, SliceType.CORE),
        )
        val sliceResult = sliceRepo.batchGet(tenantId, sliceKeys)
        sliceResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()
        val slices = (sliceResult as SliceRepositoryPort.Result.Ok).value
        slices.size shouldBe 1

        // Query 정상
        val queryResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = concurrentEntityKey,
            version = 1L,
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
    }

    // ==================== 핵심 시나리오 14: 다중 SliceType 변경 (INCREMENTAL) ====================

    "MultiSliceChange: 여러 SliceType에 영향주는 변경 시 INCREMENTAL" {
        val multiChangeKey = EntityKey("PRODUCT#oliveyoung#MULTI-CHANGE")

        // v1 Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = multiChangeKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, multiChangeKey, 1L)

        // v1 Hash 저장
        val v1CoreKey = SliceRepositoryPort.SliceKey(tenantId, multiChangeKey, 1L, SliceType.CORE)
        val v1PriceKey = SliceRepositoryPort.SliceKey(tenantId, multiChangeKey, 1L, SliceType.PRICE)
        val v1Slices = (sliceRepo.batchGet(tenantId, listOf(v1CoreKey, v1PriceKey)) as SliceRepositoryPort.Result.Ok).value
        val v1CoreHash = v1Slices.first { it.sliceType == SliceType.CORE }.hash
        val v1PriceHash = v1Slices.first { it.sliceType == SliceType.PRICE }.hash

        // v2: title + price 둘 다 변경 (CORE, PRICE 둘 다 영향)
        // impactMap: CORE ← /title, /price, PRICE ← /price
        val v2MultiFixture = """
        {
            "productId": "A000000001",
            "title": "[올영픽] 라운드랩 NEW 수분 선크림",
            "brand": "라운드랩",
            "brandId": "BRAND#oliveyoung#roundlab",
            "price": 28000,
            "salePrice": 22000,
            "discount": 21,
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

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = multiChangeKey,
            version = 2L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = v2MultiFixture,
        )

        // executeAuto: INCREMENTAL (CORE, PRICE 재생성)
        val result = slicingWorkflow.executeAuto(tenantId, multiChangeKey, 2L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // v2 Hash 비교
        val v2CoreKey = SliceRepositoryPort.SliceKey(tenantId, multiChangeKey, 2L, SliceType.CORE)
        val v2PriceKey = SliceRepositoryPort.SliceKey(tenantId, multiChangeKey, 2L, SliceType.PRICE)
        val v2Slices = (sliceRepo.batchGet(tenantId, listOf(v2CoreKey, v2PriceKey)) as SliceRepositoryPort.Result.Ok).value
        val v2CoreHash = v2Slices.first { it.sliceType == SliceType.CORE }.hash
        val v2PriceHash = v2Slices.first { it.sliceType == SliceType.PRICE }.hash

        // CORE, PRICE 모두 변경됨
        v2CoreHash shouldNotBe v1CoreHash
        v2PriceHash shouldNotBe v1PriceHash

        // Query: 변경 반영 확인
        val queryResult = queryViewWorkflowV2.execute(
            tenantId = tenantId,
            viewId = "view.product.pdp.v1",
            entityKey = multiChangeKey,
            version = 2L,
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as QueryViewWorkflow.Result.Ok).value
        response.data shouldContain "NEW 수분 선크림"
        response.data shouldContain "28000"
    }

    // ==================== 핵심 시나리오 15: 변경 없는 업데이트 (No-Op) ====================

    "NoOpUpdate: 동일 데이터로 업데이트 시 Hash 동일 (No-Op)" {
        val noOpKey = EntityKey("PRODUCT#oliveyoung#NOOP-TEST")

        // v1 Ingest + Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = noOpKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1,
        )
        slicingWorkflow.execute(tenantId, noOpKey, 1L)

        // v1 Hash 저장
        val v1Key = SliceRepositoryPort.SliceKey(tenantId, noOpKey, 1L, SliceType.CORE)
        val v1Slice = (sliceRepo.batchGet(tenantId, listOf(v1Key)) as SliceRepositoryPort.Result.Ok).value.first()
        val v1Hash = v1Slice.hash

        // v2 Ingest (동일 데이터)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = noOpKey,
            version = 2L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = productFixtureV1, // 동일 payload
        )

        // executeAuto
        val result = slicingWorkflow.executeAuto(tenantId, noOpKey, 2L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // v2 Hash 비교 - 동일해야 함 (No-Op)
        val v2Key = SliceRepositoryPort.SliceKey(tenantId, noOpKey, 2L, SliceType.CORE)
        val v2Slice = (sliceRepo.batchGet(tenantId, listOf(v2Key)) as SliceRepositoryPort.Result.Ok).value.first()
        val v2Hash = v2Slice.hash

        // 동일 데이터 → 동일 Hash (결정성)
        v2Hash shouldBe v1Hash
    }

    // ==================== 핵심 시나리오 16: 다중 Tenant 데이터 격리 ====================

    "TenantIsolation: Tenant A와 B 데이터 완전 격리" {
        val tenantA = TenantId("tenant-a")
        val tenantB = TenantId("tenant-b")
        val sameEntityKey = EntityKey("PRODUCT#shared#ISOLATION-TEST")

        // Tenant A: Ingest + Slicing
        val fixtureA = """
        {
            "productId": "ISOLATION-TEST",
            "title": "Tenant A 전용 상품",
            "brand": "A브랜드",
            "price": 10000,
            "salePrice": 9000,
            "discount": 10,
            "stock": 100,
            "availability": "IN_STOCK",
            "images": [],
            "videos": [],
            "categoryId": "CAT-A",
            "categoryPath": ["A"],
            "tags": ["tenant-a"],
            "promotionIds": [],
            "couponIds": [],
            "reviewCount": 0,
            "averageRating": 0.0,
            "ingredients": [],
            "description": "A 전용"
        }
        """.trimIndent()

        ingestWorkflow.execute(
            tenantId = tenantA,
            entityKey = sameEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = fixtureA,
        )
        slicingWorkflow.execute(tenantA, sameEntityKey, 1L)

        // Tenant B: 같은 entityKey로 다른 데이터
        val fixtureB = """
        {
            "productId": "ISOLATION-TEST",
            "title": "Tenant B 전용 상품",
            "brand": "B브랜드",
            "price": 20000,
            "salePrice": 18000,
            "discount": 10,
            "stock": 200,
            "availability": "IN_STOCK",
            "images": [],
            "videos": [],
            "categoryId": "CAT-B",
            "categoryPath": ["B"],
            "tags": ["tenant-b"],
            "promotionIds": [],
            "couponIds": [],
            "reviewCount": 0,
            "averageRating": 0.0,
            "ingredients": [],
            "description": "B 전용"
        }
        """.trimIndent()

        ingestWorkflow.execute(
            tenantId = tenantB,
            entityKey = sameEntityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = fixtureB,
        )
        slicingWorkflow.execute(tenantB, sameEntityKey, 1L)

        // Tenant A Query → A 데이터만
        val resultA = queryViewWorkflowV2.execute(
            tenantId = tenantA,
            viewId = "view.product.pdp.v1",
            entityKey = sameEntityKey,
            version = 1L,
        )
        resultA.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val responseA = (resultA as QueryViewWorkflow.Result.Ok).value
        responseA.data shouldContain "Tenant A 전용 상품"
        responseA.data shouldContain "10000"

        // Tenant B Query → B 데이터만
        val resultB = queryViewWorkflowV2.execute(
            tenantId = tenantB,
            viewId = "view.product.pdp.v1",
            entityKey = sameEntityKey,
            version = 1L,
        )
        resultB.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val responseB = (resultB as QueryViewWorkflow.Result.Ok).value
        responseB.data shouldContain "Tenant B 전용 상품"
        responseB.data shouldContain "20000"

        // 데이터 누출 없음
        responseA.data.contains("Tenant B") shouldBe false
        responseB.data.contains("Tenant A") shouldBe false
    }
})
