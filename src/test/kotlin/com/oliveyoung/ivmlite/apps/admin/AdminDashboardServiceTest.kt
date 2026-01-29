package com.oliveyoung.ivmlite.apps.admin

import com.oliveyoung.ivmlite.apps.admin.application.*
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import org.jooq.SelectSelectStep
import org.jooq.SelectJoinStep
import org.jooq.SelectConditionStep
import org.jooq.SelectLimitStep
import org.jooq.impl.DSL
import java.time.Instant
import java.util.UUID

/**
 * AdminDashboardService 단위 테스트
 *
 * SOTA TDD:
 * - 모든 public API 커버
 * - 성공/실패 케이스 분리
 * - MockK를 사용한 의존성 격리
 */
class AdminDashboardServiceTest : DescribeSpec({

    // Mocks
    lateinit var outboxRepo: OutboxRepositoryPort
    lateinit var worker: OutboxPollingWorker
    lateinit var dsl: DSLContext
    lateinit var service: AdminDashboardService

    beforeEach {
        outboxRepo = mockk(relaxed = true)
        worker = mockk(relaxed = true)
        dsl = mockk(relaxed = true)
        service = AdminDashboardService(outboxRepo, worker, dsl)
    }

    // getDashboard: jOOQ DSL 의존 → 통합 테스트에서 검증
    // 단위 테스트에서는 Repository port만 사용하는 메서드 검증

    describe("getWorkerStatus") {
        it("should return worker status when running") {
            // Given
            every { worker.isRunning() } returns true
            every { worker.getMetrics() } returns OutboxPollingWorker.Metrics(
                processed = 200,
                failed = 10,
                polls = 100,
                currentBackoffMs = 0L,
                isRunning = true
            )

            // When
            val result = service.getWorkerStatus()

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<WorkerStatus>>()
            val status = (result as AdminDashboardService.Result.Ok).value
            status.running shouldBe true
            status.processed shouldBe 200
            status.failed shouldBe 10
            status.polls shouldBe 100
        }

        it("should return worker status when stopped") {
            // Given
            every { worker.isRunning() } returns false
            every { worker.getMetrics() } returns OutboxPollingWorker.Metrics(
                processed = 0,
                failed = 0,
                polls = 0,
                currentBackoffMs = 0L,
                isRunning = false
            )

            // When
            val result = service.getWorkerStatus()

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<WorkerStatus>>()
            val status = (result as AdminDashboardService.Result.Ok).value
            status.running shouldBe false
        }
    }

    describe("getOutboxEntry") {
        it("should return outbox entry when found") {
            // Given
            val id = UUID.randomUUID()
            val entry = OutboxEntry(
                id = id,
                idempotencyKey = "test-key",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "tenant:entity-1",
                eventType = "CREATED",
                payload = """{"test": true}""",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                processedAt = null,
                retryCount = 0,
                failureReason = null
            )
            coEvery { outboxRepo.findById(id) } returns OutboxRepositoryPort.Result.Ok(entry)

            // When
            val result = service.getOutboxEntry(id)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<OutboxEntryDetail>>()
            val detail = (result as AdminDashboardService.Result.Ok).value
            detail.id shouldBe id.toString()
            detail.status shouldBe "PENDING"
            detail.aggregateType shouldBe "RAW_DATA"
        }

        it("should return error when entry not found") {
            // Given
            val id = UUID.randomUUID()
            coEvery { outboxRepo.findById(id) } returns OutboxRepositoryPort.Result.Err(
                DomainError.NotFoundError("OutboxEntry", id.toString())
            )

            // When
            val result = service.getOutboxEntry(id)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Err>()
        }
    }

    describe("retryEntry") {
        it("should reset failed entry to pending") {
            // Given
            val id = UUID.randomUUID()
            val entry = OutboxEntry(
                id = id,
                idempotencyKey = "test-key",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "tenant:entity-1",
                eventType = "CREATED",
                payload = """{"test": true}""",
                status = OutboxStatus.PENDING, // After reset
                createdAt = Instant.now(),
                processedAt = null,
                retryCount = 1,
                failureReason = null
            )
            coEvery { outboxRepo.resetToPending(id) } returns OutboxRepositoryPort.Result.Ok(entry)

            // When
            val result = service.retryEntry(id)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<OutboxEntryDetail>>()
            val detail = (result as AdminDashboardService.Result.Ok).value
            detail.status shouldBe "PENDING"
        }
    }

    describe("retryAllFailed") {
        it("should reset all failed entries") {
            // Given
            coEvery { outboxRepo.resetAllFailed(100) } returns OutboxRepositoryPort.Result.Ok(15)

            // When
            val result = service.retryAllFailed(100)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<Int>>()
            (result as AdminDashboardService.Result.Ok).value shouldBe 15
        }
    }

    describe("releaseStale") {
        it("should release stale processing entries") {
            // Given
            coEvery { outboxRepo.releaseExpiredClaims(300L) } returns OutboxRepositoryPort.Result.Ok(3)

            // When
            val result = service.releaseStale(300L)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<Int>>()
            (result as AdminDashboardService.Result.Ok).value shouldBe 3
        }
    }

    describe("replayDlq") {
        it("should replay entry from DLQ") {
            // Given
            val id = UUID.randomUUID()
            coEvery { outboxRepo.replayFromDlq(id) } returns OutboxRepositoryPort.Result.Ok(true)

            // When
            val result = service.replayDlq(id)

            // Then
            result.shouldBeInstanceOf<AdminDashboardService.Result.Ok<Boolean>>()
            (result as AdminDashboardService.Result.Ok).value shouldBe true
        }
    }
})
