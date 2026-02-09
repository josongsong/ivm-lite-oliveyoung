package com.oliveyoung.ivmlite.apps.admin.ports

import arrow.core.Either
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * Explorer 데이터 조회 Port
 *
 * P0: 헥사고날 아키텍처 준수
 * - Application 레이어에서 DynamoDB/jOOQ 직접 사용 금지
 * - 이 Port를 통해서만 데이터 접근
 */
interface ExplorerRepositoryPort {

    /**
     * RawData 목록 조회 (페이징)
     */
    suspend fun listRawData(
        tenantId: TenantId,
        entityType: String?,
        limit: Int,
        cursor: String?
    ): Either<DomainError, RawDataListResult>

    /**
     * Slice 목록 조회 (타입별)
     */
    suspend fun listSlicesByType(
        tenantId: TenantId,
        sliceType: SliceType,
        limit: Int,
        cursor: String?
    ): Either<DomainError, SliceListResult>

    /**
     * Slice 타입 목록 조회
     */
    suspend fun getSliceTypes(
        tenantId: TenantId
    ): Either<DomainError, List<SliceTypeInfo>>

    /**
     * 검색 (RawData + Slice + View)
     */
    suspend fun search(
        tenantId: TenantId,
        query: String,
        limit: Int
    ): Either<DomainError, SearchResults>

    /**
     * 자동완성
     */
    suspend fun autocomplete(
        tenantId: TenantId,
        prefix: String,
        limit: Int
    ): Either<DomainError, List<AutocompleteItem>>

    /**
     * 엔티티 검색 (간단한 반환 타입)
     */
    suspend fun searchEntities(
        tenantId: TenantId,
        query: String,
        limit: Int
    ): Either<DomainError, EntitySearchResult>

    /**
     * 특정 엔티티의 버전 히스토리 조회
     */
    suspend fun getVersionHistory(
        tenantId: TenantId,
        entityKey: EntityKey,
        limit: Int = 100
    ): Either<DomainError, List<VersionHistoryItem>>
}

// ==================== Result DTOs ====================

data class RawDataListResult(
    val items: List<RawDataItem>,
    val nextCursor: String?,
    val totalCount: Int?
)

data class RawDataItem(
    val entityKey: String,
    val schemaId: String,
    val version: Long,
    val updatedAt: String?
)

data class SliceListResult(
    val items: List<SliceItem>,
    val nextCursor: String?,
    val totalCount: Int?
)

data class SliceItem(
    val sliceId: String,
    val sliceType: String,
    val sourceKey: String,
    val version: Long,
    val updatedAt: String?
)

data class SliceTypeInfo(
    val sliceType: String,
    val count: Int
)

data class SearchResults(
    val rawData: List<RawDataItem>,
    val slices: List<SliceItem>,
    val views: List<ViewItem>
)

data class ViewItem(
    val viewId: String,
    val viewType: String,
    val sourceKeys: List<String>
)

data class AutocompleteItem(
    val value: String,
    val type: String,
    val label: String
)

data class EntitySearchResult(
    val items: List<EntitySearchItem>,
    val nextCursor: String?,
    val totalCount: Int?
)

data class EntitySearchItem(
    val entityKey: String,
    val type: String
)

data class VersionHistoryItem(
    val version: Long,
    val createdAt: String?,
    val payloadHash: String
)
