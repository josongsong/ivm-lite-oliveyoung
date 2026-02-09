package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxPage
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory Outbox Repository (테스트/로컬 개발용)
 *
 * Thread-safe 구현 (ConcurrentHashMap 사용)
 */
class InMemoryOutboxRepository : OutboxRepositoryPort, HealthCheckable {
    override val healthName: String = "outbox"
    override suspend fun healthCheck(): Boolean = true

    private val store = ConcurrentHashMap<UUID, OutboxEntry>()

    override suspend fun insert(entry: OutboxEntry): Result<OutboxEntry> {
        val prev = store.putIfAbsent(entry.id, entry)
        return if (prev == null) {
            Result.Ok(entry)
        } else {
            Result.Err(
                DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
            )
        }
    }

    // insertAll 원자성을 위한 lock 객체
    private val insertLock = Any()
    
    override suspend fun insertAll(entries: List<OutboxEntry>): Result<List<OutboxEntry>> {
        if (entries.isEmpty()) {
            return Result.Ok(emptyList())
        }

        // 원자성 보장: synchronized 블록으로 중복 체크 + 저장을 원자적으로
        synchronized(insertLock) {
            // 중복 체크 (id 또는 idempotencyKey)
            for (entry in entries) {
                if (store.containsKey(entry.id)) {
                    return Result.Err(
                        DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
                    )
                }
                // idempotencyKey 중복 체크
                val duplicateByKey = store.values.any { it.idempotencyKey == entry.idempotencyKey }
                if (duplicateByKey) {
                    return Result.Err(
                        DomainError.IdempotencyViolation("OutboxEntry with same idempotencyKey already exists: ${entry.idempotencyKey}"),
                    )
                }
            }

            // 모두 저장
            entries.forEach { store[it.id] = it }
        }
        return Result.Ok(entries)
    }

