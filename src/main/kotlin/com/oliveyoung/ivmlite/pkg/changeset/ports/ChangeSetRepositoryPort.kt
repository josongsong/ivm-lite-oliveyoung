package com.oliveyoung.ivmlite.pkg.changeset.ports

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * ChangeSet Repository Port
 *
 * RawData 변경 시 생성되는 ChangeSet을 저장/조회하는 포트.
 * v1.1에서 ImpactMap 기반 변경 추적에 사용.
 */
interface ChangeSetRepositoryPort {

    // ==================== 저장 ====================

    /**
     * ChangeSet 저장 (멱등성: 같은 ID + 같은 hash면 OK)
     */
    suspend fun save(changeSet: ChangeSet): Result<ChangeSet>

    // ==================== 조회 ====================

    /**
     * ID로 조회
     */
    suspend fun findById(changeSetId: String): Result<ChangeSet>

    /**
     * 특정 엔티티의 ChangeSet 조회 (toVersion 내림차순)
     */
    suspend fun findByEntity(tenantId: TenantId, entityKey: EntityKey): Result<List<ChangeSet>>

    /**
     * 버전 범위로 조회 (fromVersion < toVersion <= toVersionParam)
     */
    suspend fun findByVersionRange(
        tenantId: TenantId,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
    ): Result<List<ChangeSet>>

    /**
     * 가장 최신 ChangeSet 조회
     */
    suspend fun findLatest(tenantId: TenantId, entityKey: EntityKey): Result<ChangeSet>

    /**
     * ChangeType으로 필터 조회
     */
    suspend fun findByChangeType(
        tenantId: TenantId,
        changeType: ChangeType,
        limit: Int,
    ): Result<List<ChangeSet>>

    // ==================== Result 타입 ====================

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
