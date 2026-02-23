package com.oliveyoung.ivmlite.e2e

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.lambda.SinkBatchProcessor
import com.oliveyoung.ivmlite.apps.lambda.SqsSinkMessage
import com.oliveyoung.ivmlite.integration.IntegrationTag
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkLedger
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.time.Instant

/**
 * Sink SQS E2E 테스트
 *
 * LocalStack SQS를 사용한 End-to-End 시나리오:
 * 1. LocalStack SQS 큐 생성
 * 2. SqsSinkEventRepository.putAll() → SQS 전송
 * 3. SQS ReceiveMessage → 메시지 수신
 * 4. SinkBatchProcessor.processBatch() → SinkPlugin 실행
 * 5. Capture Plugin으로 수신 페이로드 검증
 *
 * 실행: ./gradlew integrationTest --tests "*.SinkSqsE2ETest"
 * 요구사항: Docker (LocalStack 컨테이너)
 */
class SinkSqsE2ETest : StringSpec(init@{

    tags(IntegrationTag)

    var localstack: LocalStackContainer? = null
    var sqsClient: SqsAsyncClient? = null
    var queueUrl: String = ""

    beforeSpec {
        val ls = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS)
        ls.start()
        localstack = ls

        sqsClient = SqsAsyncClient.builder()
            .endpointOverride(ls.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ls.accessKey, ls.secretKey)
                )
            )
            .region(Region.of(ls.region))
            .build()
    }

    beforeTest {
        val client = sqsClient!!
        val queueName = "ivm-sink-e2e-${System.currentTimeMillis()}"
        client.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).await()
        queueUrl = client.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).await().queueUrl()
    }

    afterSpec {
        sqsClient?.close()
        localstack?.stop()
    }

    "SinkEvent → SQS 전송 → 수신 → SinkBatchProcessor → Plugin 실행 검증" {
        val capturePlugin = CaptureSinkPlugin()
        val processor = SinkBatchProcessor(
            pluginRegistry = InMemorySinkPluginRegistry().apply { register("opensearch", capturePlugin) },
            sinkLedger = InMemorySinkLedger(),
            failureRepository = InMemorySinkFailureRepository(),
        )
        val repo = com.oliveyoung.ivmlite.pkg.sinks.adapters.SqsSinkEventRepository(sqsClient!!, queueUrl)

        val events = listOf(
            SinkEvent.create(
                tenantId = "oliveyoung",
                entityKey = "product:E2E-P001",
                version = 1L,
                viewType = "core",
                payload = """{"name":"E2E Product 1","price":10000}""",
                sinkTargets = listOf("opensearch"),
                jobId = "e2e-job-1",
            ),
            SinkEvent.create(
                tenantId = "oliveyoung",
                entityKey = "product:E2E-P002",
                version = 2L,
                viewType = "core",
                payload = """{"name":"E2E Product 2","price":20000}""",
                sinkTargets = listOf("opensearch"),
                jobId = "e2e-job-1",
            ),
        )

        // 1. SQS 전송
        val putResult = runBlocking { repo.putAll(events) }
        putResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<List<SinkEvent>>>()

        // 2. SQS 수신 (최대 10초 대기)
        var messages: List<com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage> = emptyList()
        for (attempt in 0 until 20) {
            val response = sqsClient!!.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()
            ).await()
            messages = response.messages().map { msg ->
                com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage().apply {
                    messageId = msg.messageId()
                    body = msg.body()
                }
            }
            if (messages.isNotEmpty()) break
            if (attempt > 0) Thread.sleep(500)
        }

        messages.shouldHaveSize(2)

        // 3. SinkBatchProcessor 처리
        val sqsMessages = messages.map { SqsSinkMessage(it.messageId, it.body) }
        val result = runBlocking { processor.processBatch(sqsMessages) }

        result.totalMessages shouldBe 2
        result.parseErrors shouldBe 0
        result.succeeded shouldBe 2
        result.failed shouldBe 0

        // 4. Capture Plugin 검증
        capturePlugin.receivedPayloads.shouldHaveSize(2)
        val payloads = capturePlugin.receivedPayloads
        payloads.any { it.entityKey == "product:E2E-P001" && it.entityVersion == 1L } shouldBe true
        payloads.any { it.entityKey == "product:E2E-P002" && it.entityVersion == 2L } shouldBe true
    }

    "다중 target → 각 target별 executeBatch 호출" {
        val repo = com.oliveyoung.ivmlite.pkg.sinks.adapters.SqsSinkEventRepository(sqsClient!!, queueUrl)

        val opensearchPlugin = CaptureSinkPlugin()
        val s3Plugin = CaptureSinkPlugin()
        val multiRegistry = InMemorySinkPluginRegistry().apply {
            register("opensearch", opensearchPlugin)
            register("s3", s3Plugin)
        }

        val multiProcessor = SinkBatchProcessor(
            pluginRegistry = multiRegistry,
            sinkLedger = InMemorySinkLedger(),
            failureRepository = InMemorySinkFailureRepository(),
        )

        val event = SinkEvent.create(
            tenantId = "oliveyoung",
            entityKey = "product:E2E-MULTI",
            version = 1L,
            viewType = "core",
            payload = """{"name":"Multi Target"}""",
            sinkTargets = listOf("opensearch", "s3"),
        )

        runBlocking { repo.putAll(listOf(event)) }

        var messages: List<com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage> = emptyList()
        for (attempt in 0 until 20) {
            val response = sqsClient!!.receiveMessage(
                ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(1).build()
            ).await()
            messages = response.messages().map { msg ->
                com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage().apply {
                    messageId = msg.messageId()
                    body = msg.body()
                }
            }
            if (messages.isNotEmpty()) break
            if (attempt > 0) Thread.sleep(500)
        }

        val result = runBlocking {
            multiProcessor.processBatch(messages.map { SqsSinkMessage(it.messageId, it.body) })
        }

        result.succeeded shouldBe 2  // opensearch 1 + s3 1
        opensearchPlugin.receivedPayloads.shouldHaveSize(1)
        s3Plugin.receivedPayloads.shouldHaveSize(1)
    }
})

/**
 * 수신 페이로드를 캡처하는 테스트용 SinkPlugin
 */
private class CaptureSinkPlugin : SinkPlugin {
    val receivedPayloads = mutableListOf<SinkPayload.V1>()

    override val pluginId = "capture"
    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 500,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<com.oliveyoung.ivmlite.sinks.contract.SinkError, BatchResult> {
        payloads.filterIsInstance<SinkPayload.V1>().forEach { receivedPayloads.add(it) }
        val results = payloads.map { p ->
            SinkResult(p.idempotencyKey, SinkStatus.SUCCESS, Instant.now().toString())
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }
}
