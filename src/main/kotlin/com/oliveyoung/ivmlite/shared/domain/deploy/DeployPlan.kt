package com.oliveyoung.ivmlite.shared.domain.deploy

/**
 * Deploy 실행 계획
 * RFC-IMPL-011 Wave 1-B
 * 
 * Note: 이 모델은 pkg, sdk 모두에서 사용되므로 shared에 위치
 */
data class DeployPlan(
    val deployId: String,
    val graph: DependencyGraph,
    val activatedRules: List<String>,
    val executionSteps: List<ExecutionStep>
)
