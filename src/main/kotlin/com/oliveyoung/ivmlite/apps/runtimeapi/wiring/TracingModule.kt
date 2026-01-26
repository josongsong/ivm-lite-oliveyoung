package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.TracingConfig
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module

/**
 * Tracing Module (RFC-IMPL-009)
 *
 * OpenTelemetry SDK 및 Tracer를 Koin DI에 등록.
 */
val tracingModule = module {
    single<OpenTelemetry> {
        TracingConfig.init(get<AppConfig>().observability)
    }
    single<Tracer> {
        get<OpenTelemetry>().getTracer("ivm-lite")
    }
}
