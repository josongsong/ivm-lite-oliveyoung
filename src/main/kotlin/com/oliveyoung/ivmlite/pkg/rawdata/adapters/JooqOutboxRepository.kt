package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * jOOQ 기반 Outbox Repository (PostgreSQL)
 *
 * RFC-IMPL Phase B-3: jOOQ Adapters
 * v1 Polling 지원 (status, processed_at, retry_count)
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 */
class JooqOutboxRepository(
    private val dsl: DSLContext,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("jooq-outbox"),
) : OutboxRepositoryPort, HealthCheckable {

    override val healthName: String = "outbox"

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = LoggerFactory.getLogger(JooqOutboxRepository::class.java)

    companion object {
        private val OUTBOX = DSL.table("outbox")
        private val ID = DSL.field("id", UUID::class.java)
        private val IDEMPOTENCY_KEY = DSL.field("idempotency_key", String::class.java)
        private val AGGREGATE_TYPE = DSL.field("aggregatetype", String::class.java)
        private val AGGREGATE_ID = DSL.field("aggregateid", String::class.java)
        private val TYPE = DSL.field("type", String::class.java)
        private val PAYLOAD = DSL.field("payload", JSONB::class.java)
        private val STATUS = DSL.field("status", String::class.java)
        private val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
        private val PROCESSED_AT = DSL.field("processed_at", OffsetDateTime::class.java)
        private val RETRY_COUNT = DSL.field("retry_count", Int::class.java)
        private val FAILURE_REASON = DSL.field("failure_reason", String::class.java)
    }

    override suspend fun insert(entry: OutboxEntry): OutboxRepositoryPort.Result<OutboxEntry> =
        tracer.withSpanSuspend(
            "PostgreSQL.insertOutbox",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "insert",
                "aggregate_type" to entry.aggregateType.name,
                "event_type" to entry.eventType,
            ),
        ) {
            withContext(Dispatchers.IO) {
                try {
                // 중복 체크: ID 또는 idempotencyKey
                val existingById = dsl.selectCount()
                    .from(OUTBOX)
                    .where(ID.eq(entry.id))
                    .fetchOne(0, Int::class.java) ?: 0

                if (existingById > 0) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
                    )
                }
                
                // idempotencyKey 중복 체크 (동일 비즈니스 이벤트 방지)
                val existingByKey = dsl.selectCount()
                    .from(OUTBOX)
                    .where(IDEMPOTENCY_KEY.eq(entry.idempotencyKey))
                    .fetchOne(0, Int::class.java) ?: 0

                if (existingByKey > 0) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.IdempotencyViolation("OutboxEntry with same idempotencyKey already exists: ${entry.idempotencyKey}"),
                    )
                }

                dsl.insertInto(OUTBOX)
                    .set(ID, entry.id)
                    .set(IDEMPOTENCY_KEY, entry.idempotencyKey)
                    .set(AGGREGATE_TYPE, entry.aggregateType.name)
                    .set(AGGREGATE_ID, entry.aggregateId)
                    .set(TYPE, entry.eventType)
                    .set(PAYLOAD, JSONB.valueOf(entry.payload))
                    .set(STATUS, entry.status.name)
                    .set(CREATED_AT, entry.createdAt.atOffset(ZoneOffset.UTC))
                    .set(PROCESSED_AT, entry.processedAt?.atOffset(ZoneOffset.UTC))
                    .set(RETRY_COUNT, entry.retryCount)
                    .set(FAILURE_REASON, entry.failureReason)
                    .execute()

                    logger.debug("Inserted outbox entry: {}", entry.id)
                    OutboxRepositoryPort.Result.Ok(entry)
                } catch (e: Exception) {
                    logger.error("Failed to insert outbox entry", e)
                    OutboxRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to insert outbox: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun insertAll(entries: List<OutboxEntry>): OutboxRepositoryPort.Result<List<OutboxEntry>> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) {
                return@withContext OutboxRepositoryPort.Result.Ok(emptyList())
            }

            try {
                dsl.transaction { config ->
                    val ctx = DSL.using(config)

                    // 중복 체크
                    val ids = entries.map { it.id }
                    val existingCount = ctx.selectCount()
                        .from(OUTBOX)
                        .where(ID.`in`(ids))
                        .fetchOne(0, Int::class.java) ?: 0

                    if (existingCount > 0) {
                        throw DomainError.IdempotencyViolation("Some OutboxEntry already exists")
                    }

                    // 일괄 삽입
                    val batch = ctx.batch(
                        ctx.insertInto(
                            OUTBOX,
                            ID,
                            IDEMPOTENCY_KEY,
                            AGGREGATE_TYPE,
                            AGGREGATE_ID,
                            TYPE,
                            PAYLOAD,
                            STATUS,
                            CREATED_AT,
                            RETRY_COUNT,
                            FAILURE_REASON,
                        ).values(
                            null as UUID?,
                            null as String?,
                            null as String?,
                            null as String?,
                            null as String?,
                            null as JSONB?,
                            null as String?,
                            null as OffsetDateTime?,
                            null as Int?,
                            null as String?,
                        ),
                    )

                    entries.forEach { entry ->
                        batch.bind(
                            entry.id,
                            entry.idempotencyKey,
                            entry.aggregateType.name,
                            entry.aggregateId,
                            entry.eventType,
                            JSONB.valueOf(entry.payload),
                            entry.status.name,
                            entry.createdAt.atOffset(ZoneOffset.UTC),
                            entry.retryCount,
                            entry.failureReason,
                        )
                    }

                    batch.execute()
                }

                logger.debug("Inserted {} outbox entries", entries.size)
                OutboxRepositoryPort.Result.Ok(entries)
            } catch (e: DomainError.IdempotencyViolation) {
                OutboxRepositoryPort.Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to insert outbox entries", e)
                OutboxRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to insert outbox entries: ${e.message}"),
                )
            }
        }

    override suspend fun findById(id: UUID): OutboxRepositoryPort.Result<OutboxEntry> =
        withContext(Dispatchers.IO) {
            try {
                val row = dsl.selectFrom(OUTBOX)
                    .where(ID.eq(id))
                    .fetchOne()

                if (row == null) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                OutboxRepositoryPort.Result.Ok(rowToEntry(row))
            } catch (e: Exception) {
                logger.error("Failed to find outbox entry", e)
                OutboxRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to find outbox: ${e.message}"),
                )
            }
        }

    /**
     * PENDING 상태 조회 (SELECT FOR UPDATE SKIP LOCKED)
     * 
     * Race Condition 방지:
     * - FOR UPDATE: 조회한 row에 exclusive lock
     * - SKIP LOCKED: 이미 lock된 row는 건너뜀
     * 
     * 이를 통해 여러 worker가 동시에 같은 entry를 처리하는 것을 방지
     */
    override suspend fun findPending(limit: Int): OutboxRepositoryPort.Result<List<OutboxEntry>> =
        tracer.withSpanSuspend(
            "PostgreSQL.findPending",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "select",
                "limit" to limit.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                try {
                    // SELECT FOR UPDATE SKIP LOCKED: 
                    // - 다른 트랜잭션이 lock한 row는 건너뜀
                    // - 동시에 여러 worker가 다른 entry를 처리 가능
                    val rows = dsl.selectFrom(OUTBOX)
                        .where(STATUS.eq(OutboxStatus.PENDING.name))
                        .orderBy(CREATED_AT.asc())
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch()

                    OutboxRepositoryPort.Result.Ok(rows.map { rowToEntry(it) })
                } catch (e: Exception) {
                    logger.error("Failed to find pending outbox entries", e)
                    OutboxRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to find pending: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun findPendingByType(
        type: AggregateType,
        limit: Int,
    ): OutboxRepositoryPort.Result<List<OutboxEntry>> = withContext(Dispatchers.IO) {
        try {
            val rows = dsl.selectFrom(OUTBOX)
                .where(STATUS.eq(OutboxStatus.PENDING.name))
                .and(AGGREGATE_TYPE.eq(type.name))
                .orderBy(CREATED_AT.asc())
                .limit(limit)
                .fetch()

            OutboxRepositoryPort.Result.Ok(rows.map { rowToEntry(it) })
        } catch (e: Exception) {
            logger.error("Failed to find pending outbox entries by type", e)
            OutboxRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to find pending by type: ${e.message}"),
            )
        }
    }

    override suspend fun markProcessed(ids: List<UUID>): OutboxRepositoryPort.Result<Int> =
        tracer.withSpanSuspend(
            "PostgreSQL.markProcessed",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "update",
                "id.count" to ids.size.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                if (ids.isEmpty()) {
                    return@withContext OutboxRepositoryPort.Result.Ok(0)
                }

            try {
                val updated = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PROCESSED.name)
                    .set(PROCESSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                    .where(ID.`in`(ids))
                    .execute()

                    logger.debug("Marked {} entries as processed", updated)
                    OutboxRepositoryPort.Result.Ok(updated)
                } catch (e: Exception) {
                    logger.error("Failed to mark processed", e)
                    OutboxRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to mark processed: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun markFailed(id: UUID, reason: String): OutboxRepositoryPort.Result<OutboxEntry> =
        withContext(Dispatchers.IO) {
            try {
                val updated = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.FAILED.name)
                    .set(RETRY_COUNT, RETRY_COUNT.plus(1))
                    .set(FAILURE_REASON, reason)  // 실패 사유 저장
                    .where(ID.eq(id))
                    .returning()
                    .fetchOne()

                if (updated == null) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                logger.debug("Marked entry {} as failed: {}", id, reason)
                OutboxRepositoryPort.Result.Ok(rowToEntry(updated))
            } catch (e: Exception) {
                logger.error("Failed to mark failed", e)
                OutboxRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to mark failed: ${e.message}"),
                )
            }
        }

    override suspend fun resetToPending(id: UUID): OutboxRepositoryPort.Result<OutboxEntry> =
        withContext(Dispatchers.IO) {
            try {
                // 먼저 현재 상태 확인
                val current = dsl.selectFrom(OUTBOX)
                    .where(ID.eq(id))
                    .fetchOne()

                if (current == null) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                val retryCount = current.get(RETRY_COUNT) ?: 0
                if (retryCount >= OutboxEntry.MAX_RETRY_COUNT) {
                    return@withContext OutboxRepositoryPort.Result.Err(
                        DomainError.InvariantViolation("Max retry count exceeded"),
                    )
                }

                val updated = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PENDING.name)
                    .where(ID.eq(id))
                    .returning()
                    .fetchOne()!!

                logger.debug("Reset entry {} to pending", id)
                OutboxRepositoryPort.Result.Ok(rowToEntry(updated))
            } catch (e: Exception) {
                logger.error("Failed to reset to pending", e)
                OutboxRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to reset to pending: ${e.message}"),
                )
            }
        }

    private fun rowToEntry(row: org.jooq.Record): OutboxEntry {
        val processedAtValue = row.get(PROCESSED_AT)
        val aggregateId = row.get(AGGREGATE_ID)!!
        val eventType = row.get(TYPE)!!
        val payload = row.get(PAYLOAD)?.data() ?: "{}"
        
        // idempotencyKey: 마이그레이션 후에는 항상 존재, 없으면 즉시 생성
        val idempotencyKey = row.get(IDEMPOTENCY_KEY)?.takeIf { it.isNotBlank() }
            ?: OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload)
        
        return OutboxEntry(
            id = row.get(ID)!!,
            idempotencyKey = idempotencyKey,
            aggregateType = AggregateType.valueOf(row.get(AGGREGATE_TYPE)!!),
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = OutboxStatus.valueOf(row.get(STATUS)!!),
            createdAt = row.get(CREATED_AT)!!.toInstant(),
            processedAt = processedAtValue?.toInstant(),
            retryCount = row.get(RETRY_COUNT) ?: 0,
            failureReason = row.get(FAILURE_REASON),
        )
    }
}
