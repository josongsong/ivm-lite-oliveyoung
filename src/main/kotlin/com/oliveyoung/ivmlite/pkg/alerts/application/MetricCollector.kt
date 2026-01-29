package com.oliveyoung.ivmlite.pkg.alerts.application

import com.oliveyoung.ivmlite.pkg.alerts.domain.MetricSnapshot
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Metric Collector
 * 
 * 시스템 메트릭을 수집하여 AlertEngine에 제공한다.
 * 캐싱을 통해 DB 쿼리 부하를 최소화한다.
 */
class MetricCollector(
    private val dsl: DSLContext,
    private val worker: OutboxPollingWorker?,
    private val outboxRepo: OutboxRepositoryPort?
) {
    private val logger = LoggerFactory.getLogger(MetricCollector::class.java)
    
    // Rate 계산용 이전 값 저장
    private val previousValues = ConcurrentHashMap<String, Pair<Long, Instant>>()
    
    // 캐시 (짧은 TTL)
    private var cachedSnapshot: MetricSnapshot? = null
    private var cacheTimestamp: Instant = Instant.MIN
    private val cacheTtlMs = 5000L  // 5초 캐시
    
    /**
     * 현재 메트릭 스냅샷 수집
     */
    suspend fun collect(): MetricSnapshot {
        val now = Instant.now()
        
        // 캐시 확인
        if (cachedSnapshot != null && 
            now.toEpochMilli() - cacheTimestamp.toEpochMilli() < cacheTtlMs) {
            return cachedSnapshot!!
        }
        
        val values = mutableMapOf<String, Any>()
        val rates = mutableMapOf<String, Map<Long, Double>>()
        
        try {
            // Worker 메트릭
            collectWorkerMetrics(values)
            
            // Outbox 메트릭
            collectOutboxMetrics(values, rates)
            
            // Health 메트릭
            collectHealthMetrics(values)
            
            // Pipeline 메트릭
            collectPipelineMetrics(values)
            
        } catch (e: Exception) {
            logger.error("Failed to collect metrics", e)
        }
        
        val snapshot = MetricSnapshot(
            values = values.toMap(),
            rates = rates.toMap(),
            timestamp = now
        )
        
        cachedSnapshot = snapshot
        cacheTimestamp = now
        
        return snapshot
    }
    
    private fun collectWorkerMetrics(values: MutableMap<String, Any>) {
        worker?.let { w ->
            values["worker.running"] = w.isRunning()
            val metrics = w.getMetrics()
            values["worker.processed"] = metrics.processed
            values["worker.failed"] = metrics.failed
            values["worker.polls"] = metrics.polls
        } ?: run {
            values["worker.running"] = false
        }
    }
    
    private fun collectOutboxMetrics(
        values: MutableMap<String, Any>,
        rates: MutableMap<String, Map<Long, Double>>
    ) {
        try {
            // Status별 카운트
            val statusCounts = dsl.select(
                DSL.field("status"),
                DSL.count()
            )
                .from(DSL.table("outbox"))
                .groupBy(DSL.field("status"))
                .fetch()
                .associate { 
                    it.get(0, String::class.java)!! to it.get(1, Long::class.java)!! 
                }
            
            val pending = statusCounts["PENDING"] ?: 0L
            val processing = statusCounts["PROCESSING"] ?: 0L
            val failed = statusCounts["FAILED"] ?: 0L
            val processed = statusCounts["PROCESSED"] ?: 0L
            
            values["outbox.pending.count"] = pending
            values["outbox.processing.count"] = processing
            values["outbox.failed.count"] = failed
            values["outbox.processed.count"] = processed
            
            // DLQ 카운트 (retry_count >= 5인 FAILED)
            val dlqCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("FAILED"))
                .and(DSL.field("retry_count").ge(5))
                .fetchOne(0, Long::class.java) ?: 0L
            values["outbox.dlq.count"] = dlqCount
            
            // Stale 카운트 (PROCESSING 5분 이상)
            val staleCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSING"))
                .and(DSL.field("claimed_at").lessThan(
                    DSL.field("NOW() - INTERVAL '5 minutes'")
                ))
                .fetchOne(0, Long::class.java) ?: 0L
            values["outbox.stale.count"] = staleCount
            
            // 실패율 계산
            calculateRate("outbox.failed.rate", failed, rates)
            
        } catch (e: Exception) {
            logger.warn("Failed to collect outbox metrics: {}", e.message)
        }
    }
    
    private fun collectHealthMetrics(values: MutableMap<String, Any>) {
        // PostgreSQL 연결 체크
        try {
            dsl.selectOne().fetch()
            values["health.postgres.connected"] = true
        } catch (e: Exception) {
            values["health.postgres.connected"] = false
        }
    }
    
    private fun collectPipelineMetrics(values: MutableMap<String, Any>) {
        try {
            // 평균 처리 시간 (최근 100건)
            val avgLatency = dsl.select(
                DSL.field("AVG(EXTRACT(EPOCH FROM (processed_at - created_at)))")
            )
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSED"))
                .and(DSL.field("processed_at").isNotNull)
                .orderBy(DSL.field("processed_at").desc())
                .limit(100)
                .fetchOne(0, Double::class.java) ?: 0.0
            
            values["pipeline.e2e.latency_seconds"] = avgLatency
            
            // Raw Data 카운트
            val rawCount = dsl.selectCount()
                .from(DSL.table("raw_data"))
                .fetchOne(0, Long::class.java) ?: 0L
            values["pipeline.rawdata.count"] = rawCount
            
            // Slice 카운트
            val sliceCount = dsl.selectCount()
                .from(DSL.table("slices"))
                .fetchOne(0, Long::class.java) ?: 0L
            values["pipeline.slices.count"] = sliceCount
            
        } catch (e: Exception) {
            logger.warn("Failed to collect pipeline metrics: {}", e.message)
        }
    }
    
    /**
     * Rate 계산 (변화량 / 시간)
     */
    private fun calculateRate(
        metricName: String,
        currentValue: Long,
        rates: MutableMap<String, Map<Long, Double>>
    ) {
        val now = Instant.now()
        val previous = previousValues[metricName]
        
        if (previous != null) {
            val (prevValue, prevTime) = previous
            val deltaValue = currentValue - prevValue
            val deltaSeconds = (now.toEpochMilli() - prevTime.toEpochMilli()) / 1000.0
            
            if (deltaSeconds > 0) {
                val rate = deltaValue / deltaSeconds
                rates[metricName] = mapOf(60L to rate)
            }
        }
        
        previousValues[metricName] = currentValue to now
    }
    
    /**
     * 캐시 무효화
     */
    fun invalidateCache() {
        cachedSnapshot = null
    }
}
