package com.oliveyoung.ivmlite.apps.lambda

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkLedger
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * SinkStream 코어 처리 로직 (Lambda/인프라 의존성 분리)
 *
 * SinkStreamHandler에서 Koin/DynamoDB 직접 의존을 제거한 순수 비즈니스 로직.
 * 테스트 가능성 + 재사용성 확보.
 */
class SinkStreamProcessor(
    private val pluginRegistry: SinkPluginRegistryPort,
    private val sinkLedger: SinkLedger,
    private val failureRepository: SinkFailureRepositoryPort,
    private val statusUpdater: SinkEventStatusUpdater,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 배치 이벤트 처리
     *
     * @return ProcessBatchResult (처리 결과 요약)
     * @throws RetryableSinkException Retryable 실패 시 (Lambda 재시도 트리거)
     */
    suspend fun processBatch(records: List<StreamRecord>): ProcessBatchResult {
        val counters = BatchCounters()

        records.forEach { record ->
            processRecord(record, counters)
        }

        if (counters.hasRetryableFailure) {
            throw RetryableSinkException("Retryable failures exist, triggering retry")
        }

        return ProcessBatchResult(
            processed = counters.processed,
            deleted = counters.deleted,
            errors = counters.errors,
        )
    }

    private suspend fun processRecord(record: StreamRecord, counters: BatchCounters) {
        try {
            when (record.eventName) {
                "INSERT", "MODIFY" -> processUpsert(record, counters)
                "REMOVE" -> processRemove(record, counters)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (e is RetryableSinkException) throw e
            counters.errors++
        }
    }

    private suspend fun processUpsert(record: StreamRecord, counters: BatchCounters) {
        val newImage = record.newImage ?: return
        if (newImage.status != "PENDING") return

        val viewData = json.parseToJsonElement(newImage.payload) as JsonObject
        val payloadDigest = SinkPayload.computePayloadDigest(viewData)
        var allTargetsSucceeded = true
        var hasRetryableError = false

        newImage.targets.forEach { target ->
            val targetResult = processTarget(target, newImage, viewData, payloadDigest)
            when (targetResult) {
                SinkTargetOutcome.SUCCESS, SinkTargetOutcome.SKIPPED -> { /* ok */ }
                SinkTargetOutcome.NON_RETRYABLE_FAILURE -> {
                    counters.errors++
                    allTargetsSucceeded = false
                }
                SinkTargetOutcome.RETRYABLE_FAILURE -> {
                    hasRetryableError = true
                    allTargetsSucceeded = false
                }
            }
        }

        if (!hasRetryableError) {
            val newStatus = if (allTargetsSucceeded) "COMPLETED" else "FAILED"
            statusUpdater.updateStatus(newImage.id, newImage.sk, newStatus)
        }

        if (hasRetryableError) {
            counters.hasRetryableFailure = true
        }

        counters.processed++
    }

    private suspend fun processTarget(
        target: String,
        sinkEvent: SinkEventImage,
        viewData: JsonObject,
        payloadDigest: String,
    ): SinkTargetOutcome {
        val plugin = pluginRegistry.resolve(target)
            ?: return SinkTargetOutcome.NON_RETRYABLE_FAILURE

        val idempotencyKey = SinkPayload.generateIdempotencyKey(
            sinkEvent.tenantId, sinkEvent.entityKey, sinkEvent.version, sinkEvent.viewType, payloadDigest
        )

        // Ledger tryStart
        val canProcess = sinkLedger.tryStart(target, idempotencyKey, payloadDigest, "1.0")
        canProcess.fold(
            { return SinkTargetOutcome.NON_RETRYABLE_FAILURE },
            { allowed -> if (!allowed) return SinkTargetOutcome.SKIPPED }
        )

        val sinkPayload = SinkPayload.V1(
            correlationId = sinkEvent.id,
            timestamp = Instant.now().toString(),
            idempotencyKey = idempotencyKey,
            orderingKey = SinkPayload.generateOrderingKey(sinkEvent.tenantId, sinkEvent.entityKey),
            payloadDigest = payloadDigest,
            tenantId = sinkEvent.tenantId,
            entityKey = sinkEvent.entityKey,
            entityVersion = sinkEvent.version,
            viewType = sinkEvent.viewType,
            viewData = viewData,
            metadata = sinkEvent.jobId?.let { mapOf("jobId" to it) } ?: emptyMap(),
        )

        return executePlugin(plugin, sinkPayload, target, idempotencyKey, sinkEvent.id)
    }

    private suspend fun executePlugin(
        plugin: SinkPlugin,
        sinkPayload: SinkPayload.V1,
        target: String,
        idempotencyKey: String,
        sinkEventId: String,
    ): SinkTargetOutcome {
        val result = plugin.execute(sinkPayload)
        return result.fold(
            { error ->
                sinkLedger.fail(target, idempotencyKey, error, 1)
                handlePluginError(error, target, sinkEventId, sinkPayload)
            },
            { sinkResult ->
                sinkLedger.complete(target, idempotencyKey, sinkResult)
                SinkTargetOutcome.SUCCESS
            }
        )
    }

    private suspend fun handlePluginError(
        error: SinkError,
        target: String,
        sinkEventId: String,
        payload: SinkPayload.V1,
    ): SinkTargetOutcome = when (error) {
        is SinkError.RetryableError -> SinkTargetOutcome.RETRYABLE_FAILURE
        is SinkError.NonRetryableError, is SinkError.PoisonPillError -> {
            saveFailureRecord(sinkEventId, target, error, payload.correlationId)
            SinkTargetOutcome.NON_RETRYABLE_FAILURE
        }
    }

    private suspend fun processRemove(record: StreamRecord, counters: BatchCounters) {
        val oldImage = record.oldImage ?: return
        val tenantId = oldImage.tenantId
        val entityKey = oldImage.entityKey
        val viewType = oldImage.viewType

        oldImage.targets.forEach { target ->
            val plugin = pluginRegistry.resolve(target) ?: return@forEach
            if (!plugin.supportsDelete) return@forEach

            plugin.delete(tenantId, entityKey, mapOf("viewType" to viewType)).fold(
                { counters.errors++ },
                { counters.deleted++ }
            )
        }
    }

    private suspend fun saveFailureRecord(
        sinkEventId: String,
        target: String,
        error: SinkError,
        correlationId: String,
    ) {
        val record = SinkFailureRecord(
            sinkEventId = sinkEventId,
            target = target,
            errorCategory = error.category.name,
            errorReasonCode = error.reasonCode.name,
            errorMessage = error.message,
            payload = correlationId,
            attemptCount = 1,
            createdAt = Instant.now().toString(),
        )
        failureRepository.save(record)
    }
}

/**
 * SinkEvent 상태 업데이트 추상화
 *
 * 프로덕션: DynamoDB UpdateItem
 * 테스트: InMemory 구현
 */
fun interface SinkEventStatusUpdater {
    suspend fun updateStatus(sinkEventId: String, sk: String, newStatus: String)
}

/**
 * DynamoDB Streams 레코드 추상화
 */
data class StreamRecord(
    val eventName: String,
    val newImage: SinkEventImage?,
    val oldImage: SinkEventImage?,
)

/**
 * SinkEvent DynamoDB 이미지 추상화
 */
data class SinkEventImage(
    val id: String,
    val sk: String,
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val viewType: String,
    val payload: String,
    val targets: List<String>,
    val status: String,
    val jobId: String? = null,
)

/**
 * 배치 처리 결과
 */
data class ProcessBatchResult(
    val processed: Int,
    val deleted: Int,
    val errors: Int,
)

private data class BatchCounters(
    var processed: Int = 0,
    var errors: Int = 0,
    var deleted: Int = 0,
    var hasRetryableFailure: Boolean = false,
)

private enum class SinkTargetOutcome {
    SUCCESS, SKIPPED, RETRYABLE_FAILURE, NON_RETRYABLE_FAILURE
}

/**
 * Retryable 실패 시 Lambda 전체 실패를 트리거하는 예외.
 * DynamoDB Streams가 자동으로 배치를 재전달.
 */
class RetryableSinkException(message: String) : RuntimeException(message)
