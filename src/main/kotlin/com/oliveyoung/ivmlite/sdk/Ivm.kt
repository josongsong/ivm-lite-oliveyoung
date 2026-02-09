package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.sdk.client.ConsumeApi
import com.oliveyoung.ivmlite.sdk.client.DeployStatusApi
import com.oliveyoung.ivmlite.sdk.client.IvmClient
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.client.PlanExplainApi
import com.oliveyoung.ivmlite.sdk.client.QueryApi
import com.oliveyoung.ivmlite.sdk.client.QueryBuilder
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandBuilder
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryBuilder
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductBuilder
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.schema.TypedQueryBuilder
import com.oliveyoung.ivmlite.sdk.schema.ViewRef
import com.oliveyoung.ivmlite.shared.config.KafkaConfig
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.Topic
import kotlinx.coroutines.flow.Flow

/**
 * IVM SDK Entry Point - DX 끝판왕
 * 
 * 모든 도메인에 대해 일관된 DSL 제공:
 * - Ivm.product { ... }.deploy()
 * - Ivm.brand { ... }.deploy()
 * - Ivm.category { ... }.deploy()
 * - Ivm.query("view.id").key("SKU-001").get()
 * 
 * @example
 * ```kotlin
 * // 기본 사용 (가장 많이 쓸 패턴)
 * Ivm.product {
 *     tenantId = "oliveyoung"
 *     sku = "SKU-001"
 *     name = "비타민C"
 *     price = 15000
 * }.deploy()
 * 
 * // 비동기 배포
 * val job = Ivm.product { ... }.deployAsync()
 * 
 * // 단계별 제어
 * Ivm.product { ... }
 *     .ingest()
 *     .compile()
 *     .ship()
 * 
 * // View 조회 (DX 끝판왕)
 * val view = Ivm.query("product.pdp").key("SKU-001").get()
 * ```
 */
object Ivm {
    // ===== 상태 =====
    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var context: IvmContext = IvmContext.EMPTY

    @Volatile
    private var config: IvmClientConfig = IvmClientConfig()

    @Volatile
    private var executor: DeployExecutor? = null

    @Volatile
    private var cachedClient: IvmClient? = null

    @Volatile
    private var queryWorkflow: com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow? = null

    @Volatile
    private var outboxPollingWorker: com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker? = null

    @Volatile
    private var outboxRepository: OutboxRepositoryPort? = null

    @Volatile
    private var kafkaConfig: KafkaConfig = KafkaConfig()

    @Volatile
    private var workerConfig: WorkerConfig = WorkerConfig()

    private val lock = Any()

    // ===== 신규 API: IvmContext 기반 초기화 (권장) =====

    /**
     * SDK 초기화 (권장 방식)
     *
     * IvmContext를 통해 모든 의존성을 한 번에 주입합니다.
     *
     * @example
     * ```kotlin
     * val context = IvmContext.builder()
     *     .executor(deployExecutor)
     *     .worker(outboxPollingWorker)
     *     .outboxRepository(outboxRepo)
     *     .kafkaConfig(kafkaConfig)
     *     .workerConfig(workerConfig)
     *     .build()
     *
     * Ivm.initialize(context)
     * ```
     */
    fun initialize(ctx: IvmContext) {
        synchronized(lock) {
            this.context = ctx
            this.config = ctx.config
            this.executor = ctx.executor
            this.queryWorkflow = ctx.queryWorkflow
            this.outboxPollingWorker = ctx.worker
            this.outboxRepository = ctx.outboxRepository
            this.kafkaConfig = ctx.kafkaConfig
            this.workerConfig = ctx.workerConfig
            IvmClientConfig.global = ctx.config
            cachedClient = null
            initialized = true

            // IvmOps 자동 연동
            com.oliveyoung.ivmlite.sdk.ops.IvmOps.initialize(ctx)
        }
    }

    /**
     * 현재 Context 조회
     */
    fun context(): IvmContext = context

    /**
     * SDK 초기화 여부 확인
     */
    fun isInitialized(): Boolean = initialized

