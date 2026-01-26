package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * DependencyGraph 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class DependencyGraphTest : StringSpec({

    "GraphNode - 생성 및 필드 확인" {
        val node = GraphNode(
            id = "node-1",
            dependencies = listOf("dep-1", "dep-2"),
            provides = listOf("output-1")
        )

        node.id shouldBe "node-1"
        node.dependencies shouldBe listOf("dep-1", "dep-2")
        node.provides shouldBe listOf("output-1")
    }

    "GraphNode - 빈 dependencies와 provides" {
        val node = GraphNode(
            id = "node-2",
            dependencies = emptyList(),
            provides = emptyList()
        )

        node.dependencies shouldBe emptyList()
        node.provides shouldBe emptyList()
    }

    "DependencyGraph - 생성 및 노드 맵 확인" {
        val node1 = GraphNode("n1", listOf("dep1"), listOf("out1"))
        val node2 = GraphNode("n2", listOf("dep2"), listOf("out2"))

        val graph = DependencyGraph(
            nodes = mapOf(
                "n1" to node1,
                "n2" to node2
            )
        )

        graph.nodes.size shouldBe 2
        graph.nodes["n1"] shouldBe node1
        graph.nodes["n2"] shouldBe node2
    }

    "DependencyGraph - 빈 그래프" {
        val graph = DependencyGraph(nodes = emptyMap())

        graph.nodes shouldBe emptyMap()
        graph.nodes.size shouldBe 0
    }

    "DependencyGraph - 복잡한 의존성 그래프" {
        val nodeA = GraphNode(
            id = "A",
            dependencies = emptyList(),
            provides = listOf("dataA")
        )
        val nodeB = GraphNode(
            id = "B",
            dependencies = listOf("dataA"),
            provides = listOf("dataB")
        )
        val nodeC = GraphNode(
            id = "C",
            dependencies = listOf("dataA", "dataB"),
            provides = listOf("dataC")
        )

        val graph = DependencyGraph(
            nodes = mapOf(
                "A" to nodeA,
                "B" to nodeB,
                "C" to nodeC
            )
        )

        graph.nodes.size shouldBe 3
        graph.nodes["A"]?.dependencies shouldBe emptyList()
        graph.nodes["B"]?.dependencies shouldBe listOf("dataA")
        graph.nodes["C"]?.dependencies shouldBe listOf("dataA", "dataB")
    }
})
