package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkJson
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.Instant

private val logger = LoggerFactory.getLogger("S3SinkPlugin")

/**
 * S3 Sink Plugin
 *
 * View 데이터를 S3에 JSON 파일로 저장.
 * - 키 설계: views/{viewType}/{entityKey}/v{entityVersion}.json
 * - 멱등성: 동일 키에 덮어쓰기 (S3 PutObject 자체가 멱등)
 */
class S3SinkPlugin(
    private val s3Client: S3Client,
    private val bucketName: String,
) : SinkPlugin {

    override val pluginId = "s3-sink"

    override val supportsDelete: Boolean = true

    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 10,
        supportsCompression = false,
        supportedCodecs = setOf("json"),
        supportsOtelPropagation = true,
        supportsIdempotency = true,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val succeeded = mutableListOf<SinkResult>()
        val retryableFailed = mutableListOf<BatchResult.FailedItem>()
        val nonRetryableFailed = mutableListOf<BatchResult.FailedItem>()

        payloads.forEach { payload ->
            when (payload) {
                is SinkPayload.V1 -> {
                    val result = uploadToS3(payload)
                    result.fold(
                        { error ->
                            val failedItem = BatchResult.FailedItem(payload.idempotencyKey, error)
                            when (error) {
                                is SinkError.RetryableError -> retryableFailed.add(failedItem)
                                is SinkError.NonRetryableError -> nonRetryableFailed.add(failedItem)
                                is SinkError.PoisonPillError -> nonRetryableFailed.add(failedItem)
                            }
                        },
                        { sinkResult -> succeeded.add(sinkResult) }
                    )
                }
            }
        }

        return Either.Right(BatchResult(succeeded, retryableFailed, nonRetryableFailed))
    }

    /**
     * DELETE 실행 (RFC-020 R1)
     *
     * S3에서 해당 엔티티의 모든 버전 파일 삭제.
     * prefix: views/{viewType}/{entityKey}/
     */
    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> {
        val viewType = metadata["viewType"] ?: return Either.Left(
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.REQUIRED_FIELD_MISSING,
                message = "viewType required for S3 delete",
            )
        )

        val prefix = "views/$viewType/$entityKey/"

        return try {
            // prefix 하위 모든 오브젝트 삭제
            val listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()

            val listResponse = s3Client.listObjectsV2(listRequest)
            var deletedCount = 0

            listResponse.contents().forEach { obj ->
                s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(obj.key())
                        .build()
                )
                deletedCount++
            }

            logger.info("S3 delete completed: prefix={}, deletedCount={}", prefix, deletedCount)

            Either.Right(
                SinkResult(
                    idempotencyKey = "$tenantId:$entityKey:delete",
                    status = if (deletedCount > 0) SinkStatus.SUCCESS else SinkStatus.ALREADY_PROCESSED,
                    processedAt = Instant.now().toString(),
                    metadata = mapOf("prefix" to prefix, "deletedCount" to deletedCount.toString()),
                )
            )
        } catch (e: Exception) {
            Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "S3 delete failed: ${e.message}",
                )
            )
        }
    }

    private fun uploadToS3(payload: SinkPayload.V1): Either<SinkError, SinkResult> {
        return try {
            val key = "views/${payload.viewType}/${payload.entityKey}/v${payload.entityVersion}.json"
            val content = SinkJson.json.encodeToString<SinkPayload>(payload)

            logger.debug("Uploading to S3: bucket={}, key={}, size={}", bucketName, key, content.length)

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
                        )
                    )
                    .build(),
                RequestBody.fromString(content)
            )

            logger.info("S3 upload completed: key={}", key)

            Either.Right(
                SinkResult(
                    idempotencyKey = payload.idempotencyKey,
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                    metadata = mapOf("s3Key" to key, "bucket" to bucketName),
                )
            )
        } catch (e: Exception) {
            Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "S3 upload failed: ${e.message}",
                )
            )
        }
    }
}
