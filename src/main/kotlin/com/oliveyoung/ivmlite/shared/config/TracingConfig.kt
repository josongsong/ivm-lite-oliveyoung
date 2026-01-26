package com.oliveyoung.ivmlite.shared.config

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenTelemetry Tracing Configuration (RFC-IMPL-009)
 *
 * OTel SDK 초기화 및 TracerProvider 설정.
 * tracingEnabled=false면 noop() 반환하여 tracing 비활성화.
 */
object TracingConfig {
    private val initialized = AtomicBoolean(false)
    
    fun init(config: ObservabilityConfig): OpenTelemetry {
        if (!config.tracingEnabled) {
            return OpenTelemetry.noop()
        }

        // 이미 초기화된 경우 기존 인스턴스 반환 (테스트 안정성)
        if (!initialized.compareAndSet(false, true)) {
            return try {
                GlobalOpenTelemetry.get()
            } catch (e: Exception) {
                OpenTelemetry.noop()
            }
        }

        val exporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(
                Resource.builder()
                    .put("service.name", "ivm-lite")
                    .put("service.version", System.getProperty("service.version") ?: "unknown")
                    .put("deployment.environment", System.getenv("ENVIRONMENT") ?: "development")
                    .build(),
            )
            .build()

        return try {
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal()
        } catch (e: IllegalStateException) {
            // 이미 등록된 경우 (테스트 환경)
            GlobalOpenTelemetry.get()
        }
    }
    
    /**
     * 테스트용: 초기화 상태 리셋
     */
    fun resetForTesting() {
        initialized.set(false)
    }
}
