package com.oliveyoung.ivmlite.pkg.changeset.adapters

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory ChangeSet Repository (테스트/로컬 개발용)
 * RFC-IMPL-010 GAP-G: HealthCheckable 구현
 */
class InMemoryChangeSetRepository : ChangeSetRepositoryPort, HealthCheckable {

    override val healthName: String = "changeset-repo"

    override suspend fun healthCheck(): Boolean = true  // InMemory는 항상 정상

    private val store = ConcurrentHashMap<String, ChangeSet>()

    override suspend fun save(changeSet: ChangeSet): ChangeSetRepositoryPort.Result<ChangeSet> {
        val prev = store.putIfAbsent(changeSet.changeSetId, changeSet)
        return when {
            prev == null -> ChangeSetRepositoryPort.Result.Ok(changeSet)
            prev.payloadHash == changeSet.payloadHash -> ChangeSetRepositoryPort.Result.Ok(changeSet)
            else -> ChangeSetRepositoryPort.Result.Err(
                DomainError.IdempotencyViolation(
                    "ChangeSet hash mismatch for ${changeSet.changeSetId}",
                ),
            )
        }
    }

    override suspend fun findById(changeSetId: String): ChangeSetRepositoryPort.Result<ChangeSet> {
        val cs = store[changeSetId]
            ?: return ChangeSetRepositoryPort.Result.Err(
                DomainError.NotFoundError("ChangeSet", changeSetId),
            )
        return ChangeSetRepositoryPort.Result.Ok(cs)
    }

    override suspend fun findByEntity(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): ChangeSetRepositoryPort.Result<List<ChangeSet>> {
        val list = store.values
            .filter { it.tenantId == tenantId && it.entityKey == entityKey }
            .sortedByDescending { it.toVersion }
        return ChangeSetRepositoryPort.Result.Ok(list)
    }

    override suspend fun findByVersionRange(
        tenantId: TenantId,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
    ): ChangeSetRepositoryPort.Result<List<ChangeSet>> {
        val list = store.values
            .filter {
                it.tenantId == tenantId &&
                    it.entityKey == entityKey &&
                    it.fromVersion >= fromVersion &&
                    it.toVersion <= toVersion
            }
            .sortedBy { it.toVersion }
        return ChangeSetRepositoryPort.Result.Ok(list)
    }

    override suspend fun findLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): ChangeSetRepositoryPort.Result<ChangeSet> {
        val latest = store.values
            .filter { it.tenantId == tenantId && it.entityKey == entityKey }
            .maxByOrNull { it.toVersion }
            ?: return ChangeSetRepositoryPort.Result.Err(
                DomainError.NotFoundError("ChangeSet", "${tenantId.value}:${entityKey.value}"),
            )
        return ChangeSetRepositoryPort.Result.Ok(latest)
    }

    override suspend fun findByChangeType(
        tenantId: TenantId,
        changeType: ChangeType,
        limit: Int,
    ): ChangeSetRepositoryPort.Result<List<ChangeSet>> {
        val list = store.values
            .filter { it.tenantId == tenantId && it.changeType == changeType }
            .sortedByDescending { it.toVersion }
            .take(limit)
        return ChangeSetRepositoryPort.Result.Ok(list)
    }

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
}
