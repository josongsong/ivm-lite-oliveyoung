package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkTargetType
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.personalizeevents.PersonalizeEventsClient
import software.amazon.awssdk.services.personalizeevents.model.Item
import software.amazon.awssdk.services.personalizeevents.model.PutItemsRequest
import java.time.Instant

private val logger = LoggerFactory.getLogger("PersonalizeSinkPlugin")

/**
 * AWS Personalize Sink Plugin
 *
 * View 데이터를 Personalize Items Dataset에 전송.
 * - PutItems API 사용 (배치)
 * - Item ID: entityKey
 * - Properties: viewData JSON
 */
class PersonalizeSinkPlugin(
    private val personalizeClient: PersonalizeEventsClient,
    private val datasetArn: String,
) : SinkPlugin {

    override val pluginId = SinkTargetType.PERSONALIZE.toPluginId()

    override val supportsDelete: Boolean = true

    override val capabilities = PluginCapabilities(
        supportedContractVersions = setOf("1.0"),
        supportsBatch = true,
        maxBatchSize = 10, // Personalize PutItems 최대 10개
        supportsCompression = false,
        supportedCodecs = setOf("json"),
        supportsOtelPropagation = false,
        supportsIdempotency = true,
    )

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        if (payloads.isEmpty()) {
            return Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))
        }

        val succeeded = mutableListOf<SinkResult>()
        val retryableFailed = mutableListOf<BatchResult.FailedItem>()

        try {
            val items = payloads.map { payload ->
                when (payload) {
                    is SinkPayload.V1 -> {
                        Item.builder()
                            .itemId(payload.entityKey)
                            .properties(payload.viewData.toString())
                            .build()
                    }
                }
            }

            val request = PutItemsRequest.builder()
                .datasetArn(datasetArn)
                .items(items)
                .build()

            logger.debug("PutItems to Personalize: datasetArn={}, count={}", datasetArn, items.size)

            personalizeClient.putItems(request)

            payloads.forEach { payload ->
                succeeded.add(
                    SinkResult(
                        idempotencyKey = payload.idempotencyKey,
                        status = SinkStatus.SUCCESS,
                        processedAt = Instant.now().toString(),
                    )
                )
            }

            logger.info("Personalize PutItems succeeded: count={}", items.size)
        } catch (e: software.amazon.awssdk.services.personalizeevents.model.InvalidInputException) {
            return Either.Left(
                SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.BUSINESS_RULE_VIOLATION,
                    message = "Personalize invalid input: ${e.message}",
                )
            )
        } catch (e: software.amazon.awssdk.services.personalizeevents.model.ResourceNotFoundException) {
            return Either.Left(
                SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
                    message = "Personalize dataset not found: $datasetArn",
                )
            )
        } catch (e: software.amazon.awssdk.core.exception.SdkServiceException) {
            return Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                    message = "Personalize service error: ${e.message}",
                )
            )
        } catch (e: Exception) {
            return Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "Personalize request failed: ${e.message}",
                )
            )
        }

        return Either.Right(BatchResult(succeeded, retryableFailed, emptyList()))
    }

    /**
     * DELETE 실행 (RFC-020 R1)
     *
     * Personalize는 명시적 삭제 API 없음.
     * 관례: PutItems with properties={"__deleted":"true"} (soft delete)
     */
    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> {
        return try {
            val item = Item.builder()
                .itemId(entityKey)
                .properties("""{"__deleted":"true"}""")
                .build()

            val request = PutItemsRequest.builder()
                .datasetArn(datasetArn)
                .items(listOf(item))
                .build()

            logger.debug("Personalize soft-delete: entityKey={}", entityKey)

            personalizeClient.putItems(request)

            logger.info("Personalize soft-delete succeeded: entityKey={}", entityKey)

            Either.Right(
                SinkResult(
                    idempotencyKey = "$tenantId:$entityKey:delete",
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                    metadata = mapOf("entityKey" to entityKey, "action" to "soft-delete"),
                )
            )
        } catch (e: software.amazon.awssdk.core.exception.SdkServiceException) {
            Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.TEMPORARY_UNAVAILABLE,
                    message = "Personalize delete failed: ${e.message}",
                )
            )
        } catch (e: Exception) {
            Either.Left(
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "Personalize delete failed: ${e.message}",
                )
            )
        }
    }
}
