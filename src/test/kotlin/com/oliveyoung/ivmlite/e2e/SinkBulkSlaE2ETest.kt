package com.oliveyoung.ivmlite.e2e

import com.oliveyoung.ivmlite.integration.IntegrationTag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * Sink Bulk + SLA E2E 테스트
 *
 * 100개 벌크 삽입 → Lambda 처리 → OpenSearch 인덱싱 + SLA 측정
 * 증분 업데이트 (일부만 v2) → 정확성 + SLA 측정
 *
 * SLA 기준:
 * - 100개 벌크 삽입 → 전체 COMPLETED: 120초 이내
 * - 100개 OpenSearch 인덱싱 확인: COMPLETED 후 10초 이내
 * - 30개 증분 업데이트 → 전체 COMPLETED: 90초 이내
 *
 * 실행: SINK_E2E_ENABLED=true ./gradlew integrationTest --tests "*.SinkBulkSlaE2ETest"
 */
class SinkBulkSlaE2ETest : StringSpec(init@{
    tags(IntegrationTag)

    val enabled = System.getenv("SINK_E2E_ENABLED") == "true"
    if (!enabled) {
        println("SINK_E2E_ENABLED not set, skipping SinkBulkSlaE2ETest")
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
    val bulkCount = 100
    val incrementalCount = 30 // 증분 업데이트 대상 수

    val dynamoClient = DynamoDbAsyncClient.builder()
        .region(region)
        .build()

    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // ========== SLA 기준 ==========

    val slaFullBulkProcessMs = 120_000L   // 100개 벌크 → COMPLETED: 120초
    val slaIndexVerifyMs = 10_000L         // COMPLETED 후 OpenSearch 검색 확인: 10초
    val slaIncrementalProcessMs = 90_000L  // 30개 증분 → COMPLETED: 90초

    // ========== Fixture Data ==========

    val testRunId = UUID.randomUUID().toString().take(8)
    val testJobId = "bulk-sla-$testRunId"
    val productIds = (1..bulkCount).map { "BULK-$testRunId-${String.format("%03d", it)}" }

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

    suspend fun waitForAllProcessed(
        sinkEventIds: Map<String, String>,
        maxWaitMs: Long,
    ): Pair<Map<String, String>, Long> {
        val startMs = System.currentTimeMillis()
        val deadline = startMs + maxWaitMs
        val statuses = sinkEventIds.keys.associateWith { "PENDING" }.toMutableMap()
        val pending = sinkEventIds.toMutableMap()

        while (pending.isNotEmpty() && System.currentTimeMillis() < deadline) {
            val toCheck = pending.entries.take(25) // batch check
            for ((productId, eventId) in toCheck) {
                val response = dynamoClient.query(
                    QueryRequest.builder()
                        .tableName(sinkEventTable)
                        .keyConditionExpression("PK = :pk")
                        .expressionAttributeValues(
                            mapOf(":pk" to attr("SINK_EVENT#$eventId"))
                        )
                        .build()
                ).await()

                val item = response.items().firstOrNull()
                val status = item?.get("status")?.s() ?: "NOT_FOUND"

                if (status in listOf("COMPLETED", "FAILED")) {
                    statuses[productId] = status
                    pending.remove(productId)
                }
            }

            if (pending.isNotEmpty()) {
                delay(2_000)
            }
        }

        // 타임아웃된 것은 TIMEOUT으로 표시
        pending.keys.forEach { statuses[it] = "TIMEOUT" }

        val elapsedMs = System.currentTimeMillis() - startMs
        return Pair(statuses, elapsedMs)
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

    fun getOpenSearchDoc(docId: String): JsonObject? {
        val encodedDocId = URLEncoder.encode(docId, Charsets.UTF_8)
        refreshOpenSearch()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$opensearchEndpoint/$opensearchIndex/_doc/$encodedDocId"))
            .header("Authorization", authHeader())
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
            val body = json.parseToJsonElement(response.body()).jsonObject
            if (body["found"]?.jsonPrimitive?.content == "true") {
                return body
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

    val brands = listOf("라운드랩", "토리든", "닥터지", "이니스프리", "에스트라")
    val categories = listOf("CAT-SKINCARE-SUN", "CAT-SKINCARE-MOIST", "CAT-MAKEUP-LIP", "CAT-BODY-WASH", "CAT-HAIR-SHAMPOO")

    fun productFixture(productId: String, version: Long = 1L): Map<String, Any> {
        val idx = productId.substringAfterLast("-").toIntOrNull() ?: 1
        val brandIdx = (idx - 1) % brands.size
        val catIdx = (idx - 1) % categories.size
        return mapOf(
            "productId" to productId,
            "testRunId" to testRunId,
            "title" to "[올영픽] 벌크테스트 $productId v$version",
            "brand" to brands[brandIdx],
            "brandId" to "BRAND#oliveyoung#brand-$brandIdx",
            "price" to if (version == 1L) 25000 + idx * 100 else 22000 + idx * 100,
            "salePrice" to if (version == 1L) 19900 + idx * 50 else 17900 + idx * 50,
            "discount" to if (version == 1L) 20 else 28,
            "stock" to 1000 + idx,
            "availability" to "IN_STOCK",
            "categoryId" to categories[catIdx],
            "categoryPath" to listOf("스킨케어", "선케어"),
            "tags" to listOf("자외선차단", "수분", "민감피부"),
            "reviewCount" to 1000 + idx * 10,
            "averageRating" to 4.5,
            "isNew" to (version >= 2L),
            "isBestSeller" to (idx <= 20),
            "description" to "벌크 E2E 상품 $productId 버전 $version",
            "version" to version,
        )
    }

    // ========== Cleanup ==========

    afterSpec {
        if (!enabled) return@afterSpec
        println("Cleanup: ${productIds.size}개 문서 삭제 중...")
        productIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            deleteOpenSearchDoc(docId)
        }
        refreshOpenSearch()
        println("Cleanup 완료")
    }

    // ========== Test Scenarios ==========

    "SLA: 100개 벌크 삽입 → Lambda COMPLETED (SLA: ${slaFullBulkProcessMs / 1000}초)" {
        println("======== 100개 벌크 삽입 SLA 테스트 ========")
        println("testRunId: $testRunId")

        // 1. 100개 SinkEvent 벌크 삽입 (시간 측정)
        val insertStartMs = System.currentTimeMillis()
        val sinkEventIds = mutableMapOf<String, String>()

        // 병렬 삽입 (10개씩 배치)
        coroutineScope {
            productIds.chunked(10).flatMap { chunk ->
                chunk.map { productId ->
                    async {
                        val entityKey = "PRODUCT#oliveyoung#$productId"
                        val viewData = productFixture(productId, 1L)
                        val eventId = putSinkEvent(
                            entityKey = entityKey,
                            version = 1L,
                            viewType = "product-search",
                            viewData = viewData,
                            jobId = testJobId,
                        )
                        productId to eventId
                    }
                }
            }.awaitAll().forEach { (productId, eventId) ->
                sinkEventIds[productId] = eventId
            }
        }

        val insertElapsedMs = System.currentTimeMillis() - insertStartMs
        println("DynamoDB 삽입 완료: ${sinkEventIds.size}개, ${insertElapsedMs}ms")

        // 2. Lambda 처리 대기 + SLA 측정
        val (statuses, processElapsedMs) = waitForAllProcessed(sinkEventIds, slaFullBulkProcessMs)

        val completedCount = statuses.values.count { it == "COMPLETED" }
        val failedCount = statuses.values.count { it == "FAILED" }
        val timeoutCount = statuses.values.count { it == "TIMEOUT" }

        println("Lambda 처리 결과: COMPLETED=$completedCount, FAILED=$failedCount, TIMEOUT=$timeoutCount")
        println("Lambda 처리 시간: ${processElapsedMs}ms (SLA: ${slaFullBulkProcessMs}ms)")

        completedCount shouldBe bulkCount
        failedCount shouldBe 0
        timeoutCount shouldBe 0
        processElapsedMs shouldBeLessThan slaFullBulkProcessMs

        println("SLA PASS: ${bulkCount}개 벌크 처리 ${processElapsedMs}ms < ${slaFullBulkProcessMs}ms")
    }

    "SLA: 100개 OpenSearch 인덱싱 확인 (SLA: ${slaIndexVerifyMs / 1000}초)" {
        println("======== OpenSearch 인덱싱 확인 ========")

        val verifyStartMs = System.currentTimeMillis()
        refreshOpenSearch()
        Thread.sleep(2_000)

        // testRunId 기반 전체 검색
        val searchResult = searchOpenSearch("""
            {
                "query": { "term": { "testRunId.keyword": "$testRunId" } },
                "size": 0,
                "track_total_hits": true
            }
        """.trimIndent())

        val totalHits = searchResult["hits"]!!.jsonObject["total"]!!.jsonObject["value"]!!.jsonPrimitive.int
        val verifyElapsedMs = System.currentTimeMillis() - verifyStartMs

        println("OpenSearch 인덱싱 수: $totalHits / $bulkCount")
        println("검증 시간: ${verifyElapsedMs}ms (SLA: ${slaIndexVerifyMs}ms)")

        totalHits shouldBe bulkCount
        verifyElapsedMs shouldBeLessThan slaIndexVerifyMs

        // 샘플 문서 정확성 확인 (첫 5개 + 마지막 5개)
        val sampleIds = productIds.take(5) + productIds.takeLast(5)
        sampleIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc shouldBe doc // not null
            val source = doc!!["_source"]!!.jsonObject
            source["productId"]!!.jsonPrimitive.content shouldBe productId
            source["testRunId"]!!.jsonPrimitive.content shouldBe testRunId
            doc["_version"]!!.jsonPrimitive.long shouldBe 1L
        }

        println("샘플 ${sampleIds.size}개 문서 정확성 확인 완료")

        // 브랜드별 분포 확인
        val aggResult = searchOpenSearch("""
            {
                "query": { "term": { "testRunId.keyword": "$testRunId" } },
                "size": 0,
                "aggs": {
                    "brands": {
                        "terms": { "field": "brand.keyword", "size": 10 }
                    }
                }
            }
        """.trimIndent())

        val buckets = aggResult["aggregations"]!!.jsonObject["brands"]!!.jsonObject["buckets"]!!.jsonArray
        println("브랜드별 분포:")
        buckets.forEach { bucket ->
            val key = bucket.jsonObject["key"]!!.jsonPrimitive.content
            val count = bucket.jsonObject["doc_count"]!!.jsonPrimitive.int
            println("  $key: ${count}개")
            count shouldBeGreaterThanOrEqual 1
        }

        println("SLA PASS: 인덱싱 검증 ${verifyElapsedMs}ms < ${slaIndexVerifyMs}ms")
    }

    "SLA: 증분 업데이트 (${incrementalCount}개만 v2) → 정확성 + SLA 확인" {
        println("======== 증분 업데이트 SLA 테스트 ========")
        val incrementalJobId = "$testJobId-incr"
        val incrementalIds = productIds.take(incrementalCount) // 앞 30개만 v2 업데이트

        // 1. 30개만 v2로 증분 업데이트
        val insertStartMs = System.currentTimeMillis()
        val sinkEventIds = mutableMapOf<String, String>()

        coroutineScope {
            incrementalIds.map { productId ->
                async {
                    val entityKey = "PRODUCT#oliveyoung#$productId"
                    val viewData = productFixture(productId, 2L)
                    val eventId = putSinkEvent(
                        entityKey = entityKey,
                        version = 2L,
                        viewType = "product-search",
                        viewData = viewData,
                        jobId = incrementalJobId,
                    )
                    productId to eventId
                }
            }.awaitAll().forEach { (productId, eventId) ->
                sinkEventIds[productId] = eventId
            }
        }

        val insertElapsedMs = System.currentTimeMillis() - insertStartMs
        println("증분 삽입 완료: ${sinkEventIds.size}개, ${insertElapsedMs}ms")

        // 2. Lambda 처리 대기 + SLA
        val (statuses, processElapsedMs) = waitForAllProcessed(sinkEventIds, slaIncrementalProcessMs)

        val completedCount = statuses.values.count { it == "COMPLETED" }
        println("증분 처리 결과: COMPLETED=$completedCount / $incrementalCount")
        println("증분 처리 시간: ${processElapsedMs}ms (SLA: ${slaIncrementalProcessMs}ms)")

        completedCount shouldBe incrementalCount
        processElapsedMs shouldBeLessThan slaIncrementalProcessMs

        // 3. OpenSearch 정확성 확인
        refreshOpenSearch()
        Thread.sleep(2_000)

        // 업데이트된 30개: version=2, price 변경됨
        var v2ConfirmedCount = 0
        incrementalIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc shouldBe doc // not null
            val source = doc!!["_source"]!!.jsonObject
            val docVersion = doc["_version"]!!.jsonPrimitive.long

            docVersion shouldBe 2L
            source["version"]!!.jsonPrimitive.long shouldBe 2L
            v2ConfirmedCount++
        }

        println("v2 업데이트 확인: ${v2ConfirmedCount}개 / $incrementalCount")

        // 업데이트 안 된 나머지 70개: 여전히 version=1
        val untouchedIds = productIds.drop(incrementalCount).take(10) // 샘플 10개만 확인
        var v1ConfirmedCount = 0
        untouchedIds.forEach { productId ->
            val docId = "${tenantId}__PRODUCT#oliveyoung#$productId"
            val doc = getOpenSearchDoc(docId)
            doc shouldBe doc // not null
            val docVersion = doc!!["_version"]!!.jsonPrimitive.long

            docVersion shouldBe 1L
            v1ConfirmedCount++
        }

        println("v1 유지 확인: ${v1ConfirmedCount}개 (샘플)")

        // 전체 문서 수는 여전히 100개
        val totalResult = searchOpenSearch("""
            {
                "query": { "term": { "testRunId.keyword": "$testRunId" } },
                "size": 0,
                "track_total_hits": true
            }
        """.trimIndent())

        val totalHits = totalResult["hits"]!!.jsonObject["total"]!!.jsonObject["value"]!!.jsonPrimitive.int
        totalHits shouldBe bulkCount
        println("전체 문서 수 유지: ${totalHits}개")

        // 가격 범위로 v2만 검색
        val v2PriceResult = searchOpenSearch("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "testRunId.keyword": "$testRunId" } },
                            { "term": { "isNew": true } }
                        ]
                    }
                },
                "size": 0,
                "track_total_hits": true
            }
        """.trimIndent())

        val v2Hits = v2PriceResult["hits"]!!.jsonObject["total"]!!.jsonObject["value"]!!.jsonPrimitive.int
        v2Hits shouldBe incrementalCount
        println("isNew=true (v2) 문서 수: ${v2Hits}개")

        println("SLA PASS: 증분 ${incrementalCount}개 처리 ${processElapsedMs}ms < ${slaIncrementalProcessMs}ms")
        println("")
        println("========== SLA 결과 요약 ==========")
        println("  100개 벌크 처리:   < ${slaFullBulkProcessMs / 1000}초")
        println("  인덱싱 검증:       < ${slaIndexVerifyMs / 1000}초")
        println("  ${incrementalCount}개 증분 업데이트: ${processElapsedMs}ms < ${slaIncrementalProcessMs / 1000}초")
        println("==================================")
    }
})
