package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
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

    override suspend fun insert(entry: OutboxEntry): OutboxRepositoryPort.Result<OutboxEntry> {
        val prev = store.putIfAbsent(entry.id, entry)
        return if (prev == null) {
            OutboxRepositoryPort.Result.Ok(entry)
        } else {
            OutboxRepositoryPort.Result.Err(
                DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
            )
        }
    }

    override suspend fun insertAll(entries: List<OutboxEntry>): OutboxRepositoryPort.Result<List<OutboxEntry>> {
        if (entries.isEmpty()) {
            return OutboxRepositoryPort.Result.Ok(emptyList())
        }

        // 원자성: 먼저 중복 체크
        for (entry in entries) {
            if (store.containsKey(entry.id)) {
                return OutboxRepositoryPort.Result.Err(
                    DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
                )
            }
        }

        // 모두 저장
        entries.forEach { store[it.id] = it }
        return OutboxRepositoryPort.Result.Ok(entries)
    }

    override suspend fun findById(id: UUID): OutboxRepositoryPort.Result<OutboxEntry> {
        val entry = store[id]
            ?: return OutboxRepositoryPort.Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )
        return OutboxRepositoryPort.Result.Ok(entry)
    }

    override suspend fun findPending(limit: Int): OutboxRepositoryPort.Result<List<OutboxEntry>> {
        val pending = store.values
            .filter { it.status == OutboxStatus.PENDING }
            .sortedBy { it.createdAt }
            .take(limit)
        return OutboxRepositoryPort.Result.Ok(pending)
    }

    override suspend fun findPendingByType(
        type: AggregateType,
        limit: Int,
    ): OutboxRepositoryPort.Result<List<OutboxEntry>> {
        val pending = store.values
            .filter { it.status == OutboxStatus.PENDING && it.aggregateType == type }
            .sortedBy { it.createdAt }
            .take(limit)
        return OutboxRepositoryPort.Result.Ok(pending)
    }

    override suspend fun markProcessed(ids: List<UUID>): OutboxRepositoryPort.Result<Int> {
        if (ids.isEmpty()) {
            return OutboxRepositoryPort.Result.Ok(0)
        }

        var count = 0
        val now = Instant.now()

        for (id in ids) {
            store.computeIfPresent(id) { _, entry ->
                count++
                entry.markProcessed(now)
            }
        }

        return OutboxRepositoryPort.Result.Ok(count)
    }

    override suspend fun markFailed(id: UUID, reason: String): OutboxRepositoryPort.Result<OutboxEntry> {
        val entry = store[id]
            ?: return OutboxRepositoryPort.Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )

        val failed = entry.markFailed()
        store[id] = failed
        return OutboxRepositoryPort.Result.Ok(failed)
    }

    override suspend fun resetToPending(id: UUID): OutboxRepositoryPort.Result<OutboxEntry> {
        val entry = store[id]
            ?: return OutboxRepositoryPort.Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString()),
            )

        return try {
            val reset = entry.resetToPending()
            store[id] = reset
            OutboxRepositoryPort.Result.Ok(reset)
        } catch (e: IllegalStateException) {
            OutboxRepositoryPort.Result.Err(
                DomainError.InvariantViolation(e.message ?: "Max retry exceeded"),
            )
        }
    }

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
}
