package com.oliveyoung.ivmlite.sinks.contract

import arrow.core.Either

/**
 * Sink Ledger (SOTA-grade Idempotency Store)
 *
 * 역할:
 * 1. 재처리 방지 (tryStart → complete/fail)
 * 2. 결과 추적 (감사)
 * 3. Replay 지원 (backfill/migration)
 *
 * 저장소: DynamoDB 권장 (PK: pluginId#idempotencyKey)
 */
interface SinkLedger {
    /**
     * 처리 시작 시도 (Optimistic Lock)
     *
     * @return true: 처리 허용, false: 이미 처리됨
     */
    suspend fun tryStart(
        pluginId: String,
        idempotencyKey: String,
        payloadDigest: String,
        contractVersion: String
    ): Either<SinkError, Boolean>

    /**
     * 처리 완료 기록
     */
    suspend fun complete(
        pluginId: String,
        idempotencyKey: String,
        result: SinkResult
    ): Either<SinkError, Unit>

    /**
     * 처리 실패 기록
     */
    suspend fun fail(
        pluginId: String,
        idempotencyKey: String,
        error: SinkError,
        attemptCount: Int
    ): Either<SinkError, Unit>

    /**
     * 상태 조회 (디버깅/모니터링)
     */
    suspend fun getStatus(
        pluginId: String,
        idempotencyKey: String
    ): Either<SinkError, LedgerEntry?>

    /**
     * Replay용 쿼리 (필터링)
     */
    suspend fun queryForReplay(
        pluginId: String,
        filters: ReplayFilters,
        limit: Int = 100
    ): Either<SinkError, List<LedgerEntry>>
}

/**
 * Ledger Entry (저장 레코드)
 */
data class LedgerEntry(
    val pluginId: String,
    val idempotencyKey: String,
    val payloadDigest: String,
    val contractVersion: String,
    val status: LedgerStatus,
    val attemptCount: Int,
    val createdAt: String,
    val processedAt: String? = null,
    val lastError: SinkError? = null,
    val resultMetadata: Map<String, String> = emptyMap()
)

enum class LedgerStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

data class ReplayFilters(
    val tenantId: String? = null,
    val timeRange: Pair<String, String>? = null,
    val errorCategory: ErrorCategory? = null,
    val reasonCode: ErrorReasonCode? = null
)
