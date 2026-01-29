package com.oliveyoung.ivmlite.pkg.workflow.canvas.domain

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 헬스 요약 통계
 */
@Serializable
data class HealthSummary(
    val healthy: Int,
    val warning: Int,
    val error: Int,
    val inactive: Int
) {
    companion object {
        fun empty() = HealthSummary(0, 0, 0, 0)

        fun fromNodes(nodes: List<WorkflowNode>): HealthSummary {
            val grouped = nodes.groupBy { it.status }
            return HealthSummary(
                healthy = grouped[NodeStatus.HEALTHY]?.size ?: 0,
                warning = grouped[NodeStatus.WARNING]?.size ?: 0,
                error = grouped[NodeStatus.ERROR]?.size ?: 0,
                inactive = grouped[NodeStatus.INACTIVE]?.size ?: 0
            )
        }
    }
}

/**
 * 그래프 메타데이터
 */
@Serializable
data class GraphMetadata(
    val entityTypes: List<String>,
    val totalNodes: Int,
    val totalEdges: Int,
    val healthSummary: HealthSummary,
    val lastUpdatedAt: String  // ISO-8601
)

/**
 * 워크플로우 그래프
 *
 * 전체 데이터 파이프라인을 노드-엣지 그래프로 표현.
 * React Flow 캔버스에 렌더링됨.
 */
data class WorkflowGraph(
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>,
    val metadata: GraphMetadata
) {
    companion object {
        fun empty() = WorkflowGraph(
            nodes = emptyList(),
            edges = emptyList(),
            metadata = GraphMetadata(
                entityTypes = emptyList(),
                totalNodes = 0,
                totalEdges = 0,
                healthSummary = HealthSummary.empty(),
                lastUpdatedAt = Instant.now().toString()
            )
        )
    }

    /**
     * 특정 엔티티 타입으로 필터링
     */
    fun filterByEntityType(entityType: String): WorkflowGraph {
        val filteredNodes = nodes.filter { node ->
            node.entityType == entityType || node.entityType == null
        }
        val filteredNodeIds = filteredNodes.map { it.id }.toSet()
        val filteredEdges = edges.filter { edge ->
            edge.source in filteredNodeIds && edge.target in filteredNodeIds
        }

        return copy(
            nodes = filteredNodes,
            edges = filteredEdges,
            metadata = metadata.copy(
                totalNodes = filteredNodes.size,
                totalEdges = filteredEdges.size,
                healthSummary = HealthSummary.fromNodes(filteredNodes)
            )
        )
    }

    /**
     * 특정 노드의 상위 노드 ID 목록
     */
    fun findUpstreamNodes(nodeId: String): List<String> {
        return edges.filter { it.target == nodeId }.map { it.source }
    }

    /**
     * 특정 노드의 하위 노드 ID 목록
     */
    fun findDownstreamNodes(nodeId: String): List<String> {
        return edges.filter { it.source == nodeId }.map { it.target }
    }

    /**
     * 특정 노드 조회
     */
    fun findNode(nodeId: String): WorkflowNode? {
        return nodes.find { it.id == nodeId }
    }
}
