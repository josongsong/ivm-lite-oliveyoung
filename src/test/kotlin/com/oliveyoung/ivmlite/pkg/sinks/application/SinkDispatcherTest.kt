package com.oliveyoung.ivmlite.pkg.sinks.application

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPublisherPort
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkRoutingTable
import com.oliveyoung.ivmlite.sinks.contract.SinkEnvelopeV1
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SinkDispatcher 단위 테스트
 *
 * RFC-017: Sink Plugin Architecture
 */
class SinkDispatcherTest : FunSpec({

    test("dispatch - 정상 발행") {
        // Given
        val mockPublisher = mockk<SinkPublisherPort>()
        val routingTable = InMemorySinkRoutingTable(
            routes = mapOf("s3" to "https://sqs.ap-northeast-2.amazonaws.com/123/test-queue")
        )
        val dispatcher = SinkDispatcher(routingTable, mockPublisher)

        val envelope = SinkEnvelopeV1(
            envelopeVersion = 1,
            target = "s3",
            producedAtEpochMs = System.currentTimeMillis(),
            payloadVersion = 1L,
            entityType = "product",
            sliceType = "CORE",
            viewName = "PRODUCT_DETAIL",
            viewData = buildJsonObject { put("name", "iPhone") }
        )

        coEvery { mockPublisher.publish(any(), any()) } returns Either.Right(Unit)

        // When
        val result = dispatcher.dispatch(envelope)

        // Then
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        coVerify(exactly = 1) {
            mockPublisher.publish(
                "https://sqs.ap-northeast-2.amazonaws.com/123/test-queue",
                envelope
            )
        }
    }

    test("dispatch - 라우팅 실패 (target 없음)") {
        // Given
        val mockPublisher = mockk<SinkPublisherPort>()
        val routingTable = InMemorySinkRoutingTable(routes = emptyMap())
        val dispatcher = SinkDispatcher(routingTable, mockPublisher)

        val envelope = SinkEnvelopeV1(
            envelopeVersion = 1,
            target = "unknown",
            producedAtEpochMs = System.currentTimeMillis(),
            payloadVersion = 1L,
            entityType = "product",
            sliceType = "CORE",
            viewName = "PRODUCT_DETAIL",
            viewData = buildJsonObject { put("name", "iPhone") }
        )

        // When
        val result = dispatcher.dispatch(envelope)

        // Then
        result.shouldBeInstanceOf<Either.Left<SinkError.NonRetryableError>>()
        val error = (result as Either.Left).value as SinkError.NonRetryableError
        error.message shouldBe "No queue URL for target=unknown"
        error.reasonCode shouldBe ErrorReasonCode.INVALID_CONFIGURATION
    }

    test("dispatch - SQS 발행 실패") {
        // Given
        val mockPublisher = mockk<SinkPublisherPort>()
        val routingTable = InMemorySinkRoutingTable(
            routes = mapOf("s3" to "https://sqs.ap-northeast-2.amazonaws.com/123/test-queue")
        )
        val dispatcher = SinkDispatcher(routingTable, mockPublisher)

        val envelope = SinkEnvelopeV1(
            envelopeVersion = 1,
            target = "s3",
            producedAtEpochMs = System.currentTimeMillis(),
            payloadVersion = 1L,
            entityType = "product",
            sliceType = "CORE",
            viewName = "PRODUCT_DETAIL",
            viewData = buildJsonObject { put("name", "iPhone") }
        )

        coEvery { mockPublisher.publish(any(), any()) } returns Either.Left(
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "SQS unavailable"
            )
        )

        // When
        val result = dispatcher.dispatch(envelope)

        // Then
        result.shouldBeInstanceOf<Either.Left<SinkError.RetryableError>>()
        val error = (result as Either.Left).value as SinkError.RetryableError
        error.message shouldBe "SQS unavailable"
        error.reasonCode shouldBe ErrorReasonCode.NETWORK_TIMEOUT
    }

    test("dispatchBatch - 일부 실패") {
        // Given
        val mockPublisher = mockk<SinkPublisherPort>()
        val routingTable = InMemorySinkRoutingTable(
            routes = mapOf("s3" to "https://sqs.ap-northeast-2.amazonaws.com/123/test-queue")
        )
        val dispatcher = SinkDispatcher(routingTable, mockPublisher)

        val envelope1 = SinkEnvelopeV1(
            envelopeVersion = 1,
            target = "s3",
            producedAtEpochMs = System.currentTimeMillis(),
            payloadVersion = 1L,
            entityType = "product",
            sliceType = "CORE",
            viewName = "PRODUCT_DETAIL",
            viewData = buildJsonObject { put("name", "iPhone") }
        )

        val envelope2 = SinkEnvelopeV1(
            envelopeVersion = 1,
            target = "s3",
            producedAtEpochMs = System.currentTimeMillis(),
            payloadVersion = 2L,
            entityType = "product",
            sliceType = "CORE",
            viewName = "PRODUCT_DETAIL",
            viewData = buildJsonObject { put("name", "Galaxy") }
        )

        coEvery { mockPublisher.publish(any(), envelope1) } returns Either.Right(Unit)
        coEvery { mockPublisher.publish(any(), envelope2) } returns Either.Left(
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "Network error"
            )
        )

        // When
        val result = dispatcher.dispatchBatch(listOf(envelope1, envelope2))

        // Then
        result.shouldBeInstanceOf<Either.Left<SinkError.RetryableError>>()
        coVerify(exactly = 1) { mockPublisher.publish(any(), envelope1) }
        coVerify(exactly = 1) { mockPublisher.publish(any(), envelope2) }
    }
})
