package com.oliveyoung.ivmlite.pkg.fanout.adapters

import com.oliveyoung.ivmlite.pkg.fanout.ports.FanoutSlicingPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * SlicingWorkflow를 FanoutSlicingPort로 래핑하는 어댑터
 *
 * RFC-V4-010: FanoutWorkflow가 SlicingWorkflow를 직접 호출하지 않고 Port 경유.
 */
class SlicingWorkflowFanoutAdapter(
    private val slicingWorkflow: SlicingWorkflow,
) : FanoutSlicingPort {

    override suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): Result<List<SliceRepositoryPort.SliceKey>> =
        slicingWorkflow.execute(tenantId, entityKey, version)
}
