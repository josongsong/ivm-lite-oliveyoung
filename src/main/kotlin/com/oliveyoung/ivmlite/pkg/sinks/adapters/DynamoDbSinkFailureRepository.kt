package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant

/**
 * DynamoDB Sink 실패 레코드 저장소 (RFC-020 R3)
 *
 * 테이블: ivm-sink-failures-{env}
 * PK: FAILURE#{sinkEventId}#{target}
 * SK: ATTEMPT#{timestamp}
 * TTL: 30일 후 자동 삭제
 */
class DynamoDbSinkFailureRepository(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String,
) : SinkFailureRepositoryPort {

    private val logger = LoggerFactory.getLogger(DynamoDbSinkFailureRepository::class.java)

    override suspend fun save(record: SinkFailureRecord): Either<SinkError, Unit> {
        val now = Instant.now()
        val ttl = now.plusSeconds(TTL_SECONDS).epochSecond

        val item = mapOf(
            "PK" to AttributeValue.builder().s("FAILURE#${record.sinkEventId}#${record.target}").build(),
            "SK" to AttributeValue.builder().s("ATTEMPT#${record.createdAt}").build(),
            "sinkEventId" to AttributeValue.builder().s(record.sinkEventId).build(),
            "target" to AttributeValue.builder().s(record.target).build(),
            "errorCategory" to AttributeValue.builder().s(record.errorCategory).build(),
            "errorReasonCode" to AttributeValue.builder().s(record.errorReasonCode).build(),
            "errorMessage" to AttributeValue.builder().s(record.errorMessage).build(),
            "payload" to AttributeValue.builder().s(record.payload).build(),
            "attemptCount" to AttributeValue.builder().n(record.attemptCount.toString()).build(),
            "createdAt" to AttributeValue.builder().s(record.createdAt).build(),
            "status" to AttributeValue.builder().s(record.status.name).build(),
            "ttl" to AttributeValue.builder().n(ttl.toString()).build(),
            "GSI1_PK" to AttributeValue.builder().s("TARGET#${record.target}").build(),
            "GSI1_SK" to AttributeValue.builder().s("CREATED#${record.createdAt}").build(),
        )

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        return try {
            dynamoClient.putItem(request).await()
            logger.info("Sink failure saved: sinkEventId={}, target={}", record.sinkEventId, record.target)
            Unit.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Failed to save sink failure record", e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Failed to save failure record: ${e.message}",
            ).left()
        }
    }

    override suspend fun findByTarget(target: String, limit: Int): Either<SinkError, List<SinkFailureRecord>> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName("GSI1")
            .keyConditionExpression("GSI1_PK = :target")
            .expressionAttributeValues(
                mapOf(":target" to AttributeValue.builder().s("TARGET#$target").build())
            )
            .scanIndexForward(false)
            .limit(limit)
            .build()

        return try {
            val response = dynamoClient.query(request).await()
            response.items().map { fromItem(it) }.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Failed to query sink failures by target: {}", target, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Failed to query failures: ${e.message}",
            ).left()
        }
    }

    override suspend fun updateStatus(
        sinkEventId: String,
        target: String,
        status: FailureStatus
    ): Either<SinkError, Unit> {
        val pk = "FAILURE#$sinkEventId#$target"

        val queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("PK = :pk")
            .expressionAttributeValues(
                mapOf(":pk" to AttributeValue.builder().s(pk).build())
            )
            .scanIndexForward(false)
            .limit(1)
            .build()

        return try {
            val queryResponse = dynamoClient.query(queryRequest).await()
            val latestItem = queryResponse.items().firstOrNull()
                ?: return SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
                    message = "Failure record not found: $pk",
                ).left()

            val sk = latestItem["SK"]?.s() ?: return SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
                message = "Failure record SK missing: $pk",
            ).left()

            val updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s(pk).build(),
                    "SK" to AttributeValue.builder().s(sk).build(),
                ))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(mapOf("#status" to "status"))
                .expressionAttributeValues(
                    mapOf(":status" to AttributeValue.builder().s(status.name).build())
                )
                .build()

            dynamoClient.updateItem(updateRequest).await()
            logger.info("Sink failure status updated: pk={}, status={}", pk, status)
            Unit.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Failed to update sink failure status: {}", pk, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Failed to update failure status: ${e.message}",
            ).left()
        }
    }

    private fun fromItem(item: Map<String, AttributeValue>) = SinkFailureRecord(
        sinkEventId = item["sinkEventId"]?.s() ?: "",
        target = item["target"]?.s() ?: "",
        errorCategory = item["errorCategory"]?.s() ?: "",
        errorReasonCode = item["errorReasonCode"]?.s() ?: "",
        errorMessage = item["errorMessage"]?.s() ?: "",
        payload = item["payload"]?.s() ?: "",
        attemptCount = item["attemptCount"]?.n()?.toIntOrNull() ?: 0,
        createdAt = item["createdAt"]?.s() ?: "",
        status = FailureStatus.valueOf(item["status"]?.s() ?: "FAILED"),
    )

    companion object {
        private const val TTL_SECONDS = 30L * 24 * 60 * 60
    }
}
