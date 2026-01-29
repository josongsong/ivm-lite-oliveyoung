package com.oliveyoung.ivmlite.pkg.workflow.canvas.ports

import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.WorkflowGraph

/**
 * 워크플로우 그래프 빌더 포트 (RFC-IMPL-015)
 *
 * Contract YAML 파일들을 분석하여 노드-엣지 그래프를 생성하는 인터페이스.
 * 테스트 시 Mock 구현으로 대체 가능.
 */
interface WorkflowGraphBuilderPort {
    /**
     * 워크플로우 그래프 빌드
     *
     * @param entityTypeFilter 필터링할 엔티티 타입 (null이면 전체)
     * @return 빌드된 워크플로우 그래프
     */
    fun build(entityTypeFilter: String? = null): WorkflowGraph
}
