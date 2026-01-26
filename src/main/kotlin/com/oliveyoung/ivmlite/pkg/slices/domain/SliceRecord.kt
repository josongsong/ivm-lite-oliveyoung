package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * DynamoDB catalog-slices Single Table item의 Domain 표현.
 * data/payload는 adapter에서 string으로 serialize되고, domain은 hash를 통해 결정성만 보장한다.
 */
data class SliceRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val sliceType: SliceType,
    val data: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: SemVer,
    /** RFC-IMPL-010 D-1: tombstone=null → 일반 slice, 존재 시 삭제된 slice */
    val tombstone: Tombstone? = null,
) {
    /** 삭제된 Slice 여부 */
    val isDeleted: Boolean get() = tombstone?.isDeleted == true
}
