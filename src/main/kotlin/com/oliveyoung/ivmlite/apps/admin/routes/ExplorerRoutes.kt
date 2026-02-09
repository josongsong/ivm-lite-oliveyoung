package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.*
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.oliveyoung.ivmlite.apps.admin.application.SliceListByTypeResult
import com.oliveyoung.ivmlite.apps.admin.application.SliceListItem
import com.oliveyoung.ivmlite.apps.admin.application.SliceTypeInfo
import com.oliveyoung.ivmlite.apps.admin.application.SliceTypesResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.koin.ktor.ext.inject

/**
 * Data Explorer Routes (Admin API)
 *
 * 데이터 탐색기를 위한 API 엔드포인트.
 * RawData, Slice, View를 조회하고 Lineage를 추적합니다.
 *
 * Endpoints:
 * - GET /query/raw      - RawData 조회
 * - GET /query/slices   - Slice 목록 조회
 * - GET /query/view     - View 조합 미리보기
 * - GET /query/lineage  - Lineage 그래프
 * - GET /query/search   - 통합 검색
 * - GET /query/autocomplete - 자동완성
 * - GET /query/diff     - 버전 비교
 * - POST /query/ingest  - RawData 등록 (SDK DSL)
 * - POST /query/ingest/batch - 배치 RawData 등록
 */
