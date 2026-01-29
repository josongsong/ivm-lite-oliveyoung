package com.oliveyoung.ivmlite.pkg.changeset.ports

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * RFC-IMPL-010: ChangeSetBuilder Port
 *
 * JSON Pointer 기반 diff로 ChangeSet을 생성하는 Port 인터페이스.
 * Orchestration에서 도메인 서비스 직접 참조를 제거하기 위한 추상화.
 *
 * - 결정성: 동일 입력 → 동일 ChangeSet (ID 포함)
 * - changeSetId는 입력값의 hash로 결정적 생성
 * - UUID.randomUUID() 사용 금지
 */
interface ChangeSetBuilderPort {

    /**
     * ChangeSet 생성
     *
     * @param tenantId 테넌트 ID
     * @param entityType 엔티티 타입
     * @param entityKey 엔티티 키
     * @param fromVersion 이전 버전
     * @param toVersion 신규 버전
     * @param fromPayload 이전 payload (없으면 CREATE)
     * @param toPayload 신규 payload (없으면 DELETE)
     * @param impactedSliceTypes 영향받은 SliceType 집합
     * @param impactMap 영향 맵
     * @return ChangeSet
     */
    fun build(
        tenantId: TenantId,
        entityType: String,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
        fromPayload: String?,
        toPayload: String?,
        impactedSliceTypes: Set<String>,
        impactMap: Map<String, ImpactDetail>,
    ): ChangeSet
}
