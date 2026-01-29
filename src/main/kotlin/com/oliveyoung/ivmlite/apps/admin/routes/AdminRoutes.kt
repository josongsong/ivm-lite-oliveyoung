package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.apps.admin.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Admin Routes (관리자 페이지용 API)
 *
 * GET /dashboard: 전체 대시보드 데이터
 * GET /outbox/stats: Outbox 통계
 * GET /worker/status: Worker 상태
 * GET /db/stats: 데이터베이스 통계
 * GET /outbox/recent: 최근 처리된 작업
 * GET /outbox/failed: 실패한 작업
 */
fun Route.adminRoutes() {
    val outboxRepo by inject<OutboxRepositoryPort>()
    val worker by inject<OutboxPollingWorker>()
    val dsl by inject<DSLContext>()

    /**
     * GET /dashboard
     * 전체 대시보드 데이터 (한 번에 모든 정보)
     */
    get("/dashboard") {
            try {
                val outboxStats = getOutboxStats(dsl)
                val workerStatus = getWorkerStatus(worker)
                val dbStats = getDatabaseStats(dsl)

                call.respond(
                    HttpStatusCode.OK,
                    DashboardResponse(
                        outbox = outboxStats,
                        worker = workerStatus,
                        database = dbStats,
                        timestamp = Instant.now().toString()
                    )
                )
            } catch (e: Exception) {
                call.application.log.error("Failed to get dashboard data", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "DASHBOARD_ERROR",
                        message = "Failed to get dashboard data: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/stats
     * Outbox 통계
     */
    get("/outbox/stats") {
            try {
                val stats = getOutboxStats(dsl)
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.application.log.error("Failed to get outbox stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "OUTBOX_STATS_ERROR",
                        message = "Failed to get outbox stats: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /worker/status
     * Worker 상태 및 메트릭
     */
    get("/worker/status") {
            try {
                val status = getWorkerStatus(worker)
                call.respond(HttpStatusCode.OK, status)
            } catch (e: Exception) {
                call.application.log.error("Failed to get worker status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "WORKER_STATUS_ERROR",
                        message = "Failed to get worker status: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /db/stats
     * 데이터베이스 통계
     */
    get("/db/stats") {
            try {
                val stats = getDatabaseStats(dsl)
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.application.log.error("Failed to get database stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "DB_STATS_ERROR",
                        message = "Failed to get database stats: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/recent
     * 최근 처리된 Outbox 엔트리
     */
    get("/outbox/recent") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val recent = getRecentOutboxEntries(dsl, limit)
                call.respond(HttpStatusCode.OK, recent)
            } catch (e: Exception) {
                call.application.log.error("Failed to get recent outbox entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "RECENT_OUTBOX_ERROR",
                        message = "Failed to get recent outbox entries: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/failed
     * 실패한 Outbox 엔트리
     */
    get("/outbox/failed") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val failed = getFailedOutboxEntries(dsl, limit)
                call.respond(HttpStatusCode.OK, failed)
            } catch (e: Exception) {
                call.application.log.error("Failed to get failed outbox entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "FAILED_OUTBOX_ERROR",
                        message = "Failed to get failed outbox entries: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/{id}
     * 특정 Outbox 엔트리 상세 조회
     */
    get("/outbox/{id}") {
            try {
                val idParam = call.parameters["id"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MISSING_ID", message = "ID parameter is required")
                    )
                    return@get
                }
                val id = try {
                    UUID.fromString(idParam)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_ID", message = "Invalid UUID format: $idParam")
                    )
                    return@get
                }

                val result = outboxRepo.findById(id)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(HttpStatusCode.OK, result.value.toDto())
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to get outbox entry", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "OUTBOX_ENTRY_ERROR",
                        message = "Failed to get outbox entry: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/dlq
     * Dead Letter Queue 조회
     */
    get("/outbox/dlq") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val result = outboxRepo.findDlq(limit)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(
                            HttpStatusCode.OK,
                            DlqResponse(
                                items = result.value.map { it.toDto() },
                                count = result.value.size
                            )
                        )
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to get DLQ entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "DLQ_ERROR",
                        message = "Failed to get DLQ entries: ${e.message}"
                    )
                )
            }
        }

    /**
     * POST /outbox/dlq/{id}/replay
     * DLQ에서 특정 엔트리 재처리
     */
    post("/outbox/dlq/{id}/replay") {
            try {
                val idParam = call.parameters["id"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MISSING_ID", message = "ID parameter is required")
                    )
                    return@post
                }
                val id = try {
                    UUID.fromString(idParam)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_ID", message = "Invalid UUID format: $idParam")
                    )
                    return@post
                }

                val result = outboxRepo.replayFromDlq(id)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "success" to result.value,
                                "message" to if (result.value) "Replay successful" else "Replay failed"
                            )
                        )
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to replay DLQ entry", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "DLQ_REPLAY_ERROR",
                        message = "Failed to replay DLQ entry: ${e.message}"
                    )
                )
            }
        }

    /**
     * POST /outbox/stale/release
     * Stale PROCESSING 엔트리 복구 (Visibility Timeout)
     */
    post("/outbox/stale/release") {
            try {
                val timeoutSeconds = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 300L
                val result = outboxRepo.releaseExpiredClaims(timeoutSeconds)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "released" to result.value,
                                "message" to "Released ${result.value} stale entries"
                            )
                        )
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to release stale entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "STALE_RELEASE_ERROR",
                        message = "Failed to release stale entries: ${e.message}"
                    )
                )
            }
        }

    /**
     * POST /outbox/{id}/retry
     * 실패한 Outbox 엔트리 재시도 (FAILED → PENDING)
     */
    post("/outbox/{id}/retry") {
            try {
                val idParam = call.parameters["id"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MISSING_ID", message = "ID parameter is required")
                    )
                    return@post
                }
                val id = try {
                    UUID.fromString(idParam)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_ID", message = "Invalid UUID format: $idParam")
                    )
                    return@post
                }

                val result = outboxRepo.resetToPending(id)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(
                            HttpStatusCode.OK,
                            RetryResponse(
                                success = true,
                                message = "Entry reset to PENDING for retry",
                                entry = result.value.toDto()
                            )
                        )
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to retry outbox entry", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "RETRY_ERROR",
                        message = "Failed to retry outbox entry: ${e.message}"
                    )
                )
            }
        }

    /**
     * POST /outbox/failed/retry-all
     * 모든 실패한 작업 일괄 재시도 (FAILED → PENDING)
     */
    post("/outbox/failed/retry-all") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                val result = outboxRepo.resetAllFailed(limit)
                when (result) {
                    is OutboxRepositoryPort.Result.Ok -> {
                        call.respond(
                            HttpStatusCode.OK,
                            BatchRetryResponse(
                                success = true,
                                retriedCount = result.value,
                                message = "Reset ${result.value} failed entries to PENDING"
                            )
                        )
                    }
                    is OutboxRepositoryPort.Result.Err -> {
                        call.respond(
                            result.error.toKtorStatus(),
                            ApiError.from(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to retry all failed entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "BATCH_RETRY_ERROR",
                        message = "Failed to retry all failed entries: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/stats/hourly
     * 시간대별 처리량/에러율 통계 (최근 N시간)
     */
    get("/outbox/stats/hourly") {
            try {
                val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24

                val stats = dsl.select(
                    DSL.field("date_trunc('hour', created_at)").`as`("hour"),
                    DSL.field("status"),
                    DSL.count().`as`("count")
                )
                    .from(DSL.table("outbox"))
                    .where(
                        DSL.field("created_at").greaterThan(
                            DSL.field("NOW() - INTERVAL '$hours hours'")
                        )
                    )
                    .groupBy(
                        DSL.field("date_trunc('hour', created_at)"),
                        DSL.field("status")
                    )
                    .orderBy(DSL.field("hour").asc())
                    .fetch()

                val hourlyData = mutableMapOf<String, HourlyStatItem>()

                stats.forEach { record ->
                    val hour = record.get("hour", java.time.OffsetDateTime::class.java)
                        ?.toInstant()?.toString() ?: return@forEach
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

                    hourlyData[hour] = when (status) {
                        "PENDING" -> item.copy(pending = count, total = item.total + count)
                        "PROCESSING" -> item.copy(processing = count, total = item.total + count)
                        "PROCESSED" -> item.copy(processed = count, total = item.total + count)
                        "FAILED" -> item.copy(failed = count, total = item.total + count)
                        else -> item.copy(total = item.total + count)
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    HourlyStatsResponse(
                        items = hourlyData.values.toList().sortedBy { it.hour },
                        hours = hours
                    )
                )
            } catch (e: Exception) {
                call.application.log.error("Failed to get hourly stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "HOURLY_STATS_ERROR",
                        message = "Failed to get hourly stats: ${e.message}"
                    )
                )
            }
        }

    /**
     * GET /outbox/stale
     * Stale PROCESSING 엔트리 조회
     */
    get("/outbox/stale") {
            try {
                val timeoutSeconds = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 300L
                // Stale 엔트리는 PROCESSING 상태이면서 claimed_at이 timeout 초과한 것들
                // SQL: WHERE claimed_at < NOW() - INTERVAL '300 seconds'
                val staleEntries = dsl.select()
                    .from(DSL.table("outbox"))
                    .where(DSL.field("status").eq("PROCESSING"))
                    .and(DSL.field("claimed_at").isNotNull)
                    .and(
                        DSL.field("claimed_at").lessThan(
                            DSL.field("NOW() - INTERVAL '{} seconds'", timeoutSeconds)
                        )
                    )
                    .orderBy(DSL.field("claimed_at").asc())
                    .limit(100)
                    .fetch()

                val items = staleEntries.map { record ->
                    val claimedAt = record.get("claimed_at", java.time.OffsetDateTime::class.java)
                    StaleOutboxItem(
                        id = record.get("id", UUID::class.java)?.toString() ?: "",
                        aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                        aggregateId = record.get("aggregateid", String::class.java) ?: "",
                        eventType = record.get("type", String::class.java) ?: "",
                        claimedAt = claimedAt?.toInstant()?.toString(),
                        claimedBy = record.get("claimed_by", String::class.java),
                        ageSeconds = claimedAt?.let {
                            java.time.Duration.between(it.toInstant(), java.time.Instant.now()).seconds
                        } ?: 0L
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    StaleOutboxResponse(
                        items = items,
                        count = items.size,
                        timeoutSeconds = timeoutSeconds
                    )
                )
            } catch (e: Exception) {
                call.application.log.error("Failed to get stale entries", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(
                        code = "STALE_ERROR",
                        message = "Failed to get stale entries: ${e.message}"
                    )
                )
            }
        }
}

// ==================== 데이터 조회 함수 ====================

private fun getOutboxStats(dsl: DSLContext): OutboxStatsResponse {
    val stats = dsl.select(
        DSL.field("status"),
        DSL.field("aggregatetype"),
        DSL.count().`as`("count"),
        DSL.min(DSL.field("created_at")).`as`("oldest"),
        DSL.max(DSL.field("created_at")).`as`("newest"),
        DSL.field("AVG(EXTRACT(EPOCH FROM (COALESCE(processed_at, NOW()) - created_at)))").`as`("avg_latency_sec")
    )
        .from(DSL.table("outbox_stats"))
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
        val oldest = record.get("oldest", java.time.OffsetDateTime::class.java)
        val newest = record.get("newest", java.time.OffsetDateTime::class.java)
        val avgLatency = record.get("avg_latency_sec", java.math.BigDecimal::class.java)?.toDouble()

        byStatus[status] = (byStatus[status] ?: 0L) + count
        byType[type] = (byType[type] ?: 0L) + count

        details.add(
            OutboxStatDetail(
                status = status,
                aggregateType = type,
                count = count,
                oldest = oldest?.toInstant()?.toString(),
                newest = newest?.toInstant()?.toString(),
                avgLatencySeconds = avgLatency
            )
        )
    }

    // 전체 통계
    val totalPending = dsl.selectCount()
        .from(DSL.table("outbox"))
        .where(DSL.field("status").eq("PENDING"))
        .fetchOne(0, Int::class.java) ?: 0

    val totalProcessing = dsl.selectCount()
        .from(DSL.table("outbox"))
        .where(DSL.field("status").eq("PROCESSING"))
        .fetchOne(0, Int::class.java) ?: 0

    val totalFailed = dsl.selectCount()
        .from(DSL.table("outbox"))
        .where(DSL.field("status").eq("FAILED"))
        .fetchOne(0, Int::class.java) ?: 0

    val totalProcessed = dsl.selectCount()
        .from(DSL.table("outbox"))
        .where(DSL.field("status").eq("PROCESSED"))
        .fetchOne(0, Int::class.java) ?: 0

    return OutboxStatsResponse(
        total = OutboxTotalStats(
            pending = totalPending.toLong(),
            processing = totalProcessing.toLong(),
            failed = totalFailed.toLong(),
            processed = totalProcessed.toLong()
        ),
        byStatus = byStatus,
        byType = byType,
        details = details
    )
}

private fun getWorkerStatus(worker: OutboxPollingWorker): WorkerStatusResponse {
    val metrics = worker.getMetrics()
    return WorkerStatusResponse(
        running = worker.isRunning(),
        processed = metrics.processed,
        failed = metrics.failed,
        polls = metrics.polls,
        lastPollTime = null // TODO: Metrics에 lastPollTime 추가 필요
    )
}

private fun getDatabaseStats(dsl: DSLContext): DatabaseStatsResponse {
    val rawDataCount = dsl.selectCount()
        .from(DSL.table("raw_data"))
        .fetchOne(0, Int::class.java) ?: 0

    // DynamoDB는 별도 조회 필요 (현재는 PostgreSQL만)
    return DatabaseStatsResponse(
        rawDataCount = rawDataCount.toLong(),
        outboxCount = 0L, // 위에서 계산됨
        note = "DynamoDB stats require separate query"
    )
}

private fun getRecentOutboxEntries(dsl: DSLContext, limit: Int): RecentOutboxResponse {
    val entries = dsl.select()
        .from(DSL.table("outbox"))
        .orderBy(DSL.field("created_at").desc())
        .limit(limit)
        .fetch()

    val items = entries.map { record ->
        RecentOutboxItem(
            id = record.get("id", UUID::class.java)?.toString() ?: "",
            aggregateType = record.get("aggregatetype", String::class.java) ?: "",
            aggregateId = record.get("aggregateid", String::class.java) ?: "",
            eventType = record.get("type", String::class.java) ?: "",
            status = record.get("status", String::class.java) ?: "",
            createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()?.toString(),
            processedAt = record.get("processed_at", java.time.OffsetDateTime::class.java)?.toInstant()?.toString(),
            retryCount = record.get("retry_count", Int::class.java) ?: 0
        )
    }

    return RecentOutboxResponse(items = items, count = items.size)
}

private fun getFailedOutboxEntries(dsl: DSLContext, limit: Int): FailedOutboxResponse {
    val entries = dsl.select()
        .from(DSL.table("outbox"))
        .where(DSL.field("status").eq("FAILED"))
        .orderBy(DSL.field("created_at").desc())
        .limit(limit)
        .fetch()

    val items = entries.map { record ->
        FailedOutboxItem(
            id = record.get("id", UUID::class.java)?.toString() ?: "",
            aggregateType = record.get("aggregatetype", String::class.java) ?: "",
            aggregateId = record.get("aggregateid", String::class.java) ?: "",
            eventType = record.get("type", String::class.java) ?: "",
            createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()?.toString(),
            retryCount = record.get("retry_count", Int::class.java) ?: 0,
            failureReason = record.get("failure_reason", String::class.java)
        )
    }

    return FailedOutboxResponse(items = items, count = items.size)
}

// ==================== Response DTOs ====================

@Serializable
data class DashboardResponse(
    val outbox: OutboxStatsResponse,
    val worker: WorkerStatusResponse,
    val database: DatabaseStatsResponse,
    val timestamp: String  // Instant를 String으로 변환
)

@Serializable
data class OutboxStatsResponse(
    val total: OutboxTotalStats,
    val byStatus: Map<String, Long>,
    val byType: Map<String, Long>,
    val details: List<OutboxStatDetail>
)

@Serializable
data class OutboxTotalStats(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val processed: Long
)

@Serializable
data class OutboxStatDetail(
    val status: String,
    val aggregateType: String,
    val count: Long,
    val oldest: String?,
    val newest: String?,
    val avgLatencySeconds: Double?
)

@Serializable
data class WorkerStatusResponse(
    val running: Boolean,
    val processed: Long,
    val failed: Long,
    val polls: Long,
    val lastPollTime: Long?
)

@Serializable
data class DatabaseStatsResponse(
    val rawDataCount: Long,
    val outboxCount: Long,
    val note: String
)

@Serializable
data class RecentOutboxResponse(
    val items: List<RecentOutboxItem>,
    val count: Int
)

@Serializable
data class RecentOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val status: String,
    val createdAt: String?,
    val processedAt: String?,
    val retryCount: Int
)

@Serializable
data class FailedOutboxResponse(
    val items: List<FailedOutboxItem>,
    val count: Int
)

@Serializable
data class FailedOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val createdAt: String?,
    val retryCount: Int,
    val failureReason: String?
)

@Serializable
data class DlqResponse(
    val items: List<OutboxEntryDto>,
    val count: Int
)

@Serializable
data class RetryResponse(
    val success: Boolean,
    val message: String,
    val entry: OutboxEntryDto?
)

@Serializable
data class BatchRetryResponse(
    val success: Boolean,
    val retriedCount: Int,
    val message: String
)

@Serializable
data class StaleOutboxResponse(
    val items: List<StaleOutboxItem>,
    val count: Int,
    val timeoutSeconds: Long
)

@Serializable
data class StaleOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val claimedAt: String?,
    val claimedBy: String?,
    val ageSeconds: Long
)

@Serializable
data class HourlyStatsResponse(
    val items: List<HourlyStatItem>,
    val hours: Int
)

@Serializable
data class HourlyStatItem(
    val hour: String,
    val pending: Long,
    val processing: Long,
    val processed: Long,
    val failed: Long,
    val total: Long
)

@Serializable
data class OutboxEntryDto(
    val id: String,
    val idempotencyKey: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: String,
    val createdAt: String,
    val processedAt: String?,
    val claimedAt: String?,
    val claimedBy: String?,
    val retryCount: Int,
    val failureReason: String?,
    val priority: Int? = null,
    val entityVersion: Long? = null
)

// ==================== DTO 변환 함수 ====================

private fun com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry.toDto(): OutboxEntryDto {
    return OutboxEntryDto(
        id = id.toString(),
        idempotencyKey = idempotencyKey,
        aggregateType = aggregateType.name,
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        status = status.name,
        createdAt = createdAt.toString(),
        processedAt = processedAt?.toString(),
        claimedAt = claimedAt?.toString(),
        claimedBy = claimedBy,
        retryCount = retryCount,
        failureReason = failureReason,
        priority = priority.takeIf { it != com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry.DEFAULT_PRIORITY },
        entityVersion = entityVersion
    )
}
