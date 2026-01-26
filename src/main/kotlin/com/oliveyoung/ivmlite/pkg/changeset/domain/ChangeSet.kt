package com.oliveyoung.ivmlite.pkg.changeset.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

data class ChangeSet(
    val changeSetId: String,
    val tenantId: TenantId,
    val entityType: String,
    val entityKey: EntityKey,
    val fromVersion: Long,
    val toVersion: Long,
    val changeType: ChangeType,
    val changedPaths: List<ChangedPath>,
    val impactedSliceTypes: Set<String>,
    val impactMap: Map<String, ImpactDetail>,
    val payloadHash: String,
)

enum class ChangeType { CREATE, UPDATE, DELETE, NO_CHANGE }

data class ChangedPath(
    val path: String,
    val valueHash: String,
)

data class ImpactDetail(
    val reason: String,
    val paths: List<String>,
    val notes: String? = null,
)
