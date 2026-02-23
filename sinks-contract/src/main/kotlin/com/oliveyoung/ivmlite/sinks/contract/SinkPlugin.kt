package com.oliveyoung.ivmlite.sinks.contract

import arrow.core.Either

/**
 * Sink Plugin 인터페이스 (SOTA-grade)
 *
 * 핵심:
 * 1. Batch 처리 기본 (성능)
 * 2. Capabilities 선언 (확장성)
 * 3. Result 상세화 (관측성)
 */
interface SinkPlugin {
    val pluginId: String
    val capabilities: PluginCapabilities

    /**
     * DELETE 지원 여부 (RFC-020 R1)
     *
     * true: DynamoDB REMOVE 이벤트 시 delete() 호출
     * false: REMOVE 이벤트 무시
     */
    val supportsDelete: Boolean get() = false

    /**
     * 단일 실행 (편의 메서드)
     */
    suspend fun execute(payload: SinkPayload): Either<SinkError, SinkResult> {
        return executeBatch(listOf(payload)).map { batchResult ->
            batchResult.succeeded.firstOrNull()
                ?: throw IllegalStateException("Batch returned no results")
        }
    }

    /**
     * 배치 실행 (기본 인터페이스)
     */
    suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult>

    /**
     * 삭제 실행 (RFC-020 R1)
     *
     * DynamoDB REMOVE 이벤트 시 Sink에서도 해당 데이터 삭제.
     * supportsDelete = true인 플러그인만 호출됨.
     */
    suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String> = emptyMap()
    ): Either<SinkError, SinkResult> {
        return Either.Left(
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                message = "Delete not supported by $pluginId"
            )
        )
    }
}

/**
 * Plugin Capabilities (확장 가능성)
 */
data class PluginCapabilities(
    val supportedContractVersions: Set<String>,
    val supportsBatch: Boolean = true,
    val maxBatchSize: Int = 10,
    val supportsCompression: Boolean = false,
    val supportedCodecs: Set<String> = setOf("json"),
    val supportsOtelPropagation: Boolean = true,
    val supportsIdempotency: Boolean = true
)

/**
 * Batch Result
 */
data class BatchResult(
    val succeeded: List<SinkResult>,
    val retryableFailed: List<FailedItem>,
    val nonRetryableFailed: List<FailedItem>
) {
    data class FailedItem(
        val idempotencyKey: String,
        val error: SinkError
    )

    val totalCount: Int
        get() = succeeded.size + retryableFailed.size + nonRetryableFailed.size

    val hasFailures: Boolean
        get() = retryableFailed.isNotEmpty() || nonRetryableFailed.isNotEmpty()
}

/**
 * Sink Result (성공 결과)
 */
data class SinkResult(
    val idempotencyKey: String,
    val status: SinkStatus,
    val processedAt: String,
    val metadata: Map<String, String> = emptyMap()
)

enum class SinkStatus {
    SUCCESS,
    ALREADY_PROCESSED  // Idempotent 재처리
}
