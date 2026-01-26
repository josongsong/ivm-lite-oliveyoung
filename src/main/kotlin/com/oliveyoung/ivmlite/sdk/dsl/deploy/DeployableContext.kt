package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.dsl.sink.SinkBuilder
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployResult
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec
import kotlinx.coroutines.runBlocking

/**
 * Deployable Context (Wave 4-J 통합 완료 + Wave 5-L Executor 연동)
 * RFC-IMPL-011 Wave 4-J, Wave 5-L
 *
 * 모든 DSL 조합: deploy(), deployAsync(), deployNow() 등
 */
@IvmDslMarker
class DeployableContext internal constructor(
    private val input: EntityInput,
    private val config: IvmClientConfig,
    private val executor: DeployExecutor? = null
) {
    // === Full DSL ===

    /**
     * Full Deploy DSL
     * compile.sync/async + ship.sync/async + cutover.ready/hold 조합 가능
     */
    fun deploy(block: DeployBuilder.() -> Unit): DeployResult {
        val spec = DeployBuilder().apply(block).build()
        return execute(spec)
    }

    /**
     * Async Deploy DSL (타입 안전)
     * compile.async 고정 + ship.async만 허용
     */
    fun deployAsync(block: DeployAsyncBuilder.() -> Unit): DeployJob {
        val spec = DeployAsyncBuilder().apply(block).build()
        return executeAsync(spec)
    }

    // === Shortcut APIs (RFC-008 Section 11) ===

    /**
     * Shortcut: compile.sync + ship.async + cutover.ready
     * 가장 일반적인 배포 패턴
     */
    fun deployNow(block: SinkBuilder.() -> Unit): DeployResult {
        val sinks = SinkBuilder().apply(block).build()
        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(ShipMode.Async, sinks),
            cutoverMode = CutoverMode.Ready
        )
        return execute(spec)
    }

    /**
     * Shortcut: compile.sync + ship.sync + cutover.ready
     * 즉시 배포 + 즉시 전송 (동기)
     */
    fun deployNowAndShipNow(block: SinkBuilder.() -> Unit): DeployResult {
        val sinks = SinkBuilder().apply(block).build()
        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(ShipMode.Sync, sinks),
            cutoverMode = CutoverMode.Ready
        )
        return execute(spec)
    }

    /**
     * Shortcut: compile.async + ship.async + cutover.ready
     * 비동기 배포 + 비동기 전송
     */
    fun deployQueued(block: SinkBuilder.() -> Unit): DeployJob {
        val sinks = SinkBuilder().apply(block).build()
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(ShipMode.Async, sinks),
            cutoverMode = CutoverMode.Ready
        )
        return executeAsync(spec)
    }

    // === Internal Execution ===

    private fun execute(spec: DeploySpec): DeployResult {
        validateSpec(spec)

        // Wave 5-L: DeployExecutor 연동
        return if (executor != null) {
            runBlocking {
                executor.executeSync(input, spec)
            }
        } else {
            // Fallback: Stub implementation (for tests without DI)
            val entityKey = buildEntityKey()
            val version = generateVersion()
            DeployResult.success(entityKey, version)
        }
    }

    private fun executeAsync(spec: DeploySpec): DeployJob {
        validateSpec(spec)

        // Wave 5-L: DeployExecutor 연동
        return if (executor != null) {
            runBlocking {
                executor.executeAsync(input, spec)
            }
        } else {
            // Fallback: Stub implementation (for tests without DI)
            val entityKey = buildEntityKey()
            val version = generateVersion()
            val jobId = generateJobId()
            DeployJob(jobId, entityKey, version, DeployState.QUEUED)
        }
    }

    private fun validateSpec(spec: DeploySpec) {
        // Shortcut APIs always provide shipSpec, but full DSL may omit it
        // Wave 5 will have proper validation logic
        require(spec.shipSpec == null || spec.shipSpec.sinks.isNotEmpty()) {
            "ShipSpec provided but sinks list is empty. Provide at least one sink or omit ship configuration."
        }
    }

    private fun buildEntityKey(): String {
        return when (input) {
            is ProductInput -> "${input.entityType}:${input.sku}"
            is BrandInput -> "${input.entityType}:${input.brandId}"
            is CategoryInput -> "${input.entityType}:${input.categoryId}"
            is GenericEntityInput -> {
                // 코드젠 엔티티는 data에서 키 필드를 찾음
                val keyField = when (input.entityType) {
                    "PRODUCT" -> "sku"
                    "BRAND" -> "brandId"
                    "CATEGORY" -> "categoryId"
                    else -> "id"
                }
                val keyValue = input.data[keyField]?.toString() ?: "unknown"
                "${input.entityType}:$keyValue"
            }
        }
    }

    private fun generateVersion(): String {
        return "v1-${System.currentTimeMillis()}"
    }

    private fun generateJobId(): String {
        return "job-${java.util.UUID.randomUUID()}"
    }
}
