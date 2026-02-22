package com.oliveyoung.ivmlite.pkg.contracts.domain

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * ContractGraph 단위 테스트
 *
 * 커버리지:
 * - subgraphFrom: 순방향 서브그래프 추출, depth 제한, 존재하지 않는 노드
 * - reverseSubgraphTo: 역방향 서브그래프 추출
 * - computeAffectedNodes: 변경 영향 분석
 * - GraphEdge.id 생성
 * - GraphNode, GraphMetadata data class
 */
class ContractGraphTest : DescribeSpec({

    // 테스트용 그래프: A → B → C → D
    //                       ↘ E
    val nodes = mapOf(
        "A" to GraphNode("A", ContractKind.ENTITY_SCHEMA, "Schema A", layer = 0),
        "B" to GraphNode("B", ContractKind.RULESET, "RuleSet B", layer = 1),
        "C" to GraphNode("C", ContractKind.VIEW_DEFINITION, "ViewDef C", layer = 2),
        "D" to GraphNode("D", ContractKind.SINK_RULE, "SinkRule D", layer = 3),
        "E" to GraphNode("E", ContractKind.VIEW_DEFINITION, "ViewDef E", layer = 2),
    )
    val edges = listOf(
        GraphEdge("A", "B", EdgeKind.DEFINES),
        GraphEdge("B", "C", EdgeKind.PRODUCES),
        GraphEdge("B", "E", EdgeKind.PRODUCES),
        GraphEdge("C", "D", EdgeKind.REQUIRES),
    )
    val metadata = GraphMetadata(totalNodes = 5, totalEdges = 4, entityTypes = listOf("product"))

    val graph = ContractGraph(nodes, edges, metadata)

    describe("subgraphFrom") {

        it("A에서 depth=2 → A, B, C, E 포함") {
            val sub = graph.subgraphFrom("A", depth = 2)

            sub.nodes.size shouldBe 4
            sub.nodes.containsKey("A") shouldBe true
            sub.nodes.containsKey("B") shouldBe true
            sub.nodes.containsKey("C") shouldBe true
            sub.nodes.containsKey("E") shouldBe true
            sub.nodes.containsKey("D") shouldBe false // depth 3
        }

        it("A에서 depth=3 → 전체 포함") {
            val sub = graph.subgraphFrom("A", depth = 3)
            sub.nodes.size shouldBe 5
        }

        it("C에서 depth=1 → C, D") {
            val sub = graph.subgraphFrom("C", depth = 1)
            sub.nodes.size shouldBe 2
            sub.nodes.containsKey("C") shouldBe true
            sub.nodes.containsKey("D") shouldBe true
        }

        it("존재하지 않는 노드 → 빈 그래프") {
            val sub = graph.subgraphFrom("NONEXISTENT")
            sub.nodes.size shouldBe 0
        }

        it("metadata 업데이트됨") {
            val sub = graph.subgraphFrom("B", depth = 1)
            sub.metadata.totalNodes shouldBe sub.nodes.size
            sub.metadata.totalEdges shouldBe sub.edges.size
        }
    }

    describe("reverseSubgraphTo") {

        it("D에서 역방향 depth=2 → D, C, B") {
            val sub = graph.reverseSubgraphTo("D", depth = 2)

            sub.nodes.size shouldBe 3
            sub.nodes.containsKey("D") shouldBe true
            sub.nodes.containsKey("C") shouldBe true
            sub.nodes.containsKey("B") shouldBe true
        }

        it("B에서 역방향 depth=1 → B, A") {
            val sub = graph.reverseSubgraphTo("B", depth = 1)

            sub.nodes.size shouldBe 2
            sub.nodes.containsKey("B") shouldBe true
            sub.nodes.containsKey("A") shouldBe true
        }

        it("A에서 역방향 → A만 (부모 없음)") {
            val sub = graph.reverseSubgraphTo("A", depth = 2)
            sub.nodes.size shouldBe 1
        }
    }

    describe("computeAffectedNodes") {

        it("A 변경 → B, C, D, E 영향") {
            val affected = graph.computeAffectedNodes("A", depth = 5)

            affected shouldBe setOf("B", "C", "D", "E")
        }

        it("B 변경 → C, D, E 영향") {
            val affected = graph.computeAffectedNodes("B", depth = 5)

            affected shouldBe setOf("C", "D", "E")
        }

        it("D 변경 → 빈 집합 (leaf 노드)") {
            val affected = graph.computeAffectedNodes("D")

            affected shouldBe emptySet()
        }

        it("depth 제한") {
            val affected = graph.computeAffectedNodes("A", depth = 1)

            affected shouldBe setOf("B")
        }
    }

    describe("GraphEdge") {

        it("id 생성") {
            val edge = GraphEdge("A", "B", EdgeKind.DEFINES, "label")
            edge.id shouldBe "A_DEFINES_B"
        }
    }

    describe("NodeStatus") {

        it("모든 상태 값") {
            NodeStatus.entries.size shouldBe 5
            NodeStatus.valueOf("NORMAL") shouldBe NodeStatus.NORMAL
            NodeStatus.valueOf("CHANGED") shouldBe NodeStatus.CHANGED
            NodeStatus.valueOf("AFFECTED") shouldBe NodeStatus.AFFECTED
        }
    }
})
