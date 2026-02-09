package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.apps.admin.config.AdminConstants
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * Slice 탐색 서비스
 *
 * P0: SRP 준수 - Slice 관련 기능만 담당
 * - 목록 조회
 * - 타입별 조회
 * - 검색
 */
class SliceExplorerService(
    private val sliceRepo: SliceRepositoryPort,
    private val explorerRepo: ExplorerRepositoryPort
) {

    /**
     * 특정 엔티티의 Slice 목록 조회
     */
    suspend fun getSlices(
        tenantId: String,
        entityKey: String,
        version: Long? = null,
        sliceType: String? = null
    ): Result<SlicesResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return Result.catch {
            val slices = if (version != null) {
                when (val result = sliceRepo.getByVersion(tenant, entity, version)) {
                    is Result.Ok -> result.value
                    is Result.Err -> throw result.error
                }
            } else {
                when (val result = sliceRepo.getLatestVersion(tenant, entity)) {
                    is Result.Ok -> result.value
                    is Result.Err -> throw result.error
                }
            }

            val filteredSlices = filterBySliceType(slices, sliceType)

            SlicesResult(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version ?: slices.firstOrNull()?.version ?: 0L,
                slices = filteredSlices.map { it.toExplorerSliceItem() },
                count = filteredSlices.size
            )
        }
    }

    private fun filterBySliceType(slices: List<SliceRecord>, sliceType: String?): List<SliceRecord> {
        if (sliceType == null) return slices
        val targetType = SliceType.fromDbValueOrNull(sliceType) ?: return slices
        return slices.filter { it.sliceType == targetType }
    }

    /**
     * Slice Range Query (프리픽스 검색)
     */
    suspend fun searchSlices(
        tenantId: String,
        keyPrefix: String,
        sliceType: String? = null,
        limit: Int = AdminConstants.DEFAULT_PAGE_SIZE,
        cursor: String? = null
    ): Result<SliceSearchResult> {
        val tenant = TenantId(tenantId)
        val type = sliceType?.let { SliceType.fromDbValueOrNull(it) }

        return Result.catch {
            when (val res = sliceRepo.findByKeyPrefix(tenant, keyPrefix, type, limit, cursor)) {
                is Result.Ok -> SliceSearchResult(
                    items = res.value.items.map { it.toExplorerSliceItem() },
                    nextCursor = res.value.nextCursor,
                    hasMore = res.value.hasMore
                )
                is Result.Err -> throw res.error
            }
        }
    }

    /**
     * 슬라이스 타입별 전체 목록 조회
     */
    suspend fun listSlicesByType(
        tenantId: String,
        sliceType: String,
        limit: Int = AdminConstants.DEFAULT_PAGE_SIZE,
        cursor: String? = null
    ): Result<SliceListByTypeResult> {
        val safeLimit = limit.coerceIn(1, AdminConstants.MAX_PAGE_SIZE)
        val tenant = TenantId(tenantId)
        val type = SliceType.fromDbValueOrNull(sliceType)
            ?: return Result.Err(DomainError.ValidationError("sliceType", "Invalid slice type: $sliceType"))

        return explorerRepo.listSlicesByType(tenant, type, safeLimit, cursor)
            .fold(
                { error -> Result.Err(error) },
                { result ->
                    Result.Ok(
                        SliceListByTypeResult(
                            tenantId = tenantId,
                            sliceType = sliceType,
                            entries = result.items.map { item ->
                                SliceListItem(
                                    entityKey = item.sourceKey,
                                    sliceType = item.sliceType,
                                    version = item.version,
                                    data = null, // 리스트에서는 데이터 생략
                                    dataRaw = "",
                                    hash = "",
                                    ruleSetId = "",
                                    ruleSetVersion = "",
                                    isDeleted = false,
                                    updatedAt = item.updatedAt
                                )
                            },
                            total = result.totalCount ?: result.items.size,
                            hasMore = result.nextCursor != null,
                            nextCursor = result.nextCursor
                        )
                    )
                }
            )
    }

    /**
     * 사용 가능한 슬라이스 타입 목록 조회
     */
    suspend fun getSliceTypes(tenantId: String): Result<SliceTypesResult> {
        val tenant = TenantId(tenantId)

        return explorerRepo.getSliceTypes(tenant)
            .fold(
                { error -> Result.Err(error) },
                { types ->
                    Result.Ok(
                        SliceTypesResult(
                            tenantId = tenantId,
                            types = types.map { SliceTypeInfo(it.sliceType, it.count) },
                            total = types.size
                        )
                    )
                }
            )
    }

    private fun SliceRecord.toExplorerSliceItem(): ExplorerSliceItem {
        return ExplorerSliceItem(
            sliceType = this.sliceType.toDbValue(),
            data = ExplorerUtils.parseJsonSafe(this.data),
            dataRaw = this.data,
            hash = this.hash,
            ruleSetId = this.ruleSetId,
            ruleSetVersion = this.ruleSetVersion.toString(),
            isDeleted = this.isDeleted
        )
    }

}
