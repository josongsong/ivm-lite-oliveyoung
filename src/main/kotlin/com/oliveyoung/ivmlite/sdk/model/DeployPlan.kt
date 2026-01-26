package com.oliveyoung.ivmlite.sdk.model

/**
 * Deploy 실행 계획
 * RFC-IMPL-011 Wave 1-B
 */
data class DeployPlan(
    val deployId: String,
    val graph: DependencyGraph,
    val activatedRules: List<String>,
    val executionSteps: List<ExecutionStep>
)
