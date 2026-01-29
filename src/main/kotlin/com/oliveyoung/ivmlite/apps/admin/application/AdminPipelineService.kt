package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Instant
import java.util.UUID

/**
 * Admin Pipeline Service
 *
 * Pipeline 모니터링 및 데이터 흐름 추적 서비스.
 * Routes에서 분리된 비즈니스 로직 담당.
 *
 * 데이터 흐름: RawData → Slice → View → Sink
 */
class AdminPipelineService(
    private val dsl: DSLContext,
    private val contractRegistry: ContractRegistryPort? = null
) {
    // ==================== Result 타입 ====================

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }

    // ==================== Public API ====================

    /**
     * 파이프라인 전체 개요 조회
     */
    suspend fun getOverview(): Result<PipelineOverview> {
        return try {
            val rawDataStats = getRawDataStatsInternal()
            val sliceStats = getSliceStatsInternal()
            val outboxStats = getOutboxPipelineStatsInternal()
            val viewDefinitionCount = countContractsByKind("VIEW_DEFINITION")

            Result.Ok(
                PipelineOverview(
                    stages = listOf(
                        PipelineStage(
                            name = "RawData",
                            description = "원본 데이터 수집",
                            count = rawDataStats.total,
                            status = if (rawDataStats.total > 0) "ACTIVE" else "EMPTY"
                        ),
                        PipelineStage(
                            name = "Slicing",
                            description = "데이터 슬라이싱",
                            count = sliceStats.total,
                            status = if (sliceStats.total > 0) "ACTIVE" else "EMPTY"
                        ),
                        PipelineStage(
                            name = "View",
                            description = "뷰 정의 (실시간 조합)",
                            count = viewDefinitionCount,
                            status = "DEFINED"
                        ),
                        PipelineStage(
                            name = "Sink",
                            description = "외부 시스템 전송",
                            count = outboxStats.pending + outboxStats.processing + outboxStats.shipped,
                            status = determineOutboxStatus(outboxStats)
                        )
                    ),
                    rawData = rawDataStats,
                    slices = sliceStats,
                    outbox = outboxStats,
                    timestamp = Instant.now()
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get pipeline overview: ${e.message}"))
        }
    }

    /**
     * RawData 상세 통계 조회
     */
    suspend fun getRawDataStats(): Result<RawDataDetailStats> {
        return try {
            val stats = getRawDataStatsInternal()
            val recent = getRecentRawDataInternal(20)
            Result.Ok(RawDataDetailStats(stats = stats, recent = recent))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get rawdata stats: ${e.message}"))
        }
    }

    /**
     * Slice 상세 통계 조회
     */
    suspend fun getSliceStats(): Result<SliceDetailStats> {
        return try {
            val stats = getSliceStatsInternal()
            val byType = getSlicesByTypeInternal()
            val recent = getRecentSlicesInternal(20)
            Result.Ok(SliceDetailStats(stats = stats, byType = byType, recent = recent))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get slice stats: ${e.message}"))
        }
    }

    /**
     * 특정 엔티티의 파이프라인 흐름 추적
     *
     * SQL Injection 방지: Prepared Statement 사용
     */
    suspend fun getEntityFlow(entityKey: String): Result<EntityFlow> {
        // 입력 검증
        if (entityKey.isBlank()) {
            return Result.Err(DomainError.ValidationError("entityKey", "entityKey cannot be blank"))
        }
        if (entityKey.length > 255) {
            return Result.Err(DomainError.ValidationError("entityKey", "entityKey too long (max 255)"))
        }

        return try {
            val rawData = getRawDataByEntityKey(entityKey)
            val slices = getSlicesByEntityKey(entityKey)
            val outbox = getOutboxByEntityKey(entityKey)

            Result.Ok(
                EntityFlow(
                    entityKey = entityKey,
                    rawData = rawData,
                    slices = slices,
                    outbox = outbox
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get entity flow: ${e.message}"))
        }
    }

    /**
     * 최근 파이프라인 처리 내역 조회
     */
    suspend fun getRecentItems(limit: Int): Result<List<PipelineItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            val items = dsl.select()
                .from(DSL.table("outbox"))
                .orderBy(DSL.field("created_at").desc())
                .limit(safeLimit)
                .fetch()
                .map { record ->
                    val aggregateType = record.get("aggregatetype", String::class.java) ?: ""
                    PipelineItem(
                        id = record.get("id", UUID::class.java)?.toString() ?: "",
                        aggregateId = record.get("aggregateid", String::class.java) ?: "",
                        aggregateType = aggregateType,
                        eventType = record.get("type", String::class.java) ?: "",
                        stage = determineStage(aggregateType),
                        status = record.get("status", String::class.java) ?: "",
                        createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                        processedAt = record.get("processed_at", java.time.OffsetDateTime::class.java)?.toInstant()
                    )
                }
            Result.Ok(items)
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get recent pipeline items: ${e.message}"))
        }
    }

    /**
     * Inverted Index 통계 조회
     */
    suspend fun getInvertedIndexStats(): Result<InvertedIndexStats> {
        return try {
            val total = dsl.selectCount()
                .from(DSL.table("inverted_index"))
                .fetchOne(0, Long::class.java) ?: 0L

            val byType = dsl.select(DSL.field("index_type"), DSL.count())
                .from(DSL.table("inverted_index"))
                .groupBy(DSL.field("index_type"))
                .fetch()
                .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }

            Result.Ok(InvertedIndexStats(total = total, byType = byType))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get inverted index stats: ${e.message}"))
        }
    }

    // ==================== Private Helpers ====================

    private fun getRawDataStatsInternal(): RawDataStats {
        val total = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .fetchOne(0, Long::class.java) ?: 0L

        val byTenant = dsl.select(DSL.field("tenant_id"), DSL.count())
            .from(DSL.table("raw_data"))
            .groupBy(DSL.field("tenant_id"))
            .fetch()
            .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }

        val bySchema = dsl.select(DSL.field("schema_id"), DSL.count())
            .from(DSL.table("raw_data"))
            .groupBy(DSL.field("schema_id"))
            .fetch()
            .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }

        return RawDataStats(total = total, byTenant = byTenant, bySchema = bySchema)
    }

    private fun getSliceStatsInternal(): SliceStats {
        val total = dsl.selectCount()
            .from(DSL.table("slices"))
            .fetchOne(0, Long::class.java) ?: 0L

        val byType = dsl.select(DSL.field("slice_type"), DSL.count())
            .from(DSL.table("slices"))
            .groupBy(DSL.field("slice_type"))
            .fetch()
            .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }

        return SliceStats(total = total, byType = byType)
    }

    private fun getSlicesByTypeInternal(): List<SliceTypeStats> {
        return dsl.select(DSL.field("slice_type"), DSL.count())
            .from(DSL.table("slices"))
            .groupBy(DSL.field("slice_type"))
            .orderBy(DSL.count().desc())
            .fetch()
            .map { record ->
                SliceTypeStats(
                    type = record.get(0, String::class.java) ?: "UNKNOWN",
                    count = record.get(1, Long::class.java) ?: 0L
                )
            }
    }

    private fun getOutboxPipelineStatsInternal(): OutboxPipelineStats {
        val pending = countByStatus(OutboxStatus.PENDING)
        val processing = countByStatus(OutboxStatus.PROCESSING)
        val shipped = countByStatus(OutboxStatus.PROCESSED)
        val failed = countByStatus(OutboxStatus.FAILED)

        return OutboxPipelineStats(
            pending = pending,
            processing = processing,
            shipped = shipped,
            failed = failed
        )
    }

    private fun countByStatus(status: OutboxStatus): Long {
        return dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq(status.name))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun getRecentRawDataInternal(limit: Int): List<RawDataItem> {
        return dsl.select()
            .from(DSL.table("raw_data"))
            .orderBy(DSL.field("created_at").desc())
            .limit(limit)
            .fetch()
            .map { record ->
                RawDataItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("version", Long::class.java) ?: 0L,
                    schemaId = record.get("schema_id", String::class.java) ?: "",
                    createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()
                )
            }
    }

    private fun getRecentSlicesInternal(limit: Int): List<SliceItem> {
        return dsl.select()
            .from(DSL.table("slices"))
            .orderBy(DSL.field("created_at").desc())
            .limit(limit)
            .fetch()
            .map { record ->
                SliceItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("slice_version", Long::class.java) ?: 0L,
                    sliceType = record.get("slice_type", String::class.java) ?: "",
                    hash = record.get("content_hash", String::class.java) ?: "",
                    createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()
                )
            }
    }

    /**
     * SQL Injection 방지: Prepared Statement 사용
     */
    private fun getRawDataByEntityKey(entityKey: String): List<RawDataItem> {
        return dsl.select()
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq(DSL.param("entityKey", entityKey)))
            .orderBy(DSL.field("version").desc())
            .limit(5)
            .fetch()
            .map { record ->
                RawDataItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("version", Long::class.java) ?: 0L,
                    schemaId = record.get("schema_id", String::class.java) ?: "",
                    createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()
                )
            }
    }

    /**
     * SQL Injection 방지: Prepared Statement 사용
     */
    private fun getSlicesByEntityKey(entityKey: String): List<SliceItem> {
        return dsl.select()
            .from(DSL.table("slices"))
            .where(DSL.field("entity_key").eq(DSL.param("entityKey", entityKey)))
            .orderBy(DSL.field("slice_version").desc(), DSL.field("slice_type"))
            .limit(20)
            .fetch()
            .map { record ->
                SliceItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("slice_version", Long::class.java) ?: 0L,
                    sliceType = record.get("slice_type", String::class.java) ?: "",
                    hash = record.get("content_hash", String::class.java) ?: "",
                    createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant()
                )
            }
    }

    /**
     * SQL Injection 방지: Prepared Statement 사용
     * LIKE 검색도 안전하게 처리
     */
    private fun getOutboxByEntityKey(entityKey: String): List<OutboxFlowItem> {
        // Safe LIKE pattern: 특수문자 이스케이프 + Prepared Statement
        val safePattern = "%${escapeLikePattern(entityKey)}%"

        return dsl.select()
            .from(DSL.table("outbox"))
            .where(DSL.field("aggregateid").like(DSL.param("pattern", safePattern)))
            .orderBy(DSL.field("created_at").desc())
            .limit(10)
            .fetch()
            .map { record ->
                OutboxFlowItem(
                    id = record.get("id", UUID::class.java)?.toString() ?: "",
                    aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                    eventType = record.get("type", String::class.java) ?: "",
                    status = record.get("status", String::class.java) ?: "",
                    createdAt = record.get("created_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                    processedAt = record.get("processed_at", java.time.OffsetDateTime::class.java)?.toInstant()
                )
            }
    }

    /**
     * LIKE 패턴 특수문자 이스케이프
     */
    private fun escapeLikePattern(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    private suspend fun countContractsByKind(kind: String): Long {
        // ContractRegistry가 주입된 경우 실제 개수 조회
        return contractRegistry?.let { registry ->
            try {
                when (val result = registry.listContractRefs(kind, null)) {
                    is ContractRegistryPort.Result.Ok -> result.value.size.toLong()
                    is ContractRegistryPort.Result.Err -> 0L
                }
            } catch (e: Exception) {
                0L
            }
        } ?: 0L
    }

    private fun determineOutboxStatus(stats: OutboxPipelineStats): String {
        return when {
            stats.pending > 0 -> "PENDING"
            stats.processing > 0 -> "PROCESSING"
            stats.shipped > 0 -> "SHIPPED"
            else -> "IDLE"
        }
    }

    private fun determineStage(aggregateType: String): String {
        return when {
            aggregateType.contains("RAW") -> "INGEST"
            aggregateType.contains("SLICE") -> "SLICING"
            aggregateType.contains("SHIP") || aggregateType.contains("SINK") -> "SHIPPING"
            else -> "UNKNOWN"
        }
    }
}

// ==================== Domain Models ====================

data class PipelineOverview(
    val stages: List<PipelineStage>,
    val rawData: RawDataStats,
    val slices: SliceStats,
    val outbox: OutboxPipelineStats,
    val timestamp: Instant
)

data class PipelineStage(
    val name: String,
    val description: String,
    val count: Long,
    val status: String
)

data class RawDataStats(
    val total: Long,
    val byTenant: Map<String, Long>,
    val bySchema: Map<String, Long>
)

data class SliceStats(
    val total: Long,
    val byType: Map<String, Long>
)

data class SliceTypeStats(
    val type: String,
    val count: Long
)

data class OutboxPipelineStats(
    val pending: Long,
    val processing: Long,
    val shipped: Long,
    val failed: Long
)

data class RawDataDetailStats(
    val stats: RawDataStats,
    val recent: List<RawDataItem>
)

data class SliceDetailStats(
    val stats: SliceStats,
    val byType: List<SliceTypeStats>,
    val recent: List<SliceItem>
)

data class RawDataItem(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val createdAt: Instant?
)

data class SliceItem(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val sliceType: String,
    val hash: String,
    val createdAt: Instant?
)

data class EntityFlow(
    val entityKey: String,
    val rawData: List<RawDataItem>,
    val slices: List<SliceItem>,
    val outbox: List<OutboxFlowItem>
)

data class OutboxFlowItem(
    val id: String,
    val aggregateType: String,
    val eventType: String,
    val status: String,
    val createdAt: Instant?,
    val processedAt: Instant?
)

data class PipelineItem(
    val id: String,
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    val stage: String,
    val status: String,
    val createdAt: Instant?,
    val processedAt: Instant?
)

data class InvertedIndexStats(
    val total: Long,
    val byType: Map<String, Long>
)
