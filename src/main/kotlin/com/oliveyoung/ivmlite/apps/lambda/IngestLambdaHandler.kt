package com.oliveyoung.ivmlite.apps.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.allModules
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.withSpan
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.GlobalContext.startKoin

/**
 * Lambda Handler for Ingest API
 *
 * API Gateway → Lambda → IngestionOrchestrator
 *
 * 배포:
 * ```bash
 * ./gradlew shadowJar
 * aws lambda update-function-code \
 *   --function-name ivm-ingest-api \
 *   --zip-file fileb://build/libs/ivm-ingest-lambda-1.0.0.jar
 * ```
 *
 * Handler: com.oliveyoung.ivmlite.apps.lambda.IngestLambdaHandler
 * RFC-IMPL-009: OpenTelemetry 트레이싱 적용
 */
class IngestLambdaHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val json = Json { ignoreUnknownKeys = true }

    // Koin 초기화 (Lambda 첫 실행 시 한 번만)
    init {
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                modules(allModules)
            }
        }
    }

    override fun handleRequest(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent {
        val logger = context.logger
        val tracer = get().get<Tracer>()

        return tracer.withSpan(
            name = "IngestLambdaHandler.handleRequest",
            attributes = mapOf(
                "aws.lambda.function" to (context.functionName ?: "unknown"),
            ),
        ) { span ->
            try {
                val request = json.decodeFromString<IngestRequest>(input.body ?: "{}")
                span.setAttribute("ingest.tenant", request.tenantId)
                span.setAttribute("ingest.entity_key", request.entityKey)

                logger.log("Processing ingest: tenant=${request.tenantId}, entity=${request.entityKey}")

                // Contract is Law: entityKey에서 entityType 추출 → 동적 Contract 해석
                val entityType = request.entityKey.substringBefore(":")
                if (entityType.isBlank() || !request.entityKey.contains(":")) {
                    return@withSpan APIGatewayProxyResponseEvent().apply {
                    statusCode = 400
                    body = json.encodeToString(ErrorResponse.serializer(),
                        ErrorResponse("INVALID_ENTITY_KEY", "entityKey must be 'type:id' format"))
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }

                val orchestrator = get().get<IngestionOrchestrator>()
                val contractResolver = get().get<EntityContractResolver>()

                val ruleSetRef = when (val r = contractResolver.resolveRuleSetRef(entityType)) {
                    is arrow.core.Either.Right -> r.value
                    is arrow.core.Either.Left -> return@withSpan APIGatewayProxyResponseEvent().apply {
                    statusCode = 400
                    body = json.encodeToString(ErrorResponse.serializer(),
                        ErrorResponse("CONTRACT_ERROR", r.value.toString()))
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }
                val viewDefId = when (val r = contractResolver.resolveViewDefId(entityType)) {
                    is arrow.core.Either.Right -> r.value
                    is arrow.core.Either.Left -> return@withSpan APIGatewayProxyResponseEvent().apply {
                    statusCode = 400
                    body = json.encodeToString(ErrorResponse.serializer(),
                        ErrorResponse("CONTRACT_ERROR", r.value.toString()))
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }
                val viewDefVersion = when (val r = contractResolver.resolveViewDefVersion(entityType)) {
                    is arrow.core.Either.Right -> r.value
                    is arrow.core.Either.Left -> return@withSpan APIGatewayProxyResponseEvent().apply {
                    statusCode = 400
                    body = json.encodeToString(ErrorResponse.serializer(),
                        ErrorResponse("CONTRACT_ERROR", r.value.toString()))
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }

                val command = IngestionCommand(
                tenantId = TenantId(request.tenantId),
                entityKey = EntityKey(request.entityKey),
                data = request.payload,
                ruleSetRef = ruleSetRef,
                viewDefId = viewDefId,
                viewDefVersion = viewDefVersion,
                version = VersionGenerator.generate(),
                    jobId = request.jobId,
                )

                val result = runBlocking { orchestrator.ingest(command) }

                when (result) {
                is Result.Ok -> {
                    val ingestionResult = result.value
                    span.setAttribute("ingest.version", ingestionResult.version)
                    span.setAttribute("ingest.slice_count", ingestionResult.sliceCount.toLong())
                    span.setAttribute("ingest.view_count", ingestionResult.viewCount.toLong())
                    span.setAttribute("ingest.duration_ms", ingestionResult.durationMs)
                    APIGatewayProxyResponseEvent().apply {
                        statusCode = 200
                        body = json.encodeToString(
                            IngestResponse.serializer(),
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
                            )
                        )
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }
                is Result.Err -> {
                    logger.log("Ingestion error: ${result.error}")
                    APIGatewayProxyResponseEvent().apply {
                        statusCode = when (result.error) {
                            is com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ValidationError -> 400
                            is com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError -> 400
                            else -> 500
                        }
                        body = json.encodeToString(
                            ErrorResponse.serializer(),
                            ErrorResponse(
                                error = result.error.javaClass.simpleName,
                                message = result.error.toString()
                            )
                        )
                        headers = mapOf("Content-Type" to "application/json")
                    }
                }
            }
            } catch (e: Exception) {
                logger.log("Lambda error: ${e.message}")
                e.printStackTrace()
                APIGatewayProxyResponseEvent().apply {
                    statusCode = 500
                    body = json.encodeToString(
                        ErrorResponse.serializer(),
                        ErrorResponse(
                            error = "INTERNAL_ERROR",
                            message = e.message ?: "Unknown error"
                        )
                    )
                    headers = mapOf("Content-Type" to "application/json")
                }
            }
        }
    }

    @Serializable
    data class IngestRequest(
        val tenantId: String,
        val entityKey: String,
        val payload: JsonObject,
        val jobId: String? = null,
    )

    @Serializable
    data class IngestResponse(
        val success: Boolean,
        val jobId: String?,
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val sliceCount: Int,
        val viewCount: Int,
        val sinkPending: Boolean,
        val durationMs: Long,
    )

    @Serializable
    data class ErrorResponse(
        val error: String,
        val message: String,
    )
}
