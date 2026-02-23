package com.oliveyoung.ivmlite.apps.lambda

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkLedger
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * SinkBatchProcessor - SQS 배치 기반 Sink 처리
 *
 * SQS 메시지 배치를 target별로 그룹핑 후 executeBatch로 벌크 처리.
 * SinkStreamProcessor와 달리 statusUpdater 없음 (SQS는 소비 후 삭제).
 */
class SinkBatchProcessor(
    private val pluginRegistry: SinkPluginRegistryPort,
    private val sinkLedger: SinkLedger,
    private val failureRepository: SinkFailureRepositoryPort,
) {
    private val logger = LoggerFactory.getLogger(SinkBatchProcessor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * SQS 메시지 배치 처리
     *
     * @param messages SQS 메시지 (body = SinkEventMessageDto JSON)
     * @return 처리 결과
     */
    suspend fun processBatch(messages: List<SqsSinkMessage>): SinkBatchProcessResult {
        val payloadsByTarget = mutableMapOf<String, MutableList<SinkPayload.V1>>()
        var parseErrors = 0

        for (msg in messages) {
            val dto = parseMessage(msg.body)
            if (dto == null) {
                parseErrors++
                logger.warn("Failed to parse SQS message: {}", msg.messageId)
                continue
            }

            val viewData = json.parseToJsonElement(dto.payload) as? JsonObject
            if (viewData == null) {
                parseErrors++
                logger.warn("Invalid payload JSON in message: {}", msg.messageId)
                continue
            }

            val payloadDigest = SinkPayload.computePayloadDigest(viewData)

            for (target in dto.sinkTargets) {
                val idempotencyKey = SinkPayload.generateIdempotencyKey(
                    dto.tenantId, dto.entityKey, dto.version, dto.viewType, payloadDigest
                )

                val canProcess = sinkLedger.tryStart(target, idempotencyKey, payloadDigest, "1.0")
                val allowed = when (canProcess) {
                    is Either.Left -> false
                    is Either.Right -> canProcess.value
                }
                if (allowed) {
                            val payload = SinkPayload.V1(
                                correlationId = dto.id,
                                timestamp = Instant.now().toString(),
                                idempotencyKey = idempotencyKey,
                                orderingKey = SinkPayload.generateOrderingKey(dto.tenantId, dto.entityKey),
                                payloadDigest = payloadDigest,
                                tenantId = dto.tenantId,
                                entityKey = dto.entityKey,
                                entityVersion = dto.version,
                                viewType = dto.viewType,
                                viewData = viewData,
                                metadata = dto.jobId?.let { mapOf("jobId" to it) } ?: emptyMap(),
                            )
                            payloadsByTarget.getOrPut(target) { mutableListOf() }.add(payload)
                        }
            }
        }

        var succeeded = 0
        var failed = 0

        for ((target, payloads) in payloadsByTarget) {
            if (payloads.isEmpty()) continue

            val plugin = pluginRegistry.resolve(target)
            if (plugin == null) {
                logger.warn("Plugin not found for target: {}", target)
                failed += payloads.size
                continue
            }

            when (val result = plugin.executeBatch(payloads)) {
                is Either.Left -> {
                    failed += payloads.size
                    result.value.let { error ->
                        logger.error("Sink batch failed for target {}: {}", target, error.message)
                        payloads.forEach { p ->
                            sinkLedger.fail(target, p.idempotencyKey, error, 1)
                            saveFailureRecord(p.correlationId, target, error)
                        }
                    }
                }
                is Either.Right -> {
                    val batchResult = result.value
                    succeeded += batchResult.succeeded.size
                    failed += batchResult.retryableFailed.size + batchResult.nonRetryableFailed.size

                    batchResult.succeeded.forEach { r ->
                        sinkLedger.complete(target, r.idempotencyKey, r)
                    }
                    (batchResult.retryableFailed + batchResult.nonRetryableFailed).forEach { item ->
                        sinkLedger.fail(target, item.idempotencyKey, item.error, 1)
                        saveFailureRecord(
                            payloads.find { it.idempotencyKey == item.idempotencyKey }?.correlationId ?: "",
                            target,
                            item.error
                        )
                    }
                }
            }
        }

        return SinkBatchProcessResult(
            totalMessages = messages.size,
            parseErrors = parseErrors,
            succeeded = succeeded,
            failed = failed,
        )
    }

    private suspend fun saveFailureRecord(
        correlationId: String,
        target: String,
        error: SinkError,
    ) {
        failureRepository.save(
            SinkFailureRecord(
                sinkEventId = correlationId,
                target = target,
                errorCategory = error.category.name,
                errorReasonCode = error.reasonCode.name,
                errorMessage = error.message,
                payload = correlationId,
                attemptCount = 1,
                createdAt = Instant.now().toString(),
            )
        )
    }

    private fun parseMessage(body: String): SinkEventMessageDto? = try {
        json.decodeFromString<SinkEventMessageDto>(body)
    } catch (_: Exception) {
        null
    }
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

/**
 * SQS 메시지 추상화
 */
data class SqsSinkMessage(
    val messageId: String,
    val body: String,
)

/**
 * 배치 처리 결과
 */
data class SinkBatchProcessResult(
    val totalMessages: Int,
    val parseErrors: Int,
    val succeeded: Int,
    val failed: Int,
)
