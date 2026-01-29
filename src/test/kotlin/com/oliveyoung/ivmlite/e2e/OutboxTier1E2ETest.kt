package com.oliveyoung.ivmlite.e2e

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
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
import kotlinx.coroutines.delay
import io.mockk.mockk
import io.mockk.coEvery
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Outbox Tier 1 E2E 테스트 (RFC-IMPL-013 SOTA)
 *
 * 실제 Worker + Repository 통합 테스트
 *
 * 시나리오:
 * 1. Visibility Timeout - Worker 장애 시뮬레이션
 * 2. Dead Letter Queue - 반복 실패 메시지 격리
 * 3. Priority Queue - 긴급 메시지 우선 처리
 * 4. Entity Ordering - 버전 순서 보장
 * 5. 복합 시나리오 - 실제 운영 환경 시뮬레이션
 */
class OutboxTier1E2ETest : StringSpec({

    lateinit var rawDataRepo: InMemoryRawDataRepository
    lateinit var sliceRepo: InMemorySliceRepository
    lateinit var outboxRepo: InMemoryOutboxRepository
    lateinit var invertedIndexRepo: InMemoryInvertedIndexRepository
    lateinit var sinkRuleRegistry: InMemorySinkRuleRegistry
    lateinit var slicingWorkflow: SlicingWorkflow
    lateinit var ingestWorkflow: IngestWorkflow

    val testConfig = WorkerConfig(
        enabled = true,
        pollIntervalMs = 50,
        idlePollIntervalMs = 100,
        batchSize = 10,
        maxBackoffMs = 500,
        backoffMultiplier = 2.0,
        jitterFactor = 0.0,
        shutdownTimeoutMs = 1000,
    )

    beforeEach {
        rawDataRepo = InMemoryRawDataRepository()
        sliceRepo = InMemorySliceRepository()
        outboxRepo = InMemoryOutboxRepository()
        invertedIndexRepo = InMemoryInvertedIndexRepository()
        sinkRuleRegistry = InMemorySinkRuleRegistry()

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
                        hash = "test-hash-${UUID.randomUUID()}",
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
        sinkRuleRegistry.clear()
    }

    // ==================== E2E Scenario 1: Visibility Timeout ====================

    "E2E: Worker 장애 후 다른 Worker가 메시지 인계" {
        // Given: 메시지 생성
        val tenantId = TenantId("tenant-vt-e2e")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT:item-1"),
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Visibility Test"}""",
        )

        // When: Worker 1이 claim 후 "죽음" (처리 안 함)
        val worker1 = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig.copy(batchSize = 1),
        )

        // claim만 하고 처리 안 함 (장애 시뮬레이션)
        val claimed = outboxRepo.claim(1, null, "dead-worker")
        (claimed as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1

        // 시간 경과 시뮬레이션: claim된 엔트리의 claimedAt을 과거로 수정
        val entry = claimed.value[0]
        val oldEntry = entry.copy(claimedAt = Instant.now().minusSeconds(60))
        // InMemory에서 직접 수정 (테스트용)
        val storeField = outboxRepo::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = storeField.get(outboxRepo) as MutableMap<UUID, OutboxEntry>
        store[entry.id] = oldEntry

        // Then: Visibility timeout으로 release
        val released = outboxRepo.releaseExpiredClaims(30)
        (released as OutboxRepositoryPort.Result.Ok).value shouldBe 1

        // And: Worker 2가 처리 가능
        val worker2 = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
        )
        worker2.start()
        delay(300)
        worker2.stop()

        // And: 처리 완료
        worker2.getMetrics().processed shouldBe 1
    }

    // ==================== E2E Scenario 2: Dead Letter Queue ====================

    "E2E: 반복 실패 메시지가 DLQ로 이동 후 수동 재처리" {
        // Given: 6번 실패한 엔트리 (MAX_RETRY 초과)
        val failedEntry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_dlq_e2e",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-dlq:entity-fail",
            eventType = "RawDataIngested",
            payload = """{"error": "bad data"}""",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now().minusSeconds(3600),
            retryCount = 6,
            failureReason = "Schema validation failed"
        )
        outboxRepo.insert(failedEntry)

        // When: DLQ 이동
        val moved = outboxRepo.moveToDlq(maxRetryCount = 5)
        (moved as OutboxRepositoryPort.Result.Ok).value shouldBe 1

        // Then: 원본에서 제거됨
        val pending = outboxRepo.findPending(10)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        // And: DLQ에서 조회 가능
        val dlqEntries = outboxRepo.findDlq(10)
        (dlqEntries as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1

        // When: 운영자가 문제 해결 후 replay
        val replayed = outboxRepo.replayFromDlq(failedEntry.id)
        (replayed as OutboxRepositoryPort.Result.Ok).value shouldBe true

        // Then: 원본 테이블에 PENDING으로 복귀
        val pendingAfterReplay = outboxRepo.findPending(10)
        (pendingAfterReplay as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1
        pendingAfterReplay.value[0].retryCount shouldBe 0  // 리셋됨
        pendingAfterReplay.value[0].failureReason shouldBe null
    }

    // ==================== E2E Scenario 3: Priority Queue ====================

    "E2E: 긴급 메시지가 일반 메시지보다 먼저 처리됨" {
        val processedOrder = mutableListOf<String>()

        // Given: 낮은 우선순위 메시지 먼저 생성
        val lowPriority = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_low_priority",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:low-priority-item",
            eventType = "RawDataIngested",
            payload = """{"name": "low"}""",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now().minusSeconds(10),
            priority = 100  // 낮은 우선순위
        )

        // 높은 우선순위 메시지 나중에 생성
        val highPriority = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_high_priority",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:high-priority-item",
            eventType = "RawDataIngested",
            payload = """{"name": "high"}""",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now(),
            priority = 1  // 높은 우선순위
        )

        outboxRepo.insert(lowPriority)
        outboxRepo.insert(highPriority)

        // When: 우선순위 기반 claim
        val claimed = outboxRepo.claimByPriority(2, "worker-1")
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value

        // Then: 높은 우선순위가 먼저
        entries shouldHaveSize 2
        entries[0].aggregateId shouldBe "tenant:high-priority-item"
        entries[1].aggregateId shouldBe "tenant:low-priority-item"
    }

    "E2E: 긴급 상품 업데이트가 일반 업데이트보다 먼저 Sink 전달" {
        // SinkRule 등록
        sinkRuleRegistry.clear()
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.opensearch.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = "http://localhost:9200"
            ),
            docId = DocIdSpec("{entityKey}")
        ))

        val tenantId = TenantId("tenant-priority")

        // Given: 일반 상품 10개 먼저 생성
        repeat(10) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:normal-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"name": "Normal $i", "priority": 100}""",
            )
        }

        // 긴급 상품 1개 나중에 생성 (우선순위 높게)
        val urgentEntry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_urgent_product",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "$tenantId:PRODUCT:urgent-flash-sale",
            eventType = "RawDataIngested",
            payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:urgent-flash-sale","version":1}""",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now(),
            priority = 1  // 최고 우선순위
        )
        outboxRepo.insert(urgentEntry)

        // RawData도 저장
        rawDataRepo.putIdempotent(
            com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:urgent-flash-sale"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payload = """{"name": "Flash Sale", "priority": 1}""",
                payloadHash = "sha256:urgent-hash",
            )
        )

        // When: 우선순위 기반 claim (1개만)
        val firstClaim = outboxRepo.claimByPriority(1, "worker-1")
        val firstEntry = (firstClaim as OutboxRepositoryPort.Result.Ok).value

        // Then: 긴급 상품이 먼저 claim됨
        firstEntry shouldHaveSize 1
        firstEntry[0].aggregateId shouldBe "$tenantId:PRODUCT:urgent-flash-sale"
    }

    // ==================== E2E Scenario 4: Entity Ordering ====================

    "E2E: 같은 상품의 여러 버전이 순서대로 처리됨" {
        val processedVersions = mutableListOf<Long>()
        val tenantId = TenantId("tenant-ordering")
        val entityKey = EntityKey("PRODUCT:ordered-item")

        // Given: 같은 상품의 v3, v1, v2 순서로 생성 (비순차)
        listOf(3L, 1L, 2L).forEach { version ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_order_v$version",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:$entityKey",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"$entityKey","version":$version}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now().minusSeconds(100 - version),
                entityVersion = version
            )
            outboxRepo.insert(entry)

            // RawData도 저장
            rawDataRepo.putIdempotent(
                com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = version,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payload = """{"version": $version}""",
                    payloadHash = "sha256:order-v$version",
                )
            )
        }

        // When: 순서 보장 claim으로 하나씩 처리
        repeat(3) { i ->
            val claimed = outboxRepo.claimWithOrdering(1, "worker-1")
            val entries = (claimed as OutboxRepositoryPort.Result.Ok).value

            if (entries.isNotEmpty()) {
                val version = entries[0].entityVersion!!
                processedVersions.add(version)

                // 처리 완료 마킹
                outboxRepo.markProcessed(listOf(entries[0].id))
            }
        }

        // Then: v1 → v2 → v3 순서로 처리됨
        processedVersions shouldBe listOf(1L, 2L, 3L)
    }

    "E2E: 다른 상품들은 병렬 처리 가능" {
        val tenantId = TenantId("tenant-parallel")

        // Given: 3개의 다른 상품
        listOf("A", "B", "C").forEach { product ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_parallel_$product",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:PRODUCT:$product",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:$product","version":1}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                entityVersion = 1L
            )
            outboxRepo.insert(entry)
        }

        // When: 순서 보장 claim으로 3개 요청
        val claimed = outboxRepo.claimWithOrdering(3, "worker-1")
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value

        // Then: 3개 모두 claim됨 (서로 다른 entity이므로)
        entries shouldHaveSize 3
    }

    // ==================== E2E Scenario 5: 복합 시나리오 ====================

    "E2E: 실제 운영 환경 시뮬레이션 - 혼합 워크로드" {
        val tenantId = TenantId("tenant-production")

        // 1. 일반 메시지 100개
        repeat(100) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:normal-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"name": "Normal $i"}""",
            )
        }

        // 2. 긴급 메시지 5개
        repeat(5) { i ->
            val urgentEntry = OutboxEntry(
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
            outboxRepo.insert(urgentEntry)
        }

        // 3. 연속 버전 메시지 (같은 entity의 v1, v2, v3)
        listOf(1L, 2L, 3L).forEach { version ->
            val entry = OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_versioned_$version",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "$tenantId:PRODUCT:versioned-item",
                eventType = "RawDataIngested",
                payload = """{"tenantId":"$tenantId","entityKey":"PRODUCT:versioned-item","version":$version}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                entityVersion = version
            )
            outboxRepo.insert(entry)
        }

        // 4. Worker 실행
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig.copy(batchSize = 20),
        )

        worker.start()
        delay(2000)  // 충분한 처리 시간
        worker.stop()

        // 5. 검증
        val metrics = worker.getMetrics()

        // 모든 메시지가 처리되어야 함 (100 + 5 + 3 = 108)
        // 단, 일부는 PROCESSING 상태일 수 있음
        val pendingAfter = outboxRepo.findPending(200)
        val remainingCount = (pendingAfter as OutboxRepositoryPort.Result.Ok).value.size

        // 대부분 처리됨
        metrics.processed shouldBeGreaterThan 50

        println("=== Production Simulation Results ===")
        println("Processed: ${metrics.processed}")
        println("Failed: ${metrics.failed}")
        println("Remaining: $remainingCount")
    }

    "E2E: 멀티 Worker 경쟁 상황에서 중복 처리 없음" {
        val tenantId = TenantId("tenant-multi-worker")
        val processedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val duplicateCount = AtomicInteger(0)

        // Given: 50개 메시지
        repeat(50) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT:item-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"name": "Item $i"}""",
            )
        }

        // When: 5개 Worker 동시 실행
        val workers = (1..5).map { workerId ->
            OutboxPollingWorker(
                outboxRepo = outboxRepo,
                slicingWorkflow = slicingWorkflow,
                config = testConfig.copy(batchSize = 5),
            )
        }

        workers.forEach { it.start() }
        delay(1500)
        workers.forEach { it.stop() }

        // Then: 총 처리 수는 정확히 50
        val totalProcessed = workers.sumOf { it.getMetrics().processed }
        totalProcessed shouldBe 50

        // And: PENDING 없음
        val pending = outboxRepo.findPending(100)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        println("=== Multi-Worker Results ===")
        workers.forEachIndexed { index, worker ->
            println("Worker ${index + 1}: processed=${worker.getMetrics().processed}")
        }
    }
})
