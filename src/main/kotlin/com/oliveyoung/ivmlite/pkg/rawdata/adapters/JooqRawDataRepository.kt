package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

/**
 * jOOQ 기반 RawData Repository (PostgreSQL)
 *
 * RFC-IMPL Phase B-3: jOOQ Adapters
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 */
class JooqRawDataRepository(
    private val dsl: DSLContext,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("jooq-rawdata"),
) : RawDataRepositoryPort, HealthCheckable {

    override val healthName: String = "rawdata"

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = LoggerFactory.getLogger(JooqRawDataRepository::class.java)

    companion object {
        // 테이블/컬럼 정의 (jOOQ codegen 없이도 동작)
        private val RAW_DATA = DSL.table("raw_data")
        private val ID = DSL.field("id", UUID::class.java)
        private val TENANT_ID = DSL.field("tenant_id", String::class.java)
        private val ENTITY_KEY = DSL.field("entity_key", String::class.java)
        private val VERSION = DSL.field("version", Long::class.java)
        private val SCHEMA_ID = DSL.field("schema_id", String::class.java)
        private val SCHEMA_VERSION = DSL.field("schema_version", String::class.java)
        private val CONTENT_HASH = DSL.field("content_hash", String::class.java)
        private val CONTENT = DSL.field("content", JSONB::class.java)
        private val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
    }

    override suspend fun putIdempotent(record: RawDataRecord): RawDataRepositoryPort.Result<Unit> =
        tracer.withSpanSuspend(
            "PostgreSQL.putIdempotent",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "insert",
                "tenant_id" to record.tenantId.value,
                "entity_key" to record.entityKey.value,
                "version" to record.version.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                try {
                // 기존 레코드 조회 (멱등성 검사)
                val existing = dsl.selectFrom(RAW_DATA)
                    .where(TENANT_ID.eq(record.tenantId.value))
                    .and(ENTITY_KEY.eq(record.entityKey.value))
                    .and(VERSION.eq(record.version))
                    .fetchOne()

                if (existing != null) {
                    // 멱등성 검사: hash, schemaId, schemaVersion 모두 동일해야 함
                    val existingHash = existing.get(CONTENT_HASH)
                    val existingSchemaId = existing.get(SCHEMA_ID)
                    val existingSchemaVersion = existing.get(SCHEMA_VERSION)

                    if (existingHash == record.payloadHash &&
                        existingSchemaId == record.schemaId &&
                        existingSchemaVersion == record.schemaVersion.toString()
                    ) {
                        logger.debug(
                            "Idempotent insert: {}:{}@{} already exists",
                            record.tenantId.value,
                            record.entityKey.value,
                            record.version,
                        )
                        return@withContext RawDataRepositoryPort.Result.Ok(Unit)
                    } else {
                        return@withContext RawDataRepositoryPort.Result.Err(
                            DomainError.InvariantViolation(
                                "RawData invariant mismatch: hash/schema differs for " +
                                    "${record.tenantId.value}:${record.entityKey.value}@${record.version}",
                            ),
                        )
                    }
                }

                // 새 레코드 삽입
                dsl.insertInto(RAW_DATA)
                    .set(ID, UUID.randomUUID())
                    .set(TENANT_ID, record.tenantId.value)
                    .set(ENTITY_KEY, record.entityKey.value)
                    .set(VERSION, record.version)
                    .set(SCHEMA_ID, record.schemaId)
                    .set(SCHEMA_VERSION, record.schemaVersion.toString())
                    .set(CONTENT_HASH, record.payloadHash)
                    .set(CONTENT, JSONB.valueOf(record.payload))
                    .execute()

                logger.debug(
                    "Inserted raw data: {}:{}@{}",
                    record.tenantId.value,
                    record.entityKey.value,
                    record.version,
                )

                    RawDataRepositoryPort.Result.Ok(Unit)
                } catch (e: Exception) {
                    logger.error("Failed to insert raw data", e)
                    RawDataRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to insert raw data: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun get(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): RawDataRepositoryPort.Result<RawDataRecord> = tracer.withSpanSuspend(
        "PostgreSQL.get",
        mapOf(
            "db.system" to "postgresql",
            "db.operation" to "select",
            "tenant_id" to tenantId.value,
            "entity_key" to entityKey.value,
            "version" to version.toString(),
        ),
    ) {
        withContext(Dispatchers.IO) {
        try {
            val row = dsl.selectFrom(RAW_DATA)
                .where(TENANT_ID.eq(tenantId.value))
                .and(ENTITY_KEY.eq(entityKey.value))
                .and(VERSION.eq(version))
                .fetchOne()

            if (row == null) {
                return@withContext RawDataRepositoryPort.Result.Err(
                    DomainError.NotFoundError(
                        "RawData",
                        "${tenantId.value}:${entityKey.value}@$version",
                    ),
                )
            }

            val content = row.get(CONTENT)?.data()
                ?: throw DomainError.StorageError("Missing required field 'content' in raw data row")
            val record = RawDataRecord(
                tenantId = TenantId(row.get(TENANT_ID)!!),
                entityKey = EntityKey(row.get(ENTITY_KEY)!!),
                version = row.get(VERSION)!!,
                schemaId = row.get(SCHEMA_ID)!!,
                schemaVersion = SemVer.parse(row.get(SCHEMA_VERSION)!!),
                payloadHash = row.get(CONTENT_HASH)!!,
                payload = content,
            )

                RawDataRepositoryPort.Result.Ok(record)
            } catch (e: DomainError) {
                RawDataRepositoryPort.Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to get raw data", e)
                RawDataRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to get raw data: ${e.message}"),
                )
            }
        }
    }

    override suspend fun getLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): RawDataRepositoryPort.Result<RawDataRecord> = withContext(Dispatchers.IO) {
        try {
            val row = dsl.selectFrom(RAW_DATA)
                .where(TENANT_ID.eq(tenantId.value))
                .and(ENTITY_KEY.eq(entityKey.value))
                .orderBy(VERSION.desc())
                .limit(1)
                .fetchOne()

            if (row == null) {
                return@withContext RawDataRepositoryPort.Result.Err(
                    DomainError.NotFoundError(
                        "RawData",
                        "${tenantId.value}:${entityKey.value}@latest",
                    ),
                )
            }

            val record = RawDataRecord(
                tenantId = TenantId(row.get(TENANT_ID)!!),
                entityKey = EntityKey(row.get(ENTITY_KEY)!!),
                version = row.get(VERSION)!!,
                schemaId = row.get(SCHEMA_ID)!!,
                schemaVersion = SemVer.parse(row.get(SCHEMA_VERSION)!!),
                payloadHash = row.get(CONTENT_HASH)!!,
                payload = row.get(CONTENT)?.data() ?: "{}",
            )

            RawDataRepositoryPort.Result.Ok(record)
        } catch (e: Exception) {
            logger.error("Failed to get latest raw data", e)
            RawDataRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to get latest raw data: ${e.message}"),
            )
        }
    }
}
