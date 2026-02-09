package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.Result
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Admin Dashboard Service
 *
 * 대시보드 및 Outbox 통계 서비스.
 * AdminRoutes에서 분리된 비즈니스 로직 담당.
 */
class AdminDashboardService(
    private val outboxRepo: OutboxRepositoryPort,
    private val worker: OutboxPollingWorker,
    private val dsl: DSLContext
) {
    private val logger = LoggerFactory.getLogger(AdminDashboardService::class.java)

    // ==================== Public API ====================

    /**
     * 전체 대시보드 데이터 조회
     */
    suspend fun getDashboard(): Result<DashboardData> {
        return try {
            val outboxStats = getOutboxStatsInternal()
            val workerStatus = getWorkerStatusInternal()
            val dbStats = getDatabaseStatsInternal()

            Result.Ok(
                DashboardData(
                    outbox = outboxStats,
                    worker = workerStatus,
                    database = dbStats,
                    timestamp = Instant.now()
                )
            )
        } catch (e: Exception) {
            logger.error("[Dashboard] Failed to get dashboard data", e)
            Result.Err(DomainError.StorageError("Failed to get dashboard data: ${e.message}"))
        }
    }

    /**
     * Outbox 통계 조회
     */
    suspend fun getOutboxStats(): Result<OutboxStats> {
        return try {
            Result.Ok(getOutboxStatsInternal())
        } catch (e: Exception) {
            logger.error("[OutboxStats] Failed to get outbox stats", e)
            Result.Err(DomainError.StorageError("Failed to get outbox stats: ${e.message}"))
        }
    }

    /**
     * Worker 상태 조회
     */
    suspend fun getWorkerStatus(): Result<WorkerStatus> {
        return try {
            Result.Ok(getWorkerStatusInternal())
        } catch (e: Exception) {
            logger.error("[WorkerStatus] Failed to get worker status", e)
            Result.Err(DomainError.StorageError("Failed to get worker status: ${e.message}"))
        }
    }

    /**
     * 데이터베이스 통계 조회
     */
    suspend fun getDatabaseStats(): Result<DatabaseStats> {
        return try {
            Result.Ok(getDatabaseStatsInternal())
        } catch (e: Exception) {
            logger.error("[DatabaseStats] Failed to get database stats", e)
            Result.Err(DomainError.StorageError("Failed to get database stats: ${e.message}"))
        }
    }

    /**
     * 최근 Outbox 엔트리 조회
     */
    suspend fun getRecentOutbox(limit: Int): Result<List<RecentOutboxItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            val entries = dsl.select()
                .from(DSL.table("outbox"))
                .orderBy(DSL.field("created_at").desc())
                .limit(safeLimit)
                .fetch()
                .map { record ->
                    RecentOutboxItem(
                        id = record.get("id", UUID::class.java)?.toString() ?: "",
                        aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                        aggregateId = record.get("aggregateid", String::class.java) ?: "",
                        eventType = record.get("type", String::class.java) ?: "",
                        status = record.get("status", String::class.java) ?: "",
                        createdAt = record.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                        processedAt = record.get("processed_at", OffsetDateTime::class.java)?.toInstant(),
                        retryCount = record.get("retry_count", Int::class.java) ?: 0
                    )
                }
            Result.Ok(entries)
        } catch (e: Exception) {
            logger.error("[RecentOutbox] Failed to get recent outbox entries", e)
            Result.Err(DomainError.StorageError("Failed to get recent outbox entries: ${e.message}"))
        }
    }

    /**
     * 실패한 Outbox 엔트리 조회
     */
    suspend fun getFailedOutbox(limit: Int): Result<List<FailedOutboxItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            val entries = dsl.select()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq(OutboxStatus.FAILED.name))
                .orderBy(DSL.field("created_at").desc())
                .limit(safeLimit)
                .fetch()
                .map { record ->
                    FailedOutboxItem(
                        id = record.get("id", UUID::class.java)?.toString() ?: "",
                        aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                        aggregateId = record.get("aggregateid", String::class.java) ?: "",
                        eventType = record.get("type", String::class.java) ?: "",
                        createdAt = record.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                        retryCount = record.get("retry_count", Int::class.java) ?: 0,
                        failureReason = record.get("failure_reason", String::class.java)
                    )
                }
            Result.Ok(entries)
        } catch (e: Exception) {
            logger.error("[FailedOutbox] Failed to get failed outbox entries", e)
            Result.Err(DomainError.StorageError("Failed to get failed outbox entries: ${e.message}"))
        }
    }

    /**
     * 시간대별 통계 조회
     */
    suspend fun getHourlyStats(hours: Int): Result<HourlyStatsData> {
        val safeHours = hours.coerceIn(1, 168) // max 1 week
        return try {
            val stats = dsl.select(
                DSL.field("date_trunc('hour', created_at)").`as`("hour"),
                DSL.field("status"),
                DSL.count().`as`("count")
            )
                .from(DSL.table("outbox"))
                .where(
                    DSL.field("created_at").greaterThan(
                        DSL.field("NOW() - INTERVAL '$safeHours hours'")
                    )
                )
                .groupBy(
                    DSL.field("date_trunc('hour', created_at)"),
                    DSL.field("status")
                )
                .orderBy(DSL.field("hour").asc())
                .fetch()

            val hourlyData = mutableMapOf<Instant, HourlyStatItem>()

            stats.forEach { record ->
                val hourOffset = record.get("hour", OffsetDateTime::class.java)
                val hour = hourOffset?.toInstant() ?: return@forEach
                val status = record.get("status", String::class.java) ?: return@forEach
                val count = record.get("count", Long::class.java) ?: 0L

                val item = hourlyData.getOrPut(hour) {
                    HourlyStatItem(
                        hour = hour,
                        pending = 0L,
                        processing = 0L,
                        processed = 0L,
                        failed = 0L,
                        total = 0L
                    )
                }

                hourlyData[hour] = when (OutboxStatus.fromDbValueOrNull(status)) {
                    OutboxStatus.PENDING -> item.copy(pending = count, total = item.total + count)
                    OutboxStatus.PROCESSING -> item.copy(processing = count, total = item.total + count)
                    OutboxStatus.PROCESSED -> item.copy(processed = count, total = item.total + count)
                    OutboxStatus.FAILED -> item.copy(failed = count, total = item.total + count)
                    null -> item.copy(total = item.total + count)
                }
            }

            Result.Ok(
                HourlyStatsData(
                    items = hourlyData.values.toList().sortedBy { it.hour },
                    hours = safeHours
                )
            )
        } catch (e: Exception) {
            logger.error("[HourlyStats] Failed to get hourly stats", e)
            Result.Err(DomainError.StorageError("Failed to get hourly stats: ${e.message}"))
        }
    }

    /**
     * Stale PROCESSING 엔트리 조회
     */
    suspend fun getStaleEntries(timeoutSeconds: Long): Result<List<StaleOutboxItem>> {
        val safeTimeout = timeoutSeconds.coerceIn(60, 86400) // 1min ~ 1day
        return try {
            val staleEntries = dsl.select()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq(OutboxStatus.PROCESSING.name))
                .and(DSL.field("claimed_at").isNotNull)
                .and(
                    DSL.field("claimed_at").lessThan(
                        DSL.field("NOW() - INTERVAL '$safeTimeout seconds'")
                    )
                )
                .orderBy(DSL.field("claimed_at").asc())
                .limit(100)
                .fetch()

            val items = staleEntries.map { record ->
                val claimedAt = record.get("claimed_at", OffsetDateTime::class.java)
                StaleOutboxItem(
                    id = record.get("id", UUID::class.java)?.toString() ?: "",
                    aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                    aggregateId = record.get("aggregateid", String::class.java) ?: "",
                    eventType = record.get("type", String::class.java) ?: "",
                    claimedAt = claimedAt?.toInstant(),
                    claimedBy = record.get("claimed_by", String::class.java),
                    ageSeconds = claimedAt?.let {
                        java.time.Duration.between(it.toInstant(), Instant.now()).seconds
                    } ?: 0L
                )
            }

            Result.Ok(items)
        } catch (e: Exception) {
            logger.error("[StaleEntries] Failed to get stale entries", e)
            Result.Err(DomainError.StorageError("Failed to get stale entries: ${e.message}"))
        }
    }

    /**
     * 특정 Outbox 엔트리 조회
     */
    suspend fun getOutboxEntry(id: UUID): Result<OutboxEntryDetail> {
        return when (val result = outboxRepo.findById(id)) {
            is Result.Ok -> {
                val entry = result.value
                Result.Ok(
                    OutboxEntryDetail(
                        id = entry.id.toString(),
                        idempotencyKey = entry.idempotencyKey,
                        aggregateType = entry.aggregateType.name,
                        aggregateId = entry.aggregateId,
                        eventType = entry.eventType,
                        payload = entry.payload,
                        status = entry.status.name,
                        createdAt = entry.createdAt,
                        processedAt = entry.processedAt,
                        claimedAt = entry.claimedAt,
                        claimedBy = entry.claimedBy,
                        retryCount = entry.retryCount,
                        failureReason = entry.failureReason,
                        priority = entry.priority,
                        entityVersion = entry.entityVersion
                    )
                )
            }
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * DLQ 조회
     */
    suspend fun getDlq(limit: Int): Result<List<OutboxEntryDetail>> {
        val safeLimit = limit.coerceIn(1, 200)
        return when (val result = outboxRepo.findDlq(safeLimit)) {
            is Result.Ok -> {
                Result.Ok(result.value.map { entry ->
                    OutboxEntryDetail(
                        id = entry.id.toString(),
                        idempotencyKey = entry.idempotencyKey,
                        aggregateType = entry.aggregateType.name,
                        aggregateId = entry.aggregateId,
                        eventType = entry.eventType,
                        payload = entry.payload,
                        status = entry.status.name,
                        createdAt = entry.createdAt,
                        processedAt = entry.processedAt,
                        claimedAt = entry.claimedAt,
                        claimedBy = entry.claimedBy,
                        retryCount = entry.retryCount,
                        failureReason = entry.failureReason,
                        priority = entry.priority,
                        entityVersion = entry.entityVersion
                    )
                })
            }
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * DLQ 재처리
     */
    suspend fun replayDlq(id: UUID): Result<Boolean> {
        return when (val result = outboxRepo.replayFromDlq(id)) {
            is Result.Ok -> Result.Ok(result.value)
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * Stale 엔트리 release
     */
    suspend fun releaseStale(timeoutSeconds: Long): Result<Int> {
        val safeTimeout = timeoutSeconds.coerceIn(60, 86400)
        return when (val result = outboxRepo.releaseExpiredClaims(safeTimeout)) {
            is Result.Ok -> Result.Ok(result.value)
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * 실패한 엔트리 재시도
     */
    suspend fun retryEntry(id: UUID): Result<OutboxEntryDetail> {
        return when (val result = outboxRepo.resetToPending(id)) {
            is Result.Ok -> {
                val entry = result.value
                Result.Ok(
                    OutboxEntryDetail(
                        id = entry.id.toString(),
                        idempotencyKey = entry.idempotencyKey,
                        aggregateType = entry.aggregateType.name,
                        aggregateId = entry.aggregateId,
                        eventType = entry.eventType,
                        payload = entry.payload,
                        status = entry.status.name,
                        createdAt = entry.createdAt,
                        processedAt = entry.processedAt,
                        claimedAt = entry.claimedAt,
                        claimedBy = entry.claimedBy,
                        retryCount = entry.retryCount,
                        failureReason = entry.failureReason,
                        priority = entry.priority,
                        entityVersion = entry.entityVersion
                    )
                )
            }
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * 모든 실패 엔트리 재시도
     */
    suspend fun retryAllFailed(limit: Int): Result<Int> {
        val safeLimit = limit.coerceIn(1, 1000)
        return when (val result = outboxRepo.resetAllFailed(safeLimit)) {
            is Result.Ok -> Result.Ok(result.value)
            is Result.Err -> Result.Err(result.error)
        }
    }

    // ==================== Private Helpers ====================

    private fun getOutboxStatsInternal(): OutboxStats {
        // outbox 테이블에서 직접 집계 (outbox_stats 뷰는 이미 집계된 데이터라 GROUP BY 불가)
        val stats = dsl.select(
            DSL.field("status", String::class.java),
            DSL.field("aggregatetype", String::class.java),
            DSL.count().`as`("count"),
            DSL.min(DSL.field("created_at", OffsetDateTime::class.java)).`as`("oldest"),
            DSL.max(DSL.field("created_at", OffsetDateTime::class.java)).`as`("newest"),
            DSL.field("AVG(EXTRACT(EPOCH FROM (COALESCE(processed_at, NOW()) - created_at)))", Double::class.java).`as`("avg_latency_sec")
        )
            .from(DSL.table("outbox"))
            .groupBy(DSL.field("status"), DSL.field("aggregatetype"))
            .orderBy(DSL.field("status"), DSL.field("aggregatetype"))
            .fetch()

        val byStatus = mutableMapOf<String, Long>()
        val byType = mutableMapOf<String, Long>()
        val details = mutableListOf<OutboxStatDetail>()

        stats.forEach { record ->
            val status = record.get("status", String::class.java) ?: "UNKNOWN"
            val type = record.get("aggregatetype", String::class.java) ?: "UNKNOWN"
            val count = record.get("count", Long::class.java) ?: 0L
            val oldest = record.get("oldest", OffsetDateTime::class.java)
            val newest = record.get("newest", OffsetDateTime::class.java)
            val avgLatency = record.get("avg_latency_sec", Double::class.java)

            byStatus[status] = (byStatus[status] ?: 0L) + count
            byType[type] = (byType[type] ?: 0L) + count

            details.add(
                OutboxStatDetail(
                    status = status,
                    aggregateType = type,
                    count = count,
                    oldest = oldest?.toInstant(),
                    newest = newest?.toInstant(),
                    avgLatencySeconds = avgLatency
                )
            )
        }

        val totalPending = countByStatus(OutboxStatus.PENDING)
        val totalProcessing = countByStatus(OutboxStatus.PROCESSING)
        val totalFailed = countByStatus(OutboxStatus.FAILED)
        val totalProcessed = countByStatus(OutboxStatus.PROCESSED)

        return OutboxStats(
            total = OutboxTotalStats(
                pending = totalPending,
                processing = totalProcessing,
                failed = totalFailed,
                processed = totalProcessed
            ),
            byStatus = byStatus,
            byType = byType,
            details = details
        )
    }

    private fun countByStatus(status: OutboxStatus): Long {
        return dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq(status.name))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun getWorkerStatusInternal(): WorkerStatus {
        val metrics = worker.getMetrics()
        return WorkerStatus(
            running = worker.isRunning(),
            processed = metrics.processed,
            failed = metrics.failed,
            polls = metrics.polls,
            lastPollTime = null
        )
    }

    private fun getDatabaseStatsInternal(): DatabaseStats {
        val rawDataCount = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .fetchOne(0, Long::class.java) ?: 0L

        return DatabaseStats(
            rawDataCount = rawDataCount,
            outboxCount = 0L,
            note = "DynamoDB stats require separate query"
        )
    }
}

// Domain Models are defined in AdminDashboardDtos.kt
