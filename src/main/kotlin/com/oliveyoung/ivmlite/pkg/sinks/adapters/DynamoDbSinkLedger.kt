package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.LedgerEntry
import com.oliveyoung.ivmlite.sinks.contract.LedgerStatus
import com.oliveyoung.ivmlite.sinks.contract.ReplayFilters
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkLedger
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant

/**
 * DynamoDB SinkLedger (RFC-020 R4)
 *
 * Lambda stateless → DynamoDB로 인스턴스 간 멱등성 보장.
 * Conditional Write로 Optimistic Lock.
 *
 * 테이블: ivm-sink-ledger-{env}
 * PK: LEDGER#{pluginId}#{idempotencyKey}
 * TTL: 7일
 */
class DynamoDbSinkLedger(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String,
) : SinkLedger {

    private val logger = LoggerFactory.getLogger(DynamoDbSinkLedger::class.java)

    override suspend fun tryStart(
        pluginId: String,
        idempotencyKey: String,
        payloadDigest: String,
        contractVersion: String
    ): Either<SinkError, Boolean> {
        val pk = "LEDGER#$pluginId#$idempotencyKey"
        val now = Instant.now()
        val ttl = now.plusSeconds(TTL_SECONDS).epochSecond

        val item = mapOf(
            "PK" to AttributeValue.builder().s(pk).build(),
            "pluginId" to AttributeValue.builder().s(pluginId).build(),
            "idempotencyKey" to AttributeValue.builder().s(idempotencyKey).build(),
            "payloadDigest" to AttributeValue.builder().s(payloadDigest).build(),
            "contractVersion" to AttributeValue.builder().s(contractVersion).build(),
            "status" to AttributeValue.builder().s(LedgerStatus.PROCESSING.name).build(),
            "attemptCount" to AttributeValue.builder().n("1").build(),
            "createdAt" to AttributeValue.builder().s(now.toString()).build(),
            "ttl" to AttributeValue.builder().n(ttl.toString()).build(),
        )

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .conditionExpression("attribute_not_exists(PK) OR #status <> :completed")
            .expressionAttributeNames(mapOf("#status" to "status"))
            .expressionAttributeValues(
                mapOf(":completed" to AttributeValue.builder().s(LedgerStatus.COMPLETED.name).build())
            )
            .build()

        return try {
            dynamoClient.putItem(request).await()
            logger.debug("Ledger tryStart: pk={}, allowed", pk)
            true.right()
        } catch (@Suppress("SwallowedException") e: ConditionalCheckFailedException) {
            logger.debug("Ledger tryStart: pk={}, already completed", pk)
            false.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Ledger tryStart failed: pk={}", pk, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Ledger tryStart failed: ${e.message}",
            ).left()
        }
    }

    override suspend fun complete(
        pluginId: String,
        idempotencyKey: String,
        result: SinkResult
    ): Either<SinkError, Unit> {
        val pk = "LEDGER#$pluginId#$idempotencyKey"

        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(mapOf("PK" to AttributeValue.builder().s(pk).build()))
            .updateExpression("SET #status = :status, processedAt = :now, resultMetadata = :metadata")
            .expressionAttributeNames(mapOf("#status" to "status"))
            .expressionAttributeValues(mapOf(
                ":status" to AttributeValue.builder().s(LedgerStatus.COMPLETED.name).build(),
                ":now" to AttributeValue.builder().s(result.processedAt).build(),
                ":metadata" to AttributeValue.builder().s(result.metadata.toString()).build(),
            ))
            .build()

        return try {
            dynamoClient.updateItem(request).await()
            logger.debug("Ledger complete: pk={}", pk)
            Unit.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Ledger complete failed: pk={}", pk, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Ledger complete failed: ${e.message}",
            ).left()
        }
    }

    override suspend fun fail(
        pluginId: String,
        idempotencyKey: String,
        error: SinkError,
        attemptCount: Int
    ): Either<SinkError, Unit> {
        val pk = "LEDGER#$pluginId#$idempotencyKey"

        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(mapOf("PK" to AttributeValue.builder().s(pk).build()))
            .updateExpression("SET #status = :status, attemptCount = :count, lastError = :error, processedAt = :now")
            .expressionAttributeNames(mapOf("#status" to "status"))
            .expressionAttributeValues(mapOf(
                ":status" to AttributeValue.builder().s(LedgerStatus.FAILED.name).build(),
                ":count" to AttributeValue.builder().n(attemptCount.toString()).build(),
                ":error" to AttributeValue.builder().s(error.message).build(),
                ":now" to AttributeValue.builder().s(Instant.now().toString()).build(),
            ))
            .build()

        return try {
            dynamoClient.updateItem(request).await()
            logger.debug("Ledger fail: pk={}, attemptCount={}", pk, attemptCount)
            Unit.right()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Ledger fail failed: pk={}", pk, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Ledger fail failed: ${e.message}",
            ).left()
        }
    }

    override suspend fun getStatus(
        pluginId: String,
        idempotencyKey: String
    ): Either<SinkError, LedgerEntry?> {
        val pk = "LEDGER#$pluginId#$idempotencyKey"

        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(mapOf("PK" to AttributeValue.builder().s(pk).build()))
            .build()

        return try {
            val response = dynamoClient.getItem(request).await()
            val item = response.item()
            if (item.isNullOrEmpty()) {
                null.right()
            } else {
                fromItem(item).right()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Ledger getStatus failed: pk={}", pk, e)
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                message = "Ledger getStatus failed: ${e.message}",
            ).left()
        }
    }

    override suspend fun queryForReplay(
        pluginId: String,
        filters: ReplayFilters,
        limit: Int
    ): Either<SinkError, List<LedgerEntry>> =
        emptyList<LedgerEntry>().right()

    private fun fromItem(item: Map<String, AttributeValue>) = LedgerEntry(
        pluginId = item["pluginId"]?.s() ?: "",
        idempotencyKey = item["idempotencyKey"]?.s() ?: "",
        payloadDigest = item["payloadDigest"]?.s() ?: "",
        contractVersion = item["contractVersion"]?.s() ?: "",
        status = LedgerStatus.valueOf(item["status"]?.s() ?: "PROCESSING"),
        attemptCount = item["attemptCount"]?.n()?.toIntOrNull() ?: 0,
        createdAt = item["createdAt"]?.s() ?: "",
        processedAt = item["processedAt"]?.s(),
        lastError = null,
        resultMetadata = emptyMap(),
    )

    companion object {
        private const val TTL_SECONDS = 7L * 24 * 60 * 60
    }
}
