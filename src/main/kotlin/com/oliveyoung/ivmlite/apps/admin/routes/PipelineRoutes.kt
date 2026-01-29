package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Pipeline Routes (파이프라인 모니터링 API)
 *
 * 데이터 흐름: Raw Data → Slice → View → Sink
 * 
 * GET /pipeline/overview: 파이프라인 전체 상태
 * GET /pipeline/rawdata: RawData 통계
 * GET /pipeline/slices: Slice 통계
 * GET /pipeline/flow: 데이터 흐름 상세 (특정 엔티티)
 * GET /pipeline/recent: 최근 처리된 파이프라인
 */
fun Route.pipelineRoutes() {
    val dsl by inject<DSLContext>()

    /**
     * GET /pipeline/overview
     * 파이프라인 전체 상태 개요
     */
    get("/pipeline/overview") {
        try {
            val rawDataStats = getRawDataStats(dsl)
            val sliceStats = getSliceStats(dsl)
            val outboxStats = getOutboxPipelineStats(dsl)
            
            // View Definition 개수 (실제 정의된 뷰 타입 수)
            val viewDefinitionCount = 6L // contracts/v1/view-*.yaml 파일 개수
            
            call.respond(HttpStatusCode.OK, PipelineOverviewResponse(
                stages = listOf(
                    PipelineStage(
                        name = "RawData",
                        description = "원본 데이터 수집",
                        count = rawDataStats.total,
                        status = if (rawDataStats.total > 0) "ACTIVE" else "EMPTY",
                        icon = "📥"
                    ),
                    PipelineStage(
                        name = "Slicing",
                        description = "데이터 슬라이싱",
                        count = sliceStats.total,
                        status = if (sliceStats.total > 0) "ACTIVE" else "EMPTY",
                        icon = "✂️"
                    ),
                    PipelineStage(
                        name = "View",
                        description = "뷰 정의 (실시간 조합)",
                        count = viewDefinitionCount,
                        status = "DEFINED",
                        icon = "👁️"
                    ),
                    PipelineStage(
                        name = "Sink",
                        description = "외부 시스템 전송",
                        count = outboxStats.pending + outboxStats.processing + outboxStats.shipped,
                        status = when {
                            outboxStats.pending > 0 -> "PENDING"
                            outboxStats.processing > 0 -> "PROCESSING"
                            outboxStats.shipped > 0 -> "SHIPPED"
                            else -> "IDLE"
                        },
                        icon = "🚀"
                    )
                ),
                rawData = rawDataStats,
                slices = sliceStats,
                outbox = outboxStats,
                timestamp = Instant.now().toString()
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get pipeline overview", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "PIPELINE_ERROR", message = "Failed to get pipeline overview: ${e.message}")
            )
        }
    }

    /**
     * GET /pipeline/rawdata
     * RawData 상세 통계
     */
    get("/pipeline/rawdata") {
        try {
            val stats = getRawDataStats(dsl)
            val recentItems = getRecentRawData(dsl, 20)
            
            call.respond(HttpStatusCode.OK, RawDataDetailResponse(
                stats = stats,
                recent = recentItems
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get rawdata stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RAWDATA_ERROR", message = "Failed to get rawdata stats: ${e.message}")
            )
        }
    }

    /**
     * GET /pipeline/slices
     * Slice 상세 통계
     */
    get("/pipeline/slices") {
        try {
            val stats = getSliceStats(dsl)
            val byType = getSlicesByType(dsl)
            val recentItems = getRecentSlices(dsl, 20)
            
            call.respond(HttpStatusCode.OK, SliceDetailResponse(
                stats = stats,
                byType = byType,
                recent = recentItems
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get slice stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "SLICE_ERROR", message = "Failed to get slice stats: ${e.message}")
            )
        }
    }

    /**
     * GET /pipeline/flow/{entityKey}
     * 특정 엔티티의 파이프라인 흐름 추적
     */
    get("/pipeline/flow/{entityKey}") {
        try {
            val entityKey = call.parameters["entityKey"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "MISSING_KEY", message = "entityKey is required"))
                return@get
            }
            
            val flow = getEntityFlow(dsl, entityKey)
            call.respond(HttpStatusCode.OK, flow)
        } catch (e: Exception) {
            call.application.log.error("Failed to get entity flow", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "FLOW_ERROR", message = "Failed to get entity flow: ${e.message}")
            )
        }
    }

    /**
     * GET /pipeline/recent
     * 최근 파이프라인 처리 내역
     */
    get("/pipeline/recent") {
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val items = getRecentPipelineItems(dsl, limit)
            
            call.respond(HttpStatusCode.OK, RecentPipelineResponse(
                items = items,
                count = items.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get recent pipeline items", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RECENT_ERROR", message = "Failed to get recent pipeline items: ${e.message}")
            )
        }
    }

    /**
     * GET /pipeline/indexes
     * Inverted Index 통계
     */
    get("/pipeline/indexes") {
        try {
            val stats = getInvertedIndexStats(dsl)
            call.respond(HttpStatusCode.OK, stats)
        } catch (e: Exception) {
            call.application.log.error("Failed to get index stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INDEX_ERROR", message = "Failed to get index stats: ${e.message}")
            )
        }
    }
}

