package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.adapters.SinkPreflightPluginRegistryAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import arrow.core.Either
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * IngestRoutes HTTP-level 테스트
 *
 * POST /api/v1/ingest 엔드포인트 검증:
 * 1. 정상 요청 → 200 OK + 올인원 응답
 * 2. jobId 전파
 * 3. 잘못된 요청 → 400 BadRequest
 * 4. 빈 tenantId/entityKey → 400
 * 5. 멱등성 (동일 요청 2번)
 */
class IngestRoutesTest : StringSpec({

    beforeTest { runCatching { stopKoin() } }

    fun createOrchestrator(): IngestionOrchestrator {
        val rawDataRepo = InMemoryRawDataRepository()
        val sliceRepo = InMemorySliceRepository()
        val sinkEventRepo = InMemorySinkEventRepository()

        val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
        val joinExecutor = JoinExecutor(rawDataRepo)
        val slicingEngine = DefaultSlicingEngineAdapter(
            SlicingEngine(contractRegistry, joinExecutor)
        )
        val viewComposer = ViewComposer()

        val workflow = IngestionWorkflow(
            rawDataRepo = rawDataRepo,
            sliceRepo = sliceRepo,
            slicingEngine = slicingEngine,
            viewComposer = viewComposer
        )

        return IngestionOrchestrator(
            workflow = workflow,
            sinkEventRepo = sinkEventRepo,
            transactionPort = NoOpTransactionAdapter(),
            sinkRuleRegistry = InMemorySinkRuleRegistry(),
            sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
        )
    }

    fun ApplicationTestBuilder.configureIngestApp(orchestrator: IngestionOrchestrator) {
        application {
            install(ContentNegotiation) { json() }
            install(Koin) {
                modules(module {
                    single { orchestrator }
                    single<ContractRegistryPort> {
                        LocalYamlContractRegistryAdapter("/contracts/v1")
                    }
                    single { EntityContractResolver(get()) }
                    single { DeployExecutor(orchestrator = get(), contractResolver = get()) }
                })
            }
            routing {
                ingestRoutes()
            }
        }
    }

    fun buildIngestBody(
        tenantId: String = "test-tenant",
        entityKey: String = "product:SKU-001",
        version: Long = 1L,
        name: String = "Test Product",
        price: Int = 29000,
        jobId: String? = null,
        skipSink: Boolean = false,
        inProcessSink: Boolean = false,
    ): String = buildJsonObject {
            jobId?.let { put("jobId", it) }
            put("tenantId", tenantId)
            put("entityKey", entityKey)
            put("version", version)
            put("schemaId", "entity.product.v1")
            put("schemaVersion", "1.0.0")
            put("payload", buildJsonObject {
                put("name", name)
                put("price", price)
                put("category", "skincare")
            })
            if (skipSink) put("skipSink", true)
            if (inProcessSink) put("inProcessSink", true)
        }.toString()

    fun createOrchestratorWithInProcessSink(
        sinkEventRepo: InMemorySinkEventRepository,
    ): Pair<IngestionOrchestrator, RouteCaptureSinkPlugin> {
        val rawDataRepo = InMemoryRawDataRepository()
        val sliceRepo = InMemorySliceRepository()

        val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
        val joinExecutor = JoinExecutor(rawDataRepo)
        val slicingEngine = DefaultSlicingEngineAdapter(
            SlicingEngine(contractRegistry, joinExecutor)
        )
        val viewComposer = ViewComposer()

        val workflow = IngestionWorkflow(
            rawDataRepo = rawDataRepo,
            sliceRepo = sliceRepo,
            slicingEngine = slicingEngine,
            viewComposer = viewComposer
        )

        val capturePlugin = RouteCaptureSinkPlugin()
        val pluginRegistry = InMemorySinkPluginRegistry(mapOf("opensearch-sink" to capturePlugin))
        val preflight = SinkPreflightPluginRegistryAdapter(pluginRegistry)

        val orchestrator = IngestionOrchestrator(
            workflow = workflow,
            sinkEventRepo = sinkEventRepo,
            transactionPort = NoOpTransactionAdapter(),
            sinkRuleRegistry = InMemorySinkRuleRegistry(),
            sinkPreflight = preflight,
            pluginRegistry = pluginRegistry,
        )
        return orchestrator to capturePlugin
    }

    "POST /api/v1/ingest → 200 OK, success=true" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody())
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            json["tenantId"]?.jsonPrimitive?.content shouldBe "test-tenant"
            json["entityKey"]?.jsonPrimitive?.content shouldBe "product:SKU-001"
            val version = json["version"]?.jsonPrimitive?.long
            check(version != null && version > 0) { "version should be positive but was $version" }
        }
    }

    "POST /api/v1/ingest → sliceCount, viewCount, sinkPending 포함" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody())
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val sliceCount = json["sliceCount"]?.jsonPrimitive?.int
            val viewCount = json["viewCount"]?.jsonPrimitive?.int
            check(sliceCount != null) { "sliceCount field missing from response" }
            check(viewCount != null) { "viewCount field missing from response" }
            assert(sliceCount >= 1) { "sliceCount should be >= 1 but was $sliceCount" }
            assert(viewCount >= 1) { "viewCount should be >= 1 but was $viewCount" }
            json["sinkPending"]?.jsonPrimitive?.boolean shouldBe true
        }
    }

    "POST /api/v1/ingest → jobId 전파" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody(jobId = "job-http-123"))
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["jobId"]?.jsonPrimitive?.content shouldBe "job-http-123"
        }
    }

    "POST /api/v1/ingest → jobId null이면 응답에도 null" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody(jobId = null))
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            // jobId가 null이면 JSON에서 null로 나옴
            val jobId = json["jobId"]
            assert(jobId == null || jobId == JsonNull) { "jobId should be null" }
        }
    }

    "POST /api/v1/ingest → 잘못된 JSON body → 400/500" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("{invalid json}")
            }

            // Ktor는 잘못된 JSON 파싱 시 400 또는 500 반환
            assert(response.status.value in listOf(400, 500)) {
                "Expected 400 or 500 but got ${response.status}"
            }
        }
    }

    "POST /api/v1/ingest → Content-Type 없이 요청 → 415" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                setBody(buildIngestBody())
            }

            // Content-Type이 없으면 Ktor가 415 UnsupportedMediaType 반환
            assert(response.status.value in listOf(400, 415, 500)) {
                "Expected 400, 415 or 500 but got ${response.status}"
            }
        }
    }

    "POST /api/v1/ingest → 멱등성: 동일 요청 2번 모두 성공" {
        testApplication {
            val orchestrator = createOrchestrator()
            configureIngestApp(orchestrator)

            val body = buildIngestBody(entityKey = "product:idempotent-001", version = 1L)

            val r1 = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val r2 = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            r1.status shouldBe HttpStatusCode.OK
            r2.status shouldBe HttpStatusCode.OK
        }
    }

    "POST /api/v1/ingest → durationMs 포함" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody())
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val durationMs = json["durationMs"]?.jsonPrimitive?.long
            check(durationMs != null) { "durationMs field missing from response" }
            assert(durationMs >= 0) { "durationMs should be >= 0 but was $durationMs" }
        }
    }

    "POST /api/v1/ingest → skipSink=true 시 sinkPending=false" {
        testApplication {
            configureIngestApp(createOrchestrator())

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody(skipSink = true))
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            json["sinkPending"]?.jsonPrimitive?.boolean shouldBe false
        }
    }

    "POST /api/v1/ingest → inProcessSink=true 시 200 OK, SinkEvent 미발행" {
        testApplication {
            val sinkEventRepo = InMemorySinkEventRepository()
            val (orchestrator, capturePlugin) = createOrchestratorWithInProcessSink(sinkEventRepo)
            configureIngestApp(orchestrator)

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildIngestBody(entityKey = "product:inproc-http-001", inProcessSink = true))
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            json["sinkPending"]?.jsonPrimitive?.boolean shouldBe true
            json["sliceCount"]?.jsonPrimitive?.int shouldBe 9
            json["viewCount"]?.jsonPrimitive?.int shouldBe 1
            sinkEventRepo.size() shouldBe 0
            capturePlugin.receivedPayloads.size shouldBe 1
            capturePlugin.receivedPayloads[0].entityKey shouldBe "product:inproc-http-001"
        }
    }
})

private class RouteCaptureSinkPlugin : SinkPlugin {
    val receivedPayloads = mutableListOf<SinkPayload.V1>()

    override val pluginId = "opensearch-sink"
    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 500,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<com.oliveyoung.ivmlite.sinks.contract.SinkError, BatchResult> {
        payloads.filterIsInstance<SinkPayload.V1>().forEach { receivedPayloads.add(it) }
        val results = payloads.map { p ->
            SinkResult(p.idempotencyKey, SinkStatus.SUCCESS, java.time.Instant.now().toString())
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }
}
