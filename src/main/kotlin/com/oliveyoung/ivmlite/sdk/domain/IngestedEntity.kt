package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployState
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

    fun compileAsync(): DeployJob {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute compileAsync() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        return runBlocking { executor.compileAsync(input, ingestResult.version) }
    }

    fun compileAndShip(): ShippedEntity<T> = compile().ship()

    fun compileAndShipAsync(): DeployJob = compileAsync()
}