    /**
     * SDK 리셋 (테스트용)
     */
    internal fun reset() {
        synchronized(lock) {
            initialized = false
            context = IvmContext.EMPTY
            config = IvmClientConfig()
            executor = null
            queryWorkflow = null
            outboxPollingWorker = null
            outboxRepository = null
            kafkaConfig = KafkaConfig()
            workerConfig = WorkerConfig()
            cachedClient = null
        }
    }

    // ===== 레거시 API (Deprecated - 하위 호환성) =====

    /**
     * SDK 설정
     *
     * @deprecated IvmContext.builder().config { ... } 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().config { ... }.build())")
    fun configure(block: IvmClientConfig.Builder.() -> Unit) {
        synchronized(lock) {
            config = IvmClientConfig.Builder().apply(block).build()
            IvmClientConfig.global = config
            cachedClient = null
        }
    }

    /**
     * DeployExecutor 주입
     *
     * @deprecated IvmContext.builder().executor(...) 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().executor(...).build())")
    fun setExecutor(executor: DeployExecutor) {
        synchronized(lock) {
            this.executor = executor
            cachedClient = null
        }
    }

    /**
     * QueryViewWorkflow 주입
     *
     * @deprecated IvmContext.builder().queryWorkflow(...) 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().queryWorkflow(...).build())")
    fun setQueryWorkflow(workflow: com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow) {
        synchronized(lock) {
            this.queryWorkflow = workflow
            cachedClient = null
        }
    }

    /**
     * OutboxPollingWorker 주입
     *
     * @deprecated IvmContext.builder().worker(...) 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().worker(...).build())")
    fun setWorker(worker: com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker) {
        synchronized(lock) {
            this.outboxPollingWorker = worker
        }
    }

    /**
     * OutboxRepository 주입
     *
     * @deprecated IvmContext.builder().outboxRepository(...) 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().outboxRepository(...).build())")
    fun setOutboxRepository(repo: OutboxRepositoryPort) {
        synchronized(lock) {
            this.outboxRepository = repo
        }
    }

    /**
     * Kafka/Worker 설정 주입
     *
     * @deprecated IvmContext.builder().kafkaConfig(...).workerConfig(...) 사용 권장
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().kafkaConfig(...).workerConfig(...).build())")
    fun setConfigs(kafka: KafkaConfig, workerCfg: WorkerConfig) {
        synchronized(lock) {
            this.kafkaConfig = kafka
            this.workerConfig = workerCfg
        }
    }

    internal fun getConfig() = config
    internal fun getExecutor() = executor
    internal fun getQueryWorkflow() = queryWorkflow
    internal fun getWorker() = outboxPollingWorker
    internal fun getOutboxRepository() = outboxRepository

    // ===== Client API (캐싱 적용) =====

    /**
     * Client API 진입점
     * 
     * 고빈도 호출 최적화: Double-checked locking으로 캐싱
     * 
     * @example
     * ```kotlin
     * // 쓰기 (Deploy)
     * Ivm.client().ingest().product { ... }.deploy()
     * 
     * // 읽기 (Query)
     * Ivm.client().query("product.pdp").key("SKU-001").get()
     * ```
     * 
     * @return 캐싱된 IvmClient 인스턴스
     */
    fun client(): IvmClient {
        // Fast path: 캐시 히트 (99% 케이스)
        cachedClient?.let { return it }
        
        // Slow path: 캐시 미스 (최초 호출 또는 설정 변경 후)
        synchronized(lock) {
            cachedClient?.let { return it }
            val newClient = IvmClient(config, executor)
            cachedClient = newClient
            return newClient
        }
    }

    // ===== Domain Direct Access =====

    /**
     * Product 도메인 DSL
     * 
     * @example
     * ```kotlin
     * Ivm.product {
     *     tenantId = "oliveyoung"
     *     sku = "SKU-001"
     *     name = "비타민C"
     *     price = 15000
     * }.deploy()
     * ```
     */
    fun product(block: ProductBuilder.() -> Unit): DeployableContext {
        val input = ProductBuilder().apply(block).build()
        return DeployableContext(input, config, executor)
    }

