package com.oliveyoung.ivmlite.shared.domain.deploy

/**
 * 의존성 그래프
 * RFC-IMPL-011 Wave 1-B
 * 
 * Note: 이 모델은 pkg, sdk 모두에서 사용되므로 shared에 위치
 */
data class DependencyGraph(
    val nodes: Map<String, GraphNode>
)

/**
 * 그래프 노드
 */
data class GraphNode(
    val id: String,
    val dependencies: List<String>,
    val provides: List<String>
)
