package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkJson
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkTargetType
import com.oliveyoung.ivmlite.pkg.sinks.projection.ProductStaticProjection
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

private val logger = LoggerFactory.getLogger("OpenSearchSinkPlugin")

/**
 * OpenSearch Sink Plugin (opensearch-index-plan v2)
 *
 * View 데이터를 OpenSearch에 인덱싱.
 * - Bulk API 사용 (배치 효율성)
 * - 문서 ID: {tenantId}__{entityKey} (멱등성)
 * - useStaticProjection=true 시 PRODUCT_SEARCH View → Static 문서(flatten) 변환 후 인덱싱
 */
class OpenSearchSinkPlugin(
    private val endpoint: String,
    private val indexPattern: String,
    private val auth: AuthConfig? = null,
    private val timeoutMs: Long = 30_000,
    private val useStaticProjection: Boolean = false,
) : SinkPlugin {

    data class AuthConfig(
        val username: String,
        val password: String,
    )

    override val pluginId = SinkTargetType.OPENSEARCH.toPluginId()

    override val supportsDelete: Boolean = true

    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 100,
        supportsCompression = false,
        supportedCodecs = setOf("json"),
        supportsOtelPropagation = true,
        supportsIdempotency = true,
    )

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build()

    private val bulkResponseJson = SinkJson.json

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        if (payloads.isEmpty()) {
            return Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))
        }

        val bulkBody = buildBulkBody(payloads)
        val response = try {
            sendBulkRequest(bulkBody)
        } catch (e: java.net.http.HttpTimeoutException) {
            return Either.Left(SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "OpenSearch request timeout: ${e.message}",
            ))
        } catch (e: java.net.ConnectException) {
            return Either.Left(SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "OpenSearch connection failed: ${e.message}",
            ))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            return Either.Left(SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "OpenSearch request failed: ${e.message}",
            ))
        }

        return classifyBulkResponse(response, payloads)
    }

    private fun classifyBulkResponse(
        response: HttpResponse<String>,
        payloads: List<SinkPayload>,
    ): Either<SinkError, BatchResult> {
        val succeeded = mutableListOf<SinkResult>()
        val retryableFailed = mutableListOf<BatchResult.FailedItem>()
        val nonRetryableFailed = mutableListOf<BatchResult.FailedItem>()

        when {
            response.statusCode() in 200..299 -> {
                parseBulkResponse(response.body(), payloads, succeeded, retryableFailed, nonRetryableFailed)
            }
            response.statusCode() == 429 -> {
                addAllAsRetryable(payloads, retryableFailed, ErrorReasonCode.RATE_LIMIT_EXCEEDED, "rate limit exceeded")
            }
            response.statusCode() in 500..599 -> {
                addAllAsRetryable(payloads, retryableFailed, ErrorReasonCode.TEMPORARY_UNAVAILABLE, "server error: ${response.statusCode()}")
            }
            response.statusCode() in listOf(401, 403) -> {
                return Either.Left(SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.PERMISSION_DENIED,
                    message = "OpenSearch auth failed: ${response.statusCode()}",
                ))
            }
            else -> {
                payloads.forEach { payload ->
                    nonRetryableFailed.add(BatchResult.FailedItem(
                        idempotencyKey = payload.idempotencyKey,
                        error = SinkError.NonRetryableError(
                            reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                            message = "OpenSearch unexpected status: ${response.statusCode()}",
                        )
                    ))
                }
            }
        }

        return Either.Right(BatchResult(succeeded, retryableFailed, nonRetryableFailed))
    }

    private fun addAllAsRetryable(
        payloads: List<SinkPayload>,
        target: MutableList<BatchResult.FailedItem>,
        reasonCode: ErrorReasonCode,
        detail: String,
    ) {
        payloads.forEach { payload ->
            target.add(BatchResult.FailedItem(
                idempotencyKey = payload.idempotencyKey,
                error = SinkError.RetryableError(reasonCode = reasonCode, message = "OpenSearch $detail"),
            ))
        }
    }

    /**
     * DELETE 실행 (RFC-020 R1)
     *
     * OpenSearch에서 문서 삭제: DELETE /{index}/_doc/{docId}
     */
    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> {
        val index = resolveIndex(tenantId)
        val docId = "${tenantId}__${entityKey}"

        try {
            val url = "${endpoint.trimEnd('/')}/$index/_doc/$docId"
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .DELETE()

            auth?.let { config ->
                val credentials = java.util.Base64.getEncoder()
                    .encodeToString("${config.username}:${config.password}".toByteArray())
                requestBuilder.header("Authorization", "Basic $credentials")
            }

            logger.debug("Deleting from OpenSearch: index={}, docId={}", index, docId)

            val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

            return when (response.statusCode()) {
                200, 204 -> {
                    logger.info("OpenSearch delete succeeded: index={}, docId={}", index, docId)
                    Either.Right(
                        SinkResult(
                            idempotencyKey = "$tenantId:$entityKey:delete",
                            status = SinkStatus.SUCCESS,
                            processedAt = Instant.now().toString(),
                            metadata = mapOf("index" to index, "docId" to docId),
                        )
                    )
                }
                404 -> {
                    // 이미 삭제됨 → 멱등
                    logger.info("OpenSearch delete: doc already absent: index={}, docId={}", index, docId)
                    Either.Right(
                        SinkResult(
                            idempotencyKey = "$tenantId:$entityKey:delete",
                            status = SinkStatus.ALREADY_PROCESSED,
                            processedAt = Instant.now().toString(),
                            metadata = mapOf("index" to index, "docId" to docId),
                        )
                    )
                }
                429 -> Either.Left(
                    SinkError.RetryableError(
                        reasonCode = ErrorReasonCode.RATE_LIMIT_EXCEEDED,
                        message = "OpenSearch rate limit on delete",
                    )
                )
                in 500..599 -> Either.Left(
                    SinkError.RetryableError(
                        reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                        message = "OpenSearch server error on delete: ${response.statusCode()}",
                    )
                )
                else -> Either.Left(
                    SinkError.NonRetryableError(
                        reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                        message = "OpenSearch delete unexpected status: ${response.statusCode()}",
                    )
                )
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            return Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "OpenSearch delete timeout: ${e.message}",
                )
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            return Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "OpenSearch delete failed: ${e.message}",
                )
            )
        }
    }

    /**
     * OpenSearch Bulk API body 생성 (RFC-020 R2: 외부 버전화)
     *
     * version_type: external → incoming version > current version 일 때만 반영
     * useStaticProjection=true 시 View → Static 문서 변환 후 인덱싱
     */
    private fun buildBulkBody(payloads: List<SinkPayload>): String {
        val sb = StringBuilder()
        payloads.forEach { payload ->
            when (payload) {
                is SinkPayload.V1 -> {
                    val index = resolveIndex(payload.tenantId)
                    val docId = "${payload.tenantId}__${payload.entityKey}"
                    val docJson = if (useStaticProjection) {
                        val staticDoc = ProductStaticProjection.project(
                            viewData = payload.viewData,
                            tenantId = payload.tenantId,
                            entityKey = payload.entityKey,
                        )
                        SinkJson.json.encodeToString(staticDoc)
                    } else {
                        SinkJson.json.encodeToString(payload.viewData)
                    }

                    val action = """{"index":{"_index":"$index","_id":"$docId",""" +
                        """"version":${payload.entityVersion},"version_type":"external"}}"""
                    sb.append(action)
                    sb.append('\n')
                    sb.append(docJson)
                    sb.append('\n')
                }
            }
        }
        return sb.toString()
    }

    /**
     * Bulk 응답 개별 item 파싱 (RFC-020 R2)
     *
     * 각 item의 status를 확인하여 개별 성공/실패 분류:
     * - 200, 201: SUCCESS
     * - 409: ALREADY_PROCESSED (외부 버전화에 의한 구버전 자동 drop)
     * - 429: RetryableError (rate limit)
     * - 기타: NonRetryableError
     */
    private fun parseBulkResponse(
        responseBody: String,
        payloads: List<SinkPayload>,
        succeeded: MutableList<SinkResult>,
        retryableFailed: MutableList<BatchResult.FailedItem>,
        nonRetryableFailed: MutableList<BatchResult.FailedItem>,
    ) {
        val bulkJson: JsonObject
        try {
            bulkJson = bulkResponseJson.parseToJsonElement(responseBody).jsonObject
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Failed to parse bulk response: {}, body={}", e.message, responseBody.take(500))
            markAllAsSuccess(payloads, succeeded)
            return
        }

        val itemsArray = bulkJson["items"]?.jsonArray
        if (itemsArray == null) {
            markAllAsSuccess(payloads, succeeded)
            return
        }

        for (idx in itemsArray.indices) {
            val payload = payloads.getOrNull(idx) ?: continue
            val actionObj = itemsArray[idx].jsonObject
            val resultObj = (actionObj["index"] ?: actionObj["create"] ?: actionObj["update"] ?: actionObj["delete"])?.jsonObject
            val itemStatus = resultObj?.get("status")?.jsonPrimitive?.int ?: 200

            when (itemStatus) {
                200, 201 -> {
                    succeeded.add(
                        SinkResult(
                            idempotencyKey = payload.idempotencyKey,
                            status = SinkStatus.SUCCESS,
                            processedAt = Instant.now().toString(),
                        )
                    )
                }
                409 -> {
                    // 외부 버전화: 이미 더 높은 버전 존재 → 정상 동작
                    logger.debug("Version conflict (expected): idempotencyKey={}", payload.idempotencyKey)
                    succeeded.add(
                        SinkResult(
                            idempotencyKey = payload.idempotencyKey,
                            status = SinkStatus.ALREADY_PROCESSED,
                            processedAt = Instant.now().toString(),
                        )
                    )
                }
                429 -> {
                    retryableFailed.add(
                        BatchResult.FailedItem(
                            idempotencyKey = payload.idempotencyKey,
                            error = SinkError.RetryableError(
                                reasonCode = ErrorReasonCode.RATE_LIMIT_EXCEEDED,
                                message = "OpenSearch rate limit on item",
                            )
                        )
                    )
                }
                else -> {
                    val errorMsg = resultObj?.get("error")?.toString() ?: "status=$itemStatus"
                    nonRetryableFailed.add(
                        BatchResult.FailedItem(
                            idempotencyKey = payload.idempotencyKey,
                            error = SinkError.NonRetryableError(
                                reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                                message = "OpenSearch item error: $errorMsg",
                            )
                        )
                    )
                }
            }
        }
    }

    private fun markAllAsSuccess(payloads: List<SinkPayload>, succeeded: MutableList<SinkResult>) {
        payloads.forEach { payload ->
            succeeded.add(
                SinkResult(
                    idempotencyKey = payload.idempotencyKey,
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                )
            )
        }
    }

    /**
     * indexPattern에서 tenantId 치환
     */
    private fun resolveIndex(tenantId: String): String =
        indexPattern.replace("{tenantId}", tenantId)

    private fun sendBulkRequest(bulkBody: String): HttpResponse<String> {
        val url = "${endpoint.trimEnd('/')}/_bulk"

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-ndjson")
            .timeout(Duration.ofMillis(timeoutMs))
            .POST(HttpRequest.BodyPublishers.ofString(bulkBody))

        auth?.let { config ->
            val credentials = java.util.Base64.getEncoder()
                .encodeToString("${config.username}:${config.password}".toByteArray())
            requestBuilder.header("Authorization", "Basic $credentials")
        }

        logger.info("Sending bulk request to {}, body size={}, body preview={}", url, bulkBody.length,
            bulkBody.take(500))

        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

        logger.info("OpenSearch bulk response: status={}, body={}", response.statusCode(),
            response.body().take(1000))

        return response
    }
}
