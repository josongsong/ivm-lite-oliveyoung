package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.shared.config.KafkaConfig
import com.oliveyoung.ivmlite.shared.config.WorkerConfig

/**
 * IvmContext - SDK 의존성 컨테이너 (SOTA DI Pattern)
 *
 * 7개의 산발적 set*() 메서드를 단일 Context 객체로 통합.
 * - Type-safe: 필수/선택 의존성 명확화
 * - Immutable: 한 번 생성 후 변경 불가 (thread-safe)
 * - Builder Pattern: 유연한 구성
 *
 * @example
 * ```kotlin
 * // DI 컨테이너에서 주입 (Koin 예시)
 * val context = IvmContext.builder()
 *     .executor(deployExecutor)
 *     .queryWorkflow(queryWorkflow)
 *     .worker(outboxPollingWorker)
 *     .outboxRepository(outboxRepo)
 *     .kafkaConfig(kafkaConfig)
 *     .workerConfig(workerConfig)
 *     .build()
 *
 * Ivm.initialize(context)
 * ```
 *
 * @example 최소 구성 (Query만 사용)
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

    // Deploy 관련 (선택)
    val executor: DeployExecutor?,

    // Query 관련 (선택)
    val queryWorkflow: QueryViewWorkflow?,

    // Worker/Consume 관련 (선택)
    val worker: OutboxPollingWorker?,
    val outboxRepository: OutboxRepositoryPort?,
    val kafkaConfig: KafkaConfig,
    val workerConfig: WorkerConfig,

    // Ops 관련 (선택) - Admin 전용
    val slicingWorkflow: SlicingWorkflow?,
    val shipWorkflow: ShipWorkflow?,
    val sliceRepository: SliceRepositoryPort?
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
     * IvmContext Builder
     *
     * 모든 의존성은 선택적이며, 필요한 것만 설정 가능.
     * 사용하지 않는 기능에 접근 시 런타임 에러 발생.
     */
    class Builder {
        private var config: IvmClientConfig = IvmClientConfig()
        private var executor: DeployExecutor? = null
        private var queryWorkflow: QueryViewWorkflow? = null
        private var worker: OutboxPollingWorker? = null
        private var outboxRepository: OutboxRepositoryPort? = null
        private var kafkaConfig: KafkaConfig = KafkaConfig()
        private var workerConfig: WorkerConfig = WorkerConfig()
        private var slicingWorkflow: SlicingWorkflow? = null
        private var shipWorkflow: ShipWorkflow? = null
        private var sliceRepository: SliceRepositoryPort? = null

        // ===== Client Config =====

        /**
         * 클라이언트 설정 (선택)
         * 미설정 시 기본값 사용
         */
        fun config(config: IvmClientConfig) = apply { this.config = config }

        /**
         * 클라이언트 설정 DSL
         */
        fun config(block: IvmClientConfig.Builder.() -> Unit) = apply {
            this.config = IvmClientConfig.Builder().apply(block).build()
        }

        // ===== Deploy =====

        /**
         * DeployExecutor 설정
         * Deploy API 사용 시 필수
         */
        fun executor(executor: DeployExecutor) = apply { this.executor = executor }

        // ===== Query =====

        /**
         * QueryViewWorkflow 설정
         * Query API 사용 시 필수
         */
        fun queryWorkflow(workflow: QueryViewWorkflow) = apply { this.queryWorkflow = workflow }

        // ===== Worker/Consume =====

        /**
         * OutboxPollingWorker 설정
         * Worker API 사용 시 필수
         */
        fun worker(worker: OutboxPollingWorker) = apply { this.worker = worker }

        /**
         * OutboxRepository 설정
         * Consume API 사용 시 필수
         */
        fun outboxRepository(repo: OutboxRepositoryPort) = apply { this.outboxRepository = repo }

        /**
         * Kafka 설정
         */
        fun kafkaConfig(config: KafkaConfig) = apply { this.kafkaConfig = config }

        /**
         * Worker 설정
         */
        fun workerConfig(config: WorkerConfig) = apply { this.workerConfig = config }

        // ===== Ops (Admin 전용) =====

        /**
         * SlicingWorkflow 설정 (Admin Ops용)
         */
        fun slicingWorkflow(workflow: SlicingWorkflow) = apply { this.slicingWorkflow = workflow }

        /**
         * ShipWorkflow 설정 (Admin Ops용)
         */
        fun shipWorkflow(workflow: ShipWorkflow) = apply { this.shipWorkflow = workflow }

        /**
         * SliceRepository 설정 (Admin Ops용)
         */
        fun sliceRepository(repo: SliceRepositoryPort) = apply { this.sliceRepository = repo }

        /**
         * IvmContext 빌드
         */
        fun build(): IvmContext = IvmContext(
            config = config,
            executor = executor,
            queryWorkflow = queryWorkflow,
            worker = worker,
            outboxRepository = outboxRepository,
            kafkaConfig = kafkaConfig,
            workerConfig = workerConfig,
            slicingWorkflow = slicingWorkflow,
            shipWorkflow = shipWorkflow,
            sliceRepository = sliceRepository
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
     * Worker API 사용 가능 여부
     */
    val canControlWorker: Boolean get() = worker != null

    /**
     * Consume API 사용 가능 여부
     */
    val canConsume: Boolean get() = outboxRepository != null

    /**
     * Ops API 사용 가능 여부
     */
    val canOps: Boolean get() = slicingWorkflow != null && shipWorkflow != null && sliceRepository != null

    /**
     * 필수 의존성 검증 (fail-fast)
     */
    fun requireExecutor(): DeployExecutor =
        executor ?: throw IllegalStateException("DeployExecutor not configured. Use IvmContext.builder().executor(...)")

    fun requireQueryWorkflow(): QueryViewWorkflow =
        queryWorkflow ?: throw IllegalStateException("QueryViewWorkflow not configured. Use IvmContext.builder().queryWorkflow(...)")

    fun requireWorker(): OutboxPollingWorker =
        worker ?: throw IllegalStateException("OutboxPollingWorker not configured. Use IvmContext.builder().worker(...)")

    fun requireOutboxRepository(): OutboxRepositoryPort =
        outboxRepository ?: throw IllegalStateException("OutboxRepository not configured. Use IvmContext.builder().outboxRepository(...)")

    fun requireSlicingWorkflow(): SlicingWorkflow =
        slicingWorkflow ?: throw IllegalStateException("SlicingWorkflow not configured. Use IvmContext.builder().slicingWorkflow(...)")

    fun requireShipWorkflow(): ShipWorkflow =
        shipWorkflow ?: throw IllegalStateException("ShipWorkflow not configured. Use IvmContext.builder().shipWorkflow(...)")

    fun requireSliceRepository(): SliceRepositoryPort =
        sliceRepository ?: throw IllegalStateException("SliceRepository not configured. Use IvmContext.builder().sliceRepository(...)")
}
