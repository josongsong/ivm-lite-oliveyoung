package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory Sink Adapter (테스트/개발용)
 * 
 * 실제 외부 연동 없이 메모리에 저장하여 테스트 용이성 제공
 */
class InMemorySinkAdapter(
    override val sinkType: String = "inmemory"
) : SinkPort {
    
    override val healthName: String = sinkType
    
    // 저장소: tenantId -> entityKey -> (version, payload)
    private val storage = ConcurrentHashMap<String, ConcurrentHashMap<String, StoredItem>>()
    
    // Ship 호출 기록 (테스트 검증용)
    private val shipHistory = mutableListOf<ShipRecord>()
    
    data class StoredItem(
        val version: Long,
        val payload: String,
        val timestamp: Instant
    )
    
    data class ShipRecord(
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val timestamp: Instant
    )
    
    override suspend fun ship(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        payload: String
    ): SinkPort.Result<SinkPort.ShipResult> {
        val startTime = Instant.now()
        
        val tenantStorage = storage.getOrPut(tenantId.value) { ConcurrentHashMap() }
        tenantStorage[entityKey.value] = StoredItem(version, payload, startTime)
        
        shipHistory.add(ShipRecord(tenantId.value, entityKey.value, version, startTime))
        
        return SinkPort.Result.Ok(SinkPort.ShipResult(
            entityKey = entityKey.value,
            version = version,
            sinkId = "$sinkType/${tenantId.value}/${entityKey.value}",
            latencyMs = 1 // 시뮬레이션
        ))
    }
    
    override suspend fun shipBatch(
        tenantId: TenantId,
        items: List<SinkPort.ShipItem>
    ): SinkPort.Result<SinkPort.BatchShipResult> {
        val startTime = Instant.now()
        
        val tenantStorage = storage.getOrPut(tenantId.value) { ConcurrentHashMap() }
        
        items.forEach { item ->
            tenantStorage[item.entityKey.value] = StoredItem(item.version, item.payload, startTime)
            shipHistory.add(ShipRecord(tenantId.value, item.entityKey.value, item.version, startTime))
        }
        
        return SinkPort.Result.Ok(SinkPort.BatchShipResult(
            successCount = items.size,
            failedCount = 0,
            failedKeys = emptyList(),
            totalLatencyMs = items.size.toLong() // 시뮬레이션
        ))
    }
    
    override suspend fun delete(
        tenantId: TenantId,
        entityKey: EntityKey
    ): SinkPort.Result<Unit> {
        storage[tenantId.value]?.remove(entityKey.value)
        return SinkPort.Result.Ok(Unit)
    }
    
    override suspend fun healthCheck(): Boolean = true
    
    // ===== 테스트 헬퍼 메서드 =====
    
    /**
     * 저장된 아이템 조회
     */
    fun get(tenantId: String, entityKey: String): StoredItem? {
        return storage[tenantId]?.get(entityKey)
    }
    
    /**
     * 모든 Ship 기록 조회
     */
    fun getShipHistory(): List<ShipRecord> = shipHistory.toList()
    
    /**
     * 특정 테넌트의 모든 아이템 조회
     */
    fun getAllByTenant(tenantId: String): Map<String, StoredItem> {
        return storage[tenantId]?.toMap() ?: emptyMap()
    }
    
    /**
     * Ship 호출 횟수
     */
    fun getShipCount(): Int = shipHistory.size
    
    /**
     * 초기화 (테스트 간 격리)
     */
    fun clear() {
        storage.clear()
        shipHistory.clear()
    }
}
