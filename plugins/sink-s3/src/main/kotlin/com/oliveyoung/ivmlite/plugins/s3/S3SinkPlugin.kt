package com.oliveyoung.ivmlite.plugins.s3

import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkJson
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * S3 Sink 플러그인
 *
 * RFC-017: Sink Plugin Architecture
 *
 * View 데이터를 S3에 JSON 파일로 저장
 * - Payload 저장 (디버깅/재현성)
 * - 키 설계: views/{viewType}/{entityKey}/v{entityVersion}.json
 */
class S3SinkPlugin(
    private val s3Client: S3Client,
    private val bucketName: String
) : SinkPlugin {

    override val pluginId = "s3-sink"

    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 10,
        supportsCompression = false,
        supportedCodecs = setOf("json"),
        supportsOtelPropagation = true,
        supportsIdempotency = true
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): arrow.core.Either<SinkError, BatchResult> {
        val succeeded = mutableListOf<SinkResult>()
        val retryableFailed = mutableListOf<BatchResult.FailedItem>()
        val nonRetryableFailed = mutableListOf<BatchResult.FailedItem>()

        payloads.forEach { payload ->
            when (payload) {
                is SinkPayload.V1 -> {
                    val result = uploadToS3(payload)
                    when (result) {
                        is arrow.core.Either.Left -> {
                            val error = result.value
                            val failedItem = BatchResult.FailedItem(
                                idempotencyKey = payload.idempotencyKey,
                                error = error
                            )
                            when (error) {
                                is SinkError.RetryableError -> retryableFailed.add(failedItem)
                                is SinkError.NonRetryableError -> nonRetryableFailed.add(failedItem)
                                is SinkError.PoisonPillError -> nonRetryableFailed.add(failedItem)
                            }
                        }
                        is arrow.core.Either.Right -> {
                            succeeded.add(result.value)
                        }
                    }
                }
            }
        }

        return arrow.core.Either.Right(
            BatchResult(
                succeeded = succeeded,
                retryableFailed = retryableFailed,
                nonRetryableFailed = nonRetryableFailed
            )
        )
    }

    private fun uploadToS3(payload: SinkPayload.V1): arrow.core.Either<SinkError, SinkResult> {
        return try {
            val key = buildKey(payload)
            val content = SinkJson.json.encodeToString<SinkPayload>(payload)

            logger.info {
                "Uploading to S3: bucket=$bucketName, key=$key, " +
                        "version=${payload.entityVersion}, size=${content.length}"
            }

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/json")
                    .metadata(
                        mapOf(
                            "contract-version" to payload.contractVersion,
                            "entity-version" to payload.entityVersion.toString(),
                            "view-type" to payload.viewType,
                            "idempotency-key" to payload.idempotencyKey,
                            "correlation-id" to payload.correlationId
                        )
                    )
                    .build(),
                RequestBody.fromString(content)
            )

            logger.info { "S3 upload completed: key=$key" }

            arrow.core.Either.Right(
                SinkResult(
                    idempotencyKey = payload.idempotencyKey,
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                    metadata = mapOf("s3Key" to key, "bucket" to bucketName)
                )
            )
        } catch (e: Exception) {
            arrow.core.Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "S3 upload failed: ${e.message}"
                )
            )
        }
    }

    /**
     * S3 키 생성 (충돌 방지)
     *
     * 형식: views/{viewType}/{entityKey}/v{entityVersion}.json
     */
    private fun buildKey(payload: SinkPayload.V1): String {
        return "views/${payload.viewType}/${payload.entityKey}/v${payload.entityVersion}.json"
    }
}