    /**
     * Brand 도메인 DSL
     * 
     * @example
     * ```kotlin
     * Ivm.brand {
     *     tenantId = "oliveyoung"
     *     brandId = "BRAND-001"
     *     name = "올리브영"
     * }.deploy()
     * ```
     */
    fun brand(block: BrandBuilder.() -> Unit): DeployableContext {
        val input = BrandBuilder().apply(block).build()
        return DeployableContext(input, config, executor)
    }

    /**
     * Category 도메인 DSL
     * 
     * @example
     * ```kotlin
     * Ivm.category {
     *     tenantId = "oliveyoung"
     *     categoryId = "CAT-001"
     *     name = "스킨케어"
     * }.deploy()
     * ```
     */
    fun category(block: CategoryBuilder.() -> Unit): DeployableContext {
        val input = CategoryBuilder().apply(block).build()
        return DeployableContext(input, config, executor)
    }

    // ===== Query API (DX 끝판왕) =====

    /**
     * View 조회 API (문자열 viewId) - 레거시 지원
     * 
     * DB 중립적 기본 API + 고급 옵션 지원 + 범위 검색
     * 
     * @example 기본 사용법 (99% 케이스)
     * ```kotlin
     * val view = Ivm.query("product.pdp")
     *     .key("SKU-001")
     *     .get()
     * 
     * println(view.data)          // 전체 데이터
     * println(view["core"])       // core slice
     * println(view.string("name")) // 특정 필드
     * ```
     * 
     * @example 명시적 테넌트/버전
     * ```kotlin
     * val view = Ivm.query("product.pdp")
     *     .tenant("oliveyoung")
     *     .key("SKU-001")
     *     .version(5L)
     *     .get()
     * ```
     * 
     * @example 고급 옵션 (DynamoDB 최적화 등)
     * ```kotlin
     * val view = Ivm.query("product.pdp")
     *     .key("SKU-001")
     *     .options {
     *         strongConsistency()           // 강한 일관성
     *         projection("core", "pricing") // 부분 조회
     *         noCache()                     // 캐시 무시
     *     }
     *     .get()
     * ```
     * 
     * @example 범위 검색
     * ```kotlin
     * val results = Ivm.query("product.pdp")
     *     .tenant("oliveyoung")
     *     .range {
     *         keyPrefix("SKU-")
     *         where("category", "스킨케어")
     *     }
     *     .limit(100)
     *     .list()
     * 
     * results.items.forEach { println(it.entityKey) }
     * ```
     * 
     * @param viewId View 정의 ID (예: "product.pdp", "product.search")
     * @return QueryBuilder for fluent chaining
     */
    fun query(viewId: String): QueryBuilder {
        require(viewId.isNotBlank()) { "viewId must not be blank" }
        return QueryBuilder(config, viewId)
    }
    
    /**
     * View 조회 API (타입 세이프) - 추천!
     * 
     * 등록된 ViewRef를 사용하여 타입 세이프하게 조회합니다.
     * IDE 자동완성 + 컴파일 타임 검증 지원.
     * 
     * @example 기본 사용법 (타입 세이프, 추천!)
     * ```kotlin
     * // Views.Product.Pdp 사용 (IDE 자동완성)
     * val view = Ivm.query(Views.Product.Pdp)
     *     .key("SKU-001")
     *     .get()
     * ```
     * 
     * @example 더 간결하게
     * ```kotlin
     * val view = Views.Product.Pdp.query()
     *     .key("SKU-001")
     *     .get()
     * 
     * // 또는
     * val view = Views.Product.Pdp["SKU-001"].get()
     * ```
     * 
     * @example 타입 세이프 결과 (파서 포함 ViewRef)
     * ```kotlin
     * // 결과 타입이 자동으로 ProductPdpData
     * val result: ProductPdpData = Ivm.query(Views.Product.Pdp)
     *     .key("SKU-001")
     *     .get()
     * 
     * println(result.name)   // IDE 자동완성
     * println(result.price)  // 타입 보장
     * ```
     * 
     * @example 범위 검색 (타입 세이프)
     * ```kotlin
     * val results = Ivm.query(Views.Product.Pdp)
     *     .tenant("oliveyoung")
     *     .range { keyPrefix("SKU-") }
     *     .list()
     * 
     * results.items.forEach { product: ProductPdpData ->
     *     println("${product.name}: ${product.price}원")
     * }
     * ```
     * 
     * @param viewRef ViewRef<T> (예: Views.Product.Pdp, Views.Product.Pdp)
     * @return TypedQueryBuilder<T> for fluent chaining
     */
    fun <T : Any> query(viewRef: ViewRef<T>): TypedQueryBuilder<T> {
        return TypedQueryBuilder(config, viewRef)
    }

