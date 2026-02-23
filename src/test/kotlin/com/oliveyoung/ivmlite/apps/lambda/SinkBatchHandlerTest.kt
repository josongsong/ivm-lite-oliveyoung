package com.oliveyoung.ivmlite.apps.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkLedger
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.time.Instant

/**
 * SinkBatchHandler Lambda 테스트
 *
 * handleRequest(SQSEvent, Context) 직접 호출로 Lambda Handler 검증.
 * Koin 테스트 모듈로 InMemory 구현 사용 (DynamoDB/AWS 불필요).
 */
class SinkBatchHandlerTest : StringSpec({

    beforeSpec {
        stopKoin()
        val capturePlugin = object : SinkPlugin {
            override val pluginId = "opensearch"
            override val capabilities = PluginCapabilities(
                supportedContractVersions = setOf("1.0"),
                supportsBatch = true,
                maxBatchSize = 500,
            )
            override suspend fun executeBatch(payloads: List<SinkPayload>) =
                arrow.core.Either.Right(
                    BatchResult(
                        payloads.map { SinkResult(it.idempotencyKey, SinkStatus.SUCCESS, Instant.now().toString()) },
                        emptyList(),
                        emptyList(),
                    )
                )
        }

        startKoin {
            modules(
                module {
                    single<DynamoDbAsyncClient> { mockk(relaxed = true) }
                },
                com.oliveyoung.ivmlite.apps.lambda.wiring.lambdaTracingModule,
                module {
                    single<com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort> {
                        InMemorySinkPluginRegistry().apply { register("opensearch", capturePlugin) }
                    }
                    single<com.oliveyoung.ivmlite.sinks.contract.SinkLedger> { InMemorySinkLedger() }
                    single<com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort> {
                        InMemorySinkFailureRepository()
                    }
                },
            )
        }
    }

    afterSpec {
        stopKoin()
    }

    "handleRequest - 정상 메시지 1건 → batchItemFailures 비어있음" {
        val handler = SinkBatchHandler()
        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockContext.functionName } returns "test-sink-batch"
        every { mockLogger.log(any<String>()) } returns Unit

        val body = buildJsonObject {
            put("id", "evt-001")
            put("jobId", null as String?)
            put("idempotencyKey", "key-evt-001")
            put("tenantId", "t1")
            put("entityKey", "product:1")
            put("version", 1)
            put("viewType", "core")
            put("payload", """{"name":"Product A"}""")
            putJsonArray("sinkTargets") { add(JsonPrimitive("opensearch")) }
        }.toString()

        val record = SQSEvent.SQSMessage().apply {
            setMessageId("msg-1")
            setBody(body)
        }
        val event = SQSEvent().apply { setRecords(listOf(record)) }

        val result = handler.handleRequest(event, mockContext)

        result.batchItemFailures.shouldBeEmpty()
    }

    "handleRequest - 정상 메시지 2건 → batchItemFailures 비어있음" {
        val handler = SinkBatchHandler()
        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockContext.functionName } returns "test-sink-batch"
        every { mockLogger.log(any<String>()) } returns Unit

        val records = listOf(
            sqsRecord("msg-1", sqsBody("evt-001", "t1", "product:1", 1L, """{"a":1}""", listOf("opensearch"))),
            sqsRecord("msg-2", sqsBody("evt-002", "t1", "product:2", 2L, """{"b":2}""", listOf("opensearch"))),
        )
        val event = SQSEvent().apply { setRecords(records) }

        val result = handler.handleRequest(event, mockContext)

        result.batchItemFailures.shouldBeEmpty()
    }

    "handleRequest - 빈 이벤트 → batchItemFailures 비어있음" {
        val handler = SinkBatchHandler()
        val mockContext = mockk<Context>()
        val mockLogger = mockk<LambdaLogger>()
        every { mockContext.logger } returns mockLogger
        every { mockContext.functionName } returns "test-sink-batch"
        every { mockLogger.log(any<String>()) } returns Unit

        val event = SQSEvent().apply { setRecords(emptyList()) }

        val result = handler.handleRequest(event, mockContext)

        result.batchItemFailures.shouldBeEmpty()
    }
})

private fun sqsRecord(messageId: String, body: String) = SQSEvent.SQSMessage().apply {
    setMessageId(messageId)
    setBody(body)
}

private fun sqsBody(
    id: String,
    tenantId: String,
    entityKey: String,
    version: Long,
    payload: String,
    targets: List<String>,
) = buildJsonObject {
    put("id", id)
    put("jobId", null as String?)
    put("idempotencyKey", "key-$id")
    put("tenantId", tenantId)
    put("entityKey", entityKey)
    put("version", version)
    put("viewType", "core")
    put("payload", payload)
    putJsonArray("sinkTargets") { targets.forEach { add(JsonPrimitive(it)) } }
}.toString()
