package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.IngestUnitOfWorkPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * jOOQ 기반 Ingest Unit of Work (PostgreSQL)
 *
 * Transactional Outbox 패턴 구현:
 * - RawData와 Outbox를 **단일 PostgreSQL 트랜잭션**으로 묶어서 저장
 * - 원자성 보장: 둘 다 성공하거나 둘 다 실패
 *
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 */
class JooqIngestUnitOfWork(
    private val dsl: DSLContext,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("jooq-ingest-uow"),
) : IngestUnitOfWorkPort, HealthCheckable {

    override val healthName: String = "ingest-uow"

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = LoggerFactory.getLogger(JooqIngestUnitOfWork::class.java)

    companion object {
        // RawData 테이블
        private val RAW_DATA = DSL.table("raw_data")
        private val RAW_ID = DSL.field("id", UUID::class.java)
        private val RAW_TENANT_ID = DSL.field("tenant_id", String::class.java)
        private val RAW_ENTITY_KEY = DSL.field("entity_key", String::class.java)
        private val RAW_VERSION = DSL.field("version", Long::class.java)
        private val RAW_SCHEMA_ID = DSL.field("schema_id", String::class.java)
        private val RAW_SCHEMA_VERSION = DSL.field("schema_version", String::class.java)
        private val RAW_CONTENT_HASH = DSL.field("content_hash", String::class.java)
        private val RAW_CONTENT = DSL.field("content", JSONB::class.java)
        private val RAW_CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)

        // Outbox 테이블
        private val OUTBOX = DSL.table("outbox")
        private val OUTBOX_ID = DSL.field("id", UUID::class.java)
        private val OUTBOX_IDEMPOTENCY_KEY = DSL.field("idempotency_key", String::class.java)
        private val OUTBOX_AGGREGATE_TYPE = DSL.field("aggregatetype", String::class.java)
        private val OUTBOX_AGGREGATE_ID = DSL.field("aggregateid", String::class.java)
        private val OUTBOX_TYPE = DSL.field("type", String::class.java)
        private val OUTBOX_PAYLOAD = DSL.field("payload", JSONB::class.java)
        private val OUTBOX_STATUS = DSL.field("status", String::class.java)
        private val OUTBOX_CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
        private val OUTBOX_RETRY_COUNT = DSL.field("retry_count", Int::class.java)
    }

    override suspend fun executeIngest(
        rawData: RawDataRecord,
        outboxEntry: OutboxEntry,
    ): Result<Unit> = tracer.withSpanSuspend(
        "PostgreSQL.executeIngest",
        mapOf(
            "db.system" to "postgresql",
            "db.operation" to "transaction",
            "tenant_id" to rawData.tenantId.value,
            "entity_key" to rawData.entityKey.value,
            "version" to rawData.version.toString(),
        ),
    ) {
        withContext(Dispatchers.IO) {
            try {
                dsl.transaction { config ->
                    val txDsl = DSL.using(config)

                    // === Step 1: RawData 멱등성 검사 및 저장 ===
                    val existing = txDsl.selectFrom(RAW_DATA)
                        .where(RAW_TENANT_ID.eq(rawData.tenantId.value))
                        .and(RAW_ENTITY_KEY.eq(rawData.entityKey.value))
                        .and(RAW_VERSION.eq(rawData.version))
                        .fetchOne()

                    if (existing != null) {
                        // 멱등성 검사
                        val existingHash = existing.get(RAW_CONTENT_HASH)?.removePrefix("sha256:")
                        val recordHash = rawData.payloadHash.removePrefix("sha256:")
                        val existingSchemaId = existing.get(RAW_SCHEMA_ID)
                        val existingSchemaVersion = existing.get(RAW_SCHEMA_VERSION)

                        if (existingHash != recordHash ||
                            existingSchemaId != rawData.schemaId ||
                            existingSchemaVersion != rawData.schemaVersion.toString()
                        ) {
                            throw DomainError.InvariantViolation(
                                "RawData invariant mismatch: hash/schema differs for " +
                                    "${rawData.tenantId.value}:${rawData.entityKey.value}@${rawData.version}"
                            )
                        }
                        // 이미 존재하면 skip (멱등성)
                        logger.debug(
                            "Idempotent: RawData already exists {}:{}@{}",
                            rawData.tenantId.value, rawData.entityKey.value, rawData.version
                        )
                    } else {
                        // 새 RawData 삽입
                        val hashWithoutPrefix = rawData.payloadHash.removePrefix("sha256:")
                        txDsl.insertInto(RAW_DATA)
                            .set(RAW_ID, UUID.randomUUID())
                            .set(RAW_TENANT_ID, rawData.tenantId.value)
                            .set(RAW_ENTITY_KEY, rawData.entityKey.value)
                            .set(RAW_VERSION, rawData.version)
                            .set(RAW_SCHEMA_ID, rawData.schemaId)
                            .set(RAW_SCHEMA_VERSION, rawData.schemaVersion.toString())
                            .set(RAW_CONTENT_HASH, hashWithoutPrefix.take(64))
                            .set(RAW_CONTENT, JSONB.valueOf(rawData.payload))
                            .execute()

                        logger.debug(
                            "Inserted RawData: {}:{}@{}",
                            rawData.tenantId.value, rawData.entityKey.value, rawData.version
                        )
                    }

                    // === Step 2: Outbox 멱등성 검사 및 저장 ===
                    val existingOutbox = txDsl.selectCount()
                        .from(OUTBOX)
                        .where(OUTBOX_IDEMPOTENCY_KEY.eq(outboxEntry.idempotencyKey))
                        .fetchOne(0, Int::class.java) ?: 0

                    if (existingOutbox > 0) {
                        // 이미 존재하면 skip (멱등성)
                        logger.debug("Idempotent: Outbox already exists with key {}", outboxEntry.idempotencyKey)
                    } else {
                        // 새 Outbox 삽입
                        txDsl.insertInto(OUTBOX)
                            .set(OUTBOX_ID, outboxEntry.id)
                            .set(OUTBOX_IDEMPOTENCY_KEY, outboxEntry.idempotencyKey)
                            .set(OUTBOX_AGGREGATE_TYPE, outboxEntry.aggregateType.name)
                            .set(OUTBOX_AGGREGATE_ID, outboxEntry.aggregateId)
                            .set(OUTBOX_TYPE, outboxEntry.eventType)
                            .set(OUTBOX_PAYLOAD, JSONB.valueOf(outboxEntry.payload))
                            .set(OUTBOX_STATUS, outboxEntry.status.name)
                            .set(OUTBOX_CREATED_AT, outboxEntry.createdAt.atOffset(ZoneOffset.UTC))
                            .set(OUTBOX_RETRY_COUNT, outboxEntry.retryCount)
                            .execute()

                        logger.debug("Inserted Outbox: {} ({})", outboxEntry.id, outboxEntry.eventType)
                    }
                }

                Result.Ok(Unit)
            } catch (e: DomainError) {
                Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to execute ingest transaction", e)
                Result.Err(
                    DomainError.StorageError("Ingest transaction failed: ${e.message}")
                )
            }
        }
    }
}
