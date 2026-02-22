package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Brand DSL Builder
 * RFC-IMPL-011 Wave 5-L, RFC-021: 프로퍼티 할당 스타일
 */
@IvmDslMarker
class BrandBuilder {
    var tenantId: String? = null
    var brandId: String? = null
    var name: String? = null
    var logoUrl: String? = null
    var description: String? = null
    var country: String? = null
    private val attributes = mutableMapOf<String, Any>()

    fun tenantId(value: String) { tenantId = value }
    fun brandId(value: String) { brandId = value }
    fun name(value: String) { name = value }
    fun logoUrl(value: String) { logoUrl = value }
    fun description(value: String) { description = value }
    fun country(value: String) { country = value }

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
