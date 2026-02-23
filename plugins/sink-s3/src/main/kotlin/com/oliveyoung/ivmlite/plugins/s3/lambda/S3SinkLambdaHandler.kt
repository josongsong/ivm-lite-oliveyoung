package com.oliveyoung.ivmlite.plugins.s3.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import com.oliveyoung.ivmlite.plugins.s3.S3SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkJson
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * S3 Sink Lambda Handler
 *
 * RFC-017: Sink Plugin Architecture
 *
 * SQS 이벤트를 수신하여 S3에 저장
 * - Batch Item Failure 지원 (부분 실패)
 */
class S3SinkLambdaHandler : RequestHandler<SQSEvent, SQSBatchResponse> {

    private val plugin by lazy {
        val bucketName = System.getenv("S3_BUCKET")
            ?: throw IllegalStateException("S3_BUCKET environment variable is required")

        S3SinkPlugin(
            s3Client = S3Client.builder().build(),
            bucketName = bucketName
        )
    }

    private val json = SinkJson.json

    override fun handleRequest(event: SQSEvent, context: Context): SQSBatchResponse {
        logger.info { "Received ${event.records.size} messages from SQS" }

        val failures = mutableListOf<SQSBatchResponse.BatchItemFailure>()

        event.records.forEach { record ->
            val success = processRecord(record)
            if (!success) {
                failures.add(SQSBatchResponse.BatchItemFailure(record.messageId))
            }
        }

        logger.info {
            "Processed ${event.records.size} messages: " +
                    "${event.records.size - failures.size} succeeded, ${failures.size} failed"
        }

        return SQSBatchResponse(failures)
    }

    private fun processRecord(record: SQSEvent.SQSMessage): Boolean {
        return runCatching {
            val payload = json.decodeFromString<SinkPayload>(record.body)

            runBlocking {
                val result = plugin.executeBatch(listOf(payload))
                result.fold(
                    { error: SinkError ->
                        logger.error {
                            "Plugin execution failed for message ${record.messageId}: ${error.message}"
                        }
                        // 에러 타입별 재시도 여부 결정
                        when (error) {
                            is SinkError.RetryableError -> false  // 재시도 필요 (SQS에 다시 나타남)
                            is SinkError.NonRetryableError -> true  // DLQ로 이동
                            is SinkError.PoisonPillError -> true  // DLQ로 이동
                        }
                    },
                    { batchResult ->
                        if (batchResult.hasFailures) {
                            logger.error {
                                "Batch has failures: retryable=${batchResult.retryableFailed.size}, " +
                                        "non-retryable=${batchResult.nonRetryableFailed.size}"
                            }
                            // 재시도 가능한 실패가 있으면 false 반환
                            batchResult.retryableFailed.isEmpty()
                        } else {
                            logger.info { "Plugin execution succeeded for message ${record.messageId}" }
                            true
                        }
                    }
                )
            }
        }.getOrElse { e: Throwable ->
            when (e) {
                is SerializationException -> {
                    logger.error(e) { "Failed to parse SQS message ${record.messageId}: ${e.message}" }
                    true  // 파싱 에러는 재시도 무의미 (DLQ로)
                }
                else -> {
                    logger.error(e) { "Unexpected error processing message ${record.messageId}: ${e.message}" }
                    false  // 예상치 못한 에러는 재시도
                }
            }
        }
    }
}
