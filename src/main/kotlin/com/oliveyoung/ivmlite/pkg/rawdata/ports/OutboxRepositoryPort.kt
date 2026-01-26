package com.oliveyoung.ivmlite.pkg.rawdata.ports

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import java.util.UUID

/**
 * Outbox Repository Port (RFC-IMPL-008)
 *
 * Transactional Outbox 패턴을 위한 포트.
 * v1: Polling 방식, v2: Debezium CDC 방식 (포트 동일, 어댑터만 교체)
 */
interface OutboxRepositoryPort {

    // ==================== 저장 ====================

    /**
     * Outbox 엔트리 저장
     *
     * ⚠️ 반드시 비즈니스 데이터 저장과 같은 트랜잭션에서 호출해야 함!
     */
    suspend fun insert(entry: OutboxEntry): Result<OutboxEntry>

    /**
     * Batch 엔트리 저장 (원자성 보장)
     */
    suspend fun insertAll(entries: List<OutboxEntry>): Result<List<OutboxEntry>>

    // ==================== 조회 ====================

    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<OutboxEntry>

    /**
     * PENDING 상태 조회 (createdAt 오름차순, FIFO)
     */
    suspend fun findPending(limit: Int): Result<List<OutboxEntry>>

    /**
     * 특정 AggregateType의 PENDING 상태 조회
     */
    suspend fun findPendingByType(type: AggregateType, limit: Int): Result<List<OutboxEntry>>

    // ==================== 상태 변경 ====================

    /**
     * PROCESSED로 마킹 (일괄)
     *
     * @return 실제 처리된 개수
     */
    suspend fun markProcessed(ids: List<UUID>): Result<Int>

    /**
     * FAILED로 마킹 (retryCount 증가)
     */
    suspend fun markFailed(id: UUID, reason: String): Result<OutboxEntry>

    /**
     * PENDING으로 리셋 (재시도용)
     */
    suspend fun resetToPending(id: UUID): Result<OutboxEntry>

    // ==================== Result 타입 ====================

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}

/**
 * 표준 이벤트 타입 상수
 */
object OutboxEventTypes {
    const val RAW_DATA_INGESTED = "RawDataIngested"
    const val SLICE_CREATED = "SliceCreated"
    const val SLICE_UPDATED = "SliceUpdated"
    const val CHANGESET_CREATED = "ChangeSetCreated"
}
