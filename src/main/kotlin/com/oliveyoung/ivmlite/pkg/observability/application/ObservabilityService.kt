package com.oliveyoung.ivmlite.pkg.observability.application

import com.oliveyoung.ivmlite.pkg.observability.domain.*
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Observability Service
 * 
 * 파이프라인 모니터링 메트릭을 제공하는 Application Service.
 */
class ObservabilityService(
    private val metricsCollector: MetricsCollectorPort
) {
    private val logger = LoggerFactory.getLogger(ObservabilityService::class.java)
    
    /**
     * 파이프라인 대시보드 데이터
     */
    suspend fun getDashboard(): PipelineDashboard {
        val metrics = metricsCollector.collectPipelineMetrics()
        val lag = metricsCollector.collectLag()
        
        return PipelineDashboard(
            metrics = metrics,
            lag = lag,
            status = determinePipelineStatus(metrics, lag),
            timestamp = Instant.now()
        )
    }
    
    /**
     * 현재 Lag 정보
     */
    suspend fun getLag(): LagMetrics {
        return metricsCollector.collectLag()
    }
    
    /**
     * 현재 처리량
     */
    suspend fun getThroughput(windowMinutes: Int = 1): ThroughputMetrics {
        return metricsCollector.collectThroughput(windowMinutes)
    }
    
    /**
     * 현재 지연 시간
     */
    suspend fun getLatency(windowMinutes: Int = 5): LatencyMetrics {
        return metricsCollector.collectE2ELatency(windowMinutes)
    }
    
    /**
     * 큐 상태
     */
    suspend fun getQueueStatus(): QueueDepthMetrics {
        return metricsCollector.collectQueueDepths()
    }
    
    /**
     * 시계열 데이터
     */
    suspend fun getTimeSeries(
        metricName: String,
        from: Instant,
        to: Instant = Instant.now(),
        resolution: Duration = Duration.ofMinutes(1)
    ): TimeSeries {
        return metricsCollector.collectTimeSeries(metricName, from, to, resolution)
    }
    
    /**
     * 파이프라인 상태 결정
     */
    private fun determinePipelineStatus(
        metrics: PipelineMetrics,
        lag: LagMetrics
    ): PipelineStatus {
        val issues = mutableListOf<String>()
        
        // Lag 체크
        if (lag.currentLag > 1000) {
            issues.add("High lag: ${lag.currentLag} messages")
        }
        if (lag.trend == LagMetrics.LagTrend.INCREASING) {
            issues.add("Lag is increasing")
        }
        
        // Latency 체크
        if (metrics.e2eLatency.p95Ms > 5000) {
            issues.add("High latency: P95=${metrics.e2eLatency.p95Ms}ms")
        }
        
        // Queue 체크
        if (metrics.queueDepths.dlq > 10) {
            issues.add("DLQ has ${metrics.queueDepths.dlq} messages")
        }
        if (metrics.queueDepths.stale > 20) {
            issues.add("${metrics.queueDepths.stale} stale entries")
        }
        
        // Throughput 체크
        if (metrics.throughput.recordsPerSecond == 0.0 && metrics.queueDepths.pending > 0) {
            issues.add("No throughput with pending messages")
        }
        
        val status = when {
            issues.any { it.contains("DLQ") || it.contains("No throughput") } -> PipelineHealth.UNHEALTHY
            issues.isNotEmpty() -> PipelineHealth.DEGRADED
            else -> PipelineHealth.HEALTHY
        }
        
        return PipelineStatus(
            health = status,
            issues = issues,
            summary = buildSummary(metrics, lag)
        )
    }
    
    private fun buildSummary(metrics: PipelineMetrics, lag: LagMetrics): String {
        return buildString {
            append("Throughput: %.1f/sec".format(metrics.throughput.recordsPerSecond))
            append(" | Lag: ${lag.currentLag}")
            append(" | P95: ${metrics.e2eLatency.p95Ms.toLong()}ms")
        }
    }
}

/**
 * 파이프라인 대시보드 데이터
 */
data class PipelineDashboard(
    val metrics: PipelineMetrics,
    val lag: LagMetrics,
    val status: PipelineStatus,
    val timestamp: Instant
)

/**
 * 파이프라인 상태
 */
data class PipelineStatus(
    val health: PipelineHealth,
    val issues: List<String>,
    val summary: String
)

enum class PipelineHealth {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}
