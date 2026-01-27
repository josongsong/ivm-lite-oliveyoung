package com.oliveyoung.ivmlite.pkg.sinks.ports

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable

/**
 * Sink Port - 외부 시스템으로 데이터 전파
 * 
 * RFC-IMPL-011 Wave 6: 실제 Sink 연동
 * 
 * 지원 Sink:
 * - OpenSearch: 검색 인덱스
 * - Personalize: 추천 데이터셋
 */
interface SinkPort : HealthCheckable {
    
    /**
     * Sink 타입 이름
     */
    val sinkType: String
    
    /**
     * 단일 엔티티 Ship
     */
    suspend fun ship(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        payload: String
    ): Result<ShipResult>
    
    /**
     * 배치 Ship (대량 처리 최적화)
     */
    suspend fun shipBatch(
        tenantId: TenantId,
        items: List<ShipItem>
    ): Result<BatchShipResult>
    
    /**
     * 엔티티 삭제 (Tombstone 처리)
     */
    suspend fun delete(
        tenantId: TenantId,
        entityKey: EntityKey
    ): Result<Unit>
    
    // ===== Result Types =====
    
    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val error: DomainError) : Result<Nothing>
    }
    
    data class ShipItem(
        val entityKey: EntityKey,
        val version: Long,
        val payload: String
    )
    
    data class ShipResult(
        val entityKey: String,
        val version: Long,
        val sinkId: String,
        val latencyMs: Long
    )
    
    data class BatchShipResult(
        val successCount: Int,
        val failedCount: Int,
        val failedKeys: List<String>,
        val totalLatencyMs: Long
    )
}
