package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor

/**
 * IvmContext - SDK 의존성 컨테이너 (SOTA - IngestionOrchestrator 기반)
 *
 * 최소 의존성으로 단순화:
 * - DeployExecutor: IngestionOrchestrator 기반 (RawData → Slicing → View → SinkEvent)
 * - QueryViewWorkflow: View 조회 (선택)
 *
 * ⚠️ DynamoDB Streams 전환 완료:
 * - SinkEvent → DynamoDB Streams → Lambda (SinkStreamHandler)
 * - SlicingWorkflow → IngestionOrchestrator 내부 처리
 *
 * @example SOTA 구성
 * ```kotlin
 * val context = IvmContext.builder()
 *     .executor(deployExecutor)  // IngestionOrchestrator 기반
 *     .build()
 *
 * Ivm.initialize(context)
 * ```
 *
 * @example Query만 사용
 * ```kotlin
 * val context = IvmContext.builder()
 *     .queryWorkflow(queryWorkflow)
 *     .build()
 *
 * Ivm.initialize(context)
 * ```
 */
data class IvmContext private constructor(
    // Client 설정
    val config: IvmClientConfig,

    // Deploy 관련 (IngestionOrchestrator 기반)
    val executor: DeployExecutor?,

    // Query 관련 (선택)
    val queryWorkflow: QueryViewWorkflow?
) {
    companion object {
        /**
         * Builder 생성
         */
        fun builder(): Builder = Builder()

        /**
         * 빈 Context (테스트용)
         */
        val EMPTY: IvmContext = Builder().build()
    }

    /**
     * IvmContext Builder (SOTA - 최소 의존성)
     */
    class Builder {
        private var config: IvmClientConfig = IvmClientConfig()
        private var executor: DeployExecutor? = null
        private var queryWorkflow: QueryViewWorkflow? = null

        /**
         * 클라이언트 설정
         */
        fun config(config: IvmClientConfig) = apply { this.config = config }

        /**
         * 클라이언트 설정 DSL
         */
        fun config(block: IvmClientConfig.Builder.() -> Unit) = apply {
            this.config = IvmClientConfig.Builder().apply(block).build()
        }

        /**
         * DeployExecutor 설정 (IngestionOrchestrator 기반)
         */
        fun executor(executor: DeployExecutor) = apply { this.executor = executor }

        /**
         * QueryViewWorkflow 설정
         */
        fun queryWorkflow(workflow: QueryViewWorkflow) = apply { this.queryWorkflow = workflow }

        /**
         * IvmContext 빌드
         */
        fun build(): IvmContext = IvmContext(
            config = config,
            executor = executor,
            queryWorkflow = queryWorkflow
        )
    }

    // ===== Validation Helpers =====

    /**
     * Deploy API 사용 가능 여부
     */
    val canDeploy: Boolean get() = executor != null

    /**
     * Query API 사용 가능 여부
     */
    val canQuery: Boolean get() = queryWorkflow != null

    /**
     * 필수 의존성 검증 (fail-fast)
     */
    fun requireExecutor(): DeployExecutor =
        executor ?: throw IllegalStateException("DeployExecutor not configured. Use IvmContext.builder().executor(...)")

    fun requireQueryWorkflow(): QueryViewWorkflow =
        queryWorkflow ?: throw IllegalStateException("QueryViewWorkflow not configured. Use IvmContext.builder().queryWorkflow(...)")
}
