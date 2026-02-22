package com.oliveyoung.ivmlite.pkg.sinks.ports

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.SinkError

/**
 * Sink 실패 레코드 저장소 Port (RFC-020 R3)
 *
 * Lambda에서 N회 시도 후 실패한 레코드를 저장.
 * Admin UI에서 조회/재처리 가능.
 */
interface SinkFailureRepositoryPort {

    /**
     * 실패 레코드 저장
     */
    suspend fun save(record: SinkFailureRecord): Either<SinkError, Unit>

    /**
     * 실패 레코드 목록 조회 (Admin UI용)
     */
    suspend fun findByTarget(target: String, limit: Int = 100): Either<SinkError, List<SinkFailureRecord>>

    /**
     * 상태 업데이트 (재처리 시)
     */
    suspend fun updateStatus(sinkEventId: String, target: String, status: FailureStatus): Either<SinkError, Unit>
}

/**
 * 실패 레코드
 */
data class SinkFailureRecord(
    val sinkEventId: String,
    val target: String,
    val errorCategory: String,
    val errorReasonCode: String,
    val errorMessage: String,
    val payload: String,
    val attemptCount: Int,
    val createdAt: String,
    val status: FailureStatus = FailureStatus.FAILED,
)

enum class FailureStatus {
    FAILED,
    RETRIED,
    RESOLVED,
}
