package com.oliveyoung.ivmlite.sdk.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
         * Core View - 상품 기본 정보
         *
         * Contract: view.product.core.v1
         * - requiredSlices: CORE
         */
        object Core : ViewRef<ProductCoreData>(
            viewId = "view.product.core.v1",
            slices = listOf("CORE"),
            description = "상품 기본 정보 View",
            resultParser = { json -> ProductCoreData.fromJson(json) }
        )

        /**
         * PDP (Product Detail Page) View
         *
         * Contract: view.product.pdp.v1
         * - requiredSlices: CORE, PRICE, MEDIA, NOTICE, ASSOCIATED
         * - optionalSlices: INVENTORY, CATEGORY, INDEX, ENRICHED
         */
        object Pdp : ViewRef<ProductPdpData>(
            viewId = "view.product.pdp.v1",
            slices = listOf("CORE", "PRICE", "MEDIA", "NOTICE", "ASSOCIATED", "INVENTORY", "CATEGORY", "INDEX", "ENRICHED"),
            description = "상품 상세 페이지 View",
            resultParser = { json -> ProductPdpData.fromJson(json) }
        )

        /**
         * 검색 View
         *
         * Contract: view.product.search.v1
         * - requiredSlices: CORE, PRICE, CATEGORY, INDEX
         * - optionalSlices: MEDIA, INVENTORY, ENRICHED
         */
        object Search : ViewRef<ProductSearchData>(
            viewId = "view.product.search.v1",
            slices = listOf("CORE", "PRICE", "CATEGORY", "INDEX", "MEDIA", "INVENTORY", "ENRICHED"),
            description = "상품 검색 결과 View",
            resultParser = { json -> ProductSearchData.fromJson(json) }
        )

        /**
         * 스토어프론트 View
         *
         * Contract: view.product.storefront.v1
         * - requiredSlices: CORE, PRICE, MEDIA, CATEGORY, INDEX
         * - optionalSlices: ENRICHED
         */
        object Storefront : ViewRef<ProductSearchData>(
            viewId = "view.product.storefront.v1",
            slices = listOf("CORE", "PRICE", "MEDIA", "CATEGORY", "INDEX", "ENRICHED"),
            description = "스토어프론트 전시 View",
            resultParser = { json -> ProductSearchData.fromJson(json) }
        )
    }

    // ===== Brand 도메인 =====

    object Brand {
        /**
         * 브랜드 상세 View
         *
         * Contract: view.brand.detail.v1
         * - requiredSlices: CORE, SUMMARY
         */
        object Detail : ViewRef<BrandDetailData>(
            viewId = "view.brand.detail.v1",
            slices = listOf("CORE", "SUMMARY"),
            description = "브랜드 상세 View",
            resultParser = { json -> BrandDetailData.fromJson(json) }
        )
    }

    /**
     * 모든 등록된 View 목록
     */
    val all: List<ViewRef<*>> = listOf(
        Product.Core,
        Product.Pdp,
        Product.Search,
        Product.Storefront,
        Brand.Detail
    )

    /**
     * View ID로 찾기
     */
    fun find(viewId: String): ViewRef<*>? = all.find { it.viewId == viewId }
}

// ===== 타입 세이프 결과 데이터 클래스들 =====

/**
 * Product Core View 결과 데이터 (CORE 슬라이스만)
 */
@Serializable
data class ProductCoreData(
    val name: String,
    val price: Long?,
    val category: String?,
    val brand: String?
) {
    companion object {
        fun fromJson(json: JsonObject): ProductCoreData {
            // QueryViewWorkflow는 slices를 JsonArray로 반환
            val coreSlice = runCatching { json["slices"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
            val core = coreSlice ?: json["core"]?.jsonObject ?: json

            return ProductCoreData(
                name = core["name"]?.jsonPrimitive?.content ?: "",
                price = core["price"]?.jsonPrimitive?.long,
                category = core["category"]?.jsonPrimitive?.content,
                brand = core["brand"]?.jsonPrimitive?.content
            )
        }
    }
}

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
        fun fromJson(json: JsonObject): ProductPdpData {
            val core = json["core"]?.jsonObject ?: json
            val pricing = json["pricing"]?.jsonObject ?: json
            val inventory = json["inventory"]?.jsonObject ?: json
            val promotion = json["promotion"]?.jsonObject

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

            val promotionIds = promotion?.get("promotionIds")?.jsonArray
            val promotions = promotionIds
                ?.map { element -> element.jsonPrimitive.content }
                ?: emptyList()

            return ProductPdpData(
                productId = productId,
                name = name,
                brand = core["brand"]?.jsonPrimitive?.content,
                category = core["category"]?.jsonPrimitive?.content,
                price = price,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                stock = stock,
                isAvailable = isAvailable,
                promotions = promotions
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
            val promotion = json["promotion"]?.jsonObject
            val metadata = json["metadata"]?.jsonObject

            val promotionIds = promotion?.get("promotionIds")?.jsonArray
            val promotions = promotionIds
                ?.map { element -> element.jsonPrimitive.content }
                ?: emptyList()

            return ProductAdminData(
                productId = core["productId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                brand = core["brand"]?.jsonPrimitive?.content,
                category = core["category"]?.jsonPrimitive?.content,
                price = pricing["price"]?.jsonPrimitive?.long ?: 0L,
                salePrice = pricing["salePrice"]?.jsonPrimitive?.long,
                stock = inventory["stock"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                isAvailable = inventory["isAvailable"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                promotions = promotions,
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

// ===== Category 데이터 클래스 =====

/**
 * Category Core View 결과 데이터
 */
@Serializable
data class CategoryCoreData(
    val categoryId: String,
    val name: String,
    val parentId: String?,
    val depth: Int
) {
    companion object {
        fun fromJson(json: JsonObject): CategoryCoreData {
            val coreSlice = runCatching { json["slices"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
            val core = coreSlice ?: json["core"]?.jsonObject ?: json

            return CategoryCoreData(
                categoryId = core["categoryId"]?.jsonPrimitive?.content ?: "",
                name = core["name"]?.jsonPrimitive?.content ?: "",
                parentId = core["parentId"]?.jsonPrimitive?.content,
                depth = core["depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        }
    }
}
