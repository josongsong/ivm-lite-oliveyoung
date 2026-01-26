package com.oliveyoung.ivmlite.shared.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope

/**
 * OpenTelemetry Tracing Extensions (RFC-IMPL-009)
 *
 * 워크플로우 및 어댑터에서 span을 쉽게 생성하기 위한 헬퍼 함수.
 */
inline fun <T> Tracer.withSpan(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: (Span) -> T,
): T {
    val span = spanBuilder(name)
        .apply {
            attributes.forEach { (k, v) -> setAttribute(k, v) }
        }
        .startSpan()

    val scope: Scope = span.makeCurrent()
    return try {
        val result = block(span)
        span.setStatus(StatusCode.OK)
        result
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, e.message ?: "unknown")
        span.recordException(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}

/**
 * Suspend 함수용 withSpan (coroutine context 전파)
 */
suspend inline fun <T> Tracer.withSpanSuspend(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend (Span) -> T,
): T {
    val span = spanBuilder(name)
        .apply {
            attributes.forEach { (k, v) -> setAttribute(k, v) }
        }
        .startSpan()

    val scope: Scope = span.makeCurrent()
    return try {
        val result = block(span)
        span.setStatus(StatusCode.OK)
        result
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, e.message ?: "unknown")
        span.recordException(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}
