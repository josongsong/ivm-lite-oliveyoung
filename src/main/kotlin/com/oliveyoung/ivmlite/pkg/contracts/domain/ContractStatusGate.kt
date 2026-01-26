package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.slf4j.LoggerFactory

/**
 * 계약 상태 검증 게이트 (RFC-003)
 *
 * fail-closed: 비활성 계약(DRAFT/ARCHIVED)은 운영 진입 차단
 * 관찰성: DEPRECATED 사용 시 경고 로그
 */
interface ContractStatusGate {
    fun check(contractId: String, status: ContractStatus): GateResult

    sealed class GateResult {
        object Ok : GateResult()
        data class Err(val error: DomainError.ContractStatusError) : GateResult()
    }
}

/**
 * RFC-003 기본 구현:
 * - ACTIVE: 허용
 * - DEPRECATED: 경고 로그 + 허용
 * - DRAFT/ARCHIVED: 차단
 */
object DefaultContractStatusGate : ContractStatusGate {

    private val logger = LoggerFactory.getLogger(DefaultContractStatusGate::class.java)

    override fun check(contractId: String, status: ContractStatus): ContractStatusGate.GateResult {
        return when (status) {
            ContractStatus.ACTIVE -> ContractStatusGate.GateResult.Ok

            ContractStatus.DEPRECATED -> {
                logger.warn("Contract '$contractId' is DEPRECATED. Consider migrating to a newer version.")
                ContractStatusGate.GateResult.Ok
            }

            ContractStatus.DRAFT, ContractStatus.ARCHIVED -> {
                ContractStatusGate.GateResult.Err(
                    DomainError.ContractStatusError(contractId, status)
                )
            }
        }
    }
}

/**
 * 테스트용: 모든 상태 허용
 */
object AllowAllStatusGate : ContractStatusGate {
    override fun check(contractId: String, status: ContractStatus): ContractStatusGate.GateResult {
        return ContractStatusGate.GateResult.Ok
    }
}
