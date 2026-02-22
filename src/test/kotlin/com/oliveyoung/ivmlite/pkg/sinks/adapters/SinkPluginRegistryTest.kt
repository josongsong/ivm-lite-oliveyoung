package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * SinkPluginRegistry + SinkPlugin 단위 테스트
 *
 * 검증:
 * - InMemorySinkPluginRegistry target 해석
 * - SinkPlugin 직접 실행 (SQS 없이)
 * - 미등록 target → null
 */
class SinkPluginRegistryTest : StringSpec({

    "등록된 target → SinkPlugin 해석" {
        val mockPlugin = createMockPlugin("test-plugin")
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to mockPlugin))

        val plugin = registry.resolve("opensearch")
        plugin shouldNotBe null
        plugin!!.pluginId shouldBe "test-plugin"
    }

    "미등록 target → null" {
        val registry = InMemorySinkPluginRegistry(emptyMap())

        val plugin = registry.resolve("nonexistent")
        plugin shouldBe null
    }

    "registeredTargets → 등록된 target 목록" {
        val registry = InMemorySinkPluginRegistry(
            mapOf(
                "opensearch" to createMockPlugin("opensearch-sink"),
                "s3" to createMockPlugin("s3-sink"),
            )
        )

        registry.registeredTargets() shouldBe setOf("opensearch", "s3")
    }

    "register → 동적 추가" {
        val registry = InMemorySinkPluginRegistry()
        registry.resolve("personalize") shouldBe null

        registry.register("personalize", createMockPlugin("personalize-sink"))
        registry.resolve("personalize") shouldNotBe null
        registry.resolve("personalize")!!.pluginId shouldBe "personalize-sink"
    }

    "SinkPlugin 직접 실행 → BatchResult" {
        val plugin = createMockPlugin("test-plugin")
        val payload = createTestPayload()

        val result = plugin.executeBatch(listOf(payload))
        result.isRight() shouldBe true
        result.getOrNull()!!.succeeded.size shouldBe 1
        result.getOrNull()!!.succeeded[0].status shouldBe SinkStatus.SUCCESS
    }

    "SinkPlugin 빈 배치 → 빈 결과" {
        val plugin = createMockPlugin("test-plugin")

        val result = plugin.executeBatch(emptyList())
        result.isRight() shouldBe true
        result.getOrNull()!!.succeeded.size shouldBe 0
    }
})

private fun createMockPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 10,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val results = payloads.map { payload ->
            SinkResult(
                idempotencyKey = payload.idempotencyKey,
                status = SinkStatus.SUCCESS,
                processedAt = Instant.now().toString(),
            )
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }
}

private fun createTestPayload(): SinkPayload.V1 {
    val viewData = buildJsonObject {
        put("name", "Test Product")
        put("price", 29000)
    }
    val digest = SinkPayload.computePayloadDigest(viewData)
    return SinkPayload.V1(
        correlationId = "test-correlation",
        timestamp = Instant.now().toString(),
        idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:1", 1L, "core", digest),
        payloadDigest = digest,
        tenantId = "t1",
        entityKey = "product:1",
        entityVersion = 1L,
        viewType = "core",
        viewData = viewData,
    )
}
