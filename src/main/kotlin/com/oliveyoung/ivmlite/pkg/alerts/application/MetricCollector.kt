package com.oliveyoung.ivmlite.pkg.alerts.application

import com.oliveyoung.ivmlite.pkg.alerts.domain.MetricSnapshot
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Metric Collector (SinkEvent 기반)
 *
 * Outbox 제거됨. SinkEvent(DynamoDB) 메트릭 수집.
 */
class MetricCollector(
    private val database: Database,
    private val sinkEventRepo: SinkEventRepositoryPort?
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
            // SinkEvent 메트릭 (DynamoDB)
            collectSinkEventMetrics(values, rates)

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

    private suspend fun collectSinkEventMetrics(
        values: MutableMap<String, Any>,
        rates: MutableMap<String, Map<Long, Double>>
    ) {
        val repo = sinkEventRepo ?: run {
            values["sink_event.pending.count"] = 0L
            values["sink_event.processing.count"] = 0L
            values["sink_event.failed.count"] = 0L
            values["sink_event.completed.count"] = 0L
            return
        }
        try {
            val pending = (repo.findByStatus("PENDING", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L
            val processing = (repo.findByStatus("PROCESSING", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L
            val failed = (repo.findByStatus("FAILED", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L
            val completed = (repo.findByStatus("COMPLETED", 10000) as? com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>)?.value?.let { (it as List<*>).size.toLong() } ?: 0L

            values["sink_event.pending.count"] = pending
            values["sink_event.processing.count"] = processing
            values["sink_event.failed.count"] = failed
            values["sink_event.completed.count"] = completed
            values["health.postgres.connected"] = true  // 호환성
            calculateRate("sink_event.failed.rate", failed, rates)
        } catch (e: Exception) {
            logger.warn("Failed to collect sink event metrics: {}", e.message)
        }
    }

    private fun collectHealthMetrics(values: MutableMap<String, Any>) {
        // PostgreSQL 연결 체크
        try {
            transaction(database) {
                exec("SELECT 1") { }
            }
            values["health.postgres.connected"] = true
        } catch (e: Exception) {
            values["health.postgres.connected"] = false
        }
    }

    private fun collectPipelineMetrics(values: MutableMap<String, Any>) {
        try {
            values["pipeline.e2e.latency_seconds"] = 0.0  // SinkEvent는 DynamoDB에서 조회
            // raw_data, slices는 V028에서 제거됨 (DynamoDB로 이전)
            values["pipeline.rawdata.count"] = 0L
            values["pipeline.slices.count"] = 0L
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
