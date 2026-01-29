package com.oliveyoung.ivmlite.pkg.rawdata

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Tier 1 Features TDD 테스트 (RFC-IMPL-013 확장)
 *
 * 1. Visibility Timeout
 * 2. Dead Letter Queue (DLQ)
 * 3. Priority Queue
 * 4. Entity-Level Ordering
 */
class OutboxTier1FeaturesTest : StringSpec({

    lateinit var outboxRepo: InMemoryOutboxRepository

    beforeEach {
        outboxRepo = InMemoryOutboxRepository()
    }

    // ==================== 1. Visibility Timeout ====================

    "Visibility Timeout - claim 후 30초 내 완료 안 되면 자동 release" {
        // Given: PENDING 엔트리 생성
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-1",
            eventType = "TestEvent",
            payload = """{"test": "visibility"}"""
        )
        outboxRepo.insert(entry)

        // When: claim
        val claimResult = outboxRepo.claim(1, null, "worker-1")
        (claimResult as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1

        // Then: PENDING이 없어짐
        val pendingAfterClaim = outboxRepo.findPending(10)
        (pendingAfterClaim as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        // When: visibility timeout 경과 시뮬레이션 (30초)
        val released = outboxRepo.releaseExpiredClaims(visibilityTimeoutSeconds = 30)
        
        // Then: 아직 30초 안 지났으므로 release 안됨
        (released as OutboxRepositoryPort.Result.Ok).value shouldBe 0
    }

    "Visibility Timeout - 30초 지나면 자동 release되어 재처리 가능" {
        // Given: 30초 전에 claim된 엔트리 (시뮬레이션)
        val oldClaimedEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_old_claim",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-old",
            eventType = "TestEvent",
            payload = """{"test": "old claim"}""",
            status = OutboxStatus.PROCESSING,
            createdAt = Instant.now().minusSeconds(120),
            claimedAt = Instant.now().minusSeconds(60), // 60초 전 claim
            claimedBy = "dead-worker"
        )
        outboxRepo.insert(oldClaimedEntry)

        // When: visibility timeout 30초로 release
        val released = outboxRepo.releaseExpiredClaims(visibilityTimeoutSeconds = 30)

        // Then: 1개 release됨
        (released as OutboxRepositoryPort.Result.Ok).value shouldBe 1

        // And: PENDING 상태로 다시 조회 가능
        val pending = outboxRepo.findPending(10)
        (pending as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1
        pending.value[0].status shouldBe OutboxStatus.PENDING
        pending.value[0].claimedAt shouldBe null
    }

    "Visibility Timeout - 이미 PROCESSED된 것은 release 안됨" {
        // Given: PROCESSED 상태의 엔트리
        val processedEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_processed",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-processed",
            eventType = "TestEvent",
            payload = """{"test": "processed"}""",
            status = OutboxStatus.PROCESSED,
            createdAt = Instant.now().minusSeconds(120),
            claimedAt = Instant.now().minusSeconds(60),
            processedAt = Instant.now().minusSeconds(30)
        )
        outboxRepo.insert(processedEntry)

        // When: release 시도
        val released = outboxRepo.releaseExpiredClaims(visibilityTimeoutSeconds = 30)

        // Then: 0개 (PROCESSED는 release 대상 아님)
        (released as OutboxRepositoryPort.Result.Ok).value shouldBe 0
    }

    // ==================== 2. Dead Letter Queue (DLQ) ====================

    "DLQ - 재시도 5회 초과 시 DLQ로 이동" {
        // Given: 재시도 5회 초과된 FAILED 엔트리
        val failedEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_max_retry",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-fail",
            eventType = "TestEvent",
            payload = """{"test": "max retry"}""",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now().minusSeconds(300),
            retryCount = 6,  // 5회 초과
            failureReason = "Repeated failure"
        )
        outboxRepo.insert(failedEntry)

        // When: DLQ 이동
        val movedToDlq = outboxRepo.moveToDlq(maxRetryCount = 5)

        // Then: 1개 이동됨
        (movedToDlq as OutboxRepositoryPort.Result.Ok).value shouldBe 1

        // And: 원본 테이블에서 제거됨
        val pending = outboxRepo.findPending(10)
        (pending as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()

        // And: DLQ에서 조회 가능
        val dlqEntries = outboxRepo.findDlq(10)
        (dlqEntries as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1
        dlqEntries.value[0].failureReason shouldBe "Repeated failure"
    }

    "DLQ - 재시도 5회 이하는 DLQ 이동 안됨" {
        // Given: 재시도 3회 FAILED 엔트리
        val failedEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_retry_3",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-retry",
            eventType = "TestEvent",
            payload = """{"test": "retry 3"}""",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now(),
            retryCount = 3,
            failureReason = "Temporary failure"
        )
        outboxRepo.insert(failedEntry)

        // When: DLQ 이동 시도
        val movedToDlq = outboxRepo.moveToDlq(maxRetryCount = 5)

        // Then: 0개 (아직 재시도 가능)
        (movedToDlq as OutboxRepositoryPort.Result.Ok).value shouldBe 0
    }

    "DLQ - DLQ에서 원본으로 재시도 (replay)" {
        // Given: DLQ에 있는 엔트리
        val dlqEntry = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_dlq_replay",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-dlq",
            eventType = "TestEvent",
            payload = """{"test": "dlq replay"}""",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now().minusSeconds(600),
            retryCount = 6,
            failureReason = "Fixed now"
        )
        outboxRepo.insert(dlqEntry)
        outboxRepo.moveToDlq(maxRetryCount = 5)

        // When: DLQ에서 replay
        val replayed = outboxRepo.replayFromDlq(dlqEntry.id)

        // Then: 성공
        (replayed as OutboxRepositoryPort.Result.Ok).value shouldBe true

        // And: 원본 테이블에 PENDING으로 복귀 (retryCount 리셋)
        val pending = outboxRepo.findPending(10)
        (pending as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 1
        pending.value[0].status shouldBe OutboxStatus.PENDING
        pending.value[0].retryCount shouldBe 0
    }

    // ==================== 3. Priority Queue ====================

    "Priority - 높은 우선순위가 먼저 처리됨" {
        // Given: 다양한 우선순위의 엔트리
        val lowPriority = createEntryWithPriority("low", priority = 10)
        val highPriority = createEntryWithPriority("high", priority = 1)
        val mediumPriority = createEntryWithPriority("medium", priority = 5)

        outboxRepo.insert(lowPriority)
        outboxRepo.insert(highPriority)
        outboxRepo.insert(mediumPriority)

        // When: claim (우선순위 순)
        val claimed = outboxRepo.claimByPriority(limit = 3, workerId = "worker-1")

        // Then: 높은 우선순위(낮은 숫자)부터
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries shouldHaveSize 3
        entries[0].aggregateId shouldBe "tenant:high"
        entries[1].aggregateId shouldBe "tenant:medium"
        entries[2].aggregateId shouldBe "tenant:low"
    }

    "Priority - 동일 우선순위는 createdAt 순" {
        // Given: 동일 우선순위, 다른 생성 시간
        val older = createEntryWithPriority("older", priority = 5, createdAt = Instant.now().minusSeconds(60))
        val newer = createEntryWithPriority("newer", priority = 5, createdAt = Instant.now())

        outboxRepo.insert(newer)
        outboxRepo.insert(older)

        // When: claim
        val claimed = outboxRepo.claimByPriority(limit = 2, workerId = "worker-1")

        // Then: 먼저 생성된 것 우선
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries[0].aggregateId shouldBe "tenant:older"
        entries[1].aggregateId shouldBe "tenant:newer"
    }

    "Priority - 기본 우선순위는 100 (낮음)" {
        // Given: 우선순위 없이 생성된 엔트리
        val defaultEntry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:default",
            eventType = "TestEvent",
            payload = """{"test": "default priority"}"""
        )
        val highPriority = createEntryWithPriority("high", priority = 1)

        outboxRepo.insert(defaultEntry)
        outboxRepo.insert(highPriority)

        // When: claim
        val claimed = outboxRepo.claimByPriority(limit = 2, workerId = "worker-1")

        // Then: 명시적 높은 우선순위가 먼저
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries[0].aggregateId shouldBe "tenant:high"
        entries[1].aggregateId shouldBe "tenant:default"
    }

    // ==================== 4. Entity-Level Ordering ====================

    "Entity Ordering - 같은 entityKey는 version 순서 보장" {
        // Given: 같은 entity의 여러 버전
        val v3 = createEntryWithVersion("entity-A", version = 3)
        val v1 = createEntryWithVersion("entity-A", version = 1)
        val v2 = createEntryWithVersion("entity-A", version = 2)

        outboxRepo.insert(v3)
        outboxRepo.insert(v1)
        outboxRepo.insert(v2)

        // When: entity별 순서 보장 claim
        val claimed = outboxRepo.claimWithOrdering(limit = 1, workerId = "worker-1")

        // Then: v1만 claim됨 (v2, v3는 아직 불가)
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries shouldHaveSize 1
        entries[0].payload shouldBe """{"version":1}"""
    }

    "Entity Ordering - 다른 entityKey는 병렬 처리 가능" {
        // Given: 다른 entity들
        val entityA_v1 = createEntryWithVersion("entity-A", version = 1)
        val entityB_v1 = createEntryWithVersion("entity-B", version = 1)
        val entityA_v2 = createEntryWithVersion("entity-A", version = 2)

        outboxRepo.insert(entityA_v1)
        outboxRepo.insert(entityB_v1)
        outboxRepo.insert(entityA_v2)

        // When: claim
        val claimed = outboxRepo.claimWithOrdering(limit = 3, workerId = "worker-1")

        // Then: entity-A v1과 entity-B v1만 claim됨 (각 entity의 첫 번째만)
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries shouldHaveSize 2
        entries.map { it.aggregateId }.toSet() shouldBe setOf("tenant:entity-A", "tenant:entity-B")
    }

    "Entity Ordering - 이전 버전 완료 후 다음 버전 처리 가능" {
        // Given: 같은 entity의 v1, v2
        val v1 = createEntryWithVersion("entity-A", version = 1)
        val v2 = createEntryWithVersion("entity-A", version = 2)

        outboxRepo.insert(v1)
        outboxRepo.insert(v2)

        // When: v1 claim 및 처리 완료
        val claimedV1 = outboxRepo.claimWithOrdering(limit = 1, workerId = "worker-1")
        val v1Entry = (claimedV1 as OutboxRepositoryPort.Result.Ok).value[0]
        outboxRepo.markProcessed(listOf(v1Entry.id))

        // Then: v2 claim 가능
        val claimedV2 = outboxRepo.claimWithOrdering(limit = 1, workerId = "worker-1")
        val entries = (claimedV2 as OutboxRepositoryPort.Result.Ok).value
        entries shouldHaveSize 1
        entries[0].payload shouldBe """{"version":2}"""
    }

    "Entity Ordering - PROCESSING 상태면 같은 entity 다른 버전 claim 불가" {
        // Given: entity-A의 v1이 PROCESSING 중
        val v1 = OutboxEntry(
            id = java.util.UUID.randomUUID(),
            idempotencyKey = "idem_ordering_v1",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity-A",
            eventType = "TestEvent",
            payload = """{"version":1}""",
            status = OutboxStatus.PROCESSING,
            createdAt = Instant.now().minusSeconds(10),
            claimedAt = Instant.now().minusSeconds(5),
            claimedBy = "worker-1"
        )
        val v2 = createEntryWithVersion("entity-A", version = 2)

        outboxRepo.insert(v1)
        outboxRepo.insert(v2)

        // When: claim 시도
        val claimed = outboxRepo.claimWithOrdering(limit = 1, workerId = "worker-2")

        // Then: entity-A의 v1이 PROCESSING이므로 v2 claim 불가, 빈 결과
        val entries = (claimed as OutboxRepositoryPort.Result.Ok).value
        entries.shouldBeEmpty()
    }

    // ==================== Edge Cases ====================

    "Edge Case - 빈 테이블에서 claim" {
        val claimed = outboxRepo.claim(10, null, "worker-1")
        (claimed as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()
    }

    "Edge Case - DLQ 빈 상태에서 replay" {
        val replayed = outboxRepo.replayFromDlq(java.util.UUID.randomUUID())
        (replayed as OutboxRepositoryPort.Result.Ok).value shouldBe false
    }

    "Edge Case - 음수 limit" {
        val claimed = outboxRepo.claim(-1, null, "worker-1")
        (claimed as OutboxRepositoryPort.Result.Ok).value.shouldBeEmpty()
    }

    "Edge Case - 동시에 여러 worker가 같은 entry claim 시도" {
        // Given: 1개 엔트리
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:race-condition",
            eventType = "TestEvent",
            payload = """{"test": "race"}"""
        )
        outboxRepo.insert(entry)

        // When: 동시 claim (InMemory에서는 synchronized로 보장)
        val claimed1 = outboxRepo.claim(1, null, "worker-1")
        val claimed2 = outboxRepo.claim(1, null, "worker-2")

        // Then: 하나만 성공
        val total = (claimed1 as OutboxRepositoryPort.Result.Ok).value.size +
                    (claimed2 as OutboxRepositoryPort.Result.Ok).value.size
        total shouldBe 1
    }
})

// ==================== Helper Functions ====================

private fun createEntryWithPriority(
    name: String,
    priority: Int,
    createdAt: Instant = Instant.now()
): OutboxEntry {
    return OutboxEntry(
        id = java.util.UUID.randomUUID(),
        idempotencyKey = "idem_priority_$name",
        aggregateType = AggregateType.RAW_DATA,
        aggregateId = "tenant:$name",
        eventType = "TestEvent",
        payload = """{"priority":$priority}""",
        status = OutboxStatus.PENDING,
        createdAt = createdAt,
        priority = priority
    )
}

private fun createEntryWithVersion(entityKey: String, version: Long): OutboxEntry {
    return OutboxEntry(
        id = java.util.UUID.randomUUID(),
        idempotencyKey = "idem_${entityKey}_v$version",
        aggregateType = AggregateType.RAW_DATA,
        aggregateId = "tenant:$entityKey",
        eventType = "TestEvent",
        payload = """{"version":$version}""",
        status = OutboxStatus.PENDING,
        createdAt = Instant.now().minusSeconds(100 - version), // 버전 순으로 생성 시간
        entityVersion = version
    )
}