    // ===== Status & Plan APIs =====

    /**
     * Deploy 상태 조회 API
     * 
     * @example
     * ```kotlin
     * val status = Ivm.deploy.status("job-123")
     * val result = Ivm.deploy.await("job-123")
     * ```
     */
    val deploy: DeployStatusApi
        get() = DeployStatusApi(config)

    /**
     * Plan 설명 API
     * 
     * @example
     * ```kotlin
     * val plan = Ivm.plan.explainLastPlan("deploy-123")
     * ```
     */
    val plan: PlanExplainApi
        get() = PlanExplainApi(config)

    /**
     * Query API (Namespace)
     * 
     * @example
     * ```kotlin
     * // Namespace 접근 (client().query() 패턴)
     * Ivm.queries.view("product.pdp").key("SKU-001").get()
     * ```
     */
    val queries: QueryApi
        get() = QueryApi(config)

    /**
     * Worker API (OutboxPollingWorker 제어)
     * 
     * OutboxPollingWorker를 시작/중지할 수 있습니다.
     * 
     * @example
     * ```kotlin
     * // Worker 시작
     * Ivm.worker.start()
     * 
     * // Worker 중지 (Graceful shutdown)
     * runBlocking {
     *     Ivm.worker.stop()
     * }
     * 
     * // Worker 상태 확인
     * if (Ivm.worker.isRunning()) {
     *     println("Worker is running")
     * }
     * ```
     * 
     * @throws IllegalStateException Worker가 주입되지 않은 경우
     */
    val worker: WorkerApi
        get() {
            val worker = outboxPollingWorker
                ?: throw IllegalStateException("OutboxPollingWorker is not set. Call Ivm.setWorker() first.")
            return WorkerApi(worker)
        }

    // ===== Consume API (토픽 기반 이벤트 소비) =====

    /**
     * Consume API - 토픽 기반 이벤트 소비
     * 
     * Kafka와 PostgreSQL Polling 모두에서 동일한 API로 사용.
     * 
     * @example
     * ```kotlin
     * // 특정 토픽만 구독
     * val entries = Ivm.consume(Topic.RAW_DATA).poll()
     * 
     * // 여러 토픽 구독
     * val entries = Ivm.consume(Topic.RAW_DATA, Topic.SLICE).poll()
     * 
     * // Flow로 연속 소비
     * Ivm.consume(Topic.RAW_DATA).flow().collect { entry ->
     *     println("Received: ${entry.eventType}")
     * }
     * 
     * // 토픽명으로 구독
     * val entries = Ivm.consumeByTopicName("ivm.events.raw_data").poll()
     * ```
     * 
     * @param topics 구독할 토픽 목록
     * @return ConsumeBuilder for fluent chaining
     * @throws IllegalStateException OutboxRepository가 주입되지 않은 경우
     */
    fun consume(vararg topics: Topic): ConsumeBuilder {
        val repo = outboxRepository
            ?: throw IllegalStateException("OutboxRepository is not set. Call Ivm.setOutboxRepository() first.")
        return ConsumeBuilder(repo, kafkaConfig.topicPrefix, workerConfig, topics.toList())
    }

