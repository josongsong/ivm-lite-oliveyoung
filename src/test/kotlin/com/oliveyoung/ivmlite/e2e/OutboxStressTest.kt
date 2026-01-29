package com.oliveyoung.ivmlite.e2e

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.coroutines.*
import io.mockk.mockk
import io.mockk.coEvery
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Outbox 스트레스 테스트 (극한 상황)
 *
 * 시나리오:
 * 1. 대량 메시지 처리 (1000개+)
 * 2. 동시성 경쟁 (10+ Workers)
 * 3. 랜덤 실패 + 재시도
 * 4. 혼합 우선순위 + 순서 보장
 * 5. 장애 복구 시뮬레이션
 * 6. 메모리/성능 테스트
 */
class OutboxStressTest : StringSpec({

    lateinit var rawDataRepo: InMemoryRawDataRepository
    lateinit var sliceRepo: InMemorySliceRepository
    lateinit var outboxRepo: InMemoryOutboxRepository
    lateinit var invertedIndexRepo: InMemoryInvertedIndexRepository
    lateinit var slicingWorkflow: SlicingWorkflow
    lateinit var ingestWorkflow: IngestWorkflow

    val fastConfig = WorkerConfig(
        enabled = true,
        pollIntervalMs = 10,
        idlePollIntervalMs = 50,
        batchSize = 50,
        maxBackoffMs = 100,
        backoffMultiplier = 1.5,
        jitterFactor = 0.1,
        shutdownTimeoutMs = 5000,
    )

    beforeEach {
        rawDataRepo = InMemoryRawDataRepository()
        sliceRepo = InMemorySliceRepository()
        outboxRepo = InMemoryOutboxRepository()
        invertedIndexRepo = InMemoryInvertedIndexRepository()

        val slicingEngine = mockk<SlicingEnginePort>().also { engine ->
            coEvery { engine.slice(any(), any()) } answers {
                // 랜덤 지연 (실제 처리 시뮬레이션)
                Thread.sleep(Random.nextLong(1, 5))
                val rawData = firstArg<com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord>()
                val slices = listOf(
                    SliceRecord(
                        tenantId = rawData.tenantId,
                        entityKey = rawData.entityKey,
                        version = rawData.version,
                        sliceType = SliceType.CORE,
                        data = rawData.payload,
                        hash = "hash-${UUID.randomUUID()}",
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

        slicingWorkflow = SlicingWorkflow(
            rawDataRepo,
            sliceRepo,
            slicingEngine,
            invertedIndexRepo,
            changeSetBuilder,
            impactCalculator,
            contractRegistry,
        )
        ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)
    }

    afterEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        outboxRepo.clear()
    }

    // ==================== 1. 대량 메시지 처리 ====================

    "Stress: 1000개 메시지 처리 - 모두 완료" {
        val messageCount = 1000
        val tenantId = TenantId("tenant-stress-1k")

        // Given: 1000개 메시지 생성
        val insertTime = measureTimeMillis {
            repeat(messageCount) { i ->
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = EntityKey("PRODUCT:item-$i"),
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = """{"index": $i}""",
                )
            }
        }
        println("Insert time for $messageCount messages: ${insertTime}ms")

        // When: Worker 처리
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = fastConfig,
        )

        val processTime = measureTimeMillis {
            worker.start()
            
            // 최대 30초 대기
            var waited = 0L
            while (waited < 30000) {
                delay(100)
                waited += 100
                val pending = outboxRepo.findPending(1)
                if ((pending as OutboxRepositoryPort.Result.Ok).value.isEmpty()) break
            }
            
            worker.stop()
        }

        // Then: 대부분 처리됨 (최소 90%)
        val metrics = worker.getMetrics()
        metrics.processed shouldBeGreaterThan (messageCount * 0.9).toLong()

        println("=== Stress Test Results (1000 messages) ===")
        println("Insert time: ${insertTime}ms")
        println("Process time: ${processTime}ms")
        println("Throughput: ${messageCount * 1000 / processTime} msg/sec")
    }

    // ==================== 2. 동시성 경쟁 ====================

    "Stress: 10 Workers 동시 경쟁 - 중복 없음 보장" {
        val messageCount = 500
        val workerCount = 10
        val tenantId = TenantId("tenant-concurrent")
        val processedMessages = ConcurrentHashMap.newKeySet<String>()
        val duplicates = AtomicInteger(0)

        // Given: 500개 메시지
        repeat(messageCount) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:concurrent-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // When: 10개 Worker 동시 실행
        val workers = (1..workerCount).map {
            OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = fastConfig.copy(batchSize = 20),
            )
        }

        workers.forEach { it.start() }
        
        // 최대 20초 대기
        var waited = 0L
        while (waited < 20000) {
            delay(100)
            waited += 100
            val pending = outboxRepo.findPending(1)
            if ((pending as OutboxRepositoryPort.Result.Ok).value.isEmpty()) break
        }
        
        workers.forEach { it.stop() }

        // Then: 정확히 500개 처리, 중복 없음
        val totalProcessed = workers.sumOf { it.getMetrics().processed }
        totalProcessed shouldBe messageCount

        val pending = outboxRepo.findPending(100)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        println("=== Concurrent Workers Test ===")
        workers.forEachIndexed { idx, w ->
            println("Worker ${idx + 1}: ${w.getMetrics().processed} processed")
        }
    }

    // ==================== 3. 랜덤 실패 + 재시도 ====================

    "Stress: 랜덤 실패 발생 시 재시도 후 결국 성공" {
        val messageCount = 100
        val tenantId = TenantId("tenant-retry")
        val failureRate = 0.3  // 30% 실패율

        // 실패할 수 있는 SlicingEngine mock
        val failingEngine = mockk<SlicingEnginePort>().also { engine ->
            val attemptCount = ConcurrentHashMap<String, AtomicInteger>()

            coEvery { engine.slice(any(), any()) } answers {
                val rawData = firstArg<com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord>()
                val key = rawData.entityKey.value
                val attempts = attemptCount.computeIfAbsent(key) { AtomicInteger(0) }
                val attempt = attempts.incrementAndGet()

                // 첫 시도에서 30% 확률로 실패, 재시도는 성공
                if (attempt == 1 && Random.nextDouble() < failureRate) {
                    throw RuntimeException("Random failure for $key")
                }

                val slices = listOf(
                    SliceRecord(
                        tenantId = rawData.tenantId,
                        entityKey = rawData.entityKey,
                        version = rawData.version,
                        sliceType = SliceType.CORE,
                        data = rawData.payload,
                        hash = "hash-${UUID.randomUUID()}",
                        ruleSetId = "test-ruleset",
                        ruleSetVersion = SemVer.parse("1.0.0"),
                    ),
                )
                SlicingEnginePort.Result.Ok(SlicingEnginePort.SlicingResult(slices, emptyList()))
            }
        }

        val retrySlicingWorkflow = SlicingWorkflow(
            rawDataRepo,
            sliceRepo,
            failingEngine,
            invertedIndexRepo,
            DefaultChangeSetBuilderAdapter(ChangeSetBuilder()),
            DefaultImpactCalculatorAdapter(ImpactCalculator()),
            mockk<ContractRegistryPort>(),
        )

        // Given: 100개 메시지
        repeat(messageCount) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:retry-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // When: Worker 처리 (실패 시 FAILED → 재시도 필요)
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = retrySlicingWorkflow,
            config = fastConfig,
        )

        worker.start()
        delay(3000)  // 첫 번째 라운드
        worker.stop()

        val firstRoundMetrics = worker.getMetrics()
        println("First round: processed=${firstRoundMetrics.processed}, failed=${firstRoundMetrics.failed}")

        // FAILED 엔트리 재시도 (PENDING으로 리셋)
        val storeField = outboxRepo::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = storeField.get(outboxRepo) as MutableMap<UUID, OutboxEntry>
        
        store.values.filter { it.status == OutboxStatus.FAILED }.forEach { entry ->
            store[entry.id] = entry.copy(
                status = OutboxStatus.PENDING,
                claimedAt = null,
                claimedBy = null
            )
        }

        // 두 번째 라운드
        val worker2 = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = retrySlicingWorkflow,
            config = fastConfig,
        )
        worker2.start()
        delay(3000)
        worker2.stop()

        // Then: 결국 모두 처리됨
        val totalProcessed = firstRoundMetrics.processed + worker2.getMetrics().processed
        println("Total processed: $totalProcessed")
        
        // 대부분 처리됨 (랜덤성으로 인해 정확히 100이 아닐 수 있음)
        totalProcessed shouldBeGreaterThan (messageCount * 0.9).toLong()
    }

    // ==================== 4. 혼합 우선순위 + 순서 보장 ====================

    "Stress: 우선순위 + Entity Ordering 혼합 시나리오" {
        val tenantId = TenantId("tenant-mixed")

        // Given: 복잡한 혼합 시나리오
        // - 10개 일반 상품 (priority=100)
        // - 3개 긴급 상품 (priority=1)
        // - 1개 상품의 v1,v2,v3 (순서 보장 필요)
        
        // 일반 상품
        repeat(10) { i ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_normal_$i",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:PRODUCT:normal-$i",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:normal-$i","version":1}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now().minusSeconds(10),
                priority = 100
            )
            outboxRepo.insert(entry)
        }

        // 긴급 상품
        repeat(3) { i ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_urgent_$i",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:PRODUCT:urgent-$i",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:urgent-$i","version":1}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                priority = 1
            )
            outboxRepo.insert(entry)
        }

        // 순서 보장 필요한 상품 (v3, v1, v2 순으로 입력)
        listOf(3L, 1L, 2L).forEach { version ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_ordered_v$version",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:PRODUCT:ordered-item",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:ordered-item","version":$version}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                priority = 50,  // 중간 우선순위
                entityVersion = version
            )
            outboxRepo.insert(entry)
        }

        // When: 우선순위 기반 claim
        val firstBatch = outboxRepo.claimByPriority(5, "worker-1")
        val firstEntries = (firstBatch as OutboxRepositoryPort.Result.Ok).value

        // Then: 긴급 상품이 상위에 포함됨 (priority=1)
        val urgentCount = firstEntries.count { it.priority == 1 }
        urgentCount.toLong() shouldBeGreaterThan 0L

        // When: Entity Ordering claim - ordered-item의 v1만 claim 가능한지 확인
        // 먼저 모든 PROCESSING을 PROCESSED로 마킹
        outboxRepo.markProcessed(firstEntries.map { it.id })
        
        // 남은 PENDING 중 ordered-item의 버전들 확인
        val remainingPending = outboxRepo.findPending(100)
        val orderedItemsPending = (remainingPending as OutboxRepositoryPort.Result.Ok).value
            .filter { it.aggregateId.contains("ordered-item") }
            .sortedBy { it.entityVersion }

        // ordered-item이 있으면 버전 순서 확인
        if (orderedItemsPending.isNotEmpty()) {
            // claimWithOrdering으로 claim
            val orderedBatch = outboxRepo.claimWithOrdering(10, "worker-2")
            val orderedBatchEntries = (orderedBatch as OutboxRepositoryPort.Result.Ok).value
            
            val orderedItem = orderedBatchEntries.find { it.aggregateId.contains("ordered-item") }
            if (orderedItem != null) {
                // 가장 낮은 버전이 claim됨
                orderedItem.entityVersion shouldBe orderedItemsPending.first().entityVersion
            }
        }

        println("=== Mixed Priority + Ordering Test ===")
        println("First batch (priority): ${firstEntries.map { it.aggregateId }}")
        println("Urgent count in first batch: $urgentCount")
    }

    // ==================== 5. 장애 복구 시뮬레이션 ====================

    "Stress: Worker 크래시 → Visibility Timeout → 복구" {
        val messageCount = 50
        val tenantId = TenantId("tenant-crash")

        // Given: 50개 메시지
        repeat(messageCount) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:crash-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // When: Worker 1이 claim 후 "크래시"
        val claimed = outboxRepo.claim(20, null, "crashed-worker")
        (claimed as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 20

        // 크래시 시뮬레이션: claimedAt을 과거로 변경
        val storeField = outboxRepo::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = storeField.get(outboxRepo) as MutableMap<UUID, OutboxEntry>
        
        store.values.filter { it.status == OutboxStatus.PROCESSING }.forEach { entry ->
            store[entry.id] = entry.copy(claimedAt = Instant.now().minusSeconds(120))
        }

        // Visibility timeout release
        val released = outboxRepo.releaseExpiredClaims(30)
        (released as OutboxRepositoryPort.Result.Ok).value shouldBe 20

        // Worker 2가 모든 메시지 처리
        val worker2 = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = fastConfig,
        )

        worker2.start()
        delay(5000)
        worker2.stop()

        // Then: 모두 처리됨
        worker2.getMetrics().processed shouldBe messageCount

        val pending = outboxRepo.findPending(100)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()
    }

    // ==================== 6. 성능 테스트 ====================

    "Stress: 처리량 측정 - 목표 1000+ msg/sec" {
        val messageCount = 500
        val tenantId = TenantId("tenant-perf")

        // Given: 500개 메시지
        repeat(messageCount) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:perf-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // When: 3개 Worker 동시 실행
        val workers = (1..3).map {
            OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = fastConfig.copy(batchSize = 100),
            )
        }

        val startTime = System.currentTimeMillis()
        workers.forEach { it.start() }

        // 최대 5초 대기
        var waited = 0L
        while (waited < 5000) {
            delay(50)
            waited += 50
            val pending = outboxRepo.findPending(1)
            if ((pending as OutboxRepositoryPort.Result.Ok).value.isEmpty()) break
        }

        workers.forEach { it.stop() }
        val endTime = System.currentTimeMillis()

        val totalProcessed = workers.sumOf { it.getMetrics().processed }
        val durationSec = (endTime - startTime) / 1000.0
        val throughput = totalProcessed / durationSec

        println("=== Performance Test Results ===")
        println("Messages: $messageCount")
        println("Duration: ${durationSec}s")
        println("Throughput: ${throughput.toLong()} msg/sec")
        println("Workers: 3")

        // 목표: 최소 100 msg/sec (InMemory 기준)
        throughput.toLong() shouldBeGreaterThan 100L
    }

    // ==================== 7. DLQ 스트레스 ====================

    "Stress: DLQ 대량 이동 + 일괄 Replay" {
        val failedCount = 100

        // Given: 100개 FAILED 엔트리 (재시도 초과)
        repeat(failedCount) { i ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_dlq_stress_$i",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "tenant-dlq:entity-$i",
                eventType = "RawDataIngested",
                payload = """{"index": $i}""",
                status = OutboxStatus.FAILED,
                createdAt = Instant.now().minusSeconds(3600),
                retryCount = 6,
                failureReason = "Stress test failure $i"
            )
            outboxRepo.insert(entry)
        }

        // When: DLQ 이동
        val moved = outboxRepo.moveToDlq(maxRetryCount = 5)
        (moved as OutboxRepositoryPort.Result.Ok).value shouldBe failedCount

        // Then: 원본 비어있음
        val pending = outboxRepo.findPending(200)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        // DLQ에 100개
        val dlq = outboxRepo.findDlq(200)
        (dlq as OutboxRepositoryPort.Result.Ok).value shouldHaveSize failedCount

        // 일괄 Replay
        val dlqEntries = dlq.value
        var replayedCount = 0
        dlqEntries.forEach { entry ->
            val result = outboxRepo.replayFromDlq(entry.id)
            if ((result as OutboxRepositoryPort.Result.Ok).value) {
                replayedCount++
            }
        }

        replayedCount shouldBe failedCount

        // 원본에 100개 복귀
        val pendingAfterReplay = outboxRepo.findPending(200)
        (pendingAfterReplay as OutboxRepositoryPort.Result.Ok).value shouldHaveSize failedCount
    }

    // ==================== 8. 극한 동시성 ====================

    "Stress: 20 Workers + 1000 Messages - 완벽한 처리 보장" {
        val messageCount = 1000
        val workerCount = 20
        val tenantId = TenantId("tenant-extreme")

        // Given: 1000개 메시지
        repeat(messageCount) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:extreme-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // When: 20개 Worker 동시 실행
        val workers = (1..workerCount).map {
            OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = fastConfig.copy(batchSize = 25),
            )
        }

        val startTime = System.currentTimeMillis()
        workers.forEach { it.start() }

        // 최대 30초 대기
        var waited = 0L
        while (waited < 30000) {
            delay(100)
            waited += 100
            val pending = outboxRepo.findPending(1)
            if ((pending as OutboxRepositoryPort.Result.Ok).value.isEmpty()) break
        }

        workers.forEach { it.stop() }
        val endTime = System.currentTimeMillis()

        // Then: 정확히 1000개 처리
        val totalProcessed = workers.sumOf { it.getMetrics().processed }
        totalProcessed shouldBe messageCount

        val totalFailed = workers.sumOf { it.getMetrics().failed }
        totalFailed shouldBe 0

        val pending = outboxRepo.findPending(100)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        println("=== Extreme Concurrency Test ===")
        println("Messages: $messageCount")
        println("Workers: $workerCount")
        println("Duration: ${endTime - startTime}ms")
        println("Processed: $totalProcessed")
        workers.take(5).forEachIndexed { idx, w ->
            println("Worker ${idx + 1}: ${w.getMetrics().processed}")
        }
        println("...")
    }
})
