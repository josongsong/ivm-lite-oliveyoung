package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * jOOQ 기반 Inverted Index Repository (PostgreSQL)
 *
 * RFC-IMPL-010 GAP-E: 운영 환경용 영속 어댑터
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 * 
 * L12 원칙:
 * - 멱등성: 동일 key에 동일 hash면 skip
 * - 결정성: 정렬된 결과 반환
 * - fail-closed: 예외 시 에러 반환
 */
class JooqInvertedIndexRepository(
    private val dsl: DSLContext,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("jooq-inverted-index"),
) : InvertedIndexRepositoryPort, HealthCheckable {

    override val healthName: String = "inverted-index"

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = LoggerFactory.getLogger(JooqInvertedIndexRepository::class.java)

    companion object {
        private val TABLE = DSL.table("inverted_index")
        private val ID = DSL.field("id", UUID::class.java)
        private val TENANT_ID = DSL.field("tenant_id", String::class.java)
        private val INDEX_KEY = DSL.field("index_key", String::class.java)
        private val ENTITY_KEY = DSL.field("entity_key", String::class.java)
        private val SLICE_VERSION = DSL.field("slice_version", Long::class.java)
        // V009 확장 컬럼
        private val INDEX_TYPE = DSL.field("index_type", String::class.java)
        private val INDEX_VALUE = DSL.field("index_value", String::class.java)
        private val REF_VERSION = DSL.field("ref_version", Long::class.java)
        private val TARGET_ENTITY_KEY = DSL.field("target_entity_key", String::class.java)
        private val TARGET_VERSION = DSL.field("target_version", Long::class.java)
        private val SLICE_TYPE = DSL.field("slice_type", String::class.java)
        private val SLICE_HASH = DSL.field("slice_hash", String::class.java)
        private val IS_TOMBSTONE = DSL.field("is_tombstone", Boolean::class.java)
    }

    override suspend fun putAllIdempotent(entries: List<InvertedIndexEntry>): InvertedIndexRepositoryPort.Result<Unit> =
        tracer.withSpanSuspend(
            "PostgreSQL.putAllIdempotent",
            mapOf(
                "db.system" to "postgresql",
                "db.operation" to "insert",
                "entry.count" to entries.size.toString(),
            ),
        ) {
            withContext(Dispatchers.IO) {
                if (entries.isEmpty()) {
                    return@withContext InvertedIndexRepositoryPort.Result.Ok(Unit)
                }

                try {
                dsl.transaction { config ->
                    val ctx = DSL.using(config)

                    for (entry in entries) {
                        // 복합 키 생성 (레거시 호환)
                        val indexKey = "${entry.indexType}:${entry.indexValue}"

                        // 기존 레코드 조회
                        val existing = ctx.selectFrom(TABLE)
                            .where(TENANT_ID.eq(entry.tenantId.value))
                            .and(INDEX_TYPE.eq(entry.indexType))
                            .and(INDEX_VALUE.eq(entry.indexValue))
                            .and(ENTITY_KEY.eq(entry.refEntityKey.value))
                            .and(SLICE_VERSION.eq(entry.refVersion.value))
                            .fetchOne()

                        if (existing != null) {
                            // 멱등성 검사: hash가 동일해야 함
                            val existingHash = existing.get(SLICE_HASH)
                            if (existingHash != entry.sliceHash) {
                                throw DomainError.InvariantViolation(
                                    "InvertedIndex hash mismatch for ${entry.tenantId.value}:" +
                                        "${entry.refEntityKey.value}:${entry.indexType}:${entry.indexValue}",
                                )
                            }
                            // 동일 hash → skip (멱등성)
                            logger.debug(
                                "Idempotent: index already exists {}:{}:{}:{}",
                                entry.tenantId.value,
                                entry.refEntityKey.value,
                                entry.indexType,
                                entry.indexValue,
                            )
                            continue
                        }

                        // 새 레코드 삽입
                        ctx.insertInto(TABLE)
                            .set(ID, UUID.randomUUID())
                            .set(TENANT_ID, entry.tenantId.value)
                            .set(INDEX_KEY, indexKey)
                            .set(ENTITY_KEY, entry.refEntityKey.value)
                            .set(SLICE_VERSION, entry.refVersion.value)
                            // V009 확장 컬럼
                            .set(INDEX_TYPE, entry.indexType)
                            .set(INDEX_VALUE, entry.indexValue)
                            .set(REF_VERSION, entry.refVersion.value)
                            .set(TARGET_ENTITY_KEY, entry.targetEntityKey.value)
                            .set(TARGET_VERSION, entry.targetVersion.value)
                            .set(SLICE_TYPE, entry.sliceType.name)
                            .set(SLICE_HASH, entry.sliceHash)
                            .set(IS_TOMBSTONE, entry.tombstone)
                            .execute()

                        logger.debug(
                            "Inserted inverted index: {}:{}:{}:{}",
                            entry.tenantId.value,
                            entry.refEntityKey.value,
                            entry.indexType,
                            entry.indexValue,
                        )
                    }
                }

                    InvertedIndexRepositoryPort.Result.Ok(Unit)
                } catch (e: DomainError.InvariantViolation) {
                    InvertedIndexRepositoryPort.Result.Err(e)
                } catch (e: Exception) {
                    logger.error("Failed to put inverted indexes", e)
                    InvertedIndexRepositoryPort.Result.Err(
                        DomainError.StorageError("Failed to put inverted indexes: ${e.message}"),
                    )
                }
            }
        }

    override suspend fun listTargets(
        tenantId: TenantId,
        refPk: String,
        limit: Int,
    ): InvertedIndexRepositoryPort.Result<List<InvertedIndexEntry>> = withContext(Dispatchers.IO) {
        try {
            val rows = dsl.selectFrom(TABLE)
                .where(TENANT_ID.eq(tenantId.value))
                .and(
                    ENTITY_KEY.like("$refPk%")
                        .or(ENTITY_KEY.eq(refPk))
                )
                .and(IS_TOMBSTONE.eq(false).or(IS_TOMBSTONE.isNull))
                .orderBy(ENTITY_KEY, SLICE_VERSION)
                .limit(limit)
                .fetch()

            val results = rows.map { row ->
                val tenantIdValue = row.get(TENANT_ID)
                    ?: throw DomainError.StorageError("Missing required field 'tenant_id' in inverted index row")
                val entityKeyValue = row.get(ENTITY_KEY)
                    ?: throw DomainError.StorageError("Missing required field 'entity_key' in inverted index row")
                val refVersionValue = row.get(SLICE_VERSION) ?: row.get(REF_VERSION)
                    ?: throw DomainError.StorageError("Missing required field 'slice_version' or 'ref_version' in inverted index row")
                val targetEntityKeyValue = row.get(TARGET_ENTITY_KEY) ?: entityKeyValue
                val targetVersionValue = row.get(TARGET_VERSION) ?: row.get(SLICE_VERSION)
                    ?: throw DomainError.StorageError("Missing required field 'target_version' or 'slice_version' in inverted index row")
                val indexTypeValue = row.get(INDEX_TYPE) ?: parseIndexType(row.get(INDEX_KEY))
                    ?: throw DomainError.StorageError("Missing required field 'index_type' in inverted index row")
                val indexValueValue = row.get(INDEX_VALUE) ?: parseIndexValue(row.get(INDEX_KEY))
                    ?: throw DomainError.StorageError("Missing required field 'index_value' in inverted index row")
                val sliceTypeStr = row.get(SLICE_TYPE)
                    ?: throw DomainError.StorageError("Missing required field 'slice_type' in inverted index row")
                val sliceHashValue = row.get(SLICE_HASH)
                    ?: throw DomainError.StorageError("Missing required field 'slice_hash' in inverted index row")

                try {
                    InvertedIndexEntry(
                        tenantId = TenantId(tenantIdValue),
                        refEntityKey = EntityKey(entityKeyValue),
                        refVersion = VersionLong(refVersionValue),
                        targetEntityKey = EntityKey(targetEntityKeyValue),
                        targetVersion = VersionLong(targetVersionValue),
                        indexType = indexTypeValue,
                        indexValue = indexValueValue,
                        sliceType = SliceType.valueOf(sliceTypeStr),
                        sliceHash = sliceHashValue,
                        tombstone = row.get(IS_TOMBSTONE) ?: false,
                    )
                } catch (e: IllegalArgumentException) {
                    throw DomainError.StorageError("Invalid SliceType in inverted index row: $sliceTypeStr")
                } catch (e: Exception) {
                    throw DomainError.StorageError("Failed to parse inverted index entry: ${e.message}")
                }
            }

            InvertedIndexRepositoryPort.Result.Ok(results)
        } catch (e: DomainError) {
            InvertedIndexRepositoryPort.Result.Err(e)
        } catch (e: Exception) {
            logger.error("Failed to list inverted index targets", e)
            InvertedIndexRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to list inverted index targets: ${e.message}"),
            )
        }
    }

    /**
     * RFC-IMPL-012: Fanout을 위한 역참조 조회
     */
    override suspend fun queryByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
        limit: Int,
        cursor: String?,
    ): InvertedIndexRepositoryPort.Result<com.oliveyoung.ivmlite.pkg.slices.ports.FanoutQueryResult> = withContext(Dispatchers.IO) {
        try {
            // 조건 구성
            var condition = TENANT_ID.eq(tenantId.value)
                .and(INDEX_TYPE.eq(indexType))
                .and(INDEX_VALUE.eq(indexValue.lowercase()))
                .and(IS_TOMBSTONE.eq(false).or(IS_TOMBSTONE.isNull))
            
            // 커서 기반 페이지네이션
            if (cursor != null) {
                condition = condition.and(ENTITY_KEY.gt(cursor))
            }
            
            val rows = dsl.selectFrom(TABLE)
                .where(condition)
                .orderBy(ENTITY_KEY, SLICE_VERSION)
                .limit(limit + 1)  // +1 for next cursor detection
                .fetch()
            
            val hasMore = rows.size > limit
            val resultRows = if (hasMore) rows.dropLast(1) else rows
            
            val entries = resultRows.mapNotNull { row ->
                try {
                    val entityKeyValue = row.get(ENTITY_KEY) ?: return@mapNotNull null
                    val refVersionValue = row.get(SLICE_VERSION) ?: row.get(REF_VERSION) ?: return@mapNotNull null
                    
                    com.oliveyoung.ivmlite.pkg.slices.ports.FanoutTarget(
                        entityKey = EntityKey(entityKeyValue),
                        currentVersion = refVersionValue,
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.entityKey.value }
            
            val nextCursor = if (hasMore) entries.lastOrNull()?.entityKey?.value else null
            
            InvertedIndexRepositoryPort.Result.Ok(
                com.oliveyoung.ivmlite.pkg.slices.ports.FanoutQueryResult(entries, nextCursor)
            )
        } catch (e: Exception) {
            logger.error("Failed to queryByIndexType", e)
            InvertedIndexRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to queryByIndexType: ${e.message}")
            )
        }
    }

    /**
     * RFC-IMPL-012: Fanout 대상 수 조회
     */
    override suspend fun countByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
    ): InvertedIndexRepositoryPort.Result<Long> = withContext(Dispatchers.IO) {
        try {
            val count = dsl.selectCount()
                .from(TABLE)
                .where(TENANT_ID.eq(tenantId.value))
                .and(INDEX_TYPE.eq(indexType))
                .and(INDEX_VALUE.eq(indexValue.lowercase()))
                .and(IS_TOMBSTONE.eq(false).or(IS_TOMBSTONE.isNull))
                .fetchOne(0, Long::class.java) ?: 0L
            
            InvertedIndexRepositoryPort.Result.Ok(count)
        } catch (e: Exception) {
            logger.error("Failed to countByIndexType", e)
            InvertedIndexRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to countByIndexType: ${e.message}")
            )
        }
    }

    /**
     * 인덱스 타입 및 값으로 조회 (레거시 호환)
     */
    suspend fun queryByIndex(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
        limit: Int = 100,
    ): InvertedIndexRepositoryPort.Result<List<InvertedIndexEntry>> = withContext(Dispatchers.IO) {
        try {
            val rows = dsl.selectFrom(TABLE)
                .where(TENANT_ID.eq(tenantId.value))
                .and(INDEX_TYPE.eq(indexType))
                .and(INDEX_VALUE.eq(indexValue))
                .and(IS_TOMBSTONE.eq(false).or(IS_TOMBSTONE.isNull))
                .orderBy(ENTITY_KEY, SLICE_VERSION)
                .limit(limit)
                .fetch()

            val results = rows.map { row ->
                val tenantIdValue = row.get(TENANT_ID)
                    ?: throw DomainError.StorageError("Missing required field 'tenant_id' in inverted index row")
                val entityKeyValue = row.get(ENTITY_KEY)
                    ?: throw DomainError.StorageError("Missing required field 'entity_key' in inverted index row")
                val refVersionValue = row.get(SLICE_VERSION) ?: row.get(REF_VERSION)
                    ?: throw DomainError.StorageError("Missing required field 'slice_version' or 'ref_version' in inverted index row")
                val targetEntityKeyValue = row.get(TARGET_ENTITY_KEY) ?: entityKeyValue
                val targetVersionValue = row.get(TARGET_VERSION) ?: row.get(SLICE_VERSION)
                    ?: throw DomainError.StorageError("Missing required field 'target_version' or 'slice_version' in inverted index row")
                val indexTypeValue = row.get(INDEX_TYPE) ?: indexType
                val indexValueValue = row.get(INDEX_VALUE) ?: indexValue
                val sliceTypeStr = row.get(SLICE_TYPE)
                    ?: throw DomainError.StorageError("Missing required field 'slice_type' in inverted index row")
                val sliceHashValue = row.get(SLICE_HASH)
                    ?: throw DomainError.StorageError("Missing required field 'slice_hash' in inverted index row")

                try {
                    InvertedIndexEntry(
                        tenantId = TenantId(tenantIdValue),
                        refEntityKey = EntityKey(entityKeyValue),
                        refVersion = VersionLong(refVersionValue),
                        targetEntityKey = EntityKey(targetEntityKeyValue),
                        targetVersion = VersionLong(targetVersionValue),
                        indexType = indexTypeValue,
                        indexValue = indexValueValue,
                        sliceType = SliceType.valueOf(sliceTypeStr),
                        sliceHash = sliceHashValue,
                        tombstone = row.get(IS_TOMBSTONE) ?: false,
                    )
                } catch (e: IllegalArgumentException) {
                    throw DomainError.StorageError("Invalid SliceType in inverted index row: $sliceTypeStr")
                } catch (e: Exception) {
                    throw DomainError.StorageError("Failed to parse inverted index entry: ${e.message}")
                }
            }

            InvertedIndexRepositoryPort.Result.Ok(results)
        } catch (e: DomainError) {
            InvertedIndexRepositoryPort.Result.Err(e)
        } catch (e: Exception) {
            logger.error("Failed to query inverted index", e)
            InvertedIndexRepositoryPort.Result.Err(
                DomainError.StorageError("Failed to query inverted index: ${e.message}"),
            )
        }
    }

    // 레거시 index_key 파싱 (type:value 형식)
    private fun parseIndexType(indexKey: String?): String? {
        if (indexKey.isNullOrBlank()) return null
        val colonIndex = indexKey.indexOf(':')
        return if (colonIndex > 0) indexKey.substring(0, colonIndex) else indexKey
    }

    private fun parseIndexValue(indexKey: String?): String? {
        if (indexKey.isNullOrBlank()) return null
        val colonIndex = indexKey.indexOf(':')
        return if (colonIndex > 0) indexKey.substring(colonIndex + 1) else null
    }
}
