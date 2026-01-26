package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * DeployPlan 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class DeployPlanTest : StringSpec({

    "DeployPlan - 생성 및 모든 필드 확인" {
        val node = GraphNode(
            id = "n1",
            dependencies = emptyList(),
            provides = listOf("out1")
        )
        val graph = DependencyGraph(nodes = mapOf("n1" to node))

        val step = ExecutionStep(
            stepNumber = 1,
            sliceRef = "slice-1",
            dependencies = emptyList()
        )

        val plan = DeployPlan(
            deployId = "deploy-123",
            graph = graph,
            activatedRules = listOf("rule-1", "rule-2"),
            executionSteps = listOf(step)
        )

        plan.deployId shouldBe "deploy-123"
        plan.graph shouldBe graph
        plan.activatedRules shouldBe listOf("rule-1", "rule-2")
        plan.executionSteps shouldBe listOf(step)
    }

    "DeployPlan - 빈 activatedRules와 executionSteps" {
        val graph = DependencyGraph(nodes = emptyMap())

        val plan = DeployPlan(
            deployId = "deploy-456",
            graph = graph,
            activatedRules = emptyList(),
            executionSteps = emptyList()
        )

        plan.activatedRules shouldBe emptyList()
        plan.executionSteps shouldBe emptyList()
    }

    "DeployPlan - 여러 execution steps" {
        val graph = DependencyGraph(nodes = emptyMap())

        val steps = listOf(
            ExecutionStep(1, "slice-1", emptyList()),
            ExecutionStep(2, "slice-2", listOf("slice-1")),
            ExecutionStep(3, "slice-3", listOf("slice-1", "slice-2"))
        )

        val plan = DeployPlan(
            deployId = "deploy-789",
            graph = graph,
            activatedRules = listOf("r1", "r2", "r3"),
            executionSteps = steps
        )

        plan.executionSteps.size shouldBe 3
        plan.executionSteps[0].stepNumber shouldBe 1
        plan.executionSteps[1].stepNumber shouldBe 2
        plan.executionSteps[2].stepNumber shouldBe 3
    }

    "DeployPlan - 복잡한 그래프와 실행 계획" {
        val nodeA = GraphNode("A", emptyList(), listOf("dataA"))
        val nodeB = GraphNode("B", listOf("dataA"), listOf("dataB"))
        val nodeC = GraphNode("C", listOf("dataA", "dataB"), listOf("dataC"))

        val graph = DependencyGraph(
            nodes = mapOf(
                "A" to nodeA,
                "B" to nodeB,
                "C" to nodeC
            )
        )

        val steps = listOf(
            ExecutionStep(1, "A", emptyList()),
            ExecutionStep(2, "B", listOf("A")),
            ExecutionStep(3, "C", listOf("A", "B"))
        )

        val plan = DeployPlan(
            deployId = "complex-deploy",
            graph = graph,
            activatedRules = listOf("rule-A", "rule-B", "rule-C"),
            executionSteps = steps
        )

        plan.graph.nodes.size shouldBe 3
        plan.activatedRules.size shouldBe 3
        plan.executionSteps.size shouldBe 3
        plan.executionSteps.last().dependencies shouldBe listOf("A", "B")
    }

    "DeployPlan - data class copy" {
        val graph = DependencyGraph(nodes = emptyMap())
        val original = DeployPlan(
            deployId = "deploy-1",
            graph = graph,
            activatedRules = listOf("r1"),
            executionSteps = emptyList()
        )

        val modified = original.copy(
            deployId = "deploy-2",
            activatedRules = listOf("r1", "r2")
        )

        modified.deployId shouldBe "deploy-2"
        modified.activatedRules shouldBe listOf("r1", "r2")
        modified.graph shouldBe original.graph
    }
})
