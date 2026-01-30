package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.TraceService
import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Trace Routes (AWS X-Ray 연동)
 *
 * GET /api/traces - 트레이스 목록 조회
 * GET /api/traces/{traceId} - 트레이스 상세 조회
 * GET /api/traces/service-map - 서비스 맵 조회
 */
fun Route.traceRoutes() {
    val traceService by inject<TraceService>()

    /**
     * GET /api/traces
     * 트레이스 목록 조회 (필터링, 페이징)
     *
     * Query Parameters:
     * - startTime: ISO8601 형식 시작 시간 (선택)
     * - endTime: ISO8601 형식 종료 시간 (선택)
     * - serviceName: 서비스 이름 필터 (선택)
     * - limit: 최대 결과 수 (기본: 100, 최대: 1000)
     * - nextToken: 페이징 토큰 (선택)
     */
    get("/traces") {
        try {
            val startTimeParam = call.request.queryParameters["startTime"]
            val endTimeParam = call.request.queryParameters["endTime"]
            val serviceName = call.request.queryParameters["serviceName"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val nextToken = call.request.queryParameters["nextToken"]

            val startTime = startTimeParam?.let {
                try {
                    Instant.parse(it)
                } catch (e: DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_START_TIME", message = "Invalid startTime format. Use ISO8601.")
                    )
                    return@get
                }
            }

            val endTime = endTimeParam?.let {
                try {
                    Instant.parse(it)
                } catch (e: DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_END_TIME", message = "Invalid endTime format. Use ISO8601.")
                    )
                    return@get
                }
            }

            when (val result = traceService.getTraces(
                startTime = startTime,
                endTime = endTime,
                serviceName = serviceName,
                limit = limit.coerceIn(1, 1000),
                nextToken = nextToken
            )) {
                is arrow.core.Either.Left -> {
                    val error = result.value
                    call.respond(
                        when (error) {
                            is DomainError.StorageError -> HttpStatusCode.InternalServerError
                            is DomainError.ValidationError -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        },
                        ApiError(code = error::class.simpleName ?: "UNKNOWN_ERROR", message = error.message ?: "Unknown error")
                    )
                }
                is arrow.core.Either.Right -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to get traces", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "TRACE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }

    /**
     * GET /api/traces/{traceId}
     * 트레이스 상세 조회 (spans 포함)
     *
     * Path Parameters:
     * - traceId: 트레이스 ID
     */
    get("/traces/{traceId}") {
        try {
            val traceId = call.parameters["traceId"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(code = "MISSING_TRACE_ID", message = "Trace ID is required")
                )
                return@get
            }

            when (val result = traceService.getTraceDetails(listOf(traceId))) {
                is arrow.core.Either.Left -> {
                    val error = result.value
                    call.respond(
                        when (error) {
                            is DomainError.StorageError -> HttpStatusCode.InternalServerError
                            is DomainError.ValidationError -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        },
                        ApiError(code = error::class.simpleName ?: "UNKNOWN_ERROR", message = error.message ?: "Unknown error")
                    )
                }
                is arrow.core.Either.Right -> {
                    val traces = result.value
                    if (traces.isEmpty()) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiError(code = "TRACE_NOT_FOUND", message = "Trace not found: $traceId")
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, traces.first().toDto())
                    }
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to get trace details", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "TRACE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }

    /**
     * GET /api/traces/service-map
     * 서비스 맵 조회
     *
     * Query Parameters:
     * - startTime: ISO8601 형식 시작 시간 (선택)
     * - endTime: ISO8601 형식 종료 시간 (선택)
     * - serviceName: 서비스 이름 필터 (선택)
     */
    get("/traces/service-map") {
        try {
            val startTimeParam = call.request.queryParameters["startTime"]
            val endTimeParam = call.request.queryParameters["endTime"]
            val serviceName = call.request.queryParameters["serviceName"]

            val startTime = startTimeParam?.let {
                try {
                    Instant.parse(it)
                } catch (e: DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_START_TIME", message = "Invalid startTime format. Use ISO8601.")
                    )
                    return@get
                }
            }

            val endTime = endTimeParam?.let {
                try {
                    Instant.parse(it)
                } catch (e: DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "INVALID_END_TIME", message = "Invalid endTime format. Use ISO8601.")
                    )
                    return@get
                }
            }

            when (val result = traceService.getServiceMap(
                startTime = startTime,
                endTime = endTime,
                serviceName = serviceName
            )) {
                is arrow.core.Either.Left -> {
                    val error = result.value
                    call.respond(
                        when (error) {
                            is DomainError.StorageError -> HttpStatusCode.InternalServerError
                            is DomainError.ValidationError -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        },
                        ApiError(code = error::class.simpleName ?: "UNKNOWN_ERROR", message = error.message ?: "Unknown error")
                    )
                }
                is arrow.core.Either.Right -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to get service map", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "SERVICE_MAP_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
}

// ==================== DTOs ====================

@Serializable
data class TraceListResultDto(
    val traces: List<TraceSummaryDto>,
    val nextToken: String? = null,
    val approximateCount: Long = 0
)

@Serializable
data class TraceSummaryDto(
    val traceId: String,
    val duration: Double,
    val startTime: String,
    val hasError: Boolean,
    val hasFault: Boolean,
    val hasThrottle: Boolean,
    val http: HttpInfoDto? = null,
    val annotations: Map<String, String> = emptyMap(),
    val serviceIds: List<String> = emptyList()
)

@Serializable
data class TraceDetailDto(
    val traceId: String,
    val duration: Double,
    val startTime: String,
    val endTime: String? = null,
    val segments: List<SpanDetailDto>
)

@Serializable
data class SpanDetailDto(
    val spanId: String,
    val parentId: String? = null,
    val name: String,
    val startTime: Double,
    val endTime: Double,
    val duration: Double,
    val service: String,
    val type: String,
    val http: HttpInfoDto? = null,
    val annotations: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

@Serializable
data class HttpInfoDto(
    val method: String? = null,
    val url: String? = null,
    val status: Int? = null
)

@Serializable
data class ServiceMapResultDto(
    val startTime: String,
    val endTime: String,
    val services: List<ServiceNodeDto>
)

@Serializable
data class ServiceNodeDto(
    val name: String,
    val referenceId: Int,
    val names: List<String> = emptyList(),
    val edges: List<ServiceEdgeDto> = emptyList()
)

@Serializable
data class ServiceEdgeDto(
    val referenceId: Int,
    val startTime: String,
    val endTime: String,
    val summaryStatistics: EdgeStatisticsDto? = null
)

@Serializable
data class EdgeStatisticsDto(
    val okCount: Long,
    val errorCount: Long,
    val faultCount: Long,
    val throttleCount: Long,
    val totalCount: Long,
    val totalResponseTime: Double
)

// ==================== Mappers ====================

private fun com.oliveyoung.ivmlite.apps.admin.application.TraceListResult.toDto() = TraceListResultDto(
    traces = traces.map { it.toDto() },
    nextToken = nextToken,
    approximateCount = approximateCount
)

private fun com.oliveyoung.ivmlite.apps.admin.application.TraceSummary.toDto() = TraceSummaryDto(
    traceId = traceId,
    duration = duration,
    startTime = startTime.toString(),
    hasError = hasError,
    hasFault = hasFault,
    hasThrottle = hasThrottle,
    http = http?.toDto(),
    annotations = annotations,
    serviceIds = serviceIds
)

private fun com.oliveyoung.ivmlite.apps.admin.application.TraceDetail.toDto() = TraceDetailDto(
    traceId = traceId,
    duration = duration,
    startTime = startTime.toString(),
    endTime = endTime?.toString(),
    segments = segments.map { it.toDto() }
)

private fun com.oliveyoung.ivmlite.apps.admin.application.SpanDetail.toDto() = SpanDetailDto(
    spanId = spanId,
    parentId = parentId,
    name = name,
    startTime = startTime,
    endTime = endTime,
    duration = duration,
    service = service,
    type = type,
    http = http?.toDto(),
    annotations = annotations,
    metadata = metadata,
    hasError = hasError,
    errorMessage = errorMessage
)

private fun com.oliveyoung.ivmlite.apps.admin.application.HttpInfo.toDto() = HttpInfoDto(
    method = method,
    url = url,
    status = status
)

private fun com.oliveyoung.ivmlite.apps.admin.application.ServiceMapResult.toDto() = ServiceMapResultDto(
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    services = services.map { it.toDto() }
)

private fun com.oliveyoung.ivmlite.apps.admin.application.ServiceNode.toDto() = ServiceNodeDto(
    name = name,
    referenceId = referenceId,
    names = names,
    edges = edges.map { it.toDto() }
)

private fun com.oliveyoung.ivmlite.apps.admin.application.ServiceEdge.toDto() = ServiceEdgeDto(
    referenceId = referenceId,
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    summaryStatistics = summaryStatistics?.toDto()
)

private fun com.oliveyoung.ivmlite.apps.admin.application.EdgeStatistics.toDto() = EdgeStatisticsDto(
    okCount = okCount,
    errorCount = errorCount,
    faultCount = faultCount,
    throttleCount = throttleCount,
    totalCount = totalCount,
    totalResponseTime = totalResponseTime
)
