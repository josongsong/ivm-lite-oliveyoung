package com.oliveyoung.ivmlite.apps.lambda

import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Ingest API 코어 처리 로직 (Lambda/Koin 의존 분리)
 *
 * IngestLambdaHandler에서 Koin DI, API Gateway 이벤트 파싱을 제거한 순수 비즈니스 로직.
 * 테스트 가능성 + 재사용성 확보.
 */
class IngestProcessor(
    private val orchestrator: IngestionOrchestrator,
    private val contractResolver: EntityContractResolver,
) {
    /**
     * Ingest 요청 처리
     *
     * @param request IngestRequest (tenantId, entityKey, payload, jobId)
     * @return IngestProcessResult (성공/에러 통합 결과)
     */
    suspend fun process(request: IngestRequest): IngestProcessResult {
        // entityKey 형식 검증
        val entityType = request.entityKey.substringBefore(":")
        if (entityType.isBlank() || !request.entityKey.contains(":")) {
            return IngestProcessResult.Error(
                statusCode = 400,
                error = "INVALID_ENTITY_KEY",
                message = "entityKey must be 'type:id' format"
            )
        }

        // Contract 해석
        val ruleSetRef = when (val r = contractResolver.resolveRuleSetRef(entityType)) {
            is arrow.core.Either.Right -> r.value
            is arrow.core.Either.Left -> return IngestProcessResult.Error(
                statusCode = 400, error = "CONTRACT_ERROR", message = r.value.toString()
            )
        }
        val viewDefId = when (val r = contractResolver.resolveViewDefId(entityType)) {
            is arrow.core.Either.Right -> r.value
            is arrow.core.Either.Left -> return IngestProcessResult.Error(
                statusCode = 400, error = "CONTRACT_ERROR", message = r.value.toString()
            )
        }
        val viewDefVersion = when (val r = contractResolver.resolveViewDefVersion(entityType)) {
            is arrow.core.Either.Right -> r.value
            is arrow.core.Either.Left -> return IngestProcessResult.Error(
                statusCode = 400, error = "CONTRACT_ERROR", message = r.value.toString()
            )
        }

        // IngestionCommand 구성
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

        // Orchestrator 실행
        return when (val result = orchestrator.ingest(command)) {
            is Result.Ok -> {
                val r = result.value
                IngestProcessResult.Success(
                    jobId = request.jobId,
                    tenantId = r.tenantId,
                    entityKey = r.entityKey,
                    version = r.version,
                    sliceCount = r.sliceCount,
                    viewCount = r.viewCount,
                    sinkPending = r.sinkPending,
                    durationMs = r.durationMs,
                )
            }
            is Result.Err -> IngestProcessResult.Error(
                statusCode = when (result.error) {
                    is DomainError.ValidationError -> 400
                    is DomainError.ContractError -> 400
                    else -> 500
                },
                error = result.error.javaClass.simpleName,
                message = result.error.toString()
            )
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

sealed class IngestProcessResult {
    data class Success(
        val jobId: String?,
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val sliceCount: Int,
        val viewCount: Int,
        val sinkPending: Boolean,
        val durationMs: Long,
    ) : IngestProcessResult()

    data class Error(
        val statusCode: Int,
        val error: String,
        val message: String,
    ) : IngestProcessResult()
}
