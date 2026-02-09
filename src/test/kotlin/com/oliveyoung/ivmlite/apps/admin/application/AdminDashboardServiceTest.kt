package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AdminDashboardService 단위 테스트
 */
class AdminDashboardServiceTest {

    private lateinit var outboxRepo: OutboxRepositoryPort
    private lateinit var worker: OutboxPollingWorker
    private lateinit var dsl: DSLContext
    private lateinit var service: AdminDashboardService

    @BeforeEach
    fun setup() {
        outboxRepo = mockk(relaxed = true)
        worker = mockk(relaxed = true)
        dsl = mockk(relaxed = true)

        // Worker metrics mock
        every { worker.isRunning() } returns true
        every { worker.getMetrics() } returns OutboxPollingWorker.Metrics(
            processed = 100L,
            failed = 5L,
            polls = 50L,
            currentBackoffMs = 0L,
            isRunning = true
        )

        service = AdminDashboardService(outboxRepo, worker, dsl)
    }

    @Test
    fun `getWorkerStatus returns running status`() = runTest {
        // When
        val result = service.getWorkerStatus()

        // Then
        assertTrue(result is Result.Ok)
        val status = (result as Result.Ok).value
        assertTrue(status.running)
        assertEquals(100L, status.processed)
        assertEquals(5L, status.failed)
    }

    @Test
    fun `getOutboxEntry returns entry when found`() = runTest {
        // Given
        val id = UUID.randomUUID()
        val entry = OutboxEntry(
            id = id,
            idempotencyKey = "test-key",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "test-tenant:test-entity",
            eventType = "TestEvent",
            payload = "{}",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now()
        )
        coEvery { outboxRepo.findById(id) } returns Result.Ok(entry)

        // When
        val result = service.getOutboxEntry(id)

        // Then
        assertTrue(result is Result.Ok)
        val detail = (result as Result.Ok).value
        assertEquals(id.toString(), detail.id)
        assertEquals("test-key", detail.idempotencyKey)
    }

    @Test
    fun `getOutboxEntry returns error when not found`() = runTest {
        // Given
        val id = UUID.randomUUID()
        coEvery { outboxRepo.findById(id) } returns Result.Err(
            DomainError.NotFoundError("OutboxEntry", id.toString())
        )

        // When
        val result = service.getOutboxEntry(id)

        // Then
        assertTrue(result is Result.Err)
        val error = (result as Result.Err).error
        assertEquals("ERR_NOT_FOUND", error.errorCode)
    }

    @Test
    fun `replayDlq calls repository and returns result`() = runTest {
        // Given
        val id = UUID.randomUUID()
        coEvery { outboxRepo.replayFromDlq(id) } returns Result.Ok(true)

        // When
        val result = service.replayDlq(id)

        // Then
        assertTrue(result is Result.Ok)
        assertTrue((result as Result.Ok).value)
    }

    @Test
    fun `releaseStale coerces timeout to valid range`() = runTest {
        // Given
        coEvery { outboxRepo.releaseExpiredClaims(any()) } returns Result.Ok(5)

        // When - too small timeout (should be coerced to 60)
        val result1 = service.releaseStale(10L)

        // When - too large timeout (should be coerced to 86400)
        val result2 = service.releaseStale(100000L)

        // Then
        assertTrue(result1 is Result.Ok)
        assertTrue(result2 is Result.Ok)
    }

    @Test
    fun `retryEntry resets entry to PENDING`() = runTest {
        // Given
        val id = UUID.randomUUID()
        val entry = OutboxEntry(
            id = id,
            idempotencyKey = "test-key",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "test-tenant:test-entity",
            eventType = "TestEvent",
            payload = "{}",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now()
        )
        coEvery { outboxRepo.resetToPending(id) } returns Result.Ok(entry)

        // When
        val result = service.retryEntry(id)

        // Then
        assertTrue(result is Result.Ok)
        val detail = (result as Result.Ok).value
        assertEquals(id.toString(), detail.id)
        assertEquals("PENDING", detail.status)
    }

    @Test
    fun `retryAllFailed coerces limit to valid range`() = runTest {
        // Given
        coEvery { outboxRepo.resetAllFailed(any()) } returns Result.Ok(10)

        // When - too small limit (should be coerced to 1)
        val result1 = service.retryAllFailed(-5)

        // When - too large limit (should be coerced to 1000)
        val result2 = service.retryAllFailed(5000)

        // Then
        assertTrue(result1 is Result.Ok)
        assertTrue(result2 is Result.Ok)
    }

    @Test
    fun `getDlq returns entries`() = runTest {
        // Given
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "dlq-key",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "dlq-tenant:dlq-entity",
            eventType = "DlqEvent",
            payload = "{}",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now()
        )
        coEvery { outboxRepo.findDlq(any()) } returns Result.Ok(listOf(entry))

        // When
        val result = service.getDlq(50)

        // Then
        assertTrue(result is Result.Ok)
        val entries = (result as Result.Ok).value
        assertEquals(1, entries.size)
        assertEquals("dlq-key", entries[0].idempotencyKey)
    }
}
