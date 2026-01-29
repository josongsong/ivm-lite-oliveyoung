package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import java.time.Instant
import java.util.UUID

/**
 * Transactional Outbox 패턴의 핵심 엔티티.
 * 비즈니스 데이터와 같은 트랜잭션에 저장되어 이벤트 발행의 원자성을 보장한다.
 *
 * @property id 고유 식별자 (UUID)
 * @property idempotencyKey 멱등성 키 (동일 비즈니스 이벤트 중복 방지)
 * @property aggregateType 집계 타입 (RAW_DATA, SLICE, CHANGESET)
 * @property aggregateId 집계 ID (형식: "tenantId:entityKey")
 * @property eventType 이벤트 타입 (예: "RawDataIngested", "SliceCreated")
 * @property payload 이벤트 페이로드 (JSON 문자열)
 * @property status 처리 상태 (PENDING, PROCESSING, PROCESSED, FAILED)
 * @property createdAt 생성 시각
 * @property claimedAt claim 시각 (PROCESSING 상태 전환 시각)
 * @property claimedBy claim한 worker ID (디버깅용)
 * @property processedAt 처리 완료 시각 (null이면 미처리)
 * @property retryCount 재시도 횟수
 * @property failureReason 실패 사유 (FAILED 상태일 때)
 * @property priority 처리 우선순위 (낮을수록 높은 우선순위, 기본 100)
 * @property entityVersion 엔티티 버전 (순서 보장용)
 */
data class OutboxEntry(
    val id: UUID,
    val idempotencyKey: String,
    val aggregateType: AggregateType,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxStatus,
    val createdAt: Instant,
    val claimedAt: Instant? = null,
    val claimedBy: String? = null,
    val processedAt: Instant? = null,
    val retryCount: Int = 0,
    val failureReason: String? = null,
    val priority: Int = DEFAULT_PRIORITY,  // Tier 1: Priority Queue
    val entityVersion: Long? = null,       // Tier 1: Entity-Level Ordering
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(aggregateId.isNotBlank()) { "aggregateId must not be blank" }
        require(aggregateId.contains(":")) { "aggregateId must be in format 'tenantId:entityKey'" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
        require(retryCount >= 0) { "retryCount must be non-negative" }
    }

    companion object {
        const val MAX_RETRY_COUNT = 5
        const val DEFAULT_PRIORITY = 100  // 기본 우선순위 (낮음)

        /**
         * 새 OutboxEntry 생성 (PENDING 상태)
         * 
         * 결정성: idempotencyKey는 입력값 기반으로 결정적 생성
         * - 동일 aggregateId + eventType + payload → 동일 idempotencyKey
         * - 중복 이벤트 방지에 활용
         * 
         * @param aggregateType 집계 타입
         * @param aggregateId 집계 ID
         * @param eventType 이벤트 타입
         * @param payload 페이로드
         * @param timestamp 생성 시각 (테스트 시 주입 가능, 기본값: Instant.now())
         */
        fun create(
            aggregateType: AggregateType,
            aggregateId: String,
            eventType: String,
            payload: String,
            timestamp: Instant = Instant.now(),
        ): OutboxEntry {
            // 결정적 idempotencyKey 생성: aggregateId + eventType + payload hash
            val idempotencyKey = generateIdempotencyKey(aggregateId, eventType, payload)
            return OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = idempotencyKey,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
                status = OutboxStatus.PENDING,
                createdAt = timestamp,
            )
        }
        
        /**
         * 결정적 idempotencyKey 생성
         * 
         * 동일 비즈니스 이벤트 → 동일 key
         * 멱등성 보장에 활용 (INSERT 시 UNIQUE 제약조건)
         */
        fun generateIdempotencyKey(aggregateId: String, eventType: String, payload: String): String {
            val input = "$aggregateId|$eventType|$payload"
            return "idem_${Hashing.sha256Hex(input).take(32)}"
        }
    }

    /**
     * PROCESSING으로 마킹 (worker가 claim)
     */
    fun markClaimed(workerId: String? = null, at: Instant = Instant.now()): OutboxEntry = copy(
        status = OutboxStatus.PROCESSING,
        claimedAt = at,
        claimedBy = workerId,
    )

    /**
     * 처리 완료로 마킹
     */
    fun markProcessed(at: Instant = Instant.now()): OutboxEntry = copy(
        status = OutboxStatus.PROCESSED,
        processedAt = at,
    )

    /**
     * 실패로 마킹 (재시도 카운트 증가, 실패 사유 기록)
     * 
     * @param reason 실패 사유 (에러 메시지)
     */
    fun markFailed(reason: String? = null): OutboxEntry = copy(
        status = OutboxStatus.FAILED,
        retryCount = retryCount + 1,
        failureReason = reason,
    )

    /**
     * 재시도 가능 여부
     */
    fun canRetry(): Boolean = retryCount < MAX_RETRY_COUNT

    /**
     * PENDING으로 리셋 (재시도용 또는 stale 복구)
     */
    fun resetToPending(): OutboxEntry {
        check(canRetry()) { "Max retry count ($MAX_RETRY_COUNT) exceeded" }
        return copy(
            status = OutboxStatus.PENDING,
            claimedAt = null,
            claimedBy = null,
        )
    }

    /**
     * tenantId 추출
     */
    fun extractTenantId(): String = aggregateId.substringBefore(":")

    /**
     * entityKey 추출
     */
    fun extractEntityKey(): String = aggregateId.substringAfter(":")
}
