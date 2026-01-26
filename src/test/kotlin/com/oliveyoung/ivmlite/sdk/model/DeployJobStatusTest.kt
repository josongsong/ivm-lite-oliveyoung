package com.oliveyoung.ivmlite.sdk.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * DeployJobStatus 테스트
 * RFC-IMPL-011 Wave 1-B
 */
class DeployJobStatusTest : StringSpec({

    "DeployJobStatus - 생성 및 모든 필드 확인" {
        val now = Instant.now()
        val status = DeployJobStatus(
            jobId = "job-123",
            state = DeployState.QUEUED,
            createdAt = now,
            updatedAt = now,
            error = null
        )

        status.jobId shouldBe "job-123"
        status.state shouldBe DeployState.QUEUED
        status.createdAt shouldBe now
        status.updatedAt shouldBe now
        status.error shouldBe null
    }

    "DeployJobStatus - error 메시지 포함" {
        val now = Instant.now()
        val status = DeployJobStatus(
            jobId = "job-456",
            state = DeployState.FAILED,
            createdAt = now,
            updatedAt = now.plusSeconds(10),
            error = "Compilation failed"
        )

        status.jobId shouldBe "job-456"
        status.state shouldBe DeployState.FAILED
        status.error shouldBe "Compilation failed"
    }

    "DeployJobStatus - error null이 기본값" {
        val now = Instant.now()
        val status = DeployJobStatus(
            jobId = "job-789",
            state = DeployState.RUNNING,
            createdAt = now,
            updatedAt = now
        )

        status.error shouldBe null
    }

    "DeployJobStatus - 다양한 상태로 생성 가능" {
        val now = Instant.now()

        val queued = DeployJobStatus("1", DeployState.QUEUED, now, now)
        val running = DeployJobStatus("2", DeployState.RUNNING, now, now)
        val ready = DeployJobStatus("3", DeployState.READY, now, now)
        val sinking = DeployJobStatus("4", DeployState.SINKING, now, now)
        val done = DeployJobStatus("5", DeployState.DONE, now, now)
        val failed = DeployJobStatus("6", DeployState.FAILED, now, now, "Error")

        queued.state shouldBe DeployState.QUEUED
        running.state shouldBe DeployState.RUNNING
        ready.state shouldBe DeployState.READY
        sinking.state shouldBe DeployState.SINKING
        done.state shouldBe DeployState.DONE
        failed.state shouldBe DeployState.FAILED
        failed.error shouldBe "Error"
    }

    "DeployJobStatus - data class copy" {
        val now = Instant.now()
        val original = DeployJobStatus(
            jobId = "job-1",
            state = DeployState.QUEUED,
            createdAt = now,
            updatedAt = now
        )

        val updated = original.copy(
            state = DeployState.RUNNING,
            updatedAt = now.plusSeconds(5)
        )

        updated.jobId shouldBe original.jobId
        updated.state shouldBe DeployState.RUNNING
        updated.createdAt shouldBe original.createdAt
        updated.updatedAt shouldBe now.plusSeconds(5)
    }
})
