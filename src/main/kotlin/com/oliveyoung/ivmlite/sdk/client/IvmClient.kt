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
 * @deprecated 신규 코드는 com.oliveyoung.ivmlite.sdk.Ivm 사용 권장
 *
 * 이 객체는 하위 호환성을 위해 유지되며, 내부적으로
 * com.oliveyoung.ivmlite.sdk.Ivm에 위임합니다.
 */
@Deprecated(
    message = "Use com.oliveyoung.ivmlite.sdk.Ivm instead",
    replaceWith = ReplaceWith("com.oliveyoung.ivmlite.sdk.Ivm")
)
object Ivm {
    /**
     * @deprecated Use com.oliveyoung.ivmlite.sdk.Ivm.configure() instead
     */
    @Deprecated("Use com.oliveyoung.ivmlite.sdk.Ivm.configure()")
    fun configure(block: IvmClientConfig.Builder.() -> Unit) {
        @Suppress("DEPRECATION")
        com.oliveyoung.ivmlite.sdk.Ivm.configure(block)
    }

    /**
     * @deprecated Use Ivm.initialize(IvmContext.builder().executor(...).build()) instead
     */
    @Deprecated("Use Ivm.initialize(IvmContext.builder().executor(...).build())")
    fun setExecutor(executor: DeployExecutor) {
        @Suppress("DEPRECATION")
        com.oliveyoung.ivmlite.sdk.Ivm.setExecutor(executor)
    }

    /**
     * IvmClient 반환 (sdk.Ivm에 위임)
     */
    fun client(): IvmClient = com.oliveyoung.ivmlite.sdk.Ivm.client()
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