fun Route.explorerRoutes() {
    val explorerService by inject<ExplorerService>()

    /**
     * GET /query/raw/list
     * RawData 목록 조회 (페이지네이션)
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityPrefix: 엔티티 키 프리픽스 필터 (optional)
     * - limit: 결과 수 (default: 50, max: 100)
     * - cursor: 페이징 커서 (optional)
     */
    get("/raw/list") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityPrefix = call.request.queryParameters["entityPrefix"]
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val cursor = call.request.queryParameters["cursor"]

        when (val result = explorerService.listRawData(tenantId, entityPrefix, limit, cursor)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/raw
     * RawData 조회
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityId or entity: 엔티티 키 (required)
     * - version: 특정 버전 (optional, 없으면 latest)
     */
    get("/raw") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityKey = call.request.queryParameters["entityId"]
            ?: call.request.queryParameters["entity"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("entityId parameter is required")
            )
        val version = call.request.queryParameters["version"]?.toLongOrNull()

        when (val result = explorerService.getRawData(tenantId, entityKey, version)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/slices
     * Slice 목록 조회
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityId or entity: 엔티티 키 (required)
     * - version: 특정 버전 (optional)
     * - sliceType or type: Slice 타입 필터 (optional)
     */
    get("/slices") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityKey = call.request.queryParameters["entityId"]
            ?: call.request.queryParameters["entity"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("entityId parameter is required")
            )
        val version = call.request.queryParameters["version"]?.toLongOrNull()
        val sliceType = call.request.queryParameters["sliceType"] ?: call.request.queryParameters["type"]

        when (val result = explorerService.getSlices(tenantId, entityKey, version, sliceType)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/slices/search
     * Slice Range Query (프리픽스 검색)
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - prefix: 키 프리픽스 (required)
     * - type: Slice 타입 필터 (optional)
     * - limit: 결과 수 (default: 50)
     * - cursor: 페이징 커서 (optional)
     */
    get("/slices/search") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val keyPrefix = call.request.queryParameters["prefix"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("prefix parameter is required")
            )
        val sliceType = call.request.queryParameters["type"]
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val cursor = call.request.queryParameters["cursor"]

        when (val result = explorerService.searchSlices(tenantId, keyPrefix, sliceType, limit, cursor)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/slices/types
     * 사용 가능한 슬라이스 타입 목록 조회
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     */
    get("/slices/types") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"

        when (val result = explorerService.getSliceTypes(tenantId)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/slices/list
     * 슬라이스 타입별 전체 목록 조회
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - sliceType or type: Slice 타입 (required)
     * - limit: 결과 수 (default: 50)
     * - cursor: 페이징 커서 (optional)
     */
    get("/slices/list") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val sliceType = call.request.queryParameters["sliceType"]
            ?: call.request.queryParameters["type"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("sliceType parameter is required")
            )
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val cursor = call.request.queryParameters["cursor"]

        when (val result = explorerService.listSlicesByType(tenantId, sliceType, limit, cursor)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/view
     * View 조합 미리보기
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityId or entity: 엔티티 키 (required)
     * - viewDefId or viewDef: ViewDef ID (optional)
     */
    get("/view") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityKey = call.request.queryParameters["entityId"]
            ?: call.request.queryParameters["entity"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("entityId parameter is required")
            )
        val viewDefId = call.request.queryParameters["viewDefId"]
            ?: call.request.queryParameters["viewDef"]
            ?: "default"

        when (val result = explorerService.getView(tenantId, entityKey, viewDefId)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/lineage
     * Lineage 그래프 조회
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityId or entity: 엔티티 키 (required)
     * - version: 특정 버전 (optional)
     */
    get("/lineage") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityKey = call.request.queryParameters["entityId"]
            ?: call.request.queryParameters["entity"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("entityId parameter is required")
            )
        val version = call.request.queryParameters["version"]?.toLongOrNull()

        when (val result = explorerService.getLineage(tenantId, entityKey, version)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/search
     * 통합 검색 (엔티티 키 검색)
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - q: 검색어 (optional, 빈 값이면 전체 목록)
     * - limit: 결과 수 (default: 20)
     */
    get("/search") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val query = call.request.queryParameters["q"] ?: ""
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

        when (val result = explorerService.search(tenantId, query, limit)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/autocomplete
     * 자동완성 (빠른 프리픽스 매칭)
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - query or prefix: 검색어/프리픽스 (required)
     * - limit: 결과 수 (default: 10)
     */
    get("/autocomplete") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val prefix = call.request.queryParameters["query"]
            ?: call.request.queryParameters["prefix"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("query parameter is required")
            )
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

        when (val result = explorerService.autocomplete(tenantId, prefix, limit)) {
            is Result.Ok -> call.respond(
                HttpStatusCode.OK,
                AutocompleteResponse(
                    prefix = prefix,
                    suggestions = result.value
                )
            )
            is Result.Err -> throw result.error
        }
    }

    /**
     * GET /query/diff
     * 버전 비교 (Diff)
     *
     * Query Params:
     * - tenant: 테넌트 ID (default: "oliveyoung")
     * - entityId or entity: 엔티티 키 (required)
     * - fromVersion or from: 시작 버전 (required)
     * - toVersion or to: 끝 버전 (required)
     */
    get("/diff") {
        val tenantId = call.request.queryParameters["tenant"] ?: "oliveyoung"
        val entityKey = call.request.queryParameters["entityId"]
            ?: call.request.queryParameters["entity"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("entityId parameter is required")
            )
        val fromVersion = (call.request.queryParameters["fromVersion"] ?: call.request.queryParameters["from"])?.toLongOrNull()
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("fromVersion parameter is required")
            )
        val toVersion = (call.request.queryParameters["toVersion"] ?: call.request.queryParameters["to"])?.toLongOrNull()
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("toVersion parameter is required")
            )

        when (val result = explorerService.diffVersions(tenantId, entityKey, fromVersion, toVersion)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    // ==================== RawData 등록 (SDK DSL) ====================

    /**
     * POST /query/ingest
     * RawData 등록 (단일)
     *
     * Request Body:
     * {
     *   "tenantId": "oliveyoung",
     *   "entityKey": "PRODUCT:SKU-001",
     *   "schemaId": "product_v1.0.0",
     *   "schemaVersion": "1.0.0",
     *   "payload": "{\"name\": \"립스틱\", \"price\": 25000}",
     *   "compile": true
     * }
     */
    post("/ingest") {
        val request = call.receive<IngestRequest>()

        when (val result = explorerService.ingest(
            tenantId = request.tenantId,
            entityKey = request.entityKey,
            schemaId = request.schemaId,
            schemaVersion = request.schemaVersion,
            payload = request.payload,
            compile = request.compile
        )) {
            is Result.Ok -> call.respond(HttpStatusCode.Created, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }

    /**
     * POST /query/ingest/batch
     * RawData 배치 등록
     *
     * Request Body:
     * {
     *   "tenantId": "oliveyoung",
     *   "items": [
     *     {"entityKey": "PRODUCT:SKU-001", "schemaId": "product_v1.0.0", "payload": {...}},
     *     {"entityKey": "PRODUCT:SKU-002", "schemaId": "product_v1.0.0", "payload": {...}}
     *   ]
     * }
     */
    post("/ingest/batch") {
        val request = call.receive<BatchIngestRequest>()

        val items = request.items.map { item ->
            IngestItem(
                entityKey = item.entityKey,
                schemaId = item.schemaId,
                schemaVersion = item.schemaVersion,
                payload = item.payload,
                compile = item.compile
            )
        }

        when (val result = explorerService.ingestBatch(request.tenantId, items)) {
            is Result.Ok -> call.respond(HttpStatusCode.OK, result.value.toResponse())
            is Result.Err -> throw result.error
        }
    }
}

// ==================== Request DTOs ====================

@Serializable
data class IngestRequest(
    val tenantId: String = "oliveyoung",
    val entityKey: String,
    val schemaId: String,
    val schemaVersion: String = "1.0.0",
    val payload: String,
    val compile: Boolean = false
)

@Serializable
data class BatchIngestRequest(
    val tenantId: String = "oliveyoung",
    val items: List<IngestItemRequest>
)

@Serializable
data class IngestItemRequest(
    val entityKey: String,
    val schemaId: String,
    val schemaVersion: String = "1.0.0",
    val payload: String,
    val compile: Boolean = false
)

// ==================== Response DTOs ====================

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class IngestResponse(
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
data class BatchIngestResponse(
    val tenantId: String,
    val succeeded: List<IngestResponse>,
    val failed: List<IngestErrorResponse>,
    val totalCount: Int,
    val successCount: Int,
    val failCount: Int
)

@Serializable
data class IngestErrorResponse(
    val entityKey: String,
    val error: String
)

@Serializable
data class RawDataResponse(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payload: JsonElement?,
    val payloadRaw: String,
    val payloadHash: String,
    val versions: List<VersionInfoResponse>
)

@Serializable
data class VersionInfoResponse(
    val version: Long,
    val createdAt: String?,
    val hash: String
)

@Serializable
data class SlicesResponse(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val slices: List<ExplorerSliceItemResponse>,
    val count: Int
)

@Serializable
data class ExplorerSliceItemResponse(
    val sliceType: String,
    val data: JsonElement?,
    val dataRaw: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val isDeleted: Boolean
)

@Serializable
data class SliceSearchResponse(
    val items: List<ExplorerSliceItemResponse>,
    val nextCursor: String?,
    val hasMore: Boolean
)

@Serializable
data class ViewResponse(
    val tenantId: String,
    val entityKey: String,
    val viewDefId: String,
    val data: JsonElement?,
    val dataRaw: String,
    val slicesUsed: List<String>,
    val version: Long,
    val assembledAt: String
)

@Serializable
data class LineageResponse(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val nodes: List<LineageNodeResponse>,
    val edges: List<LineageEdgeResponse>
)

@Serializable
data class LineageNodeResponse(
    val id: String,
    val type: String,
    val label: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class LineageEdgeResponse(
    val id: String,
    val source: String,
    val target: String,
    val label: String? = null
)

@Serializable
data class SearchResponse(
    val query: String,
    val items: List<SearchItemResponse>,
    val count: Int
)

@Serializable
data class SearchItemResponse(
    val entityKey: String,
    val tenantId: String,
    val type: String
)

@Serializable
data class AutocompleteResponse(
    val prefix: String,
    val suggestions: List<String>
)

@Serializable
data class DiffResponse(
    val entityKey: String,
    val fromVersion: Long,
    val toVersion: Long,
    val fromData: JsonElement?,
    val toData: JsonElement?,
    val changes: List<DiffChangeResponse>
)

@Serializable
data class DiffChangeResponse(
    val path: String,
    val type: String,
    val oldValue: String?,
    val newValue: String?
)

// ==================== Domain → Response 변환 ====================

private fun IngestResult.toResponse() = IngestResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    schemaId = schemaId,
    schemaVersion = schemaVersion,
    payloadHash = payloadHash,
    compiled = compiled,
    slicesCreated = slicesCreated,
    timestamp = timestamp
)

private fun BatchIngestResult.toResponse() = BatchIngestResponse(
    tenantId = tenantId,
    succeeded = succeeded.map { it.toResponse() },
    failed = failed.map { IngestErrorResponse(it.entityKey, it.error) },
    totalCount = totalCount,
    successCount = successCount,
    failCount = failCount
)

private fun RawDataResult.toResponse() = RawDataResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    schemaId = schemaId,
    schemaVersion = schemaVersion,
    payload = payload,
    payloadRaw = payloadRaw,
    payloadHash = payloadHash,
    versions = versions.map { it.toResponse() }
)

private fun VersionInfo.toResponse() = VersionInfoResponse(
    version = version,
    createdAt = createdAt,
    hash = hash
)

private fun SlicesResult.toResponse() = SlicesResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    slices = slices.map { it.toResponse() },
    count = count
)

private fun ExplorerSliceItem.toResponse() = ExplorerSliceItemResponse(
    sliceType = sliceType,
    data = data,
    dataRaw = dataRaw,
    hash = hash,
    ruleSetId = ruleSetId,
    ruleSetVersion = ruleSetVersion,
    isDeleted = isDeleted
)

private fun SliceSearchResult.toResponse() = SliceSearchResponse(
    items = items.map { it.toResponse() },
    nextCursor = nextCursor,
    hasMore = hasMore
)

private fun ViewResult.toResponse() = ViewResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    viewDefId = viewDefId,
    data = data,
    dataRaw = dataRaw,
    slicesUsed = slicesUsed,
    version = version,
    assembledAt = assembledAt
)

private fun LineageResult.toResponse() = LineageResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    nodes = nodes.map { it.toResponse() },
    edges = edges.map { it.toResponse() }
)

private fun LineageNode.toResponse() = LineageNodeResponse(
    id = id,
    type = type,
    label = label,
    metadata = metadata
)

private fun LineageEdge.toResponse() = LineageEdgeResponse(
    id = id,
    source = source,
    target = target,
    label = label
)

private fun SearchResult.toResponse() = SearchResponse(
    query = query,
    items = items.map { it.toResponse() },
    count = count
)

private fun SearchItem.toResponse() = SearchItemResponse(
    entityKey = entityKey,
    tenantId = tenantId,
    type = type
)

private fun VersionDiffResult.toResponse() = DiffResponse(
    entityKey = entityKey,
    fromVersion = fromVersion,
    toVersion = toVersion,
    fromData = fromData,
    toData = toData,
    changes = changes.map { it.toResponse() }
)

private fun DiffChange.toResponse() = DiffChangeResponse(
    path = path,
    type = type,
    oldValue = oldValue,
    newValue = newValue
)

private fun RawDataListResult.toResponse() = RawDataListResponse(
    entries = entries.map { it.toResponse() },
    total = total,
    hasMore = hasMore,
    nextCursor = nextCursor
)

private fun RawDataListItem.toResponse() = RawDataListItemResponse(
    entityId = entityId,
    version = version,
    schemaRef = schemaRef,
    updatedAt = updatedAt
)

// ==================== RawData List Response DTOs ====================

@Serializable
data class RawDataListResponse(
    val entries: List<RawDataListItemResponse>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class RawDataListItemResponse(
    val entityId: String,
    val version: Long,
    val schemaRef: String,
    val updatedAt: String?
)

// ==================== Slice List By Type Response DTOs ====================

@Serializable
data class SliceTypesResponse(
    val tenantId: String,
    val types: List<SliceTypeInfoResponse>,
    val total: Int
)

@Serializable
data class SliceTypeInfoResponse(
    val type: String,
    val count: Int
)

@Serializable
data class SliceListByTypeResponse(
    val tenantId: String,
    val sliceType: String,
    val entries: List<SliceListItemResponse>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class SliceListItemResponse(
    val entityId: String,
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

// ==================== Slice Type Conversions ====================

private fun SliceTypesResult.toResponse() = SliceTypesResponse(
    tenantId = tenantId,
    types = types.map { it.toResponse() },
    total = total
)

private fun SliceTypeInfo.toResponse() = SliceTypeInfoResponse(
    type = type,
    count = count
)

private fun SliceListByTypeResult.toResponse() = SliceListByTypeResponse(
    tenantId = tenantId,
    sliceType = sliceType,
    entries = entries.map { it.toResponse() },
    total = total,
    hasMore = hasMore,
    nextCursor = nextCursor
)

private fun SliceListItem.toResponse() = SliceListItemResponse(
    entityId = entityId,
    sliceType = sliceType,
    version = version,
    data = data,
    dataRaw = dataRaw,
    hash = hash,
    ruleSetId = ruleSetId,
    ruleSetVersion = ruleSetVersion,
    isDeleted = isDeleted,
    updatedAt = updatedAt
)
