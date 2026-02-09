package com.oliveyoung.ivmlite.pkg.contracts.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable

/**
 * 상태 검증 게이트 래퍼 (Decorator 패턴)
 *
 * RFC-003: 모든 계약 로드 시 상태 검증
 * - delegate 호출 → 결과 수신 → gate 검증
 * - gate 실패 시 Err 반환
 */
class GatedContractRegistryAdapter(
    private val delegate: ContractRegistryPort,
    private val statusGate: ContractStatusGate,
) : ContractRegistryPort, HealthCheckable {

    override val healthName: String = "contract-registry-gated"

    override suspend fun healthCheck(): Boolean {
        return (delegate as? HealthCheckable)?.healthCheck() ?: true
    }

    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        return gateCheck(delegate.loadChangeSetContract(ref)) { it.meta }
    }

    override suspend fun loadJoinSpecContract(ref: ContractRef): Result<JoinSpecContract> {
        return gateCheck(delegate.loadJoinSpecContract(ref)) { it.meta }
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     * RuleSet.indexes의 IndexSpec.references로 통합되었습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    override suspend fun loadInvertedIndexContract(ref: ContractRef): Result<InvertedIndexContract> {
        return gateCheck(delegate.loadInvertedIndexContract(ref)) { it.meta }
    }

    override suspend fun loadRuleSetContract(ref: ContractRef): Result<RuleSetContract> {
        return gateCheck(delegate.loadRuleSetContract(ref)) { it.meta }
    }

    override suspend fun loadViewDefinitionContract(ref: ContractRef): Result<ViewDefinitionContract> {
        return gateCheck(delegate.loadViewDefinitionContract(ref)) { it.meta }
    }

    private inline fun <T> gateCheck(
        result: Result<T>,
        getMeta: (T) -> ContractMeta,
    ): Result<T> {
        return when (result) {
            is Result.Err -> result
            is Result.Ok -> {
                val meta = getMeta(result.value)
                when (val gateResult = statusGate.check(meta.id, meta.status)) {
                    is ContractStatusGate.GateResult.Ok -> result
                    is ContractStatusGate.GateResult.Err -> Result.Err(gateResult.error)
                }
            }
        }
    }
}
