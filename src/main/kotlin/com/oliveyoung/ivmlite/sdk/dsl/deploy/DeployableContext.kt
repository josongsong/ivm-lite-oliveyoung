package com.oliveyoung.ivmlite.sdk.dsl.deploy

import arrow.core.Either
import arrow.core.getOrElse
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
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
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
     * Deploy (RFC-IMPL-013: SinkRule 기반 자동 Ship)
     *
     * Slice 생성 후 SinkRule에 따라 자동으로 Ship이 트리거됩니다.
     * 명시적으로 ship.to { } 설정하면 SinkRule 대신 해당 sink로만 전송됩니다.
     *
     * 사용 예시:
     * ```kotlin
     * // 기본: SinkRule 기반 자동 Ship
     * ivm.product(product).deploy()
     *
     * // 블록으로 compile 모드 설정
     * ivm.product(product).deploy {
     *     compile.async()  // 비동기 컴파일
     * }
     *
     * // 특정 sink로 override
     * ivm.product(product).deploy {
     *     ship.to { personalize() }  // SinkRule 대신 personalize로만
     * }
     * ```
     */
    fun deploy(block: DeployBuilder.() -> Unit = {}): DeployResult {
        val spec = DeployBuilder().apply(block).build()
        return execute(spec)
    }

    /**
     * Async Deploy DSL (타입 안전)
     * compile.async 고정 + ship은 SinkRule 기반 자동
     */
    fun deployAsync(block: DeployAsyncBuilder.() -> Unit): Either<DomainError, DeployJob> {
        val spec = DeployAsyncBuilder().apply(block).build()
        return executeAsync(spec)
    }

    /**
     * Compile Only DSL - Ship을 완전히 비활성화
     *
     * SinkRule이 있어도 Ship을 트리거하지 않습니다.
     *
     * 사용 사례:
     * - 테스트/디버깅
     * - 데이터 마이그레이션 (Ship 없이 Slice만 생성)
     * - 배치 처리 후 수동 Ship
     *
     * ⚠️ 주의: Ship이 완전히 비활성화됩니다.
     */
    fun compileOnly(block: CompileOnlyBuilder.() -> Unit = {}): DeployResult {
        val spec = CompileOnlyBuilder().apply(block).build()
        // CompileOnly 플래그를 DeployExecutor에 전달하여 자동 Ship 비활성화
        return execute(spec)
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
     * Shortcut: compile.sync + ship.async + cutover.ready
     * 즉시 배포 + Outbox를 통한 비동기 전송
     *
     * @deprecated Ship은 항상 Outbox를 통해 비동기로 처리됩니다.
     *             deployNow()를 사용하세요 (동일한 동작).
     */
    @Deprecated("Ship은 항상 Outbox를 통해 비동기로 처리됩니다. deployNow()를 사용하세요.", ReplaceWith("deployNow(block)"))
    fun deployNowAndShipNow(block: SinkBuilder.() -> Unit): DeployResult {
        // Ship은 항상 Outbox를 통해 비동기로 처리
        return deployNow(block)
    }

    /**
     * Shortcut: compile.async + ship.async + cutover.ready
     * 비동기 배포 + 비동기 전송
     */
    fun deployQueued(block: SinkBuilder.() -> Unit): Either<DomainError, DeployJob> {
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

    private fun executeAsync(spec: DeploySpec): Either<DomainError, DeployJob> {
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
            Either.Right(DeployJob(jobId, entityKey, version, DeployState.QUEUED))
        }
    }

    private fun validateSpec(spec: DeploySpec) {
        when (spec) {
            is DeploySpec.Full -> {
                require(spec.ship.sinks.isNotEmpty()) {
                    "ShipSpec provided but sinks list is empty. Provide at least one sink."
                }
            }
            is DeploySpec.CompileOnly -> {
                // CompileOnly는 ship이 없으므로 추가 검증 불필요
            }
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
        return VersionGenerator.generate().toString()
    }

    private fun generateJobId(): String {
        return "job-${java.util.UUID.randomUUID()}"
    }
}
