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
 * RawData/Slice/SinkEvent: DynamoDB 기반. Outbox 제거됨.
 * /outbox/ 경로는 Admin UI 호환을 위해 유지 (SinkEvent 데이터 반환)
 *
 * GET /dashboard: 전체 대시보드 데이터
 * GET /outbox/stats: SinkEvent 통계
 * GET /worker/status: Worker 상태 (제거됨, stopped 반환)
 * GET /db/stats: RawData/SinkEvent 통계 (DynamoDB)
 * GET /outbox/recent: 최근 SinkEvent
 * GET /outbox/failed: 실패한 SinkEvent
 * GET /outbox/{id}: SinkEvent 상세
 * GET /outbox/dlq: DLQ (미지원)
 * POST /outbox/dlq/{id}/replay: DLQ 재처리 (미지원)
 * POST /outbox/stale/release: Stale 복구 (미지원)
 * POST /outbox/{id}/retry: 재시도 (미지원)
 * POST /outbox/failed/retry-all: 전체 재시도 (미지원)
 * GET /outbox/stats/hourly: 시간대별 통계 (미지원)
 * GET /outbox/stale: Stale 조회 (미지원)
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
        when (val result = dashboardService.getSinkEventStats()) {
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

        when (val result = dashboardService.getRecentSinkEvents(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, RecentSinkEventResponse(
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

        when (val result = dashboardService.getFailedSinkEvents(limit)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, FailedSinkEventResponse(
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

        when (val result = dashboardService.getSinkEventEntry(id)) {
            is Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toSinkEventResponse())
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
                    items = result.value.map { it.toSinkEventResponse() },
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
                    entry = result.value.toSinkEventResponse()
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
    val sinkEvent: SinkEventStatsResponse,
    val worker: WorkerStatusResponse,
    val database: DatabaseStatsResponse,
    val timestamp: String
)

@Serializable
data class SinkEventStatsResponse(
    val total: SinkEventTotalStatsResponse,
    val byStatus: Map<String, Long>,
    val details: List<SinkEventStatDetailResponse>
)

@Serializable
data class SinkEventTotalStatsResponse(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val completed: Long
)

@Serializable
data class SinkEventStatDetailResponse(
    val status: String,
    val viewType: String,
    val count: Long,
    val oldest: String?,
    val newest: String?
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
    val sinkEventCount: Long,
    val contractsCount: Long = 0L,
    val note: String
)

@Serializable
data class RecentSinkEventResponse(
    val items: List<RecentSinkEventItemResponse>,
    val count: Int
)

@Serializable
data class RecentSinkEventItemResponse(
    val id: String,
    val entityKey: String,
    val viewType: String,
    val status: String,
    val createdAt: String?,
    val processedAt: String?
)

@Serializable
data class FailedSinkEventResponse(
    val items: List<FailedSinkEventItemResponse>,
    val count: Int
)

@Serializable
data class FailedSinkEventItemResponse(
    val id: String,
    val entityKey: String,
    val viewType: String,
    val createdAt: String?
)

@Serializable
data class SinkEventEntryResponse(
    val id: String,
    val idempotencyKey: String,
    val entityKey: String,
    val viewType: String,
    val status: String,
    val createdAt: String,
    val processedAt: String?,
    val sinkTargets: List<String>
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
    val items: List<SinkEventEntryResponse>,
    val count: Int
)

@Serializable
data class RetryResponse(
    val success: Boolean,
    val message: String,
    val entry: SinkEventEntryResponse?
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
    sinkEvent = sinkEvent.toResponse(),
    worker = worker.toResponse(),
    database = database.toResponse(),
    timestamp = timestamp.toString()
)

private fun SinkEventStats.toResponse() = SinkEventStatsResponse(
    total = SinkEventTotalStatsResponse(total.pending, total.processing, total.failed, total.completed),
    byStatus = byStatus,
    details = details.map { it.toResponse() }
)

private fun SinkEventStatDetail.toResponse() = SinkEventStatDetailResponse(
    status = status,
    viewType = viewType,
    count = count,
    oldest = oldest?.toString(),
    newest = newest?.toString()
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
    sinkEventCount = sinkEventCount,
    contractsCount = contractsCount,
    note = note
)

private fun RecentSinkEventItem.toResponse() = RecentSinkEventItemResponse(
    id = id,
    entityKey = entityKey,
    viewType = viewType,
    status = status,
    createdAt = createdAt?.toString(),
    processedAt = processedAt?.toString()
)

private fun FailedSinkEventItem.toResponse() = FailedSinkEventItemResponse(
    id = id,
    entityKey = entityKey,
    viewType = viewType,
    createdAt = createdAt?.toString()
)

private fun SinkEventEntryDetail.toSinkEventResponse() = SinkEventEntryResponse(
    id = id,
    idempotencyKey = idempotencyKey,
    entityKey = entityKey,
    viewType = viewType,
    status = status,
    createdAt = createdAt.toString(),
    processedAt = processedAt?.toString(),
    sinkTargets = sinkTargets
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
