package com.oliveyoung.ivmlite.pkg.orchestration

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.OpenTelemetry

/**
 * SlicingWorkflow 단위 테스트 (RFC-IMPL-004)
 */
class SlicingWorkflowTest : StringSpec({

    val testTracer = OpenTelemetry.noop().getTracer("test")
    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo, testTracer)
    val contractRegistry = LocalYamlContractRegistryAdapter()
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(SlicingEngine(contractRegistry, joinExecutor))
    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())
    val slicingWorkflow = SlicingWorkflow(
        rawDataRepo,
        sliceRepo,
        slicingEngine,
        invertedIndexRepo,
        changeSetBuilder,
        impactCalculator,
        contractRegistry,
        testTracer,
    )

    val tenantId = TenantId("tenant-1")
    val entityKey = EntityKey("PRODUCT#tenant-1#slice-test")
    val schemaId = "product.v1"
    val schemaVersion = SemVer.parse("1.0.0")

    // 테스트 격리: 각 테스트 전에 repository 초기화
    beforeTest {
        sliceRepo.clear()
        invertedIndexRepo.clear()
    }

    "성공: RawData → CORE slice 생성" {
        // 먼저 RawData 저장
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name": "Slice Test Product"}"""
        )

        // Slicing 실행
        val result = slicingWorkflow.execute(tenantId, entityKey, 1L)
        
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val keys = (result as SlicingWorkflow.Result.Ok).value
        keys.map { it.sliceType }.shouldContain(SliceType.CORE)
    }

    "멱등성: 같은 입력으로 2번 slicing → OK" {
        // RawData 저장
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name": "Idempotent Slice"}"""
        )

        // 첫 번째 슬라이싱
        val result1 = slicingWorkflow.execute(tenantId, entityKey, 2L)
        result1.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // 두 번째 슬라이싱 (멱등)
        val result2 = slicingWorkflow.execute(tenantId, entityKey, 2L)
        result2.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
    }

    "실패: RawData 없음 → Err" {
        val result = slicingWorkflow.execute(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#tenant-1#not-exists"),
            version = 999L
        )

        result.shouldBeInstanceOf<SlicingWorkflow.Result.Err>()
    }

    // ===== RFC-IMPL-010 D-8: INCREMENTAL 슬라이싱 테스트 =====

    val ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

    "INCREMENTAL: 첫 버전 → FULL로 폴백" {
        // v1 저장
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#incr-1"),
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"title": "First Version"}"""
        )

        // fromVersion 없음 (0 → 1)
        val result = slicingWorkflow.executeIncremental(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#incr-1"),
            fromVersion = 0L,
            toVersion = 1L,
            ruleSetRef = ruleSetRef
        )

        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val keys = (result as SlicingWorkflow.Result.Ok).value
        keys.map { it.sliceType }.shouldContain(SliceType.CORE)
    }

    "INCREMENTAL: 변경된 필드 → 영향받는 Slice만 재생성" {
        val ek = EntityKey("PRODUCT#incr-2")

        // v1 저장 및 FULL 슬라이싱
        ingestWorkflow.execute(tenantId, ek, 1L, schemaId, schemaVersion, """{"title": "v1", "brand": "BrandA"}""")
        slicingWorkflow.execute(tenantId, ek, 1L)

        // v2 저장 (title만 변경, brand는 동일)
        ingestWorkflow.execute(tenantId, ek, 2L, schemaId, schemaVersion, """{"title": "v2", "brand": "BrandA"}""")

        // INCREMENTAL 슬라이싱
        val result = slicingWorkflow.executeIncremental(tenantId, ek, 1L, 2L, ruleSetRef)

        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val keys = (result as SlicingWorkflow.Result.Ok).value
        keys.map { it.version }.forEach { it shouldBe 2L }
    }

    "INCREMENTAL: 영향 없는 Slice → version만 증가" {
        val ek = EntityKey("PRODUCT#incr-3")

        // v1 저장 및 FULL 슬라이싱
        ingestWorkflow.execute(tenantId, ek, 1L, schemaId, schemaVersion, """{"title": "Product"}""")
        slicingWorkflow.execute(tenantId, ek, 1L)
        val v1Slices = sliceRepo.getByVersion(tenantId, ek, 1L)
        v1Slices.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        // v2 저장 (동일 데이터)
        ingestWorkflow.execute(tenantId, ek, 2L, schemaId, schemaVersion, """{"title": "Product"}""")

        // INCREMENTAL 슬라이싱 (변경 없음)
        val result = slicingWorkflow.executeIncremental(tenantId, ek, 1L, 2L, ruleSetRef)

        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()
        val v2Slices = sliceRepo.getByVersion(tenantId, ek, 2L)
        v2Slices.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        // hash는 동일해야 함
        val v1Hashes = (v1Slices as SliceRepositoryPort.Result.Ok).value.map { it.hash }.toSet()
        val v2Hashes = (v2Slices as SliceRepositoryPort.Result.Ok).value.map { it.hash }.toSet()
        v2Hashes shouldBe v1Hashes
    }

    "INCREMENTAL vs FULL: 결과 동치 (hash 동일)" {
        val ek = EntityKey("PRODUCT#incr-equiv")

        // v1 저장 및 FULL
        ingestWorkflow.execute(tenantId, ek, 1L, schemaId, schemaVersion, """{"title": "v1"}""")
        slicingWorkflow.execute(tenantId, ek, 1L)

        // v2 저장
        ingestWorkflow.execute(tenantId, ek, 2L, schemaId, schemaVersion, """{"title": "v2"}""")

        // FULL 슬라이싱
        slicingWorkflow.execute(tenantId, ek, 2L)
        val fullSlices = sliceRepo.getByVersion(tenantId, ek, 2L)
        fullSlices.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        // v3 저장 (v2와 동일)
        ingestWorkflow.execute(tenantId, ek, 3L, schemaId, schemaVersion, """{"title": "v2"}""")

        // INCREMENTAL 슬라이싱 (2 → 3, 변경 없음)
        slicingWorkflow.executeIncremental(tenantId, ek, 2L, 3L, ruleSetRef)
        val incrSlices = sliceRepo.getByVersion(tenantId, ek, 3L)
        incrSlices.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        // hash 집합 동일
        val fullHashes = (fullSlices as SliceRepositoryPort.Result.Ok).value.map { it.hash }.toSet()
        val incrHashes = (incrSlices as SliceRepositoryPort.Result.Ok).value.map { it.hash }.toSet()
        incrHashes shouldBe fullHashes
    }

    "INCREMENTAL: toVersion RawData 없음 → Err" {
        val result = slicingWorkflow.executeIncremental(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#not-exists"),
            fromVersion = 1L,
            toVersion = 2L,
            ruleSetRef = ruleSetRef
        )

        result.shouldBeInstanceOf<SlicingWorkflow.Result.Err>()
    }

    // ===== RFC-IMPL-010 D-9: InvertedIndex 통합 테스트 (GAP-C 검증) =====

    "통합: RuleSet indexes 로드 → InvertedIndexBuilder 실행 → 저장 검증" {
        val ek = EntityKey("PRODUCT#index-test")

        // RawData 저장 (brand, categoryId, tags 포함)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = ek,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{
                "title": "Test Product",
                "brand": "BrandX",
                "categoryId": "CAT123",
                "tags": ["sale", "new", "featured"]
            }"""
        )

        // 먼저 RuleSet이 indexes를 제대로 로드하는지 확인
        val ruleSetResult = contractRegistry.loadRuleSetContract(ruleSetRef)
        ruleSetResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort.Result.Ok<*>>()
        val ruleSet = (ruleSetResult as com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort.Result.Ok).value

        // indexes가 제대로 로드되었는지 확인
        ruleSet.indexes.shouldHaveSize(3)

        // Slicing 실행 (RuleSet의 indexes 자동 로드)
        val result = slicingWorkflow.execute(tenantId, ek, 1L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // InvertedIndex가 생성되어 저장되었는지 확인
        // ruleset.v1.yaml에 정의된 indexes:
        // - brand: $.brand → "brandx" (canonicalized)
        // - category: $.categoryId → "cat123" (canonicalized)
        // - tag: $.tags[*] → ["sale", "new", "featured"]

        // brand 인덱스 확인 (CORE 슬라이스에서만 생성)
        val brandEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "brandx")
        brandEntries.size shouldBe 1
        brandEntries[0].targetEntityKey shouldBe ek
        brandEntries[0].sliceType shouldBe SliceType.CORE

        // category 인덱스 확인 (CORE와 CATEGORY 슬라이스에서 각각 생성 → 2개)
        val catEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "category", "cat123")
        catEntries.shouldHaveSize(2)
        catEntries.map { it.sliceType }.toSet() shouldBe setOf(SliceType.CORE, SliceType.CATEGORY)

        // tag 인덱스 확인 (배열 → 3개 엔트리)
        val saleEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "tag", "sale")
        saleEntries.shouldHaveSize(1)
        saleEntries[0].indexValue shouldBe "sale"

        val newEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "tag", "new")
        newEntries.shouldHaveSize(1)

        val featuredEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "tag", "featured")
        featuredEntries.shouldHaveSize(1)
    }

    "통합: INCREMENTAL 슬라이싱 → indexes도 재생성" {
        val ek = EntityKey("PRODUCT#index-incr")

        // v1 저장 및 FULL 슬라이싱
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = ek,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{
                "title": "Product v1",
                "brand": "OldBrand",
                "categoryId": "CAT1"
            }"""
        )
        slicingWorkflow.execute(tenantId, ek, 1L)

        // v1 brand 인덱스 확인
        val v1Brand = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "oldbrand")
        v1Brand.shouldHaveSize(1)

        // v2 저장 (brand 변경)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = ek,
            version = 2L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{
                "title": "Product v2",
                "brand": "NewBrand",
                "categoryId": "CAT1"
            }"""
        )

        // INCREMENTAL 슬라이싱
        slicingWorkflow.executeIncremental(tenantId, ek, 1L, 2L, ruleSetRef)

        // v2 brand 인덱스 확인 (새 값)
        val v2Brand = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "newbrand")
        v2Brand.shouldHaveSize(1)
        v2Brand[0].targetVersion.value shouldBe 2L
    }

    "통합: indexes 없는 필드 → null/blank 필터링" {
        val ek = EntityKey("PRODUCT#no-brand")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = ek,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"title": "No Brand Product"}"""
        )

        // Slicing 실행
        val result = slicingWorkflow.execute(tenantId, ek, 1L)
        result.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // brand 필드가 없으므로 brand 인덱스도 없음
        val brandEntries = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "")
        brandEntries.filter { it.targetEntityKey == ek }.shouldHaveSize(0)
    }
})
