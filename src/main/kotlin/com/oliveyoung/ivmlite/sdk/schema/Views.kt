package com.oliveyoung.ivmlite.sdk.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonObject

/**
 * 타입 세이프한 View 레지스트리
 * 
 * Contract에서 정의된 모든 View들의 참조를 제공합니다.
 * 이 파일은 코드젠으로 자동 생성되거나 수동으로 정의할 수 있습니다.
 * 
 * @example 사용 예시
 * ```kotlin
 * // 타입 세이프한 조회
 * val result = Ivm.query(Views.Product.pdp)
 *     .key("SKU-001")
 *     .get()
 * 
 * // 더 간결하게
 * val result = Views.Product.pdp.query()
 *     .key("SKU-001")
 *     .get()
 * 
 * // 범위 검색
 * val results = Views.Product.pdp.query()
 *     .tenant("oliveyoung")
 *     .range { keyPrefix("SKU-") }
 *     .list()
 * 
 * // 타입 세이프 + 자동 파싱
 * val typed: ProductPdpView = Views.Product.Pdp.typedQuery()
 *     .key("SKU-001")
 *     .get()
 * ```
 */
object Views {
    
    // ===== Product 도메인 =====
    
    object Product {
        /**
         * PDP (Product Detail Page) View
         * 
         * 상품 상세 페이지에 필요한 모든 정보
         * - Slices: CORE, PRICING, INVENTORY, PROMOTION
         */
        val pdp = ViewRef<JsonObject>(
            viewId = "product.pdp",
            slices = listOf("CORE", "PRICING", "INVENTORY", "PROMOTION"),
            description = "상품 상세 페이지 View"
        )
        
        /**
         * 검색용 View
         * 
         * 검색 결과 리스트에 필요한 최소 정보
         * - Slices: CORE, PRICING
         */
        val search = ViewRef<JsonObject>(
            viewId = "product.search",
            slices = listOf("CORE", "PRICING"),
            description = "상품 검색 결과 View"
        )
        
        /**
         * 카트용 View
         * 
         * 장바구니에 필요한 정보
         * - Slices: CORE, PRICING, INVENTORY
         */
        val cart = ViewRef<JsonObject>(
            viewId = "product.cart",
            slices = listOf("CORE", "PRICING", "INVENTORY"),
            description = "장바구니 View"
        )
        
        /**
         * 관리자용 View (모든 정보)
         * 
         * - Slices: ALL
         */
        val admin = ViewRef<JsonObject>(
            viewId = "product.admin",
            slices = listOf("CORE", "PRICING", "INVENTORY", "PROMOTION", "METADATA"),
            description = "관리자용 전체 View"
        )
        
        // ===== 타입 세이프 버전 (파서 포함) =====
        
        /**
         * PDP View (타입 세이프)
         * 
         * @example
         * ```kotlin
         * val result: ProductPdpData = Views.Product.Pdp.typedQuery()
         *     .key("SKU-001")
         *     .get()
         * 
         * println(result.name)
         * println(result.price)
         * ```
         */
        object Pdp : ViewRef<ProductPdpData>(
            viewId = "product.pdp",
            slices = listOf("CORE", "PRICING", "INVENTORY", "PROMOTION"),
            description = "상품 상세 페이지 View (타입 세이프)",
            resultParser = { json -> ProductPdpData.fromJson(json) }
        )
        
        /**
         * 검색 View (타입 세이프)
         */
        object Search : ViewRef<ProductSearchData>(
            viewId = "product.search",
            slices = listOf("CORE", "PRICING"),
            description = "상품 검색 결과 View (타입 세이프)",
            resultParser = { json -> ProductSearchData.fromJson(json) }
        )
    }
    
    // ===== Brand 도메인 =====
    
    object Brand {
        /**
         * 브랜드 상세 View
         */
        val detail = ViewRef<JsonObject>(
            viewId = "brand.detail",
            slices = listOf("CORE", "METADATA"),
            description = "브랜드 상세 View"
        )
        
