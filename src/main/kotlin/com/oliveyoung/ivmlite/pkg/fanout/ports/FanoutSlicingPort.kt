package com.oliveyoung.ivmlite.pkg.fanout.ports

import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * Fanout Slicing Port - RFC-V4-010 준수
 *
 * FanoutWorkflow가 재슬라이싱을 수행할 때 사용하는 Port.
 * orchestration → orchestration 직접 호출을 피하기 위해 SlicingWorkflow를 Port 뒤에 숨김.
 *
 * @see com.oliveyoung.ivmlite.pkg.fanout.adapters.SlicingWorkflowFanoutAdapter
 */
interface FanoutSlicingPort {

    /**
     * 엔티티에 대한 FULL 슬라이싱 실행 (재슬라이싱)
     *
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param version 슬라이싱 대상 버전
     * @return 생성된 SliceKey 목록 또는 에러
     */
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): Result<List<SliceRepositoryPort.SliceKey>>
}
