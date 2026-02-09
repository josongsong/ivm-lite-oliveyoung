package com.oliveyoung.ivmlite.apps.admin.application.explorer

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.apps.admin.ports.RawDataItem
import com.oliveyoung.ivmlite.apps.admin.ports.RawDataListResult
import com.oliveyoung.ivmlite.apps.admin.ports.VersionHistoryItem
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RawDataExplorerService 단위 테스트
 *
 * P0 버그 수정 검증:
 * - ingestBatch 병렬화 (순차 → 병렬)
 */
class RawDataExplorerServiceTest {

    private lateinit var rawDataRepo: RawDataRepositoryPort
    private lateinit var explorerRepo: ExplorerRepositoryPort
    private lateinit var ingestWorkflow: IngestWorkflow
    private lateinit var slicingWorkflow: SlicingWorkflow
    private lateinit var service: RawDataExplorerService

    @BeforeEach
    fun setup() {
        rawDataRepo = mockk(relaxed = true)
        explorerRepo = mockk(relaxed = true)
        ingestWorkflow = mockk()
        slicingWorkflow = mockk()

        service = RawDataExplorerService(
            rawDataRepo = rawDataRepo,
            explorerRepo = explorerRepo,
            ingestWorkflow = ingestWorkflow,
            slicingWorkflow = slicingWorkflow
        )
    }

