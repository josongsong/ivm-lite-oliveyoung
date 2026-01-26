package com.oliveyoung.ivmlite.sdk.schema

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.client.QueryBuilder
import com.oliveyoung.ivmlite.sdk.client.QueryOptionsBuilder
import com.oliveyoung.ivmlite.sdk.client.QueryResultPage
import com.oliveyoung.ivmlite.sdk.client.RangeBuilder
import com.oliveyoung.ivmlite.sdk.client.SortOrder
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.ViewResult

/**
 * 타입 세이프한 Query Builder
 * 
 * ViewRef<T>를 통해 결과 타입이 보장됩니다.
 * 
 * @example
 * ```kotlin
 * // 타입이 자동으로 추론됨
 * val result: ProductPdpView = Views.Product.pdp.typedQuery()
 *     .key("SKU-001")
 *     .get()
 * 
 * // result는 ProductPdpView 타입 (컴파일 타임에 보장)
 * println(result.name)
 * println(result.price)
 * ```
 */
@IvmDslMarker
class TypedQueryBuilder<T : Any> internal constructor(
    private val config: IvmClientConfig,
    private val viewRef: ViewRef<T>
) {
    private val delegate = QueryBuilder(config, viewRef.viewId)
    
    // ===== 파라미터 설정 =====
    
    fun tenant(tenantId: String): TypedQueryBuilder<T> {
        delegate.tenant(tenantId)
        return this
    }
    
    fun key(entityKey: String): TypedQueryBuilder<T> {
        delegate.key(entityKey)
        return this
    }
    
    fun version(version: Long): TypedQueryBuilder<T> {
        delegate.version(version)
        return this
    }
    
    fun latest(): TypedQueryBuilder<T> {
        delegate.latest()
        return this
    }
    
    // ===== 범위 검색 =====
    
    fun range(block: RangeBuilder.() -> Unit): TypedQueryBuilder<T> {
        delegate.range(block)
        return this
    }
    
    fun limit(maxResults: Int): TypedQueryBuilder<T> {
        delegate.limit(maxResults)
        return this
    }
    
    fun after(cursor: String?): TypedQueryBuilder<T> {
        delegate.after(cursor)
        return this
    }
    
    fun orderBy(order: SortOrder): TypedQueryBuilder<T> {
        delegate.orderBy(order)
        return this
    }
    
    fun ascending(): TypedQueryBuilder<T> {
        delegate.ascending()
        return this
    }
    
    fun descending(): TypedQueryBuilder<T> {
        delegate.descending()
        return this
    }
    
    // ===== 고급 옵션 =====
    
    fun options(block: QueryOptionsBuilder.() -> Unit): TypedQueryBuilder<T> {
        delegate.options(block)
        return this
    }
    
    // ===== 단일 조회 실행 =====
    
    /**
     * 타입 세이프한 조회 실행
     * 
     * @return T 타입의 결과 (ViewRef의 parser로 변환됨)
     * @throws IllegalStateException ViewRef에 parser가 없는 경우
     */
    fun get(): T {
        val result = delegate.get()
        return parseResult(result)
    }
    
    /**
     * 타입 세이프한 비동기 조회 실행
     */
    suspend fun getAsync(): T {
        val result = delegate.getAsync()
        return parseResult(result)
    }
    
    /**
     * Raw ViewResult 조회 (타입 변환 없이)
     */
    fun getRaw(): ViewResult {
        return delegate.get()
    }
    
    /**
     * 조회 또는 null 반환
     */
    fun getOrNull(): T? {
        return try {
            val result = delegate.get()
            if (result.success) parseResult(result) else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 조회 또는 기본값 반환
     */
    fun getOrDefault(default: T): T {
        return getOrNull() ?: default
    }
    
    /**
     * 존재 여부만 확인
     */
    fun exists(): Boolean {
        return delegate.exists()
    }
    
    // ===== 범위 검색 실행 =====
    
    /**
     * 타입 세이프한 범위 검색
     * 
     * @return TypedQueryResultPage<T>
     */
    fun list(): TypedQueryResultPage<T> {
        val page = delegate.list()
        return TypedQueryResultPage(
            items = page.items.map { parseResult(it) },
            rawItems = page.items,
            totalCount = page.totalCount,
            hasMore = page.hasMore,
            nextCursor = page.nextCursor,
            queryTimeMs = page.queryTimeMs
        )
    }
    
    /**
     * 타입 세이프한 스트림
     */
    fun stream(): Sequence<T> {
        return delegate.stream().map { parseResult(it) }
    }
    
    /**
     * 개수만 조회
     */
    fun count(): Long {
        return delegate.count()
    }
    
    /**
     * 첫 번째 결과만
     */
    fun first(): T? {
        val result = delegate.first() ?: return null
        return parseResult(result)
    }
    
    /**
     * 첫 번째 결과 또는 예외
     */
    fun firstOrThrow(): T {
        return first() ?: throw NoSuchElementException("No results found for ${viewRef.viewId}")
    }
    
    // ===== 내부 =====
    
    private fun parseResult(result: ViewResult): T {
        val parser = viewRef.resultParser
            ?: throw IllegalStateException(
                "ViewRef '${viewRef.viewId}' has no result parser. " +
                "Use getRaw() for untyped access, or define a parser in ViewRef."
            )
        return parser(result.data)
    }
}

/**
 * 타입 세이프한 범위 검색 결과 페이지
 */
data class TypedQueryResultPage<T : Any>(
    /** 타입 세이프한 결과 목록 */
    val items: List<T>,
    
    /** Raw ViewResult 목록 (필요시 접근) */
    val rawItems: List<ViewResult>,
    
    /** 전체 결과 개수 */
    val totalCount: Long,
    
    /** 다음 페이지 존재 여부 */
    val hasMore: Boolean,
    
    /** 다음 페이지 커서 */
    val nextCursor: String?,
    
    /** 쿼리 소요 시간 (ms) */
    val queryTimeMs: Long
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val size: Int get() = items.size
    val first: T? get() = items.firstOrNull()
    val last: T? get() = items.lastOrNull()
}
