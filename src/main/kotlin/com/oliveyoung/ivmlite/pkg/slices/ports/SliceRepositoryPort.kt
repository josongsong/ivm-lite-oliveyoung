package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord

interface SliceRepositoryPort {
    suspend fun putAllIdempotent(slices: List<SliceRecord>): Result<Unit>

    /**
     * 배치 조회
     * @param includeTombstones true면 tombstone 포함, false면 제외 (기본값: false)
     */
    suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceKey>,
        includeTombstones: Boolean = false,
    ): Result<List<SliceRecord>>

    /**
     * RFC-IMPL-010 D-8: 특정 버전의 모든 Slice 조회 (INCREMENTAL 슬라이싱용)
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param version 버전
     * @param includeTombstones true면 tombstone 포함, false면 제외 (기본값: false)
     * @return 해당 버전의 모든 SliceRecord 목록
     */
    suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean = false,
    ): Result<List<SliceRecord>>

    data class SliceKey(val tenantId: TenantId, val entityKey: EntityKey, val version: Long, val sliceType: SliceType)

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
