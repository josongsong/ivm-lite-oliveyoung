package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.ingest.IngestContext
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.schema.EntityBuilder
import com.oliveyoung.ivmlite.sdk.schema.EntityRef
import com.oliveyoung.ivmlite.sdk.schema.TypedQueryBuilder
import com.oliveyoung.ivmlite.sdk.schema.ViewRef

/**
 * Legacy Ivm object (client() 패턴용)
 * 
 * 신규 코드는 com.oliveyoung.ivmlite.sdk.Ivm 사용 권장
 * 
 * 스레드 안전성: @Volatile + lazy 캐싱으로 고빈도 호출 최적화
 */
object Ivm {
    @Volatile
    private var config: IvmClientConfig = IvmClientConfig()
    
    @Volatile
    private var executor: DeployExecutor? = null
    
    // 캐싱된 클라이언트 (config 변경 시 무효화)
    @Volatile
    private var cachedClient: IvmClient? = null
    
    private val lock = Any()

    fun configure(block: IvmClientConfig.Builder.() -> Unit) {
        synchronized(lock) {
            config = IvmClientConfig.Builder().apply(block).build()
            cachedClient = null  // 설정 변경 시 캐시 무효화
        }
    }

    /**
     * DI 컨테이너에서 사용하기 위한 executor 주입
     * Wave 5-L: DeployExecutor 연동
     */
    fun setExecutor(executor: DeployExecutor) {
        synchronized(lock) {
            this.executor = executor
            cachedClient = null  // executor 변경 시 캐시 무효화
        }
    }

    /**
     * 캐싱된 IvmClient 반환 (고빈도 호출 최적화)
     * 
     * Double-checked locking으로 스레드 안전 + 성능 보장
     */
    fun client(): IvmClient {
        // Fast path: 캐시 히트
        cachedClient?.let { return it }
        
        // Slow path: 캐시 미스
        synchronized(lock) {
            cachedClient?.let { return it }
            val newClient = IvmClient(config, executor)
            cachedClient = newClient
            return newClient
        }
    }
}

/**
 * IvmClient - Fluent API 진입점
 * 
 * @example
 * ```kotlin
 * // 쓰기 (Deploy)
 * Ivm.client().ingest().product { ... }.deploy()
 * 
 * // 읽기 (Query)
 * Ivm.client().query("product.pdp").key("SKU-001").get()
 * ```
 */
class IvmClient internal constructor(
    private val config: IvmClientConfig,
    private val executor: DeployExecutor? = null
) {
    // ===== Write Path =====
    
    /**
     * Ingest Context 시작 (레거시)
     * 
     * @example
     * ```kotlin
     * Ivm.client().ingest().product { ... }.deploy()
     * ```
     */
    fun ingest(): IngestContext = IngestContext(config, executor)
    
    /**
     * 코드젠 엔티티로 Ingest (추천)
     * 
     * @example
     * ```kotlin
     * Ivm.client().ingest(Entities.Product) {
     *     sku = "SKU-001"
     *     name = "비타민C"
     *     price = 15000
     * }.deploy()
     * ```
     */
    fun <T : EntityBuilder> ingest(
        entityRef: EntityRef<T>,
        block: T.() -> Unit
    ): DeployableContext {
        return IngestContext(config, executor).entity(entityRef, block)
    }
    
    // ===== Read Path (Query) =====
    
    /**
     * View 조회 시작 (문자열 viewId)
     * 
     * @example
     * ```kotlin
     * val view = Ivm.client()
     *     .query("product.pdp")
     *     .key("SKU-001")
     *     .get()
     * ```
     */
    fun query(viewId: String): QueryBuilder {
        require(viewId.isNotBlank()) { "viewId must not be blank" }
        return QueryBuilder(config, viewId)
    }
    
    /**
     * View 조회 시작 (타입 세이프)
     * 
     * @example
     * ```kotlin
     * val view = Ivm.client()
     *     .query(Views.Product.Pdp)
     *     .key("SKU-001")
     *     .get()
     * ```
     */
    fun <T : Any> query(viewRef: ViewRef<T>): TypedQueryBuilder<T> {
        return TypedQueryBuilder(config, viewRef)
    }
    
    /**
     * Query API (Namespace)
     */
    val queries: QueryApi = QueryApi(config)
    
    // ===== Status APIs =====
    
    val deploy: DeployStatusApi = DeployStatusApi(config)
    val plan: PlanExplainApi = PlanExplainApi(config)
}
