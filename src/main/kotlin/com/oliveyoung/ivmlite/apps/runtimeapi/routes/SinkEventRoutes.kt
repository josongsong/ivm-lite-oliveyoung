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
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * SinkEvent Routes (DynamoDB Streams 기반)
 *
 * GET /api/v1/sink-events/pending: PENDING 상태 SinkEvent 조회
 * GET /api/v1/sink-events/{id}: ID로 SinkEvent 조회
 */
fun Route.sinkEventRoutes() {
    val sinkEventRepo by inject<SinkEventRepositoryPort>()

    route("/api/v1") {
        get("/sink-events/pending") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

            when (val result = sinkEventRepo.findByStatus("PENDING", limit)) {
                is Result.Ok -> {
                    val events = result.value
                    call.respond(
                        HttpStatusCode.OK,
                        SinkEventListResponse(
                            success = true,
                            entries = events.map { it.toDto() },
                            count = events.size
                        )
                    )
                }
                is Result.Err -> {
                    call.respond(
                        result.error.toKtorStatus(),
                        ApiError.from(result.error)
                    )
                }
            }
        }

        get("/sink-events/{id}") {
            val idParam = call.parameters["id"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        code = "MISSING_PARAMETER",
                        message = "Missing id parameter"
                    )
                )
                return@get
            }

            val id = try {
                UUID.fromString(idParam)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        code = "INVALID_UUID",
                        message = "Invalid UUID format: $idParam"
                    )
                )
                return@get
            }

            when (val result = sinkEventRepo.findById(id)) {
                is Result.Ok -> {
                    val event = result.value
                    if (event == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("NOT_FOUND", "SinkEvent not found: $id"))
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            SinkEventResponse(
                                success = true,
                                entry = event.toDto()
                            )
                        )
                    }
                }
                is Result.Err -> {
                    call.respond(
                        result.error.toKtorStatus(),
                        ApiError.from(result.error)
                    )
                }
            }
        }
    }
}

private fun com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent.toDto(): SinkEventDto {
    return SinkEventDto(
        id = id.toString(),
        idempotencyKey = idempotencyKey,
        entityKey = entityKey,
        viewType = viewType,
        status = status.name,
        createdAt = createdAt.toString(),
        processedAt = processedAt?.toString(),
        sinkTargets = sinkTargets,
    )
}

@kotlinx.serialization.Serializable
data class SinkEventDto(
    val id: String,
    val idempotencyKey: String,
    val entityKey: String,
    val viewType: String,
    val status: String,
    val createdAt: String,
    val processedAt: String?,
    val sinkTargets: List<String>,
)

@kotlinx.serialization.Serializable
data class SinkEventListResponse(
    val success: Boolean,
    val entries: List<SinkEventDto>,
    val count: Int
)

@kotlinx.serialization.Serializable
data class SinkEventResponse(
    val success: Boolean,
    val entry: SinkEventDto
)
