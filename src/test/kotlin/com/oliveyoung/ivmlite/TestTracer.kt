package com.oliveyoung.ivmlite

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * 테스트용 NoOp Tracer 제공
 */
object TestTracer {
    val NOOP: Tracer = OpenTelemetry.noop().getTracer("test")
}
