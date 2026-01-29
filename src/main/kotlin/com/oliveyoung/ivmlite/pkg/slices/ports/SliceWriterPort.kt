package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord

/**
 * SliceWriterPort - Slice 쓰기 전용 인터페이스
 * 
 * SOLID 원칙 적용:
 * - Interface Segregation: 읽기/쓰기 분리
 * - 쓰기만 필요한 컴포넌트는 이 인터페이스만 의존
 * 
 * 사용처:
 * - SlicingWorkflow: Slice 생성 및 저장
 * - BackfillService: Slice 재생성
 */
interface SliceWriterPort {
    
    /**
     * 멱등성 보장 저장 (배치)
     */
    suspend fun putAllIdempotent(slices: List<SliceRecord>): SliceRepositoryPort.Result<Unit>
}
