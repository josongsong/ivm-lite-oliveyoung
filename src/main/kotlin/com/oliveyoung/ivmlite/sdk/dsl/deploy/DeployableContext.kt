package com.oliveyoung.ivmlite.sdk.dsl.deploy

import arrow.core.Either
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployResult
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.coroutines.runBlocking

/**
 * Deployable Context (SOTA: IngestionOrchestrator 올인원)
 *
 * IngestionOrchestrator가 RawData → Slicing → View → SinkEvent를 단일 트랜잭션으로 처리.
 * SinkRule 기반 자동 Ship (DynamoDB Streams → Lambda).
 *
 * 사용 예시:
 * ```kotlin
 * ivm.product(product).deploy()       // 동기 실행
 * ivm.product(product).deployAsync()  // 비동기 실행
 * ```
 */
@IvmDslMarker
class DeployableContext internal constructor(
    private val input: EntityInput,
    @Suppress("UnusedPrivateProperty") private val config: IvmClientConfig,
    private val executor: DeployExecutor? = null
) {
    /**
     * 동기 Deploy (IngestionOrchestrator 기반)
     *
     * RawData → Slicing → View → SinkEvent 단일 트랜잭션 처리.
     * SinkRule에 따라 자동으로 Ship이 트리거됩니다.
     */
    fun deploy(): DeployResult {
        val exec = requireExecutor()
        return runBlocking { exec.executeSync(input) }
    }

    /**
     * 비동기 Deploy (IngestionOrchestrator 기반)
     *
     * 내부적으로 동기와 동일하게 동작 (IngestionOrchestrator가 Sink까지 자동 처리).
     */
    fun deployAsync(): Either<DomainError, DeployJob> {
        val exec = executor ?: return Either.Left(
            DomainError.ConfigError("DeployExecutor not configured. Use Ivm.initialize() first.")
        )
        return runBlocking { exec.executeAsync(input) }
    }

    private fun requireExecutor(): DeployExecutor {
        return executor ?: throw IllegalStateException(
            "DeployExecutor not configured. Use Ivm.initialize() first."
        )
    }
}
