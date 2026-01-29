package com.oliveyoung.ivmlite.pkg.changeset.ports

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * RFC-IMPL-010: ImpactCalculator Port
 *
 * ChangeSet + RuleSet → ImpactMap 계산하는 Port 인터페이스.
 * Orchestration에서 도메인 서비스 직접 참조를 제거하기 위한 추상화.
 *
 * - 결정성: 동일 ChangeSet + RuleSet → 동일 ImpactMap
 * - fail-closed: 매핑 안 된 변경 경로 → UnmappedChangePathError
 * - Contract is Law: RuleSet.impactMap이 영향 범위의 SSOT
 */
interface ImpactCalculatorPort {

    /**
     * ChangeSet과 RuleSet을 기반으로 ImpactMap 계산
     *
     * @param changeSet 변경 세트
     * @param ruleSet 규칙 세트 계약
     * @return ImpactMap (SliceType → ImpactDetail)
     * @throws DomainError.UnmappedChangePathError 매핑되지 않은 변경 경로가 있을 경우
     */
    fun calculate(
        changeSet: ChangeSet,
        ruleSet: RuleSetContract,
    ): Map<String, ImpactDetail>
}