        /**
         * 브랜드 리스트 View
         */
        val list = ViewRef<JsonObject>(
            viewId = "brand.list",
            slices = listOf("CORE"),
            description = "브랜드 리스트 View"
        )
    }
    
    // ===== Category 도메인 =====
    
    object Category {
        /**
         * 카테고리 트리 View
         */
        val tree = ViewRef<JsonObject>(
            viewId = "category.tree",
            slices = listOf("HIERARCHY"),
            description = "카테고리 트리 View"
        )
        
        /**
         * 카테고리 상세 View
         */
        val detail = ViewRef<JsonObject>(
            viewId = "category.detail",
            slices = listOf("CORE", "HIERARCHY", "METADATA"),
            description = "카테고리 상세 View"
        )
    }
    
    // ===== 동적 View 조회 =====
    
    /**
     * 문자열 ID로 View 참조 생성 (런타임 동적 조회용)
     * 
     * @example
     * ```kotlin
     * val viewId = config.getViewId() // 런타임에 결정
     * val view = Views.of(viewId).query().key("key").get()
     * ```
     */
    fun of(viewId: String): ViewRef<JsonObject> = SimpleViewRef(viewId)
    
    /**
     * 모든 등록된 View 목록
     */
    val all: List<ViewRef<*>> = listOf(
        Product.pdp,
        Product.search,
        Product.cart,
        Product.admin,
        Brand.detail,
        Brand.list,
        Category.tree,
        Category.detail
    )
    
    /**
     * View ID로 찾기
     */
    fun find(viewId: String): ViewRef<*>? = all.find { it.viewId == viewId }
}

// ===== 타입 세이프 결과 데이터 클래스들 =====

/**
 * PDP View 결과 데이터 (코드젠으로 생성 가능)
 */
@Serializable
data class ProductPdpData(
    val productId: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val price: Long,
    val salePrice: Long?,
    val stock: Int,
    val isAvailable: Boolean,
    val promotions: List<String>
) {
    companion object {
        @Suppress("UNUSED_VARIABLE")
        fun fromJson(json: JsonObject): ProductPdpData {
            val core = json["core"]?.jsonObject ?: json
            val pricing = json["pricing"]?.jsonObject ?: json
            val inventory = json["inventory"]?.jsonObject ?: json
            val promotion = json["promotion"]?.jsonObject // TODO: parse promotions array
            
            val productId = core["productId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing required field 'productId' in ProductPdpData")
            val name = core["name"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing required field 'name' in ProductPdpData")
            val price = pricing["price"]?.jsonPrimitive?.long
                ?: throw IllegalArgumentException("Missing required field 'price' in ProductPdpData")
            val stock = inventory["stock"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw IllegalArgumentException("Missing or invalid required field 'stock' in ProductPdpData")
            val isAvailable = inventory["isAvailable"]?.jsonPrimitive?.content?.toBoolean()
                ?: throw IllegalArgumentException("Missing required field 'isAvailable' in ProductPdpData")
            
            return ProductPdpData(
                productId = productId,
                name = name,
                brand = core["brand"]?.jsonPrimitive?.content,
                category = core["category"]?.jsonPrimitive?.content,
                price = price,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                stock = stock,
                isAvailable = isAvailable,
                promotions = emptyList() // TODO: parse from promotion slice
            )
        }
    }
}

/**
 * Search View 결과 데이터
 */
@Serializable
data class ProductSearchData(
    val productId: String,
    val name: String,
    val brand: String?,
    val price: Long,
    val salePrice: Long?,
    val thumbnailUrl: String?
) {
    companion object {
        fun fromJson(json: JsonObject): ProductSearchData {
            val core = json["core"]?.jsonObject ?: json
            val pricing = json["pricing"]?.jsonObject ?: json
            
            return ProductSearchData(
                productId = core["productId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                brand = core["brand"]?.jsonPrimitive?.content,
                price = pricing["price"]?.jsonPrimitive?.long ?: 0L,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                thumbnailUrl = core["thumbnailUrl"]?.jsonPrimitive?.content
            )
        }
    }
}
