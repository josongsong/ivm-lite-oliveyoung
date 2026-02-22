package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRawDataStats
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertTrue

/**
 * AdminDashboardService 단위 테스트 (SinkEvent + ExplorerRepositoryPort 기반)
 */
class AdminDashboardServiceTest {

    private lateinit var sinkEventRepo: SinkEventRepositoryPort
    private lateinit var explorerRepo: ExplorerRepositoryPort
    private lateinit var service: AdminDashboardService

    @BeforeEach
    fun setup() {
        sinkEventRepo = InMemorySinkEventRepository()
        explorerRepo = mockk(relaxed = true)
        coEvery { explorerRepo.getRawDataStats(any<TenantId>()) } returns Either.Right(
            ExplorerRawDataStats(total = 0L, byTenant = emptyMap(), bySchema = emptyMap())
        )
        service = AdminDashboardService(sinkEventRepo, explorerRepo)
    }

    @Test
    fun `getWorkerStatus returns stopped`() = runTest {
        val result = service.getWorkerStatus()
        assertTrue(result is Result.Ok)
        assertTrue(!(result as Result.Ok).value.running)
    }

    @Test
    fun `getSinkEventEntry returns entry when found`() = runTest {
        val event = SinkEvent.create(
            tenantId = "t",
            entityKey = "product:1",
            version = 1L,
            viewType = "pdp",
            payload = "{}",
            sinkTargets = listOf("s3"),
            jobId = null
        )
        sinkEventRepo.put(event)

        val result = service.getSinkEventEntry(event.id)
        assertTrue(result is Result.Ok)
        assertTrue((result as Result.Ok).value.id == event.id.toString())
    }

    @Test
    fun `getSinkEventEntry returns error when not found`() = runTest {
        val result = service.getSinkEventEntry(UUID.randomUUID())
        assertTrue(result is Result.Err)
    }

    @Test
    fun `retryEntry returns Err`() = runTest {
        val result = service.retryEntry(UUID.randomUUID())
        assertTrue(result is Result.Err)
    }

    @Test
    fun `getDlq returns empty list`() = runTest {
        val result = service.getDlq(10)
        assertTrue(result is Result.Ok)
        assertTrue((result as Result.Ok).value.isEmpty())
    }
}