// ==================== Helper Functions ====================

private fun getRawDataStats(dsl: DSLContext): RawDataStats {
    return try {
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
        
        RawDataStats(total = total, byTenant = byTenant, bySchema = bySchema)
    } catch (e: Exception) {
        RawDataStats(total = 0L, byTenant = emptyMap(), bySchema = emptyMap())
    }
}

private fun getSliceStats(dsl: DSLContext): SliceStats {
    return try {
        val total = dsl.selectCount()
            .from(DSL.table("slices"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        val byType = dsl.select(DSL.field("slice_type"), DSL.count())
            .from(DSL.table("slices"))
            .groupBy(DSL.field("slice_type"))
            .fetch()
            .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }
        
        SliceStats(total = total, byType = byType)
    } catch (e: Exception) {
        SliceStats(total = 0L, byType = emptyMap())
    }
}

private fun getSlicesByType(dsl: DSLContext): List<SliceTypeStats> {
    return try {
        dsl.select(
            DSL.field("slice_type"),
            DSL.count()
        )
            .from(DSL.table("slices"))
            .groupBy(DSL.field("slice_type"))
            .orderBy(DSL.count().desc())
            .fetch()
            .map { record ->
                SliceTypeStats(
                    type = record.get(0, String::class.java) ?: "UNKNOWN",
                    count = record.get(1, Long::class.java) ?: 0L,
                    lastCreated = null
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun getOutboxPipelineStats(dsl: DSLContext): OutboxPipelineStats {
    return try {
        val pending = dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq("PENDING"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        val processing = dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq("PROCESSING"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        val shipped = dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq("PROCESSED"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        val failed = dsl.selectCount()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq("FAILED"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        OutboxPipelineStats(pending = pending, processing = processing, shipped = shipped, failed = failed)
    } catch (e: Exception) {
        OutboxPipelineStats(pending = 0L, processing = 0L, shipped = 0L, failed = 0L)
    }
}

private fun getRecentRawData(dsl: DSLContext, limit: Int): List<RawDataItem> {
    return try {
        dsl.select()
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
                    createdAt = null
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun getRecentSlices(dsl: DSLContext, limit: Int): List<SliceItem> {
    return try {
        dsl.select()
            .from(DSL.table("slices"))
            .orderBy(DSL.field("created_at").desc())
            .limit(limit)
            .fetch()
            .map { record ->
                SliceItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("version", Long::class.java) ?: 0L,
                    sliceType = record.get("slice_type", String::class.java) ?: "",
                    hash = record.get("hash", String::class.java) ?: "",
                    createdAt = null
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun getEntityFlow(dsl: DSLContext, entityKey: String): EntityFlowResponse {
    val rawData = try {
        dsl.select()
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq(entityKey))
            .orderBy(DSL.field("version").desc())
            .limit(5)
            .fetch()
            .map { record ->
                RawDataItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("version", Long::class.java) ?: 0L,
                    schemaId = record.get("schema_id", String::class.java) ?: "",
                    createdAt = null
                )
            }
    } catch (e: Exception) { emptyList() }
    
    val slices = try {
        dsl.select()
            .from(DSL.table("slices"))
            .where(DSL.field("entity_key").eq(entityKey))
            .orderBy(DSL.field("version").desc(), DSL.field("slice_type"))
            .limit(20)
            .fetch()
            .map { record ->
                SliceItem(
                    tenantId = record.get("tenant_id", String::class.java) ?: "",
                    entityKey = record.get("entity_key", String::class.java) ?: "",
                    version = record.get("version", Long::class.java) ?: 0L,
                    sliceType = record.get("slice_type", String::class.java) ?: "",
                    hash = record.get("hash", String::class.java) ?: "",
                    createdAt = null
                )
            }
    } catch (e: Exception) { emptyList() }
    
    val outbox = try {
        dsl.select()
            .from(DSL.table("outbox"))
            .where(DSL.field("aggregateid").like("%$entityKey%"))
            .orderBy(DSL.field("created_at").desc())
            .limit(10)
            .fetch()
            .map { record ->
                OutboxFlowItem(
                    id = record.get("id", UUID::class.java)?.toString() ?: "",
                    aggregateType = record.get("aggregatetype", String::class.java) ?: "",
                    eventType = record.get("type", String::class.java) ?: "",
                    status = record.get("status", String::class.java) ?: "",
                    createdAt = null,
                    processedAt = null
                )
            }
    } catch (e: Exception) { emptyList() }
    
    return EntityFlowResponse(entityKey = entityKey, rawData = rawData, slices = slices, outbox = outbox)
}

private fun getRecentPipelineItems(dsl: DSLContext, limit: Int): List<PipelineItem> {
    return try {
        dsl.select()
            .from(DSL.table("outbox"))
            .orderBy(DSL.field("created_at").desc())
            .limit(limit)
            .fetch()
            .map { record ->
                val aggregateType = record.get("aggregatetype", String::class.java) ?: ""
                val stage = when {
                    aggregateType.contains("RAW") -> "INGEST"
                    aggregateType.contains("SLICE") -> "SLICING"
                    aggregateType.contains("SHIP") || aggregateType.contains("SINK") -> "SHIPPING"
                    else -> "UNKNOWN"
                }
                
                PipelineItem(
                    id = record.get("id", UUID::class.java)?.toString() ?: "",
                    aggregateId = record.get("aggregateid", String::class.java) ?: "",
                    aggregateType = aggregateType,
                    eventType = record.get("type", String::class.java) ?: "",
                    stage = stage,
                    status = record.get("status", String::class.java) ?: "",
                    createdAt = null,
                    processedAt = null
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun getInvertedIndexStats(dsl: DSLContext): InvertedIndexStatsResponse {
    return try {
        val total = dsl.selectCount()
            .from(DSL.table("inverted_index"))
            .fetchOne(0, Long::class.java) ?: 0L
        
        val byType = dsl.select(DSL.field("index_type"), DSL.count())
            .from(DSL.table("inverted_index"))
            .groupBy(DSL.field("index_type"))
            .fetch()
            .associate { (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L) }
        
        InvertedIndexStatsResponse(total = total, byType = byType)
    } catch (e: Exception) {
        InvertedIndexStatsResponse(total = 0L, byType = emptyMap())
    }
}

// ==================== Response DTOs ====================

@Serializable
data class PipelineOverviewResponse(
    val stages: List<PipelineStage>,
    val rawData: RawDataStats,
    val slices: SliceStats,
    val outbox: OutboxPipelineStats,
    val timestamp: String
)

@Serializable
data class PipelineStage(
    val name: String,
    val description: String,
    val count: Long,
    val status: String,
    val icon: String
)

@Serializable
data class RawDataStats(
    val total: Long,
    val byTenant: Map<String, Long>,
    val bySchema: Map<String, Long>
)

@Serializable
data class SliceStats(
    val total: Long,
    val byType: Map<String, Long>
)

@Serializable
data class SliceTypeStats(
    val type: String,
    val count: Long,
    val lastCreated: String?
)

@Serializable
data class OutboxPipelineStats(
    val pending: Long,
    val processing: Long,
    val shipped: Long,
    val failed: Long
)

@Serializable
data class RawDataDetailResponse(
    val stats: RawDataStats,
    val recent: List<RawDataItem>
)

@Serializable
data class RawDataItem(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val createdAt: String?
)

@Serializable
data class SliceDetailResponse(
    val stats: SliceStats,
    val byType: List<SliceTypeStats>,
    val recent: List<SliceItem>
)

@Serializable
data class SliceItem(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val sliceType: String,
    val hash: String,
    val createdAt: String?
)

@Serializable
data class EntityFlowResponse(
    val entityKey: String,
    val rawData: List<RawDataItem>,
    val slices: List<SliceItem>,
    val outbox: List<OutboxFlowItem>
)

@Serializable
data class OutboxFlowItem(
    val id: String,
    val aggregateType: String,
    val eventType: String,
    val status: String,
    val createdAt: String?,
    val processedAt: String?
)

@Serializable
data class RecentPipelineResponse(
    val items: List<PipelineItem>,
    val count: Int
)

@Serializable
data class PipelineItem(
    val id: String,
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    val stage: String,
    val status: String,
    val createdAt: String?,
    val processedAt: String?
)

@Serializable
data class InvertedIndexStatsResponse(
    val total: Long,
    val byType: Map<String, Long>
)
