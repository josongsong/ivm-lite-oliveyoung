package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEventStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.time.Instant
import java.util.UUID

/**
 * DynamoDB SinkEvent Repository
 *
 * 테이블 설계:
 * - PK: SINK_EVENT#<id>
 * - SK: VERSION#<timestamp>
 * - GSI1_PK: JOB#<jobId>
 * - GSI1_SK: CREATED#<timestamp>
 * - TTL: ttl (7일 후 자동 삭제)
 * - Streams: NEW_AND_OLD_IMAGES (Lambda 트리거용)
 */
class DynamoDbSinkEventRepository(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String,
) : SinkEventRepositoryPort {

    private val logger = LoggerFactory.getLogger(DynamoDbSinkEventRepository::class.java)

    override suspend fun put(event: SinkEvent): Result<SinkEvent> {
        return try {
            val item = toItem(event)

            // 멱등성: idempotencyKey 중복 시 무시
            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(idempotencyKey)")
                .build()

            try {
                dynamoClient.putItem(request).await()
                logger.debug("SinkEvent saved: ${event.id}")
                Result.Ok(event)
            } catch (e: ConditionalCheckFailedException) {
                // 중복 키 - 이미 존재
                logger.debug("SinkEvent already exists (idempotent): ${event.idempotencyKey}")
                Result.Ok(event)
            }
        } catch (e: Exception) {
            logger.error("Failed to put SinkEvent: ${event.id}", e)
            Result.Err(DomainError.StorageError("Failed to save SinkEvent: ${e.message}"))
        }
    }

    override suspend fun putAll(events: List<SinkEvent>): Result<List<SinkEvent>> {
        // Batch write (최대 25개씩)
        return try {
            events.chunked(25).forEach { chunk ->
                chunk.forEach { event ->
                    when (val result = put(event)) {
                        is Result.Err -> return result
                        is Result.Ok -> Unit
                    }
                }
            }
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to put batch SinkEvents", e)
            Result.Err(DomainError.StorageError("Failed to batch save: ${e.message}"))
        }
    }

    override suspend fun findById(id: UUID): Result<SinkEvent?> {
        return try {
            val request = GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf("PK" to AttributeValue.builder().s("SINK_EVENT#$id").build()))
                .build()

            val response = dynamoClient.getItem(request).await()
            val item = response.item()

            if (item.isEmpty()) {
                Result.Ok(null)
            } else {
                Result.Ok(fromItem(item))
            }
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvent: $id", e)
            Result.Err(DomainError.StorageError("Failed to find SinkEvent: ${e.message}"))
        }
    }

    override suspend fun findByJobId(jobId: String): Result<List<SinkEvent>> {
        return try {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI1")
                .keyConditionExpression("GSI1_PK = :jobId")
                .expressionAttributeValues(
                    mapOf(":jobId" to AttributeValue.builder().s("JOB#$jobId").build())
                )
                .build()

            val response = dynamoClient.query(request).await()
            val events = response.items().map { fromItem(it) }
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvents by jobId: $jobId", e)
            Result.Err(DomainError.StorageError("Failed to query by jobId: ${e.message}"))
        }
    }

    override suspend fun findByStatus(status: String, limit: Int): Result<List<SinkEvent>> {
        return try {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI2")
                .keyConditionExpression("GSI2_PK = :status")
                .expressionAttributeValues(
                    mapOf(":status" to AttributeValue.builder().s("STATUS#$status").build())
                )
                .limit(limit)
                .build()

            val response = dynamoClient.query(request).await()
            val events = response.items().map { fromItem(it) }
            Result.Ok(events)
        } catch (e: Exception) {
            logger.error("Failed to find SinkEvents by status: $status", e)
            Result.Err(DomainError.StorageError("Failed to query by status: ${e.message}"))
        }
    }

    // ==================== Helpers ====================

    private fun toItem(event: SinkEvent): Map<String, AttributeValue> {
        val item = mutableMapOf(
            "PK" to AttributeValue.builder().s("SINK_EVENT#${event.id}").build(),
            "SK" to AttributeValue.builder().s("VERSION#${event.createdAt.toEpochMilli()}").build(),
            "id" to AttributeValue.builder().s(event.id.toString()).build(),
            "idempotencyKey" to AttributeValue.builder().s(event.idempotencyKey).build(),
            "tenantId" to AttributeValue.builder().s(event.tenantId).build(),
            "entityKey" to AttributeValue.builder().s(event.entityKey).build(),
            "version" to AttributeValue.builder().n(event.version.toString()).build(),
            "viewType" to AttributeValue.builder().s(event.viewType).build(),
            "payload" to AttributeValue.builder().s(event.payload).build(),
            "sinkTargets" to AttributeValue.builder().ss(event.sinkTargets).build(),
            "status" to AttributeValue.builder().s(event.status.name).build(),
            "createdAt" to AttributeValue.builder().n(event.createdAt.toEpochMilli().toString()).build(),
            "ttl" to AttributeValue.builder().n(event.ttl.toString()).build(),
            "GSI2_PK" to AttributeValue.builder().s("STATUS#${event.status.name}").build(),
            "GSI2_SK" to AttributeValue.builder().s("CREATED#${event.createdAt.toEpochMilli()}").build(),
        )

        if (event.jobId != null) {
            item["jobId"] = AttributeValue.builder().s(event.jobId).build()
            item["GSI1_PK"] = AttributeValue.builder().s("JOB#${event.jobId}").build()
            item["GSI1_SK"] = AttributeValue.builder().s("CREATED#${event.createdAt.toEpochMilli()}").build()
        }

        if (event.processedAt != null) {
            item["processedAt"] = AttributeValue.builder().n(event.processedAt.toEpochMilli().toString()).build()
        }

        return item
    }

    private fun fromItem(item: Map<String, AttributeValue>): SinkEvent {
        return SinkEvent(
            id = UUID.fromString(item["id"]!!.s()),
            jobId = item["jobId"]?.s(),
            idempotencyKey = item["idempotencyKey"]!!.s(),
            tenantId = item["tenantId"]!!.s(),
            entityKey = item["entityKey"]!!.s(),
            version = item["version"]!!.n().toLong(),
            viewType = item["viewType"]!!.s(),
            payload = item["payload"]!!.s(),
            sinkTargets = item["sinkTargets"]!!.ss(),
            status = SinkEventStatus.valueOf(item["status"]!!.s()),
            createdAt = Instant.ofEpochMilli(item["createdAt"]!!.n().toLong()),
            processedAt = item["processedAt"]?.n()?.toLong()?.let { Instant.ofEpochMilli(it) },
            ttl = item["ttl"]!!.n().toLong(),
        )
    }
}
