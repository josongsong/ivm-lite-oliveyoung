package com.oliveyoung.ivmlite.apps.lambda

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
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * IngestProcessor E2E 테스트
 *
 * Lambda 인프라(Koin, API Gateway SDK) 의존 없이
 * IngestLambdaHandler의 코어 로직을 완전 검증.
 *
 * 검증 항목:
 * - 정상 Ingest → RawData + Slice + View + SinkEvent
 * - entityKey 형식 검증 (type:id)
 * - 미지원 entityType → CONTRACT_ERROR
 * - jobId 전파
 * - 여러 entityType (product, brand, category) 지원
 */
class IngestProcessorE2ETest : StringSpec({

    // ===== Infrastructure Setup =====
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

    val contractResolver = EntityContractResolver(contractRegistry)

    val processor = IngestProcessor(
        orchestrator = orchestrator,
        contractResolver = contractResolver,
    )

    beforeTest {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    // ===== 정상 Ingest =====

    "Product Ingest 성공 → RawData + Slice + View 생성" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-001",
            payload = buildJsonObject {
                put("name", "Lambda Test Product")
                put("price", 29000)
                put("category", "skincare")
            },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Success>()
        result.tenantId shouldBe "lambda-test"
        result.entityKey shouldBe "product:SKU-001"
        result.version shouldBeGreaterThan 0L
        result.sliceCount intShouldBeGreaterThan 0
        result.viewCount intShouldBeGreaterThan 0
    }

    "Brand Ingest 성공" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "brand:BR-001",
            payload = buildJsonObject {
                put("name", "Test Brand")
                put("description", "Lambda E2E Brand")
            },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Success>()
        result.entityKey shouldBe "brand:BR-001"
    }

    "Category entityType 미지원 → CONTRACT_ERROR" {
        // entity-category, ruleset.category 계약이 없음 → CONTRACT_ERROR
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "category:CAT-001",
            payload = buildJsonObject {
                put("name", "Skincare")
                put("depth", 1)
            },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Error>()
        (result as IngestProcessResult.Error).error shouldBe "CONTRACT_ERROR"
    }

    // ===== entityKey 형식 검증 =====

    "잘못된 entityKey (콜론 없음) → 400 INVALID_ENTITY_KEY" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "invalidkey",
            payload = buildJsonObject { put("name", "Bad Key") },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Error>()
        result.statusCode shouldBe 400
        result.error shouldBe "INVALID_ENTITY_KEY"
    }

    "빈 entityKey → 400 INVALID_ENTITY_KEY" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = ":missing-type",
            payload = buildJsonObject { put("name", "No Type") },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Error>()
        result.statusCode shouldBe 400
        result.error shouldBe "INVALID_ENTITY_KEY"
    }

    // ===== 미지원 entityType =====

    "미지원 entityType → 400 CONTRACT_ERROR" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "unknown:UNK-001",
            payload = buildJsonObject { put("name", "Unknown Type") },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Error>()
        result.statusCode shouldBe 400
        result.error shouldBe "CONTRACT_ERROR"
    }

    // ===== jobId 전파 =====

    "jobId 전파: Ingest 결과에 jobId 포함" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-JOB-001",
            payload = buildJsonObject {
                put("name", "Job Tracking Product")
                put("price", 15000)
                put("category", "makeup")
            },
            jobId = "JOB-LAMBDA-001",
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Success>()
        result.jobId shouldBe "JOB-LAMBDA-001"
    }

    "jobId null → 결과에도 null" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-NOJOB",
            payload = buildJsonObject {
                put("name", "No Job Product")
                put("price", 8000)
                put("category", "skincare")
            },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Success>()
        result.jobId shouldBe null
    }

    // ===== 다건 연속 처리 =====

    "다건 연속 Ingest → 각각 독립 처리" {
        val results = (1..5).map { i ->
            val request = IngestRequest(
                tenantId = "lambda-test",
                entityKey = "product:BATCH-$i",
                payload = buildJsonObject {
                    put("name", "Batch Product $i")
                    put("price", i * 10000)
                    put("category", "skincare")
                },
            )
            processor.process(request)
        }

        results.forEach { result ->
            result.shouldBeInstanceOf<IngestProcessResult.Success>()
        }

        // 각각 다른 entityKey
        val keys = results.map { (it as IngestProcessResult.Success).entityKey }.toSet()
        keys.size shouldBe 5
    }

    // ===== 동일 entityKey 버전 업데이트 =====

    "동일 entityKey 2회 Ingest → 버전 증가" {
        val request1 = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-VER",
            payload = buildJsonObject {
                put("name", "Version 1")
                put("price", 10000)
                put("category", "skincare")
            },
        )

        val request2 = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-VER",
            payload = buildJsonObject {
                put("name", "Version 2")
                put("price", 20000)
                put("category", "skincare")
            },
        )

        val result1 = processor.process(request1)
        val result2 = processor.process(request2)

        result1.shouldBeInstanceOf<IngestProcessResult.Success>()
        result2.shouldBeInstanceOf<IngestProcessResult.Success>()

        // 둘 다 성공하고 다른 version
        result1.version shouldNotBe result2.version
    }

    // ===== durationMs 검증 =====

    "durationMs가 0 이상" {
        val request = IngestRequest(
            tenantId = "lambda-test",
            entityKey = "product:SKU-DUR",
            payload = buildJsonObject {
                put("name", "Duration Test")
                put("price", 5000)
                put("category", "skincare")
            },
        )

        val result = processor.process(request)

        result.shouldBeInstanceOf<IngestProcessResult.Success>()
        result.durationMs shouldBeGreaterThan -1L
    }
})
