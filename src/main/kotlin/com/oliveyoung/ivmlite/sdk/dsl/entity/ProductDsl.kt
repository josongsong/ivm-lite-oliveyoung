package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Product DSL Builder
 * RFC-IMPL-011 Wave 2-D, RFC-021: 프로퍼티 할당 스타일 (tenantId = "x")
 */
@IvmDslMarker
class ProductBuilder {
    var tenantId: String? = null
    var sku: String? = null
    var name: String? = null
    var price: Long? = null
    var currency: String = "KRW"
    var category: String? = null
    var brand: String? = null
    private val attributes = mutableMapOf<String, Any>()

    /** 레거시: 함수 호출 스타일 (하위 호환) */
    fun tenantId(value: String) { tenantId = value }
    fun sku(value: String) { sku = value }
    fun name(value: String) { name = value }
    fun price(value: Long) { price = value }
    fun currency(value: String) { currency = value }
    fun category(value: String) { category = value }
    fun brand(value: String) { brand = value }

    fun attribute(key: String, value: Any) {
        attributes[key] = value
    }

    internal fun build(): ProductInput {
        return ProductInput(
            tenantId = requireNotNull(tenantId) { "tenantId is required" },
            sku = requireNotNull(sku) { "sku is required" },
            name = requireNotNull(name) { "name is required" },
            price = requireNotNull(price) { "price is required" },
            currency = currency,
            category = category,
            brand = brand,
            attributes = attributes.toMap()
        )
    }
}
