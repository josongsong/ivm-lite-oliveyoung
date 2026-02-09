package com.oliveyoung.ivmlite.pkg.sinks.adapters
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * AWS Personalize Sink Adapter (Stub 버전)
 * 
 * RFC-IMPL-011 Wave 6: Personalize 연동 인터페이스
 * 
 * 실제 Personalize 연동은 AWS SDK for Personalize Events 의존성 추가 필요:
 * implementation("software.amazon.awssdk:personalizeevents")
 * 
 * 현재는 InMemory로 시뮬레이션하여 테스트 가능하도록 구현.
 * 운영 배포 시 실제 AWS SDK 연동 버전으로 교체.
 */
class PersonalizeSinkAdapter(
    private val config: PersonalizeConfig
) : SinkPort {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override val sinkType: String = "personalize"
    override val healthName: String = "personalize-sink"
    
    // 테스트용 메모리 저장소
    private val storage = mutableMapOf<String, String>()
    
    override suspend fun ship(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        payload: String
    ): Result<SinkPort.ShipResult> = withContext(Dispatchers.IO) {
        val startTime = Instant.now()
        
        try {
            val itemId = buildItemId(tenantId, entityKey)
            
            logger.info("Shipping to Personalize (stub): dataset={}, itemId={}", config.datasetArn, itemId)
            
            // Stub: 메모리에 저장
            storage[itemId] = payload
            
            val latencyMs = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            Result.Ok(SinkPort.ShipResult(
                entityKey = entityKey.value,
                version = version,
                sinkId = "${config.datasetArn}/$itemId",
                latencyMs = latencyMs
            ))
        } catch (e: Exception) {
            logger.error("Personalize ship exception: {}", e.message, e)
            Result.Err(DomainError.ExternalServiceError("personalize", e.message ?: "Unknown error"))
        }
    }
    
    override suspend fun shipBatch(
        tenantId: TenantId,
        items: List<SinkPort.ShipItem>
    ): Result<SinkPort.BatchShipResult> = withContext(Dispatchers.IO) {
        val startTime = Instant.now()
        
        if (items.isEmpty()) {
            return@withContext Result.Ok(SinkPort.BatchShipResult(0, 0, emptyList(), 0))
        }
        
        try {
            // Stub: 메모리에 저장
            items.forEach { item ->
                val itemId = buildItemId(tenantId, item.entityKey)
                storage[itemId] = item.payload
            }
            
            val latencyMs = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            
            logger.info("Personalize batch complete (stub): count={}, latency={}ms", items.size, latencyMs)
            
            Result.Ok(SinkPort.BatchShipResult(
                successCount = items.size,
                failedCount = 0,
                failedKeys = emptyList(),
                totalLatencyMs = latencyMs
            ))
        } catch (e: Exception) {
            logger.error("Personalize batch exception: {}", e.message, e)
            Result.Err(DomainError.ExternalServiceError("personalize", e.message ?: "Unknown error"))
        }
    }
    
    override suspend fun delete(
        tenantId: TenantId,
        entityKey: EntityKey
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val itemId = buildItemId(tenantId, entityKey)
        storage.remove(itemId)
        logger.debug("Personalize delete (stub): itemId={}", itemId)
        Result.Ok(Unit)
    }
    
    override suspend fun healthCheck(): Boolean = true
    
    private fun buildItemId(tenantId: TenantId, entityKey: EntityKey): String {
        return "${tenantId.value}__${entityKey.value}".replace("#", "_").replace(":", "_")
    }
    
    // 테스트 헬퍼
    fun get(itemId: String): String? = storage[itemId]
    fun clear() = storage.clear()
}

/**
 * Personalize 설정
 */
data class PersonalizeConfig(
    val datasetArn: String,
    val region: String = "ap-northeast-2"
)
