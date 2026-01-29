package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord

/**
 * SliceRepositoryPort - Slice 저장소 통합 인터페이스
 * 
 * SOLID 원칙 적용:
 * - Interface Segregation: SliceReaderPort + SliceWriterPort 분리
 * - 읽기만 필요하면 SliceReaderPort, 쓰기만 필요하면 SliceWriterPort 사용 가능
 * - 기존 호환성을 위해 통합 인터페이스도 제공
 * 
 * NOTE: 메서드는 SliceReaderPort/SliceWriterPort에서 상속
 *       여기서는 공통 타입만 정의
 */
interface SliceRepositoryPort : SliceReaderPort, SliceWriterPort {
    
    // ===== 공통 타입 정의 =====
    
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
