package com.oliveyoung.ivmlite.pkg.rawdata
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * OutboxRepositoryPort 계약 테스트 (InMemory 구현체로 검증)
 */
class OutboxRepositoryPortTest {

    private lateinit var repo: OutboxRepositoryPort

    @BeforeEach
    fun setup() {
        repo = InMemoryOutboxRepository()
    }

    // ==================== insert 테스트 ====================

    @Test
    fun `insert - 새 엔트리 저장 성공`() = runBlocking {
        val entry = createEntry()

        val result = repo.insert(entry)

        assertIs<Result.Ok<OutboxEntry>>(result)
        assertEquals(entry.id, result.value.id)
    }

    @Test
    fun `insert - 동일 ID 중복 저장 시 IdempotencyViolation`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        val result = repo.insert(entry)

        assertIs<Result.Err>(result)
        assertIs<DomainError.IdempotencyViolation>(result.error)
    }

    // ==================== insertAll 테스트 ====================

    @Test
    fun `insertAll - 여러 엔트리 일괄 저장`() = runBlocking {
        val entries = (1..5).map { createEntry() }

        val result = repo.insertAll(entries)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(5, result.value.size)
    }

    @Test
    fun `insertAll - 빈 리스트 저장 OK`() = runBlocking {
        val result = repo.insertAll(emptyList())

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `insertAll - 중복 ID 포함 시 전체 실패 (원자성)`() = runBlocking {
        val existing = createEntry()
        repo.insert(existing)

        val entries = listOf(createEntry(), existing, createEntry())
        val result = repo.insertAll(entries)

        assertIs<Result.Err>(result)
    }

    // ==================== findPending 테스트 ====================

    @Test
    fun `findPending - PENDING 상태만 조회`() = runBlocking {
        val pending1 = createEntry(status = OutboxStatus.PENDING)
        val pending2 = createEntry(status = OutboxStatus.PENDING)
        val processed = createEntry(status = OutboxStatus.PROCESSED)
        val failed = createEntry(status = OutboxStatus.FAILED)

        repo.insertAll(listOf(pending1, pending2, processed, failed))

        val result = repo.findPending(limit = 10)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.status == OutboxStatus.PENDING })
    }

    @Test
    fun `findPending - limit 적용`() = runBlocking {
        val entries = (1..10).map { createEntry() }
        repo.insertAll(entries)

        val result = repo.findPending(limit = 3)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(3, result.value.size)
    }

    @Test
    fun `findPending - createdAt 오름차순 정렬 (FIFO)`() = runBlocking {
        val now = Instant.now()
        val old = createEntry(createdAt = now.minusSeconds(100))
        val mid = createEntry(createdAt = now.minusSeconds(50))
        val recent = createEntry(createdAt = now)

        // 순서 섞어서 저장
        repo.insertAll(listOf(recent, old, mid))

        val result = repo.findPending(limit = 10)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(old.id, result.value[0].id)
        assertEquals(mid.id, result.value[1].id)
        assertEquals(recent.id, result.value[2].id)
    }

    @Test
    fun `findPending - 없으면 빈 리스트`() = runBlocking {
        val result = repo.findPending(limit = 10)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertTrue(result.value.isEmpty())
    }

    // ==================== markProcessed 테스트 ====================

    @Test
    fun `markProcessed - 상태를 PROCESSED로 변경`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        val result = repo.markProcessed(listOf(entry.id))

        assertIs<Result.Ok<Int>>(result)
        assertEquals(1, result.value)

        // 확인
        val pending = repo.findPending(limit = 10)
        assertIs<Result.Ok<List<OutboxEntry>>>(pending)
        assertTrue(pending.value.none { it.id == entry.id })
    }

    @Test
    fun `markProcessed - 여러 ID 일괄 처리`() = runBlocking {
        val entries = (1..5).map { createEntry() }
        repo.insertAll(entries)

        val ids = entries.take(3).map { it.id }
        val result = repo.markProcessed(ids)

        assertIs<Result.Ok<Int>>(result)
        assertEquals(3, result.value)

        val pending = repo.findPending(limit = 10)
        assertIs<Result.Ok<List<OutboxEntry>>>(pending)
        assertEquals(2, pending.value.size)
    }

    @Test
    fun `markProcessed - 존재하지 않는 ID는 무시 (멱등)`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        val result = repo.markProcessed(listOf(entry.id, UUID.randomUUID()))

        assertIs<Result.Ok<Int>>(result)
        assertEquals(1, result.value) // 실제 처리된 것만 카운트
    }

    @Test
    fun `markProcessed - 빈 리스트 OK`() = runBlocking {
        val result = repo.markProcessed(emptyList())

        assertIs<Result.Ok<Int>>(result)
        assertEquals(0, result.value)
    }

    // ==================== markFailed 테스트 ====================

    @Test
    fun `markFailed - 상태를 FAILED로 변경하고 retryCount 증가`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        val result = repo.markFailed(entry.id, "Connection timeout")

        assertIs<Result.Ok<OutboxEntry>>(result)
        assertEquals(OutboxStatus.FAILED, result.value.status)
        assertEquals(1, result.value.retryCount)
    }

    @Test
    fun `markFailed - 존재하지 않는 ID는 NotFoundError`() = runBlocking {
        val result = repo.markFailed(UUID.randomUUID(), "error")

        assertIs<Result.Err>(result)
        assertIs<DomainError.NotFoundError>(result.error)
    }

    // ==================== resetToPending 테스트 ====================

    @Test
    fun `resetToPending - FAILED 상태를 PENDING으로 복구`() = runBlocking {
        val entry = createEntry(status = OutboxStatus.FAILED, retryCount = 2)
        repo.insert(entry)

        val result = repo.resetToPending(entry.id)

        assertIs<Result.Ok<OutboxEntry>>(result)
        assertEquals(OutboxStatus.PENDING, result.value.status)
        assertEquals(2, result.value.retryCount) // retryCount 유지
    }

    @Test
    fun `resetToPending - MAX_RETRY 초과시 에러`() = runBlocking {
        val entry = createEntry(
            status = OutboxStatus.FAILED,
            retryCount = OutboxEntry.MAX_RETRY_COUNT,
        )
        repo.insert(entry)

        val result = repo.resetToPending(entry.id)

        assertIs<Result.Err>(result)
    }

    // ==================== findById 테스트 ====================

    @Test
    fun `findById - 존재하는 엔트리 조회`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        val result = repo.findById(entry.id)

        assertIs<Result.Ok<OutboxEntry>>(result)
        assertEquals(entry.id, result.value.id)
    }

    @Test
    fun `findById - 없으면 NotFoundError`() = runBlocking {
        val result = repo.findById(UUID.randomUUID())

        assertIs<Result.Err>(result)
        assertIs<DomainError.NotFoundError>(result.error)
    }

    // ==================== claim 테스트 ====================

    @Test
    fun `claim - PENDING 엔트리를 PROCESSING으로 전환하고 반환`() = runBlocking {
        val entries = (1..5).map { createEntry() }
        repo.insertAll(entries)

        val result = repo.claim(limit = 3, type = null, workerId = "test-worker")

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(3, result.value.size)
        assertTrue(result.value.all { it.status == OutboxStatus.PROCESSING })
        assertTrue(result.value.all { it.claimedBy == "test-worker" })
        assertTrue(result.value.all { it.claimedAt != null })

        // PENDING 상태는 2개만 남음
        val pending = repo.findPending(limit = 10)
        assertIs<Result.Ok<List<OutboxEntry>>>(pending)
        assertEquals(2, pending.value.size)
    }

    @Test
    fun `claim - createdAt 오름차순으로 claim (FIFO)`() = runBlocking {
        val now = Instant.now()
        val old = createEntry(createdAt = now.minusSeconds(100))
        val mid = createEntry(createdAt = now.minusSeconds(50))
        val recent = createEntry(createdAt = now)

        // 순서 섞어서 저장
        repo.insertAll(listOf(recent, old, mid))

        val result = repo.claim(limit = 2, type = null, workerId = null)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(old.id, result.value[0].id)
        assertEquals(mid.id, result.value[1].id)
    }

    @Test
    fun `claim - type 필터 적용`() = runBlocking {
        val rawData1 = createEntry(aggregateType = AggregateType.RAW_DATA)
        val rawData2 = createEntry(aggregateType = AggregateType.RAW_DATA)
        val slice = createEntry(aggregateType = AggregateType.SLICE)

        repo.insertAll(listOf(rawData1, rawData2, slice))

        val result = repo.claim(limit = 10, type = AggregateType.SLICE, workerId = null)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(1, result.value.size)
        assertEquals(slice.id, result.value[0].id)
    }

    @Test
    fun `claim - PENDING이 없으면 빈 리스트`() = runBlocking {
        // PROCESSED만 저장
        val processed = createEntry(status = OutboxStatus.PROCESSED)
        repo.insert(processed)

        val result = repo.claim(limit = 10, type = null, workerId = null)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `claim - PROCESSING 상태는 다시 claim되지 않음`() = runBlocking {
        val entry = createEntry()
        repo.insert(entry)

        // 첫 번째 claim
        val first = repo.claim(limit = 10, type = null, workerId = "worker-1")
        assertIs<Result.Ok<List<OutboxEntry>>>(first)
        assertEquals(1, first.value.size)

        // 두 번째 claim - 이미 PROCESSING이므로 빈 결과
        val second = repo.claim(limit = 10, type = null, workerId = "worker-2")
        assertIs<Result.Ok<List<OutboxEntry>>>(second)
        assertTrue(second.value.isEmpty())
    }

    // ==================== claimOne 테스트 ====================

    @Test
    fun `claimOne - 단일 엔트리 claim`() = runBlocking {
        val entries = (1..5).map { createEntry() }
        repo.insertAll(entries)

        val result = repo.claimOne(type = null, workerId = "single-worker")

        assertIs<Result.Ok<OutboxEntry?>>(result)
        val claimed = result.value
        assertNotNull(claimed)
        assertEquals(OutboxStatus.PROCESSING, claimed.status)
        assertEquals("single-worker", claimed.claimedBy)
    }

    @Test
    fun `claimOne - PENDING 없으면 null 반환`() = runBlocking {
        val result = repo.claimOne(type = null, workerId = null)

        assertIs<Result.Ok<OutboxEntry?>>(result)
        assertEquals(null, result.value)
    }

    // ==================== recoverStaleProcessing 테스트 ====================

    @Test
    fun `recoverStaleProcessing - 오래된 PROCESSING을 PENDING으로 복구`() = runBlocking {
        val now = Instant.now()
        
        // 10분 전에 claim된 엔트리 (stale)
        val stale = createEntry(
            status = OutboxStatus.PROCESSING,
            claimedAt = now.minusSeconds(600),
            claimedBy = "dead-worker",
        )
        // 1분 전에 claim된 엔트리 (not stale)
        val recent = createEntry(
            status = OutboxStatus.PROCESSING,
            claimedAt = now.minusSeconds(60),
            claimedBy = "active-worker",
        )
        repo.insertAll(listOf(stale, recent))

        // 5분(300초) 기준으로 stale 복구
        val result = repo.recoverStaleProcessing(olderThanSeconds = 300)

        assertIs<Result.Ok<Int>>(result)
        assertEquals(1, result.value)

        // stale은 PENDING으로 복구, claimedAt/claimedBy 초기화
        val staleAfter = repo.findById(stale.id)
        assertIs<Result.Ok<OutboxEntry>>(staleAfter)
        assertEquals(OutboxStatus.PENDING, staleAfter.value.status)
        assertEquals(null, staleAfter.value.claimedAt)
        assertEquals(null, staleAfter.value.claimedBy)
        assertEquals(1, staleAfter.value.retryCount) // retryCount 증가

        // recent는 그대로 PROCESSING
        val recentAfter = repo.findById(recent.id)
        assertIs<Result.Ok<OutboxEntry>>(recentAfter)
        assertEquals(OutboxStatus.PROCESSING, recentAfter.value.status)
    }

    @Test
    fun `recoverStaleProcessing - MAX_RETRY 초과시 복구 안함`() = runBlocking {
        val now = Instant.now()
        
        // MAX_RETRY에 도달한 stale 엔트리
        val maxRetried = createEntry(
            status = OutboxStatus.PROCESSING,
            claimedAt = now.minusSeconds(600),
            claimedBy = "dead-worker",
            retryCount = OutboxEntry.MAX_RETRY_COUNT,
        )
        repo.insert(maxRetried)

        val result = repo.recoverStaleProcessing(olderThanSeconds = 300)

        assertIs<Result.Ok<Int>>(result)
        assertEquals(0, result.value) // 복구 안됨

        // 여전히 PROCESSING
        val after = repo.findById(maxRetried.id)
        assertIs<Result.Ok<OutboxEntry>>(after)
        assertEquals(OutboxStatus.PROCESSING, after.value.status)
    }

    @Test
    fun `recoverStaleProcessing - PENDING이나 PROCESSED는 영향 없음`() = runBlocking {
        val pending = createEntry(status = OutboxStatus.PENDING)
        val processed = createEntry(status = OutboxStatus.PROCESSED)
        repo.insertAll(listOf(pending, processed))

        val result = repo.recoverStaleProcessing(olderThanSeconds = 0) // 즉시 타임아웃

        assertIs<Result.Ok<Int>>(result)
        assertEquals(0, result.value)
    }

    // ==================== AggregateType 필터 테스트 ====================

    @Test
    fun `findPendingByType - 특정 AggregateType만 조회`() = runBlocking {
        val rawData = createEntry(aggregateType = AggregateType.RAW_DATA)
        val slice = createEntry(aggregateType = AggregateType.SLICE)
        val changeset = createEntry(aggregateType = AggregateType.CHANGESET)

        repo.insertAll(listOf(rawData, slice, changeset))

        val result = repo.findPendingByType(AggregateType.RAW_DATA, limit = 10)

        assertIs<Result.Ok<List<OutboxEntry>>>(result)
        assertEquals(1, result.value.size)
        assertEquals(AggregateType.RAW_DATA, result.value[0].aggregateType)
    }

    // ==================== 헬퍼 ====================

    private fun createEntry(
        status: OutboxStatus = OutboxStatus.PENDING,
        retryCount: Int = 0,
        createdAt: Instant = Instant.now(),
        aggregateType: AggregateType = AggregateType.RAW_DATA,
        claimedAt: Instant? = null,
        claimedBy: String? = null,
    ): OutboxEntry {
        val aggregateId = "tenant-1:entity-${UUID.randomUUID().toString().take(8)}"
        val eventType = "TestEvent"
        val payload = """{"test": true, "random": "${UUID.randomUUID()}"}"""
        return OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = status,
            createdAt = createdAt,
            claimedAt = claimedAt,
            claimedBy = claimedBy,
            processedAt = if (status == OutboxStatus.PROCESSED) Instant.now() else null,
            retryCount = retryCount,
        )
    }
}
