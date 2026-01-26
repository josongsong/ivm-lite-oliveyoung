package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * StateError 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class StateErrorTest : StringSpec({

    "StateError.InvalidTransition - 생성 및 필드 확인" {
        val error = StateError.InvalidTransition(
            current = DeployState.QUEUED,
            event = DeployEvent.CompileComplete
        )

        error.shouldBeInstanceOf<StateError.InvalidTransition>()
        error.current shouldBe DeployState.QUEUED
        error.event shouldBe DeployEvent.CompileComplete
    }

    "StateError.InvalidTransition - RUNNING 상태에서 StartSinking 이벤트" {
        val error = StateError.InvalidTransition(
            current = DeployState.RUNNING,
            event = DeployEvent.StartSinking
        )

        error.current shouldBe DeployState.RUNNING
        error.event shouldBe DeployEvent.StartSinking
    }

    "StateError.InvalidTransition - data class equality" {
        val error1 = StateError.InvalidTransition(
            current = DeployState.QUEUED,
            event = DeployEvent.Complete
        )
        val error2 = StateError.InvalidTransition(
            current = DeployState.QUEUED,
            event = DeployEvent.Complete
        )

        error1 shouldBe error2
    }

    "StateError.InvalidTransition - Failed 이벤트 포함" {
        val failedEvent = DeployEvent.Failed("Test error")
        val error = StateError.InvalidTransition(
            current = DeployState.DONE,
            event = failedEvent
        )

        error.event.shouldBeInstanceOf<DeployEvent.Failed>()
        (error.event as DeployEvent.Failed).error shouldBe "Test error"
    }
})
