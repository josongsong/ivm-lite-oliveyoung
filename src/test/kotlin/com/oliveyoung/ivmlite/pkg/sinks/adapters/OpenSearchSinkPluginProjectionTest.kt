package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import com.sun.net.httpserver.HttpServer

/**
 * OpenSearchSinkPlugin Static Projection 적용 검증 테스트
 *
 * useStaticProjection=true/false에 따라 bulk body에 올바른 문서 형식이 포함되는지 검증
 */
class OpenSearchSinkPluginProjectionTest {

    private var server: HttpServer? = null
    private var capturedBulkBody: String? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startCapturingServer(): Int {
        capturedBulkBody = null
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/_bulk") { exchange ->
                if (exchange.requestMethod == "POST") {
                    val body = exchange.requestBody.bufferedReader().readText()
                    capturedBulkBody = body
                    val response = """{"took":1,"errors":false,"items":[{"index":{"_index":"test","_id":"1","status":201}}]}"""
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                    exchange.responseBody.use { it.write(response.toByteArray()) }
                } else {
                    exchange.sendResponseHeaders(405, -1)
                }
                exchange.close()
            }
            start()
        }
        return server!!.address.port
    }

    private fun createPayload(viewData: kotlinx.serialization.json.JsonObject): SinkPayload.V1 {
        val digest = SinkPayload.computePayloadDigest(viewData)
        return SinkPayload.V1(
            correlationId = "test-correlation",
            timestamp = Instant.now().toString(),
            idempotencyKey = SinkPayload.generateIdempotencyKey("oliveyoung", "PRODUCT:oliveyoung:UA123", 1L, "product-search", digest),
            payloadDigest = digest,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA123",
            entityVersion = 1L,
            viewType = "product-search",
            viewData = viewData,
        )
    }

    @Test
    fun `useStaticProjection=true 시 bulk body에 Static 문서 필드 포함`() = runBlocking {
        val port = startCapturingServer()
        val viewData = buildJsonObject {
            put("testRunId", "proj-test-001")
            putJsonObject("CORE") {
                put("uaCode", "UA123")
                putJsonObject("masterInfo") {
                    put("gdsNm", "프로젝션 테스트 상품")
                    putJsonObject("brand") {
                        put("code", "TEST_BRAND")
                        put("krName", "테스트브랜드")
                    }
                }
                putJsonObject("onlineInfo") { put("prdtName", "프로젝션 테스트 상품 50ml") }
            }
            putJsonObject("INDEX") {
                putJsonObject("additionalInfo") { put("srchKeyWordText", "시카,보습") }
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "2"); put("attrValue", "수분크림 제형") })
                    add(buildJsonObject { put("attrCode", "6"); put("attrValue", "모든피부타입") })
                }
            }
            putJsonObject("MEDIA") {
                putJsonArray("thumbnailImages") {
                    add(buildJsonObject { put("url", "https://cdn.example.com/thumb.jpg") })
                }
            }
        }

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = true,
        )

        val payload = createPayload(viewData)
        val result = plugin.executeBatch(listOf(payload))

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "title_ko"
        capturedBulkBody shouldContain "brand_code"
        capturedBulkBody shouldContain "프로젝션 테스트 상품 50ml"
        capturedBulkBody shouldContain "TEST_BRAND"
        capturedBulkBody shouldContain "testRunId"
        capturedBulkBody shouldContain "proj-test-001"
        capturedBulkBody shouldContain "schemaVersion"
        capturedBulkBody shouldContain "\"v1\""
        capturedBulkBody shouldContain "attr_formulation"
        capturedBulkBody shouldContain "attr_skin_type"
        capturedBulkBody shouldContain "수분크림 제형"
        capturedBulkBody shouldContain "모든피부타입"
    }

    @Test
    fun `useStaticProjection=true 시 attr facet 필드 (main_functions, ingredients) 포함`() = runBlocking {
        val port = startCapturingServer()
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-FACET")
                putJsonObject("masterInfo") {
                    put("gdsNm", "페이셋 테스트")
                    putJsonObject("brand") { put("code", "F"); put("krName", "F") }
                }
            }
            putJsonObject("INDEX") {
                putJsonArray("attributes") {
                    add(buildJsonObject { put("attrCode", "42"); put("attrValue", "보습") })
                    add(buildJsonObject { put("attrCode", "81"); put("attrValue", "히알루론산") })
                }
            }
        }

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = true,
        )

        val payload = createPayload(viewData)
        val result = plugin.executeBatch(listOf(payload))

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "attr_main_functions"
        capturedBulkBody shouldContain "attr_ingredients"
        capturedBulkBody shouldContain "보습"
        capturedBulkBody shouldContain "히알루론산"
    }

    @Test
    fun `useStaticProjection=false 시 bulk body에 viewData 그대로 전송`() = runBlocking {
        val port = startCapturingServer()
        val viewData = buildJsonObject {
            put("productId", "UA456")
            put("title", "원본 뷰 상품")
            put("brand", "원본브랜드")
            put("price", 19900)
        }

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = false,
        )

        val payload = createPayload(viewData)
        val result = plugin.executeBatch(listOf(payload))

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "productId"
        capturedBulkBody shouldContain "UA456"
        capturedBulkBody shouldContain "title"
        capturedBulkBody shouldContain "원본 뷰 상품"
        capturedBulkBody shouldContain "price"
        capturedBulkBody shouldContain "19900"
        capturedBulkBody shouldNotContain "title_ko"
        capturedBulkBody shouldNotContain "brand_code"
        capturedBulkBody shouldNotContain "schemaVersion"
    }

    @Test
    fun `useStaticProjection=true 시 version_type external 포함`() = runBlocking {
        val port = startCapturingServer()
        val viewData = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA789")
                putJsonObject("masterInfo") { put("gdsNm", "버전 테스트") }
            }
        }

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = true,
        )

        val digest = SinkPayload.computePayloadDigest(viewData)
        val payload = SinkPayload.V1(
            correlationId = "ver-test",
            timestamp = Instant.now().toString(),
            idempotencyKey = SinkPayload.generateIdempotencyKey("oliveyoung", "PRODUCT:oliveyoung:UA789", 5L, "product-search", digest),
            payloadDigest = digest,
            tenantId = "oliveyoung",
            entityKey = "PRODUCT:oliveyoung:UA789",
            entityVersion = 5L,
            viewType = "product-search",
            viewData = viewData,
        )

        val result = plugin.executeBatch(listOf(payload))

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "\"version\":5"
        capturedBulkBody shouldContain "\"version_type\":\"external\""
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 엣지 케이스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    fun `빈 payloads 리스트 - 즉시 성공`() = runBlocking {
        val port = startCapturingServer()
        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = true,
        )

        val result = plugin.executeBatch(emptyList())

        result.isRight() shouldBe true
        capturedBulkBody shouldBe null
    }

    @Test
    fun `다중 payload 배치 - useStaticProjection`() = runBlocking {
        val port = startCapturingServer()
        val viewData1 = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-BATCH-1")
                putJsonObject("masterInfo") { put("gdsNm", "배치상품1") }
            }
        }
        val viewData2 = buildJsonObject {
            putJsonObject("CORE") {
                put("uaCode", "UA-BATCH-2")
                putJsonObject("masterInfo") { put("gdsNm", "배치상품2") }
            }
        }

        val digest1 = SinkPayload.computePayloadDigest(viewData1)
        val digest2 = SinkPayload.computePayloadDigest(viewData2)
        val payloads = listOf(
            SinkPayload.V1(
                correlationId = "batch-1",
                timestamp = Instant.now().toString(),
                idempotencyKey = SinkPayload.generateIdempotencyKey(
                    "oliveyoung", "PRODUCT:oliveyoung:UA-BATCH-1", 1L, "product-search", digest1
                ),
                payloadDigest = digest1,
                tenantId = "oliveyoung",
                entityKey = "PRODUCT:oliveyoung:UA-BATCH-1",
                entityVersion = 1L,
                viewType = "product-search",
                viewData = viewData1,
            ),
            SinkPayload.V1(
                correlationId = "batch-2",
                timestamp = Instant.now().toString(),
                idempotencyKey = SinkPayload.generateIdempotencyKey(
                    "oliveyoung", "PRODUCT:oliveyoung:UA-BATCH-2", 1L, "product-search", digest2
                ),
                payloadDigest = digest2,
                tenantId = "oliveyoung",
                entityKey = "PRODUCT:oliveyoung:UA-BATCH-2",
                entityVersion = 1L,
                viewType = "product-search",
                viewData = viewData2,
            ),
        )

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-{tenantId}__write",
            useStaticProjection = true,
        )

        val result = plugin.executeBatch(payloads)

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "배치상품1"
        capturedBulkBody shouldContain "배치상품2"
        capturedBulkBody shouldContain "UA-BATCH-1"
        capturedBulkBody shouldContain "UA-BATCH-2"
        capturedBulkBody shouldContain "oliveyoung__PRODUCT:oliveyoung:UA-BATCH-1"
        capturedBulkBody shouldContain "oliveyoung__PRODUCT:oliveyoung:UA-BATCH-2"
    }

    @Test
    fun `빈 viewData projection - 최소 Static 문서`() = runBlocking {
        val port = startCapturingServer()
        val viewData = buildJsonObject { }

        val plugin = OpenSearchSinkPlugin(
            endpoint = "http://localhost:$port",
            indexPattern = "ivm-products-oliveyoung__write",
            useStaticProjection = true,
        )

        val payload = createPayload(viewData)
        val result = plugin.executeBatch(listOf(payload))

        result.isRight() shouldBe true
        capturedBulkBody shouldContain "tenantId"
        capturedBulkBody shouldContain "entityKey"
        capturedBulkBody shouldContain "schemaVersion"
        capturedBulkBody shouldContain "title_ko"
        capturedBulkBody shouldContain "brand_code"
    }
}