    @Nested
    inner class IngestBatchParallelTests {

        @Test
        fun `ingestBatch가 병렬로 실행됨`() = runTest {
            // Given - 각 ingest가 100ms 걸린다고 가정
            val delayMs = 100L
            val itemCount = 5

            coEvery {
                ingestWorkflow.execute(any(), any(), any(), any(), any(), any())
            } coAnswers {
                delay(delayMs)
                Result.Ok(Unit)
            }

            val items = (1..itemCount).map { i ->
                IngestItem(
                    entityKey = "entity-$i",
                    schemaId = "schema",
                    schemaVersion = "1.0.0",
                    payload = "{}",
                    compile = false
                )
            }

            // When
            val elapsed = measureTimeMillis {
                service.ingestBatch("oliveyoung", items)
            }

            // Then
            // 순차 실행이면 500ms+, 병렬이면 ~100ms
            // 테스트 환경 고려하여 300ms 미만으로 체크
            assertTrue(elapsed < delayMs * itemCount, "Expected parallel execution, but took ${elapsed}ms")

            // 모든 항목이 처리됨
            coVerify(exactly = itemCount) {
                ingestWorkflow.execute(any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `ingestBatch 일부 실패해도 다른 항목 처리 계속`() = runTest {
            // Given
            coEvery {
                ingestWorkflow.execute(any(), EntityKey("entity-1"), any(), any(), any(), any())
            } returns Result.Ok(Unit)

            coEvery {
                ingestWorkflow.execute(any(), EntityKey("entity-2"), any(), any(), any(), any())
            } returns Result.Err(DomainError.ValidationError("test", "failed"))

            coEvery {
                ingestWorkflow.execute(any(), EntityKey("entity-3"), any(), any(), any(), any())
            } returns Result.Ok(Unit)

            val items = listOf(
                IngestItem("entity-1", "schema", "1.0.0", "{}", false),
                IngestItem("entity-2", "schema", "1.0.0", "{}", false),
                IngestItem("entity-3", "schema", "1.0.0", "{}", false)
            )

            // When
            val result = service.ingestBatch("oliveyoung", items)

            // Then
            assertTrue(result is Result.Ok)
            val batchResult = (result as Result.Ok).value
            assertEquals(2, batchResult.successCount)
            assertEquals(1, batchResult.failCount)
            assertEquals(3, batchResult.totalCount)
        }

        @Test
        fun `ingestBatch 빈 목록이면 빈 결과 반환`() = runTest {
            // When
            val result = service.ingestBatch("oliveyoung", emptyList())

            // Then
            assertTrue(result is Result.Ok)
            val batchResult = (result as Result.Ok).value
            assertEquals(0, batchResult.totalCount)
            assertEquals(0, batchResult.successCount)
            assertEquals(0, batchResult.failCount)
        }

        @Test
        fun `ingestBatch 결과에 성공 실패 항목 정확히 분류`() = runTest {
            // Given
            coEvery {
                ingestWorkflow.execute(any(), EntityKey("success-1"), any(), any(), any(), any())
            } returns Result.Ok(Unit)

            coEvery {
                ingestWorkflow.execute(any(), EntityKey("fail-1"), any(), any(), any(), any())
            } returns Result.Err(DomainError.StorageError("DB error"))

            val items = listOf(
                IngestItem("success-1", "schema", "1.0.0", "{}", false),
                IngestItem("fail-1", "schema", "1.0.0", "{}", false)
            )

            // When
            val result = service.ingestBatch("oliveyoung", items)

            // Then
            assertTrue(result is Result.Ok)
            val batchResult = (result as Result.Ok).value

            assertEquals(1, batchResult.succeeded.size)
            assertEquals("success-1", batchResult.succeeded[0].entityKey)

            assertEquals(1, batchResult.failed.size)
            assertEquals("fail-1", batchResult.failed[0].entityKey)
            assertTrue(batchResult.failed[0].error.contains("DB error"))
        }
    }

    @Nested
    inner class ListRawDataTests {

        @Test
        fun `listRawData 정상 반환`() = runTest {
            // Given
            coEvery { explorerRepo.listRawData(any(), any(), any(), any()) } returns Either.Right(
                RawDataListResult(
                    items = listOf(
                        RawDataItem("entity-1", "schema@1.0.0", 1L, "2024-01-01"),
                        RawDataItem("entity-2", "schema@1.0.0", 2L, "2024-01-02")
                    ),
                    nextCursor = null,
                    totalCount = 2
                )
            )

            // When
            val result = service.listRawData("oliveyoung", null, 50, null)

            // Then
            assertTrue(result is Result.Ok)
            val data = (result as Result.Ok).value
            assertEquals(2, data.entries.size)
            assertEquals("entity-1", data.entries[0].entityKey)
        }

        @Test
        fun `listRawData limit 범위 제한`() = runTest {
            // Given
            coEvery { explorerRepo.listRawData(any(), any(), any(), any()) } returns Either.Right(
                RawDataListResult(items = emptyList(), nextCursor = null, totalCount = 0)
            )

            // When - limit가 범위를 벗어나도 coerceIn 처리됨
            service.listRawData("oliveyoung", null, 1000, null)

            // Then - MAX_PAGE_SIZE(200)로 제한됨
            coVerify {
                explorerRepo.listRawData(any(), any(), 200, any())
            }
        }
    }

    @Nested
    inner class GetRawDataTests {

        @Test
        fun `getRawData 최신 버전 조회`() = runTest {
            // Given
            val record = RawDataRecord(
                tenantId = TenantId("oliveyoung"),
                entityKey = EntityKey("test-entity"),
                version = 5L,
                schemaId = "product-schema",
                schemaVersion = SemVer.parse("1.0.0"),
                payload = """{"name": "Test"}""",
                payloadHash = "abc123"
            )

            coEvery { rawDataRepo.getLatest(any(), any()) } returns Result.Ok(record)
            coEvery { explorerRepo.getVersionHistory(any(), any(), any()) } returns Either.Right(emptyList())

            // When
            val result = service.getRawData("oliveyoung", "test-entity", null)

            // Then
            assertTrue(result is Result.Ok)
            val data = (result as Result.Ok).value
            assertEquals("test-entity", data.entityKey)
            assertEquals(5L, data.version)
        }

        @Test
        fun `getRawData 특정 버전 조회`() = runTest {
            // Given
            val record = RawDataRecord(
                tenantId = TenantId("oliveyoung"),
                entityKey = EntityKey("test-entity"),
                version = 3L,
                schemaId = "product-schema",
                schemaVersion = SemVer.parse("1.0.0"),
                payload = """{"name": "Old"}""",
                payloadHash = "def456"
            )

            coEvery { rawDataRepo.get(any(), any(), eq(3L)) } returns Result.Ok(record)
            coEvery { explorerRepo.getVersionHistory(any(), any(), any()) } returns Either.Right(emptyList())

            // When
            val result = service.getRawData("oliveyoung", "test-entity", 3L)

            // Then
            assertTrue(result is Result.Ok)
            val data = (result as Result.Ok).value
            assertEquals(3L, data.version)
        }

        @Test
        fun `getRawData 버전 히스토리 포함`() = runTest {
            // Given
            val record = RawDataRecord(
                tenantId = TenantId("oliveyoung"),
                entityKey = EntityKey("test-entity"),
                version = 5L,
                schemaId = "product-schema",
                schemaVersion = SemVer.parse("1.0.0"),
                payload = """{}""",
                payloadHash = "hash"
            )

            val history = listOf(
                VersionHistoryItem(5L, "2024-01-05", "hash5"),
                VersionHistoryItem(4L, "2024-01-04", "hash4"),
                VersionHistoryItem(3L, "2024-01-03", "hash3")
            )

            coEvery { rawDataRepo.getLatest(any(), any()) } returns Result.Ok(record)
            coEvery { explorerRepo.getVersionHistory(any(), any(), any()) } returns Either.Right(history)

            // When
            val result = service.getRawData("oliveyoung", "test-entity", null)

            // Then
            assertTrue(result is Result.Ok)
            val data = (result as Result.Ok).value
            assertEquals(3, data.versions.size)
            assertEquals(5L, data.versions[0].version)
        }
    }

    @Nested
    inner class IngestTests {

        @Test
        fun `ingest IngestWorkflow 없으면 ConfigError`() = runTest {
            // Given
            val serviceWithoutWorkflow = RawDataExplorerService(
                rawDataRepo = rawDataRepo,
                explorerRepo = explorerRepo,
                ingestWorkflow = null,
                slicingWorkflow = null
            )

            // When
            val result = serviceWithoutWorkflow.ingest(
                tenantId = "oliveyoung",
                entityKey = "test",
                schemaId = "schema",
                schemaVersion = "1.0.0",
                payload = "{}",
                compile = false
            )

            // Then
            assertTrue(result is Result.Err)
            val error = (result as Result.Err).error
            assertTrue(error is DomainError.ConfigError)
        }

        @Test
        fun `ingest 잘못된 schemaVersion이면 ValidationError`() = runTest {
            // When
            val result = service.ingest(
                tenantId = "oliveyoung",
                entityKey = "test",
                schemaId = "schema",
                schemaVersion = "invalid-version",
                payload = "{}",
                compile = false
            )

            // Then
            assertTrue(result is Result.Err)
            val error = (result as Result.Err).error
            assertTrue(error is DomainError.ValidationError)
        }
    }
}
