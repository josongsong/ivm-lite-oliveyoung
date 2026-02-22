package com.oliveyoung.ivmlite.sdk.domain

import arrow.core.Either
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployResult
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.coroutines.runBlocking

/**
 * Entity Actions - 도메인별 작업 수행
 *
 * SOTA: IngestionOrchestrator 올인원 아키텍처
 * 모든 옵션은 Contract에 정의됨 (Contract is Law)
 */
abstract class EntityActions<T : EntityInput>(
    protected val input: T,
    protected val config: IvmClientConfig,
    protected val executor: DeployExecutor?
) {
    /**
     * Deploy 실행 (DeployResult 반환)
     *
     * 실패 시 DeployResult.success=false, 성공 시 true.
     * @throws IllegalStateException executor 미설정 시
     */
    fun deploy(): DeployResult {
        return executeSync()
    }

    /**
     * Deploy 실행 (Either 반환 - 함수형 에러 핸들링)
     *
     * deploy()와 동일한 파이프라인이지만 Either<DomainError, DeployJob>을 반환.
     * Sink 전송은 DynamoDB Streams → Lambda가 비동기 처리합니다.
     */
    fun deployAsync(): Either<DomainError, DeployJob> {
        return executeAsync()
    }

    fun explain(): DeployPlan {
        val executor = this.executor
            ?: return DeployPlan(
                entityKey = buildEntityKey(),
                entityType = input.entityType,
                slices = emptyList(),
                views = emptyList(),
                rules = emptyList()
            )
        return executor.explain(input.entityType, buildEntityKey())
    }

    protected abstract fun buildEntityKey(): String

    private fun executeSync(): DeployResult {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute deploy() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        return runBlocking { executor.executeSync(input) }
    }

    private fun executeAsync(): Either<DomainError, DeployJob> {
        val executor = this.executor ?: return Either.Left(
            DomainError.ConfigError("DeployExecutor is not configured. Configure executor via Ivm.client().configure { executor = ... }")
        )
        return runBlocking { executor.executeAsync(input) }
    }
}

/**
 * DeployPlan - explain() 결과 (Contract is Law)
 *
 * slices: RuleSet에서 생성되는 Slice 타입 목록 (예: CORE, PRICE, INVENTORY)
 * views: ViewDefinition ID 목록 (예: view.product.core.v1)
 * rules: 적용되는 RuleSet ID 목록 (예: ruleset.core.v1)
 */
data class DeployPlan(
    val entityKey: String,
    val entityType: String,
    val slices: List<String>,
    val views: List<String>,
    val rules: List<String>
)
