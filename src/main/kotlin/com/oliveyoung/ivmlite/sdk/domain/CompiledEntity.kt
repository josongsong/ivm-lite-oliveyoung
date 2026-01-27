package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.dsl.sink.SinkBuilder
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.SinkSpec
import kotlinx.coroutines.runBlocking

class CompiledEntity<T : EntityInput>(
    private val input: T,
    private val compileResult: CompileResult,
    private val config: IvmClientConfig,
    private val executor: DeployExecutor?
) {
    val entityKey: String get() = compileResult.entityKey
    val version: Long get() = compileResult.version
    val slices: List<String> get() = compileResult.slices

    fun ship(): ShippedEntity<T> {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute ship() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        val result = runBlocking { executor.shipSync(input, compileResult.version, config.defaultSinks) }
        return ShippedEntity(input, result, config)
    }

    fun shipAsync(): DeployJob {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute shipAsync() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        return runBlocking { executor.shipAsync(input, compileResult.version) }
    }

    fun ship(block: ShipModeBuilder.() -> Unit): ShipMixedResult {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute ship() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        val builder = ShipModeBuilder().apply(block)
        
        val syncResult = if (builder.syncSinks.isNotEmpty()) {
            runBlocking { executor.shipSyncTo(input, compileResult.version, builder.syncSinks) }
        } else null
        
        val asyncJob = if (builder.asyncSinks.isNotEmpty()) {
            runBlocking { executor.shipAsyncTo(input, compileResult.version, builder.asyncSinks) }
        } else null
        
        return ShipMixedResult(compileResult.entityKey, compileResult.version, syncResult, asyncJob)
    }
}

class ShipModeBuilder {
    internal val syncSinks = mutableListOf<SinkSpec>()
    internal val asyncSinks = mutableListOf<SinkSpec>()

    fun sync(block: SinkBuilder.() -> Unit) {
        syncSinks.addAll(SinkBuilder().apply(block).build())
    }

    fun async(block: SinkBuilder.() -> Unit) {
        asyncSinks.addAll(SinkBuilder().apply(block).build())
    }
}

data class ShipMixedResult(
    val entityKey: String,
    val version: Long,
    val syncResult: ShipResult?,
    val asyncJob: DeployJob?
) {
    val syncSinks: List<String> get() = syncResult?.sinks ?: emptyList()
    val success: Boolean get() = syncResult?.success ?: false
}
