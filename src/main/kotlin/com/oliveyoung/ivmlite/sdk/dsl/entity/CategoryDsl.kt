package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Category DSL Builder
 * RFC-IMPL-011 Wave 5-L, RFC-021: 프로퍼티 할당 스타일
 */
@IvmDslMarker
class CategoryBuilder {
    var tenantId: String? = null
    var categoryId: String? = null
    var name: String? = null
    var parentId: String? = null
    var depth: Int = 0
    var displayOrder: Int = 0
    private val attributes = mutableMapOf<String, Any>()

    fun tenantId(value: String) { tenantId = value }
    fun categoryId(value: String) { categoryId = value }
    fun name(value: String) { name = value }
    fun parentId(value: String) { parentId = value }
    fun depth(value: Int) { depth = value }
    fun displayOrder(value: Int) { displayOrder = value }

    fun attribute(key: String, value: Any) {
        attributes[key] = value
    }

    internal fun build(): CategoryInput {
        return CategoryInput(
            tenantId = requireNotNull(tenantId) { "tenantId is required" },
            categoryId = requireNotNull(categoryId) { "categoryId is required" },
            name = requireNotNull(name) { "name is required" },
            parentId = parentId,
            depth = depth,
            displayOrder = displayOrder,
            attributes = attributes.toMap()
        )
    }
}
