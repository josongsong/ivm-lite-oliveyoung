package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Ship 이벤트 핸들러
 * 
 * RFC-IMPL-011 Wave 6: Outbox에서 Ship 이벤트를 처리
 * 
 * 이벤트 타입:
 * - ShipRequested: 비동기 Ship 요청
 * - CompileRequested: 비동기 Compile 요청
 */
class ShipEventHandler(
    private val shipWorkflow: ShipWorkflow,
    private val slicingWorkflow: SlicingWorkflow,
    private val deployJobRepository: DeployJobRepositoryPort? = null
) : OutboxPollingWorker.EventHandler {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val json = Json {
        ignoreUnknownKeys = true
    }
    
    override suspend fun handleSliceEvent(entry: OutboxEntry) {
        when (entry.eventType) {
            "ShipRequested" -> processShipRequested(entry)
            "CompileRequested" -> processCompileRequested(entry)
            else -> {
                logger.debug("Unknown SLICE event type: {}", entry.eventType)
            }
        }
    }
    
    override suspend fun handleChangeSetEvent(entry: OutboxEntry) {
        logger.debug("ChangeSetEvent received (no-op): {}", entry.id)
    }
    
    private suspend fun processShipRequested(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<ShipRequestedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse ShipRequested payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid ShipRequested payload: ${e.message}")
        }
        
        // Payload 버전 로깅 (확장성 추적)
        logger.debug("Processing ShipRequested: entity={}, sink={}, payloadVersion={}", 
            payload.entityKey, payload.sink, payload.payloadVersion)
        
        logger.info("Processing ShipRequested: entity={}, sink={}", 
            payload.entityKey, payload.sink)
        
        // Job 상태 업데이트: SINKING
        deployJobRepository?.updateState(entry.id.toString(), "SINKING")
        
        val result = shipWorkflow.execute(
            tenantId = TenantId(payload.tenantId),
            entityKey = EntityKey(payload.entityKey),
            version = payload.version.toLong(),
            sinkType = mapSinkName(payload.sink)
        )
        
        when (result) {
            is Result.Ok -> {
                logger.info("Ship success: entity={}, sink={}, latency={}ms",
                    payload.entityKey, payload.sink, result.value.latencyMs)
                // Job 상태 업데이트: DONE
                deployJobRepository?.updateState(entry.id.toString(), "DONE")
            }
            is Result.Err -> {
                logger.error("Ship failed: entity={}, error={}", payload.entityKey, result.error)
                // Job 상태 업데이트: FAILED
                deployJobRepository?.updateState(entry.id.toString(), "FAILED", result.error.toString())
                throw OutboxPollingWorker.ProcessingException("Ship failed: ${result.error}")
            }
        }
    }
    
    private suspend fun processCompileRequested(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<CompileRequestedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse CompileRequested payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid CompileRequested payload: ${e.message}")
        }
        
        // Payload 버전 로깅 (확장성 추적)
        logger.debug("Processing CompileRequested: entity={}, payloadVersion={}", 
            payload.entityKey, payload.payloadVersion)
        
        logger.info("Processing CompileRequested: entity={}", payload.entityKey)
        
        // Job 상태 업데이트: RUNNING
        deployJobRepository?.updateState(entry.id.toString(), "RUNNING")
        
        val result = slicingWorkflow.executeAuto(
            tenantId = TenantId(payload.tenantId),
            entityKey = EntityKey(payload.entityKey),
            version = payload.version.toLong()
        )
        
        when (result) {
            is Result.Ok -> {
                logger.info("Compile success: entity={}, slices={}",
                    payload.entityKey, result.value.size)

                // Ship이 있으면 READY 상태, 없으면 DONE
                val nextState = if (payload.shipSpec == "present") "READY" else "DONE"
                deployJobRepository?.updateState(entry.id.toString(), nextState)
            }
            is Result.Err -> {
                logger.error("Compile failed: entity={}, error={}", payload.entityKey, result.error)
                deployJobRepository?.updateState(entry.id.toString(), "FAILED", result.error.toString())
                throw OutboxPollingWorker.ProcessingException("Compile failed: ${result.error}")
            }
        }
    }
    
    private fun mapSinkName(sinkName: String): String {
        return when (sinkName.lowercase()) {
            "opensearchsinkspec", "opensearch" -> "opensearch"
            "personalizesinkspec", "personalize" -> "personalize"
            else -> sinkName.lowercase()
        }
    }
    
    @Serializable
    data class ShipRequestedPayload(
        val payloadVersion: String = "1.0",
        val tenantId: String,
        val entityKey: String,
        val version: String,
        val sink: String,
        val shipMode: String
    )
    
    @Serializable
    data class CompileRequestedPayload(
        val payloadVersion: String = "1.0",
        val tenantId: String,
        val entityKey: String,
        val version: String,
        val compileMode: String,
        val shipSpec: String = "absent"
    )
}

/**
 * DeployJob 저장소 인터페이스 (상태 추적용)
 */
interface DeployJobRepositoryPort {
    suspend fun save(job: DeployJobRecord): Result<DeployJobRecord>
    suspend fun get(jobId: String): Result<DeployJobRecord?>
    suspend fun updateState(jobId: String, state: String, error: String? = null): Result<Unit>
}

/**
 * DeployJob 레코드
 */
data class DeployJobRecord(
    val jobId: String,
    val entityKey: String,
    val version: String,
    val state: String,
    val error: String? = null,
    val createdAt: java.time.Instant = java.time.Instant.now(),
    val updatedAt: java.time.Instant = java.time.Instant.now()
)
