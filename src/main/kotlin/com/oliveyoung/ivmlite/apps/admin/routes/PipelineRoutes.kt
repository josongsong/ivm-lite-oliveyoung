package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Pipeline Routes (파이프라인 모니터링 API)
 *
 * 데이터 흐름: Raw Data → Slice → View → Sink
 *
 * SOTA 리팩토링:
 * - Service 레이어로 비즈니스 로직 분리
 * - StatusPages로 에러 처리 (try-catch 제거)
 * - SQL Injection 방지 (Prepared Statement)
 *
 * GET /pipeline/overview: 파이프라인 전체 상태
 * GET /pipeline/rawdata: RawData 통계
 * GET /pipeline/slices: Slice 통계
 * GET /pipeline/flow/{entityKey}: 데이터 흐름 상세
 * GET /pipeline/recent: 최근 처리된 파이프라인
 * GET /pipeline/indexes: Inverted Index 통계
 */
fun Route.pipelineRoutes() {
    val pipelineService by inject<AdminPipelineService>()

    /**
     * GET /pipeline/overview
     * 파이프라인 전체 상태 개요
     */
    get("/pipeline/overview") {
        when (val result = pipelineService.getOverview()) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /pipeline/rawdata
     * RawData 상세 통계
     */
    get("/pipeline/rawdata") {
        when (val result = pipelineService.getRawDataStats()) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /pipeline/slices
     * Slice 상세 통계
     */
    get("/pipeline/slices") {
        when (val result = pipelineService.getSliceStats()) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /pipeline/flow/{entityKey}
     * 특정 엔티티의 파이프라인 흐름 추적
     */
    get("/pipeline/flow/{entityKey}") {
        val entityKey = call.parameters["entityKey"]
            ?: throw IllegalArgumentException("entityKey is required")

        when (val result = pipelineService.getEntityFlow(entityKey)) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /pipeline/recent
     * 최근 파이프라인 처리 내역
     */
    get("/pipeline/recent") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        when (val result = pipelineService.getRecentItems(limit)) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, RecentPipelineResponse(
                    items = result.value.map { it.toResponse() },
                    count = result.value.size
                ))
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /pipeline/indexes
     * Inverted Index 통계
     */
    get("/pipeline/indexes") {
        when (val result = pipelineService.getInvertedIndexStats()) {
            is AdminPipelineService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminPipelineService.Result.Err -> {
                throw result.error
            }
        }
    }
}

// ==================== Response DTOs ====================

@Serializable
data class PipelineOverviewResponse(
    val stages: List<PipelineStageResponse>,
    val rawData: RawDataStatsResponse,
    val slices: SliceStatsResponse,
    val outbox: OutboxPipelineStatsResponse,
    val timestamp: String
)

@Serializable
data class PipelineStageResponse(
    val name: String,
    val description: String,
    val count: Long,
    val status: String
)

@Serializable
data class RawDataStatsResponse(
    val total: Long,
    val byTenant: Map<String, Long>,
    val bySchema: Map<String, Long>
)

@Serializable
data class SliceStatsResponse(
    val total: Long,
    val byType: Map<String, Long>
)

@Serializable
data class SliceTypeStatsResponse(
    val type: String,
    val count: Long
)

@Serializable
data class OutboxPipelineStatsResponse(
    val pending: Long,
    val processing: Long,
    val shipped: Long,
    val failed: Long
)

@Serializable
data class RawDataDetailResponse(
    val stats: RawDataStatsResponse,
    val recent: List<RawDataItemResponse>
)

@Serializable
data class RawDataItemResponse(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val createdAt: String?
)

@Serializable
data class SliceDetailResponse(
    val stats: SliceStatsResponse,
    val byType: List<SliceTypeStatsResponse>,
    val recent: List<SliceItemResponse>
)

@Serializable
data class SliceItemResponse(
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
    val rawData: List<RawDataItemResponse>,
    val slices: List<SliceItemResponse>,
    val outbox: List<OutboxFlowItemResponse>
)

@Serializable
data class OutboxFlowItemResponse(
    val id: String,
    val aggregateType: String,
    val eventType: String,
    val status: String,
    val createdAt: String?,
    val processedAt: String?
)

@Serializable
data class RecentPipelineResponse(
    val items: List<PipelineItemResponse>,
    val count: Int
)

@Serializable
data class PipelineItemResponse(
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

// ==================== Domain → DTO 변환 ====================

private fun PipelineOverview.toResponse() = PipelineOverviewResponse(
    stages = stages.map { PipelineStageResponse(it.name, it.description, it.count, it.status) },
    rawData = rawData.toResponse(),
    slices = slices.toResponse(),
    outbox = OutboxPipelineStatsResponse(outbox.pending, outbox.processing, outbox.shipped, outbox.failed),
    timestamp = timestamp.toString()
)

private fun RawDataStats.toResponse() = RawDataStatsResponse(
    total = total,
    byTenant = byTenant,
    bySchema = bySchema
)

private fun SliceStats.toResponse() = SliceStatsResponse(
    total = total,
    byType = byType
)

private fun RawDataDetailStats.toResponse() = RawDataDetailResponse(
    stats = stats.toResponse(),
    recent = recent.map { it.toResponse() }
)

private fun SliceDetailStats.toResponse() = SliceDetailResponse(
    stats = stats.toResponse(),
    byType = byType.map { SliceTypeStatsResponse(it.type, it.count) },
    recent = recent.map { it.toResponse() }
)

private fun RawDataItem.toResponse() = RawDataItemResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    schemaId = schemaId,
    createdAt = createdAt?.toString()
)

private fun SliceItem.toResponse() = SliceItemResponse(
    tenantId = tenantId,
    entityKey = entityKey,
    version = version,
    sliceType = sliceType,
    hash = hash,
    createdAt = createdAt?.toString()
)

private fun EntityFlow.toResponse() = EntityFlowResponse(
    entityKey = entityKey,
    rawData = rawData.map { it.toResponse() },
    slices = slices.map { it.toResponse() },
    outbox = outbox.map { it.toResponse() }
)

private fun OutboxFlowItem.toResponse() = OutboxFlowItemResponse(
    id = id,
    aggregateType = aggregateType,
    eventType = eventType,
    status = status,
    createdAt = createdAt?.toString(),
    processedAt = processedAt?.toString()
)

private fun PipelineItem.toResponse() = PipelineItemResponse(
    id = id,
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = eventType,
    stage = stage,
    status = status,
    createdAt = createdAt?.toString(),
    processedAt = processedAt?.toString()
)

private fun InvertedIndexStats.toResponse() = InvertedIndexStatsResponse(
    total = total,
    byType = byType
)
