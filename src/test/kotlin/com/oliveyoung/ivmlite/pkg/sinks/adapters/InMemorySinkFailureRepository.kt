package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory 실패 레코드 저장소 (테스트용)
 */
class InMemorySinkFailureRepository : SinkFailureRepositoryPort {

    private val store = ConcurrentHashMap<String, MutableList<SinkFailureRecord>>()

    override suspend fun save(record: SinkFailureRecord): Either<SinkError, Unit> {
        val key = "${record.sinkEventId}#${record.target}"
        store.getOrPut(key) { mutableListOf() }.add(record)
        return Unit.right()
    }

    override suspend fun findByTarget(target: String, limit: Int): Either<SinkError, List<SinkFailureRecord>> {
        val records = store.values.flatten()
            .filter { it.target == target }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return records.right()
    }

    override suspend fun updateStatus(
        sinkEventId: String,
        target: String,
        status: FailureStatus
    ): Either<SinkError, Unit> {
        val key = "$sinkEventId#$target"
        val records = store[key]
        if (records.isNullOrEmpty()) {
            return SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
                message = "Failure record not found: $key",
            ).left()
        }
        val latest = records.last()
        records[records.lastIndex] = latest.copy(status = status)
        return Unit.right()
    }

    fun allRecords(): List<SinkFailureRecord> = store.values.flatten()
    fun clear() = store.clear()
    fun size(): Int = store.values.sumOf { it.size }
}
