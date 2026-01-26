package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import java.time.Instant
import java.util.UUID

/**
 * Transactional Outbox 패턴의 핵심 엔티티.
 * 비즈니스 데이터와 같은 트랜잭션에 저장되어 이벤트 발행의 원자성을 보장한다.
 *
 * @property id 고유 식별자 (UUID)
 * @property aggregateType 집계 타입 (RAW_DATA, SLICE, CHANGESET)
 * @property aggregateId 집계 ID (형식: "tenantId:entityKey")
 * @property eventType 이벤트 타입 (예: "RawDataIngested", "SliceCreated")
 * @property payload 이벤트 페이로드 (JSON 문자열)
 * @property status 처리 상태 (PENDING, PROCESSED, FAILED)
 * @property createdAt 생성 시각
 * @property processedAt 처리 완료 시각 (null이면 미처리)
 * @property retryCount 재시도 횟수
 */
data class OutboxEntry(
    val id: UUID,
    val aggregateType: AggregateType,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxStatus,
    val createdAt: Instant,
    val processedAt: Instant? = null,
    val retryCount: Int = 0,
) {
    init {
        require(aggregateId.isNotBlank()) { "aggregateId must not be blank" }
        require(aggregateId.contains(":")) { "aggregateId must be in format 'tenantId:entityKey'" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
        require(retryCount >= 0) { "retryCount must be non-negative" }
    }

    companion object {
        const val MAX_RETRY_COUNT = 5

        /**
         * 새 OutboxEntry 생성 (PENDING 상태)
         */
        fun create(
            aggregateType: AggregateType,
            aggregateId: String,
            eventType: String,
            payload: String,
        ): OutboxEntry = OutboxEntry(
            id = UUID.randomUUID(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = OutboxStatus.PENDING,
            createdAt = Instant.now(),
        )
    }

    /**
     * 처리 완료로 마킹
     */
    fun markProcessed(at: Instant = Instant.now()): OutboxEntry = copy(
        status = OutboxStatus.PROCESSED,
        processedAt = at,
    )

    /**
     * 실패로 마킹 (재시도 카운트 증가)
     */
    fun markFailed(): OutboxEntry = copy(
        status = OutboxStatus.FAILED,
        retryCount = retryCount + 1,
    )

    /**
     * 재시도 가능 여부
     */
    fun canRetry(): Boolean = retryCount < MAX_RETRY_COUNT

    /**
     * PENDING으로 리셋 (재시도용)
     */
    fun resetToPending(): OutboxEntry {
        check(canRetry()) { "Max retry count ($MAX_RETRY_COUNT) exceeded" }
        return copy(status = OutboxStatus.PENDING)
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
