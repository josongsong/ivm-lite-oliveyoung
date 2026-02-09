package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.IngestUnitOfWorkPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable

/**
 * InMemory Ingest Unit of Work (테스트용)
 *
 * 실제 트랜잭션은 없지만, RawData와 Outbox를 순차적으로 저장.
 * 테스트 환경에서 IngestWorkflow의 동작 검증용.
 */
class InMemoryIngestUnitOfWork(
    private val rawRepo: RawDataRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
) : IngestUnitOfWorkPort, HealthCheckable {

    override val healthName: String = "ingest-uow"

    override suspend fun healthCheck(): Boolean = true

    override suspend fun executeIngest(
        rawData: RawDataRecord,
        outboxEntry: OutboxEntry,
    ): Result<Unit> {
        // Step 1: RawData 저장
        when (val r = rawRepo.putIdempotent(rawData)) {
            is Result.Ok -> { /* continue */ }
            is Result.Err -> return Result.Err(r.error)
        }

        // Step 2: Outbox 저장
        when (val r = outboxRepo.insert(outboxEntry)) {
            is Result.Ok -> { /* continue */ }
            is Result.Err -> return Result.Err(r.error)
        }

        return Result.Ok(Unit)
    }
}
