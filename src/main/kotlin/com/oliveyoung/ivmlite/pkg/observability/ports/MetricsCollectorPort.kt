package com.oliveyoung.ivmlite.pkg.observability.ports

import com.oliveyoung.ivmlite.pkg.observability.domain.*
import java.time.Duration
import java.time.Instant

/**
 * Metrics Collector Port
 * 
 * 파이프라인 메트릭을 수집하는 책임.
 */
interface MetricsCollectorPort {
    
    /**
     * 현재 파이프라인 메트릭 수집
     */
    suspend fun collectPipelineMetrics(): PipelineMetrics
    
    /**
     * End-to-End 지연 시간 수집
     */
    suspend fun collectE2ELatency(windowMinutes: Int = 5): LatencyMetrics
    
    /**
     * 단계별 지연 시간 수집
     */
    suspend fun collectStageLatencies(windowMinutes: Int = 5): Map<PipelineStage, LatencyMetrics>
    
    /**
     * 처리량 수집
     */
    suspend fun collectThroughput(windowMinutes: Int = 1): ThroughputMetrics
    
    /**
     * 큐 깊이 수집
     */
    suspend fun collectQueueDepths(): QueueDepthMetrics
    
    /**
     * Lag 메트릭 수집
     */
    suspend fun collectLag(): LagMetrics
    
    /**
     * 시계열 데이터 수집
     * 
     * @param metricName 메트릭 이름
     * @param from 시작 시각
     * @param to 종료 시각
     * @param resolution 해상도 (포인트 간 간격)
     */
    suspend fun collectTimeSeries(
        metricName: String,
        from: Instant,
        to: Instant = Instant.now(),
        resolution: Duration = Duration.ofMinutes(1)
    ): TimeSeries
}
