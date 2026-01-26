package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.*
import kotlinx.coroutines.runBlocking

/**
 * Entity Actions - 도메인별 작업 수행
 */
abstract class EntityActions<T : EntityInput>(
    protected val input: T,
    protected val config: IvmClientConfig,
    protected val executor: DeployExecutor?
) {
    fun deploy(): DeployResult {
        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = null,
            cutoverMode = CutoverMode.Ready
        )
        return executeSync(spec)
    }

    fun deployAsync(): DeployJob {
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = null,
            cutoverMode = CutoverMode.Ready
        )
        return executeAsync(spec)
    }

    fun ingest(): IngestedEntity<T> {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute ingest() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        val result = runBlocking { executor.ingestOnly(input) }
        return IngestedEntity(input, result, config, executor)
    }

    fun ingestAndCompile(): CompiledEntity<T> {
        return ingest().compile()
    }

    fun explain(): DeployPlan {
        return DeployPlan(
            entityKey = buildEntityKey(),
            entityType = input.entityType,
            slices = listOf("search-doc", "reco-feed"),
            sinks = listOf("opensearch", "personalize"),
            rules = listOf("${input.entityType}-to-search-doc", "${input.entityType}-to-reco-feed")
        )
    }

    protected abstract fun buildEntityKey(): String

    private fun executeSync(spec: DeploySpec): DeployResult {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute deploy() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        return runBlocking { executor.executeSync(input, spec) }
    }

    private fun executeAsync(spec: DeploySpec): DeployJob {
        val executor = this.executor ?: throw IllegalStateException(
            "DeployExecutor is not configured. Cannot execute deployAsync() operation. " +
            "Configure executor via Ivm.client().configure { executor = ... }"
        )
        return runBlocking { executor.executeAsync(input, spec) }
    }
}

data class IngestResult(
    val entityKey: String,
    val version: Long,
    val success: Boolean,
    val error: String? = null
)

data class CompileResult(
    val entityKey: String,
    val version: Long,
    val slices: List<String>,
    val success: Boolean,
    val error: String? = null
)

data class ShipResult(
    val entityKey: String,
    val version: Long,
    val sinks: List<String>,
    val success: Boolean,
    val error: String? = null
)

data class DeployPlan(
    val entityKey: String,
    val entityType: String,
    val slices: List<String>,
    val sinks: List<String>,
    val rules: List<String>
)
