package com.oliveyoung.ivmlite.pkg.changeset.adapters

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetBuilderPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * RFC-IMPL-010: ChangeSetBuilder Adapter
 *
 * ChangeSetBuilderPort 구현체.
 * 도메인 서비스 ChangeSetBuilder를 래핑하여 Port 인터페이스 제공.
 */
class DefaultChangeSetBuilderAdapter(
    private val delegate: ChangeSetBuilder,
) : ChangeSetBuilderPort {

    override fun build(
        tenantId: TenantId,
        entityType: String,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
        fromPayload: String?,
        toPayload: String?,
        impactedSliceTypes: Set<String>,
        impactMap: Map<String, ImpactDetail>,
    ): ChangeSet {
        return delegate.build(
            tenantId = tenantId,
            entityType = entityType,
            entityKey = entityKey,
            fromVersion = fromVersion,
            toVersion = toVersion,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = impactedSliceTypes,
            impactMap = impactMap,
        )
    }
}
