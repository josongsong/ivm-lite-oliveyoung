package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory SinkEvent Repository (테스트/개발용)
 *
 * Production에서는 DynamoDbSinkEventRepository 사용.
 */
class InMemorySinkEventRepository : SinkEventRepositoryPort {

    private val logger = LoggerFactory.getLogger(InMemorySinkEventRepository::class.java)
    private val store = ConcurrentHashMap<UUID, SinkEvent>()
    private val idempotencyKeys = ConcurrentHashMap<String, UUID>()

    override suspend fun put(event: SinkEvent): Result<SinkEvent> {
        return try {
            // Idempotency check
            val existingId = idempotencyKeys[event.idempotencyKey]
            if (existingId != null) {
                logger.debug("SinkEvent already exists (idempotent): ${event.idempotencyKey}")
                return Result.Ok(store[existingId]!!)
            }

            store[event.id] = event
            idempotencyKeys[event.idempotencyKey] = event.id
            logger.debug("SinkEvent saved: ${event.id}")
            Result.Ok(event)
        } catch (e: Exception) {
            logger.error("Failed to put SinkEvent: ${event.id}", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError("Failed to save SinkEvent: ${e.message}"))
        }
    }

    override suspend fun putAll(events: List<SinkEvent>): Result<List<SinkEvent>> {
        return try {
            events.forEach { event ->
                when (val result = put(event)) {
                    is Result.Err -> return result
                    is Result.Ok -> Unit
                }
            }
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to put batch SinkEvents", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError("Failed to batch save: ${e.message}"))
        }
    }

    override suspend fun findById(id: UUID): Result<SinkEvent?> {
        return try {
            Result.Ok(store[id])
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvent: $id", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError("Failed to find SinkEvent: ${e.message}"))
        }
    }

    override suspend fun findByJobId(jobId: String): Result<List<SinkEvent>> {
        return try {
            val events = store.values.filter { it.jobId == jobId }.sortedBy { it.createdAt }
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvents by jobId: $jobId", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError("Failed to query by jobId: ${e.message}"))
        }
    }

    override suspend fun findByStatus(status: String, limit: Int): Result<List<SinkEvent>> {
        return try {
            val events = store.values
                .filter { it.status.name == status }
                .sortedBy { it.createdAt }
                .take(limit)
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvents by status: $status", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError("Failed to query by status: ${e.message}"))
        }
    }

    // Test helper
    fun clear() {
        store.clear()
        idempotencyKeys.clear()
    }

    fun size(): Int = store.size
}
