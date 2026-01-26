package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.QueryRequest
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.SliceRequest
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.SliceResponse
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Query Routes (RFC-IMPL-004, RFC-IMPL-005, RFC-IMPL-010 GAP-D)
 *
 * POST /api/v1/slice: Slicing 실행
 * POST /api/v1/query: View 조회 (v1 - sliceTypes 직접 전달, deprecated)
 * POST /api/v2/query: View 조회 (v2 - ViewDefinition 기반)
 */
fun Route.queryRoutes() {
    val slicingWorkflow by inject<SlicingWorkflow>()
    val queryViewWorkflow by inject<QueryViewWorkflow>()

    route("/api/v1") {

        // Slicing (RFC-IMPL-004)
        post("/slice") {
            val request = call.receive<SliceRequest>()

            val tenantId = TenantId(request.tenantId)
            val entityKey = EntityKey(request.entityKey)

            val result = slicingWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = request.version,
            )

            when (result) {
                is SlicingWorkflow.Result.Ok -> {
                    val sliceTypes = result.value.map { it.sliceType.name }
                    call.respond(
                        HttpStatusCode.OK,
                        SliceResponse(
                            success = true,
                            sliceTypes = sliceTypes,
                            count = sliceTypes.size,
                        ),
                    )
                }
                is SlicingWorkflow.Result.Err -> {
                    call.respond(
                        result.error.toKtorStatus(),
                        ApiError.from(result.error),
                    )
                }
            }
        }

        // Query View v1 (RFC-IMPL-005) - deprecated, sliceTypes 직접 전달
        @Suppress("DEPRECATION")
        post("/query") {
            val request = call.receive<QueryRequest>()

            val tenantId = TenantId(request.tenantId)
            val entityKey = EntityKey(request.entityKey)

            // sliceTypes가 비어있으면 v2 API로 처리 (ViewDefinition 기반)
            if (request.sliceTypes.isEmpty()) {
                val result = queryViewWorkflow.execute(
                    tenantId = tenantId,
                    viewId = request.viewId,
                    entityKey = entityKey,
                    version = request.version,
                )
                when (result) {
                    is QueryViewWorkflow.Result.Ok -> {
                        call.respondText(
                            serializeViewResponse(result.value),
                            ContentType.Application.Json,
                            HttpStatusCode.OK,
                        )
                    }
                    is QueryViewWorkflow.Result.Err -> {
                        call.respond(result.error.toKtorStatus(), ApiError.from(result.error))
                    }
                }
                return@post
            }

            // v1: String → SliceType 변환 (실패 시 ValidationError)
            val sliceTypes = try {
                request.sliceTypes.map { SliceType.fromDbValue(it) }
            } catch (e: DomainError.ValidationError) {
                call.respond(e.toKtorStatus(), ApiError.from(e))
                return@post
            }

            val result = queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = request.viewId,
                entityKey = entityKey,
                version = request.version,
                requiredSliceTypes = sliceTypes,
            )

            when (result) {
                is QueryViewWorkflow.Result.Ok -> {
                    call.respondText(
                        serializeViewResponse(result.value),
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }
                is QueryViewWorkflow.Result.Err -> {
                    call.respond(result.error.toKtorStatus(), ApiError.from(result.error))
                }
            }
        }
    }

    // v2 API (RFC-IMPL-010 GAP-D: ViewDefinition 기반)
    route("/api/v2") {
        post("/query") {
            val request = call.receive<QueryRequestV2>()

            val tenantId = TenantId(request.tenantId)
            val entityKey = EntityKey(request.entityKey)

            val result = queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = request.viewId,
                entityKey = entityKey,
                version = request.version,
            )

            when (result) {
                is QueryViewWorkflow.Result.Ok -> {
                    call.respondText(
                        serializeViewResponse(result.value),
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }
                is QueryViewWorkflow.Result.Err -> {
                    call.respond(result.error.toKtorStatus(), ApiError.from(result.error))
                }
            }
        }
    }
}

/**
 * ViewResponse를 JSON으로 직렬화
 */
private fun serializeViewResponse(response: QueryViewWorkflow.ViewResponse): String {
    val jsonBuilder = StringBuilder()
    jsonBuilder.append("{\"data\":")
    jsonBuilder.append(response.data)
    if (response.meta != null) {
        jsonBuilder.append(",\"meta\":{")
        var first = true
        response.meta.missingSlices?.let { slices ->
            if (!first) jsonBuilder.append(",")
            jsonBuilder.append("\"missingSlices\":[")
            jsonBuilder.append(slices.joinToString(",") { "\"$it\"" })
            jsonBuilder.append("]")
            first = false
        }
        response.meta.usedContracts?.let { contracts ->
            if (!first) jsonBuilder.append(",")
            jsonBuilder.append("\"usedContracts\":[")
            jsonBuilder.append(contracts.joinToString(",") { "\"$it\"" })
            jsonBuilder.append("]")
        }
        jsonBuilder.append("}")
    }
    jsonBuilder.append("}")
    return jsonBuilder.toString()
}

/**
 * v2 Query Request (ViewDefinition 기반 - sliceTypes 없음)
 */
@kotlinx.serialization.Serializable
data class QueryRequestV2(
    val tenantId: String,
    val viewId: String,
    val entityKey: String,
    val version: Long,
)
