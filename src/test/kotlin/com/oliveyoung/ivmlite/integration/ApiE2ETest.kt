package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.apps.runtimeapi.module
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.stopKoin

/**
 * API E2E Test (RFC-IMPL Phase B-3)
 *
 * HTTP 요청 → 응답 테스트 (Ktor TestApplication 사용)
 *
 * 테스트 시나리오:
 * 1. POST /api/v1/ingest → 성공 응답
 * 2. POST /api/v1/slice → Slice 생성
 * 3. POST /api/v1/query → JSON 응답
 * 4. Health check
 */
class ApiE2ETest : StringSpec({

    afterTest { stopKoin() }

    "Health check - GET /health → 200 OK" {
        testApplication {
            application { module() }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().shouldContain("UP")
        }
    }

    "Ingest API - POST /api/v1/ingest → 200 OK" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "api-e2e-tenant",
                        "entityKey": "PRODUCT#api-e2e-tenant#api-test-001",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "API E2E Test Product", "price": 19900}
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body.shouldContain("success")
            body.shouldContain("true")
        }
    }

    "Ingest API - 잘못된 JSON → 400 Bad Request" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""not a valid json""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    "Slice API - POST /api/v1/slice → Slice 생성" {
        testApplication {
            application { module() }

            // 먼저 Ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "slice-api-tenant",
                        "entityKey": "PRODUCT#slice-api-tenant#slice-001",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Slice API Test"}
                    }
                """.trimIndent())
            }

            // Slice 실행
            val response = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "slice-api-tenant",
                        "entityKey": "PRODUCT#slice-api-tenant#slice-001",
                        "version": 1
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body.shouldContain("success")
            body.shouldContain("CORE")
        }
    }

    "Query API - POST /api/v1/query → JSON 응답" {
        testApplication {
            application { module() }

            // Ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "query-api-tenant",
                        "entityKey": "PRODUCT#query-api-tenant#query-001",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Query API Test", "category": "Electronics"}
                    }
                """.trimIndent())
            }

            // Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "query-api-tenant",
                        "entityKey": "PRODUCT#query-api-tenant#query-001",
                        "version": 1
                    }
                """.trimIndent())
            }

            // Query
            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "query-api-tenant",
                        "viewId": "default",
                        "entityKey": "PRODUCT#query-api-tenant#query-001",
                        "version": 1,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body.shouldContain("Query API Test")
            body.shouldContain("Electronics")
        }
    }

    "Query API - 존재하지 않는 Slice → 404 Not Found" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "not-exists-tenant",
                        "viewId": "default",
                        "entityKey": "PRODUCT#not-exists#missing",
                        "version": 999,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "Query API - 잘못된 sliceType → 400 Bad Request" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "invalid-type-tenant",
                        "viewId": "default",
                        "entityKey": "PRODUCT#invalid-type#test",
                        "version": 1,
                        "sliceTypes": ["INVALID_TYPE"]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    "전체 플로우 - Ingest → Slice → Query" {
        testApplication {
            application { module() }

            val tenantId = "full-flow-tenant"
            val entityKey = "PRODUCT#full-flow-tenant#product-full"

            // Step 1: Ingest
            val ingestResponse = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Full Flow Product", "price": 50000, "stock": 100}
                    }
                """.trimIndent())
            }
            ingestResponse.status shouldBe HttpStatusCode.OK

            // Step 2: Slice
            val sliceResponse = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1
                    }
                """.trimIndent())
            }
            sliceResponse.status shouldBe HttpStatusCode.OK
            sliceResponse.bodyAsText().shouldContain("CORE")

            // Step 3: Query
            val queryResponse = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "viewId": "v1",
                        "entityKey": "$entityKey",
                        "version": 1,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            body.shouldContain("Full Flow Product")
            body.shouldContain("50000")
            body.shouldContain("100")
        }
    }

    "Query API v2 - POST /api/v2/query (ViewDefinition 기반)" {
        testApplication {
            application { module() }

            val tenantId = "v2-api-tenant"
            val entityKey = "PRODUCT#v2-api-tenant#v2-test-001"

            // Ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "V2 API Test", "price": 30000}
                    }
                """.trimIndent())
            }

            // Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1
                    }
                """.trimIndent())
            }

            // Query v2 (ViewDefinition 기반 - sliceTypes 없음)
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "viewId": "view.product.pdp.v1",
                        "entityKey": "$entityKey",
                        "version": 1
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body.shouldContain("V2 API Test")
            body.shouldContain("view.product.pdp.v1")
        }
    }

    "Readiness Probe - GET /ready → 동적 어댑터 체크" {
        testApplication {
            application { module() }

            val response = client.get("/ready")

            // 모든 어댑터 정상이면 200, 하나라도 실패하면 503
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body.shouldContain("status")
            body.shouldContain("checks")
        }
    }

    // ==================== 멀티 테넌트 격리 E2E ====================

    "멀티 테넌트 격리 - Tenant A 데이터를 Tenant B가 접근 불가" {
        testApplication {
            application { module() }

            val tenantA = "isolation-tenant-A"
            val tenantB = "isolation-tenant-B"
            val entityKeyA = "PRODUCT#$tenantA#secret-product"
            val entityKeyB = "PRODUCT#$tenantB#secret-product"

            // Tenant A: Ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantA",
                        "entityKey": "$entityKeyA",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Tenant A Secret", "secret": "A-CONFIDENTIAL"}
                    }
                """.trimIndent())
            }

            // Tenant A: Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantA",
                        "entityKey": "$entityKeyA",
                        "version": 1
                    }
                """.trimIndent())
            }

            // Tenant B: Ingest (다른 데이터)
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantB",
                        "entityKey": "$entityKeyB",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Tenant B Product", "secret": "B-CONFIDENTIAL"}
                    }
                """.trimIndent())
            }

            // Tenant B: Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantB",
                        "entityKey": "$entityKeyB",
                        "version": 1
                    }
                """.trimIndent())
            }

            // Tenant A: 자신의 데이터 조회 → 성공
            val responseA = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantA",
                        "viewId": "default",
                        "entityKey": "$entityKeyA",
                        "version": 1,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }
            responseA.status shouldBe HttpStatusCode.OK
            val bodyA = responseA.bodyAsText()
            bodyA.shouldContain("Tenant A Secret")
            bodyA.shouldContain("A-CONFIDENTIAL")

            // Tenant A: Tenant B 데이터 접근 시도 → 실패 (404)
            val crossAccessResponse = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantA",
                        "viewId": "default",
                        "entityKey": "$entityKeyB",
                        "version": 1,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }
            crossAccessResponse.status shouldBe HttpStatusCode.NotFound
        }
    }

    // ==================== 에러 응답 상세 검증 ====================

    "에러 응답 - NotFoundError → 404 + ApiError 형식" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "error-test-tenant",
                        "viewId": "default",
                        "entityKey": "PRODUCT#error-test#not-exists",
                        "version": 999,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.NotFound
            val body = response.bodyAsText()
            
            // ApiError 형식 검증
            body.shouldContain("code")
            body.shouldContain("message")
            body.shouldContain("ERR_NOT_FOUND")
        }
    }

    "에러 응답 - ValidationError → 400 + ApiError 형식" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "validation-test",
                        "viewId": "default",
                        "entityKey": "PRODUCT#validation-test#test",
                        "version": 1,
                        "sliceTypes": ["INVALID_SLICE_TYPE"]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.BadRequest
            val body = response.bodyAsText()
            
            // ApiError 형식 검증
            body.shouldContain("code")
            body.shouldContain("message")
        }
    }

    "에러 응답 - 빈 tenantId → 400 Bad Request" {
        testApplication {
            application { module() }

            val response = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "",
                        "entityKey": "PRODUCT#test#test",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Test"}
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // ==================== INCREMENTAL Slicing E2E (HTTP API) ====================

    "INCREMENTAL - v1→v2 업데이트 후 Query (HTTP API)" {
        testApplication {
            application { module() }

            val tenantId = "incremental-api-tenant"
            val entityKey = "PRODUCT#$tenantId#incremental-001"

            // v1 Ingest
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Original Product", "price": 10000}
                    }
                """.trimIndent())
            }

            // v1 Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 1
                    }
                """.trimIndent())
            }

            // v2 Ingest (가격 변경)
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 2,
                        "schemaId": "product.v1",
                        "schemaVersion": "1.0.0",
                        "payload": {"name": "Updated Product", "price": 15000}
                    }
                """.trimIndent())
            }

            // v2 Slice (INCREMENTAL 자동 선택)
            val sliceResponse = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "entityKey": "$entityKey",
                        "version": 2
                    }
                """.trimIndent())
            }
            sliceResponse.status shouldBe HttpStatusCode.OK

            // v2 Query - 변경 반영 확인
            val v2Response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "viewId": "default",
                        "entityKey": "$entityKey",
                        "version": 2,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }
            v2Response.status shouldBe HttpStatusCode.OK
            val v2Body = v2Response.bodyAsText()
            v2Body.shouldContain("Updated Product")
            v2Body.shouldContain("15000")

            // v1 Query - 이전 버전 유지 확인 (버전 독립성)
            val v1Response = client.post("/api/v1/query") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tenantId": "$tenantId",
                        "viewId": "default",
                        "entityKey": "$entityKey",
                        "version": 1,
                        "sliceTypes": ["CORE"]
                    }
                """.trimIndent())
            }
            v1Response.status shouldBe HttpStatusCode.OK
            val v1Body = v1Response.bodyAsText()
            v1Body.shouldContain("Original Product")
            v1Body.shouldContain("10000")
        }
    }
})
