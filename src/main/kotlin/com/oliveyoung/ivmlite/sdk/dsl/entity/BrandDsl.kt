package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.ingest.IngestContext
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Brand DSL Builder
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
@IvmDslMarker
class BrandBuilder {
    private var tenantId: String? = null
    private var brandId: String? = null
    private var name: String? = null
    private var logoUrl: String? = null
    private var description: String? = null
    private var country: String? = null
    private val attributes = mutableMapOf<String, Any>()

    fun tenantId(value: String) {
        tenantId = value
    }

    fun brandId(value: String) {
        brandId = value
    }

    fun name(value: String) {
        name = value
    }

    fun logoUrl(value: String) {
        logoUrl = value
    }

    fun description(value: String) {
        description = value
    }

    fun country(value: String) {
        country = value
    }

    fun attribute(key: String, value: Any) {
        attributes[key] = value
    }

    internal fun build(): BrandInput {
        return BrandInput(
            tenantId = requireNotNull(tenantId) { "tenantId is required" },
            brandId = requireNotNull(brandId) { "brandId is required" },
            name = requireNotNull(name) { "name is required" },
            logoUrl = logoUrl,
            description = description,
            country = country,
            attributes = attributes.toMap()
        )
    }
}

/**
 * IngestContext 확장 함수: brand { } DSL
 */
fun IngestContext.brand(block: BrandBuilder.() -> Unit): DeployableContext {
    val input = BrandBuilder().apply(block).build()
    return DeployableContext(input, config, executor)
}
