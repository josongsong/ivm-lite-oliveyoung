package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
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
import java.util.UUID

/**
 * jOOQ 기반 Slice Repository (PostgreSQL)
 *
 * RFC-IMPL Phase B-3: jOOQ Adapters
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 */
class JooqSliceRepository(
    private val dsl: DSLContext,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("jooq-slice"),
) : SliceRepositoryPort, HealthCheckable {

    override val healthName: String = "slice"

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = LoggerFactory.getLogger(JooqSliceRepository::class.java)

    companion object {
        private val SLICES = DSL.table("slices")
        private val ID = DSL.field("id", UUID::class.java)
        private val TENANT_ID = DSL.field("tenant_id", String::class.java)
        private val ENTITY_KEY = DSL.field("entity_key", String::class.java)
        private val SLICE_TYPE = DSL.field("slice_type", String::class.java)
        private val SLICE_VERSION = DSL.field("slice_version", Long::class.java)
        private val CONTENT_HASH = DSL.field("content_hash", String::class.java)
        private val CONTENT = DSL.field("content", JSONB::class.java)
        private val RULESET_REF = DSL.field("ruleset_ref", String::class.java)
        private val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
        // Tombstone 필드 (RFC-IMPL-010 D-1)
        private val IS_DELETED = DSL.field("is_deleted", Boolean::class.java)
        private val DELETED_AT_VERSION = DSL.field("deleted_at_version", Long::class.java)
        private val DELETE_REASON = DSL.field("delete_reason", String::class.java)
    }

    override suspend fun putAllIdempotent(slices: List<SliceRecord>): SliceRepositoryPort.Result<Unit> =
        tracer.withSpanSuspend(
            "PostgreSQL.putAllIdempotent",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "insert",
                "slice.count" to slices.size.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                if (slices.isEmpty()) {
                    return@withContext SliceRepositoryPort.Result.Ok(Unit)
                }

                try {
                dsl.transaction { config ->
                    val ctx = DSL.using(config)

                    for (slice in slices) {
                        // 기존 레코드 조회
                        val existing = ctx.selectFrom(SLICES)
                            .where(TENANT_ID.eq(slice.tenantId.value))
                            .and(ENTITY_KEY.eq(slice.entityKey.value))
                            .and(SLICE_TYPE.eq(slice.sliceType.name))
                            .and(SLICE_VERSION.eq(slice.version))
                            .fetchOne()

                        if (existing != null) {
                            // 멱등성 검사: hash가 동일해야 함
                            val existingHash = existing.get(CONTENT_HASH)
                            if (existingHash != slice.hash) {
                                throw DomainError.InvariantViolation(
                                    "Slice hash mismatch for ${slice.tenantId.value}:" +
                                        "${slice.entityKey.value}:${slice.sliceType.name}@${slice.version}",
                                )
                            }
                            // 동일 hash → skip
                            logger.debug(
                                "Idempotent: slice already exists {}:{}:{}@{}",
                                slice.tenantId.value,
                                slice.entityKey.value,
                                slice.sliceType.name,
                                slice.version,
                            )
                            continue
                        }

                        // 새 레코드 삽입
                        val rulesetRef = "${slice.ruleSetId}@${slice.ruleSetVersion}"
                        val ts = slice.tombstone

                        ctx.insertInto(SLICES)
                            .set(ID, UUID.randomUUID())
                            .set(TENANT_ID, slice.tenantId.value)
                            .set(ENTITY_KEY, slice.entityKey.value)
                            .set(SLICE_TYPE, slice.sliceType.name)
                            .set(SLICE_VERSION, slice.version)
                            .set(CONTENT_HASH, slice.hash)
                            .set(CONTENT, JSONB.valueOf(slice.data))
                            .set(RULESET_REF, rulesetRef)
                            .set(IS_DELETED, ts?.isDeleted ?: false)
                            .set(DELETED_AT_VERSION, ts?.deletedAtVersion)
                            .set(DELETE_REASON, ts?.deleteReason?.toDbValue())
                            .execute()

                        logger.debug(
                            "Inserted slice: {}:{}:{}@{}",
                            slice.tenantId.value,
                            slice.entityKey.value,
                            slice.sliceType.name,
                            slice.version,
                        )
                    }
                }

                    SliceRepositoryPort.Result.Ok(Unit)
                } catch (e: DomainError.InvariantViolation) {
                    SliceRepositoryPort.Result.Err(e)
                } catch (e: Exception) {
                    logger.error("Failed to put slices", e)
                    SliceRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to put slices: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceRepositoryPort.SliceKey>,
        includeTombstones: Boolean,
    ): SliceRepositoryPort.Result<List<SliceRecord>> = tracer.withSpanSuspend(
        "PostgreSQL.batchGet",
        mapOf(
            "db.system" to "postgresql",
            "db.operation" to "select",
            "key.count" to keys.size.toString(),
        ),
    ) {
        withContext(Dispatchers.IO) {
        if (keys.isEmpty()) {
            return@withContext SliceRepositoryPort.Result.Ok(emptyList())
        }

        try {
            val results = mutableListOf<SliceRecord>()

            for (key in keys) {
                var query = dsl.selectFrom(SLICES)
                    .where(TENANT_ID.eq(key.tenantId.value))
                    .and(ENTITY_KEY.eq(key.entityKey.value))
                    .and(SLICE_TYPE.eq(key.sliceType.name))
                    .and(SLICE_VERSION.eq(key.version))

                // tombstone 필터링
                if (!includeTombstones) {
                    query = query.and(IS_DELETED.eq(false))
                }

                val row = query.fetchOne()

                if (row == null) {
                    return@withContext SliceRepositoryPort.Result.Err(
                        DomainError.NotFoundError(
                            "Slice",
                            "${key.tenantId.value}:${key.entityKey.value}:${key.sliceType.name}@${key.version}",
                        ),
                    )
                }

                val rulesetRef = row.get(RULESET_REF)
                    ?: throw DomainError.StorageError("Missing required field 'ruleset_ref' in slice row")
                val (ruleSetId, ruleSetVersionStr) = if (rulesetRef.contains("@")) {
                    val parts = rulesetRef.split("@", limit = 2)
                    if (parts.size != 2) {
                        throw DomainError.StorageError("Invalid ruleset_ref format in slice row: $rulesetRef")
                    }
                    parts[0] to parts[1]
                } else {
                    throw DomainError.StorageError("Invalid ruleset_ref format in slice row (missing @): $rulesetRef")
                }

                // Tombstone 복원
                val isDeleted = row.get(IS_DELETED) ?: false
                val tombstone = if (isDeleted) {
                    val deletedAtVersion = row.get(DELETED_AT_VERSION)!!
                    val deleteReason = DeleteReason.fromDbValue(row.get(DELETE_REASON)!!)
                    Tombstone(isDeleted = true, deletedAtVersion = deletedAtVersion, deleteReason = deleteReason)
                } else {
                    null
                }

                val record = SliceRecord(
                    tenantId = TenantId(row.get(TENANT_ID)!!),
                    entityKey = EntityKey(row.get(ENTITY_KEY)!!),
                    version = row.get(SLICE_VERSION)!!,
                    sliceType = SliceType.valueOf(row.get(SLICE_TYPE)!!),
                    data = row.get(CONTENT)?.data()
                        ?: throw DomainError.StorageError("Missing required field 'content' in slice row"),
                    hash = row.get(CONTENT_HASH)!!,
                    ruleSetId = ruleSetId,
                    ruleSetVersion = SemVer.parse(ruleSetVersionStr),
                    tombstone = tombstone,
                )

                results.add(record)
            }

                SliceRepositoryPort.Result.Ok(results)
            } catch (e: DomainError) {
                SliceRepositoryPort.Result.Err(e)
            } catch (e: Exception) {
                logger.error("Failed to batch get slices", e)
                SliceRepositoryPort.Result.Err(
                    DomainError.StorageError("Failed to batch get slices: ${e.message}"),
                )
            }
        }
    }

    override suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean,
    ): SliceRepositoryPort.Result<List<SliceRecord>> = withContext(Dispatchers.IO) {
        try {
            var query = dsl.selectFrom(SLICES)
                .where(TENANT_ID.eq(tenantId.value))
                .and(ENTITY_KEY.eq(entityKey.value))
                .and(SLICE_VERSION.eq(version))

            // tombstone 필터링
            if (!includeTombstones) {
                query = query.and(IS_DELETED.eq(false))
            }

            val rows = query.fetch()
            val results = rows.map { row ->
                val rulesetRef = row.get(RULESET_REF)
                    ?: throw DomainError.StorageError("Missing required field 'ruleset_ref' in slice row")
                val (ruleSetId, ruleSetVersionStr) = if (rulesetRef.contains("@")) {
                    val parts = rulesetRef.split("@", limit = 2)
                    if (parts.size != 2) {
                        throw DomainError.StorageError("Invalid ruleset_ref format in slice row: $rulesetRef")
                    }
                    parts[0] to parts[1]
                } else {
                    throw DomainError.StorageError("Invalid ruleset_ref format in slice row (missing @): $rulesetRef")
                }

                // Tombstone 복원
                val isDeleted = row.get(IS_DELETED) ?: false
                val tombstone = if (isDeleted) {
                    val deletedAtVersion = row.get(DELETED_AT_VERSION)!!
                    val deleteReason = DeleteReason.fromDbValue(row.get(DELETE_REASON)!!)
                    Tombstone(isDeleted = true, deletedAtVersion = deletedAtVersion, deleteReason = deleteReason)
                } else {
                    null
                }

                SliceRecord(
                    tenantId = TenantId(row.get(TENANT_ID)!!),
                    entityKey = EntityKey(row.get(ENTITY_KEY)!!),
                    version = row.get(SLICE_VERSION)!!,
                    sliceType = SliceType.valueOf(row.get(SLICE_TYPE)!!),
                    data = row.get(CONTENT)?.data()
                        ?: throw DomainError.StorageError("Missing required field 'content' in slice row"),
                    hash = row.get(CONTENT_HASH)!!,
                    ruleSetId = ruleSetId,
                    ruleSetVersion = SemVer.parse(ruleSetVersionStr),
                    tombstone = tombstone,
                )
            }

            SliceRepositoryPort.Result.Ok(results)
        } catch (e: DomainError) {
            SliceRepositoryPort.Result.Err(e)
        } catch (e: Exception) {
            logger.error("Failed to get slices by version", e)
            SliceRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to get slices by version: ${e.message}"),
            )
        }
    }
}
