package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.util.UUID

/**
 * SQS SinkEvent Repository
 *
 * SinkEvent를 SQS 큐로 전송. Lambda Batch Window(batchSize=500, batchWindow=60초)와 함께 사용 시
 * 메시지가 SQS에 모였다가 500건 또는 60초 경과 시 Lambda가 executeBatch로 벌크 처리.
 *
 * - putAll: sendMessageBatch (SQS 최대 10건/요청, 여러 번 호출)
 * - findById, findByJobId, findByStatus: SQS는 조회 미지원 → 빈 결과 반환
 *
 * 환경변수: SQS_SINK_QUEUE_URL
 */
class SqsSinkEventRepository(
    private val sqsClient: SqsAsyncClient,
    private val queueUrl: String,
) : SinkEventRepositoryPort {

    private val logger = LoggerFactory.getLogger(SqsSinkEventRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun put(event: SinkEvent): Result<SinkEvent> =
        putAll(listOf(event)).map { it.first() }

    override suspend fun putAll(events: List<SinkEvent>): Result<List<SinkEvent>> {
        if (events.isEmpty()) return Result.Ok(emptyList())

        return try {
            // SQS sendMessageBatch 최대 10건/요청
            events.chunked(10).forEach { chunk ->
                val entries = chunk.mapIndexed { index, event ->
                    SendMessageBatchRequestEntry.builder()
                        .id(index.toString())
                        .messageBody(toMessageBody(event))
                        .build()
                }
                val request = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build()
                sqsClient.sendMessageBatch(request).await()
            }
            logger.debug("SinkEvents sent to SQS: {} events, queue={}", events.size, queueUrl)
            Result.Ok(events)
        } catch (e: software.amazon.awssdk.core.exception.SdkException) {
            logger.error("Failed to send SinkEvents to SQS", e)
            Result.Err(DomainError.StorageError("Failed to send to SQS: ${e.message}"))
        }
    }

    override suspend fun findById(id: UUID): Result<SinkEvent?> =
        Result.Ok(null)

    override suspend fun findByJobId(jobId: String): Result<List<SinkEvent>> =
        Result.Ok(emptyList())

    override suspend fun findByStatus(status: String, limit: Int): Result<List<SinkEvent>> =
        Result.Ok(emptyList())

    private fun toMessageBody(event: SinkEvent): String {
        val dto = SinkEventMessageDto(
            id = event.id.toString(),
            jobId = event.jobId,
            idempotencyKey = event.idempotencyKey,
            tenantId = event.tenantId,
            entityKey = event.entityKey,
            version = event.version,
            viewType = event.viewType,
            payload = event.payload,
            sinkTargets = event.sinkTargets,
        )
        return json.encodeToString(dto)
    }

    @Serializable
    private data class SinkEventMessageDto(
        val id: String,
        val jobId: String?,
        val idempotencyKey: String,
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val viewType: String,
        val payload: String,
        val sinkTargets: List<String>,
    )
}
