package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.ingest.IngestContext
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Product DSL Builder
 * RFC-IMPL-011 Wave 2-D
 */
@IvmDslMarker
class ProductBuilder {
    private var tenantId: String? = null
    private var sku: String? = null
    private var name: String? = null
    private var price: Long? = null
    private var currency: String = "KRW"
    private var category: String? = null
    private var brand: String? = null
    private val attributes = mutableMapOf<String, Any>()

    fun tenantId(value: String) {
        tenantId = value
    }

    fun sku(value: String) {
        sku = value
    }

    fun name(value: String) {
        name = value
    }

    fun price(value: Long) {
        price = value
    }

    fun currency(value: String) {
        currency = value
    }

    fun category(value: String) {
        category = value
    }

    fun brand(value: String) {
        brand = value
    }

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

/**
 * IngestContext 확장 함수: product { } DSL
 * RFC-IMPL-011 Wave 2-D, Wave 5-L
 */
fun IngestContext.product(block: ProductBuilder.() -> Unit): DeployableContext {
    val input = ProductBuilder().apply(block).build()
    return DeployableContext(input, config, executor)
}
