package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceMerger
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.slf4j.LoggerFactory

/**
 * Ship Workflow - Slice 데이터를 외부 Sink로 전파
 * 
 * RFC-IMPL-011 Wave 6: 실제 Ship 구현
 * 
 * 플로우:
 * 1. Slice 조회 (최신 버전 또는 특정 버전)
 * 2. Sink별 payload 변환
 * 3. Sink로 Ship
 * 4. 결과 반환
 */
class ShipWorkflow(
    private val sliceRepository: SliceRepositoryPort,
    private val sinks: Map<String, SinkPort>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /**
     * 특정 Sink로 Ship 실행
     */
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        sinkType: String
    ): Result<ShipResult> {
        logger.info("ShipWorkflow.execute: tenant={}, entity={}, version={}, sink={}",
            tenantId.value, entityKey.value, version, sinkType)
        
        // 1. Sink 찾기
        val sink = sinks[sinkType]
            ?: return Result.Err(DomainError.NotFoundError("sink", sinkType))
        
        // 2. Slice 조회
        val slices = when (val result = sliceRepository.getByVersion(tenantId, entityKey, version)) {
            is SliceRepositoryPort.Result.Ok -> result.value
            is SliceRepositoryPort.Result.Err -> {
                logger.error("Failed to get slices: {}", result.error)
                return Result.Err(result.error)
            }
        }
        
        if (slices.isEmpty()) {
            logger.warn("No slices found for entity: {}", entityKey.value)
            return Result.Err(DomainError.NotFoundError("slice", entityKey.value))
        }
        
        // 3. Slice들을 하나의 문서로 병합 (SOLID: SliceMerger로 분리)
        val mergedPayload = SliceMerger.merge(slices)
        
        // 4. Sink로 Ship
        return when (val shipResult = sink.ship(tenantId, entityKey, version, mergedPayload)) {
            is SinkPort.Result.Ok -> {
                logger.info("Ship success: sink={}, latency={}ms", sinkType, shipResult.value.latencyMs)
                Result.Ok(ShipResult(
                    entityKey = entityKey.value,
                    version = version,
                    sinkType = sinkType,
                    sinkId = shipResult.value.sinkId,
                    latencyMs = shipResult.value.latencyMs
                ))
            }
            is SinkPort.Result.Err -> {
                logger.error("Ship failed: sink={}, error={}", sinkType, shipResult.error)
                Result.Err(shipResult.error)
            }
        }
    }
    
    /**
     * 여러 Sink로 동시 Ship
     */
    suspend fun executeToMultipleSinks(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        sinkTypes: List<String>
    ): Result<MultiShipResult> {
        logger.info("ShipWorkflow.executeToMultipleSinks: tenant={}, entity={}, sinks={}",
            tenantId.value, entityKey.value, sinkTypes)
        
        // 1. Slice 조회 (한 번만)
        val slices = when (val result = sliceRepository.getByVersion(tenantId, entityKey, version)) {
            is SliceRepositoryPort.Result.Ok -> result.value
            is SliceRepositoryPort.Result.Err -> {
                return Result.Err(result.error)
            }
        }
        
        if (slices.isEmpty()) {
            return Result.Err(DomainError.NotFoundError("slice", entityKey.value))
        }
        
        val mergedPayload = SliceMerger.merge(slices)
        
        // 2. 각 Sink로 Ship
        val results = mutableListOf<SingleSinkResult>()
        val errors = mutableListOf<String>()
        
        sinkTypes.forEach { sinkType ->
            val sink = sinks[sinkType]
            if (sink == null) {
                errors.add("Sink not found: $sinkType")
                return@forEach
            }
            
            when (val shipResult = sink.ship(tenantId, entityKey, version, mergedPayload)) {
                is SinkPort.Result.Ok -> {
                    results.add(SingleSinkResult(
                        sinkType = sinkType,
                        success = true,
                        sinkId = shipResult.value.sinkId,
                        latencyMs = shipResult.value.latencyMs
                    ))
                }
                is SinkPort.Result.Err -> {
                    results.add(SingleSinkResult(
                        sinkType = sinkType,
                        success = false,
                        error = shipResult.error.toString()
                    ))
                }
            }
        }
        
        val successCount = results.count { it.success }
        val failedCount = results.count { !it.success }
        
        return Result.Ok(MultiShipResult(
            entityKey = entityKey.value,
            version = version,
            sinkResults = results,
            successCount = successCount,
            failedCount = failedCount + errors.size,
            errors = errors
        ))
    }
    
    /**
     * 배치 Ship (여러 엔티티를 하나의 Sink로)
     */
    suspend fun executeBatch(
        tenantId: TenantId,
        entities: List<EntityVersion>,
        sinkType: String
    ): Result<BatchShipResult> {
        logger.info("ShipWorkflow.executeBatch: tenant={}, count={}, sink={}",
            tenantId.value, entities.size, sinkType)
        
        val sink = sinks[sinkType]
            ?: return Result.Err(DomainError.NotFoundError("sink", sinkType))
        
        // 각 엔티티의 Slice 조회 및 변환
        val items = entities.mapNotNull { entity ->
            val slices = when (val result = sliceRepository.getByVersion(tenantId, entity.entityKey, entity.version)) {
                is SliceRepositoryPort.Result.Ok -> result.value
                is SliceRepositoryPort.Result.Err -> {
                    logger.warn("Failed to get slices for {}: {}", entity.entityKey.value, result.error)
                    return@mapNotNull null
                }
            }
            
            if (slices.isEmpty()) {
                return@mapNotNull null
            }
            
            SinkPort.ShipItem(
                entityKey = entity.entityKey,
                version = entity.version,
                payload = SliceMerger.merge(slices)
            )
        }
        
        if (items.isEmpty()) {
            return Result.Ok(BatchShipResult(
                sinkType = sinkType,
                requestedCount = entities.size,
                successCount = 0,
                failedCount = entities.size,
                skippedCount = entities.size
            ))
        }
        
        // 배치 Ship 실행
        return when (val result = sink.shipBatch(tenantId, items)) {
            is SinkPort.Result.Ok -> {
                Result.Ok(BatchShipResult(
                    sinkType = sinkType,
                    requestedCount = entities.size,
                    successCount = result.value.successCount,
                    failedCount = result.value.failedCount,
                    skippedCount = entities.size - items.size,
                    totalLatencyMs = result.value.totalLatencyMs
                ))
            }
            is SinkPort.Result.Err -> {
                Result.Err(result.error)
            }
        }
    }
    
    // ===== Result Types =====
    
    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val error: DomainError) : Result<Nothing>
    }
    
    data class ShipResult(
        val entityKey: String,
        val version: Long,
        val sinkType: String,
        val sinkId: String,
        val latencyMs: Long
    )
    
    data class MultiShipResult(
        val entityKey: String,
        val version: Long,
        val sinkResults: List<SingleSinkResult>,
        val successCount: Int,
        val failedCount: Int,
        val errors: List<String>
    )
    
    data class SingleSinkResult(
        val sinkType: String,
        val success: Boolean,
        val sinkId: String? = null,
        val latencyMs: Long = 0,
        val error: String? = null
    )
    
    data class BatchShipResult(
        val sinkType: String,
        val requestedCount: Int,
        val successCount: Int,
        val failedCount: Int,
        val skippedCount: Int = 0,
        val totalLatencyMs: Long = 0
    )
    
    data class EntityVersion(
        val entityKey: EntityKey,
        val version: Long
    )
}
