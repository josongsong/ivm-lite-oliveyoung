package com.oliveyoung.ivmlite.e2e

import com.oliveyoung.ivmlite.integration.IntegrationTag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Sink → Lambda → OpenSearch E2E 테스트
 *
 * 실제 AWS 리소스를 사용한 End-to-End 시나리오:
 * 1. DynamoDB sink-events 테이블에 SinkEvent 벌크 삽입
 * 2. DynamoDB Streams → Lambda 트리거 대기
 * 3. SinkEvent 상태 COMPLETED 확인
 * 4. OpenSearch에서 검색하여 데이터 인덱싱 확인
 * 5. 벌크 수정 (v2) → OpenSearch 업데이트 확인
 * 6. 구버전 재전송 → version_type:external로 거부 확인
 *
 * 실행: SINK_E2E_ENABLED=true ./gradlew integrationTest --tests "*.SinkOpenSearchE2ETest"
 */
class SinkOpenSearchE2ETest : StringSpec(init@{
    tags(IntegrationTag)

    val enabled = System.getenv("SINK_E2E_ENABLED") == "true"
    if (!enabled) {
        println("SINK_E2E_ENABLED not set, skipping SinkOpenSearchE2ETest")
        return@init
    }

    val region = Region.of(System.getenv("AWS_REGION") ?: "ap-northeast-2")
    val sinkEventTable = System.getenv("SINK_EVENT_TABLE") ?: "ivm-sink-events-registry"
    val opensearchEndpoint = System.getenv("OPENSEARCH_ENDPOINT")
        ?: "https://search-ivm-opensearch-registry-3e2cnnyk3qos5kn4t5s226xgbq.ap-northeast-2.es.amazonaws.com"
    val opensearchUser = System.getenv("OPENSEARCH_USERNAME") ?: "admin"
    val opensearchPass = System.getenv("OPENSEARCH_PASSWORD") ?: "Whthdals123!@#"
    val opensearchIndex = "ivm-products-oliveyoung"

    val tenantId = "oliveyoung"
    val json = Json { ignoreUnknownKeys = true }

    val dynamoClient = DynamoDbAsyncClient.builder()
        .region(region)
        .build()

    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // ========== Fixture Data ==========

    val testRunId = UUID.randomUUID().toString().take(8)
    val testJobId = "e2e-sink-$testRunId"
    // testRunId를 productId에 포함 → 매 실행마다 새로운 문서 ID (version_type:external 충돌 방지)
    val testProductSuffixes = listOf("P001", "P002", "P003", "P004", "P005")
    val testProductIds = testProductSuffixes.map { "E2E-$testRunId-$it" }

    // ========== Helper Functions ==========

    fun authHeader(): String {
        val creds = java.util.Base64.getEncoder()
            .encodeToString("$opensearchUser:$opensearchPass".toByteArray())
        return "Basic $creds"
    }

    fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    fun buildJsonPayload(data: Map<String, Any>): String {
        val sb = StringBuilder("{")
        var first = true
        data.forEach { (key, value) ->
            if (!first) sb.append(",")
            first = false
            sb.append("\"$key\":")
            when (value) {
                is String -> sb.append("\"${escapeJson(value)}\"")
                is Int -> sb.append(value)
                is Long -> sb.append(value)
                is Double -> sb.append(value)
                is Boolean -> sb.append(value)
                is List<*> -> {
                    sb.append("[")
                    value.forEachIndexed { idx, item ->
                        if (idx > 0) sb.append(",")
                        when (item) {
                            is String -> sb.append("\"${escapeJson(item)}\"")
                            is Int -> sb.append(item)
                            else -> sb.append("\"$item\"")
                        }
                    }
                    sb.append("]")
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    sb.append(buildJsonPayload(value as Map<String, Any>))
                }
                else -> sb.append("\"$value\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    fun attr(value: String): AttributeValue =
        AttributeValue.builder().s(value).build()

    fun numAttr(value: String): AttributeValue =
        AttributeValue.builder().n(value).build()

    fun ssAttr(values: List<String>): AttributeValue =
        AttributeValue.builder().ss(values).build()

    suspend fun putSinkEvent(
        entityKey: String,
        version: Long,
        viewType: String,
        viewData: Map<String, Any>,
        sinkTargets: List<String> = listOf("opensearch-sink"),
        jobId: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val ttl = now.plusSeconds(7 * 24 * 3600).epochSecond
        val payload = buildJsonPayload(viewData)

        val item = mutableMapOf(
            "PK" to attr("SINK_EVENT#$id"),
            "SK" to attr("VERSION#${now.toEpochMilli()}"),
            "id" to attr(id),
            "idempotencyKey" to attr("e2e_${id}_${entityKey}_${version}"),
            "tenantId" to attr(tenantId),
            "entityKey" to attr(entityKey),
            "version" to numAttr(version.toString()),
            "viewType" to attr(viewType),
            "payload" to attr(payload),
            "sinkTargets" to ssAttr(sinkTargets),
            "status" to attr("PENDING"),
            "createdAt" to numAttr(now.toEpochMilli().toString()),
            "ttl" to numAttr(ttl.toString()),
            "GSI2_PK" to attr("STATUS#PENDING"),
            "GSI2_SK" to attr("CREATED#${now.toEpochMilli()}"),
        )

        if (jobId != null) {
            item["jobId"] = attr(jobId)
            item["GSI1_PK"] = attr("JOB#$jobId")
            item["GSI1_SK"] = attr("CREATED#${now.toEpochMilli()}")
        }

        dynamoClient.putItem {
            it.tableName(sinkEventTable).item(item)
        }.await()

        return id
    }

    suspend fun waitForSinkEventProcessed(
        sinkEventId: String,
        maxWaitMs: Long = 60_000,
        pollIntervalMs: Long = 3_000,
    ): String {
        val deadline = System.currentTimeMillis() + maxWaitMs

        while (System.currentTimeMillis() < deadline) {
            val response = dynamoClient.query(
                QueryRequest.builder()
                    .tableName(sinkEventTable)
                    .keyConditionExpression("PK = :pk")
                    .expressionAttributeValues(
                        mapOf(":pk" to attr("SINK_EVENT#$sinkEventId"))
                    )
                    .build()
            ).await()

            val item = response.items().firstOrNull()
            val status = item?.get("status")?.s() ?: "NOT_FOUND"

            if (status in listOf("COMPLETED", "FAILED")) {
                return status
            }

            delay(pollIntervalMs)
        }

        return "TIMEOUT"
    }

    fun searchOpenSearch(queryBody: String): JsonObject {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$opensearchEndpoint/$opensearchIndex/_search"))
            .header("Content-Type", "application/json")
            .header("Authorization", authHeader())
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(queryBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return json.parseToJsonElement(response.body()).jsonObject
    }

    fun refreshOpenSearch() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$opensearchEndpoint/$opensearchIndex/_refresh"))
            .header("Authorization", authHeader())
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun getOpenSearchDoc(docId: String, maxRetries: Int = 5): JsonObject? {
        val encodedDocId = URLEncoder.encode(docId, Charsets.UTF_8)
        repeat(maxRetries) { attempt ->
            refreshOpenSearch()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$opensearchEndpoint/$opensearchIndex/_doc/$encodedDocId"))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            println("  getDoc attempt=${attempt + 1}: status=${response.statusCode()}, body=${response.body().take(200)}")
            if (response.statusCode() == 200) {
                val body = json.parseToJsonElement(response.body()).jsonObject
                if (body["found"]?.jsonPrimitive?.content == "true") {
                    return body
                }
            }
            if (attempt < maxRetries - 1) {
                Thread.sleep(3_000)
            }
        }
        return null
    }

    fun deleteOpenSearchDoc(docId: String) {
        val encodedDocId = URLEncoder.encode(docId, Charsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$opensearchEndpoint/$opensearchIndex/_doc/$encodedDocId"))
            .header("Authorization", authHeader())
            .timeout(Duration.ofSeconds(5))
            .DELETE()
            .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun productFixture(productId: String, version: Long = 1L): Map<String, Any> {
        val suffix = productId.substringAfterLast("-") // P001, P002, ...
        return mapOf(
            "productId" to productId,
            "testRunId" to testRunId,
            "title" to "[올영픽] 테스트 상품 $productId v$version",
            "brand" to when (suffix) {
                "P001", "P002" -> "라운드랩"
                "P003", "P004" -> "토리든"
                else -> "닥터지"
            },
            "brandId" to when (suffix) {
                "P001", "P002" -> "BRAND#oliveyoung#roundlab"
                "P003", "P004" -> "BRAND#oliveyoung#torriden"
                else -> "BRAND#oliveyoung#drg"
            },
            "price" to if (version == 1L) 25000 else 22000,
            "salePrice" to if (version == 1L) 19900 else 17900,
            "discount" to if (version == 1L) 20 else 28,
            "stock" to 1500,
            "availability" to "IN_STOCK",
            "categoryId" to "CAT-SKINCARE-SUN",
            "categoryPath" to listOf("스킨케어", "선케어", "선크림"),
            "tags" to listOf("자외선차단", "수분", "민감피부"),
            "reviewCount" to 12847,
            "averageRating" to 4.8,
            "isNew" to (version >= 2L),
            "isBestSeller" to true,
            "description" to "E2E 테스트용 상품 $productId 버전 $version",
        )
    }

    // ========== Test Cleanup ==========

    afterSpec {
        if (!enabled) return@afterSpec
        testProductIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            deleteOpenSearchDoc(docId)
        }
        refreshOpenSearch()
        println("E2E 테스트 Cleanup 완료")
    }

    // ========== Test Scenarios ==========

    "E2E: 벌크 SinkEvent 삽입 → Lambda 처리 → OpenSearch 인덱싱 확인" {
        val sinkEventIds = mutableMapOf<String, String>()

        testProductIds.forEach { productId ->
            val entityKey = "PRODUCT#oliveyoung#$productId"
            val viewData = productFixture(productId, 1L)

            val eventId = putSinkEvent(
                entityKey = entityKey,
                version = 1L,
                viewType = "product-search",
                viewData = viewData,
                sinkTargets = listOf("opensearch-sink"),
                jobId = testJobId,
            )
            sinkEventIds[productId] = eventId
            println("SinkEvent 삽입: $productId → $eventId")
        }

        println("${sinkEventIds.size}개 SinkEvent 삽입 완료, Lambda 처리 대기...")

        val statuses = mutableMapOf<String, String>()
        sinkEventIds.forEach { (productId, eventId) ->
            val status = waitForSinkEventProcessed(eventId, maxWaitMs = 90_000)
            statuses[productId] = status
            println("SinkEvent 처리 결과: $productId → $status")
        }

        statuses.values.forEach { status ->
            status shouldBe "COMPLETED"
        }
        println("모든 SinkEvent COMPLETED 확인")

        refreshOpenSearch()
        delay(2_000)

        testProductIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc.shouldNotBeNull()
            val source = doc["_source"]!!.jsonObject
            source["productId"]!!.jsonPrimitive.content shouldBe productId
            source["testRunId"]!!.jsonPrimitive.content shouldBe testRunId
            val docVersion = doc["_version"]!!.jsonPrimitive.long
            docVersion shouldBe 1L
            println("OpenSearch 문서 확인: $productId, version=$docVersion")
        }

        val searchResult = searchOpenSearch("""
            {
                "query": { "term": { "testRunId.keyword": "$testRunId" } },
                "size": 10,
                "_source": ["productId", "brand"]
            }
        """.trimIndent())

        val totalHits = searchResult["hits"]!!.jsonObject["total"]!!.jsonObject["value"]!!.jsonPrimitive.int
        totalHits shouldBe testProductIds.size
        println("testRunId 기반 검색: ${totalHits}개")

        val brandSearchResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "match": { "brand": "라운드랩" } }
                        ]
                    }
                },
                "size": 10,
                "_source": ["productId", "brand"]
            }
        """.trimIndent())

        val brandHits = brandSearchResult["hits"]!!.jsonObject["hits"]!!.jsonArray
        val brandProductIds = brandHits.map {
            it.jsonObject["_source"]!!.jsonObject["productId"]!!.jsonPrimitive.content
        }
        brandProductIds.size shouldBe 2
        println("브랜드 '라운드랩' 검색: ${brandProductIds.joinToString()}")
    }

    "E2E: 벌크 수정 (v2) → OpenSearch 버전 업데이트 확인" {
        val sinkEventIds = mutableMapOf<String, String>()

        testProductIds.forEach { productId ->
            val entityKey = "PRODUCT#oliveyoung#$productId"
            val viewData = productFixture(productId, 2L)

            val eventId = putSinkEvent(
                entityKey = entityKey,
                version = 2L,
                viewType = "product-search",
                viewData = viewData,
                sinkTargets = listOf("opensearch-sink"),
                jobId = "$testJobId-v2",
            )
            sinkEventIds[productId] = eventId
        }

        println("v2 벌크 수정 ${sinkEventIds.size}개 삽입, Lambda 처리 대기...")

        val statuses = mutableMapOf<String, String>()
        sinkEventIds.forEach { (productId, eventId) ->
            val status = waitForSinkEventProcessed(eventId, maxWaitMs = 90_000)
            statuses[productId] = status
        }

        statuses.values.forEach { it shouldBe "COMPLETED" }
        println("v2 벌크 수정 모두 COMPLETED")

        refreshOpenSearch()
        delay(2_000)

        testProductIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc.shouldNotBeNull()
            val source = doc["_source"]!!.jsonObject
            val docVersion = doc["_version"]!!.jsonPrimitive.long

            docVersion shouldBe 2L
            source["price"]!!.jsonPrimitive.int shouldBe 22000
            source["salePrice"]!!.jsonPrimitive.int shouldBe 17900
            source["title"]!!.jsonPrimitive.content shouldContain "v2"

            println("v2 확인: $productId, version=$docVersion, price=${source["price"]!!.jsonPrimitive.int}")
        }

        val priceSearchResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "range": { "salePrice": { "lte": 18000 } } }
                        ]
                    }
                },
                "size": 10,
                "_source": ["productId", "salePrice"]
            }
        """.trimIndent())

        val priceHits = priceSearchResult["hits"]!!.jsonObject["hits"]!!.jsonArray
        priceHits.size shouldBe testProductIds.size
        println("가격 검색 (salePrice <= 18000): ${priceHits.size}개")
    }

    "E2E: 구버전 재전송 (v1) → OpenSearch version_type:external 거부 확인" {
        // 테스트 1,2에서 이미 v1→v2 순서로 인덱싱됨. v1 재전송 시 409 Conflict 기대
        val retryProductIds = testProductIds.take(2)
        val sinkEventIds = mutableMapOf<String, String>()

        retryProductIds.forEach { productId ->
            val entityKey = "PRODUCT#oliveyoung#$productId"
            val viewData = productFixture(productId, 1L) + ("retryTag" to "v1-retry-$testRunId")

            val eventId = putSinkEvent(
                entityKey = entityKey,
                version = 1L,
                viewType = "product-search",
                viewData = viewData,
                sinkTargets = listOf("opensearch-sink"),
                jobId = "$testJobId-v1-retry",
            )
            sinkEventIds[productId] = eventId
        }

        println("v1 재전송 ${sinkEventIds.size}개, Lambda 처리 대기...")

        val statuses = mutableMapOf<String, String>()
        sinkEventIds.forEach { (productId, eventId) ->
            val status = waitForSinkEventProcessed(eventId, maxWaitMs = 90_000)
            statuses[productId] = status
        }

        // OpenSearch 409 Conflict → ALREADY_PROCESSED → SinkEvent COMPLETED
        statuses.values.forEach { it shouldBe "COMPLETED" }
        println("v1 재전송 모두 COMPLETED (OpenSearch 409 → ALREADY_PROCESSED)")

        refreshOpenSearch()
        delay(1_000)

        // OpenSearch에서 여전히 v2인지 확인
        retryProductIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc.shouldNotBeNull()
            val docVersion = doc["_version"]!!.jsonPrimitive.long
            val source = doc["_source"]!!.jsonObject

            docVersion shouldBe 2L
            source["price"]!!.jsonPrimitive.int shouldBe 22000
            source["title"]!!.jsonPrimitive.content shouldContain "v2"

            println("v1 거부 확인: $productId, version=$docVersion (v2 유지)")
        }
    }

    "E2E: 키워드 검색 (한글) → OpenSearch 결과 확인" {
        refreshOpenSearch()
        delay(1_000)

        val keywordSearchResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "match": { "title": "테스트 상품" } }
                        ]
                    }
                },
                "size": 10,
                "_source": ["productId", "title"]
            }
        """.trimIndent())

        val keywordHits = keywordSearchResult["hits"]!!.jsonObject["hits"]!!.jsonArray
        keywordHits.size shouldBe testProductIds.size
        println("키워드 검색 '테스트 상품': ${keywordHits.size}개")

        val categorySearchResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "term": { "categoryId.keyword": "CAT-SKINCARE-SUN" } }
                        ]
                    }
                },
                "size": 10,
                "_source": ["productId", "categoryId"]
            }
        """.trimIndent())

        val categoryHits = categorySearchResult["hits"]!!.jsonObject["hits"]!!.jsonArray
        categoryHits.size shouldBe testProductIds.size
        println("카테고리 검색: ${categoryHits.size}개")

        val complexSearchResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "match": { "brand": "토리든" } },
                            { "range": { "salePrice": { "lte": 18000 } } }
                        ]
                    }
                },
                "size": 10,
                "_source": ["productId", "brand", "salePrice"]
            }
        """.trimIndent())

        val complexHits = complexSearchResult["hits"]!!.jsonObject["hits"]!!.jsonArray
        complexHits.size shouldBe 2
        println("복합 검색 (토리든 AND salePrice<=18000): ${complexHits.size}개")
    }

    "E2E: jobId 기반 SinkEvent 추적 확인" {
        val response = dynamoClient.query(
            QueryRequest.builder()
                .tableName(sinkEventTable)
                .indexName("GSI1")
                .keyConditionExpression("GSI1_PK = :jobPk")
                .expressionAttributeValues(
                    mapOf(":jobPk" to attr("JOB#$testJobId"))
                )
                .build()
        ).await()

        val items = response.items()
        items.size shouldBe testProductIds.size
        println("jobId '$testJobId' SinkEvent 수: ${items.size}")

        items.forEach { item ->
            val status = item["status"]?.s()
            status shouldBe "COMPLETED"
        }
        println("jobId 기반 전체 COMPLETED 확인")
    }
})
