package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import java.time.Instant

/**
 * Admin Pipeline Service (SinkEvent 기반)
 *
 * RawData/Slice: DynamoDB (ExplorerRepositoryPort)
 * Sink: SinkEvent(DynamoDB). Outbox 제거됨.
 */
class AdminPipelineService(
    private val contractRegistry: ContractRegistryPort? = null,
    private val explorerRepo: ExplorerRepositoryPort? = null,
    private val sinkEventRepo: SinkEventRepositoryPort? = null
) {
    // ==================== Public API ====================

    /**
     * 파이프라인 전체 개요 조회
     */
    suspend fun getOverview(tenantId: String = "default"): Result<PipelineOverview> {
        return try {
            val rawDataStats = getRawDataStatsInternal(tenantId)
            val sliceStats = getSliceStatsInternal(tenantId)
            val sinkEventStats = getSinkEventPipelineStatsInternal()
            val viewDefinitionCount = countContractsByKind(ContractKind.VIEW_DEFINITION)

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
                            description = "외부 시스템 전송 (DynamoDB Streams)",
                            count = sinkEventStats.pending + sinkEventStats.processing + sinkEventStats.shipped,
                            status = determineSinkEventStatus(sinkEventStats)
                        )
                    ),
                    rawData = rawDataStats,
                    slices = sliceStats,
                    sinkEvent = sinkEventStats,
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
    suspend fun getRawDataStats(tenantId: String = "default"): Result<RawDataDetailStats> {
        return try {
            val stats = getRawDataStatsInternal(tenantId)
            val recent = getRecentRawDataInternal(tenantId, 20)
            Result.Ok(RawDataDetailStats(stats = stats, recent = recent))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get rawdata stats: ${e.message}"))
        }
    }

    /**
     * Slice 상세 통계 조회
     */
    suspend fun getSliceStats(tenantId: String = "default"): Result<SliceDetailStats> {
        return try {
            val stats = getSliceStatsInternal(tenantId)
            val byType = getSlicesByTypeInternal(tenantId)
            val recent = getRecentSlicesInternal(tenantId, 20)
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
    suspend fun getEntityFlow(entityKey: String, tenantId: String = "default"): Result<EntityFlow> {
        // 입력 검증
        if (entityKey.isBlank()) {
            return Result.Err(DomainError.ValidationError("entityKey", "entityKey cannot be blank"))
        }
        if (entityKey.length > 255) {
            return Result.Err(DomainError.ValidationError("entityKey", "entityKey too long (max 255)"))
        }

        return try {
            val rawData = getRawDataByEntityKey(tenantId, entityKey)
            val slices = getSlicesByEntityKey(tenantId, entityKey)
            val sinkEvents = getSinkEventsByEntityKey(entityKey)

            Result.Ok(
                EntityFlow(
                    entityKey = entityKey,
                    rawData = rawData,
                    slices = slices,
                    sinkEvent = sinkEvents
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get entity flow: ${e.message}"))
        }
    }

    /**
     * 최근 파이프라인 처리 내역 조회 (SinkEvent 기반)
     */
    suspend fun getRecentItems(limit: Int): Result<List<PipelineItem>> {
        val safeLimit = limit.coerceIn(1, 200)
        return try {
            val items = mutableListOf<PipelineItem>()
            sinkEventRepo?.let { repo ->
                listOf("PENDING", "PROCESSING", "COMPLETED").forEach { status ->
                    (repo.findByStatus(status, safeLimit / 3) as? Result.Ok)?.value?.forEach { event ->
                        items.add(
                            PipelineItem(
                                id = event.id.toString(),
                                aggregateId = event.entityKey,
                                aggregateType = "SINK_EVENT",
                                eventType = event.viewType,
                                stage = "SHIPPING",
                                status = event.status.name,
                                createdAt = event.createdAt,
                                processedAt = event.processedAt
                            )
                        )
                    }
                }
            }
            Result.Ok(items.sortedByDescending { it.createdAt?.toEpochMilli() ?: 0L }.take(safeLimit))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get recent pipeline items: ${e.message}"))
        }
    }

    /**
     * Inverted Index 통계 조회 (DynamoDB에서 조회)
     */
    suspend fun getInvertedIndexStats(tenantId: String = "default"): Result<InvertedIndexStats> {
        return try {
            val stats = when (val result = explorerRepo?.getInvertedIndexStats(TenantId(tenantId))) {
                null -> InvertedIndexStats(total = 0L, byType = emptyMap())
                else -> result.fold(
                    { raise -> return Result.Err(raise) },
                    { s -> InvertedIndexStats(total = s.total, byType = s.byType) }
                )
            }
            Result.Ok(stats)
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get inverted index stats: ${e.message}"))
        }
    }

    // ==================== Private Helpers ====================

    private suspend fun getRawDataStatsInternal(tenantId: String): RawDataStats {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getRawDataStats(tenant)?.fold(
            { _ -> RawDataStats(total = 0L, byTenant = emptyMap(), bySchema = emptyMap()) },
            { s -> RawDataStats(total = s.total, byTenant = s.byTenant, bySchema = s.bySchema) }
        ) ?: RawDataStats(total = 0L, byTenant = emptyMap(), bySchema = emptyMap())
    }

    private suspend fun getSliceStatsInternal(tenantId: String): SliceStats {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getSliceStats(tenant)?.fold(
            { _ -> SliceStats(total = 0L, byType = emptyMap()) },
            { s -> SliceStats(total = s.total, byType = s.byType) }
        ) ?: SliceStats(total = 0L, byType = emptyMap())
    }

    private suspend fun getSlicesByTypeInternal(tenantId: String): List<SliceTypeStats> {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getSlicesByTypeStats(tenant)?.fold(
            { _ -> emptyList() },
            { items -> items.map { SliceTypeStats(type = it.type, count = it.count) } }
        ) ?: emptyList()
    }

    private suspend fun getSinkEventPipelineStatsInternal(): SinkEventPipelineStats {
        return if (sinkEventRepo != null) {
            val pending = (sinkEventRepo.findByStatus("PENDING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val processing = (sinkEventRepo.findByStatus("PROCESSING", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val shipped = (sinkEventRepo.findByStatus("COMPLETED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            val failed = (sinkEventRepo.findByStatus("FAILED", 10000) as? Result.Ok)?.value?.size?.toLong() ?: 0L
            SinkEventPipelineStats(pending, processing, shipped, failed)
        } else {
            SinkEventPipelineStats(0, 0, 0, 0)
        }
    }

    private suspend fun getRecentRawDataInternal(tenantId: String, limit: Int): List<RawDataItem> {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getRecentRawData(tenant, limit)?.fold(
            { _ -> emptyList() },
            { items -> items.map { RawDataItem(tenantId = it.tenantId, entityKey = it.entityKey, version = it.version, schemaId = it.schemaId, createdAt = it.createdAt?.let { s -> java.time.Instant.parse(s) }) } }
        ) ?: emptyList()
    }

    private suspend fun getRecentSlicesInternal(tenantId: String, limit: Int): List<SliceItem> {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getRecentSlices(tenant, limit)?.fold(
            { _ -> emptyList() },
            { items -> items.map { SliceItem(tenantId = it.tenantId, entityKey = it.entityKey, version = it.version, sliceType = it.sliceType, hash = it.hash, createdAt = it.createdAt?.let { s -> java.time.Instant.parse(s) }) } }
        ) ?: emptyList()
    }

    private suspend fun getRawDataByEntityKey(tenantId: String, entityKey: String): List<RawDataItem> {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getRawDataByEntityKey(tenant, entityKey, 5)?.fold(
            { _ -> emptyList() },
            { items -> items.map { RawDataItem(tenantId = it.tenantId, entityKey = it.entityKey, version = it.version, schemaId = it.schemaId, createdAt = it.createdAt?.let { s -> java.time.Instant.parse(s) }) } }
        ) ?: emptyList()
    }

    private suspend fun getSlicesByEntityKey(tenantId: String, entityKey: String): List<SliceItem> {
        val tenant = TenantId(tenantId)
        return explorerRepo?.getSlicesByEntityKey(tenant, entityKey, 20)?.fold(
            { _ -> emptyList() },
            { items -> items.map { SliceItem(tenantId = it.tenantId, entityKey = it.entityKey, version = it.version, sliceType = it.sliceType, hash = it.hash, createdAt = it.createdAt?.let { s -> java.time.Instant.parse(s) }) } }
        ) ?: emptyList()
    }

    private suspend fun getSinkEventsByEntityKey(entityKey: String): List<SinkEventFlowItem> {
        // SinkEvent: entityKey 필터링 (findByStatus로 조회 후 필터)
        val items = mutableListOf<SinkEventFlowItem>()
        sinkEventRepo?.let { repo ->
            listOf("PENDING", "PROCESSING", "COMPLETED", "FAILED").forEach { status ->
                (repo.findByStatus(status, 100) as? Result.Ok)?.value
                    ?.filter { it.entityKey.contains(entityKey) }
                    ?.take(10)
                    ?.forEach { event ->
                        items.add(
                            SinkEventFlowItem(
                                id = event.id.toString(),
                                aggregateType = "SINK_EVENT",
                                eventType = event.viewType,
                                status = event.status.name,
                                createdAt = event.createdAt,
                                processedAt = event.processedAt
                            )
                        )
                    }
            }
        }
        return items.sortedByDescending { it.createdAt?.toEpochMilli() ?: 0L }.take(10)
    }

    private suspend fun countContractsByKind(kind: ContractKind): Long {
        // ContractRegistry가 주입된 경우 실제 개수 조회
        return contractRegistry?.let { registry ->
            try {
                when (val result = registry.listContractRefs(kind, null)) {
                    is Result.Ok -> result.value.size.toLong()
                    is Result.Err -> 0L
                }
            } catch (e: Exception) {
                0L
            }
        } ?: 0L
    }

    private fun determineSinkEventStatus(stats: SinkEventPipelineStats): String {
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
    val sinkEvent: SinkEventPipelineStats,
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

data class SinkEventPipelineStats(
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
    val sinkEvent: List<SinkEventFlowItem>
)

data class SinkEventFlowItem(
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
