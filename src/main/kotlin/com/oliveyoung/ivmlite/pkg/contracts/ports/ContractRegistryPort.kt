package com.oliveyoung.ivmlite.pkg.contracts.ports

import com.oliveyoung.ivmlite.pkg.contracts.domain.ChangeSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.InvertedIndexContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.JoinSpecContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

interface ContractRegistryPort {
    // ===== Load (Single) =====
    suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract>
    suspend fun loadJoinSpecContract(ref: ContractRef): Result<JoinSpecContract>
    suspend fun loadInvertedIndexContract(ref: ContractRef): Result<InvertedIndexContract>
    suspend fun loadRuleSetContract(ref: ContractRef): Result<RuleSetContract>
    suspend fun loadViewDefinitionContract(ref: ContractRef): Result<ViewDefinitionContract>

    // ===== List (Multiple) =====
    /**
     * 특정 종류의 Contract 목록 조회 (GSI 사용)
     * 
     * @param kind Contract 종류 (VIEW_DEFINITION, RULE_SET, CHANGESET 등)
     * @param status 상태 필터 (null이면 전체)
     * @return Contract 메타데이터 목록
     */
    suspend fun listContractRefs(kind: String, status: ContractStatus? = null): Result<List<ContractRef>> {
        // 기본 구현: 빈 목록 (하위 호환성)
        return Result.Ok(emptyList())
    }
    
    /**
     * 모든 ViewDefinition Contract 목록 조회
     */
    suspend fun listViewDefinitions(status: ContractStatus? = ContractStatus.ACTIVE): Result<List<ViewDefinitionContract>> {
        // 기본 구현: 빈 목록
        return Result.Ok(emptyList())
    }

    // ===== Save (Write) =====
    /**
     * ViewDefinition Contract 저장
     */
    suspend fun saveViewDefinitionContract(contract: ViewDefinitionContract): Result<Unit> {
        return Result.Err(DomainError.StorageError("saveViewDefinitionContract not implemented"))
    }
    
    /**
     * RuleSet Contract 저장
     */
    suspend fun saveRuleSetContract(contract: RuleSetContract): Result<Unit> {
        return Result.Err(DomainError.StorageError("saveRuleSetContract not implemented"))
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
