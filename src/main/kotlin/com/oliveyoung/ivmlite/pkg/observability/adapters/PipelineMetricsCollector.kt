package com.oliveyoung.ivmlite.pkg.observability.adapters

import com.oliveyoung.ivmlite.pkg.observability.domain.*
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Pipeline Metrics Collector
 * 
 * PostgreSQL에서 파이프라인 메트릭을 수집한다.
 */
class PipelineMetricsCollector(
    private val dsl: DSLContext,
    private val worker: OutboxPollingWorker?
) : MetricsCollectorPort {
    
    private val logger = LoggerFactory.getLogger(PipelineMetricsCollector::class.java)
    
    // Lag 추적용 이전 값
    private var previousLag: Long? = null
    private var previousThroughput: Double = 0.0
    
    override suspend fun collectPipelineMetrics(): PipelineMetrics {
        return PipelineMetrics(
            e2eLatency = collectE2ELatency(),
            stageLatencies = collectStageLatencies(),
            throughput = collectThroughput(),
            queueDepths = collectQueueDepths()
        )
    }
    
    override suspend fun collectE2ELatency(windowMinutes: Int): LatencyMetrics {
        return try {
            val since = Instant.now().minusSeconds(windowMinutes * 60L)
            
            // 완료된 Outbox 엔트리의 처리 시간
            val latencies = dsl.select(
                DSL.field("EXTRACT(EPOCH FROM (processed_at - created_at)) * 1000")
            )
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSED"))
                .and(DSL.field("processed_at").isNotNull)
                .and(DSL.field("processed_at").ge(java.sql.Timestamp.from(since)))
                .orderBy(DSL.field("processed_at").desc())
                .limit(1000)
                .fetch()
                .mapNotNull { it.get(0, Double::class.java)?.toLong() }
            
            LatencyMetrics.from(latencies)
        } catch (e: Exception) {
            logger.warn("Failed to collect E2E latency: {}", e.message)
            LatencyMetrics.empty()
        }
    }
    
    override suspend fun collectStageLatencies(windowMinutes: Int): Map<PipelineStage, LatencyMetrics> {
        // 현재는 전체 E2E만 측정
        // TODO: 단계별 측정을 위해 각 단계에 타임스탬프 추가 필요
        return mapOf(
            PipelineStage.OUTBOX_WAIT to collectOutboxWaitLatency(windowMinutes)
        )
    }
    
    private fun collectOutboxWaitLatency(windowMinutes: Int): LatencyMetrics {
        return try {
            val since = Instant.now().minusSeconds(windowMinutes * 60L)
            
            // claimed_at - created_at = 대기 시간
            val latencies = dsl.select(
                DSL.field("EXTRACT(EPOCH FROM (claimed_at - created_at)) * 1000")
            )
                .from(DSL.table("outbox"))
                .where(DSL.field("claimed_at").isNotNull)
                .and(DSL.field("claimed_at").ge(java.sql.Timestamp.from(since)))
                .limit(1000)
                .fetch()
                .mapNotNull { it.get(0, Double::class.java)?.toLong() }
            
            LatencyMetrics.from(latencies)
        } catch (e: Exception) {
            logger.warn("Failed to collect outbox wait latency: {}", e.message)
            LatencyMetrics.empty()
        }
    }
    
    override suspend fun collectThroughput(windowMinutes: Int): ThroughputMetrics {
        return try {
            val since = Instant.now().minusSeconds(windowMinutes * 60L)
            
            val count = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSED"))
                .and(DSL.field("processed_at").ge(java.sql.Timestamp.from(since)))
                .fetchOne(0, Long::class.java) ?: 0L
            
            previousThroughput = count.toDouble() / (windowMinutes * 60)
            
            ThroughputMetrics.from(count, windowMinutes * 60L)
        } catch (e: Exception) {
            logger.warn("Failed to collect throughput: {}", e.message)
            ThroughputMetrics.empty()
        }
    }
    
    override suspend fun collectQueueDepths(): QueueDepthMetrics {
        return try {
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
            
            val dlqCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("FAILED"))
                .and(DSL.field("retry_count").ge(5))
                .fetchOne(0, Long::class.java) ?: 0L
            
            val staleCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSING"))
                .and(DSL.field("claimed_at").lessThan(
                    DSL.field("NOW() - INTERVAL '5 minutes'")
                ))
                .fetchOne(0, Long::class.java) ?: 0L
            
            QueueDepthMetrics(
                pending = statusCounts["PENDING"] ?: 0L,
                processing = statusCounts["PROCESSING"] ?: 0L,
                failed = statusCounts["FAILED"] ?: 0L,
                dlq = dlqCount,
                stale = staleCount
            )
        } catch (e: Exception) {
            logger.warn("Failed to collect queue depths: {}", e.message)
            QueueDepthMetrics.empty()
        }
    }
    
    override suspend fun collectLag(): LagMetrics {
        val depths = collectQueueDepths()
        val currentLag = depths.pending + depths.processing
        
        val lagMetrics = LagMetrics.from(currentLag, previousLag, previousThroughput)
        previousLag = currentLag
        
        return lagMetrics
    }
    
    override suspend fun collectTimeSeries(
        metricName: String,
        from: Instant,
        to: Instant,
        resolution: Duration
    ): TimeSeries {
        // TODO: 실제 시계열 데이터 저장/조회 구현
        // 현재는 빈 시계열 반환
        return TimeSeries(metricName, emptyList())
    }
}
