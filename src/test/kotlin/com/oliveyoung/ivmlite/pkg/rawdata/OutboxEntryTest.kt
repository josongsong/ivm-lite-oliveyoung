package com.oliveyoung.ivmlite.pkg.rawdata

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxEntryTest {

    // ==================== 생성 테스트 ====================

    @Test
    fun `create 팩토리 - PENDING 상태로 생성`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:product-123",
            eventType = "RawDataIngested",
            payload = """{"key":"value"}""",
        )

        assertEquals(OutboxStatus.PENDING, entry.status)
        assertEquals(AggregateType.RAW_DATA, entry.aggregateType)
        assertEquals("tenant-1:product-123", entry.aggregateId)
        assertEquals("RawDataIngested", entry.eventType)
        assertEquals(0, entry.retryCount)
        assertNull(entry.processedAt)
        assertNull(entry.failureReason)
        assertNotNull(entry.id)
        assertNotNull(entry.createdAt)
        assertNotNull(entry.idempotencyKey)
        assertTrue(entry.idempotencyKey.startsWith("idem_"))
    }
    
    @Test
    fun `idempotencyKey - 동일 입력으로 결정적 생성`() {
        val entry1 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:product-123",
            eventType = "RawDataIngested",
            payload = """{"key":"value"}""",
        )
        val entry2 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:product-123",
            eventType = "RawDataIngested",
            payload = """{"key":"value"}""",
        )
        
        // ID는 다르지만 idempotencyKey는 동일
        assertNotEquals(entry1.id, entry2.id)
        assertEquals(entry1.idempotencyKey, entry2.idempotencyKey)
    }

    @Test
    fun `생성자 - 모든 필드 직접 지정`() {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val entry = OutboxEntry(
            id = id,
            idempotencyKey = "idem_test_key_12345",
            aggregateType = AggregateType.SLICE,
            aggregateId = "tenant-2:order-456",
            eventType = "SliceCreated",
            payload = """{"sliceType":"CORE"}""",
            status = OutboxStatus.PROCESSED,
            createdAt = now,
            processedAt = now.plusSeconds(1),
            retryCount = 2,
        )

        assertEquals(id, entry.id)
        assertEquals(OutboxStatus.PROCESSED, entry.status)
        assertEquals(2, entry.retryCount)
    }

    // ==================== Validation 테스트 ====================

    @Test
    fun `검증 실패 - aggregateId 빈 문자열`() {
        val ex = assertThrows<IllegalArgumentException> {
            OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "",
                eventType = "Test",
                payload = "{}",
            )
        }
        assertTrue(ex.message!!.contains("blank"))
    }

    @Test
    fun `검증 실패 - aggregateId 콜론 없음`() {
        val ex = assertThrows<IllegalArgumentException> {
            OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "tenant-only",
                eventType = "Test",
                payload = "{}",
            )
        }
        assertTrue(ex.message!!.contains("tenantId:entityKey"))
    }

    @Test
    fun `검증 실패 - eventType 빈 문자열`() {
        val ex = assertThrows<IllegalArgumentException> {
            OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "t:e",
                eventType = "   ",
                payload = "{}",
            )
        }
        assertTrue(ex.message!!.contains("eventType"))
    }

    @Test
    fun `검증 실패 - payload 빈 문자열`() {
        val ex = assertThrows<IllegalArgumentException> {
            OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "t:e",
                eventType = "Test",
                payload = "",
            )
        }
        assertTrue(ex.message!!.contains("payload"))
    }

    @Test
    fun `검증 실패 - retryCount 음수`() {
        val ex = assertThrows<IllegalArgumentException> {
            OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "idem_test",
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "t:e",
                eventType = "Test",
                payload = "{}",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                retryCount = -1,
            )
        }
        assertTrue(ex.message!!.contains("non-negative"))
    }

    // ==================== 상태 전이 테스트 ====================

    @Test
    fun `markProcessed - PENDING에서 PROCESSED로 전이`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )
        val processedAt = Instant.now()

        val processed = entry.markProcessed(processedAt)

        assertEquals(OutboxStatus.PROCESSED, processed.status)
        assertEquals(processedAt, processed.processedAt)
        assertEquals(0, processed.retryCount) // 변경 없음
    }

    @Test
    fun `markFailed - retryCount 증가 및 reason 기록`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )

        val failed = entry.markFailed("Connection timeout")

        assertEquals(OutboxStatus.FAILED, failed.status)
        assertEquals(1, failed.retryCount)
        assertEquals("Connection timeout", failed.failureReason)
    }
    
    @Test
    fun `markFailed - reason 없이 호출 가능`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )

        val failed = entry.markFailed()

        assertEquals(OutboxStatus.FAILED, failed.status)
        assertEquals(1, failed.retryCount)
        assertNull(failed.failureReason)
    }

    @Test
    fun `markFailed 연속 호출 - retryCount 계속 증가`() {
        var entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )

        repeat(3) { entry = entry.markFailed() }

        assertEquals(3, entry.retryCount)
    }

    // ==================== 재시도 테스트 ====================

    @Test
    fun `canRetry - MAX_RETRY_COUNT 미만이면 true`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_test",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now(),
            retryCount = OutboxEntry.MAX_RETRY_COUNT - 1,
        )

        assertTrue(entry.canRetry())
    }

    @Test
    fun `canRetry - MAX_RETRY_COUNT 이상이면 false`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_test",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now(),
            retryCount = OutboxEntry.MAX_RETRY_COUNT,
        )

        assertFalse(entry.canRetry())
    }

    @Test
    fun `resetToPending - 재시도 가능하면 PENDING으로 전환`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_test",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now(),
            retryCount = 2,
        )

        val reset = entry.resetToPending()

        assertEquals(OutboxStatus.PENDING, reset.status)
        assertEquals(2, reset.retryCount) // 변경 없음
    }

    @Test
    fun `resetToPending - MAX_RETRY_COUNT 초과시 예외`() {
        val entry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = "idem_test",
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.FAILED,
            createdAt = Instant.now(),
            retryCount = OutboxEntry.MAX_RETRY_COUNT,
        )

        val ex = assertThrows<IllegalStateException> {
            entry.resetToPending()
        }
        assertTrue(ex.message!!.contains("exceeded"))
    }

    // ==================== ID 추출 테스트 ====================

    @Test
    fun `extractTenantId - 콜론 앞부분 추출`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-abc:entity-xyz",
            eventType = "Test",
            payload = "{}",
        )

        assertEquals("tenant-abc", entry.extractTenantId())
    }

    @Test
    fun `extractEntityKey - 콜론 뒷부분 추출`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-abc:entity-xyz",
            eventType = "Test",
            payload = "{}",
        )

        assertEquals("entity-xyz", entry.extractEntityKey())
    }

    @Test
    fun `extractEntityKey - 다중 콜론 포함시 첫 콜론 이후 전체`() {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant:entity:with:colons",
            eventType = "Test",
            payload = "{}",
        )

        assertEquals("entity:with:colons", entry.extractEntityKey())
    }

    // ==================== 불변성 테스트 ====================

    @Test
    fun `상태 전이 메서드는 새 인스턴스 반환 (불변성)`() {
        val original = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )

        val processed = original.markProcessed()
        val failed = original.markFailed()

        // 원본 불변
        assertEquals(OutboxStatus.PENDING, original.status)
        assertEquals(0, original.retryCount)

        // 새 인스턴스 생성
        assertEquals(OutboxStatus.PROCESSED, processed.status)
        assertEquals(OutboxStatus.FAILED, failed.status)
    }

    // ==================== 모든 AggregateType 테스트 ====================

    @Test
    fun `모든 AggregateType으로 생성 가능`() {
        AggregateType.entries.forEach { type ->
            val entry = OutboxEntry.create(
                aggregateType = type,
                aggregateId = "t:e",
                eventType = "Test",
                payload = "{}",
            )
            assertEquals(type, entry.aggregateType)
        }
    }

    // ==================== Edge Case 테스트 ====================

    @Test
    fun `aggregateId 최소 형식 (콜론만)`() {
        // 콜론이 있으면 valid, 빈 tenantId나 entityKey도 허용 (비즈니스 로직에서 검증)
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = ":",
            eventType = "Test",
            payload = "{}",
        )

        assertEquals("", entry.extractTenantId())
        assertEquals("", entry.extractEntityKey())
    }

    @Test
    fun `대용량 payload 허용`() {
        val largePayload = """{"data":"${"x".repeat(100_000)}"}"""

        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = largePayload,
        )

        assertEquals(largePayload, entry.payload)
    }
}
