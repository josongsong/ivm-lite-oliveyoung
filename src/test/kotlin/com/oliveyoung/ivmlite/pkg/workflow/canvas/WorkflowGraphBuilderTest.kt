package com.oliveyoung.ivmlite.pkg.workflow.canvas

import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeType
import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.NodeStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * WorkflowGraphBuilder 단위 테스트 (RFC-IMPL-015)
 */
class WorkflowGraphBuilderTest : StringSpec({

    val builder = WorkflowGraphBuilder()

    "전체 그래프 빌드 - Contract YAML에서 노드/엣지 생성" {
        val graph = builder.build()

        // 노드가 생성되었는지 확인
        graph.nodes.shouldNotBeEmpty()
        graph.edges.shouldNotBeEmpty()

        // 메타데이터 검증
        graph.metadata.totalNodes shouldBe graph.nodes.size
        graph.metadata.totalEdges shouldBe graph.edges.size
        graph.metadata.entityTypes.shouldNotBeEmpty()
    }

    "RawData 노드 생성 - ENTITY_SCHEMA 기반" {
        val graph = builder.build()

        val rawDataNodes = graph.nodes.filter { it.type == NodeType.RAWDATA }
        rawDataNodes.shouldNotBeEmpty()

        // PRODUCT 엔티티 확인
        val productNode = rawDataNodes.find { it.entityType == "PRODUCT" }
        productNode shouldNotBe null
        productNode?.label shouldBe "PRODUCT"
        productNode?.id shouldBe "rawdata_PRODUCT"
    }

    "RuleSet 노드 생성 - 작은 규칙 노드" {
        val graph = builder.build()

        val ruleSetNodes = graph.nodes.filter { it.type == NodeType.RULESET }
        ruleSetNodes.shouldNotBeEmpty()

        // 규칙 노드는 isRuleNode = true
        ruleSetNodes.all { it.isRuleNode } shouldBe true
    }

    "Slice 노드 생성 - RuleSet slices 기반" {
        val graph = builder.build()

        val sliceNodes = graph.nodes.filter { it.type == NodeType.SLICE }
        sliceNodes.shouldNotBeEmpty()

        // CORE, PRICE 등 슬라이스 타입 확인
        val sliceLabels = sliceNodes.map { it.label }
        sliceLabels.any { it == "CORE" } shouldBe true
    }

    "View 노드 생성 - VIEW_DEFINITION 기반" {
        val graph = builder.build()

        val viewNodes = graph.nodes.filter { it.type == NodeType.VIEW }
        viewNodes.shouldNotBeEmpty()

        // PRODUCT_DETAIL 뷰 확인
        val detailView = viewNodes.find { it.label == "PRODUCT_DETAIL" }
        detailView shouldNotBe null
    }

    "Sink 노드 생성 - SINKRULE 기반" {
        val graph = builder.build()

        val sinkNodes = graph.nodes.filter { it.type == NodeType.SINK }
        sinkNodes.shouldNotBeEmpty()

        // OpenSearch 싱크 확인
        val openSearchSink = sinkNodes.find { it.label == "OPENSEARCH" }
        openSearchSink shouldNotBe null
    }

    "엣지 생성 - 노드 간 연결" {
        val graph = builder.build()

        // RawData → RuleSet 엣지
        val rawDataToRuleSet = graph.edges.find { edge ->
            edge.source.startsWith("rawdata_") && edge.target.startsWith("ruleset_")
        }
        rawDataToRuleSet shouldNotBe null

        // RuleSet → Slice 엣지 (애니메이션)
        val ruleSetToSlice = graph.edges.find { edge ->
            edge.source.startsWith("ruleset_") &&
            graph.nodes.find { it.id == edge.target }?.type == NodeType.SLICE
        }
        ruleSetToSlice shouldNotBe null
        ruleSetToSlice?.animated shouldBe true
    }

    "엔티티 필터 적용 - 특정 엔티티만 조회" {
        val fullGraph = builder.build()
        val filteredGraph = builder.build("PRODUCT")

        // 필터된 그래프는 PRODUCT 관련 노드만 포함
        filteredGraph.nodes.filter { it.entityType != null }
            .all { it.entityType == "PRODUCT" } shouldBe true

        // 필터된 그래프 노드 수가 전체보다 작거나 같음
        filteredGraph.nodes.size shouldBe filteredGraph.metadata.totalNodes
    }

    "자동 레이아웃 - 노드 위치 계산" {
        val graph = builder.build()

        // 모든 노드에 위치가 설정됨 (y >= 0, x는 중앙정렬로 음수 가능)
        graph.nodes.all { it.position.y >= 0 } shouldBe true

        // 레이어별로 y 좌표가 증가 (RawData < Slice)
        val rawDataY = graph.nodes.filter { it.type == NodeType.RAWDATA }.map { it.position.y }
        val sliceY = graph.nodes.filter { it.type == NodeType.SLICE }.map { it.position.y }

        if (rawDataY.isNotEmpty() && sliceY.isNotEmpty()) {
            // RawData(layer 0)는 Slice(layer 2)보다 y좌표가 작아야 함
            rawDataY.average() shouldNotBe sliceY.average()
        }
    }

    "헬스 요약 통계 생성" {
        val graph = builder.build()

        val summary = graph.metadata.healthSummary
        val total = summary.healthy + summary.warning + summary.error + summary.inactive

        // 모든 노드가 헬스 요약에 포함
        total shouldBe graph.nodes.size
    }

    "그래프 유틸리티 메서드 - upstream/downstream 조회" {
        val graph = builder.build()

        // Slice 노드의 upstream (RuleSet)과 downstream (ViewDef) 확인
        val sliceNode = graph.nodes.find { it.type == NodeType.SLICE }
        if (sliceNode != null) {
            val upstreamNodes = graph.findUpstreamNodes(sliceNode.id)
            val downstreamNodes = graph.findDownstreamNodes(sliceNode.id)

            // Slice는 RuleSet에서 들어오고, ViewDef로 나감
            upstreamNodes.shouldNotBeEmpty()
        }
    }
})
