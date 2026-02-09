package com.oliveyoung.ivmlite.apps.admin.application.explorer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Explorer 모듈 공유 DTO (Single Source of Truth)
 *
 * RFC-V4-010: DTO는 application 레이어에서 정의하고,
 * 모든 Explorer 서비스에서 공유합니다.
 */

// ==================== Ingest DTOs ====================

@Serializable
data class IngestResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payloadHash: String,
    val compiled: Boolean,
    val slicesCreated: Int,
    val timestamp: String
)

@Serializable
data class IngestItem(
    val entityKey: String,
    val schemaId: String,
    val schemaVersion: String = "1.0.0",
    val payload: String,
    val compile: Boolean = false
)

@Serializable
data class IngestError(
    val entityKey: String,
    val error: String
)

@Serializable
data class BatchIngestResult(
    val tenantId: String,
    val succeeded: List<IngestResult>,
    val failed: List<IngestError>,
    val totalCount: Int,
    val successCount: Int,
    val failCount: Int
)

// ==================== RawData DTOs ====================

@Serializable
data class RawDataResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payload: JsonElement?,
    val payloadRaw: String,
    val payloadHash: String,
    val versions: List<VersionInfo>
)

@Serializable
data class VersionInfo(
    val version: Long,
    val createdAt: String?,
    val hash: String
)

@Serializable
data class RawDataListResult(
    val entries: List<RawDataListItem>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class RawDataListItem(
    val entityKey: String,
    val version: Long,
    val schemaRef: String,
    val updatedAt: String?
)

// ==================== Slice DTOs ====================

@Serializable
data class SlicesResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val slices: List<ExplorerSliceItem>,
    val count: Int
)

@Serializable
data class ExplorerSliceItem(
    val sliceType: String,
    val data: JsonElement?,
    val dataRaw: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val isDeleted: Boolean
)

@Serializable
data class SliceSearchResult(
    val items: List<ExplorerSliceItem>,
    val nextCursor: String?,
    val hasMore: Boolean
)

@Serializable
data class SliceListByTypeResult(
    val tenantId: String,
    val sliceType: String,
    val entries: List<SliceListItem>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class SliceListItem(
    val entityKey: String,
    val sliceType: String,
    val version: Long,
    val data: JsonElement?,
    val dataRaw: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val isDeleted: Boolean,
    val updatedAt: String?
)

@Serializable
data class SliceTypesResult(
    val tenantId: String,
    val types: List<SliceTypeInfo>,
    val total: Int
)

@Serializable
data class SliceTypeInfo(
    val type: String,
    val count: Int
)

// ==================== View DTOs ====================

@Serializable
data class ViewResult(
    val tenantId: String,
    val entityKey: String,
    val viewDefId: String,
    val data: JsonElement?,
    val dataRaw: String,
    val slicesUsed: List<String>,
    val version: Long,
    val assembledAt: String
)

// ==================== Lineage DTOs ====================

@Serializable
data class LineageResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val nodes: List<LineageNode>,
    val edges: List<LineageEdge>
)

@Serializable
data class LineageNode(
    val id: String,
    val type: String, // rawdata, slice, view
    val label: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class LineageEdge(
    val id: String,
    val source: String,
    val target: String,
    val label: String? = null
)

// ==================== Search DTOs ====================

@Serializable
data class SearchResult(
    val query: String,
    val items: List<SearchItem>,
    val count: Int
)

@Serializable
data class SearchItem(
    val entityKey: String,
    val tenantId: String,
    val type: String
)

@Serializable
data class VersionDiffResult(
    val entityKey: String,
    val fromVersion: Long,
    val toVersion: Long,
    val fromData: JsonElement?,
    val toData: JsonElement?,
    val changes: List<DiffChange>
)

@Serializable
data class DiffChange(
    val path: String,
    val type: String, // added, removed, modified
    val oldValue: String?,
    val newValue: String?
)
