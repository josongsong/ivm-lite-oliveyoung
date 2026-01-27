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

    /**
     * RFC-IMPL-011 Wave 6: Range Query - 키 프리픽스로 조회
     * @param tenantId 테넌트 ID
     * @param keyPrefix entityKey 프리픽스
     * @param sliceType Slice 타입 (null이면 전체)
     * @param limit 최대 결과 수 (기본 100)
     * @param cursor 페이지네이션 커서 (다음 페이지 시작점)
     * @return Slice 목록과 다음 커서
     */
    suspend fun findByKeyPrefix(
        tenantId: TenantId,
        keyPrefix: String,
        sliceType: SliceType? = null,
        limit: Int = 100,
        cursor: String? = null,
    ): Result<RangeQueryResult>

    /**
     * RFC-IMPL-011 Wave 6: Count - 조건에 맞는 Slice 개수
     * @param tenantId 테넌트 ID
     * @param keyPrefix entityKey 프리픽스 (null이면 전체)
     * @param sliceType Slice 타입 (null이면 전체)
     * @return 개수
     */
    suspend fun count(
        tenantId: TenantId,
        keyPrefix: String? = null,
        sliceType: SliceType? = null,
    ): Result<Long>

    /**
     * RFC-IMPL-011 Wave 6: 최신 버전 조회
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param sliceType Slice 타입 (null이면 전체)
     * @return 해당 엔티티의 최신 버전 Slice들
     */
    suspend fun getLatestVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        sliceType: SliceType? = null,
    ): Result<List<SliceRecord>>

    data class SliceKey(val tenantId: TenantId, val entityKey: EntityKey, val version: Long, val sliceType: SliceType)

    data class RangeQueryResult(
        val items: List<SliceRecord>,
        val nextCursor: String?,
        val hasMore: Boolean
    )

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
