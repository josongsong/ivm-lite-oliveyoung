package com.oliveyoung.ivmlite.sdk.domain

import arrow.core.Either
import arrow.core.getOrElse
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.coroutines.runBlocking

class IngestedEntity<T : EntityInput>(
    private val input: T,
    private val ingestResult: IngestResult,
    private val config: IvmClientConfig,
    private val executor: DeployExecutor?
) {
    val entityKey: String get() = ingestResult.entityKey
    val version: Long get() = ingestResult.version

    fun compile(): CompiledEntity<T> {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute compile() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        val result = runBlocking { executor.compileOnly(input, ingestResult.version) }
        return CompiledEntity(input, result, config, executor)
    }

    fun compileAsync(): Either<DomainError, DeployJob> {
        val executor = this.executor ?: return Either.Left(
            DomainError.ConfigError("DeployExecutor is not configured. Configure executor via Ivm.client().configure { executor = ... }")
        )
        return runBlocking { executor.compileAsync(input, ingestResult.version) }
    }

    fun compileAndShip(): Either<DomainError, ShippedEntity<T>> = compile().ship()

    fun compileAndShipAsync(): Either<DomainError, DeployJob> = compileAsync()
}
