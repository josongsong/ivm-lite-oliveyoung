package com.oliveyoung.ivmlite.sdk.dsl.entity

/**
 * Brand 입력 데이터
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
data class BrandInput(
    override val tenantId: String,
    val brandId: String,
    val name: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val country: String? = null,
    val attributes: Map<String, Any> = emptyMap()
) : EntityInput {
    override val entityType: String = "brand"
}
