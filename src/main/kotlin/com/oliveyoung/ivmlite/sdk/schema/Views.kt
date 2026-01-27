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
 * 모든 View는 **대문자로 시작**하며 **타입 세이프한 결과**를 반환합니다.
 * 
 * @example 사용 예시
 * ```kotlin
 * // 타입 세이프 조회 (IDE 자동완성 지원)
 * val product: ProductPdpData = Ivm.query(Views.Product.Pdp)
 *     .key("SKU-001")
 *     .get()
 * 
 * println(product.name)   // String
 * println(product.price)  // Long
 * 
 * // 범위 검색
 * val results = Ivm.query(Views.Product.Search)
 *     .tenant("oliveyoung")
 *     .range { keyPrefix("SKU-") }
 *     .list()
 * 
 * results.items.forEach { product: ProductSearchData ->
 *     println("${product.name}: ${product.price}원")
 * }
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
         * 
         * @example
         * ```kotlin
         * val product: ProductPdpData = Ivm.query(Views.Product.Pdp)
         *     .key("SKU-001")
         *     .get()
         * 
         * println(product.name)
         * println(product.price)
         * ```
         */
        object Pdp : ViewRef<ProductPdpData>(
            viewId = "product.pdp",
            slices = listOf("CORE", "PRICING", "INVENTORY", "PROMOTION"),
            description = "상품 상세 페이지 View",
            resultParser = { json -> ProductPdpData.fromJson(json) }
        )
        
        /**
         * 검색 View
         * 
         * 검색 결과 리스트에 필요한 최소 정보
         * - Slices: CORE, PRICING
         */
        object Search : ViewRef<ProductSearchData>(
            viewId = "product.search",
            slices = listOf("CORE", "PRICING"),
            description = "상품 검색 결과 View",
            resultParser = { json -> ProductSearchData.fromJson(json) }
        )
        
        /**
         * 장바구니 View
         * 
         * 장바구니에 필요한 정보
         * - Slices: CORE, PRICING, INVENTORY
         */
        object Cart : ViewRef<ProductCartData>(
            viewId = "product.cart",
            slices = listOf("CORE", "PRICING", "INVENTORY"),
            description = "장바구니 View",
            resultParser = { json -> ProductCartData.fromJson(json) }
        )
        
        /**
         * 관리자용 View (모든 정보)
         * 
         * - Slices: ALL
         */
        object Admin : ViewRef<ProductAdminData>(
            viewId = "product.admin",
            slices = listOf("CORE", "PRICING", "INVENTORY", "PROMOTION", "METADATA"),
            description = "관리자용 전체 View",
            resultParser = { json -> ProductAdminData.fromJson(json) }
        )
    }
    
    // ===== Brand 도메인 =====
    
    object Brand {
        /**
         * 브랜드 상세 View
         */
        object Detail : ViewRef<BrandDetailData>(
            viewId = "brand.detail",
            slices = listOf("CORE", "METADATA"),
            description = "브랜드 상세 View",
            resultParser = { json -> BrandDetailData.fromJson(json) }
        )
        
        /**
         * 브랜드 리스트 View
         */
        object List : ViewRef<BrandListData>(
            viewId = "brand.list",
            slices = listOf("CORE"),
            description = "브랜드 리스트 View",
            resultParser = { json -> BrandListData.fromJson(json) }
        )
    }
    
    // ===== Category 도메인 =====
    
    object Category {
        /**
         * 카테고리 트리 View
         */
        object Tree : ViewRef<CategoryTreeData>(
            viewId = "category.tree",
            slices = listOf("HIERARCHY"),
            description = "카테고리 트리 View",
            resultParser = { json -> CategoryTreeData.fromJson(json) }
        )
        
        /**
         * 카테고리 상세 View
         */
        object Detail : ViewRef<CategoryDetailData>(
            viewId = "category.detail",
            slices = listOf("CORE", "HIERARCHY", "METADATA"),
            description = "카테고리 상세 View",
            resultParser = { json -> CategoryDetailData.fromJson(json) }
        )
    }
    
    /**
     * 모든 등록된 View 목록
     */
    val all: List<ViewRef<*>> = listOf(
        Product.Pdp,
        Product.Search,
        Product.Cart,
        Product.Admin,
        Brand.Detail,
        Brand.List,
        Category.Tree,
        Category.Detail
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

/**
 * Cart View 결과 데이터
 */
@Serializable
data class ProductCartData(
    val productId: String,
    val name: String,
    val price: Long,
    val salePrice: Long?,
    val stock: Int,
    val isAvailable: Boolean,
    val thumbnailUrl: String?
) {
    companion object {
        fun fromJson(json: JsonObject): ProductCartData {
            val core = json["core"]?.jsonObject ?: json
            val pricing = json["pricing"]?.jsonObject ?: json
            val inventory = json["inventory"]?.jsonObject ?: json
            
            return ProductCartData(
                productId = core["productId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                price = pricing["price"]?.jsonPrimitive?.long ?: 0L,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                stock = inventory["stock"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                isAvailable = inventory["isAvailable"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                thumbnailUrl = core["thumbnailUrl"]?.jsonPrimitive?.content
            )
        }
    }
}

/**
 * Admin View 결과 데이터 (전체 정보)
 */
@Serializable
data class ProductAdminData(
    val productId: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val price: Long,
    val salePrice: Long?,
    val stock: Int,
    val isAvailable: Boolean,
    val promotions: List<String>,
    val metadata: JsonObject?
) {
    companion object {
        fun fromJson(json: JsonObject): ProductAdminData {
            val core = json["core"]?.jsonObject ?: json
            val pricing = json["pricing"]?.jsonObject ?: json
            val inventory = json["inventory"]?.jsonObject ?: json
            val metadata = json["metadata"]?.jsonObject
            
            return ProductAdminData(
                productId = core["productId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                brand = core["brand"]?.jsonPrimitive?.content,
                category = core["category"]?.jsonPrimitive?.content,
                price = pricing["price"]?.jsonPrimitive?.long ?: 0L,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                stock = inventory["stock"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                isAvailable = inventory["isAvailable"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                promotions = emptyList(),
                metadata = metadata
            )
        }
    }
}

// ===== Brand 데이터 클래스 =====

/**
 * Brand Detail View 결과 데이터
 */
@Serializable
data class BrandDetailData(
    val brandId: String,
    val name: String,
    val logoUrl: String?,
    val description: String?,
    val metadata: JsonObject?
) {
    companion object {
        fun fromJson(json: JsonObject): BrandDetailData {
            val core = json["core"]?.jsonObject ?: json
            val metadata = json["metadata"]?.jsonObject
            
            return BrandDetailData(
                brandId = core["brandId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                logoUrl = core["logoUrl"]?.jsonPrimitive?.content,
                description = core["description"]?.jsonPrimitive?.content,
                metadata = metadata
            )
        }
    }
}

/**
 * Brand List View 결과 데이터
 */
@Serializable
data class BrandListData(
    val brandId: String,
    val name: String,
    val logoUrl: String?
) {
    companion object {
        fun fromJson(json: JsonObject): BrandListData {
            val core = json["core"]?.jsonObject ?: json
            
            return BrandListData(
                brandId = core["brandId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                logoUrl = core["logoUrl"]?.jsonPrimitive?.content
            )
        }
    }
}

// ===== Category 데이터 클래스 =====

/**
 * Category Tree View 결과 데이터
 */
@Serializable
data class CategoryTreeData(
    val categoryId: String,
    val name: String,
    val parentId: String?,
    val depth: Int,
    val path: String?
) {
    companion object {
        fun fromJson(json: JsonObject): CategoryTreeData {
            val hierarchy = json["hierarchy"]?.jsonObject ?: json
            
            return CategoryTreeData(
                categoryId = hierarchy["categoryId"]?.jsonPrimitive?.content ?: "",
                name = hierarchy["name"]?.jsonPrimitive?.content ?: "",
                parentId = hierarchy["parentId"]?.jsonPrimitive?.content,
                depth = hierarchy["depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                path = hierarchy["path"]?.jsonPrimitive?.content
            )
        }
    }
}

/**
 * Category Detail View 결과 데이터
 */
@Serializable
data class CategoryDetailData(
    val categoryId: String,
    val name: String,
    val parentId: String?,
    val depth: Int,
    val path: String?,
    val description: String?,
    val metadata: JsonObject?
) {
    companion object {
        fun fromJson(json: JsonObject): CategoryDetailData {
            val core = json["core"]?.jsonObject ?: json
            val hierarchy = json["hierarchy"]?.jsonObject ?: json
            val metadata = json["metadata"]?.jsonObject
            
            return CategoryDetailData(
                categoryId = core["categoryId"]?.jsonPrimitive?.content 
                    ?: hierarchy["categoryId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content 
                    ?: hierarchy["name"]?.jsonPrimitive?.content ?: "",
                parentId = hierarchy["parentId"]?.jsonPrimitive?.content,
                depth = hierarchy["depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                path = hierarchy["path"]?.jsonPrimitive?.content,
                description = core["description"]?.jsonPrimitive?.content,
                metadata = metadata
            )
        }
    }
}
