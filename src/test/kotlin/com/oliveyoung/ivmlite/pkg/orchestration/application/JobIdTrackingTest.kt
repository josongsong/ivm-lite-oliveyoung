package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * JobId end-to-end 추적 테스트 (LEGACY - Outbox 제거됨)
 *
 * 🔥 새 아키텍처:
 * - IngestWorkflow는 RawData 저장만 수행 (Outbox 제거)
 * - jobId 추적은 IngestionOrchestrator → SinkEvent로 이동
 * - 이 테스트는 IngestionOrchestratorTest.kt로 대체됨
 *
 * RFC-019: External SDK Integration
 */
class JobIdTrackingTest : DescribeSpec({

    describe("IngestWorkflow (LEGACY - RawData 저장만, Outbox 제거됨)") {
        it("✅ RawData 저장 성공 (jobId는 무시됨)") {
            // Given
            val rawRepo = InMemoryRawDataRepository()
            val workflow = IngestWorkflow(rawRepo)

            val jobId = "test-job-12345"
            val tenantId = TenantId("test-tenant")
            val entityKey = EntityKey("product:001")

            // When
            val result = workflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "test.schema",
                schemaVersion = SemVer(1, 0, 0),
                payloadJson = """{"name":"Test Product"}""",
                jobId = jobId, // 레거시 호환: 파라미터는 받지만 무시됨
            )

            // Then
            result.shouldBeInstanceOf<Result.Ok<Unit>>()

            // NOTE: Outbox 제거됨 - jobId 추적은 IngestionOrchestrator → SinkEvent에서 수행
        }

        it("✅ jobId null일 때도 정상 동작") {
            // Given
            val rawRepo = InMemoryRawDataRepository()
            val workflow = IngestWorkflow(rawRepo)

            // When
            val result = workflow.execute(
                tenantId = TenantId("test-tenant"),
                entityKey = EntityKey("product:002"),
                version = 1L,
                schemaId = "test.schema",
                schemaVersion = SemVer(1, 0, 0),
                payloadJson = """{"name":"Test Product 2"}""",
                jobId = null,
            )

            // Then
            result.shouldBeInstanceOf<Result.Ok<Unit>>()
        }
    }
})
