package com.oliveyoung.ivmlite.pkg.rawdata.ports

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * Ingest Unit of Work Port
 *
 * RawData 저장과 Outbox 이벤트 발행을 **원자적으로** 처리하는 포트.
 * Transactional Outbox 패턴의 핵심: 두 작업이 같은 트랜잭션에서 실행되어야 함.
 *
 * ## 왜 필요한가?
 * - RawData 저장 성공 → Outbox 저장 실패 시 이벤트 유실
 * - Outbox 저장 성공 → RawData 저장 실패 시 고아 이벤트 발생
 * - 두 작업을 같은 트랜잭션으로 묶어야 일관성 보장
 *
 * ## 구현 어댑터
 * - JooqIngestUnitOfWork: PostgreSQL 트랜잭션으로 묶음
 * - InMemoryIngestUnitOfWork: 테스트용 (순차 실행)
 * - DynamoDBIngestUnitOfWork: DynamoDB TransactWriteItems 사용
 */
interface IngestUnitOfWorkPort {

    /**
     * RawData와 Outbox를 원자적으로 저장
     *
     * @param rawData 저장할 RawData
     * @param outboxEntry 발행할 Outbox 이벤트
     * @return 성공 시 Unit, 실패 시 DomainError
     */
    suspend fun executeIngest(
        rawData: RawDataRecord,
        outboxEntry: OutboxEntry,
    ): Result<Unit>

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
