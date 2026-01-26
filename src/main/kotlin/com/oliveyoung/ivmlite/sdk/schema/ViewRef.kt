package com.oliveyoung.ivmlite.sdk.schema

import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.sdk.client.QueryBuilder
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import kotlinx.serialization.json.JsonObject

/**
 * 타입 세이프한 View 참조
 * 
 * Contract에서 정의된 View를 타입 세이프하게 참조할 수 있게 합니다.
 * 
 * @example 사용 예시
 * ```kotlin
 * // 문자열 대신 (타입 안전하지 않음)
 * Ivm.query("product.pdp").key("SKU-001").get()
 * 
 * // ViewRef 사용 (타입 세이프)
 * Ivm.query(Views.Product.pdp).key("SKU-001").get()
 * 
 * // 더 간결하게
 * Views.Product.pdp.query().key("SKU-001").get()
 * ```
 * 
 * @param T 이 View의 결과 타입 (TypedViewResult<T>로 사용)
 */
@IvmDslMarker
open class ViewRef<T : Any>(
    /** View 정의 ID (예: "product.pdp", "product.search") */
    val viewId: String,
    
    /** 이 View에 포함된 슬라이스들 */
    val slices: List<String> = emptyList(),
    
    /** View 설명 */
    val description: String = "",
    
    /** 결과 파서 (JSON → T) */
    val resultParser: ((JsonObject) -> T)? = null
) {
    /**
     * 이 View에 대한 쿼리 시작
     * 
     * @example
     * ```kotlin
     * Views.Product.pdp.query()
     *     .key("SKU-001")
     *     .get()
     * ```
     */
    fun query(): QueryBuilder {
        return Ivm.query(viewId)
    }
    
    /**
     * 이 View에 대한 타입 세이프 쿼리 시작
     * 
     * @example
     * ```kotlin
     * val result: ProductPdpView = Views.Product.Pdp.typedQuery()
     *     .key("SKU-001")
     *     .get()
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun typedQuery(): TypedQueryBuilder<T> {
        return Ivm.query(this)
    }
    
    /**
     * 특정 키로 바로 조회 (shortcut)
     * 
     * @example
     * ```kotlin
     * val view = Views.Product.pdp["SKU-001"]
     * ```
     */
    operator fun get(entityKey: String): QueryBuilder {
        return query().key(entityKey)
    }
    
    override fun toString(): String = viewId
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewRef<*>) return false
        return viewId == other.viewId
    }
    
    override fun hashCode(): Int = viewId.hashCode()
}

/**
 * 타입 세이프하지 않은 간단한 View 참조 (String viewId만)
 */
class SimpleViewRef(viewId: String) : ViewRef<JsonObject>(viewId)

/**
 * View 참조 생성 헬퍼
 */
object ViewRefs {
    /**
     * 문자열에서 ViewRef 생성
     */
    fun of(viewId: String): ViewRef<JsonObject> = SimpleViewRef(viewId)
    
    /**
     * 타입 세이프한 ViewRef 생성
     */
    inline fun <reified T : Any> typed(
        viewId: String,
        slices: List<String> = emptyList(),
        description: String = "",
        noinline parser: (JsonObject) -> T
    ): ViewRef<T> = ViewRef(viewId, slices, description, parser)
}
