package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.observability.application.ObservabilityService
import com.oliveyoung.ivmlite.pkg.observability.application.PipelineDashboard
import com.oliveyoung.ivmlite.pkg.observability.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Duration
import java.time.Instant

/**
 * Observability Routes (파이프라인 모니터링 API)
 *
 * GET /observability/dashboard: 대시보드 데이터
 * GET /observability/lag: Lag 정보
 * GET /observability/throughput: 처리량
 * GET /observability/latency: 지연 시간
 * GET /observability/queues: 큐 상태
 * GET /observability/timeseries/{metric}: 시계열 데이터
 */
fun Route.observabilityRoutes() {
    val observabilityService by inject<ObservabilityService>()
    
    /**
     * GET /observability/dashboard
     * 파이프라인 대시보드
     */
    get("/observability/dashboard") {
        try {
            val dashboard = observabilityService.getDashboard()
            call.respond(HttpStatusCode.OK, dashboard.toDto())
        } catch (e: Exception) {
            call.application.log.error("Failed to get observability dashboard", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "OBSERVABILITY_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /observability/lag
     * 현재 Lag 정보
     */
    get("/observability/lag") {
        try {
            val lag = observabilityService.getLag()
            call.respond(HttpStatusCode.OK, lag.toDto())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "LAG_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /observability/throughput
     * 현재 처리량
     */
    get("/observability/throughput") {
        try {
            val windowMinutes = call.request.queryParameters["window"]?.toIntOrNull() ?: 1
            val throughput = observabilityService.getThroughput(windowMinutes)
            call.respond(HttpStatusCode.OK, throughput.toDto())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "THROUGHPUT_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /observability/latency
     * 지연 시간
     */
    get("/observability/latency") {
        try {
            val windowMinutes = call.request.queryParameters["window"]?.toIntOrNull() ?: 5
            val latency = observabilityService.getLatency(windowMinutes)
            call.respond(HttpStatusCode.OK, latency.toDto())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "LATENCY_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /observability/queues
     * 큐 상태
     */
    get("/observability/queues") {
        try {
            val queues = observabilityService.getQueueStatus()
            call.respond(HttpStatusCode.OK, queues.toDto())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "QUEUE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /observability/timeseries/{metric}
     * 시계열 데이터
     */
    get("/observability/timeseries/{metric}") {
        try {
            val metricName = call.parameters["metric"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "MISSING_METRIC", message = "Metric name required"))
                return@get
            }
            
            val fromParam = call.request.queryParameters["from"]
            val toParam = call.request.queryParameters["to"]
            val resolutionMinutes = call.request.queryParameters["resolution"]?.toLongOrNull() ?: 1
            
            val to = if (toParam != null) Instant.parse(toParam) else Instant.now()
            val from = if (fromParam != null) Instant.parse(fromParam) else to.minusSeconds(3600) // 기본 1시간
            
            val timeSeries = observabilityService.getTimeSeries(
                metricName = metricName,
                from = from,
                to = to,
                resolution = Duration.ofMinutes(resolutionMinutes)
            )
            
            call.respond(HttpStatusCode.OK, timeSeries.toDto())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "TIMESERIES_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
}

// ==================== DTOs ====================

@Serializable
data class PipelineDashboardDto(
    val metrics: PipelineMetricsDto,
    val lag: LagMetricsDto,
    val status: PipelineStatusDto,
    val timestamp: String
)

@Serializable
data class PipelineMetricsDto(
    val e2eLatency: LatencyMetricsDto,
    val throughput: ThroughputMetricsDto,
    val queueDepths: QueueDepthMetricsDto
)

@Serializable
data class LatencyMetricsDto(
    val avgMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val sampleCount: Long
)

@Serializable
data class ThroughputMetricsDto(
    val recordsPerSecond: Double,
    val recordsPerMinute: Double,
    val recordsPerHour: Double,
    val recentMinuteCount: Long
)

@Serializable
data class QueueDepthMetricsDto(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val dlq: Long,
    val stale: Long,
    val totalPending: Long,
    val totalProblematic: Long
)

@Serializable
data class LagMetricsDto(
    val currentLag: Long,
    val estimatedLagSeconds: Long?,
    val trend: String,
    val delta: Long,
    val timestamp: String
)

@Serializable
data class PipelineStatusDto(
    val health: String,
    val issues: List<String>,
    val summary: String
)

@Serializable
data class TimeSeriesDto(
    val name: String,
    val points: List<TimeSeriesPointDto>,
    val unit: String
)

@Serializable
data class TimeSeriesPointDto(
    val timestamp: String,
    val value: Double
)

private fun PipelineDashboard.toDto() = PipelineDashboardDto(
    metrics = metrics.toDto(),
    lag = lag.toDto(),
    status = PipelineStatusDto(
        health = status.health.name,
        issues = status.issues,
        summary = status.summary
    ),
    timestamp = timestamp.toString()
)

private fun PipelineMetrics.toDto() = PipelineMetricsDto(
    e2eLatency = e2eLatency.toDto(),
    throughput = throughput.toDto(),
    queueDepths = queueDepths.toDto()
)

private fun LatencyMetrics.toDto() = LatencyMetricsDto(
    avgMs = avgMs,
    p50Ms = p50Ms,
    p95Ms = p95Ms,
    p99Ms = p99Ms,
    maxMs = maxMs,
    sampleCount = sampleCount
)

private fun ThroughputMetrics.toDto() = ThroughputMetricsDto(
    recordsPerSecond = recordsPerSecond,
    recordsPerMinute = recordsPerMinute,
    recordsPerHour = recordsPerHour,
    recentMinuteCount = recentMinuteCount
)

private fun QueueDepthMetrics.toDto() = QueueDepthMetricsDto(
    pending = pending,
    processing = processing,
    failed = failed,
    dlq = dlq,
    stale = stale,
    totalPending = totalPending,
    totalProblematic = totalProblematic
)

private fun LagMetrics.toDto() = LagMetricsDto(
    currentLag = currentLag,
    estimatedLagSeconds = estimatedLagDuration?.seconds,
    trend = trend.name,
    delta = delta,
    timestamp = timestamp.toString()
)

private fun TimeSeries.toDto() = TimeSeriesDto(
    name = name,
    points = points.map { TimeSeriesPointDto(it.timestamp.toString(), it.value) },
    unit = unit
)
