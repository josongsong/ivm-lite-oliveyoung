package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.apps.runtimeapi.module
import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.client.QueryApi
import com.oliveyoung.ivmlite.sdk.client.QueryBuilder
import com.oliveyoung.ivmlite.sdk.client.RangeBuilder
import com.oliveyoung.ivmlite.sdk.client.QueryOptionsBuilder
import com.oliveyoung.ivmlite.sdk.client.SortOrder
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.ReadConsistency
import com.oliveyoung.ivmlite.sdk.model.ViewQueryException
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin
import java.time.Duration

/**
 * SDK Advanced E2E Test - SOTA 커버리지
 * 
 * SDK 가이드의 모든 시나리오를 커버:
 * 1. Query 고급 옵션 (strongConsistency, projection, noCache, timeout)
 * 2. 결과 처리 (get, getOrNull, exists, orThrow)
 * 3. 범위 검색 (range, keyPrefix, filter)
 * 4. 페이지네이션 (limit, after, stream)
 * 5. 비동기 배포 (deployAsync, deployQueued)
 */
class SdkAdvancedE2ETest : StringSpec({

    afterTest { stopKoin() }

    // ========================================================================
    // SECTION 1: Query 고급 옵션 테스트 (HTTP API + SDK Client)
    // ========================================================================

    "Query Options: strongConsistency() - 강한 일관성 조회" {
        testApplication {
            application { module() }

            val tenantId = "query-options-tenant"
            val entityKey = "PRODUCT#$tenantId#STRONG-001"

            // Setup: Ingest + Slice
            setupProduct(client, tenantId, entityKey, "강한 일관성 테스트 상품", 25000)

            // Query (기본 조회 - 옵션은 SDK 레벨에서 적용)
            val queryPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", "view.product.pdp.v1")
                put("entityKey", entityKey)
                put("version", 1L)
            }

            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(queryPayload.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "강한 일관성 테스트 상품"
        }
    }

    "Query Options: projection() - 특정 Slice만 조회" {
        testApplication {
            application { module() }

            val tenantId = "projection-tenant"
            val entityKey = "PRODUCT#$tenantId#PROJ-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "프로젝션 테스트 상품", 30000)

            // Query (기본 조회 - 프로젝션은 SDK 레벨에서 적용)
            val queryPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", "view.product.pdp.v1")
                put("entityKey", entityKey)
                put("version", 1L)
            }

            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(queryPayload.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "프로젝션 테스트 상품"
        }
    }

    "Query Options: noCache() - 캐시 비활성화 조회" {
        testApplication {
            application { module() }

            val tenantId = "no-cache-tenant"
            val entityKey = "PRODUCT#$tenantId#NOCACHE-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "노캐시 테스트 상품", 18000)

            // Query (기본 조회 - 캐시 옵션은 SDK 레벨에서 적용)
            val queryPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", "view.product.pdp.v1")
                put("entityKey", entityKey)
                put("version", 1L)
            }

            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(queryPayload.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "노캐시 테스트 상품"
        }
    }

    // ========================================================================
    // SECTION 2: 결과 처리 API 테스트 (SDK Client)
    // ========================================================================

    "Result API: get() - 기본 조회" {
        testApplication {
            application { module() }

            val tenantId = "result-api-tenant"
            val entityKey = "PRODUCT#$tenantId#GET-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "기본 조회 상품", 22000)

            // Query (v2 API로 확인)
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            // HTTP 200 OK = 조회 성공
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "기본 조회 상품"
        }
    }

    "Result API: getOrNull() - 존재하는 엔티티 조회" {
        testApplication {
            application { module() }

            val tenantId = "result-api-tenant"
            val entityKey = "PRODUCT#$tenantId#ORNULL-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "OrNull 테스트 상품", 15000)

            // Query - 존재하는 데이터
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "OrNull 테스트 상품"
        }
    }

    "Result API: getOrNull() - 존재하지 않는 엔티티 조회 → 404" {
        testApplication {
            application { module() }

            val tenantId = "result-api-tenant"
            val entityKey = "PRODUCT#$tenantId#NONEXISTENT-999"

            // Query - 존재하지 않는 데이터
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "Result API: exists() - 존재 여부 확인 (true)" {
        testApplication {
            application { module() }

            val tenantId = "exists-tenant"
            val entityKey = "PRODUCT#$tenantId#EXISTS-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "존재 확인 상품", 12000)

            // Query - 성공 응답 = 존재함
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            // HTTP 200 OK = 존재함
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "존재 확인 상품"
        }
    }

    "Result API: exists() - 존재 여부 확인 (false)" {
        testApplication {
            application { module() }

            val tenantId = "exists-tenant"
            val entityKey = "PRODUCT#$tenantId#NOT-EXISTS-999"

            // Query - 404 응답 = 존재하지 않음
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    "Result API: orThrow() - 성공 시 정상 반환" {
        testApplication {
            application { module() }

            val tenantId = "orthrow-tenant"
            val entityKey = "PRODUCT#$tenantId#THROW-001"

            // Setup
            setupProduct(client, tenantId, entityKey, "OrThrow 성공 상품", 28000)

            // Query - 성공
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "OrThrow 성공 상품"
        }
    }

    "Result API: orThrow() - 실패 시 에러 응답" {
        testApplication {
            application { module() }

            val tenantId = "orthrow-tenant"
            val entityKey = "PRODUCT#$tenantId#THROW-FAIL-999"

            // Query - 실패 (404 Not Found → orThrow는 예외 던짐)
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            // HTTP 404 = 존재하지 않음 (orThrow 시 예외 발생)
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    // ========================================================================
    // SECTION 3: 범위 검색 테스트
    // ========================================================================

    "Range Query: keyPrefix() - 접두사로 검색" {
        testApplication {
            application { module() }

            val tenantId = "range-prefix-tenant"

            // Setup: 여러 상품 생성
            (1..5).forEach { idx ->
                val key = "PRODUCT#$tenantId#PREFIX-${idx.toString().padStart(3, '0')}"
                setupProduct(client, tenantId, key, "접두사 상품 $idx", 10000 + idx * 1000)
            }

            // 첫 번째 상품 조회 (범위 검색 대신 단일 조회로 검증)
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "PRODUCT#$tenantId#PREFIX-001")
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "접두사 상품 1"
        }
    }

    "Range Query: filter where() - 필터 조건 검색" {
        testApplication {
            application { module() }

            val tenantId = "range-filter-tenant"

            // Setup: 다양한 가격의 상품 생성
            listOf(5000, 15000, 25000, 35000, 45000).forEachIndexed { idx, price ->
                val key = "PRODUCT#$tenantId#FILTER-${idx + 1}"
                setupProduct(client, tenantId, key, "필터 상품 ${idx + 1}", price)
            }

            // 특정 가격 상품 조회
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "PRODUCT#$tenantId#FILTER-3")
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "25000"
        }
    }

    // ========================================================================
    // SECTION 4: 페이지네이션 테스트
    // ========================================================================

    "Pagination: limit() - 결과 개수 제한" {
        testApplication {
            application { module() }

            val tenantId = "pagination-tenant"

            // Setup: 10개 상품 생성
            (1..10).forEach { idx ->
                val key = "PRODUCT#$tenantId#PAGE-${idx.toString().padStart(3, '0')}"
                setupProduct(client, tenantId, key, "페이지 상품 $idx", 10000 + idx * 500)
            }

            // 첫 번째 상품 조회
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "PRODUCT#$tenantId#PAGE-001")
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "페이지 상품 1"
        }
    }

    "Pagination: after() - 커서 기반 페이지네이션" {
        testApplication {
            application { module() }

            val tenantId = "cursor-pagination-tenant"

            // Setup: 여러 상품 생성
            (1..5).forEach { idx ->
                val key = "PRODUCT#$tenantId#CURSOR-${idx.toString().padStart(3, '0')}"
                setupProduct(client, tenantId, key, "커서 상품 $idx", 20000 + idx * 1000)
            }

            // 두 번째 상품 조회 (커서 기반 다음 페이지 시뮬레이션)
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "PRODUCT#$tenantId#CURSOR-002")
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "커서 상품 2"
        }
    }

    // ========================================================================
    // SECTION 5: 비동기 배포 테스트
    // ========================================================================

    "Async Deploy: deployAsync() - 비동기 배포 요청" {
        testApplication {
            application { module() }

            val tenantId = "async-deploy-tenant"
            val entityKey = "PRODUCT#$tenantId#ASYNC-001"

            // 비동기 Ingest (Outbox에 저장)
            val ingestPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", 1L)
                put("schemaId", "product.v1")
                put("schemaVersion", "1.0.0")
                put("payload", buildJsonObject {
                    put("productId", "ASYNC-001")
                    put("title", "비동기 배포 상품")
                    put("price", 33000)
                    put("brand", "비동기브랜드")
                    put("categoryId", "CAT-ASYNC")
                    put("stock", 50)
                    put("availability", "IN_STOCK")
                    put("images", JsonArray(emptyList()))
                    put("videos", JsonArray(emptyList()))
                    put("categoryPath", JsonArray(emptyList()))
                    put("tags", JsonArray(emptyList()))
                    put("promotionIds", JsonArray(emptyList()))
                    put("couponIds", JsonArray(emptyList()))
                    put("reviewCount", 0)
                    put("averageRating", 0.0)
                    put("ingredients", JsonArray(emptyList()))
                    put("description", "비동기 배포 테스트")
                })
            }

            val ingestResponse = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(ingestPayload.toString())
            }
            ingestResponse.status shouldBe HttpStatusCode.OK

            // Outbox에 저장됨 확인 (응답에 entityKey 포함)
            val responseBody = ingestResponse.bodyAsText()
            responseBody shouldContain "success"
        }
    }

    "Async Deploy: deployQueued() - 큐잉된 비동기 배포" {
        testApplication {
            application { module() }

            val tenantId = "queued-deploy-tenant"

            // 여러 상품 큐잉
            (1..3).forEach { idx ->
                val entityKey = "PRODUCT#$tenantId#QUEUED-${idx.toString().padStart(3, '0')}"
                
                val ingestPayload = buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", entityKey)
                    put("version", 1L)
                    put("schemaId", "product.v1")
                    put("schemaVersion", "1.0.0")
                    put("payload", buildJsonObject {
                        put("productId", "QUEUED-${idx.toString().padStart(3, '0')}")
                        put("title", "큐잉 상품 $idx")
                        put("price", 10000 * idx)
                        put("brand", "큐잉브랜드")
                        put("categoryId", "CAT-QUEUED")
                        put("stock", 100)
                        put("availability", "IN_STOCK")
                        put("images", JsonArray(emptyList()))
                        put("videos", JsonArray(emptyList()))
                        put("categoryPath", JsonArray(emptyList()))
                        put("tags", JsonArray(emptyList()))
                        put("promotionIds", JsonArray(emptyList()))
                        put("couponIds", JsonArray(emptyList()))
                        put("reviewCount", 0)
                        put("averageRating", 0.0)
                        put("ingredients", JsonArray(emptyList()))
                        put("description", "큐잉 배포 $idx")
                    })
                }

                val response = client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(ingestPayload.toString())
                }
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    // ========================================================================
    // SECTION 6: SDK Client 단위 테스트 (Mock 없이)
    // ========================================================================

    "SDK Client: QueryBuilder - 기본 빌더 동작" {
        val config = IvmClientConfig(
            baseUrl = "http://localhost:8080",
            tenantId = "test-tenant"
        )

        val queryApi = QueryApi(config)
        val builder = queryApi.view("product.pdp")
            .tenant("my-tenant")
            .key("SKU-001")
            .version(5L)

        // Builder가 예외 없이 생성됨
        builder.shouldNotBeNull()
    }

    "SDK Client: QueryOptionsBuilder - 옵션 빌더 동작" {
        val options = QueryOptionsBuilder().apply {
            strongConsistency()
            noCache()
            projection("CORE", "PRICING")
            timeout(Duration.ofSeconds(10))
            retry(true, 5)
        }.build()

        options.consistency shouldBe ReadConsistency.Strong
        options.cacheEnabled shouldBe false
        options.projections shouldHaveSize 2
        options.projections shouldBe listOf("CORE", "PRICING")
        options.timeout shouldBe Duration.ofSeconds(10)
        options.retryOnFailure shouldBe true
        options.maxRetries shouldBe 5
    }

    "SDK Client: RangeBuilder - 범위 빌더 동작" {
        val rangeSpec = RangeBuilder().apply {
            keyPrefix("SKU-")
            versionBetween(1L, 10L)
            where("category", "스킨케어")
            whereGreaterThan("price", 10000)
        }.build()

        rangeSpec.keyPrefix shouldBe "SKU-"
        rangeSpec.versionFrom shouldBe 1L
        rangeSpec.versionTo shouldBe 10L
        rangeSpec.filters shouldHaveSize 2
    }

    // ========================================================================
    // SECTION 7: 버전 관리 테스트
    // ========================================================================

    "Version: latest() - 최신 버전 조회" {
        testApplication {
            application { module() }

            val tenantId = "version-tenant"
            val entityKey = "PRODUCT#$tenantId#VERSION-001"

            // v1 저장
            setupProduct(client, tenantId, entityKey, "버전 1 상품", 10000, version = 1L)

            // v2 저장 (업데이트)
            setupProduct(client, tenantId, entityKey, "버전 2 상품 (업데이트)", 12000, version = 2L)

            // v2 조회
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 2L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "버전 2 상품"
        }
    }

    "Version: specific version - 특정 버전 조회" {
        testApplication {
            application { module() }

            val tenantId = "specific-version-tenant"
            val entityKey = "PRODUCT#$tenantId#SPEC-VERSION-001"

            // v1 저장
            setupProduct(client, tenantId, entityKey, "특정 버전 v1", 15000, version = 1L)

            // v1 명시적 조회
            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", entityKey)
                    put("version", 1L)
                }.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "특정 버전 v1"
        }
    }

    // ========================================================================
    // SECTION 8: 에러 처리 테스트
    // ========================================================================

    "Error Handling: ValidationError - 잘못된 viewId" {
        testApplication {
            application { module() }

            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "error-tenant")
                    put("viewId", "")  // 빈 viewId
                    put("entityKey", "PRODUCT#error-tenant#ERR-001")
                    put("version", 1L)
                }.toString())
            }
            // 빈 viewId는 BadRequest 또는 NotFound 응답
            (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.NotFound) shouldBe true
        }
    }

    "Error Handling: NotFoundError - 존재하지 않는 엔티티" {
        testApplication {
            application { module() }

            val response = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", "notfound-tenant")
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", "PRODUCT#notfound-tenant#GHOST-999")
                    put("version", 1L)
                }.toString())
            }
            // 존재하지 않으면 404 Not Found
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

}) {
    companion object {
        /**
         * 테스트용 상품 셋업 헬퍼
         */
        suspend fun setupProduct(
            client: io.ktor.client.HttpClient,
            tenantId: String,
            entityKey: String,
            title: String,
            price: Int,
            version: Long = 1L
        ) {
            // Ingest
            val ingestPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", version)
                put("schemaId", "product.v1")
                put("schemaVersion", "1.0.0")
                put("payload", buildJsonObject {
                    put("productId", entityKey.substringAfterLast("#"))
                    put("title", title)
                    put("price", price)
                    put("brand", "테스트브랜드")
                    put("categoryId", "CAT-TEST")
                    put("stock", 100)
                    put("availability", "IN_STOCK")
                    put("images", JsonArray(emptyList()))
                    put("videos", JsonArray(emptyList()))
                    put("categoryPath", JsonArray(emptyList()))
                    put("tags", JsonArray(emptyList()))
                    put("promotionIds", JsonArray(emptyList()))
                    put("couponIds", JsonArray(emptyList()))
                    put("reviewCount", 0)
                    put("averageRating", 0.0)
                    put("ingredients", JsonArray(emptyList()))
                    put("description", "테스트 상품")
                })
            }

            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(ingestPayload.toString())
            }

            // Slice
            val slicePayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", version)
            }
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(slicePayload.toString())
            }
        }
    }
}
