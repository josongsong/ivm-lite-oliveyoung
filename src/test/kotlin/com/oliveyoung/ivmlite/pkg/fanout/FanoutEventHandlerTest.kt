package com.oliveyoung.ivmlite.pkg.fanout
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutEventHandler
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutResult
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutResultStatus
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutWorkflow
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID

/**
 * RFC-IMPL-012: FanoutEventHandler 테스트
 *
 * 이벤트 핸들러의 모든 시나리오 커버:
 * 1. EntityUpdated 이벤트 처리
 * 2. EntityCreated 이벤트 처리
 * 3. EntityDeleted 이벤트 처리
 * 4. FanoutRequested 명시적 요청
 * 5. 잘못된 payload 처리
 * 6. 우선순위 설정
 */
class FanoutEventHandlerTest : StringSpec({

    // ==================== 1. EntityUpdated 이벤트 처리 ====================

    "EntityUpdated - 정상 처리" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Ok(FanoutResult(
                status = FanoutResultStatus.SUCCESS,
                totalAffected = 5,
                processedCount = 5,
                skippedCount = 0,
                failedCount = 0,
            ))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 2,
                    "priority": "HIGH"
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then
        val keySlot = slot<EntityKey>()
        coVerify(exactly = 1) {
            fanoutWorkflow.onEntityChange(
                tenantId = TenantId("test-tenant"),
                upstreamEntityType = "brand",
                upstreamEntityKey = capture(keySlot),
                upstreamVersion = 2L,
                overrideConfig = any(),
            )
        }
        keySlot.captured.value shouldBe "BRAND#test-tenant#BR001"
    }

    "EntityUpdated - 부분 실패 시 경고 로그" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Ok(FanoutResult(
                status = FanoutResultStatus.PARTIAL_FAILURE,
                totalAffected = 10,
                processedCount = 8,
                skippedCount = 0,
                failedCount = 2,
            ))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 2
                }
            """.trimIndent()
        )

        // When - 부분 실패는 예외를 던지지 않음
        handler.handleSliceEvent(entry)

        // Then
        coVerify(exactly = 1) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    "EntityUpdated - 완전 실패 시 예외 발생" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Err(DomainError.StorageError("Connection failed"))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 2
                }
            """.trimIndent()
        )

        // When/Then
        shouldThrow<OutboxPollingWorker.ProcessingException> {
            handler.handleSliceEvent(entry)
        }
    }

    // ==================== 2. EntityCreated 이벤트 처리 ====================

    "EntityCreated - 정상 처리 (보통 fanout 없음)" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Ok(FanoutResult.empty())

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityCreated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR002",
                    "version": 1
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then
        coVerify(exactly = 1) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    // ==================== 3. EntityDeleted 이벤트 처리 ====================

    "EntityDeleted - 정상 처리" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Ok(FanoutResult(
                status = FanoutResultStatus.SUCCESS,
                totalAffected = 3,
                processedCount = 3,
                skippedCount = 0,
                failedCount = 0,
            ))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityDeleted",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 3
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then
        coVerify(exactly = 1) {
            fanoutWorkflow.onEntityChange(
                tenantId = TenantId("test-tenant"),
                upstreamEntityType = "brand",
                upstreamEntityKey = EntityKey("BRAND#test-tenant#BR001"),
                upstreamVersion = 3L,
                overrideConfig = null,
            )
        }
    }

    "EntityDeleted - 실패 시 예외 발생" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Err(DomainError.ValidationError("entity", "Cannot delete"))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityDeleted",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 3
                }
            """.trimIndent()
        )

        // When/Then
        shouldThrow<OutboxPollingWorker.ProcessingException> {
            handler.handleSliceEvent(entry)
        }
    }

    // ==================== 4. FanoutRequested 명시적 요청 ====================

    "FanoutRequested - 기본 요청" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) } returns
            Result.Ok(FanoutResult(
                status = FanoutResultStatus.SUCCESS,
                totalAffected = 10,
                processedCount = 10,
                skippedCount = 0,
                failedCount = 0,
            ))

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "FanoutRequested",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "category",
                    "entityKey": "CATEGORY#test-tenant#CAT001",
                    "version": 5,
                    "priority": "CRITICAL"
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then
        coVerify(exactly = 1) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    "FanoutRequested - configOverride 적용" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val configSlot = slot<FanoutConfig>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), capture(configSlot)) } returns
            Result.Ok(FanoutResult.empty())

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "FanoutRequested",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 2,
                    "configOverride": {
                        "batchSize": 25,
                        "maxFanout": 500
                    }
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then
        configSlot.captured.batchSize shouldBe 25
        configSlot.captured.maxFanout shouldBe 500
    }

    // ==================== 5. 잘못된 payload 처리 ====================

    "EntityUpdated - 잘못된 JSON payload" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = "{ invalid json }"
        )

        // When/Then
        shouldThrow<OutboxPollingWorker.ProcessingException> {
            handler.handleSliceEvent(entry)
        }

        coVerify(exactly = 0) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    "EntityUpdated - 필수 필드 누락" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant"
                }
            """.trimIndent()
        )

        // When/Then
        shouldThrow<OutboxPollingWorker.ProcessingException> {
            handler.handleSliceEvent(entry)
        }
    }

    // ==================== 6. 알 수 없는 이벤트 타입 ====================

    "알 수 없는 이벤트 타입 - 무시" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "UnknownEventType",
            payload = """{"foo": "bar"}"""
        )

        // When - 예외 없이 처리되어야 함
        handler.handleSliceEvent(entry)

        // Then
        coVerify(exactly = 0) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    // ==================== 7. ChangeSet 이벤트 (no-op) ====================

    "ChangeSet 이벤트 - no-op" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "ChangeSetCreated",
            payload = """{"changeSetId": "123"}"""
        )

        // When
        handler.handleChangeSetEvent(entry)

        // Then - fanoutWorkflow가 호출되지 않아야 함
        coVerify(exactly = 0) { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), any()) }
    }

    // ==================== 8. 엔티티 타입별 기본 설정 ====================

    "brand 타입 - 보수적 배치 크기" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val configSlot = slot<FanoutConfig>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), capture(configSlot)) } returns
            Result.Ok(FanoutResult.empty())

        val defaultConfig = FanoutConfig(batchSize = 100)
        val handler = FanoutEventHandler(fanoutWorkflow, defaultConfig)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "brand",
                    "entityKey": "BRAND#test-tenant#BR001",
                    "version": 2
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then - brand는 기본값보다 작은 배치 크기 사용
        configSlot.captured.batchSize shouldBe 50
    }

    "price 타입 - 높은 우선순위" {
        // Given
        val fanoutWorkflow = mockk<FanoutWorkflow>()
        val configSlot = slot<FanoutConfig>()
        coEvery { fanoutWorkflow.onEntityChange(any(), any(), any(), any(), capture(configSlot)) } returns
            Result.Ok(FanoutResult.empty())

        val handler = FanoutEventHandler(fanoutWorkflow)

        val entry = createOutboxEntry(
            eventType = "EntityUpdated",
            payload = """
                {
                    "tenantId": "test-tenant",
                    "entityType": "price",
                    "entityKey": "PRICE#test-tenant#P001",
                    "version": 5,
                    "priority": "NORMAL"
                }
            """.trimIndent()
        )

        // When
        handler.handleSliceEvent(entry)

        // Then - price는 NORMAL보다 높은 HIGH 우선순위로 승격
        configSlot.captured.priority shouldBe com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutPriority.HIGH
    }
})

// ==================== Helper Functions ====================

private fun createOutboxEntry(
    eventType: String,
    payload: String,
    id: UUID = UUID.randomUUID(),
): OutboxEntry {
    return OutboxEntry(
        id = id,
        idempotencyKey = "idem_${id}",
        aggregateType = com.oliveyoung.ivmlite.shared.domain.types.AggregateType.SLICE,
        aggregateId = "test-tenant:test-key",
        eventType = eventType,
        payload = payload,
        status = com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus.PENDING,
        createdAt = java.time.Instant.now(),
    )
}
