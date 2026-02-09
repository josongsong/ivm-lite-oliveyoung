package com.oliveyoung.ivmlite.pkg.rawdata.ports

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.Result
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

    /**
     * 커서 기반 PENDING 상태 조회 (순서 보장)
     * 
     * @param limit 최대 반환 개수
     * @param cursor 다음 페이지 커서 (이전 응답의 nextCursor)
     * @param type 선택적 AggregateType 필터
     * @return OutboxPage (entries + nextCursor)
     */
    suspend fun findPendingWithCursor(
        limit: Int,
        cursor: String? = null,
        type: AggregateType? = null
    ): Result<OutboxPage>

    // ==================== Claim (원자적 태스크 할당) ====================

    /**
     * PENDING 엔트리를 원자적으로 PROCESSING으로 전환 후 반환
     * 
     * 단일 트랜잭션 내에서:
     * 1. SELECT FOR UPDATE SKIP LOCKED
     * 2. UPDATE status = PROCESSING, claimed_at = now()
     * 3. RETURN entry
     * 
     * @param limit 최대 claim할 개수
     * @param type 선택적 AggregateType 필터
     * @param workerId worker 식별자 (디버깅/모니터링용)
     * @return claim된 엔트리 목록
     */
    suspend fun claim(
        limit: Int,
        type: AggregateType? = null,
        workerId: String? = null
    ): Result<List<OutboxEntry>>

    /**
     * 단일 엔트리 claim (pollTask 스타일)
     */
    suspend fun claimOne(
        type: AggregateType? = null,
        workerId: String? = null
    ): Result<OutboxEntry?>

    /**
     * 오래된 PROCESSING 엔트리를 PENDING으로 복구 (타임아웃 처리)
     * 
     * @param olderThanSeconds PROCESSING 상태로 이 시간 이상 경과한 엔트리
     * @return 복구된 개수
     */
    suspend fun recoverStaleProcessing(olderThanSeconds: Long): Result<Int>

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

    /**
     * FAILED 상태 엔트리 조회
     */
    suspend fun findFailed(limit: Int): Result<List<OutboxEntry>>

    /**
     * 모든 FAILED 엔트리를 PENDING으로 일괄 리셋 (Admin UI용)
     * @return 리셋된 개수
     */
    suspend fun resetAllFailed(limit: Int): Result<Int>

    // ==================== Tier 1: Visibility Timeout ====================

    /**
     * Visibility Timeout 초과한 PROCESSING 엔트리를 PENDING으로 release
     *
     * @param visibilityTimeoutSeconds claim 후 이 시간 내 완료 안 되면 release
     * @return release된 개수
     */
    suspend fun releaseExpiredClaims(visibilityTimeoutSeconds: Long): Result<Int>

    // ==================== Tier 1: Dead Letter Queue ====================

    /**
     * 재시도 횟수 초과한 FAILED 엔트리를 DLQ로 이동
     *
     * @param maxRetryCount 이 횟수 초과 시 DLQ 이동
     * @return 이동된 개수
     */
    suspend fun moveToDlq(maxRetryCount: Int): Result<Int>

    /**
     * DLQ 엔트리 조회
     */
    suspend fun findDlq(limit: Int): Result<List<OutboxEntry>>

    /**
     * DLQ에서 원본 테이블로 재시도 (replay)
     *
     * @param id DLQ 엔트리 ID
     * @return 성공 여부
     */
    suspend fun replayFromDlq(id: UUID): Result<Boolean>

    // ==================== Tier 1: Priority Queue ====================

    /**
     * 우선순위 기반 claim
     * 낮은 priority 값 → 높은 우선순위 (먼저 처리)
     * 동일 priority는 createdAt 순
     *
     * @param limit 최대 claim 개수
     * @param workerId Worker 식별자
     */
    suspend fun claimByPriority(limit: Int, workerId: String?): Result<List<OutboxEntry>>

    // ==================== Tier 1: Entity-Level Ordering ====================

    /**
     * Entity별 순서 보장 claim
     *
     * 같은 aggregateId의 엔트리는 entityVersion 순서대로만 처리 가능.
     * 이전 버전이 PROCESSING 중이면 해당 entity의 다음 버전은 claim 불가.
     *
     * @param limit 최대 claim 개수
     * @param workerId Worker 식별자
     */
    suspend fun claimWithOrdering(limit: Int, workerId: String?): Result<List<OutboxEntry>>

}

/**
 * Outbox 페이지 결과 (커서 기반 페이지네이션)
 */
data class OutboxPage(
    val entries: List<OutboxEntry>,
    val nextCursor: String?,
    val hasMore: Boolean
)

/**
 * 표준 이벤트 타입 상수
 */
object OutboxEventTypes {
    const val RAW_DATA_INGESTED = "RawDataIngested"
    const val SLICE_CREATED = "SliceCreated"
    const val SLICE_UPDATED = "SliceUpdated"
    const val CHANGESET_CREATED = "ChangeSetCreated"
}