    override suspend fun findById(id: UUID): Result<OutboxEntry> {
        val entry = store[id]
            ?: return Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )
        return Result.Ok(entry)
    }

    override suspend fun findPending(limit: Int): Result<List<OutboxEntry>> {
        val pending = store.values
            .filter { it.status == OutboxStatus.PENDING }
            .sortedBy { it.createdAt }
            .take(limit)
        return Result.Ok(pending)
    }

    override suspend fun findPendingByType(
        type: AggregateType,
        limit: Int,
    ): Result<List<OutboxEntry>> {
        val pending = store.values
            .filter { it.status == OutboxStatus.PENDING && it.aggregateType == type }
            .sortedBy { it.createdAt }
            .take(limit)
        return Result.Ok(pending)
    }

    override suspend fun findFailed(limit: Int): Result<List<OutboxEntry>> {
        val failed = store.values
            .filter { it.status == OutboxStatus.FAILED }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return Result.Ok(failed)
    }

    override suspend fun resetAllFailed(limit: Int): Result<Int> {
        var count = 0
        synchronized(claimLock) {
            val failed = store.values
                .filter { it.status == OutboxStatus.FAILED }
                .take(limit)
            for (entry in failed) {
                val reset = entry.copy(
                    status = OutboxStatus.PENDING,
                    failureReason = null
                )
                store[entry.id] = reset
                count++
            }
        }
        return Result.Ok(count)
    }

    override suspend fun findPendingWithCursor(
        limit: Int,
        cursor: String?,
        type: AggregateType?
    ): Result<OutboxPage> {
        // 커서 파싱
        val (cursorTime, cursorId) = if (cursor != null) {
            val parts = cursor.split("_", limit = 2)
            if (parts.size == 2) {
                val time = Instant.parse(parts[0])
                val id = UUID.fromString(parts[1])
                time to id
            } else {
                null to null
            }
        } else {
            null to null
        }

        // 필터링 및 정렬
        var filtered = store.values
            .filter { it.status == OutboxStatus.PENDING }
            .let { entries ->
                if (type != null) entries.filter { it.aggregateType == type }
                else entries
            }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))

        // 커서 이후 필터링
        if (cursorTime != null && cursorId != null) {
            filtered = filtered.filter { entry ->
                entry.createdAt > cursorTime ||
                    (entry.createdAt == cursorTime && entry.id.toString() > cursorId.toString())
            }
        }

        // +1개 더 조회해서 hasMore 판단
        val allEntries = filtered.take(limit + 1)
        val entries = allEntries.take(limit)
        val hasMore = allEntries.size > limit

        // 다음 커서 생성
        val nextCursor = if (hasMore && entries.isNotEmpty()) {
            val lastEntry = entries.last()
            "${lastEntry.createdAt}_${lastEntry.id}"
        } else {
            null
        }

        return Result.Ok(OutboxPage(entries, nextCursor, hasMore))
    }

    // claim을 위한 락
    private val claimLock = Any()

    override suspend fun claim(
        limit: Int,
        type: AggregateType?,
        workerId: String?
    ): Result<List<OutboxEntry>> {
        if (limit <= 0) return Result.Ok(emptyList())
        
        val now = Instant.now()
        val claimed = mutableListOf<OutboxEntry>()
        
        synchronized(claimLock) {
            val pending = store.values
                .filter { it.status == OutboxStatus.PENDING }
                .let { entries ->
                    if (type != null) entries.filter { it.aggregateType == type }
                    else entries
                }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
                .take(limit)
            
            for (entry in pending) {
                val claimedEntry = entry.markClaimed(workerId, now)
                store[entry.id] = claimedEntry
                claimed.add(claimedEntry)
            }
        }
        
        return Result.Ok(claimed)
    }

    override suspend fun claimOne(
        type: AggregateType?,
        workerId: String?
    ): Result<OutboxEntry?> {
        return when (val result = claim(1, type, workerId)) {
            is Result.Ok -> 
                Result.Ok(result.value.firstOrNull())
            is Result.Err -> result
        }
    }

    override suspend fun recoverStaleProcessing(
        olderThanSeconds: Long
    ): Result<Int> {
        val cutoff = Instant.now().minusSeconds(olderThanSeconds)
        var recovered = 0
        
        synchronized(claimLock) {
            val stale = store.values
                .filter { 
                    it.status == OutboxStatus.PROCESSING &&
                    it.claimedAt != null &&
                    it.claimedAt.isBefore(cutoff) &&
                    it.retryCount < OutboxEntry.MAX_RETRY_COUNT
                }
            
            for (entry in stale) {
                val reset = entry.copy(
                    status = OutboxStatus.PENDING,
                    claimedAt = null,
                    claimedBy = null,
                    retryCount = entry.retryCount + 1
                )
                store[entry.id] = reset
                recovered++
            }
        }
        
        return Result.Ok(recovered)
    }

    override suspend fun markProcessed(ids: List<UUID>): Result<Int> {
        if (ids.isEmpty()) {
            return Result.Ok(0)
        }

        var count = 0
        val now = Instant.now()

        for (id in ids) {
            store.computeIfPresent(id) { _, entry ->
                count++
                entry.markProcessed(now)
            }
        }

        return Result.Ok(count)
    }

    override suspend fun markFailed(id: UUID, reason: String): Result<OutboxEntry> {
        var result: OutboxEntry? = null
        store.compute(id) { _, entry ->
            if (entry != null) {
                result = entry.markFailed(reason)
                result
            } else {
                null
            }
        }
        return result?.let { Result.Ok(it) }
            ?: Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )
    }

    override suspend fun resetToPending(id: UUID): Result<OutboxEntry> {
        val entry = store[id]
            ?: return Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )

        return try {
            val reset = entry.resetToPending()
            store[id] = reset
            Result.Ok(reset)
        } catch (e: IllegalStateException) {
            Result.Err(
                DomainError.InvariantViolation(e.message ?: "Max retry exceeded"),
            )
        }
    }

    // ==================== Tier 1: Visibility Timeout ====================

    override suspend fun releaseExpiredClaims(visibilityTimeoutSeconds: Long): Result<Int> {
        val cutoff = Instant.now().minusSeconds(visibilityTimeoutSeconds)
        var released = 0

        synchronized(claimLock) {
            val expired = store.values.filter {
                it.status == OutboxStatus.PROCESSING &&
                it.claimedAt != null &&
                it.claimedAt.isBefore(cutoff)
            }

            for (entry in expired) {
                val reset = entry.copy(
                    status = OutboxStatus.PENDING,
                    claimedAt = null,
                    claimedBy = null
                    // retryCount는 증가시키지 않음 (Visibility Timeout은 재시도가 아님)
                )
                store[entry.id] = reset
                released++
            }
        }

        return Result.Ok(released)
    }

    // ==================== Tier 1: Dead Letter Queue ====================

    private val dlqStore = mutableMapOf<UUID, OutboxEntry>()

    override suspend fun moveToDlq(maxRetryCount: Int): Result<Int> {
        var moved = 0

        synchronized(claimLock) {
            val toMove = store.values.filter {
                it.status == OutboxStatus.FAILED &&
                it.retryCount > maxRetryCount
            }

            for (entry in toMove) {
                dlqStore[entry.id] = entry
                store.remove(entry.id)
                moved++
            }
        }

        return Result.Ok(moved)
    }

    override suspend fun findDlq(limit: Int): Result<List<OutboxEntry>> {
        val entries = dlqStore.values
            .sortedBy { it.createdAt }
            .take(limit)
        return Result.Ok(entries)
    }

    override suspend fun replayFromDlq(id: UUID): Result<Boolean> {
        val entry = dlqStore[id] ?: return Result.Ok(false)

        synchronized(claimLock) {
            // DLQ에서 제거
            dlqStore.remove(id)

            // 원본 테이블에 PENDING으로 복귀 (retryCount 리셋)
            val reset = entry.copy(
                status = OutboxStatus.PENDING,
                claimedAt = null,
                claimedBy = null,
                processedAt = null,
                retryCount = 0,
                failureReason = null
            )
            store[id] = reset
        }

        return Result.Ok(true)
    }

    // ==================== Tier 1: Priority Queue ====================

    override suspend fun claimByPriority(limit: Int, workerId: String?): Result<List<OutboxEntry>> {
        if (limit <= 0) return Result.Ok(emptyList())

        val now = Instant.now()
        val claimed = mutableListOf<OutboxEntry>()

        synchronized(claimLock) {
            val pending = store.values
                .filter { it.status == OutboxStatus.PENDING }
                // 우선순위 순 (낮은 값 = 높은 우선순위), 같으면 createdAt 순
                .sortedWith(compareBy({ it.priority }, { it.createdAt }, { it.id }))
                .take(limit)

            for (entry in pending) {
                val updated = entry.copy(
                    status = OutboxStatus.PROCESSING,
                    claimedAt = now,
                    claimedBy = workerId
                )
                store[entry.id] = updated
                claimed.add(updated)
            }
        }

        return Result.Ok(claimed)
    }

    // ==================== Tier 1: Entity-Level Ordering ====================

    override suspend fun claimWithOrdering(limit: Int, workerId: String?): Result<List<OutboxEntry>> {
        if (limit <= 0) return Result.Ok(emptyList())

        val now = Instant.now()
        val claimed = mutableListOf<OutboxEntry>()

        synchronized(claimLock) {
            // 1. PROCESSING 중인 entity들 찾기 (같은 entity의 다른 버전 claim 불가)
            val processingEntities = store.values
                .filter { it.status == OutboxStatus.PROCESSING }
                .map { it.aggregateId }
                .toSet()

            // 2. entity별로 가장 낮은 entityVersion을 가진 PENDING 엔트리만 선택
            val candidatesByEntity = store.values
                .filter { it.status == OutboxStatus.PENDING }
                .filter { it.aggregateId !in processingEntities }  // PROCESSING 중인 entity 제외
                .groupBy { it.aggregateId }

            val candidates = candidatesByEntity.values
                .mapNotNull { entries ->
                    // 각 entity에서 가장 낮은 버전만 (없으면 createdAt 순)
                    entries.minWithOrNull(
                        compareBy(
                            { it.entityVersion ?: Long.MAX_VALUE },
                            { it.createdAt }
                        )
                    )
                }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
                .take(limit)

            // 3. claim
            for (entry in candidates) {
                val updated = entry.copy(
                    status = OutboxStatus.PROCESSING,
                    claimedAt = now,
                    claimedBy = workerId
                )
                store[entry.id] = updated
                claimed.add(updated)
            }
        }

        return Result.Ok(claimed)
    }

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
        dlqStore.clear()
    }

    fun size(): Int = store.size

    fun dlqSize(): Int = dlqStore.size
}
