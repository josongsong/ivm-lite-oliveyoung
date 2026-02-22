package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.sdk.client.IvmClient
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandBuilder
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryBuilder
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductBuilder
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor

/**
 * IVM SDK Entry Point (SOTA - IngestionOrchestrator 기반)
 *
 * 모든 도메인에 대해 일관된 DSL 제공:
 * - Ivm.product { ... }.deploy()
 * - Ivm.brand { ... }.deploy()
 * - Ivm.category { ... }.deploy()
 * - Ivm.client().query().view("view.id").key("SKU-001").get()
 *
 * @example Deploy (IngestionOrchestrator 기반)
 * ```kotlin
 * Ivm.product {
 *     tenantId = "oliveyoung"
 *     sku = "SKU-001"
 *     name = "비타민C"
 *     price = 15000
 * }.deploy()
 * ```
 *
 * @example Query
 * ```kotlin
 * val view = Ivm.client().query().view("product.pdp").key("SKU-001").get()
 * ```
 */
object Ivm {
    // ===== State =====
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

    private val lock = Any()

    // ===== Initialization =====

    /**
     * SDK 초기화 (SOTA - IvmContext 기반)
     *
     * @example
     * ```kotlin
     * val context = IvmContext.builder()
     *     .executor(deployExecutor)  // IngestionOrchestrator 기반
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
            IvmClientConfig.global = ctx.config
            cachedClient = null
            initialized = true
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
            cachedClient = null
        }
    }

    // ===== Configuration =====

    /**
     * 설정 조회
     */
    fun configure(config: IvmClientConfig) {
        synchronized(lock) {
            this.config = config
            IvmClientConfig.global = config
            cachedClient = null
        }
    }

    /**
     * 설정 DSL
     */
    fun configure(block: IvmClientConfig.Builder.() -> Unit) {
        val newConfig = IvmClientConfig.Builder().apply(block).build()
        configure(newConfig)
    }

    // ===== Deploy API (IngestionOrchestrator 기반) =====

    /**
     * Product Deploy
     */
    fun product(block: ProductBuilder.() -> Unit): DeployableContext {
        val builder = ProductBuilder().apply(block)
        val input = builder.build()
        return DeployableContext(input, config, executor)
    }

    /**
     * Brand Deploy
     */
    fun brand(block: BrandBuilder.() -> Unit): DeployableContext {
        val builder = BrandBuilder().apply(block)
        val input = builder.build()
        return DeployableContext(input, config, executor)
    }

    /**
     * Category Deploy
     */
    fun category(block: CategoryBuilder.() -> Unit): DeployableContext {
        val builder = CategoryBuilder().apply(block)
        val input = builder.build()
        return DeployableContext(input, config, executor)
    }

    // ===== Query API (RFC-021: Ivm.query() 단축) =====

    /**
     * View 조회 시작 (문자열 viewId) - Ivm.client().query() 단축
     *
     * @example
     * ```kotlin
     * val data = Ivm.query("view.product.core.v1").key("product:SKU-001").version(1L).getOrThrow()
     * ```
     */
    fun query(viewId: String): com.oliveyoung.ivmlite.sdk.client.QueryBuilder {
        return client().query(viewId)
    }

    /**
     * View 조회 시작 (타입 세이프 ViewRef)
     *
     * @example
     * ```kotlin
     * val product: ProductCoreData = Ivm.query(Views.Product.Core).key("product:SKU-001").getOrThrow()
     * ```
     */
    fun <T : Any> query(viewRef: com.oliveyoung.ivmlite.sdk.schema.ViewRef<T>): com.oliveyoung.ivmlite.sdk.schema.TypedQueryBuilder<T> {
        return client().query(viewRef)
    }

    // ===== Client API =====

    /**
     * IvmClient 생성 (HTTP API 호출용)
     *
     * @example
     * ```kotlin
     * val view = Ivm.client().query().view("product.pdp").key("SKU-001").get()
     * ```
     */
    fun client(): IvmClient {
        return cachedClient ?: synchronized(lock) {
            cachedClient ?: IvmClient(config).also { cachedClient = it }
        }
    }

    /**
     * 현재 Config 조회 (내부용)
     */
    internal fun getConfig(): IvmClientConfig = config

    /**
     * QueryWorkflow 조회 (내부용)
     */
    internal fun getQueryWorkflow(): com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow? = queryWorkflow

}
