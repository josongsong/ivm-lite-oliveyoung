package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.apps.runtimeapi.module
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin

/**
 * SDK E2E Test (RFC-IMPL-011)
 *
 * Fluent SDK 스타일의 HTTP API E2E 테스트:
 * - testApplication을 통한 HTTP 호출 시뮬레이션
 * - Brand, Category, Product 엔티티 타입
 */
class SdkE2ETest : StringSpec({

    afterTest { stopKoin() }

    // ==================== Product Entity E2E ====================

    "SDK E2E: Product Ingest → Slice → Query" {
        testApplication {
            application { module() }

            val tenantId = "sdk-product-tenant"
            val entityKey = "PRODUCT#$tenantId#SDK-PROD-001"

            // Step 1: Ingest
            val ingestPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", 1L)
                put("schemaId", "product.v1")
                put("schemaVersion", "1.0.0")
                put("payload", buildJsonObject {
                    put("productId", "SDK-PROD-001")
                    put("title", "SDK 테스트 크림")
                    put("price", 35000)
                    put("brand", "올리브영")
                    put("categoryId", "CAT-SKINCARE")
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
                    put("description", "SDK E2E 테스트 상품입니다")
                })
            }

            val ingestResponse = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(ingestPayload.toString())
            }
            ingestResponse.status shouldBe HttpStatusCode.OK

            // Step 2: Slice
            val slicePayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", 1L)
            }
            val sliceResponse = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(slicePayload.toString())
            }
            sliceResponse.status shouldBe HttpStatusCode.OK

            // Step 3: Query (v2)
            val queryPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", "view.product.pdp.v1")
                put("entityKey", entityKey)
                put("version", 1L)
            }
            val queryResponse = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(queryPayload.toString())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            val queryBody = queryResponse.bodyAsText()
            queryBody shouldContain "SDK 테스트 크림"
            queryBody shouldContain "35000"
        }
    }

    // ==================== Brand-like Entity E2E ====================

    "SDK E2E: Brand-like Product Ingest → Slice → Query" {
        testApplication {
            application { module() }

            val tenantId = "sdk-brand-tenant"
            // PRODUCT 접두사 사용 (현재 시스템은 PRODUCT에 최적화)
            val entityKey = "PRODUCT#$tenantId#BRAND-001"

            // Brand-like Product Ingest
            val ingestPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", 1L)
                put("schemaId", "product.v1")
                put("schemaVersion", "1.0.0")
                put("payload", buildJsonObject {
                    put("productId", "BRAND-001")
                    put("title", "라네즈")
                    put("price", 0)
                    put("brand", "라네즈")
                    put("categoryId", "BRAND")
                    put("stock", 0)
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
                    put("description", "수분 전문 브랜드")
                })
            }

            val ingestResponse = client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(ingestPayload.toString())
            }
            ingestResponse.status shouldBe HttpStatusCode.OK

            // Slice
            val slicePayload = buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", 1L)
            }
            val sliceResponse = client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(slicePayload.toString())
            }
            sliceResponse.status shouldBe HttpStatusCode.OK

            // Query (v2)
            val queryPayload = buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", "view.product.pdp.v1")
                put("entityKey", entityKey)
                put("version", 1L)
            }
            val queryResponse = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(queryPayload.toString())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            val queryBody = queryResponse.bodyAsText()
            queryBody shouldContain "라네즈"
        }
    }

    // ==================== Category-like Entity E2E ====================

    "SDK E2E: Category-like Product Ingest → Query" {
        testApplication {
            application { module() }

            val tenantId = "sdk-category-tenant"
            // PRODUCT 접두사 사용
            val leafKey = "PRODUCT#$tenantId#CAT-TONER"

            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", leafKey)
                    put("version", 1L)
                    put("schemaId", "product.v1")
                    put("schemaVersion", "1.0.0")
                    put("payload", buildJsonObject {
                        put("productId", "CAT-TONER")
                        put("title", "토너/스킨")
                        put("price", 0)
                        put("brand", "CATEGORY")
                        put("categoryId", "CAT-SKINCARE")
                        put("stock", 0)
                        put("availability", "IN_STOCK")
                        put("images", JsonArray(emptyList()))
                        put("videos", JsonArray(emptyList()))
                        put("categoryPath", JsonArray(listOf(
                            JsonPrimitive("CAT-ROOT"),
                            JsonPrimitive("CAT-SKINCARE")
                        )))
                        put("tags", JsonArray(emptyList()))
                        put("promotionIds", JsonArray(emptyList()))
                        put("couponIds", JsonArray(emptyList()))
                        put("reviewCount", 0)
                        put("averageRating", 0.0)
                        put("ingredients", JsonArray(emptyList()))
                        put("description", "토너/스킨 카테고리")
                    })
                }.toString())
            }

            // Slice
            client.post("/api/v1/slice") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", leafKey)
                    put("version", 1L)
                }.toString())
            }

            // Query (v2)
            val queryResponse = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", leafKey)
                    put("version", 1L)
                }.toString())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            queryResponse.bodyAsText() shouldContain "토너/스킨"
        }
    }

    // ==================== Multiple Entity Types Together ====================

    "SDK E2E: Product with Brand & Category references" {
        testApplication {
            application { module() }

            val tenantId = "sdk-multi-tenant"

            // 1. Brand Ingest
            val brandKey = "BRAND#$tenantId#LANEIGE"
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", brandKey)
                    put("version", 1L)
                    put("schemaId", "brand.v1")
                    put("schemaVersion", "1.0.0")
                    put("payload", buildJsonObject {
                        put("brandId", "LANEIGE")
                        put("name", "라네즈")
                        put("country", "KR")
                    })
                }.toString())
            }

            // 2. Category Ingest
            val categoryKey = "CATEGORY#$tenantId#SKINCARE"
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", categoryKey)
                    put("version", 1L)
                    put("schemaId", "category.v1")
                    put("schemaVersion", "1.0.0")
                    put("payload", buildJsonObject {
                        put("categoryId", "SKINCARE")
                        put("name", "스킨케어")
                        put("depth", 0)
                    })
                }.toString())
            }

            // 3. Product Ingest (referencing Brand & Category)
            val productKey = "PRODUCT#$tenantId#PROD-CREAM-001"
            client.post("/api/v1/ingest") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("entityKey", productKey)
                    put("version", 1L)
                    put("schemaId", "product.v1")
                    put("schemaVersion", "1.0.0")
                    put("payload", buildJsonObject {
                        put("productId", "PROD-CREAM-001")
                        put("title", "워터뱅크 블루 히알루로닉 크림")
                        put("price", 42000)
                        put("brand", "LANEIGE")
                        put("categoryId", "SKINCARE")
                        put("stock", 100)
                        put("availability", "IN_STOCK")
                        put("images", JsonArray(emptyList()))
                        put("videos", JsonArray(emptyList()))
                        put("categoryPath", JsonArray(listOf(JsonPrimitive("SKINCARE"))))
                        put("tags", JsonArray(emptyList()))
                        put("promotionIds", JsonArray(emptyList()))
                        put("couponIds", JsonArray(emptyList()))
                        put("reviewCount", 2345)
                        put("averageRating", 4.7)
                        put("ingredients", JsonArray(emptyList()))
                        put("description", "깊은 수분 보습 크림")
                    })
                }.toString())
            }

            // 4. Slice all
            listOf(brandKey, categoryKey, productKey).forEach { key ->
                client.post("/api/v1/slice") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", tenantId)
                        put("entityKey", key)
                        put("version", 1L)
                    }.toString())
                }
            }

            // 5. Query Product (v2 - full view)
            val queryResponse = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", productKey)
                    put("version", 1L)
                }.toString())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            body shouldContain "워터뱅크 블루 히알루로닉 크림"
            body shouldContain "42000"
            body shouldContain "LANEIGE"
        }
    }

    // ==================== Batch Operations ====================

    "SDK E2E: Batch Ingest 3 Products" {
        testApplication {
            application { module() }

            val tenantId = "sdk-batch-tenant"

            // Batch ingest 3 products
            (1..3).forEach { idx ->
                val productKey = "PRODUCT#$tenantId#BATCH-${idx.toString().padStart(3, '0')}"
                client.post("/api/v1/ingest") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", tenantId)
                        put("entityKey", productKey)
                        put("version", 1L)
                        put("schemaId", "product.v1")
                        put("schemaVersion", "1.0.0")
                        put("payload", buildJsonObject {
                            put("productId", "BATCH-${idx.toString().padStart(3, '0')}")
                            put("title", "배치 상품 $idx")
                            put("price", 10000 + idx * 1000)
                            put("brand", "배치브랜드")
                            put("categoryId", "CAT-001")
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
                            put("description", "배치 테스트 $idx")
                        })
                    }.toString())
                }
            }

            // Batch slice
            (1..3).forEach { idx ->
                val productKey = "PRODUCT#$tenantId#BATCH-${idx.toString().padStart(3, '0')}"
                val sliceResponse = client.post("/api/v1/slice") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("tenantId", tenantId)
                        put("entityKey", productKey)
                        put("version", 1L)
                    }.toString())
                }
                sliceResponse.status shouldBe HttpStatusCode.OK
            }

            // Query first product (v2)
            val productKey = "PRODUCT#$tenantId#BATCH-001"
            val queryResponse = client.post("/api/v2/query") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantId", tenantId)
                    put("viewId", "view.product.pdp.v1")
                    put("entityKey", productKey)
                    put("version", 1L)
                }.toString())
            }
            queryResponse.status shouldBe HttpStatusCode.OK
            queryResponse.bodyAsText() shouldContain "배치 상품 1"
        }
    }
})
