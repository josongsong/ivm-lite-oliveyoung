package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.pkg.orchestration.adapters.DeployPlanRepositoryPort
import com.oliveyoung.ivmlite.sdk.model.DeployPlan
import com.oliveyoung.ivmlite.sdk.model.DependencyGraph
import kotlinx.coroutines.runBlocking

/**
 * Deploy Plan 조회 API
 * RFC-IMPL-011 Wave 5-K, Wave 6
 */
class PlanExplainApi internal constructor(
    private val config: IvmClientConfig,
    private val repository: DeployPlanRepositoryPort? = null
) {
    /**
     * 마지막 Deploy Plan 설명 조회
     * @param deployId Deploy ID
     * @return DeployPlan (그래프, 활성화된 규칙, 실행 단계)
     * @throws IllegalArgumentException deployId가 빈 문자열인 경우
     */
    fun explainLastPlan(deployId: String): DeployPlan {
        require(deployId.isNotBlank()) { "deployId must not be blank" }

        // Repository가 있으면 실제 조회
        if (repository != null) {
            return runBlocking {
                when (val result = repository.get(deployId)) {
                    is DeployPlanRepositoryPort.Result.Ok -> {
                        result.value?.toDeployPlan() ?: defaultPlan(deployId)
                    }
                    is DeployPlanRepositoryPort.Result.Err -> {
                        defaultPlan(deployId)
                    }
                }
            }
        }
        
        return defaultPlan(deployId)
    }
    
    /**
     * 엔티티의 모든 Deploy Plan 조회
     */
    fun explainByEntityKey(entityKey: String): List<DeployPlan> {
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        
        if (repository != null) {
            return runBlocking {
                when (val result = repository.getByEntityKey(entityKey)) {
                    is DeployPlanRepositoryPort.Result.Ok -> {
                        result.value.map { it.toDeployPlan() }
                    }
                    is DeployPlanRepositoryPort.Result.Err -> {
                        emptyList()
                    }
                }
            }
        }
        
        return emptyList()
    }
    
    private fun defaultPlan(deployId: String) = DeployPlan(
        deployId = deployId,
        graph = DependencyGraph(emptyMap()),
        activatedRules = listOf("product-to-search-doc", "product-to-reco-feed"),
        executionSteps = emptyList()
    )
}
