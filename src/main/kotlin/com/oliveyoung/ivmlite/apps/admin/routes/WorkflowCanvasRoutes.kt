package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.*
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Workflow Canvas Routes (RFC-IMPL-015)
 *
 * 데이터 파이프라인 시각화를 위한 API 엔드포인트.
 *
 * GET /workflow/graph          - 전체 워크플로우 그래프 조회
 * GET /workflow/nodes/{nodeId} - 노드 상세 정보 조회
 * GET /workflow/stats          - 워크플로우 통계 조회
 */
fun Route.workflowCanvasRoutes() {
    val workflowService by inject<WorkflowCanvasService>()

    route("/workflow") {

        /**
         * GET /workflow/graph
         *
         * 전체 워크플로우 그래프 조회.
         * React Flow 캔버스에 직접 렌더링 가능한 포맷.
         *
         * Query Parameters:
         * - entityType: 특정 엔티티 타입으로 필터링 (optional)
         */
        get("/graph") {
            try {
                val entityType = call.request.queryParameters["entityType"]
                val graph = workflowService.getGraph(entityType)

                call.respond(HttpStatusCode.OK, graph.toResponse())
            } catch (e: Exception) {
                call.application.log.error("Failed to get workflow graph", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "WORKFLOW_ERROR", message = "Failed to get workflow graph: ${e.message}")
                )
            }
        }

        /**
         * GET /workflow/nodes/{nodeId}
         *
         * 노드 상세 정보 조회.
         * 연관 Contract, 상위/하위 노드, 최근 활동, 메트릭 포함.
         */
        get("/nodes/{nodeId}") {
            try {
                val nodeId = call.parameters["nodeId"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MISSING_NODE_ID", message = "nodeId is required")
                    )
                    return@get
                }

                when (val result = workflowService.getNodeDetail(nodeId)) {
                    is WorkflowCanvasService.Result.Ok -> {
                        call.respond(HttpStatusCode.OK, result.value.toResponse())
                    }
                    is WorkflowCanvasService.Result.Err -> {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiError(code = "NOT_FOUND", message = result.error.message ?: "Node not found")
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Failed to get node detail", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "NODE_ERROR", message = "Failed to get node detail: ${e.message}")
                )
            }
        }

        /**
         * GET /workflow/stats
         *
         * 워크플로우 통계 조회.
         * 전체 노드/엣지 수, 헬스 요약, 타입별 분포.
         */
        get("/stats") {
            try {
                val stats = workflowService.getStats()
                call.respond(HttpStatusCode.OK, stats.toResponse())
            } catch (e: Exception) {
                call.application.log.error("Failed to get workflow stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(code = "STATS_ERROR", message = "Failed to get workflow stats: ${e.message}")
                )
            }
        }
    }
}

// ==================== Response DTOs ====================

@Serializable
data class WorkflowGraphResponse(
    val nodes: List<NodeResponse>,
    val edges: List<EdgeResponse>,
    val metadata: MetadataResponse
)

@Serializable
data class NodeResponse(
    val id: String,
    val type: String,
    val data: NodeDataResponse,
    val position: PositionResponse
)

@Serializable
data class NodeDataResponse(
    val label: String,
    val entityType: String?,
    val contractId: String?,
    val status: String,
    val stats: StatsResponse?,
    val metadata: Map<String, String>
)

@Serializable
data class StatsResponse(
    val recordCount: Long,
    val throughput: Double,
    val latencyP99Ms: Long?,
    val errorCount: Long,
    val lastUpdatedAt: String?
)

@Serializable
data class PositionResponse(
    val x: Double,
    val y: Double
)

@Serializable
data class EdgeResponse(
    val id: String,
    val source: String,
    val target: String,
    val sourceHandle: String?,
    val targetHandle: String?,
    val label: String?,
    val animated: Boolean,
    val type: String? = "smoothstep"
)

@Serializable
data class MetadataResponse(
    val entityTypes: List<String>,
    val totalNodes: Int,
    val totalEdges: Int,
    val healthSummary: HealthSummaryResponse,
    val lastUpdatedAt: String
)

@Serializable
data class HealthSummaryResponse(
    val healthy: Int,
    val warning: Int,
    val error: Int,
    val inactive: Int
)

