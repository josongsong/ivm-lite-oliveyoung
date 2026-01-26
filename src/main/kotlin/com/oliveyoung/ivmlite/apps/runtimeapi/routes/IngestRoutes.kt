package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestRequest
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestResponse
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Ingest Routes (RFC-IMPL-003)
 *
 * POST /api/v1/ingest: RawData 저장
 */
fun Route.ingestRoutes() {
    val ingestWorkflow by inject<IngestWorkflow>()

    route("/api/v1") {

        post("/ingest") {
            val request = call.receive<IngestRequest>()

            val tenantId = TenantId(request.tenantId)
            val entityKey = EntityKey(request.entityKey)
            val schemaVersion = SemVer.parse(request.schemaVersion)

            val result = ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = request.version,
                schemaId = request.schemaId,
                schemaVersion = schemaVersion,
                payloadJson = request.payload.toString(),
            )

            when (result) {
                is IngestWorkflow.Result.Ok -> {
                    call.respond(
                        HttpStatusCode.OK,
                        IngestResponse(
                            success = true,
                            tenantId = request.tenantId,
                            entityKey = request.entityKey,
                            version = request.version,
                        ),
                    )
                }
                is IngestWorkflow.Result.Err -> {
                    call.respond(
                        result.error.toKtorStatus(),
                        ApiError.from(result.error),
                    )
                }
            }
        }
    }
}
