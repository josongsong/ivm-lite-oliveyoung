package com.oliveyoung.ivmlite.pkg.observability.adapters

import com.oliveyoung.ivmlite.pkg.observability.domain.*
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Pipeline Metrics Collector (SinkEvent 기반)
 *
 * DynamoDB Streams 전환으로 Outbox 제거됨.
 * SinkEvent(DynamoDB) 메트릭 수집.
 */
class PipelineMetricsCollector(
    private val sinkEventRepo: SinkEventRepositoryPort?
) : MetricsCollectorPort {

    private val logger = LoggerFactory.getLogger(PipelineMetricsCollector::class.java)

    override suspend fun collectPipelineMetrics(): PipelineMetrics {
        return PipelineMetrics(
            e2eLatency = collectE2ELatency(),
            stageLatencies = collectStageLatencies(),
            throughput = collectThroughput(),
            queueDepths = collectQueueDepths()
        )
    }

    override suspend fun collectE2ELatency(windowMinutes: Int): LatencyMetrics {
        // SinkEvent는 DynamoDB - E2E 지연은 Lambda 처리이므로 여기서 수집 불가
        return LatencyMetrics.empty()
    }

    override suspend fun collectStageLatencies(windowMinutes: Int): Map<PipelineStage, LatencyMetrics> {
        return mapOf(
            PipelineStage.SINK_EVENT_WAIT to LatencyMetrics.empty()
        )
    }

    override suspend fun collectThroughput(windowMinutes: Int): ThroughputMetrics {
        // SinkEvent는 DynamoDB - 처리량은 Lambda에서 수집
        return ThroughputMetrics.empty()
    }

    override suspend fun collectQueueDepths(): QueueDepthMetrics {
        val repo = sinkEventRepo ?: return QueueDepthMetrics.empty()
        return try {
            val pending = (repo.findByStatus("PENDING", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L
            val processing = (repo.findByStatus("PROCESSING", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L
            val failed = (repo.findByStatus("FAILED", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L

            QueueDepthMetrics(
                pending = pending,
                processing = processing,
                failed = failed,
                dlq = 0L,  // SinkEvent DLQ 미지원
                stale = 0L  // SinkEvent Stale 미지원
            )
        } catch (e: Exception) {
            logger.warn("Failed to collect queue depths: {}", e.message)
            QueueDepthMetrics.empty()
        }
    }

    override suspend fun collectLag(): LagMetrics {
        val depths = collectQueueDepths()
        return LagMetrics.from(
            currentLag = depths.totalPending,
            previousLag = null,
            throughputPerSecond = 0.0
        )
    }

    override suspend fun collectTimeSeries(
        metricName: String,
        from: Instant,
        to: Instant,
        resolution: Duration
    ): TimeSeries {
        return TimeSeries(metricName, emptyList())
    }
}
