package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.domain.AllowAllStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * ContractStatusGate 단위 테스트 (RFC-IMPL-010 Phase D-6)
 *
 * RFC-003 상태 검증 규칙:
 * - ACTIVE: 허용
 * - DEPRECATED: 경고 로그 + 허용
 * - DRAFT: 차단
 * - ARCHIVED: 차단
 *
 * 수학적 완결성: |ContractStatus| = 4, 모든 케이스 검증
 */
class ContractStatusGateTest : StringSpec({

    val gate: ContractStatusGate = DefaultContractStatusGate
    val contractId = "test-contract.v1"

    // ==================== DefaultContractStatusGate 기본 테스트 ====================

    "ACTIVE 상태 → Ok" {
        val result = gate.check(contractId, ContractStatus.ACTIVE)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
    }

    "DEPRECATED 상태 → 경고 로그 + Ok" {
        val result = gate.check(contractId, ContractStatus.DEPRECATED)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
    }

    "DRAFT 상태 → Err(ContractStatusError)" {
        val result = gate.check(contractId, ContractStatus.DRAFT)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Err>()
        val err = (result as ContractStatusGate.GateResult.Err).error
        err.shouldBeInstanceOf<DomainError.ContractStatusError>()
        err.status shouldBe ContractStatus.DRAFT
        err.contractId shouldBe contractId
    }

    "ARCHIVED 상태 → Err(ContractStatusError)" {
        val result = gate.check(contractId, ContractStatus.ARCHIVED)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Err>()
        val err = (result as ContractStatusGate.GateResult.Err).error
        err.shouldBeInstanceOf<DomainError.ContractStatusError>()
        err.status shouldBe ContractStatus.ARCHIVED
        err.contractId shouldBe contractId
    }

    // ==================== 수학적 완결성: 모든 상태 전수 검증 ====================

    "수학적 완결성 - 모든 ContractStatus enum 값에 대해 결정적 결과" {
        val allowedStatuses = setOf(ContractStatus.ACTIVE, ContractStatus.DEPRECATED)
        val blockedStatuses = setOf(ContractStatus.DRAFT, ContractStatus.ARCHIVED)

        // 모든 enum 값이 두 집합 중 하나에 속함 (전수)
        ContractStatus.entries.forEach { status ->
            val result = gate.check("any-contract", status)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
            } else {
                result.shouldBeInstanceOf<ContractStatusGate.GateResult.Err>()
            }
        }

        // 합집합 = 전체 enum
        (allowedStatuses + blockedStatuses) shouldBe ContractStatus.entries.toSet()
    }

    // ==================== ContractStatusError 속성 테스트 ====================

    "ContractStatusError - errorCode는 ERR_CONTRACT_STATUS" {
        val error = DomainError.ContractStatusError("test.v1", ContractStatus.DRAFT)
        error.errorCode shouldBe "ERR_CONTRACT_STATUS"
    }

    "ContractStatusError - toHttpStatus는 400 (Bad Request)" {
        val error = DomainError.ContractStatusError("test.v1", ContractStatus.ARCHIVED)
        error.toHttpStatus() shouldBe 400
    }

    "ContractStatusError - message에 contractId와 status 포함" {
        val error = DomainError.ContractStatusError("my-contract.v2", ContractStatus.DRAFT)
        error.message shouldContain "my-contract.v2"
        error.message shouldContain "DRAFT"
    }

    // ==================== 엣지케이스: contractId 변형 ====================

    "엣지케이스 - 빈 contractId" {
        val result = gate.check("", ContractStatus.DRAFT)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Err>()
        val err = (result as ContractStatusGate.GateResult.Err).error
        err.contractId shouldBe ""
    }

    "엣지케이스 - 특수문자 포함 contractId" {
        val specialId = "contract#with@special!chars"
        val result = gate.check(specialId, ContractStatus.ARCHIVED)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Err>()
        val err = (result as ContractStatusGate.GateResult.Err).error
        err.contractId shouldBe specialId
    }

    "엣지케이스 - 매우 긴 contractId" {
        val longId = "a".repeat(1000)
        val result = gate.check(longId, ContractStatus.ACTIVE)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
    }

    // ==================== AllowAllStatusGate 테스트 ====================

    "AllowAllStatusGate - 모든 상태 허용" {
        val allowAllGate = AllowAllStatusGate
        ContractStatus.entries.forEach { status ->
            val result = allowAllGate.check("any-contract", status)
            result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
        }
    }

    "AllowAllStatusGate - DRAFT도 허용 (테스트 환경용)" {
        val allowAllGate = AllowAllStatusGate
        val result = allowAllGate.check("draft-contract", ContractStatus.DRAFT)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
    }

    "AllowAllStatusGate - ARCHIVED도 허용 (테스트 환경용)" {
        val allowAllGate = AllowAllStatusGate
        val result = allowAllGate.check("archived-contract", ContractStatus.ARCHIVED)
        result.shouldBeInstanceOf<ContractStatusGate.GateResult.Ok>()
    }
})
