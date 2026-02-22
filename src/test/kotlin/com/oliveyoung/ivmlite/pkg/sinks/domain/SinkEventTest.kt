package com.oliveyoung.ivmlite.pkg.sinks.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SinkEvent 도메인 모델 테스트
 *
 * 검증 항목:
 * - 생성 시 자동 필드 (UUID, TTL, 타임스탬프)
 * - Idempotency Key 생성
 * - 상태 전이
 */
class SinkEventTest {

    @Test
    fun `create - 기본 SinkEvent 생성`() {
        // When
        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123","name":"Product A"}""",
            sinkTargets = listOf("s3-raw", "opensearch"),
            jobId = "job-001"
        )

        // Then
        assertNotNull(event.id)
        assertEquals("oliveyoung", event.tenantId)
        assertEquals("product:123", event.entityKey)
        assertEquals(1L, event.version)
        assertEquals("view-product-core", event.viewType)
        assertEquals(SinkEventStatus.PENDING, event.status)
        assertEquals("job-001", event.jobId)
        assertEquals(2, event.sinkTargets.size)
        assertTrue(event.ttl > 0) // TTL이 설정됨 (7일 후)
        assertNotNull(event.createdAt)
    }

    @Test
    fun `create - jobId 없이 생성`() {
        // When
        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123"}""",
            sinkTargets = listOf("s3-raw")
        )

        // Then
        assertEquals(null, event.jobId)
        assertEquals(SinkEventStatus.PENDING, event.status)
    }

    @Test
    fun `idempotencyKey - 동일 입력에 대해 동일 키 생성`() {
        // Given
        val params = mapOf(
            "tenantId" to "oliveyoung",
            "entityKey" to "product:123",
            "version" to 1L,
            "viewType" to "view-product-core",
            "sinkTargets" to listOf("s3-raw")
        )

        // When
        val event1 = SinkEvent.create(
            tenantId = params["tenantId"] as String,
            entityKey = params["entityKey"] as String,
            version = params["version"] as Long,
            viewType = params["viewType"] as String,
            payload = """{"id":"123"}""",
            sinkTargets = params["sinkTargets"] as List<String>
        )

        val event2 = SinkEvent.create(
            tenantId = params["tenantId"] as String,
            entityKey = params["entityKey"] as String,
            version = params["version"] as Long,
            viewType = params["viewType"] as String,
            payload = """{"id":"123"}""",
            sinkTargets = params["sinkTargets"] as List<String>
        )

        // Then
        assertEquals(event1.idempotencyKey, event2.idempotencyKey)
    }

    @Test
    fun `idempotencyKey - 다른 입력에 대해 다른 키 생성`() {
        // When
        val event1 = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123"}""",
            sinkTargets = listOf("s3-raw")
        )

        val event2 = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:456", // 다른 entityKey
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"456"}""",
            sinkTargets = listOf("s3-raw")
        )

        // Then
        assertTrue(event1.idempotencyKey != event2.idempotencyKey)
    }

    @Test
    fun `ttl - 7일 후 타임스탬프 설정`() {
        // When
        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123"}""",
            sinkTargets = listOf("s3-raw")
        )

        // Then
        val now = java.time.Instant.now().epochSecond
        val expectedTtl = now + 7 * 24 * 3600
        assertTrue(event.ttl >= expectedTtl - 10) // 10초 오차 허용
        assertTrue(event.ttl <= expectedTtl + 10)
    }

    @Test
    fun `markCompleted - PENDING에서 COMPLETED로 전이`() {
        // Given
        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123"}""",
            sinkTargets = listOf("s3-raw")
        )

        // When
        val completed = event.markCompleted()

        // Then
        assertEquals(SinkEventStatus.COMPLETED, completed.status)
        assertNotNull(completed.processedAt)
    }

    @Test
    fun `markFailed - PENDING에서 FAILED로 전이`() {
        // Given
        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:123",
            version = 1L,
            viewType = "view-product-core",
            payload = """{"id":"123"}""",
            sinkTargets = listOf("s3-raw")
        )

        // When
        val failed = event.markFailed()

        // Then
        assertEquals(SinkEventStatus.FAILED, failed.status)
        assertNotNull(failed.processedAt)
    }

    @Test
    fun `validate - 빈 tenantId 검증 실패`() {
        // When & Then
        assertThrows<IllegalArgumentException> {
            SinkEvent.create(
                tenantId = "",
                entityKey = "product:123",
                version = 1L,
                viewType = "view-product-core",
                payload = """{"id":"123"}""",
                sinkTargets = listOf("s3-raw")
            )
        }
    }

    @Test
    fun `validate - 빈 entityKey 검증 실패`() {
        // When & Then
        assertThrows<IllegalArgumentException> {
            SinkEvent.create(
                tenantId = "oliveyoung",
                entityKey = "",
                version = 1L,
                viewType = "view-product-core",
                payload = """{"id":"123"}""",
                sinkTargets = listOf("s3-raw")
            )
        }
    }

    @Test
    fun `validate - 빈 sinkTargets 검증 실패`() {
        // When & Then
        assertThrows<IllegalArgumentException> {
            SinkEvent.create(
                tenantId = "oliveyoung",
                entityKey = "product:123",
                version = 1L,
                viewType = "view-product-core",
                payload = """{"id":"123"}""",
                sinkTargets = emptyList()
            )
        }
    }
}
