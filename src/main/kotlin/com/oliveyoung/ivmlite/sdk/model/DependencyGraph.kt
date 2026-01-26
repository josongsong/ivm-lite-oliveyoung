package com.oliveyoung.ivmlite.sdk.model

/**
 * 의존성 그래프
 * RFC-IMPL-011 Wave 1-B
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
