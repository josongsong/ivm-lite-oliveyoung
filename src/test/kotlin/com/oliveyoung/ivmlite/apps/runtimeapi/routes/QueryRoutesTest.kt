package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
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
 * QueryRoutes HTTP-level 테스트
 *
 * POST /api/v1/slice: Slicing 실행
 * POST /api/v1/query: View 조회 (sliceTypes 없으면 ViewDefinition 기반)
 */
class QueryRoutesTest : StringSpec(init@{
    tags(com.oliveyoung.ivmlite.integration.IntegrationTag)

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

    val orchestrator = IngestionOrchestrator(
        workflow = ingestionWorkflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = InMemorySinkRuleRegistry(),
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())

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

    suspend fun seedProduct(
        tenantId: String = "test-tenant",
        entityKey: String = "product:SKU-001",
        name: String = "Test Product",
        price: Int = 29000,
        version: Long = 1L
    ) {
        orchestrator.ingest(
            IngestionCommand(
                tenantId = TenantId(tenantId),
                entityKey = EntityKey(entityKey),
                data = buildJsonObject {
                    put("name", name)
                    put("price", price)
                    put("category", "skincare")
                },
                ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0")),
                viewDefId = "view.product.pdp.v1",
                version = version
            )
        )
    }

    fun ApplicationTestBuilder.configureQueryApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Koin) {
                modules(module {
                    single { slicingWorkflow }
                    single { queryViewWorkflow }
                })
            }
            routing {
                queryRoutes()
            }
        }
    }

    beforeTest {
        runCatching { stopKoin() }
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        invertedIndexRepo.clear()
    }

    "POST /api/v1/slice → 200 OK, Slicing 성공" {
        seedProduct()

        testApplication {
            configureQueryApp()

            val response = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "test-tenant")
                    put("entityKey", "product:SKU-001")
                    put("version", 1L)
                }.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            val count = json["count"]?.jsonPrimitive?.int
            check(count != null) { "count field missing from response" }
            assert(count >= 1) { "count should be >= 1 but was $count" }
        }
    }

    "POST /api/v1/slice → RawData 없으면 에러" {
        testApplication {
            configureQueryApp()

            val response = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "no-tenant")
                    put("entityKey", "product:NONE")
                    put("version", 1L)
                }.toString())
            }

            // RawData가 없으면 NotFoundError → 404
            assert(response.status.value in listOf(404, 500)) {
                "Expected 404 or 500 but got ${response.status}"
            }
        }
    }

    "POST /api/v1/query → 200 OK, ViewDefinition 기반 조회" {
        seedProduct()

        testApplication {
            configureQueryApp()

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "test-tenant")
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "product:SKU-001")
                    put("version", 1L)
                }.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            // 응답은 { "data": {...}, "meta": {...} } 구조
            assert(json.containsKey("data")) { "Response should contain 'data' key" }
        }
    }

    "POST /api/v1/query → 존재하지 않는 데이터 → 에러" {
        testApplication {
            configureQueryApp()

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "no-tenant")
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "product:NONE")
                    put("version", 1L)
                }.toString())
            }

            assert(response.status.value in listOf(400, 404, 500)) {
                "Expected error status but got ${response.status}"
            }
        }
    }

    "POST /api/v1/slice → sliceTypes 응답에 포함" {
        seedProduct()

        testApplication {
            configureQueryApp()

            val response = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "test-tenant")
                    put("entityKey", "product:SKU-001")
                    put("version", 1L)
                }.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val sliceTypes = json["sliceTypes"]?.jsonArray
            check(sliceTypes != null) { "sliceTypes field missing from response" }
            assert(sliceTypes.isNotEmpty()) { "sliceTypes should not be empty" }
        }
    }
})
