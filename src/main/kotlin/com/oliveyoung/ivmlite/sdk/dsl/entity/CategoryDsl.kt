package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.ingest.IngestContext
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker

/**
 * Category DSL Builder
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
@IvmDslMarker
class CategoryBuilder {
    private var tenantId: String? = null
    private var categoryId: String? = null
    private var name: String? = null
    private var parentId: String? = null
    private var depth: Int = 0
    private var displayOrder: Int = 0
    private val attributes = mutableMapOf<String, Any>()

    fun tenantId(value: String) {
        tenantId = value
    }

    fun categoryId(value: String) {
        categoryId = value
    }

    fun name(value: String) {
        name = value
    }

    fun parentId(value: String) {
        parentId = value
    }

    fun depth(value: Int) {
        depth = value
    }

    fun displayOrder(value: Int) {
        displayOrder = value
    }

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

/**
 * IngestContext 확장 함수: category { } DSL
 */
fun IngestContext.category(block: CategoryBuilder.() -> Unit): DeployableContext {
    val input = CategoryBuilder().apply(block).build()
    return DeployableContext(input, config, executor)
}
