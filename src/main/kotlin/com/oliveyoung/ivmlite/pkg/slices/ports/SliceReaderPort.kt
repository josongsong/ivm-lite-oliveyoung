package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * SliceReaderPort - Slice 읽기 전용 인터페이스
 * 
 * SOLID 원칙 적용:
 * - Interface Segregation: 읽기/쓰기 분리
 * - 읽기만 필요한 컴포넌트는 이 인터페이스만 의존
 * 
 * 사용처:
 * - QueryViewWorkflow: View 조회 시 Slice 읽기
 * - ShipWorkflow: Sink 전송 전 Slice 읽기
 */
interface SliceReaderPort {
    
    /**
     * 배치 조회
     */
    suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceRepositoryPort.SliceKey>,
        includeTombstones: Boolean = false,
    ): Result<List<SliceRecord>>
    
    /**
     * 특정 버전의 모든 Slice 조회
     */
    suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean = false,
    ): Result<List<SliceRecord>>
    
    /**
     * Range Query - 키 프리픽스로 조회
     */
    suspend fun findByKeyPrefix(
        tenantId: TenantId,
        keyPrefix: String,
        sliceType: SliceType? = null,
        limit: Int = 100,
        cursor: String? = null,
    ): Result<SliceRepositoryPort.RangeQueryResult>
    
    /**
     * Count - 조건에 맞는 Slice 개수
     */
    suspend fun count(
        tenantId: TenantId,
        keyPrefix: String? = null,
        sliceType: SliceType? = null,
    ): Result<Long>
    
    /**
     * 최신 버전 조회
     */
    suspend fun getLatestVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        sliceType: SliceType? = null,
    ): Result<List<SliceRecord>>
}
