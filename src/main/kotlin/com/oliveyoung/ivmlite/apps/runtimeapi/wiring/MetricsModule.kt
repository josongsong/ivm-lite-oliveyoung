package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.MetricsConfig
import io.micrometer.core.instrument.MeterRegistry
import org.koin.dsl.module

/**
 * Metrics Module (RFC-IMPL-009)
 *
 * Micrometer MeterRegistry를 Koin DI에 등록.
 */
val metricsModule = module {
    single<MeterRegistry> {
        MetricsConfig.init(get<AppConfig>().observability)
    }
}
