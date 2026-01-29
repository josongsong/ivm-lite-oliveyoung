package com.oliveyoung.ivmlite.pkg.orchestration

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxEventTypes
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.mockk.mockk
import io.mockk.coEvery
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * OutboxPollingWorker 단위 테스트 (RFC-IMPL Phase B-2)
 *
 * 테스트 항목:
 * 1. Worker 시작/정지
 * 2. Polling → SlicingWorkflow 트리거
 * 3. 에러 핸들링 (markFailed)
 * 4. Graceful shutdown
 * 5. 메트릭 수집
 * 6. Backoff 동작
 */
class OutboxPollingWorkerTest : StringSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
    val slicingEngine = mockk<SlicingEnginePort>().also { engine ->
        coEvery { engine.slice(any(), any()) } answers {
            val rawData = firstArg<com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord>()
            val slices = listOf(
                SliceRecord(
                    tenantId = rawData.tenantId,
                    entityKey = rawData.entityKey,
                    version = rawData.version,
                    sliceType = SliceType.CORE,
                    data = rawData.payload,
                    hash = "test-hash",
                    ruleSetId = "test-ruleset",
                    ruleSetVersion = SemVer.parse("1.0.0"),
                ),
            )
            SlicingEnginePort.Result.Ok(SlicingEnginePort.SlicingResult(slices, emptyList()))
        }
    }
    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())
    val contractRegistry = mockk<ContractRegistryPort>()
    val slicingWorkflow = SlicingWorkflow(
        rawDataRepo,
        sliceRepo,
        slicingEngine,
        invertedIndexRepo,
        changeSetBuilder,
        impactCalculator,
        contractRegistry,
    )
    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)

    val testConfig = WorkerConfig(
        enabled = true,
        pollIntervalMs = 50,
        idlePollIntervalMs = 100,
        batchSize = 10,
        maxBackoffMs = 500,
        backoffMultiplier = 2.0,
        jitterFactor = 0.0, // 테스트에서는 jitter 비활성화
        shutdownTimeoutMs = 1000,
    )

    afterEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        outboxRepo.clear()
    }

    "Worker 시작/정지 기본 동작" {
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.isRunning() shouldBe false

        val started = worker.start()
        started shouldBe true
        worker.isRunning() shouldBe true

        // 두 번 시작 시도 → false
        worker.start() shouldBe false

        val stopped = worker.stop()
        stopped shouldBe true
        worker.isRunning() shouldBe false

        // 두 번 정지 시도 → false
        worker.stop() shouldBe false
    }

    "disabled 상태에서 start → false" {
        val disabledConfig = testConfig.copy(enabled = false)
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = disabledConfig,
        )

        worker.start() shouldBe false
        worker.isRunning() shouldBe false
    }

    "RawDataIngested 이벤트 → SlicingWorkflow 트리거" {
        val tenantId = TenantId("tenant-polling-1")
        val entityKey = EntityKey("PRODUCT#tenant-polling-1#test-1")
        val version = 1L

        // 1. RawData 저장 (IngestWorkflow가 자동으로 Outbox에도 저장)
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Polling Test"}""",
        )

        // 2. Worker 실행
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(300) // Polling 여러 번 발생하도록 대기
        worker.stop()

        // 4. 검증
        val metrics = worker.getMetrics()
        metrics.processed shouldBe 1
        metrics.failed shouldBe 0
        metrics.polls shouldBeGreaterThan 0
    }

    "여러 Outbox 엔트리 배치 처리" {
        val tenantId = TenantId("tenant-batch")

        // 5개 RawData 저장 (IngestWorkflow가 자동으로 Outbox에도 저장)
        repeat(5) { i ->
            val entityKey = EntityKey("PRODUCT#tenant-batch#item-$i")
            val version = (i + 1).toLong()

            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(500) // 충분한 처리 시간
        worker.stop()

        val metrics = worker.getMetrics()
        metrics.processed shouldBe 5
    }

    "잘못된 payload → markFailed 호출" {
        // 잘못된 JSON payload
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:key",
            eventType = OutboxEventTypes.RAW_DATA_INGESTED,
            payload = """{"invalid": "json structure"}""", // tenantId/entityKey/version 없음
        )
        outboxRepo.insert(entry)

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(300)
        worker.stop()

        val metrics = worker.getMetrics()
        metrics.processed shouldBe 0
        metrics.failed shouldBe 1
    }

    "RawData 없는 상태에서 Slicing 실패 → markFailed" {
        // RawData 저장 없이 Outbox만 생성
        val payload = OutboxPollingWorker.RawDataIngestedPayload(
            tenantId = "tenant-no-raw",
            entityKey = "PRODUCT#tenant-no-raw#missing",
            version = 1L,
        )
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-no-raw:PRODUCT#tenant-no-raw#missing",
            eventType = OutboxEventTypes.RAW_DATA_INGESTED,
            payload = Json.encodeToString(payload),
        )
        outboxRepo.insert(entry)

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(300)
        worker.stop()

        val metrics = worker.getMetrics()
        metrics.processed shouldBe 0
        metrics.failed shouldBe 1
    }

    "Graceful shutdown - 진행 중인 배치 완료 후 종료" {
        val tenantId = TenantId("tenant-shutdown")

        // 10개 RawData 저장 (IngestWorkflow가 자동으로 Outbox에도 저장)
        repeat(10) { i ->
            val entityKey = EntityKey("PRODUCT#tenant-shutdown#item-$i")

            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = (i + 1).toLong(),
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig.copy(shutdownTimeoutMs = 5000),
        )

        worker.start()
        delay(100) // 약간의 처리 후 shutdown
        worker.stop()

        // Graceful shutdown이므로 일부 또는 전부 처리됨
        val metrics = worker.getMetrics()
        metrics.processed shouldBeGreaterThan 0
    }

    "메트릭 초기값 검증" {
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        val metrics = worker.getMetrics()
        metrics.processed shouldBe 0
        metrics.failed shouldBe 0
        metrics.polls shouldBe 0
        metrics.currentBackoffMs shouldBe 0
        metrics.isRunning shouldBe false
    }

    // 매개변수화 테스트: 다양한 AggregateType 이벤트 처리 (no-op in v1)
    listOf(
        Triple(AggregateType.SLICE, "tenant:slice-key", OutboxEventTypes.SLICE_CREATED),
        Triple(AggregateType.CHANGESET, "tenant:changeset-key", OutboxEventTypes.CHANGESET_CREATED),
    ).forEach { (aggregateType, aggregateId, eventType) ->
        "${aggregateType.name} 이벤트 → EventHandler 호출 (no-op in v1)" {
            val entry = OutboxEntry.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = """{"id": "test"}""",
            )
            outboxRepo.insert(entry)

            val worker = OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = testConfig,
            )

            worker.start()
            delay(300)
            worker.stop()

            // DefaultEventHandler는 no-op이므로 processed
            val metrics = worker.getMetrics()
            metrics.processed shouldBe 1
        }
    }

    // ==================== Claim 기반 동작 테스트 ====================

    "claim 기반 처리 - PROCESSING 상태로 전환 후 처리" {
        val tenantId = TenantId("tenant-claim-test")
        val entityKey = EntityKey("PRODUCT#tenant-claim-test#item-1")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Claim Test"}""",
        )

        // 처리 전 상태 확인
        val beforePending = outboxRepo.findPending(10)
        (beforePending as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value.size shouldBe 1

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(300)
        worker.stop()

        // 처리 후: PENDING 없음, PROCESSED 1개
        val afterPending = outboxRepo.findPending(10)
        (afterPending as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value.size shouldBe 0

        val metrics = worker.getMetrics()
        metrics.processed shouldBe 1
    }

    "stale PROCESSING 엔트리 자동 복구" {
        // 오래된 PROCESSING 상태의 엔트리 직접 생성
        val staleEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_stale_test",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-stale:entity-stale",
            eventType = OutboxEventTypes.RAW_DATA_INGESTED,
            payload = Json.encodeToString(
                OutboxPollingWorker.RawDataIngestedPayload(
                    tenantId = "tenant-stale",
                    entityKey = "PRODUCT#tenant-stale#stale-item",
                    version = 1L,
                )
            ),
            status = com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus.PROCESSING,
            createdAt = java.time.Instant.now().minusSeconds(1000),
            claimedAt = java.time.Instant.now().minusSeconds(600), // 10분 전 claim
            claimedBy = "dead-worker",
        )
        outboxRepo.insert(staleEntry)

        // RawData도 저장 (Slicing을 위해)
        val stalePayload = """{"name": "Stale Test"}"""
        rawDataRepo.putIdempotent(
            com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord(
                tenantId = TenantId("tenant-stale"),
                entityKey = EntityKey("PRODUCT#tenant-stale#stale-item"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payload = stalePayload,
                payloadHash = "sha256:stale-test-hash",
            )
        )

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )

        worker.start()
        delay(500) // recovery + 처리 시간
        worker.stop()

        // stale 엔트리가 복구되어 처리됨
        val metrics = worker.getMetrics()
        metrics.processed shouldBeGreaterThan 0
    }

    "여러 worker 시뮬레이션 - 중복 처리 없음" {
        val tenantId = TenantId("tenant-multi-worker")

        // 20개 엔트리 생성
        repeat(20) { i ->
            val entityKey = EntityKey("PRODUCT#tenant-multi-worker#item-$i")
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = (i + 1).toLong(),
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // 3개 worker 동시 실행
        val workers = (1..3).map {
            OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = testConfig.copy(batchSize = 5),
            )
        }

        workers.forEach { it.start() }
        delay(1000) // 처리 시간
        workers.forEach { it.stop() }

        // 총 처리 수는 정확히 20개
        val totalProcessed = workers.sumOf { it.getMetrics().processed }
        totalProcessed shouldBe 20

        // PENDING 없음
        val pending = outboxRepo.findPending(100)
        (pending as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value.size shouldBe 0
    }
})
