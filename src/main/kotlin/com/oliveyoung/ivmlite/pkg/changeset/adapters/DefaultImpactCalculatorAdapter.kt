package com.oliveyoung.ivmlite.pkg.changeset.adapters

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.pkg.changeset.ports.ImpactCalculatorPort
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract

/**
 * RFC-IMPL-010: ImpactCalculator Adapter
 *
 * ImpactCalculatorPort 구현체.
 * 도메인 서비스 ImpactCalculator를 래핑하여 Port 인터페이스 제공.
 */
class DefaultImpactCalculatorAdapter(
    private val delegate: ImpactCalculator,
) : ImpactCalculatorPort {

    override fun calculate(
        changeSet: ChangeSet,
        ruleSet: RuleSetContract,
    ): Map<String, ImpactDetail> {
        return delegate.calculate(changeSet, ruleSet)
    }
}