@Serializable
data class NodeDetailResponse(
    val node: NodeResponse,
    val relatedContracts: List<ContractSummaryResponse>,
    val upstreamNodes: List<String>,
    val downstreamNodes: List<String>,
    val recentActivity: List<ActivityItemResponse>,
    val metrics: NodeMetricsResponse
)

@Serializable
data class ContractSummaryResponse(
    val id: String,
    val kind: String,
    val version: String
)

@Serializable
data class ActivityItemResponse(
    val timestamp: String,
    val action: String,
    val details: String
)

@Serializable
data class NodeMetricsResponse(
    val avgLatencyMs: Double,
    val p99LatencyMs: Double,
    val errorRate: Double,
    val throughputTrend: List<Double>
)

@Serializable
data class WorkflowStatsResponse(
    val entityTypes: List<String>,
    val totalNodes: Int,
    val totalEdges: Int,
    val healthSummary: HealthSummaryResponse,
    val nodesByType: Map<String, Int>
)

// ==================== Extension Functions (Domain -> DTO) ====================

private fun WorkflowGraph.toResponse(): WorkflowGraphResponse {
    return WorkflowGraphResponse(
        nodes = nodes.map { it.toResponse() },
        edges = edges.map { it.toResponse() },
        metadata = metadata.toResponse()
    )
}

private fun WorkflowNode.toResponse(): NodeResponse {
    return NodeResponse(
        id = id,
        type = type.name.lowercase().replace("_", ""),  // VIEW_DEF -> viewdef
        data = NodeDataResponse(
            label = label,
            entityType = entityType,
            contractId = contractId,
            status = status.name.lowercase(),
            stats = stats?.toResponse(),
            metadata = metadata.mapValues { it.value.toString() }
        ),
        position = PositionResponse(position.x, position.y)
    )
}

private fun NodeStats.toResponse(): StatsResponse {
    return StatsResponse(
        recordCount = recordCount,
        throughput = throughput,
        latencyP99Ms = latencyP99Ms,
        errorCount = errorCount,
        lastUpdatedAt = lastUpdatedAt
    )
}

private fun WorkflowEdge.toResponse(): EdgeResponse {
    return EdgeResponse(
        id = id,
        source = source,
        target = target,
        sourceHandle = sourceHandle,
        targetHandle = targetHandle,
        label = label,
        animated = animated,
        type = "smoothstep"
    )
}

private fun GraphMetadata.toResponse(): MetadataResponse {
    return MetadataResponse(
        entityTypes = entityTypes,
        totalNodes = totalNodes,
        totalEdges = totalEdges,
        healthSummary = healthSummary.toResponse(),
        lastUpdatedAt = lastUpdatedAt
    )
}

private fun HealthSummary.toResponse(): HealthSummaryResponse {
    return HealthSummaryResponse(
        healthy = healthy,
        warning = warning,
        error = error,
        inactive = inactive
    )
}

private fun NodeDetail.toResponse(): NodeDetailResponse {
    return NodeDetailResponse(
        node = node.toResponse(),
        relatedContracts = relatedContracts.map { it.toResponse() },
        upstreamNodes = upstreamNodes,
        downstreamNodes = downstreamNodes,
        recentActivity = recentActivity.map { it.toResponse() },
        metrics = metrics.toResponse()
    )
}

private fun ContractSummary.toResponse(): ContractSummaryResponse {
    return ContractSummaryResponse(
        id = id,
        kind = kind,
        version = version
    )
}

private fun ActivityItem.toResponse(): ActivityItemResponse {
    return ActivityItemResponse(
        timestamp = timestamp,
        action = action,
        details = details
    )
}

private fun NodeMetrics.toResponse(): NodeMetricsResponse {
    return NodeMetricsResponse(
        avgLatencyMs = avgLatencyMs,
        p99LatencyMs = p99LatencyMs,
        errorRate = errorRate,
        throughputTrend = throughputTrend
    )
}

private fun WorkflowStats.toResponse(): WorkflowStatsResponse {
    return WorkflowStatsResponse(
        entityTypes = entityTypes,
        totalNodes = totalNodes,
        totalEdges = totalEdges,
        healthSummary = healthSummary.toResponse(),
        nodesByType = nodesByType.mapKeys { it.key.name.lowercase().replace("_", "") }
    )
}
