package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.apps.runtimeapi.dto.ApiError
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestRequest
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.IngestResponse
import com.oliveyoung.ivmlite.apps.runtimeapi.dto.toKtorStatus
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.shared.domain.types.Result
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
 * SDK(DeployExecutor)를 통한 일원화된 처리:
 * - Contract 해석 → IngestionCommand → IngestionOrchestrator
 * - skipSink=true: RawData → Slicing → View만 (SinkEvent/Lambda 미호출)
 * - inProcessSink=true: 같은 세션에서 SinkPlugin 직접 호출 (Lambda/DynamoDB 미사용)
 * - 둘 다 false: DynamoDB Streams → Lambda → S3/OpenSearch/Personalize
 *
 * 응답 시간: 1~2초
 */
fun Route.ingestRoutes() {
    val deployExecutor by inject<DeployExecutor>()

    route("/api/v1") {

        post("/ingest") {
            val request = call.receive<IngestRequest>()

            val result = deployExecutor.executeFromApi(
                tenantId = request.tenantId,
                entityKey = request.entityKey,
                payload = request.payload,
                jobId = request.jobId,
                skipSink = request.skipSink,
                inProcessSink = request.inProcessSink,
            )

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
