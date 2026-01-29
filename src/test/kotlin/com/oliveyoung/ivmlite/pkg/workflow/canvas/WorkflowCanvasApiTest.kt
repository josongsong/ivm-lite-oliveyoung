package com.oliveyoung.ivmlite.pkg.workflow.canvas

import com.oliveyoung.ivmlite.apps.admin.routes.*
import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.ports.WorkflowGraphBuilderPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.WorkflowCanvasService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * Workflow Canvas API 통합 테스트 (RFC-IMPL-015)
 *
 * 핵심 시나리오:
 * 1. GET /workflow/graph - 전체 그래프 조회
 * 2. GET /workflow/graph?entityType=PRODUCT - 필터링된 그래프
 * 3. GET /workflow/nodes/{nodeId} - 노드 상세 조회
 * 4. GET /workflow/nodes/{invalid} - 404 에러
 * 5. GET /workflow/stats - 통계 조회
 */
class WorkflowCanvasApiTest : StringSpec({

    beforeEach { runCatching { stopKoin() } }
    afterEach { runCatching { stopKoin() } }

    fun ApplicationTestBuilder.setupTestApp() {
        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
            install(Koin) {
                modules(module {
                    single<WorkflowGraphBuilderPort> { WorkflowGraphBuilder() }
                    single { WorkflowCanvasService(get(), null) }
                })
            }
            routing {
                route("/api") {
                    workflowCanvasRoutes()
                }
            }
        }
    }

    "GET /api/workflow/graph - 전체 그래프 반환" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/graph")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "nodes"
            body shouldContain "edges"
            body shouldContain "metadata"
        }
    }

    "GET /api/workflow/graph?entityType=PRODUCT - 필터링된 그래프" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/graph?entityType=PRODUCT")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "PRODUCT"
        }
    }

    "GET /api/workflow/nodes/{nodeId} - 존재하는 노드 상세" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/nodes/rawdata_PRODUCT")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "rawdata_PRODUCT"
            body shouldContain "upstreamNodes"
            body shouldContain "downstreamNodes"
        }
    }

    "GET /api/workflow/nodes/{invalid} - 존재하지 않는 노드 404" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/nodes/nonexistent_node")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "GET /api/workflow/stats - 워크플로우 통계" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/stats")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "entityTypes"
            body shouldContain "totalNodes"
            body shouldContain "healthSummary"
        }
    }

    "GET /api/workflow/graph - React Flow 호환 응답 구조" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/graph")
            val body = response.bodyAsText()

            // React Flow 필수 필드
            body shouldContain "\"id\""
            body shouldContain "\"type\""
            body shouldContain "\"position\""
            body shouldContain "\"data\""
            body shouldContain "\"source\""
            body shouldContain "\"target\""
        }
    }

    "GET /api/workflow/graph - 노드 상태 포함" {
        testApplication {
            setupTestApp()

            val response = client.get("/api/workflow/graph")
            val body = response.bodyAsText()

            // 상태 필드 확인
            body shouldContain "\"status\""
            // healthy, warning, error, inactive 중 하나
            (body.contains("healthy") || body.contains("inactive")) shouldBe true
        }
    }
})
