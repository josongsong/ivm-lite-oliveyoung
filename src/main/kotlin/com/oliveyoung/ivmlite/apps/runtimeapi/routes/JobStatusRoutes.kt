package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Job Status Routes (RFC-019)
 *
 * GET /api/v1/jobs/:jobId/status: jobId로 SinkEvent 추적 (DynamoDB Streams 기반)
 */
fun Route.jobStatusRoutes() {
    val sinkEventRepo by inject<SinkEventRepositoryPort>()

    route("/api/v1/jobs") {
        get("/{jobId}/status") {
            val jobId = call.parameters["jobId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        code = "INVALID_REQUEST",
                        message = "jobId parameter is required",
                    ),
                )

            when (val result = sinkEventRepo.findByJobId(jobId)) {
                is Result.Ok -> {
                    val events = result.value
                    val response = JobStatusResponse(
                        jobId = jobId,
                        eventCount = events.size,
                        events = events.map { event ->
                            EventStatus(
                                eventType = event.viewType,
                                aggregateType = "SINK_EVENT",
                                aggregateId = event.entityKey,
                                status = event.status.name,
                                createdAt = event.createdAt.toString(),
                                processedAt = event.processedAt?.toString(),
                                retryCount = 0,
                                failureReason = null,
                            )
                        },
                    )
                    call.respond(HttpStatusCode.OK, response)
                }
                is Result.Err -> {
                    call.respond(
                        result.error.toKtorStatus(),
                        ApiError.from(result.error),
                    )
                }
            }
        }
    }
}

@Serializable
data class JobStatusResponse(
    val jobId: String,
    val eventCount: Int,
    val events: List<EventStatus>,
)

@Serializable
data class EventStatus(
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val status: String,
    val createdAt: String,
    val processedAt: String? = null,
    val retryCount: Int,
    val failureReason: String? = null,
)
