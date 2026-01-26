package com.oliveyoung.ivmlite.sdk.dsl.entity

/**
 * Category 입력 데이터
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
data class CategoryInput(
    override val tenantId: String,
    val categoryId: String,
    val name: String,
    val parentId: String? = null,
    val depth: Int = 0,
    val displayOrder: Int = 0,
    val attributes: Map<String, Any> = emptyMap()
) : EntityInput {
    override val entityType: String = "category"
}
