package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Outbox Routes
 *
 * GET /api/v1/outbox: Outbox 엔트리 조회
 */
fun Route.outboxRoutes() {
    val outboxRepo by inject<OutboxRepositoryPort>()

    route("/api/v1") {

        /**
         * GET /api/v1/outbox/pending
         * PENDING 상태의 Outbox 엔트리 조회
         *
         * Query Parameters:
         * - limit: 최대 반환 개수 (기본값: 10)
         * - type: AggregateType 필터 (선택, 예: SLICE, RAW_DATA)
         */
        get("/outbox/pending") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val typeParam = call.request.queryParameters["type"]

            val result = if (typeParam != null) {
                val aggregateType = try {
                    AggregateType.valueOf(typeParam.uppercase())
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = "INVALID_AGGREGATE_TYPE",
                            message = "Invalid aggregate type: $typeParam"
                        )
                    )
                    return@get
                }
                outboxRepo.findPendingByType(aggregateType, limit)
            } else {
                outboxRepo.findPending(limit)
            }

            when (result) {
                is OutboxRepositoryPort.Result.Ok -> {
                    call.respond(
                        HttpStatusCode.OK,
                        OutboxListResponse(
                            success = true,
                            entries = result.value.map { it.toDto() },
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
        }

        /**
         * GET /api/v1/outbox/{id}
         * ID로 Outbox 엔트리 조회
         */
        get("/outbox/{id}") {
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

            when (val result = outboxRepo.findById(id)) {
                is OutboxRepositoryPort.Result.Ok -> {
                    call.respond(
                        HttpStatusCode.OK,
                        OutboxResponse(
                            success = true,
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
        }
    }
}

/**
 * OutboxEntry DTO 변환
 */
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
        retryCount = retryCount,
        failureReason = failureReason
    )
}

/**
 * OutboxEntry DTO
 */
@kotlinx.serialization.Serializable
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
    val retryCount: Int,
    val failureReason: String?
)

/**
 * OutboxListResponse
 */
@kotlinx.serialization.Serializable
data class OutboxListResponse(
    val success: Boolean,
    val entries: List<OutboxEntryDto>,
    val count: Int
)

/**
 * OutboxResponse
 */
@kotlinx.serialization.Serializable
data class OutboxResponse(
    val success: Boolean,
    val entry: OutboxEntryDto
)
