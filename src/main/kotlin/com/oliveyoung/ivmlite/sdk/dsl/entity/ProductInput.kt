package com.oliveyoung.ivmlite.sdk.dsl.entity

/**
 * Product 입력 데이터
 * RFC-IMPL-011 Wave 2-D
 */
data class ProductInput(
    override val tenantId: String,
    val sku: String,
    val name: String,
    val price: Long,
    val currency: String = "KRW",
    val category: String? = null,
    val brand: String? = null,
    val attributes: Map<String, Any> = emptyMap()
) : EntityInput {
    override val entityType: String = "product"
}
