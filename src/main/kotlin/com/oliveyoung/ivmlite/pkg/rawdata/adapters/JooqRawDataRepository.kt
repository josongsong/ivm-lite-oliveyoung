package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
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

    override suspend fun putIdempotent(record: RawDataRecord): Result<Unit> =
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

                    // DB에는 접두사 없이 저장되므로 비교 시 접두사 제거
                    val recordHashWithoutPrefix = record.payloadHash.removePrefix("sha256:")
                    val existingHashWithoutPrefix = existingHash?.removePrefix("sha256:")
                    if (existingHashWithoutPrefix == recordHashWithoutPrefix &&
                        existingSchemaId == record.schemaId &&
                        existingSchemaVersion == record.schemaVersion.toString()
                    ) {
                        logger.debug(
                            "Idempotent insert: {}:{}@{} already exists",
                            record.tenantId.value,
                            record.entityKey.value,
                            record.version,
                        )
                        return@withContext Result.Ok(Unit)
                    } else {
                        return@withContext Result.Err(
                            DomainError.InvariantViolation(
                                "RawData invariant mismatch: hash/schema differs for " +
                                    "${record.tenantId.value}:${record.entityKey.value}@${record.version}",
                            ),
                        )
                    }
                }

                // 새 레코드 삽입
                // content_hash는 VARCHAR(64)이므로 "sha256:" 접두사 제거 (hex만 저장)
                val hashWithoutPrefix = record.payloadHash.removePrefix("sha256:")
                dsl.insertInto(RAW_DATA)
                    .set(ID, UUID.randomUUID())
                    .set(TENANT_ID, record.tenantId.value)
                    .set(ENTITY_KEY, record.entityKey.value)
                    .set(VERSION, record.version)
                    .set(SCHEMA_ID, record.schemaId)
                    .set(SCHEMA_VERSION, record.schemaVersion.toString())
                    .set(CONTENT_HASH, hashWithoutPrefix.take(64)) // 안전하게 64자로 제한
                    .set(CONTENT, JSONB.valueOf(record.payload))
                    .execute()

                logger.debug(
                    "Inserted raw data: {}:{}@{}",
                    record.tenantId.value,
                    record.entityKey.value,
                    record.version,
                )

                    Result.Ok(Unit)
                } catch (e: Exception) {
                    logger.error("Failed to insert raw data", e)
                    Result.Err(
                        DomainError.StorageError("Failed to insert raw data: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun get(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): Result<RawDataRecord> = tracer.withSpanSuspend(
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
                return@withContext Result.Err(
                    DomainError.NotFoundError(
                        "RawData",
                        "${tenantId.value}:${entityKey.value}@$version",
                    ),
                )
            }

            val content = row.get(CONTENT)?.data()
                ?: throw DomainError.StorageError("Missing required field 'content' in raw data row")
            // DB에서 읽을 때는 "sha256:" 접두사 복원
            val hashFromDb = requireField(row.get(CONTENT_HASH), "content_hash")
            val hashWithPrefix = if (hashFromDb.startsWith("sha256:")) hashFromDb else "sha256:$hashFromDb"
            val record = RawDataRecord(
                tenantId = TenantId(requireField(row.get(TENANT_ID), "tenant_id")),
                entityKey = EntityKey(requireField(row.get(ENTITY_KEY), "entity_key")),
                version = requireField(row.get(VERSION), "version"),
                schemaId = requireField(row.get(SCHEMA_ID), "schema_id"),
                schemaVersion = SemVer.parse(requireField(row.get(SCHEMA_VERSION), "schema_version")),
                payloadHash = hashWithPrefix,
                payload = content,
            )

            Result.Ok(record)
            } catch (e: DomainError) {
                Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to get raw data", e)
                Result.Err(
                    DomainError.StorageError("Failed to get raw data: ${e.message}"),
                )
            }
        }
    }

    override suspend fun getLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): Result<RawDataRecord> = withContext(Dispatchers.IO) {
        try {
            val row = dsl.selectFrom(RAW_DATA)
                .where(TENANT_ID.eq(tenantId.value))
                .and(ENTITY_KEY.eq(entityKey.value))
                .orderBy(VERSION.desc())
                .limit(1)
                .fetchOne()

            if (row == null) {
                return@withContext Result.Err(
                    DomainError.NotFoundError(
                        "RawData",
                        "${tenantId.value}:${entityKey.value}@latest",
                    ),
                )
            }

            // DB에서 읽을 때는 "sha256:" 접두사 복원
            val hashFromDb = requireField(row.get(CONTENT_HASH), "content_hash")
            val hashWithPrefix = if (hashFromDb.startsWith("sha256:")) hashFromDb else "sha256:$hashFromDb"
            val record = RawDataRecord(
                tenantId = TenantId(requireField(row.get(TENANT_ID), "tenant_id")),
                entityKey = EntityKey(requireField(row.get(ENTITY_KEY), "entity_key")),
                version = requireField(row.get(VERSION), "version"),
                schemaId = requireField(row.get(SCHEMA_ID), "schema_id"),
                schemaVersion = SemVer.parse(requireField(row.get(SCHEMA_VERSION), "schema_version")),
                payloadHash = hashWithPrefix,
                payload = row.get(CONTENT)?.data() ?: "{}",
            )

            Result.Ok(record)
        } catch (e: DomainError) {
            Result.Err(e)
        } catch (e: Exception) {
            logger.error("Failed to get latest raw data", e)
            Result.Err(
                DomainError.StorageError("Failed to get latest raw data: ${e.message}"),
            )
        }
    }

    /**
     * 여러 엔티티의 최신 RawData 일괄 조회 (N+1 쿼리 최적화)
     *
     * Window Function을 사용하여 단일 쿼리로 여러 엔티티의 최신 버전 조회.
     * ROW_NUMBER() OVER (PARTITION BY entity_key ORDER BY version DESC) = 1
     */
    override suspend fun batchGetLatest(
        tenantId: TenantId,
        entityKeys: List<EntityKey>,
    ): Result<Map<EntityKey, RawDataRecord>> = withContext(Dispatchers.IO) {
        if (entityKeys.isEmpty()) {
            return@withContext Result.Ok(emptyMap())
        }

        try {
            // Window function으로 각 entity_key별 최신 버전만 선택
            val rowNum = DSL.rowNumber()
                .over(DSL.partitionBy(ENTITY_KEY).orderBy(VERSION.desc()))
                .`as`("rn")

            val subquery = dsl
                .select(ID, TENANT_ID, ENTITY_KEY, VERSION, SCHEMA_ID, SCHEMA_VERSION, CONTENT_HASH, CONTENT, rowNum)
                .from(RAW_DATA)
                .where(TENANT_ID.eq(tenantId.value))
                .and(ENTITY_KEY.`in`(entityKeys.map { it.value }))
                .asTable("ranked")

            val rows = dsl
                .selectFrom(subquery)
                .where(DSL.field("rn", Int::class.java).eq(1))
                .fetch()

            val resultMap = rows.associate { row ->
                val entityKeyValue = requireField(row.get(ENTITY_KEY), "entity_key")
                val hashFromDb = requireField(row.get(CONTENT_HASH), "content_hash")
                val hashWithPrefix = if (hashFromDb.startsWith("sha256:")) hashFromDb else "sha256:$hashFromDb"

                EntityKey(entityKeyValue) to RawDataRecord(
                    tenantId = TenantId(requireField(row.get(TENANT_ID), "tenant_id")),
                    entityKey = EntityKey(entityKeyValue),
                    version = requireField(row.get(VERSION), "version"),
                    schemaId = requireField(row.get(SCHEMA_ID), "schema_id"),
                    schemaVersion = SemVer.parse(requireField(row.get(SCHEMA_VERSION), "schema_version")),
                    payloadHash = hashWithPrefix,
                    payload = row.get(CONTENT)?.data() ?: "{}",
                )
            }

            logger.debug("Batch fetched {} raw data records for {} keys", resultMap.size, entityKeys.size)
            Result.Ok(resultMap)
        } catch (e: Exception) {
            logger.error("Failed to batch get latest raw data", e)
            Result.Err(
                DomainError.StorageError("Failed to batch get latest raw data: ${e.message}"),
            )
        }
    }

    private fun <T> requireField(value: T?, fieldName: String): T =
        value ?: throw DomainError.StorageError("Required field '$fieldName' is null in raw data record")
}
