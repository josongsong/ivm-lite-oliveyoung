package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.model.DeployPlan
import com.oliveyoung.ivmlite.sdk.model.DependencyGraph

/**
 * Deploy Plan 조회 API
 * RFC-IMPL-011 Wave 5-K
 */
class PlanExplainApi internal constructor(
    private val config: IvmClientConfig
) {
    /**
     * 마지막 Deploy Plan 설명 조회
     * @param deployId Deploy ID
     * @return DeployPlan (그래프, 활성화된 규칙, 실행 단계)
     * @throws IllegalArgumentException deployId가 빈 문자열인 경우
     */
    fun explainLastPlan(deployId: String): DeployPlan {
        require(deployId.isNotBlank()) { "deployId must not be blank" }

        // TODO: 실제 API 호출
        return DeployPlan(
            deployId = deployId,
            graph = DependencyGraph(emptyMap()),
            activatedRules = listOf("product-to-search-doc", "product-to-reco-feed"),
            executionSteps = emptyList()
        )
    }
}
