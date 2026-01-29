package com.oliveyoung.ivmlite.shared.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Micrometer Metrics Configuration (RFC-IMPL-009)
 *
 * Prometheus MeterRegistry 초기화 및 설정.
 * metricsEnabled=false면 noop() 반환하여 메트릭 비활성화.
 */
object MetricsConfig {
    private val initialized = AtomicBoolean(false)
    private var registry: MeterRegistry? = null
    
    fun init(config: ObservabilityConfig): MeterRegistry {
        if (!config.metricsEnabled) {
            return io.micrometer.core.instrument.Metrics.globalRegistry
        }

        // 이미 초기화된 경우 기존 인스턴스 반환 (테스트 안정성)
        if (!initialized.compareAndSet(false, true)) {
            return registry ?: io.micrometer.core.instrument.Metrics.globalRegistry
        }

        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        
        // Global registry에 등록 (Micrometer 표준)
        io.micrometer.core.instrument.Metrics.addRegistry(prometheusRegistry)
        
        registry = prometheusRegistry
        return prometheusRegistry
    }
    
    /**
     * Prometheus 포맷으로 메트릭 내보내기
     */
    fun scrape(registry: MeterRegistry): String {
        return if (registry is PrometheusMeterRegistry) {
            registry.scrape()
        } else {
            ""
        }
    }
}
