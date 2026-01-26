package com.oliveyoung.ivmlite.apps.runtimeapi.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * API Request DTOs
 */

@Serializable
data class IngestRequest(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payload: JsonObject,
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        require(version >= 0) { "version must be non-negative" }
        require(schemaId.isNotBlank()) { "schemaId must not be blank" }
        require(schemaVersion.isNotBlank()) { "schemaVersion must not be blank" }
    }
}

@Serializable
data class SliceRequest(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        require(version >= 0) { "version must be non-negative" }
    }
}

@Serializable
data class QueryRequest(
    val tenantId: String,
    val viewId: String,
    val entityKey: String,
    val version: Long,
    val sliceTypes: List<String>,
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(viewId.isNotBlank()) { "viewId must not be blank" }
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        require(version >= 0) { "version must be non-negative" }
        require(sliceTypes.isNotEmpty()) { "sliceTypes must not be empty" }
    }
}
