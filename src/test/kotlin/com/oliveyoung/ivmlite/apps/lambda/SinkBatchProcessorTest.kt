package com.oliveyoung.ivmlite.apps.lambda

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkLedger
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/**
 * SinkBatchProcessor 단위 테스트
 *
 * SQS 메시지 배치 → target별 그룹핑 → executeBatch 처리 검증.
 */
class SinkBatchProcessorTest : StringSpec({

    lateinit var ledger: InMemorySinkLedger
    lateinit var failureRepo: InMemorySinkFailureRepository
    lateinit var pluginRegistry: InMemorySinkPluginRegistry
    lateinit var processor: SinkBatchProcessor

    beforeTest {
        ledger = InMemorySinkLedger()
        failureRepo = InMemorySinkFailureRepository()
        pluginRegistry = InMemorySinkPluginRegistry()
        processor = SinkBatchProcessor(
            pluginRegistry = pluginRegistry,
            sinkLedger = ledger,
            failureRepository = failureRepo,
        )
    }

    "정상 메시지 1건 → target 성공 → succeeded=1" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val body = sqsMessageBody(
            id = "evt-001",
            tenantId = "t1",
            entityKey = "product:1",
            version = 1L,
            viewType = "core",
            payload = """{"name":"Product A","price":10000}""",
            targets = listOf("opensearch"),
        )

        val result = runBlocking {
            processor.processBatch(listOf(SqsSinkMessage("msg-1", body)))
        }

        result.totalMessages shouldBe 1
        result.parseErrors shouldBe 0
        result.succeeded shouldBe 1
        result.failed shouldBe 0
    }

    "정상 메시지 2건 → 다중 target → succeeded=2" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        pluginRegistry.register("s3", successPlugin("s3"))

        val messages = listOf(
            SqsSinkMessage("msg-1", sqsMessageBody("evt-001", "t1", "product:1", 1L, "core", """{"a":1}""", listOf("opensearch", "s3"))),
            SqsSinkMessage("msg-2", sqsMessageBody("evt-002", "t1", "product:2", 2L, "core", """{"b":2}""", listOf("opensearch"))),
        )

        val result = runBlocking { processor.processBatch(messages) }

        result.totalMessages shouldBe 2
        result.parseErrors shouldBe 0
        result.succeeded shouldBe 3  // evt-001 → opensearch, s3 / evt-002 → opensearch
        result.failed shouldBe 0
    }

    "파싱 실패 메시지 → parseErrors 증가" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val messages = listOf(
            SqsSinkMessage("msg-1", sqsMessageBody("evt-001", "t1", "product:1", 1L, "core", """{"a":1}""", listOf("opensearch"))),
            SqsSinkMessage("msg-2", "invalid-json"),
            SqsSinkMessage("msg-3", sqsMessageBody("evt-003", "t1", "p3", 3L, "core", "123", listOf("opensearch"))),  // payload가 JSON Object 아님 (숫자)
        )

        val result = runBlocking { processor.processBatch(messages) }

        result.totalMessages shouldBe 3
        result.parseErrors shouldBe 2
        result.succeeded shouldBe 1
    }

    "등록되지 않은 target → failed 증가" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        // s3 미등록

        val body = sqsMessageBody("evt-001", "t1", "product:1", 1L, "core", """{"a":1}""", listOf("opensearch", "s3"))
        val result = runBlocking {
            processor.processBatch(listOf(SqsSinkMessage("msg-1", body)))
        }

        result.succeeded shouldBe 1  // opensearch 성공
        result.failed shouldBe 1     // s3 플러그인 없음
    }

    "executeBatch 실패 → failed 증가, FailureRepository 저장" {
        pluginRegistry.register("opensearch", failingPlugin(SinkError.RetryableError(
            reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
            message = "Connection timeout",
        )))

        val body = sqsMessageBody("evt-001", "t1", "product:1", 1L, "core", """{"a":1}""", listOf("opensearch"))
        val result = runBlocking {
            processor.processBatch(listOf(SqsSinkMessage("msg-1", body)))
        }

        result.succeeded shouldBe 0
        result.failed shouldBe 1
        failureRepo.size() shouldBe 1
    }

    "빈 배치 → succeeded=0, failed=0" {
        val result = runBlocking { processor.processBatch(emptyList()) }
        result.totalMessages shouldBe 0
        result.parseErrors shouldBe 0
        result.succeeded shouldBe 0
        result.failed shouldBe 0
    }
})

private fun successPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 500,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val results = payloads.map { p ->
            SinkResult(p.idempotencyKey, SinkStatus.SUCCESS, Instant.now().toString())
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }
}

private fun failingPlugin(error: SinkError): SinkPlugin = object : SinkPlugin {
    override val pluginId = "failing"
    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
    )
    override suspend fun executeBatch(payloads: List<SinkPayload>) = Either.Left(error)
}

private fun sqsMessageBody(
    id: String,
    tenantId: String,
    entityKey: String,
    version: Long,
    viewType: String,
    payload: String,
    targets: List<String>,
    jobId: String? = null,
): String = buildJsonObject {
    put("id", id)
    put("jobId", jobId)
    put("idempotencyKey", "key-$id")
    put("tenantId", tenantId)
    put("entityKey", entityKey)
    put("version", version)
    put("viewType", viewType)
    put("payload", payload)
    putJsonArray("sinkTargets") { targets.forEach { add(JsonPrimitive(it)) } }
}.toString()
