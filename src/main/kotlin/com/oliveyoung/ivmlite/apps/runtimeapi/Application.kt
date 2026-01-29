package com.oliveyoung.ivmlite.apps.runtimeapi

import com.oliveyoung.ivmlite.shared.config.DotenvLoader
import com.oliveyoung.ivmlite.apps.runtimeapi.routes.healthRoutes
import com.oliveyoung.ivmlite.apps.runtimeapi.routes.ingestRoutes
import com.oliveyoung.ivmlite.apps.runtimeapi.routes.outboxRoutes
import com.oliveyoung.ivmlite.apps.runtimeapi.routes.queryRoutes
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.allModules
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import io.micrometer.core.instrument.MeterRegistry
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.MDC
import java.util.*

/**
 * ivm-lite Runtime API Application (RFC-IMPL-009)
 * 
 * HTTP Server: Ktor + Netty (고정)
 * DI: Koin
 * Config: Hoplite
 */
fun main() {
    // .env 파일 로드 (환경변수 미설정 시 fallback)
    DotenvLoader.load()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Koin DI
    install(Koin) {
        slf4jLogger()
        modules(allModules)
    }
    
    // OpenTelemetry (RFC-IMPL-009: Tracing SSOT)
    // NOTE: Ktor OTel instrumentation은 패키지명 확인 필요
    // 현재는 수동 HTTP span + MDC 연동 구현 (하이브리드 계획에 따라)
    
    val openTelemetry by inject<OpenTelemetry>()
    val tracer = openTelemetry.getTracer("ivm-lite-http")
    
    // HTTP 요청 span 생성 + MDC 연동 (RFC-IMPL-009: Log Correlation)
    val otelSpanKey = io.ktor.util.AttributeKey<Span>("otel.span")
    val otelScopeKey = io.ktor.util.AttributeKey<io.opentelemetry.context.Scope>("otel.scope")
    
    install(createApplicationPlugin("HttpTracing") {
        onCall { call ->
            val method = call.request.local.method.value
            val path = call.request.local.uri
            val span = tracer.spanBuilder("HTTP $method $path")
                .setAttribute("http.method", method)
                .setAttribute("http.target", path)
                .setAttribute("http.route", path)
                .startSpan()
            
            val scope = span.makeCurrent()
            call.attributes.put(otelScopeKey, scope)
            call.attributes.put(otelSpanKey, span)
            
            if (span.spanContext.isValid) {
                MDC.put("traceId", span.spanContext.traceId)
                MDC.put("spanId", span.spanContext.spanId)
            }
        }
        onCallRespond { call, _ ->
            val span = call.attributes.getOrNull(otelSpanKey)
            val scope = call.attributes.getOrNull(otelScopeKey)
            
            span?.let {
                it.setAttribute("http.status_code", call.response.status()?.value?.toLong() ?: 0L)
                it.setStatus(
                    if ((call.response.status()?.value ?: 0) < 400) {
                        io.opentelemetry.api.trace.StatusCode.OK
                    } else {
                        io.opentelemetry.api.trace.StatusCode.ERROR
                    }
                )
                it.end()
            }
            scope?.close()
            
            call.attributes.remove(otelSpanKey)
            call.attributes.remove(otelScopeKey)
            
            MDC.remove("traceId")
            MDC.remove("spanId")
        }
    })
    
    // HTTP span 예외 처리 (StatusPages와 함께 사용)
    fun cleanupSpan(call: ApplicationCall, cause: Throwable? = null) {
        val span = call.attributes.getOrNull(otelSpanKey)
        val scope = call.attributes.getOrNull(otelScopeKey)
        
        span?.let {
            if (cause != null) {
                it.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, cause.message ?: "unknown")
                it.recordException(cause)
            } else {
                it.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
            }
            it.end()
        }
        scope?.close()
        
        call.attributes.remove(otelSpanKey)
        call.attributes.remove(otelScopeKey)
        
        MDC.remove("traceId")
        MDC.remove("spanId")
    }
    
    // Content Negotiation (JSON)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = false  // RFC-IMPL-009: fail-closed
            isLenient = false
        })
    }
    
    // Call ID (Tracing correlation)
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
    
    // Status Pages (Error handling)
    install(StatusPages) {
        exception<kotlinx.serialization.SerializationException> { call, cause ->
            cleanupSpan(call, cause)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid JSON: ${cause.message}")
            )
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            cleanupSpan(call, cause)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Bad request"))
            )
        }
        exception<Throwable> { call, cause ->
            cleanupSpan(call, cause)
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
    }
    
    // Config
    val config by inject<AppConfig>()

    // Worker Lifecycle (RFC-IMPL Phase B-2)
    val worker by inject<OutboxPollingWorker>()

    environment.monitor.subscribe(ApplicationStarted) {
        if (worker.start()) {
            log.info("OutboxPollingWorker started")
        } else {
            log.warn("OutboxPollingWorker not started (disabled or already running)")
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            if (worker.stop()) {
                log.info("OutboxPollingWorker stopped gracefully")
            }
        }
    }

    // Routes
    val healthCheckAdapters = getKoin().getAll<HealthCheckable>()
    val meterRegistry = getKoin().getOrNull<MeterRegistry>()

    routing {
        healthRoutes(healthCheckAdapters, meterRegistry = meterRegistry)
        ingestRoutes()
        queryRoutes()
        outboxRoutes()
    }

    log.info("ivm-lite Runtime API started on ${config.server.host}:${config.server.port}")
}