    /**
     * 토픽명으로 Consume
     * 
     * @param topicNames 토픽명 목록 (예: "ivm.events.raw_data")
     * @return ConsumeBuilder
     */
    fun consumeByTopicName(vararg topicNames: String): ConsumeBuilder {
        val repo = outboxRepository
            ?: throw IllegalStateException("OutboxRepository is not set. Call Ivm.setOutboxRepository() first.")
        val topics = topicNames.mapNotNull { Topic.fromTopicName(it) }
        return ConsumeBuilder(repo, kafkaConfig.topicPrefix, workerConfig, topics)
    }

    /**
     * 모든 토픽 Consume
     * 
     * @return ConsumeBuilder
     */
    fun consumeAll(): ConsumeBuilder {
        val repo = outboxRepository
            ?: throw IllegalStateException("OutboxRepository is not set. Call Ivm.setOutboxRepository() first.")
        return ConsumeBuilder(repo, kafkaConfig.topicPrefix, workerConfig, Topic.entries.toList())
    }

    /**
     * 사용 가능한 토픽명 목록 조회
     */
    fun availableTopics(): List<String> = Topic.allTopicNames(kafkaConfig.topicPrefix)
}

/**
 * Consume Builder - Fluent API
 * 
 * @example
 * ```kotlin
 * Ivm.consume(Topic.RAW_DATA)
 *     .batchSize(50)
 *     .pollInterval(200)
 *     .poll()
 * ```
 */
class ConsumeBuilder(
    private val outboxRepo: OutboxRepositoryPort,
    private val topicPrefix: String,
    private val workerConfig: WorkerConfig,
    private val topics: List<Topic>,
) {
    private var batchSize: Int = workerConfig.batchSize
    private var pollIntervalMs: Long = workerConfig.pollIntervalMs
    
    fun batchSize(size: Int): ConsumeBuilder {
        this.batchSize = size
        return this
    }
    
    fun pollInterval(ms: Long): ConsumeBuilder {
        this.pollIntervalMs = ms
        return this
    }

    /**
     * 이벤트 조회 (한 번)
     */
    suspend fun poll(limit: Int = batchSize): List<OutboxEntry> {
        val aggregateType = topics.firstOrNull()?.aggregateType
        
        val result = if (aggregateType != null) {
            outboxRepo.findPendingByType(aggregateType, limit)
        } else {
            outboxRepo.findPending(limit)
        }
        
        return when (result) {
            is Result.Ok -> result.value
            is Result.Err -> emptyList()
        }
    }

    /**
     * Flow로 연속 소비
     */
    fun flow(): Flow<OutboxEntry> = kotlinx.coroutines.flow.flow {
        while (true) {
            val entries = poll()
            
            for (entry in entries) {
                emit(entry)
            }
            
            val delayMs = if (entries.isEmpty()) {
                workerConfig.idlePollIntervalMs
            } else {
                pollIntervalMs
            }
            kotlinx.coroutines.delay(delayMs)
        }
    }

    /**
     * 콜백으로 처리
     */
    suspend fun forEach(handler: suspend (OutboxEntry) -> Unit) {
        flow().collect { entry ->
            handler(entry)
        }
    }

    /**
     * 이벤트 처리 완료 표시
     */
    suspend fun ack(entries: List<OutboxEntry>) {
        val ids = entries.map { it.id }
        outboxRepo.markProcessed(ids)
    }

    /**
     * 이벤트 처리 실패 표시
     */
    suspend fun nack(entry: OutboxEntry, reason: String) {
        outboxRepo.markFailed(entry.id, reason)
    }
}

/**
 * Worker API - OutboxPollingWorker 제어
 * 
 * @example
 * ```kotlin
 * Ivm.worker.start()  // Worker 시작
 * runBlocking { Ivm.worker.stop() }  // Worker 중지
 * ```
 */
class WorkerApi(
    private val worker: com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
) {
    /**
     * Worker 시작
     * 
     * @return true if started, false if already running or disabled
     */
    fun start(): Boolean = worker.start()

    /**
     * Worker 중지 (Graceful shutdown)
     * 
     * @return true if stopped, false if not running
     */
    suspend fun stop(): Boolean = worker.stop()

    /**
     * Worker 실행 중 여부 확인
     */
    fun isRunning(): Boolean = worker.isRunning()
}
