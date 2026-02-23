package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionResult
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SOTA Ingest → Query E2E Test
 *
 * IngestionOrchestrator 기반 올인원 처리 검증.
 * Ingest 1번 호출 → RawData + Slicing + View + SinkEvent 자동 완료.
 */
class IngestQueryE2ETest : StringSpec({

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
    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val productRuleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
    val productViewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
    val productViewDefVersion = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    fun productCommand(
        tenantId: String,
        entityKey: String,
        name: String = "Test Product",
        price: Int = 29000,
        version: Long = 1L,
        jobId: String? = null
    ) = IngestionCommand(
        tenantId = TenantId(tenantId),
        entityKey = EntityKey(entityKey),
        data = buildJsonObject {
            put("name", name)
            put("price", price)
            put("category", "skincare")
        },
        ruleSetRef = productRuleSetRef,
        viewDefId = productViewDefId,
        viewDefVersion = productViewDefVersion,
        version = version,
        jobId = jobId
    )

    beforeTest {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    "올인원: Ingest 1번 → Raw + Slice + View + SinkEvent 모두 생성" {
        val result = orchestrator.ingest(
            productCommand("e2e-tenant", "product:e2e-001", name = "SOTA Product", price = 39900)
        )

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val r = (result as Result.Ok<*>).value as IngestionResult
        r.tenantId shouldBe "e2e-tenant"
        r.entityKey shouldBe "product:e2e-001"
        assert(r.sliceCount >= 1) { "sliceCount should be >= 1 but was ${r.sliceCount}" }
        assert(r.viewCount >= 1) { "viewCount should be >= 1 but was ${r.viewCount}" }
        r.sinkPending shouldBe true

        // SinkEvent가 생성되었으므로 Query 가능 상태 (1 View = 1 SinkEvent)
        runBlocking {
            when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                is Result.Ok -> {
                    val events = r.value.filter {
                        it.tenantId == "e2e-tenant" && it.entityKey == "product:e2e-001" && it.version == 1L
                    }
                    events.isNotEmpty() shouldBe true
                }
                is Result.Err -> throw AssertionError("Expected to find sink events")
            }
        }
    }

    "올인원: Ingest 응답에 sliceCount, viewCount, sinkPending 포함" {
        val result = orchestrator.ingest(
            productCommand("meta-tenant", "product:meta-001")
        )

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val r = (result as Result.Ok<*>).value as IngestionResult
        assert(r.sliceCount >= 1)
        assert(r.viewCount >= 1)
        r.sinkPending shouldBe true
        assert(r.durationMs >= 0)
    }

    "올인원: jobId 전파 (Ingest → SinkEvent)" {
        val myJobId = "e2e-job-123"
        val result = orchestrator.ingest(
            productCommand("job-tenant", "product:job-001", jobId = myJobId)
        )

        result.shouldBeInstanceOf<Result.Ok<*>>()

        @Suppress("UNCHECKED_CAST")
        val events = (sinkEventRepo.findByJobId(myJobId) as Result.Ok<List<SinkEvent>>).value
        events shouldHaveSize 1
        events[0].jobId shouldBe myJobId
        events[0].entityKey shouldBe "product:job-001"
    }

    "버전 독립성: v1 Ingest → v2 Ingest → 각각 독립 View 생성" {
        // v1 Ingest
        val r1 = orchestrator.ingest(
            productCommand("ver-tenant", "product:ver-001", name = "Version 1", price = 10000, version = 1L)
        )
        r1.shouldBeInstanceOf<Result.Ok<*>>()

        // v2 Ingest
        val r2 = orchestrator.ingest(
            productCommand("ver-tenant", "product:ver-001", name = "Version 2", price = 20000, version = 2L)
        )
        r2.shouldBeInstanceOf<Result.Ok<*>>()

        // v1, v2 각각 SinkEvent 존재
        runBlocking {
            when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                is Result.Ok -> {
                    val eventsV1 = r.value.filter {
                        it.tenantId == "ver-tenant" && it.entityKey == "product:ver-001" && it.version == 1L
                    }
                    val eventsV2 = r.value.filter {
                        it.tenantId == "ver-tenant" && it.entityKey == "product:ver-001" && it.version == 2L
                    }
                    eventsV1.isNotEmpty() shouldBe true
                    eventsV2.isNotEmpty() shouldBe true
                }
                is Result.Err -> throw AssertionError("Expected to find sink events")
            }
        }
    }

    "멱등성: 동일 Ingest 2번 호출 → 에러 없이 처리" {
        val cmd = productCommand("idem-tenant", "product:idem-001", version = 1L)

        val r1 = orchestrator.ingest(cmd)
        val r2 = orchestrator.ingest(cmd)

        r1.shouldBeInstanceOf<Result.Ok<*>>()
        r2.shouldBeInstanceOf<Result.Ok<*>>()

        // RawData는 1건만
        rawDataRepo.size() shouldBe 1
    }
})
