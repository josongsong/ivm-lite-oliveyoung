package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestRequest
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestResponse
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Ingest Routes (SOTA Hybrid Architecture)
 *
 * POST /api/v1/ingest: 동기 처리 (RawData → Slicing → View Composition)
 *
 * 🔥 DynamoDB Streams 기반:
 * - RawData → Slicing → View (단일 트랜잭션)
 * - SinkEvent 발행 (DynamoDB → Streams → Lambda)
 *
 * 응답 시간: 1~2초
 */
fun Route.ingestRoutes() {
    val orchestrator by inject<IngestionOrchestrator>()
    val contractResolver by inject<EntityContractResolver>()

    route("/api/v1") {

        post("/ingest") {
            val request = call.receive<IngestRequest>()

            // Contract is Law: entityKey에서 entityType 추출 → 동적 Contract 해석
            val entityType = request.entityKey.substringBefore(":")
            if (entityType.isBlank() || !request.entityKey.contains(":")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(code = "INVALID_ENTITY_KEY", message = "entityKey must be 'type:id' format (e.g. 'product:SKU-001')")
                )
                return@post
            }

            val ruleSetRef = when (val r = contractResolver.resolveRuleSetRef(entityType)) {
                is Either.Left -> {
                    call.respond(r.value.toKtorStatus(), ApiError.from(r.value))
                    return@post
                }
                is Either.Right -> r.value
            }
            val viewDefId = when (val r = contractResolver.resolveViewDefId(entityType)) {
                is Either.Left -> {
                    call.respond(r.value.toKtorStatus(), ApiError.from(r.value))
                    return@post
                }
                is Either.Right -> r.value
            }
            val viewDefVersion = when (val r = contractResolver.resolveViewDefVersion(entityType)) {
                is Either.Left -> {
                    call.respond(r.value.toKtorStatus(), ApiError.from(r.value))
                    return@post
                }
                is Either.Right -> r.value
            }

            val command = IngestionCommand(
                tenantId = TenantId(request.tenantId),
                entityKey = EntityKey(request.entityKey),
                data = request.payload,
                ruleSetRef = ruleSetRef,
                viewDefId = viewDefId,
                viewDefVersion = viewDefVersion,
                version = VersionGenerator.generate(),
                jobId = request.jobId
            )

            val result = orchestrator.ingest(command)

            when (result) {
                is Result.Ok<*> -> {
                    val ingestionResult = result.value as com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionResult
                    call.respond(
                        HttpStatusCode.OK,
                        IngestResponse(
                            success = true,
                            jobId = request.jobId,
                            tenantId = ingestionResult.tenantId,
                            entityKey = ingestionResult.entityKey,
                            version = ingestionResult.version,
                            sliceCount = ingestionResult.sliceCount,
                            viewCount = ingestionResult.viewCount,
                            sinkPending = ingestionResult.sinkPending,
                            durationMs = ingestionResult.durationMs,
                        ),
                    )
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
