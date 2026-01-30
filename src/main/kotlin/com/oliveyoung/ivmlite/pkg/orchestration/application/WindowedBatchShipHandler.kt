package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 윈도우 기반 배치 Ship 핸들러
 * 
 * Kafka 등으로 보낼 때 변경 드리프트를 줄이기 위해:
 * 1. 일정 시간(윈도우) 동안 쌓인 ShipRequested를 모아서 배치 처리
 * 2. 같은 엔티티의 여러 변경을 하나로 합침 (latest wins)
 * 3. 배치 크기 제한으로 즉시 처리 가능
 * 
 * 사용법:
 * ```kotlin
 * val windowedHandler = WindowedBatchShipHandler(
 *     delegate = shipEventHandler,
 *     windowSizeMs = 5000L,  // 5초 윈도우
 *     maxBatchSize = 100,    // 최대 100개까지 모음
 *     dedupeByEntity = true   // 같은 엔티티 중복 제거
 * )
 * ```
 */
class WindowedBatchShipHandler(
    private val delegate: OutboxPollingWorker.EventHandler,
    /**
     * 윈도우 크기 (밀리초)
     * 이 시간 동안 쌓인 이벤트를 모아서 배치 처리
     */
    private val windowSizeMs: Long = 5000L,
    /**
     * 최대 배치 크기
     * 이 개수만큼 모이면 즉시 처리 (윈도우 시간 기다리지 않음)
     */
    private val maxBatchSize: Int = 100,
    /**
     * 엔티티별 중복 제거 여부
     * true면 같은 엔티티의 여러 변경을 하나로 합침 (latest wins)
     */
    private val dedupeByEntity: Boolean = true
) : OutboxPollingWorker.EventHandler {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // 윈도우별 배치 큐 (sinkType -> (entityKey -> entry))
    private val windowQueues = ConcurrentHashMap<String, MutableMap<String, WindowEntry>>()
    
    // 윈도우 타이머 (sinkType -> Job)
    private val windowTimers = ConcurrentHashMap<String, Job>()
    
    // 동기화 뮤텍스
    private val mutex = Mutex()
    
    // 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 윈도우 엔트리 (엔티티별 최신 버전만 유지)
     */
    private data class WindowEntry(
        val entry: OutboxEntry,
        val payload: ShipEventHandler.ShipRequestedPayload,
        val addedAt: Instant = Instant.now()
    )
    
    override suspend fun handleSliceEvent(entry: OutboxEntry) {
        when (entry.eventType) {
            "ShipRequested" -> {
                val payload = parsePayload(entry)
                val sinkType = normalizeSinkName(payload.sink)
                
                mutex.withLock {
                    // 윈도우 큐에 추가
                    val queue = windowQueues.getOrPut(sinkType) { mutableMapOf() }
                    
                    if (dedupeByEntity) {
                        // 같은 엔티티의 최신 버전만 유지 (latest wins)
                        val entityKey = "${payload.tenantId}:${payload.entityKey}"
                        val existing = queue[entityKey]
                        
                        if (existing != null && existing.payload.version.toLong() < payload.version.toLong()) {
                            logger.debug("Deduplicating: entity={}, oldVersion={}, newVersion={}",
                                entityKey, existing.payload.version, payload.version)
                        }
                        
                        queue[entityKey] = WindowEntry(entry, payload)
                    } else {
                        // 중복 제거 없이 모두 추가
                        val key = "${entry.id}:${payload.entityKey}"
                        queue[key] = WindowEntry(entry, payload)
                    }
                    
                    // 배치 크기 체크: 최대 크기 도달 시 즉시 처리
                    if (queue.size >= maxBatchSize) {
                        logger.info("Batch size limit reached: sink={}, size={}, processing immediately",
                            sinkType, queue.size)
                        processWindow(sinkType, queue)
                        windowQueues.remove(sinkType)
                        windowTimers[sinkType]?.cancel()
                        windowTimers.remove(sinkType)
                    } else if (!windowTimers.containsKey(sinkType)) {
                        // 윈도우 타이머 시작 (이미 실행 중이면 무시)
                        startWindowTimer(sinkType)
                    }
                    Unit
                }
            }
            else -> {
                // ShipRequested가 아닌 이벤트는 즉시 처리
                delegate.handleSliceEvent(entry)
            }
        }
    }
    
    override suspend fun handleChangeSetEvent(entry: OutboxEntry) {
        delegate.handleChangeSetEvent(entry)
    }
    
    /**
     * 윈도우 타이머 시작
     */
    private fun startWindowTimer(sinkType: String) {
        val timerJob = scope.launch {
            delay(windowSizeMs)
            
            mutex.withLock {
                val queue = windowQueues[sinkType]
                if (queue != null && queue.isNotEmpty()) {
                    logger.info("Window timeout: sink={}, size={}, processing batch",
                        sinkType, queue.size)
                    processWindow(sinkType, queue)
                    windowQueues.remove(sinkType)
                }
                windowTimers.remove(sinkType)
            }
        }
        
        windowTimers[sinkType] = timerJob
    }
    
    /**
     * 윈도우 배치 처리
     */
    private suspend fun processWindow(sinkType: String, queue: Map<String, WindowEntry>) {
        if (queue.isEmpty()) {
            return
        }
        
        logger.info("Processing window batch: sink={}, size={}", sinkType, queue.size)
        
        // 배치로 처리할 엔티티 목록 생성
        val entities = queue.values.map { it.payload }
        
        // 같은 sinkType으로 묶인 배치 처리
        // TODO: ShipWorkflow.executeBatch()를 사용하여 배치 처리
        // 현재는 개별 처리로 fallback (배치 API가 sinkType별로 지원되면 개선)
        
        val results = mutableListOf<Pair<OutboxEntry, Boolean>>()
        
        for (windowEntry in queue.values) {
            try {
                delegate.handleSliceEvent(windowEntry.entry)
                results.add(windowEntry.entry to true)
            } catch (e: Exception) {
                logger.error("Failed to process windowed entry: entity={}, error={}",
                    windowEntry.payload.entityKey, e.message)
                results.add(windowEntry.entry to false)
            }
        }
        
        val successCount = results.count { it.second }
        val failedCount = results.count { !it.second }
        
        logger.info("Window batch completed: sink={}, success={}, failed={}",
            sinkType, successCount, failedCount)
    }
    
    /**
     * Payload 파싱
     */
    private fun parsePayload(entry: OutboxEntry): ShipEventHandler.ShipRequestedPayload {
        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.decodeFromString(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse ShipRequested payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid ShipRequested payload: ${e.message}")
        }
    }
    
    /**
     * Sink 이름 정규화
     */
    private fun normalizeSinkName(sinkName: String): String {
        return when (sinkName.lowercase()) {
            "opensearchsinkspec", "opensearch" -> "opensearch"
            "personalizesinkspec", "personalize" -> "personalize"
            "kafkasinkspec", "kafka" -> "kafka"
            else -> sinkName.lowercase()
        }
    }
    
    /**
     * 강제로 모든 윈도우 플러시 (shutdown 시 사용)
     */
    suspend fun flushAll() {
        mutex.withLock {
            logger.info("Flushing all windows: count={}", windowQueues.size)
            
            for ((sinkType, queue) in windowQueues) {
                if (queue.isNotEmpty()) {
                    logger.info("Flushing window: sink={}, size={}", sinkType, queue.size)
                    processWindow(sinkType, queue)
                }
            }
            
            windowQueues.clear()
            
            // 모든 타이머 취소
            for (timer in windowTimers.values) {
                timer.cancel()
            }
            windowTimers.clear()
        }
    }
    
    /**
     * 현재 윈도우 상태 조회 (모니터링용)
     * Note: 동시성 문제를 피하기 위해 스냅샷을 반환
     */
    fun getWindowStatus(): Map<String, WindowStatus> {
        // ConcurrentHashMap의 스냅샷을 생성 (mutex 불필요)
        return windowQueues.toMap().mapValues { (sinkType, queue) ->
            WindowStatus(
                sinkType = sinkType,
                pendingCount = queue.size,
                oldestEntryAgeMs = if (queue.isNotEmpty()) {
                    val oldest = queue.values.minByOrNull { it.addedAt }
                    oldest?.let {
                        java.time.Duration.between(it.addedAt, Instant.now()).toMillis()
                    } ?: 0L
                } else {
                    0L
                },
                hasTimer = windowTimers.containsKey(sinkType)
            )
        }
    }
    
    data class WindowStatus(
        val sinkType: String,
        val pendingCount: Int,
        val oldestEntryAgeMs: Long,
        val hasTimer: Boolean
    )
}
