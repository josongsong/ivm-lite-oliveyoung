package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * ExecutionStep 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class ExecutionStepTest : StringSpec({

    "ExecutionStep - 생성 및 필드 확인" {
        val step = ExecutionStep(
            stepNumber = 1,
            sliceRef = "product-core-v1",
            dependencies = listOf("raw-data")
        )

        step.stepNumber shouldBe 1
        step.sliceRef shouldBe "product-core-v1"
        step.dependencies shouldBe listOf("raw-data")
    }

    "ExecutionStep - 빈 dependencies" {
        val step = ExecutionStep(
            stepNumber = 0,
            sliceRef = "initial-slice",
            dependencies = emptyList()
        )

        step.stepNumber shouldBe 0
        step.sliceRef shouldBe "initial-slice"
        step.dependencies shouldBe emptyList()
    }

    "ExecutionStep - 여러 dependencies" {
        val step = ExecutionStep(
            stepNumber = 3,
            sliceRef = "joined-slice",
            dependencies = listOf("slice-1", "slice-2", "slice-3")
        )

        step.stepNumber shouldBe 3
        step.dependencies.size shouldBe 3
        step.dependencies shouldBe listOf("slice-1", "slice-2", "slice-3")
    }

    "ExecutionStep - data class equality" {
        val step1 = ExecutionStep(1, "ref-1", listOf("dep-1"))
        val step2 = ExecutionStep(1, "ref-1", listOf("dep-1"))

        step1 shouldBe step2
    }

    "ExecutionStep - data class copy" {
        val original = ExecutionStep(
            stepNumber = 1,
            sliceRef = "slice-v1",
            dependencies = listOf("dep-1")
        )

        val modified = original.copy(
            stepNumber = 2,
            dependencies = listOf("dep-1", "dep-2")
        )

        modified.stepNumber shouldBe 2
        modified.sliceRef shouldBe "slice-v1"
        modified.dependencies shouldBe listOf("dep-1", "dep-2")
    }

    "ExecutionStep - 순차적 스텝 넘버링" {
        val steps = listOf(
            ExecutionStep(1, "step-1", emptyList()),
            ExecutionStep(2, "step-2", listOf("step-1")),
            ExecutionStep(3, "step-3", listOf("step-2"))
        )

        steps[0].stepNumber shouldBe 1
        steps[1].stepNumber shouldBe 2
        steps[2].stepNumber shouldBe 3
    }
})
