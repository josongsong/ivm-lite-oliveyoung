package com.oliveyoung.ivmlite.pkg.rawdata.application

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
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
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.TransactionPort
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IngestionOrchestratorTest : DescribeSpec({

    // === Shared Repositories ===
    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()

    // === Domain Services ===
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

    fun createOrchestrator(
        transactionPort: TransactionPort = NoOpTransactionAdapter()
    ) = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = transactionPort,
        sinkRuleRegistry = sinkRuleRegistry
    )

    fun productCommand(
        entityKey: String,
        name: String = "Test Product",
        price: Int = 29000,
        version: Long = 1L,
        jobId: String? = null
    ) = IngestionCommand(
        tenantId = TenantId("test-tenant"),
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

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("올인원 처리: Raw -> Slicing -> View -> SinkEvent") {

        it("성공: 단일 엔티티 처리") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:orch-001")

            val result = orchestrator.ingest(command)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val r = (result as Result.Ok<*>).value as IngestionResult
            r.tenantId shouldBe "test-tenant"
            r.entityKey shouldBe "product:orch-001"
            assert(r.sliceCount >= 1) { "sliceCount should be >= 1 but was ${r.sliceCount}" }
            assert(r.viewCount >= 1) { "viewCount should be >= 1 but was ${r.viewCount}" }
            r.sinkPending shouldBe true
            assert(r.durationMs >= 0) { "durationMs should be >= 0 but was ${r.durationMs}" }
        }

        it("SinkEvent PENDING 상태로 자동 발행") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:sink-001")

            orchestrator.ingest(command)

            @Suppress("UNCHECKED_CAST")
            val events = (sinkEventRepo.findByStatus("PENDING", 100) as Result.Ok<List<SinkEvent>>).value
            events shouldHaveSize 1
            events[0].entityKey shouldBe "product:sink-001"
            assert(events[0].sinkTargets.isNotEmpty()) { "sinkTargets should not be empty" }
        }
    }

    describe("jobId 전파") {

        it("jobId가 SinkEvent까지 전파") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:job-001", jobId = "job-abc-123")

            orchestrator.ingest(command)

            @Suppress("UNCHECKED_CAST")
            val events = (sinkEventRepo.findByJobId("job-abc-123") as Result.Ok<List<SinkEvent>>).value
            events shouldHaveSize 1
            events[0].jobId shouldBe "job-abc-123"
        }

        it("jobId null이면 SinkEvent에도 null") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:job-null")

            orchestrator.ingest(command)

            @Suppress("UNCHECKED_CAST")
            val events = (sinkEventRepo.findByStatus("PENDING", 100) as Result.Ok<List<SinkEvent>>).value
            events shouldHaveSize 1
            events[0].jobId shouldBe null
        }
    }

    describe("멱등성") {

        it("동일 command 2번 실행 시 RawData 중복 저장 안됨") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:idempotent-001", version = 10L)

            val result1 = orchestrator.ingest(command)
            val result2 = orchestrator.ingest(command)

            result1.shouldBeInstanceOf<Result.Ok<*>>()
            result2.shouldBeInstanceOf<Result.Ok<*>>()
            // RawData는 1건만 저장
            rawDataRepo.size() shouldBe 1
        }
    }

    describe("트랜잭션 실패 시 롤백") {

        it("TransactionPort 실패 시 Result.Err 반환") {
            val failingTransaction = object : TransactionPort {
                override suspend fun <T> execute(block: suspend () -> Result<T>): Result<T> =
                    Result.Err(DomainError.StorageError("Transaction failed"))
            }
            val orchestrator = createOrchestrator(transactionPort = failingTransaction)
            val command = productCommand("product:tx-fail")

            val result = orchestrator.ingest(command)

            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("다중 엔티티 처리") {

        it("서로 다른 엔티티 독립적으로 처리") {
            val orchestrator = createOrchestrator()

            val r1 = orchestrator.ingest(productCommand("product:multi-001", name = "Product A", version = 1L))
            val r2 = orchestrator.ingest(productCommand("product:multi-002", name = "Product B", version = 2L))

            r1.shouldBeInstanceOf<Result.Ok<*>>()
            r2.shouldBeInstanceOf<Result.Ok<*>>()

            @Suppress("UNCHECKED_CAST")
            val events = (sinkEventRepo.findByStatus("PENDING", 100) as Result.Ok<List<SinkEvent>>).value
            events shouldHaveSize 2
        }
    }

    describe("SinkRule 미매칭") {

        it("SinkRule 없으면 SinkEvent 미발행") {
            sinkRuleRegistry.clear()
            val orchestrator = createOrchestrator()
            val command = productCommand("product:no-sink", version = 50L)

            val result = orchestrator.ingest(command)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val r = (result as Result.Ok<*>).value as IngestionResult
            r.sinkPending shouldBe false
            sinkEventRepo.size() shouldBe 0
        }
    }

    describe("IngestionResult 필드 검증") {

        it("durationMs >= 0") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:dur-001", version = 60L)

            val result = orchestrator.ingest(command)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val r = (result as Result.Ok<*>).value as IngestionResult
            assert(r.durationMs >= 0) { "durationMs should be non-negative" }
        }

        it("version이 command의 version과 일치") {
            val orchestrator = createOrchestrator()
            val command = productCommand("product:ver-001", version = 777L)

            val result = orchestrator.ingest(command)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val r = (result as Result.Ok<*>).value as IngestionResult
            r.version shouldBe 777L
        }
    }
})
