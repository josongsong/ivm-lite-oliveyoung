package com.oliveyoung.ivmlite.pkg.workflow.canvas.application

import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.*
import com.oliveyoung.ivmlite.pkg.workflow.canvas.ports.WorkflowGraphBuilderPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.time.Instant

/**
 * Workflow Canvas Service
 *
 * 데이터 파이프라인 그래프를 빌드하고 실시간 통계를 주입하는 서비스.
 * RFC-IMPL-015 구현.
 */
class WorkflowCanvasService(
    private val graphBuilder: WorkflowGraphBuilderPort,
    private val metricsCollector: MetricsCollectorPort?
) {

    /**
     * 워크플로우 그래프 조회
     *
     * @param entityType 필터링할 엔티티 타입 (null이면 전체)
     * @return 워크플로우 그래프
     */
    suspend fun getGraph(entityType: String? = null): WorkflowGraph {
        // 1. 기본 그래프 빌드
        val graph = graphBuilder.build(entityType)

        // 2. 실시간 통계 주입 (MetricsCollector가 있으면)
        val enrichedNodes = if (metricsCollector != null) {
            enrichNodesWithStats(graph.nodes)
        } else {
            graph.nodes
        }

        // 3. 상태 재계산
        val nodesWithStatus = enrichedNodes.map { node ->
            node.copy(status = calculateStatus(node.stats))
        }

        // 4. 메타데이터 업데이트
        val metadata = graph.metadata.copy(
            healthSummary = HealthSummary.fromNodes(nodesWithStatus),
            lastUpdatedAt = Instant.now().toString()
        )

        return graph.copy(nodes = nodesWithStatus, metadata = metadata)
    }

    /**
     * 노드 상세 정보 조회
     *
     * @param nodeId 노드 ID
     * @return 노드 상세 정보
     */
    suspend fun getNodeDetail(nodeId: String): Result<NodeDetail> {
        val graph = getGraph()
        val node = graph.findNode(nodeId)
            ?: return Result.Err(DomainError.NotFoundError("Node", nodeId))

        val relatedContracts = findRelatedContracts(node)
        val upstreamNodes = graph.findUpstreamNodes(nodeId)
        val downstreamNodes = graph.findDownstreamNodes(nodeId)
        val recentActivity = getRecentActivity(node)
        val metrics = getNodeMetrics(node)

        return Result.Ok(
            NodeDetail(
                node = node,
                relatedContracts = relatedContracts,
                upstreamNodes = upstreamNodes,
                downstreamNodes = downstreamNodes,
                recentActivity = recentActivity,
                metrics = metrics
            )
        )
    }

    /**
     * 워크플로우 통계 조회
     */
    suspend fun getStats(): WorkflowStats {
        val graph = getGraph()
        return WorkflowStats(
            entityTypes = graph.metadata.entityTypes,
            totalNodes = graph.metadata.totalNodes,
            totalEdges = graph.metadata.totalEdges,
            healthSummary = graph.metadata.healthSummary,
            nodesByType = graph.nodes.groupBy { it.type }.mapValues { it.value.size }
        )
    }

    // ==================== Private Methods ====================

    /**
     * 노드에 실시간 통계 주입
     */
    private suspend fun enrichNodesWithStats(nodes: List<WorkflowNode>): List<WorkflowNode> {
        if (metricsCollector == null) return nodes

        return try {
            val throughput = metricsCollector.collectThroughput(1)
            val queueDepths = metricsCollector.collectQueueDepths()

            nodes.map { node ->
                val stats = when (node.type) {
                    NodeType.RAWDATA -> NodeStats(
                        recordCount = queueDepths.pending.toLong(),
                        throughput = throughput.recordsPerMinute,
                        lastUpdatedAt = Instant.now().toString()
                    )
                    NodeType.SLICE -> NodeStats(
                        recordCount = 0,
                        throughput = throughput.recordsPerMinute,
                        lastUpdatedAt = Instant.now().toString()
                    )
                    NodeType.VIEW -> NodeStats(
                        recordCount = 0,
                        throughput = throughput.recordsPerMinute,
                        lastUpdatedAt = Instant.now().toString()
                    )
                    else -> null
                }
                node.copy(stats = stats)
            }
        } catch (e: Exception) {
            // 메트릭 수집 실패해도 그래프는 반환
            nodes
        }
    }

    /**
     * 노드 상태 계산
     */
    private fun calculateStatus(stats: NodeStats?): NodeStatus {
        if (stats == null) return NodeStatus.INACTIVE
        return when {
            stats.errorCount > 0 -> NodeStatus.ERROR
            stats.latencyP99Ms != null && stats.latencyP99Ms > 5000 -> NodeStatus.WARNING
            stats.throughput < 1.0 && stats.recordCount > 0 -> NodeStatus.WARNING
            stats.throughput == 0.0 && stats.recordCount == 0L -> NodeStatus.INACTIVE
            else -> NodeStatus.HEALTHY
        }
    }

    /**
     * 관련 Contract 조회
     */
    private fun findRelatedContracts(node: WorkflowNode): List<ContractSummary> {
        val contracts = mutableListOf<ContractSummary>()
        if (node.contractId != null) {
            contracts.add(
                ContractSummary(
                    id = node.contractId,
                    kind = node.type.name,
                    version = node.metadata["version"]?.toString() ?: "1.0.0"
                )
            )
        }
        return contracts
    }

    /**
     * 최근 활동 조회 (placeholder - 실제 구현 필요)
     */
    private suspend fun getRecentActivity(node: WorkflowNode): List<ActivityItem> {
        // TODO: Outbox 또는 로그에서 실제 활동 조회
        return emptyList()
    }

    /**
     * 노드 메트릭 조회 (placeholder - 실제 구현 필요)
     */
    private suspend fun getNodeMetrics(node: WorkflowNode): NodeMetrics {
        return NodeMetrics(
            avgLatencyMs = 0.0,
            p99LatencyMs = 0.0,
            errorRate = 0.0,
            throughputTrend = emptyList()
        )
    }

    // ==================== Result Type ====================

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}

// ==================== Domain Models ====================

/**
 * 노드 상세 정보
 */
data class NodeDetail(
    val node: WorkflowNode,
    val relatedContracts: List<ContractSummary>,
    val upstreamNodes: List<String>,
    val downstreamNodes: List<String>,
    val recentActivity: List<ActivityItem>,
    val metrics: NodeMetrics
)

/**
 * Contract 요약
 */
data class ContractSummary(
    val id: String,
    val kind: String,
    val version: String
)

/**
 * 활동 아이템
 */
data class ActivityItem(
    val timestamp: String,
    val action: String,
    val details: String
)

/**
 * 노드 메트릭
 */
data class NodeMetrics(
    val avgLatencyMs: Double,
    val p99LatencyMs: Double,
    val errorRate: Double,
    val throughputTrend: List<Double>
)

/**
 * 워크플로우 통계
 */
data class WorkflowStats(
    val entityTypes: List<String>,
    val totalNodes: Int,
    val totalEdges: Int,
    val healthSummary: HealthSummary,
    val nodesByType: Map<NodeType, Int>
)
