package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * DeployEvent 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class DeployEventTest : StringSpec({

    "DeployEvent.StartRunning - workerId 포함" {
        val event = DeployEvent.StartRunning("worker-123")
        event.shouldBeInstanceOf<DeployEvent.StartRunning>()
        event.workerId shouldBe "worker-123"
    }

    "DeployEvent.CompileComplete - data object" {
        val event = DeployEvent.CompileComplete
        event.shouldBeInstanceOf<DeployEvent>()
    }

    "DeployEvent.StartSinking - data object" {
        val event = DeployEvent.StartSinking
        event.shouldBeInstanceOf<DeployEvent>()
    }

    "DeployEvent.Complete - data object" {
        val event = DeployEvent.Complete
        event.shouldBeInstanceOf<DeployEvent>()
    }

    "DeployEvent.Failed - error 메시지 포함" {
        val event = DeployEvent.Failed("Compilation error")
        event.shouldBeInstanceOf<DeployEvent.Failed>()
        event.error shouldBe "Compilation error"
    }

    "DeployEvent.StartRunning - 다른 workerId로 생성 가능" {
        val event1 = DeployEvent.StartRunning("worker-1")
        val event2 = DeployEvent.StartRunning("worker-2")
        event1.workerId shouldBe "worker-1"
        event2.workerId shouldBe "worker-2"
    }

    "DeployEvent.Failed - 다른 에러 메시지로 생성 가능" {
        val event1 = DeployEvent.Failed("Error 1")
        val event2 = DeployEvent.Failed("Error 2")
        event1.error shouldBe "Error 1"
        event2.error shouldBe "Error 2"
    }
})
