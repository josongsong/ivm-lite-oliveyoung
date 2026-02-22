package com.oliveyoung.ivmlite.apps.admin

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.admin.application.*
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRawDataStats
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

/**
 * AdminDashboardService 단위 테스트 (SinkEvent + ExplorerRepositoryPort 기반)
 *
 * DynamoDB Streams 전환으로 Outbox 제거됨.
 */
class AdminDashboardServiceTest : DescribeSpec({

    lateinit var sinkEventRepo: SinkEventRepositoryPort
    lateinit var explorerRepo: ExplorerRepositoryPort
    lateinit var service: AdminDashboardService

    beforeEach {
        sinkEventRepo = InMemorySinkEventRepository()
        explorerRepo = mockk(relaxed = true)
        coEvery { explorerRepo.getRawDataStats(any<TenantId>()) } returns Either.Right(
            ExplorerRawDataStats(total = 0L, byTenant = emptyMap(), bySchema = emptyMap())
        )
        service = AdminDashboardService(sinkEventRepo, explorerRepo)
    }

    describe("getDashboard") {
        it("should return dashboard data") {
            val result = kotlinx.coroutines.runBlocking { service.getDashboard() }
            result.shouldBeInstanceOf<Result.Ok<DashboardData>>()
            val data = (result as Result.Ok).value
            data.sinkEvent.shouldBeInstanceOf<SinkEventStats>()
            data.worker.running shouldBe false
        }
    }

    describe("getWorkerStatus") {
        it("should return worker stopped (Worker 제거됨)") {
            val result = kotlinx.coroutines.runBlocking { service.getWorkerStatus() }
            result.shouldBeInstanceOf<Result.Ok<WorkerStatus>>()
            (result as Result.Ok).value.running shouldBe false
        }
    }

    describe("getSinkEventEntry") {
        it("should return SinkEvent when found") {
            val event = SinkEvent.create(
                tenantId = "t",
                entityKey = "product:1",
                version = 1L,
                viewType = "pdp",
                payload = "{}",
                sinkTargets = listOf("s3"),
                jobId = null
            )
            kotlinx.coroutines.runBlocking { sinkEventRepo.put(event) }

            val result = kotlinx.coroutines.runBlocking { service.getSinkEventEntry(event.id) }
            result.shouldBeInstanceOf<Result.Ok<SinkEventEntryDetail>>()
            (result as Result.Ok).value.id shouldBe event.id.toString()
        }

        it("should return error when not found") {
            val result = kotlinx.coroutines.runBlocking { service.getSinkEventEntry(UUID.randomUUID()) }
            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("retryEntry") {
        it("should return Err (미지원)") {
            val result = kotlinx.coroutines.runBlocking { service.retryEntry(UUID.randomUUID()) }
            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("getDlq") {
        it("should return empty (DLQ 미지원)") {
            val result = kotlinx.coroutines.runBlocking { service.getDlq(10) }
            result.shouldBeInstanceOf<Result.Ok<List<*>>>()
            (result as Result.Ok).value shouldBe emptyList()
        }
    }
})
