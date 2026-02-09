package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.*
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Admin Routes (관리자 페이지용 API)
 *
 * SOTA 리팩토링:
 * - Service 레이어로 비즈니스 로직 분리
 * - StatusPages로 에러 처리 (try-catch 제거)
 * - OutboxStatus enum 사용 (하드코딩 제거)
 *
 * GET /dashboard: 전체 대시보드 데이터
 * GET /outbox/stats: Outbox 통계
 * GET /worker/status: Worker 상태
 * GET /db/stats: 데이터베이스 통계
 * GET /outbox/recent: 최근 처리된 작업
 * GET /outbox/failed: 실패한 작업
 * GET /outbox/{id}: 특정 Outbox 엔트리 상세
 * GET /outbox/dlq: Dead Letter Queue
 * POST /outbox/dlq/{id}/replay: DLQ 재처리
 * POST /outbox/stale/release: Stale 엔트리 복구
 * POST /outbox/{id}/retry: 실패 엔트리 재시도
 * POST /outbox/failed/retry-all: 모든 실패 엔트리 재시도
 * GET /outbox/stats/hourly: 시간대별 통계
 * GET /outbox/stale: Stale 엔트리 조회
 */
fun Route.adminRoutes() {
    val dashboardService by inject<AdminDashboardService>()

    /**
     * GET /dashboard
     * 전체 대시보드 데이터
     */
    get("/dashboard") {
        when (val result = dashboardService.getDashboard()) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/stats
     * Outbox 통계
     */
    get("/outbox/stats") {
        when (val result = dashboardService.getOutboxStats()) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /worker/status
     * Worker 상태
     */
    get("/worker/status") {
        when (val result = dashboardService.getWorkerStatus()) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /db/stats
     * 데이터베이스 통계
     */
    get("/db/stats") {
        when (val result = dashboardService.getDatabaseStats()) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/recent
     * 최근 Outbox 엔트리
     */
    get("/outbox/recent") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        when (val result = dashboardService.getRecentOutbox(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, RecentOutboxResponse(
                    items = result.value.map { it.toResponse() },
                    count = result.value.size
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/failed
     * 실패한 Outbox 엔트리
     */
    get("/outbox/failed") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

        when (val result = dashboardService.getFailedOutbox(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, FailedOutboxResponse(
                    items = result.value.map { it.toResponse() },
                    count = result.value.size
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/{id}
     * 특정 Outbox 엔트리 상세
     */
    get("/outbox/{id}") {
        val idParam = call.parameters["id"]
            ?: throw IllegalArgumentException("ID parameter is required")
        val id = try {
            UUID.fromString(idParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID format: $idParam")
        }

        when (val result = dashboardService.getOutboxEntry(id)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/dlq
     * Dead Letter Queue
     */
    get("/outbox/dlq") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        when (val result = dashboardService.getDlq(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, DlqResponse(
                    items = result.value.map { it.toResponse() },
                    count = result.value.size
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * POST /outbox/dlq/{id}/replay
     * DLQ 재처리
     */
    post("/outbox/dlq/{id}/replay") {
        val idParam = call.parameters["id"]
            ?: throw IllegalArgumentException("ID parameter is required")
        val id = try {
            UUID.fromString(idParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID format: $idParam")
        }

        when (val result = dashboardService.replayDlq(id)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to result.value,
                    "message" to if (result.value) "Replay successful" else "Replay failed"
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * POST /outbox/stale/release
     * Stale PROCESSING 엔트리 복구
     */
    post("/outbox/stale/release") {
        val timeoutSeconds = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 300L

        when (val result = dashboardService.releaseStale(timeoutSeconds)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, mapOf(
                    "released" to result.value,
                    "message" to "Released ${result.value} stale entries"
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * POST /outbox/{id}/retry
     * 실패한 엔트리 재시도
     */
    post("/outbox/{id}/retry") {
        val idParam = call.parameters["id"]
            ?: throw IllegalArgumentException("ID parameter is required")
        val id = try {
            UUID.fromString(idParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID format: $idParam")
        }

        when (val result = dashboardService.retryEntry(id)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, RetryResponse(
                    success = true,
                    message = "Entry reset to PENDING for retry",
                    entry = result.value.toResponse()
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * POST /outbox/failed/retry-all
     * 모든 실패 엔트리 재시도
     */
    post("/outbox/failed/retry-all") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

        when (val result = dashboardService.retryAllFailed(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, BatchRetryResponse(
                    success = true,
                    retriedCount = result.value,
                    message = "Reset ${result.value} failed entries to PENDING"
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/stats/hourly
     * 시간대별 통계
     */
    get("/outbox/stats/hourly") {
        val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24

        when (val result = dashboardService.getHourlyStats(hours)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /outbox/stale
     * Stale PROCESSING 엔트리 조회
     */
    get("/outbox/stale") {
        val timeoutSeconds = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 300L

        when (val result = dashboardService.getStaleEntries(timeoutSeconds)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, StaleOutboxResponse(
                    items = result.value.map { it.toResponse() },
                    count = result.value.size,
                    timeoutSeconds = timeoutSeconds
                ))
            }
            is Result.Err -> {
                throw result.error
            }
        }
    }
}

// ==================== Response DTOs ====================

@Serializable
data class DashboardResponse(
    val outbox: OutboxStatsResponse,
    val worker: WorkerStatusResponse,
    val database: DatabaseStatsResponse,
    val timestamp: String
)

@Serializable
data class OutboxStatsResponse(
    val total: OutboxTotalStatsResponse,
    val byStatus: Map<String, Long>,
    val byType: Map<String, Long>,
    val details: List<OutboxStatDetailResponse>
)

@Serializable
data class OutboxTotalStatsResponse(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val processed: Long
)

@Serializable
data class OutboxStatDetailResponse(
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
    val items: List<RecentOutboxItemResponse>,
    val count: Int
)

@Serializable
data class RecentOutboxItemResponse(
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
    val items: List<FailedOutboxItemResponse>,
    val count: Int
)

@Serializable
data class FailedOutboxItemResponse(
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
    val items: List<OutboxEntryResponse>,
    val count: Int
)

@Serializable
data class OutboxEntryResponse(
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

@Serializable
data class RetryResponse(
    val success: Boolean,
    val message: String,
    val entry: OutboxEntryResponse?
)

@Serializable
data class BatchRetryResponse(
    val success: Boolean,
    val retriedCount: Int,
    val message: String
)

@Serializable
data class HourlyStatsResponse(
    val items: List<HourlyStatItemResponse>,
    val hours: Int
)

@Serializable
data class HourlyStatItemResponse(
    val hour: String,
    val pending: Long,
    val processing: Long,
    val processed: Long,
    val failed: Long,
    val total: Long
)

@Serializable
data class StaleOutboxResponse(
    val items: List<StaleOutboxItemResponse>,
    val count: Int,
    val timeoutSeconds: Long
)

@Serializable
data class StaleOutboxItemResponse(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val claimedAt: String?,
    val claimedBy: String?,
    val ageSeconds: Long
)

// ==================== Domain → DTO 변환 ====================

private fun DashboardData.toResponse() = DashboardResponse(
    outbox = outbox.toResponse(),
    worker = worker.toResponse(),
    database = database.toResponse(),
    timestamp = timestamp.toString()
)

private fun OutboxStats.toResponse() = OutboxStatsResponse(
    total = OutboxTotalStatsResponse(total.pending, total.processing, total.failed, total.processed),
    byStatus = byStatus,
    byType = byType,
    details = details.map { it.toResponse() }
)

private fun OutboxStatDetail.toResponse() = OutboxStatDetailResponse(
    status = status,
    aggregateType = aggregateType,
    count = count,
    oldest = oldest?.toString(),
    newest = newest?.toString(),
    avgLatencySeconds = avgLatencySeconds
)

private fun WorkerStatus.toResponse() = WorkerStatusResponse(
    running = running,
    processed = processed,
    failed = failed,
    polls = polls,
    lastPollTime = lastPollTime
)

private fun DatabaseStats.toResponse() = DatabaseStatsResponse(
    rawDataCount = rawDataCount,
    outboxCount = outboxCount,
    note = note
)

private fun RecentOutboxItem.toResponse() = RecentOutboxItemResponse(
    id = id,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    status = status,
    createdAt = createdAt?.toString(),
    processedAt = processedAt?.toString(),
    retryCount = retryCount
)

private fun FailedOutboxItem.toResponse() = FailedOutboxItemResponse(
    id = id,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    createdAt = createdAt?.toString(),
    retryCount = retryCount,
    failureReason = failureReason
)

private fun OutboxEntryDetail.toResponse() = OutboxEntryResponse(
    id = id,
    idempotencyKey = idempotencyKey,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    payload = payload,
    status = status,
    createdAt = createdAt.toString(),
    processedAt = processedAt?.toString(),
    claimedAt = claimedAt?.toString(),
    claimedBy = claimedBy,
    retryCount = retryCount,
    failureReason = failureReason,
    priority = priority,
    entityVersion = entityVersion
)

private fun HourlyStatsData.toResponse() = HourlyStatsResponse(
    items = items.map { it.toResponse() },
    hours = hours
)

private fun HourlyStatItem.toResponse() = HourlyStatItemResponse(
    hour = hour.toString(),
    pending = pending,
    processing = processing,
    processed = processed,
    failed = failed,
    total = total
)

private fun StaleOutboxItem.toResponse() = StaleOutboxItemResponse(
    id = id,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    claimedAt = claimedAt?.toString(),
    claimedBy = claimedBy,
    ageSeconds = ageSeconds
)
