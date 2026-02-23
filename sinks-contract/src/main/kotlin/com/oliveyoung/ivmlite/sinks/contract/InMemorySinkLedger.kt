package com.oliveyoung.ivmlite.sinks.contract

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory SinkLedger (SOTA-grade)
 *
 * 용도:
 * - 테스트
 * - 로컬 개발
 * - 프로토타이핑
 *
 * 프로덕션: DynamoDBSinkLedger 사용 권장
 */
class InMemorySinkLedger : SinkLedger {
    private val ledger = ConcurrentHashMap<String, LedgerEntry>()

    /**
     * Optimistic Lock 기반 처리 시작
     */
    override suspend fun tryStart(
        pluginId: String,
        idempotencyKey: String,
        payloadDigest: String,
        contractVersion: String
    ): Either<SinkError, Boolean> {
        val key = "$pluginId#$idempotencyKey"

        // 이미 존재하는지 확인
        val existing = ledger[key]
        if (existing != null) {
            // Digest 일치 확인 (동일 데이터)
            if (existing.payloadDigest != payloadDigest) {
                return SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.BUSINESS_RULE_VIOLATION,
                    message = "Idempotency key conflict: same key, different digest",
                    context = mapOf(
                        "existing_digest" to existing.payloadDigest,
                        "new_digest" to payloadDigest
                    )
                ).left()
            }

            // 이미 처리 완료
            if (existing.status == LedgerStatus.COMPLETED) {
                return false.right()  // 재처리 방지
            }

            // 처리 중 or 실패 → 재시도 허용
            return true.right()
        }

        // 새 항목 생성
        val newEntry = LedgerEntry(
            pluginId = pluginId,
            idempotencyKey = idempotencyKey,
            payloadDigest = payloadDigest,
            contractVersion = contractVersion,
            status = LedgerStatus.PROCESSING,
            attemptCount = 1,
            createdAt = Instant.now().toString(),
            processedAt = null,
            lastError = null,
            resultMetadata = emptyMap()
        )

        ledger[key] = newEntry
        return true.right()
    }

    /**
     * 처리 완료 기록
     */
    override suspend fun complete(
        pluginId: String,
        idempotencyKey: String,
        result: SinkResult
    ): Either<SinkError, Unit> {
        val key = "$pluginId#$idempotencyKey"
        val entry = ledger[key] ?: return SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
            message = "Ledger entry not found: $key"
        ).left()

        val updated = entry.copy(
            status = LedgerStatus.COMPLETED,
            processedAt = result.processedAt,
            resultMetadata = result.metadata
        )

        ledger[key] = updated
        return Unit.right()
    }

    /**
     * 처리 실패 기록
     */
    override suspend fun fail(
        pluginId: String,
        idempotencyKey: String,
        error: SinkError,
        attemptCount: Int
    ): Either<SinkError, Unit> {
        val key = "$pluginId#$idempotencyKey"
        val entry = ledger[key] ?: return SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.RESOURCE_NOT_FOUND,
            message = "Ledger entry not found: $key"
        ).left()

        val updated = entry.copy(
            status = LedgerStatus.FAILED,
            attemptCount = attemptCount,
            lastError = error,
            processedAt = Instant.now().toString()
        )

        ledger[key] = updated
        return Unit.right()
    }

    /**
     * 상태 조회
     */
    override suspend fun getStatus(
        pluginId: String,
        idempotencyKey: String
    ): Either<SinkError, LedgerEntry?> {
        val key = "$pluginId#$idempotencyKey"
        return ledger[key].right()
    }

    /**
     * Replay용 쿼리
     */
    override suspend fun queryForReplay(
        pluginId: String,
        filters: ReplayFilters,
        limit: Int
    ): Either<SinkError, List<LedgerEntry>> {
        val filtered = ledger.values
            .filter { it.pluginId == pluginId }
            .filter { entry ->
                filters.errorCategory?.let { entry.lastError?.category == it } ?: true
            }
            .filter { entry ->
                filters.reasonCode?.let { entry.lastError?.reasonCode == it } ?: true
            }
            .sortedByDescending { it.createdAt }
            .take(limit)

        return filtered.right()
    }

    /**
     * 테스트용: 전체 초기화
     */
    fun clear() {
        ledger.clear()
    }

    /**
     * 테스트용: 전체 개수
     */
    fun size(): Int = ledger.size
}
