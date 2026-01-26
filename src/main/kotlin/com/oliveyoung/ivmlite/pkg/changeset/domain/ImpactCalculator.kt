package com.oliveyoung.ivmlite.pkg.changeset.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * RFC-IMPL-010 Phase D-7: ImpactCalculator
 *
 * ChangeSet + RuleSet → ImpactMap 계산
 * - changedPaths와 RuleSet.impactMap 매칭
 * - 매칭 안 된 변경 경로 → fail-closed (UnmappedChangePathError)
 * - 결정성: 동일 ChangeSet + RuleSet → 동일 ImpactMap
 * - Contract is Law: RuleSet.impactMap이 영향 범위의 SSOT
 */
class ImpactCalculator {

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
    ): Map<String, ImpactDetail> {
        // 빈 changedPaths → 빈 impactMap
        if (changeSet.changedPaths.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, ImpactDetail>()

        // 각 슬라이스 타입별로 영향 받는 경로 매칭
        for ((sliceType, impactPaths) in ruleSet.impactMap) {
            val matchedPaths = changeSet.changedPaths
                .filter { changed ->
                    impactPaths.any { impactPath ->
                        matchesPath(changed.path, impactPath)
                    }
                }

            if (matchedPaths.isNotEmpty()) {
                result[sliceType.name] = ImpactDetail(
                    reason = "FIELD_CHANGE",
                    paths = matchedPaths.map { it.path }.distinct(),
                )
            }
        }

        // fail-closed: 매칭 안 된 경로 체크
        val allImpactPaths = ruleSet.impactMap.values.flatten().toSet()
        val unmatchedPaths = changeSet.changedPaths
            .filter { changed ->
                allImpactPaths.none { impactPath ->
                    matchesPath(changed.path, impactPath)
                }
            }

        if (unmatchedPaths.isNotEmpty()) {
            throw DomainError.UnmappedChangePathError(unmatchedPaths.map { it.path })
        }

        return result
    }

    /**
     * JSON Pointer 경로 매칭 (RFC 6901)
     *
     * BUG FIX: trailing slash 처리
     * - impactPath: "/brand/" → normalize → "/brand"
     * - changedPath: "/brand/name" → matches!
     *
     * @param changedPath 변경된 경로
     * @param impactPath 영향 범위 경로 (RuleSet.impactMap에서)
     * @return 매칭 여부
     */
    private fun matchesPath(changedPath: String, impactPath: String): Boolean {
        // trailing slash 제거 (normalize)
        val normalizedImpact = impactPath.trimEnd('/')

        // 1. 정확히 일치
        if (changedPath == normalizedImpact) return true

        // 2. prefix 매칭 (하위 경로)
        //    /brand/name은 /brand에 매칭
        //    /brandnew는 /brand에 매칭 안 됨 (슬래시 필수)
        return changedPath.startsWith("$normalizedImpact/")
    }
}
