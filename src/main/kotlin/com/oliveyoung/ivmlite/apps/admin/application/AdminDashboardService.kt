package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Admin Dashboard Service (SinkEvent 기반)
 *
 * RawData/Slice: DynamoDB (ExplorerRepositoryPort)
 * SinkEvent: DynamoDB. Outbox 제거됨.
 */
class AdminDashboardService(
    private val sinkEventRepo: SinkEventRepositoryPort,
    private val explorerRepo: ExplorerRepositoryPort
) {
    private val logger = LoggerFactory.getLogger(AdminDashboardService::class.java)

    suspend fun getDashboard(): Result<DashboardData> {
        return try {
            val sinkEventStats = getSinkEventStatsInternal()
            val dbStats = getDatabaseStatsInternal()

            Result.Ok(
                DashboardData(
                    sinkEvent = sinkEventStats,
                    worker = WorkerStatus(
                        running = false,
                        processed = 0,
                        failed = 0,
                        polls = 0,
                        lastPollTime = null
                    ),
                    database = dbStats,
                    timestamp = Instant.now()
                )
            )
        } catch (e: Exception) {
            logger.error("[Dashboard] Failed to get dashboard data", e)
            Result.Err(DomainError.StorageError("Failed to get dashboard data: ${e.message}"))
        }
    }

    suspend fun getWorkerStatus(): Result<WorkerStatus> {
        return Result.Ok(
            WorkerStatus(
                running = false,
                processed = 0,
                failed = 0,
                polls = 0,
                lastPollTime = null
            )
        )
    }

    suspend fun getSinkEventStats(): Result<SinkEventStats> {
        return try {
            Result.Ok(getSinkEventStatsInternal())
        } catch (e: Exception) {
            logger.error("[SinkEventStats] Failed to get sink event stats", e)
            Result.Err(DomainError.StorageError("Failed to get sink event stats: ${e.message}"))
        }
    }

    suspend fun getDatabaseStats(): Result<DatabaseStats> {
        return try {
            Result.Ok(getDatabaseStatsInternal())
        } catch (e: Exception) {
            logger.error("[DatabaseStats] Failed to get database stats", e)
            Result.Err(DomainError.StorageError("Failed to get database stats: ${e.message}"))
        }
    }

    suspend fun getRecentSinkEvents(limit: Int): Result<List<RecentSinkEventItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            val all = mutableListOf<RecentSinkEventItem>()
            listOf("PENDING", "PROCESSING", "COMPLETED").forEach { status ->
                when (val result = sinkEventRepo.findByStatus(status, safeLimit / 3)) {
                    is Result.Ok -> all.addAll(result.value.map { it.toRecentItem() })
                    is Result.Err -> logger.warn("Failed to get $status events: ${result.error}")
                }
            }
            Result.Ok(all.sortedByDescending { it.createdAt?.toEpochMilli() ?: 0L }.take(safeLimit))
        } catch (e: Exception) {
            logger.error("[RecentSinkEvents] Failed", e)
            Result.Err(DomainError.StorageError("Failed to get recent sink events: ${e.message}"))
        }
    }

    suspend fun getFailedSinkEvents(limit: Int): Result<List<FailedSinkEventItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            when (val result = sinkEventRepo.findByStatus("FAILED", safeLimit)) {
                is Result.Ok -> Result.Ok(result.value.map { it.toFailedItem() })
                is Result.Err -> Result.Err(result.error)
            }
        } catch (e: Exception) {
            logger.error("[FailedSinkEvents] Failed", e)
            Result.Err(DomainError.StorageError("Failed to get failed sink events: ${e.message}"))
        }
    }

    suspend fun getDlq(limit: Int): Result<List<SinkEventEntryDetail>> =
        Result.Ok(emptyList())  // DLQ 미지원 (SinkEvent 기반, Outbox 제거됨)

    suspend fun replayDlq(id: UUID): Result<Boolean> =
        Result.Err(DomainError.ValidationError("replay", "DLQ replay 미지원 (Outbox 제거됨)"))

    suspend fun releaseStale(timeoutSeconds: Long): Result<Int> =
        Result.Ok(0)  // Stale 미지원

    suspend fun retryEntry(id: UUID): Result<SinkEventEntryDetail> =
        Result.Err(DomainError.ValidationError("retry", "Retry 미지원 (SinkEvent는 Lambda 처리)"))

    suspend fun retryAllFailed(limit: Int): Result<Int> =
        Result.Ok(0)  // 미지원

    suspend fun getHourlyStats(hours: Int): Result<HourlyStatsData> =
        Result.Ok(HourlyStatsData(items = emptyList(), hours = hours))

    suspend fun getStaleEntries(timeoutSeconds: Long): Result<List<StaleOutboxItem>> =
        Result.Ok(emptyList())  // Stale 미지원

    suspend fun getSinkEventEntry(id: UUID): Result<SinkEventEntryDetail> {
        return when (val result = sinkEventRepo.findById(id)) {
            is Result.Ok -> {
                val event = result.value
                if (event == null) {
                    Result.Err(DomainError.ValidationError("id", "SinkEvent not found: $id"))
                } else {
                    Result.Ok(
                        SinkEventEntryDetail(
                            id = event.id.toString(),
                            idempotencyKey = event.idempotencyKey,
                            entityKey = event.entityKey,
                            viewType = event.viewType,
                            status = event.status.name,
                            createdAt = event.createdAt,
                            processedAt = event.processedAt,
                            sinkTargets = event.sinkTargets
                        )
                    )
                }
            }
            is Result.Err -> Result.Err(result.error)
        }
    }

    private suspend fun getSinkEventStatsInternal(): SinkEventStats {
        val pending = (sinkEventRepo.findByStatus("PENDING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
        val processing = (sinkEventRepo.findByStatus("PROCESSING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
        val failed = (sinkEventRepo.findByStatus("FAILED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
        val completed = (sinkEventRepo.findByStatus("COMPLETED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L

        val byStatus = mapOf(
            "PENDING" to pending,
            "PROCESSING" to processing,
            "FAILED" to failed,
            "COMPLETED" to completed
        )

        val details = listOf(
            SinkEventStatDetail("PENDING", "SINK_EVENT", pending, null, null),
            SinkEventStatDetail("PROCESSING", "SINK_EVENT", processing, null, null),
            SinkEventStatDetail("FAILED", "SINK_EVENT", failed, null, null),
            SinkEventStatDetail("COMPLETED", "SINK_EVENT", completed, null, null)
        )

        return SinkEventStats(
            total = SinkEventTotalStats(pending, processing, failed, completed),
            byStatus = byStatus,
            details = details
        )
    }

    private suspend fun getDatabaseStatsInternal(): DatabaseStats = withContext(Dispatchers.IO) {
        val rawDataCount = explorerRepo?.getRawDataStats(TenantId("oliveyoung"))?.fold(
            { _ -> 0L },
            { it.total }
        ) ?: 0L

        val sinkEventCount = run {
            val p = (sinkEventRepo.findByStatus("PENDING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val pr = (sinkEventRepo.findByStatus("PROCESSING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val f = (sinkEventRepo.findByStatus("FAILED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val c = (sinkEventRepo.findByStatus("COMPLETED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            p + pr + f + c
        }

        DatabaseStats(
            rawDataCount = rawDataCount,
            sinkEventCount = sinkEventCount,
            contractsCount = 0L,
            note = "RawData/Slice/SinkEvent는 DynamoDB에서 조회"
        )
    }
}

private fun com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent.toRecentItem() = RecentSinkEventItem(
    id = id.toString(),
    entityKey = entityKey,
    viewType = viewType,
    status = status.name,
    createdAt = createdAt,
    processedAt = processedAt
)

private fun com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent.toFailedItem() = FailedSinkEventItem(
    id = id.toString(),
    entityKey = entityKey,
    viewType = viewType,
    createdAt = createdAt
)
