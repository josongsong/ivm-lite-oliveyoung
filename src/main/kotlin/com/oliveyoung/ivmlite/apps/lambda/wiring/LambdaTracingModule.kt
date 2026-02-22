package com.oliveyoung.ivmlite.apps.lambda.wiring

import com.oliveyoung.ivmlite.shared.config.ObservabilityConfig
import com.oliveyoung.ivmlite.shared.config.TracingConfig
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module

/**
 * Lambda 전용 Tracing 모듈 (RFC-IMPL-009)
 *
 * appModule/ConfigLoader 없이 환경변수 기반으로 동작.
 * - TRACING_ENABLED: true/false (기본: true)
 * - OTEL_EXPORTER_OTLP_ENDPOINT: OTLP 수집기 주소 (미설정 시 noop)
 *
 * Lambda 배포 시 X-Ray 연동: OTEL_EXPORTER_OTLP_ENDPOINT를 Collector 주소로 설정.
 */
val lambdaTracingModule = module {
    single<ObservabilityConfig> {
        val tracingEnabled = System.getenv("TRACING_ENABLED")?.lowercase() != "false"
        val otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
            ?: System.getenv("OTLP_ENDPOINT")
            ?: "http://localhost:4317"
        ObservabilityConfig(
            metricsEnabled = false,  // Lambda는 메트릭 별도
            tracingEnabled = tracingEnabled,
            otlpEndpoint = otlpEndpoint,
            useXRay = System.getenv("USE_XRAY")?.lowercase() == "true",
            awsRegion = System.getenv("AWS_REGION") ?: "ap-northeast-2",
        )
    }
    single<OpenTelemetry> {
        TracingConfig.init(get<ObservabilityConfig>())
    }
    single<Tracer> {
        get<OpenTelemetry>().getTracer("ivm-lite-lambda")
    }
}
