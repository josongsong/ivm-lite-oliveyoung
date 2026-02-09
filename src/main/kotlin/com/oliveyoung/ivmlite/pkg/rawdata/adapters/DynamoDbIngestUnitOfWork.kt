package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.IngestUnitOfWorkPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import org.slf4j.LoggerFactory

/**
 * DynamoDB RawData + PostgreSQL Outbox Ingest Unit of Work
 *
 * - RawData: DynamoDB에 저장
 * - Outbox: PostgreSQL에 저장 (JooqOutboxRepository 사용)
 *
 * NOTE: DynamoDB와 PostgreSQL 간 완전한 트랜잭션은 보장되지 않음.
 * RawData 저장 후 Outbox 저장 실패 시 데이터 불일치 가능.
 * 실패 시 재시도 로직으로 eventually consistent 보장.
 */
class DynamoDbIngestUnitOfWork(
    private val rawDataRepo: RawDataRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
) : IngestUnitOfWorkPort, HealthCheckable {

    override val healthName: String = "dynamodb-ingest-uow"

    private val logger = LoggerFactory.getLogger(DynamoDbIngestUnitOfWork::class.java)

    override suspend fun healthCheck(): Boolean {
        return try {
            // HealthCheckable 인터페이스를 구현하는 Repository만 체크
            val rawDataHealthy = (rawDataRepo as? HealthCheckable)?.healthCheck() ?: true
            val outboxHealthy = (outboxRepo as? HealthCheckable)?.healthCheck() ?: true
            rawDataHealthy && outboxHealthy
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun executeIngest(
        rawData: RawDataRecord,
        outboxEntry: OutboxEntry,
    ): Result<Unit> {
        return try {
            // Step 1: RawData를 DynamoDB에 저장 (멱등성 보장)
            val rawResult = rawDataRepo.putIdempotent(rawData)
            when (rawResult) {
                is Result.Err -> {
                    logger.error(
                        "Failed to save RawData to DynamoDB: {}:{}@{} - {}",
                        rawData.tenantId.value, rawData.entityKey.value, rawData.version,
                        rawResult.error.message
                    )
                    return Result.Err(rawResult.error)
                }
                is Result.Ok -> {
                    logger.debug(
                        "Saved RawData to DynamoDB: {}:{}@{}",
                        rawData.tenantId.value, rawData.entityKey.value, rawData.version
                    )
                }
            }

            // Step 2: Outbox를 PostgreSQL에 저장 (멱등성 보장)
            val outboxResult = outboxRepo.insert(outboxEntry)
            when (outboxResult) {
                is Result.Err -> {
                    // RawData는 이미 저장됨, Outbox 저장 실패
                    // 재시도 시 RawData는 멱등성으로 skip, Outbox만 저장 시도
                    logger.error(
                        "Failed to save Outbox to PostgreSQL: {} - {}. RawData already saved, retry needed.",
                        outboxEntry.idempotencyKey, outboxResult.error.message
                    )
                    return Result.Err(outboxResult.error)
                }
                is Result.Ok -> {
                    logger.debug(
                        "Saved Outbox to PostgreSQL: {} ({})",
                        outboxEntry.id, outboxEntry.eventType
                    )
                }
            }

            Result.Ok(Unit)
        } catch (e: DomainError) {
            Result.Err(e)
        } catch (e: Exception) {
            logger.error("Failed to execute ingest", e)
            Result.Err(
                DomainError.StorageError("Ingest failed: ${e.message}")
            )
        }
    }
}
