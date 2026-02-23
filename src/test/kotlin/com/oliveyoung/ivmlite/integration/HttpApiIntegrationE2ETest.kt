package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.apps.runtimeapi.routes.ingestRoutes
import com.oliveyoung.ivmlite.apps.runtimeapi.routes.queryRoutes
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import io.kotest.core.spec.style.DescribeSpec
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
 * HTTP API 통합 E2E 테스트
 *
 * Ktor testApplication을 사용하여 실제 HTTP 레이어 경유 검증:
 * 1. POST /api/v1/ingest → 200 OK (올인원 처리)
 * 2. POST /api/v1/slice → 200 OK (Slicing 확인)
 * 3. POST /api/v1/query → 200 OK (View 조회)
 * 4. Ingest → Query 전체 라운드트립
 * 5. 잘못된 entityKey 형식 → 400 BadRequest
 * 6. Multi-tenant HTTP 레벨 격리
 */
class HttpApiIntegrationE2ETest : DescribeSpec({

    tags(IntegrationTag)

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()

    val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(
        SlicingEngine(contractRegistry, joinExecutor)
    )
    val viewComposer = ViewComposer()

    val ingestionWorkflow = IngestionWorkflow(
        rawDataRepo = rawDataRepo,
        sliceRepo = sliceRepo,
        slicingEngine = slicingEngine,
        viewComposer = viewComposer
    )

    val sinkRuleRegistry = InMemorySinkRuleRegistry()

    val orchestrator = IngestionOrchestrator(
        workflow = ingestionWorkflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())
    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))

    val slicingWorkflow = SlicingWorkflow(
        rawRepo = rawDataRepo,
        sliceRepo = sliceRepo,
        slicingEngine = slicingEngine,
        invertedIndexRepo = invertedIndexRepo,
        changeSetBuilder = changeSetBuilder,
        impactCalculator = impactCalculator,
        contractRegistry = contractRegistry
    )

    val queryViewWorkflow = QueryViewWorkflow(
        sliceRepo = sliceRepo,
        contractRegistry = contractRegistry
    )

    fun ApplicationTestBuilder.configureFullApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Koin) {
                modules(module {
                    single { orchestrator }
                    single { contractResolver }
                    single { DeployExecutor(orchestrator = get(), contractResolver = get()) }
                    single { slicingWorkflow }
                    single { queryViewWorkflow }
                })
            }
            routing {
                ingestRoutes()
                queryRoutes()
            }
        }
    }

    fun buildIngestBody(
        tenantId: String = "http-e2e",
        entityKey: String = "product:HTTP-001",
        name: String = "HTTP E2E Product",
        price: Int = 29000,
        jobId: String? = null
    ): String {
        return buildJsonObject {
            jobId?.let { put("jobId", it) }
            put("tenantId", tenantId)
            put("entityKey", entityKey)
            put("version", 1L)
            put("schemaId", "entity.product.v1")
            put("schemaVersion", "1.0.0")
            put("payload", buildJsonObject {
                put("name", name)
                put("price", price)
                put("category", "skincare")
            })
        }.toString()
    }

    beforeEach {
        runCatching { stopKoin() }
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        invertedIndexRepo.clear()
        sinkRuleRegistry.clear()
    }

    describe("Ingest → Query 전체 라운드트립") {

        it("Ingest API → Slice API → Query API 순차 성공") {
            testApplication {
                configureFullApp()

                // 1. Ingest
                val ingestResponse = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody())
                }

                ingestResponse.status shouldBe HttpStatusCode.OK
                val ingestJson = Json.parseToJsonElement(ingestResponse.bodyAsText()).jsonObject
                ingestJson["success"]?.jsonPrimitive?.boolean shouldBe true
                val version = ingestJson["version"]?.jsonPrimitive?.long
                check(version != null && version > 0) { "version should be positive" }

                // 2. Slice
                val sliceResponse = client.post("/api/v1/slice") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "http-e2e")
                        put("entityKey", "product:HTTP-001")
                        put("version", version)
                    }.toString())
                }

                sliceResponse.status shouldBe HttpStatusCode.OK
                val sliceJson = Json.parseToJsonElement(sliceResponse.bodyAsText()).jsonObject
                sliceJson["success"]?.jsonPrimitive?.boolean shouldBe true
                val sliceCount = sliceJson["count"]?.jsonPrimitive?.int
                check(sliceCount != null && sliceCount >= 1) { "sliceCount should be >= 1" }

                // 3. Query (ViewDefinition 기반)
                val queryResponse = client.post("/api/v1/query") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "http-e2e")
                        put("viewId", "view.product.pdp.v1")
                        put("entityKey", "product:HTTP-001")
                        put("version", version)
                    }.toString())
                }

                queryResponse.status shouldBe HttpStatusCode.OK
                val queryBody = queryResponse.bodyAsText()
                val queryJson = Json.parseToJsonElement(queryBody).jsonObject
                assert(queryJson.containsKey("data")) { "Response should contain 'data' key" }
            }
        }
    }

    describe("Ingest API 에러 경로") {

        it("잘못된 entityKey 형식 (colon 없음) → 400 BadRequest") {
            testApplication {
                configureFullApp()

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "http-e2e")
                        put("entityKey", "invalid-no-colon")
                        put("version", 1L)
                        put("schemaId", "entity.product.v1")
                        put("schemaVersion", "1.0.0")
                        put("payload", buildJsonObject {
                            put("name", "Bad Entity")
                            put("price", 1000)
                        })
                    }.toString())
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["code"]?.jsonPrimitive?.content shouldBe "INVALID_ENTITY_KEY"
            }
        }

        it("미지원 entityType → 에러 응답") {
            testApplication {
                configureFullApp()

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "http-e2e")
                        put("entityKey", "unknown_type:ID-001")
                        put("version", 1L)
                        put("schemaId", "entity.unknown.v1")
                        put("schemaVersion", "1.0.0")
                        put("payload", buildJsonObject {
                            put("name", "Unknown")
                        })
                    }.toString())
                }

                // ContractResolver에서 entityType을 찾을 수 없음 → 에러
                assert(response.status.value in listOf(400, 404, 500)) {
                    "Expected error status but got ${response.status}"
                }
            }
        }

        it("빈 entityKey → 잘못된 요청") {
            testApplication {
                configureFullApp()

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "http-e2e")
                        put("entityKey", "")
                        put("version", 1L)
                        put("schemaId", "entity.product.v1")
                        put("schemaVersion", "1.0.0")
                        put("payload", buildJsonObject {
                            put("name", "Empty Key")
                        })
                    }.toString())
                }

                assert(response.status.value in listOf(400, 500)) {
                    "Expected 400 or 500 but got ${response.status}"
                }
            }
        }
    }

    describe("Multi-tenant HTTP 레벨 격리") {

        it("Tenant A Ingest → Tenant B Query → 데이터 없음") {
            testApplication {
                configureFullApp()

                // Tenant A Ingest
                val ingestResponse = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody(tenantId = "tenant-A", entityKey = "product:ISO-001"))
                }
                ingestResponse.status shouldBe HttpStatusCode.OK
                val version = Json.parseToJsonElement(ingestResponse.bodyAsText())
                    .jsonObject["version"]?.jsonPrimitive?.long!!

                // Tenant B Query → 데이터 없음 (에러 응답)
                val queryResponse = client.post("/api/v1/query") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "tenant-B")
                        put("viewId", "view.product.pdp.v1")
                        put("entityKey", "product:ISO-001")
                        put("version", version)
                    }.toString())
                }

                // Tenant B에는 데이터가 없으므로 에러 응답
                assert(queryResponse.status.value in listOf(400, 404, 500)) {
                    "Expected error for cross-tenant query but got ${queryResponse.status}"
                }
            }
        }

        it("동일 entityKey, 다른 tenant → 각각 독립 Ingest + Query") {
            testApplication {
                configureFullApp()

                // Tenant A Ingest
                val respA = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody(
                        tenantId = "tenant-A",
                        entityKey = "product:DUAL-001",
                        name = "Tenant A Product",
                        price = 10000
                    ))
                }
                respA.status shouldBe HttpStatusCode.OK

                // Tenant B Ingest (동일 entityKey, 다른 데이터)
                val respB = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody(
                        tenantId = "tenant-B",
                        entityKey = "product:DUAL-001",
                        name = "Tenant B Product",
                        price = 20000
                    ))
                }
                respB.status shouldBe HttpStatusCode.OK

                val versionA = Json.parseToJsonElement(respA.bodyAsText())
                    .jsonObject["version"]?.jsonPrimitive?.long!!
                val versionB = Json.parseToJsonElement(respB.bodyAsText())
                    .jsonObject["version"]?.jsonPrimitive?.long!!

                // Tenant A Query → Tenant A 데이터
                val queryA = client.post("/api/v1/query") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "tenant-A")
                        put("viewId", "view.product.pdp.v1")
                        put("entityKey", "product:DUAL-001")
                        put("version", versionA)
                    }.toString())
                }
                queryA.status shouldBe HttpStatusCode.OK

                // Tenant B Query → Tenant B 데이터
                val queryB = client.post("/api/v1/query") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", "tenant-B")
                        put("viewId", "view.product.pdp.v1")
                        put("entityKey", "product:DUAL-001")
                        put("version", versionB)
                    }.toString())
                }
                queryB.status shouldBe HttpStatusCode.OK
            }
        }
    }

    describe("Ingest 응답 필드 검증") {

        it("Ingest 응답에 sliceCount, viewCount, durationMs 포함") {
            testApplication {
                configureFullApp()

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody())
                }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["success"]?.jsonPrimitive?.boolean shouldBe true

                val sliceCount = json["sliceCount"]?.jsonPrimitive?.int
                val viewCount = json["viewCount"]?.jsonPrimitive?.int
                val durationMs = json["durationMs"]?.jsonPrimitive?.long
                check(sliceCount != null && sliceCount >= 1) { "sliceCount should be >= 1" }
                check(viewCount != null && viewCount >= 1) { "viewCount should be >= 1" }
                check(durationMs != null && durationMs >= 0) { "durationMs should be >= 0" }
            }
        }

        it("jobId 전파 검증") {
            testApplication {
                configureFullApp()

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildIngestBody(jobId = "http-job-001"))
                }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["jobId"]?.jsonPrimitive?.content shouldBe "http-job-001"
            }
        }
    }
})
