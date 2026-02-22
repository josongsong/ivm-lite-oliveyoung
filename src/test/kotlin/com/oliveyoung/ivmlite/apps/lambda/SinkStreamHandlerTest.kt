package com.oliveyoung.ivmlite.apps.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * SinkStreamHandler Lambda 테스트
 *
 * 아키텍처: DynamoDB Streams → Lambda → SinkPlugin 직접 실행 (SQS 제거)
 *
 * ⚠️ 주의: 실제 Koin DI 초기화 필요하므로 통합 테스트로 분류
 *
 * 검증 항목:
 * - DynamoDB Streams INSERT 이벤트 처리
 * - PENDING 상태 SinkEvent 필터링
 * - SinkPluginRegistryPort를 통한 SinkPlugin 직접 실행
 */
@Disabled("Integration test - requires full Koin DI setup")
class SinkStreamHandlerTest {

    @Test
    fun `handleRequest - INSERT 이벤트 처리`() {
        // Given
        val handler = SinkStreamHandler()

        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockLogger.log(any<String>()) } returns Unit

        val record = DynamodbEvent.DynamodbStreamRecord().apply {
            eventName = "INSERT"
            dynamodb = StreamRecord().apply {
                val attrId = AttributeValue()
                attrId.s = "event-123"

                val attrTenant = AttributeValue()
                attrTenant.s = "oliveyoung"

                val attrEntity = AttributeValue()
                attrEntity.s = "product:123"

                val attrVersion = AttributeValue()
                attrVersion.n = "1"

                val attrViewType = AttributeValue()
                attrViewType.s = "view-product-core"

                val attrPayload = AttributeValue().apply {
                    s = """{"id":"123","name":"Product A"}"""
                }

                val attrStatus = AttributeValue().apply {
                    s = "PENDING"
                }

                val attrSinkTargets = AttributeValue()
                attrSinkTargets.setSS(listOf("s3-raw"))

                val attrJobId = AttributeValue().apply {
                    s = "job-001"
                }

                val newImage = mapOf(
                    "id" to attrId,
                    "tenantId" to attrTenant,
                    "entityKey" to attrEntity,
                    "version" to attrVersion,
                    "viewType" to attrViewType,
                    "payload" to attrPayload,
                    "status" to attrStatus,
                    "sinkTargets" to attrSinkTargets,
                    "jobId" to attrJobId
                )
            }
        }

        val event = DynamodbEvent().apply {
            records = listOf(record)
        }

        // When
        val result = handler.handleRequest(event, mockContext)

        // Then
        assertTrue(result.contains("Processed: 1"))
    }

    @Test
    fun `handleRequest - MODIFY 이벤트 PROCESSED 상태는 스킵`() {
        // Given
        val handler = SinkStreamHandler()

        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockLogger.log(any<String>()) } returns Unit

        val record = DynamodbEvent.DynamodbStreamRecord().apply {
            eventName = "MODIFY"
            dynamodb = StreamRecord().apply {
                val attrId = AttributeValue()
                attrId.s = "event-456"

                val attrStatus = AttributeValue()
                attrStatus.s = "PROCESSED"

                val attrTenant = AttributeValue()
                attrTenant.s = "oliveyoung"

                val attrEntity = AttributeValue()
                attrEntity.s = "product:456"

                newImage = mapOf(
                    "id" to attrId,
                    "status" to attrStatus,
                    "tenantId" to attrTenant,
                    "entityKey" to attrEntity
                )
            }
        }

        val event = DynamodbEvent().apply {
            records = listOf(record)
        }

        // When
        val result = handler.handleRequest(event, mockContext)

        // Then
        assertTrue(result.contains("Processed: 0")) // PENDING 아니므로 스킵
    }

    @Test
    fun `handleRequest - REMOVE 이벤트 스킵`() {
        // Given
        val handler = SinkStreamHandler()

        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockLogger.log(any<String>()) } returns Unit

        val record = DynamodbEvent.DynamodbStreamRecord().apply {
            eventName = "REMOVE"
        }

        val event = DynamodbEvent().apply {
            records = listOf(record)
        }

        // When
        val result = handler.handleRequest(event, mockContext)

        // Then
        assertTrue(result.contains("Processed: 0"))
    }

    @Test
    fun `handleRequest - 빈 이벤트 처리`() {
        // Given
        val handler = SinkStreamHandler()

        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockLogger.log(any<String>()) } returns Unit

        val event = DynamodbEvent().apply {
            records = emptyList()
        }

        // When
        val result = handler.handleRequest(event, mockContext)

        // Then
        assertTrue(result.contains("Processed: 0"))
    }
}
