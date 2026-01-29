package com.oliveyoung.ivmlite.pkg.workflow.canvas.domain

import kotlinx.serialization.Serializable

/**
 * 엣지 스타일
 */
enum class EdgeStyle {
    DEFAULT,      // 기본 실선
    DASHED,       // 점선
    ANIMATED,     // 애니메이션 (데이터 흐름 표시)
    ERROR         // 에러 상태 (빨간색)
}

/**
 * 워크플로우 엣지 (연결선)
 *
 * 두 노드 간의 데이터 흐름을 나타냄.
 * React Flow Edge와 1:1 대응됨.
 */
@Serializable
data class WorkflowEdge(
    val id: String,
    val source: String,               // 소스 노드 ID
    val target: String,               // 타겟 노드 ID
    val sourceHandle: String? = null, // 소스 핸들 ID (다중 출력 시)
    val targetHandle: String? = null, // 타겟 핸들 ID (다중 입력 시)
    val label: String? = null,        // 엣지 라벨 (optional)
    val animated: Boolean = false,    // 애니메이션 여부
    val style: String = "DEFAULT"     // EdgeStyle enum name
) {
    companion object {
        /**
         * 기본 엣지 생성
         */
        fun create(
            source: String,
            target: String,
            animated: Boolean = false,
            label: String? = null
        ): WorkflowEdge = WorkflowEdge(
            id = "edge_${source}_${target}",
            source = source,
            target = target,
            animated = animated,
            label = label
        )

        /**
         * 애니메이션 엣지 생성 (활성 데이터 흐름 표시)
         */
        fun animated(source: String, target: String): WorkflowEdge =
            create(source, target, animated = true)

        /**
         * 에러 엣지 생성
         */
        fun error(source: String, target: String): WorkflowEdge =
            WorkflowEdge(
                id = "edge_${source}_${target}",
                source = source,
                target = target,
                animated = false,
                style = EdgeStyle.ERROR.name
            )
    }
}
