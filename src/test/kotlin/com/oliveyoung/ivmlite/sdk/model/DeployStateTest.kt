package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * DeployState 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class DeployStateTest : StringSpec({

    "DeployState - 모든 상태 값 존재" {
        DeployState.QUEUED.name shouldBe "QUEUED"
        DeployState.RUNNING.name shouldBe "RUNNING"
        DeployState.READY.name shouldBe "READY"
        DeployState.SINKING.name shouldBe "SINKING"
        DeployState.DONE.name shouldBe "DONE"
        DeployState.FAILED.name shouldBe "FAILED"
    }

    "DeployState - enum entries 개수" {
        DeployState.entries.size shouldBe 6
    }

    "DeployState - valueOf 정상 변환" {
        DeployState.valueOf("QUEUED") shouldBe DeployState.QUEUED
        DeployState.valueOf("RUNNING") shouldBe DeployState.RUNNING
        DeployState.valueOf("READY") shouldBe DeployState.READY
        DeployState.valueOf("SINKING") shouldBe DeployState.SINKING
        DeployState.valueOf("DONE") shouldBe DeployState.DONE
        DeployState.valueOf("FAILED") shouldBe DeployState.FAILED
    }

    "DeployState - toString 기본 동작" {
        DeployState.QUEUED.toString() shouldBe "QUEUED"
        DeployState.RUNNING.toString() shouldBe "RUNNING"
    }
})
