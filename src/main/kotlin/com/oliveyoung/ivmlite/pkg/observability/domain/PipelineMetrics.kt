package com.oliveyoung.ivmlite.pkg.observability.domain

import java.time.Duration
import java.time.Instant

/**
 * 파이프라인 단계별 메트릭
 */
data class PipelineMetrics(
    /** 전체 End-to-End 지연 시간 */
    val e2eLatency: LatencyMetrics,
    
    /** 단계별 지연 시간 */
    val stageLatencies: Map<PipelineStage, LatencyMetrics>,
    
    /** 처리량 */
    val throughput: ThroughputMetrics,
    
    /** 큐 깊이 */
    val queueDepths: QueueDepthMetrics,
    
    /** 수집 시각 */
    val timestamp: Instant = Instant.now()
)

/**
 * 파이프라인 단계
 */
enum class PipelineStage {
    /** 데이터 수집 */
    INGEST,
    
    /** 슬라이싱 */
    SLICING,
    
    /** 뷰 조합 */
    VIEW,
    
    /** Outbox 대기 */
    OUTBOX_WAIT,
    
    /** Sink 전송 */
    SINK
}

/**
 * 지연 시간 메트릭
 */
data class LatencyMetrics(
    /** 평균 지연 (ms) */
    val avgMs: Double,
    
    /** P50 (중앙값) */
    val p50Ms: Double,
    
    /** P95 */
    val p95Ms: Double,
    
    /** P99 */
    val p99Ms: Double,
    
    /** 최대값 */
    val maxMs: Double,
    
    /** 샘플 수 */
    val sampleCount: Long
) {
    companion object {
        fun empty() = LatencyMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        
        /**
         * 값 목록에서 계산
         */
        fun from(values: List<Long>): LatencyMetrics {
            if (values.isEmpty()) return empty()
            
            val sorted = values.sorted()
            val count = sorted.size
            
            return LatencyMetrics(
                avgMs = sorted.average(),
                p50Ms = sorted[count / 2].toDouble(),
                p95Ms = sorted[(count * 0.95).toInt().coerceAtMost(count - 1)].toDouble(),
                p99Ms = sorted[(count * 0.99).toInt().coerceAtMost(count - 1)].toDouble(),
                maxMs = sorted.last().toDouble(),
                sampleCount = count.toLong()
            )
        }
    }
    
    fun isHealthy(thresholdP95Ms: Double): Boolean = p95Ms <= thresholdP95Ms
}

/**
 * 처리량 메트릭
 */
data class ThroughputMetrics(
    /** 초당 처리량 */
    val recordsPerSecond: Double,
    
    /** 분당 처리량 */
    val recordsPerMinute: Double,
    
    /** 시간당 처리량 */
    val recordsPerHour: Double,
    
    /** 최근 1분간 처리량 */
    val recentMinuteCount: Long,
    
    /** 측정 기간 (초) */
    val measurementPeriodSeconds: Long
) {
    companion object {
        fun empty() = ThroughputMetrics(0.0, 0.0, 0.0, 0, 0)
        
        fun from(count: Long, periodSeconds: Long): ThroughputMetrics {
            if (periodSeconds <= 0) return empty()
            
            val rps = count.toDouble() / periodSeconds
            return ThroughputMetrics(
                recordsPerSecond = rps,
                recordsPerMinute = rps * 60,
                recordsPerHour = rps * 3600,
                recentMinuteCount = count,
                measurementPeriodSeconds = periodSeconds
            )
        }
    }
}

/**
 * 큐 깊이 메트릭
 */
data class QueueDepthMetrics(
    /** Pending 큐 깊이 */
    val pending: Long,
    
    /** Processing 큐 깊이 */
    val processing: Long,
    
    /** Failed 큐 깊이 */
    val failed: Long,
    
    /** DLQ 깊이 */
    val dlq: Long,
    
    /** Stale 엔트리 수 */
    val stale: Long
) {
    companion object {
        fun empty() = QueueDepthMetrics(0, 0, 0, 0, 0)
    }
    
    /** 전체 대기 중 */
    val totalPending: Long get() = pending + processing
    
    /** 문제 있는 엔트리 수 */
    val totalProblematic: Long get() = failed + dlq + stale
}

/**
 * Lag 메트릭 (지연/백로그)
 */
data class LagMetrics(
    /** 현재 Lag (처리 대기 중인 메시지 수) */
    val currentLag: Long,
    
    /** 추정 지연 시간 (현재 처리량 기준) */
    val estimatedLagDuration: Duration?,
    
    /** Lag 추이 (증가/감소/안정) */
    val trend: LagTrend,
    
    /** 이전 측정 대비 변화량 */
    val delta: Long,
    
    /** 측정 시각 */
    val timestamp: Instant = Instant.now()
) {
    enum class LagTrend {
        INCREASING,
        DECREASING,
        STABLE,
        UNKNOWN
    }
    
    companion object {
        fun from(
            currentLag: Long,
            previousLag: Long?,
            throughputPerSecond: Double
        ): LagMetrics {
            val trend = when {
                previousLag == null -> LagTrend.UNKNOWN
                currentLag > previousLag * 1.1 -> LagTrend.INCREASING
                currentLag < previousLag * 0.9 -> LagTrend.DECREASING
                else -> LagTrend.STABLE
            }
            
            val estimatedDuration = if (throughputPerSecond > 0) {
                Duration.ofSeconds((currentLag / throughputPerSecond).toLong())
            } else {
                null
            }
            
            return LagMetrics(
                currentLag = currentLag,
                estimatedLagDuration = estimatedDuration,
                trend = trend,
                delta = currentLag - (previousLag ?: 0),
                timestamp = Instant.now()
            )
        }
    }
}

/**
 * 시계열 데이터 포인트
 */
data class TimeSeriesPoint(
    val timestamp: Instant,
    val value: Double
)

/**
 * 시계열 데이터
 */
data class TimeSeries(
    val name: String,
    val points: List<TimeSeriesPoint>,
    val unit: String = ""
) {
    fun latest(): Double? = points.lastOrNull()?.value
    fun min(): Double? = points.minOfOrNull { it.value }
    fun max(): Double? = points.maxOfOrNull { it.value }
    fun avg(): Double? = points.map { it.value }.average().takeIf { !it.isNaN() }
}
