package com.oliveyoung.ivmlite.pkg.contracts.domain

/**
 * Contract Dependency Graph
 *
 * Contract 간의 의존성을 표현하는 그래프.
 * Impact Analysis, Causality Tracing의 기반 데이터.
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 0
 */
data class ContractGraph(
    /** 그래프 노드들 (Contract ID → Node) */
    val nodes: Map<String, GraphNode>,
    /** 그래프 엣지들 */
    val edges: List<GraphEdge>,
    /** 메타데이터 */
    val metadata: GraphMetadata
) {
    /**
     * 특정 노드에서 시작하는 하위 그래프 추출
     */
    fun subgraphFrom(nodeId: String, depth: Int = 2): ContractGraph {
        val visited = mutableSetOf<String>()
        val resultNodes = mutableMapOf<String, GraphNode>()
        val resultEdges = mutableListOf<GraphEdge>()

        fun traverse(id: String, currentDepth: Int) {
            if (currentDepth > depth || id in visited) return
            visited.add(id)

            nodes[id]?.let { node ->
                resultNodes[id] = node
                edges.filter { it.from == id }.forEach { edge ->
                    resultEdges.add(edge)
                    traverse(edge.to, currentDepth + 1)
                }
            }
        }

        traverse(nodeId, 0)
        return ContractGraph(
            nodes = resultNodes,
            edges = resultEdges,
            metadata = metadata.copy(
                totalNodes = resultNodes.size,
                totalEdges = resultEdges.size
            )
        )
    }

    /**
     * 특정 노드로 향하는 역방향 그래프 추출 (dependents)
     */
    fun reverseSubgraphTo(nodeId: String, depth: Int = 2): ContractGraph {
        val visited = mutableSetOf<String>()
        val resultNodes = mutableMapOf<String, GraphNode>()
        val resultEdges = mutableListOf<GraphEdge>()

        fun traverse(id: String, currentDepth: Int) {
            if (currentDepth > depth || id in visited) return
            visited.add(id)

            nodes[id]?.let { node ->
                resultNodes[id] = node
                edges.filter { it.to == id }.forEach { edge ->
                    resultEdges.add(edge)
                    traverse(edge.from, currentDepth + 1)
                }
            }
        }

        traverse(nodeId, 0)
        return ContractGraph(
            nodes = resultNodes,
            edges = resultEdges,
            metadata = metadata.copy(
                totalNodes = resultNodes.size,
                totalEdges = resultEdges.size
            )
        )
    }

    /**
     * 변경 영향 분석 (affected nodes)
     */
    fun computeAffectedNodes(changedNodeId: String, depth: Int = 3): Set<String> {
        val affected = mutableSetOf<String>()

        fun traverse(id: String, currentDepth: Int) {
            if (currentDepth > depth || id in affected) return
            if (id != changedNodeId) affected.add(id)

            edges.filter { it.from == id }.forEach { edge ->
                traverse(edge.to, currentDepth + 1)
            }
        }

        traverse(changedNodeId, 0)
        return affected
    }
}

/**
 * 그래프 노드 (Contract 기반)
 */
data class GraphNode(
    /** 노드 ID (Contract ID) */
    val id: String,
    /** Contract 종류 */
    val kind: ContractKind,
    /** 표시 라벨 */
    val label: String,
    /** Entity Type (해당되는 경우) */
    val entityType: String? = null,
    /** 레이어 (시각화용) */
    val layer: Int = 0,
    /** 상태 */
    val status: NodeStatus = NodeStatus.NORMAL,
    /** 추가 메타데이터 */
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * 노드 상태
 */
enum class NodeStatus {
    NORMAL,
    CHANGED,
    AFFECTED,
    ERROR,
    WARNING
}

/**
 * 그래프 엣지
 */
data class GraphEdge(
    /** 시작 노드 ID */
    val from: String,
    /** 끝 노드 ID */
    val to: String,
    /** 엣지 종류 */
    val kind: EdgeKind,
    /** 엣지 라벨 */
    val label: String? = null
) {
    val id: String get() = "${from}_${kind.name}_${to}"
}

/**
 * 엣지 종류
 */
enum class EdgeKind {
    /** Schema → RuleSet (정의) */
    DEFINES,
    /** RuleSet → Slice (생산) */
    PRODUCES,
    /** Slice → View (필요) */
    REQUIRES,
    /** Slice → Sink (소비) */
    CONSUMES,
    /** 일반 사용 관계 */
    USES
}

/**
 * 그래프 메타데이터
 */
data class GraphMetadata(
    /** 총 노드 수 */
    val totalNodes: Int,
    /** 총 엣지 수 */
    val totalEdges: Int,
    /** 포함된 Entity 타입들 */
    val entityTypes: List<String> = emptyList(),
    /** 마지막 업데이트 시간 */
    val lastUpdatedAt: String? = null
)
