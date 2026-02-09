package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * Explorer Facade
 *
 * P0: Facade 패턴 - 기존 ExplorerService API 호환 유지
 * 내부적으로 분할된 서비스들에 위임
 *
 * 구성:
 * - RawDataExplorerService: RawData 등록/조회
 * - SliceExplorerService: Slice 조회/검색
 * - ViewExplorerService: View 조합
 * - LineageService: 데이터 흐름 그래프
 * - SearchService: 통합 검색/자동완성
 * - DiffService: 버전 비교
 */
class ExplorerFacade(
    private val rawDataExplorer: RawDataExplorerService,
    private val sliceExplorer: SliceExplorerService,
    private val viewExplorer: ViewExplorerService,
    private val lineageService: LineageService,
    private val searchService: SearchService,
    private val diffService: DiffService
) {
    // ==================== RawData ====================

    suspend fun ingest(
        tenantId: String,
        entityKey: String,
        schemaId: String,
        schemaVersion: String = "1.0.0",
        payload: String,
        compile: Boolean = false
    ): Result<IngestResult> = rawDataExplorer.ingest(tenantId, entityKey, schemaId, schemaVersion, payload, compile)

    suspend fun ingestBatch(
        tenantId: String,
        items: List<IngestItem>
    ): Result<BatchIngestResult> = rawDataExplorer.ingestBatch(tenantId, items)

    suspend fun listRawData(
        tenantId: String,
        entityPrefix: String? = null,
        limit: Int = 50,
        cursor: String? = null
    ): Result<RawDataListResult> = rawDataExplorer.listRawData(tenantId, entityPrefix, limit, cursor)

    suspend fun getRawData(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): Result<RawDataResult> = rawDataExplorer.getRawData(tenantId, entityKey, version)

    // ==================== Slice ====================

    suspend fun getSlices(
        tenantId: String,
        entityKey: String,
        version: Long? = null,
        sliceType: String? = null
    ): Result<SlicesResult> = sliceExplorer.getSlices(tenantId, entityKey, version, sliceType)

    suspend fun searchSlices(
        tenantId: String,
        keyPrefix: String,
        sliceType: String? = null,
        limit: Int = 50,
        cursor: String? = null
    ): Result<SliceSearchResult> = sliceExplorer.searchSlices(tenantId, keyPrefix, sliceType, limit, cursor)

    suspend fun listSlicesByType(
        tenantId: String,
        sliceType: String,
        limit: Int = 50,
        cursor: String? = null
    ): Result<SliceListByTypeResult> = sliceExplorer.listSlicesByType(tenantId, sliceType, limit, cursor)

    suspend fun getSliceTypes(tenantId: String): Result<SliceTypesResult> = sliceExplorer.getSliceTypes(tenantId)

    // ==================== View ====================

    suspend fun getView(
        tenantId: String,
        entityKey: String,
        viewDefId: String
    ): Result<ViewResult> = viewExplorer.getView(tenantId, entityKey, viewDefId)

    // ==================== Lineage ====================

    suspend fun getLineage(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): Result<LineageResult> = lineageService.getLineage(tenantId, entityKey, version)

    // ==================== Search ====================

    suspend fun search(
        tenantId: String,
        query: String,
        limit: Int = 20
    ): Result<SearchResult> = searchService.search(tenantId, query, limit)

    suspend fun autocomplete(
        tenantId: String,
        prefix: String,
        limit: Int = 10
    ): Result<List<String>> = searchService.autocomplete(tenantId, prefix, limit)

    // ==================== Diff ====================

    suspend fun diffVersions(
        tenantId: String,
        entityKey: String,
        fromVersion: Long,
        toVersion: Long
    ): Result<VersionDiffResult> = diffService.diffVersions(tenantId, entityKey, fromVersion, toVersion)
}
