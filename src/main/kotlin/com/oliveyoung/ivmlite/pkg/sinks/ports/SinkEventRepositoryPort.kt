package com.oliveyoung.ivmlite.pkg.sinks.ports

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.shared.domain.types.Result
import java.util.UUID

/**
 * SinkEvent Repository Port
 *
 * DynamoDB 기반 Sink 이벤트 저장소.
 * DynamoDB Streams를 통해 Lambda가 자동으로 처리.
 */
interface SinkEventRepositoryPort {

    /**
     * SinkEvent 저장 (멱등성 보장)
     *
     * idempotencyKey 중복 시 무시 (기존 항목 반환)
     */
    suspend fun put(event: SinkEvent): Result<SinkEvent>

    /**
     * Batch SinkEvent 저장
     */
    suspend fun putAll(events: List<SinkEvent>): Result<List<SinkEvent>>

    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<SinkEvent?>

    /**
     * jobId로 조회 (end-to-end 추적용)
     */
    suspend fun findByJobId(jobId: String): Result<List<SinkEvent>>

    /**
     * 상태별 조회 (Admin UI용)
     */
    suspend fun findByStatus(status: String, limit: Int): Result<List<SinkEvent>>
}
