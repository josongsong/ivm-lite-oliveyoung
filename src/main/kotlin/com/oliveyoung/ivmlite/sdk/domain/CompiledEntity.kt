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
        // Ship은 항상 Outbox를 통해 비동기로 처리
        val job = runBlocking { executor.shipAsync(input, compileResult.version) }
        // 호환성을 위해 ShipResult로 변환 (실제로는 Outbox에 저장됨)
        val result = ShipResult(
            entityKey = job.entityKey,
            version = job.version.toLong(),
            sinks = config.defaultSinks,
            success = true,
            error = null
        )
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
        
        // Ship은 항상 Outbox를 통해 비동기로 처리
        // sync 옵션도 내부적으로는 Outbox를 통해 처리됨
        val allSinks = builder.syncSinks + builder.asyncSinks
        val asyncJob = if (allSinks.isNotEmpty()) {
            runBlocking { executor.shipAsyncTo(input, compileResult.version, allSinks) }
        } else {
            runBlocking { executor.shipAsync(input, compileResult.version) }
        }
        
        // 호환성을 위해 syncResult는 null로 설정 (실제로는 모두 Outbox를 통해 처리됨)
        return ShipMixedResult(compileResult.entityKey, compileResult.version, null, asyncJob)
    }
}

class ShipModeBuilder {
    internal val syncSinks = mutableListOf<SinkSpec>()
    internal val asyncSinks = mutableListOf<SinkSpec>()

    /**
     * @deprecated Ship은 항상 Outbox를 통해 비동기로 처리됩니다.
     *             async()를 사용하세요. sync()를 호출해도 내부적으로는 Outbox를 통해 처리됩니다.
     */
    @Deprecated("Ship은 항상 Outbox를 통해 비동기로 처리됩니다. async()를 사용하세요.", ReplaceWith("async(block)"))
    fun sync(block: SinkBuilder.() -> Unit) {
        // 내부적으로는 항상 Outbox를 통해 처리 (일관성 유지)
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
