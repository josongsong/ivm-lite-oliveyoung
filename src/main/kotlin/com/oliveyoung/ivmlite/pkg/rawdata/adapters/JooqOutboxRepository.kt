package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.generated.jooq.tables.Outbox
import com.oliveyoung.ivmlite.generated.jooq.tables.references.OUTBOX
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxPage
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
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
        // jOOQ 생성 코드 사용: 타입 세이프, 컴파일 타임 체크, IDE 자동완성
        private val O = OUTBOX  // 생성된 테이블 참조
        
        // 필드 참조 (OUTBOX.ID, OUTBOX.STATUS 등으로 직접 사용)
        // 기존 코드와의 호환성을 위해 alias 유지
        private val ID = OUTBOX.ID
        private val IDEMPOTENCY_KEY = OUTBOX.IDEMPOTENCY_KEY
        private val AGGREGATE_TYPE = OUTBOX.AGGREGATETYPE
        private val AGGREGATE_ID = OUTBOX.AGGREGATEID
        private val TYPE = OUTBOX.TYPE
        private val PAYLOAD = OUTBOX.PAYLOAD
        private val STATUS = OUTBOX.STATUS
        private val CREATED_AT = OUTBOX.CREATED_AT
        private val CLAIMED_AT = OUTBOX.CLAIMED_AT
        private val CLAIMED_BY = OUTBOX.CLAIMED_BY
        private val PROCESSED_AT = OUTBOX.PROCESSED_AT
        private val RETRY_COUNT = OUTBOX.RETRY_COUNT
        private val FAILURE_REASON = OUTBOX.FAILURE_REASON
    }

    override suspend fun insert(entry: OutboxEntry): Result<OutboxEntry> =
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
                    return@withContext Result.Err(
                        DomainError.IdempotencyViolation("OutboxEntry already exists: ${entry.id}"),
                    )
                }
                
                // idempotencyKey 중복 체크 (동일 비즈니스 이벤트 방지)
                val existingByKey = dsl.selectCount()
                    .from(OUTBOX)
                    .where(IDEMPOTENCY_KEY.eq(entry.idempotencyKey))
                    .fetchOne(0, Int::class.java) ?: 0

                if (existingByKey > 0) {
                    return@withContext Result.Err(
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
                    Result.Ok(entry)
                } catch (e: Exception) {
                    logger.error("Failed to insert outbox entry", e)
                    Result.Err(
                        DomainError.StorageError("Failed to insert outbox: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun insertAll(entries: List<OutboxEntry>): Result<List<OutboxEntry>> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) {
                return@withContext Result.Ok(emptyList())
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
                Result.Ok(entries)
            } catch (e: DomainError.IdempotencyViolation) {
                Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to insert outbox entries", e)
                Result.Err(
                    DomainError.StorageError("Failed to insert outbox entries: ${e.message}"),
                )
            }
        }

    override suspend fun findById(id: UUID): Result<OutboxEntry> =
        withContext(Dispatchers.IO) {
            try {
                val row = dsl.selectFrom(OUTBOX)
                    .where(ID.eq(id))
                    .fetchOne()

                if (row == null) {
                    return@withContext Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                Result.Ok(rowToEntry(row))
            } catch (e: Exception) {
                logger.error("Failed to find outbox entry", e)
                Result.Err(
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
    override suspend fun findPending(limit: Int): Result<List<OutboxEntry>> =
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

                    Result.Ok(rows.map { rowToEntry(it) })
                } catch (e: Exception) {
                    logger.error("Failed to find pending outbox entries", e)
                    Result.Err(
                        DomainError.StorageError("Failed to find pending: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun findPendingByType(
        type: AggregateType,
        limit: Int,
    ): Result<List<OutboxEntry>> = withContext(Dispatchers.IO) {
        try {
            val rows = dsl.selectFrom(OUTBOX)
                .where(STATUS.eq(OutboxStatus.PENDING.name))
                .and(AGGREGATE_TYPE.eq(type.name))
                .orderBy(CREATED_AT.asc())
                .limit(limit)
                .fetch()

            Result.Ok(rows.map { rowToEntry(it) })
        } catch (e: Exception) {
            logger.error("Failed to find pending outbox entries by type", e)
            Result.Err(
                DomainError.StorageError("Failed to find pending by type: ${e.message}"),
            )
        }
    }

    /**
     * 커서 기반 PENDING 상태 조회
     * 
     * 커서 형식: "{createdAt}_{id}" (정렬 일관성 보장)
     */
    override suspend fun findPendingWithCursor(
        limit: Int,
        cursor: String?,
        type: AggregateType?
    ): Result<OutboxPage> = withContext(Dispatchers.IO) {
        try {
            // 커서 파싱
            val (cursorTime, cursorId) = if (cursor != null) {
                val parts = cursor.split("_", limit = 2)
                if (parts.size == 2) {
                    val time = OffsetDateTime.parse(parts[0])
                    val id = UUID.fromString(parts[1])
                    time to id
                } else {
                    null to null
                }
            } else {
                null to null
            }

            // 쿼리 빌드
            var query = dsl.selectFrom(OUTBOX)
                .where(STATUS.eq(OutboxStatus.PENDING.name))

            // 타입 필터
            if (type != null) {
                query = query.and(AGGREGATE_TYPE.eq(type.name))
            }

            // 커서 조건 (createdAt, id 조합으로 정확한 위치 지정)
            if (cursorTime != null && cursorId != null) {
                query = query.and(
                    CREATED_AT.gt(cursorTime)
                        .or(CREATED_AT.eq(cursorTime).and(ID.gt(cursorId)))
                )
            }

            // +1개 더 조회해서 hasMore 판단
            val rows = query
                .orderBy(CREATED_AT.asc(), ID.asc())
                .limit(limit + 1)
                .fetch()

            val entries = rows.take(limit).map { rowToEntry(it) }
            val hasMore = rows.size > limit

            // 다음 커서 생성
            val nextCursor = if (hasMore && entries.isNotEmpty()) {
                val lastEntry = entries.last()
                "${lastEntry.createdAt}_${lastEntry.id}"
            } else {
                null
            }

            Result.Ok(OutboxPage(entries, nextCursor, hasMore))
        } catch (e: Exception) {
            logger.error("Failed to find pending outbox entries with cursor", e)
            Result.Err(
                DomainError.StorageError("Failed to find pending with cursor: ${e.message}"),
            )
        }
    }

    /**
     * PENDING 엔트리를 원자적으로 PROCESSING으로 전환 후 반환
     * 
     * 단일 트랜잭션 내에서:
     * 1. SELECT ... FOR UPDATE SKIP LOCKED
     * 2. UPDATE status = PROCESSING, claimed_at = now()
     * 3. RETURN entries
     * 
     * 이렇게 하면 여러 worker가 동시에 호출해도 중복 claim 없음
     */
    override suspend fun claim(
        limit: Int,
        type: AggregateType?,
        workerId: String?
    ): Result<List<OutboxEntry>> = 
        tracer.withSpanSuspend(
            "PostgreSQL.claim",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "claim",
                "limit" to limit.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                try {
                    val now = OffsetDateTime.now(ZoneOffset.UTC)
                    
                    // 원자적 claim: CTE (WITH ... UPDATE ... RETURNING)
                    // SELECT + UPDATE를 단일 쿼리로 실행
                    val claimedIds = dsl.transactionResult { cfg ->
                        val txDsl = DSL.using(cfg)
                        
                        // Step 1: SELECT FOR UPDATE SKIP LOCKED (id만)
                        var selectQuery = txDsl.select(ID)
                            .from(OUTBOX)
                            .where(STATUS.eq(OutboxStatus.PENDING.name))
                        
                        if (type != null) {
                            selectQuery = selectQuery.and(AGGREGATE_TYPE.eq(type.name))
                        }
                        
                        val ids = selectQuery
                            .orderBy(CREATED_AT.asc(), ID.asc())
                            .limit(limit)
                            .forUpdate()
                            .skipLocked()
                            .fetch(ID)
                        
                        if (ids.isEmpty()) {
                            return@transactionResult emptyList()
                        }
                        
                        // Step 2: UPDATE to PROCESSING
                        txDsl.update(OUTBOX)
                            .set(STATUS, OutboxStatus.PROCESSING.name)
                            .set(CLAIMED_AT, now)
                            .set(CLAIMED_BY, workerId)
                            .where(ID.`in`(ids))
                            .execute()
                        
                        ids
                    }
                    
                    if (claimedIds.isEmpty()) {
                        return@withContext Result.Ok(emptyList<OutboxEntry>())
                    }
                    
                    // Step 3: 업데이트된 row 조회
                    val rows = dsl.selectFrom(OUTBOX)
                        .where(ID.`in`(claimedIds))
                        .orderBy(CREATED_AT.asc(), ID.asc())
                        .fetch()
                    
                    Result.Ok(rows.map { rowToEntry(it) })
                } catch (e: Exception) {
                    logger.error("Failed to claim outbox entries", e)
                    Result.Err(
                        DomainError.StorageError("Failed to claim: ${e.message}"),
                    )
                }
            }
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

    /**
     * 오래된 PROCESSING 엔트리를 PENDING으로 복구 (타임아웃 처리)
     * 
     * Worker가 죽거나 처리가 오래 걸리는 경우 복구
     */
    override suspend fun recoverStaleProcessing(
        olderThanSeconds: Long
    ): Result<Int> = 
        tracer.withSpanSuspend(
            "PostgreSQL.recoverStaleProcessing",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "recover",
                "older_than_seconds" to olderThanSeconds.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                try {
                    val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(olderThanSeconds)
                    
                    val recovered = dsl.update(OUTBOX)
                        .set(STATUS, OutboxStatus.PENDING.name)
                        .set(CLAIMED_AT, null as OffsetDateTime?)
                        .set(CLAIMED_BY, null as String?)
                        .set(RETRY_COUNT, RETRY_COUNT.plus(1))
                        .where(STATUS.eq(OutboxStatus.PROCESSING.name))
                        .and(CLAIMED_AT.lt(cutoff))
                        .and(RETRY_COUNT.lt(OutboxEntry.MAX_RETRY_COUNT))
                        .execute()
                    
                    if (recovered > 0) {
                        logger.info("Recovered {} stale PROCESSING entries (older than {}s)", 
                            recovered, olderThanSeconds)
                    }
                    
                    Result.Ok(recovered)
                } catch (e: Exception) {
                    logger.error("Failed to recover stale processing entries", e)
                    Result.Err(
                        DomainError.StorageError("Failed to recover stale: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun markProcessed(ids: List<UUID>): Result<Int> =
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
                    return@withContext Result.Ok(0)
                }

            try {
                val updated = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PROCESSED.name)
                    .set(PROCESSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                    .where(ID.`in`(ids))
                    .execute()

                    logger.debug("Marked {} entries as processed", updated)
                    Result.Ok(updated)
                } catch (e: Exception) {
                    logger.error("Failed to mark processed", e)
                    Result.Err(
                        DomainError.StorageError("Failed to mark processed: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun markFailed(id: UUID, reason: String): Result<OutboxEntry> =
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
                    return@withContext Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                logger.debug("Marked entry {} as failed: {}", id, reason)
                Result.Ok(rowToEntry(updated))
            } catch (e: Exception) {
                logger.error("Failed to mark failed", e)
                Result.Err(
                    DomainError.StorageError("Failed to mark failed: ${e.message}"),
                )
            }
        }

    override suspend fun resetToPending(id: UUID): Result<OutboxEntry> =
        withContext(Dispatchers.IO) {
            try {
                // 먼저 현재 상태 확인
                val current = dsl.selectFrom(OUTBOX)
                    .where(ID.eq(id))
                    .fetchOne()

                if (current == null) {
                    return@withContext Result.Err(
                        DomainError.NotFoundError("OutboxEntry", id.toString()),
                    )
                }

                val retryCount = current.get(RETRY_COUNT) ?: 0
                if (retryCount >= OutboxEntry.MAX_RETRY_COUNT) {
                    return@withContext Result.Err(
                        DomainError.InvariantViolation("Max retry count exceeded"),
                    )
                }

                val updated = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PENDING.name)
                    .where(ID.eq(id))
                    .returning()
                    .fetchOne()
                    ?: return@withContext Result.Err(
                        DomainError.StorageError("Failed to fetch updated record for entry: $id")
                    )

                logger.debug("Reset entry {} to pending", id)
                Result.Ok(rowToEntry(updated))
            } catch (e: Exception) {
                logger.error("Failed to reset to pending", e)
                Result.Err(
                    DomainError.StorageError("Failed to reset to pending: ${e.message}"),
                )
            }
        }

    override suspend fun findFailed(limit: Int): Result<List<OutboxEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val rows = dsl.selectFrom(OUTBOX)
                    .where(STATUS.eq(OutboxStatus.FAILED.name))
                    .orderBy(CREATED_AT.asc())
                    .limit(limit)
                    .fetch()

                Result.Ok(rows.map { rowToEntry(it) })
            } catch (e: Exception) {
                logger.error("Failed to find failed outbox entries", e)
                Result.Err(
                    DomainError.StorageError("Failed to find failed entries: ${e.message}"),
                )
            }
        }

    override suspend fun resetAllFailed(limit: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val count = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PENDING.name)
                    .set(FAILURE_REASON, null as String?)
                    .where(STATUS.eq(OutboxStatus.FAILED.name))
                    .limit(limit)
                    .execute()

                if (count > 0) {
                    logger.info("Reset {} failed entries to pending", count)
                }
                Result.Ok(count)
            } catch (e: Exception) {
                logger.error("Failed to reset all failed entries", e)
                Result.Err(
                    DomainError.StorageError("Failed to reset all failed: ${e.message}"),
                )
            }
        }

    // ==================== Tier 1: Visibility Timeout ====================

    override suspend fun releaseExpiredClaims(visibilityTimeoutSeconds: Long): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(visibilityTimeoutSeconds)
                
                val released = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PENDING.name)
                    .set(CLAIMED_AT, null as OffsetDateTime?)
                    .set(CLAIMED_BY, null as String?)
                    .where(STATUS.eq(OutboxStatus.PROCESSING.name))
                    .and(CLAIMED_AT.lt(cutoff))
                    .execute()
                
                if (released > 0) {
                    logger.info("Released {} expired claims (visibility timeout {}s)", 
                        released, visibilityTimeoutSeconds)
                }
                
                Result.Ok(released)
            } catch (e: Exception) {
                logger.error("Failed to release expired claims", e)
                Result.Err(
                    DomainError.StorageError("Failed to release expired claims: ${e.message}"),
                )
            }
        }

    // ==================== Tier 1: Dead Letter Queue ====================
    // TODO: DLQ 테이블 마이그레이션 필요 (V021__outbox_dlq.sql)

    override suspend fun moveToDlq(maxRetryCount: Int): Result<Int> {
        // TODO: INSERT INTO outbox_dlq SELECT * FROM outbox WHERE status = 'FAILED' AND retry_count > maxRetryCount
        // TODO: DELETE FROM outbox WHERE ...
        logger.warn("moveToDlq not implemented for PostgreSQL yet")
        return Result.Ok(0)
    }

    override suspend fun findDlq(limit: Int): Result<List<OutboxEntry>> {
        // TODO: SELECT * FROM outbox_dlq ORDER BY created_at LIMIT limit
        logger.warn("findDlq not implemented for PostgreSQL yet")
        return Result.Ok(emptyList())
    }

    override suspend fun replayFromDlq(id: UUID): Result<Boolean> {
        // TODO: INSERT INTO outbox SELECT * FROM outbox_dlq WHERE id = id (with reset)
        // TODO: DELETE FROM outbox_dlq WHERE id = id
        logger.warn("replayFromDlq not implemented for PostgreSQL yet")
        return Result.Ok(false)
    }

    // ==================== Tier 1: Priority Queue ====================
    // TODO: outbox 테이블에 priority 컬럼 마이그레이션 필요 (V021__outbox_tier1.sql)

    override suspend fun claimByPriority(limit: Int, workerId: String?): Result<List<OutboxEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                
                // TODO: priority 컬럼 추가 후 ORDER BY priority ASC, created_at ASC
                // 현재는 created_at 순으로 폴백
                val rows = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PROCESSING.name)
                    .set(CLAIMED_AT, now)
                    .set(CLAIMED_BY, workerId)
                    .where(ID.`in`(
                        dsl.select(ID)
                            .from(OUTBOX)
                            .where(STATUS.eq(OutboxStatus.PENDING.name))
                            .orderBy(CREATED_AT.asc(), ID.asc())
                            .limit(limit)
                            .forUpdate()
                            .skipLocked()
                    ))
                    .returning()
                    .fetch()
                
                Result.Ok(rows.map { rowToEntry(it) })
            } catch (e: Exception) {
                logger.error("Failed to claim by priority", e)
                Result.Err(
                    DomainError.StorageError("Failed to claim by priority: ${e.message}"),
                )
            }
        }

    // ==================== Tier 1: Entity-Level Ordering ====================
    // TODO: outbox 테이블에 entity_version 컬럼 마이그레이션 필요 (V021__outbox_tier1.sql)

    override suspend fun claimWithOrdering(limit: Int, workerId: String?): Result<List<OutboxEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                
                // 1. PROCESSING 중인 entity들 조회
                val processingEntities = dsl.select(AGGREGATE_ID)
                    .from(OUTBOX)
                    .where(STATUS.eq(OutboxStatus.PROCESSING.name))
                    .fetch()
                    .into(String::class.java)
                    .filterNotNull()
                    .toSet()
                
                // 2. PENDING 엔트리 조회 (PROCESSING 중인 entity 제외)
                val allPending = dsl.selectFrom(OUTBOX)
                    .where(STATUS.eq(OutboxStatus.PENDING.name))
                    .let { q ->
                        if (processingEntities.isNotEmpty()) {
                            q.and(AGGREGATE_ID.notIn(processingEntities))
                        } else q
                    }
                    .orderBy(CREATED_AT.asc(), ID.asc())
                    .fetch()
                    .map { row -> rowToEntry(row) }
                
                // 3. entity별 가장 오래된 것만 선택
                val candidatesByEntity = allPending.groupBy { it.aggregateId }
                val candidates = candidatesByEntity.values
                    .mapNotNull { entries -> entries.minByOrNull { it.createdAt } }
                    .sortedBy { it.createdAt }
                    .take(limit)
                
                if (candidates.isEmpty()) {
                    return@withContext Result.Ok(emptyList<OutboxEntry>())
                }
                
                // 4. claim
                val candidateIds = candidates.map { it.id }
                val rows = dsl.update(OUTBOX)
                    .set(STATUS, OutboxStatus.PROCESSING.name)
                    .set(CLAIMED_AT, now)
                    .set(CLAIMED_BY, workerId)
                    .where(ID.`in`(candidateIds as Collection<UUID>))
                    .and(STATUS.eq(OutboxStatus.PENDING.name))
                    .returning()
                    .fetch()
                
                Result.Ok(rows.map { row -> rowToEntry(row) })
            } catch (e: Exception) {
                logger.error("Failed to claim with ordering", e)
                Result.Err(
                    DomainError.StorageError("Failed to claim with ordering: ${e.message}"),
                )
            }
        }

    private fun rowToEntry(row: org.jooq.Record): OutboxEntry {
        val processedAtValue = PROCESSED_AT.get(row)
        val claimedAtValue = CLAIMED_AT.get(row)
        val aggregateId = requireField(AGGREGATE_ID.get(row), "aggregate_id")
        val eventType = requireField(TYPE.get(row), "event_type")
        val payload = PAYLOAD.get(row)?.data() ?: "{}"

        // idempotencyKey: 마이그레이션 후에는 항상 존재, 없으면 즉시 생성
        val storedKey = IDEMPOTENCY_KEY.get(row)
        val idempotencyKey = storedKey?.takeIf { it.isNotBlank() }
            ?: OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload)

        return OutboxEntry(
            id = requireField(ID.get(row), "id"),
            idempotencyKey = idempotencyKey,
            aggregateType = AggregateType.valueOf(requireField(AGGREGATE_TYPE.get(row), "aggregate_type")),
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = OutboxStatus.valueOf(requireField(STATUS.get(row), "status")),
            createdAt = requireField(CREATED_AT.get(row), "created_at").toInstant(),
            claimedAt = claimedAtValue?.toInstant(),
            claimedBy = CLAIMED_BY.get(row),
            processedAt = processedAtValue?.toInstant(),
            retryCount = RETRY_COUNT.get(row) ?: 0,
            failureReason = FAILURE_REASON.get(row),
        )
    }

    private fun <T> requireField(value: T?, fieldName: String): T =
        value ?: throw IllegalStateException("Required field '$fieldName' is null in outbox record")
}
