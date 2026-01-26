package com.oliveyoung.ivmlite.sdk

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
    @Volatile
    private var config: IvmClientConfig = IvmClientConfig()
    
    @Volatile
    private var executor: DeployExecutor? = null
    
    // 캐싱된 클라이언트 (config/executor 변경 시 무효화)
    @Volatile
    private var cachedClient: IvmClient? = null
    
    private val lock = Any()

    /**
     * SDK 설정
     * 
     * 스레드 안전: synchronized로 동시 접근 보호
     * 
     * @example
     * ```kotlin
     * Ivm.configure {
     *     tenantId = "oliveyoung"
     * }
     * ```
     */
    fun configure(block: IvmClientConfig.Builder.() -> Unit) {
        synchronized(lock) {
            config = IvmClientConfig.Builder().apply(block).build()
            IvmClientConfig.global = config  // ViewRef.query() 등에서 사용
            cachedClient = null  // 설정 변경 시 캐시 무효화
        }
    }

    /**
     * DeployExecutor 주입 (DI 컨테이너용)
     * 
     * Executor가 내부적으로 Repository를 통해 직접 DB 접근
     * - Ingest → PostgreSQL RawData Insert
     * - Compile → PostgreSQL Slices Insert (via SlicingWorkflow)
     * - Ship → PostgreSQL Outbox Insert (비동기 작업용)
     */
    fun setExecutor(executor: DeployExecutor) {
        synchronized(lock) {
            this.executor = executor
            cachedClient = null  // executor 변경 시 캐시 무효화
        }
    }
    
    @Volatile
    private var queryWorkflow: com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow? = null
    
    /**
     * QueryViewWorkflow 주입 (DI 컨테이너용)
     * 
     * Query가 내부적으로 Repository를 통해 직접 DB 접근
     * - Query → PostgreSQL Slices Select
     * - Contract → DynamoDB GetItem
     */
    fun setQueryWorkflow(workflow: com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow) {
        synchronized(lock) {
            this.queryWorkflow = workflow
            cachedClient = null
        }
    }

    internal fun getConfig() = config
    internal fun getExecutor() = executor
    internal fun getQueryWorkflow() = queryWorkflow

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
     * // Views.Product.pdp 사용 (IDE 자동완성)
     * val view = Ivm.query(Views.Product.pdp)
     *     .key("SKU-001")
     *     .get()
     * ```
     * 
     * @example 더 간결하게
     * ```kotlin
     * val view = Views.Product.pdp.query()
     *     .key("SKU-001")
     *     .get()
     * 
     * // 또는
     * val view = Views.Product.pdp["SKU-001"].get()
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
     * @param viewRef ViewRef<T> (예: Views.Product.pdp, Views.Product.Pdp)
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
}
