package com.oliveyoung.ivmlite.pkg.workflow.canvas.domain

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 워크플로우 노드 타입
 *
 * 데이터 파이프라인의 각 단계를 나타냄:
 * RAWDATA → RULESET → SLICE → VIEW_DEF → VIEW → SINK_RULE → SINK
 */
enum class NodeType {
    RAWDATA,      // 원본 데이터 (Entity Schema)
    RULESET,      // 슬라이싱 규칙
    SLICE,        // 슬라이스 (CORE, PRICE, MEDIA 등)
    VIEW_DEF,     // 뷰 정의 규칙
    VIEW,         // 조합된 뷰 (DETAIL, SEARCH, LIST 등)
    SINK_RULE,    // 싱크 규칙
    SINK          // 외부 시스템 (OpenSearch, Kafka 등)
}

/**
 * 노드 상태
 *
 * 각 노드의 현재 헬스 상태를 나타냄
 */
enum class NodeStatus {
    HEALTHY,      // 정상 동작
    WARNING,      // 경고 (지연, 처리량 저하 등)
    ERROR,        // 오류 발생
    INACTIVE      // 비활성 (처리 없음)
}

/**
 * 노드 통계 정보
 *
 * 각 노드의 실시간 메트릭
 */
@Serializable
data class NodeStats(
    val recordCount: Long,
    val throughput: Double,           // records/min
    val latencyP99Ms: Long? = null,   // P99 지연 (ms)
    val errorCount: Long = 0,
    val lastUpdatedAt: String? = null // ISO-8601 포맷
) {
    companion object {
        fun empty() = NodeStats(
            recordCount = 0,
            throughput = 0.0,
            latencyP99Ms = null,
            errorCount = 0,
            lastUpdatedAt = null
        )
    }
}

/**
 * 노드 위치 (캔버스 좌표)
 */
@Serializable
data class NodePosition(
    val x: Double,
    val y: Double
)

/**
 * 워크플로우 노드
 *
 * 데이터 파이프라인의 한 단계를 나타내는 노드.
 * React Flow 노드와 1:1 대응됨.
 */
data class WorkflowNode(
    val id: String,
    val type: NodeType,
    val label: String,
    val entityType: String? = null,   // PRODUCT, BRAND, CATEGORY 등
    val contractId: String? = null,   // 연관 Contract ID
    val status: NodeStatus = NodeStatus.INACTIVE,
    val stats: NodeStats? = null,
    val position: NodePosition,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 규칙 노드인지 여부 (작은 크기로 렌더링)
     */
    val isRuleNode: Boolean
        get() = type in listOf(NodeType.RULESET, NodeType.VIEW_DEF, NodeType.SINK_RULE)
}
