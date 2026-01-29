package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.shared.config.MetricsConfig
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("HealthRoutes")

/**
 * Health Check Routes (RFC-IMPL-010)
 *
 * - GET /health: Liveness probe (프로세스 실행 여부)
 * - GET /ready: Readiness probe (어댑터 동적 체크)
 * - GET /metrics: Prometheus metrics (RFC-IMPL-009)
 *
 * fail-closed: 어댑터 장애 시 503 → K8s가 트래픽 차단
 */
fun Route.healthRoutes(
    adapters: List<HealthCheckable> = emptyList(),
    healthCheckTimeoutMs: Long = 5000,
    meterRegistry: io.micrometer.core.instrument.MeterRegistry? = null,
) {
    // 입력 검증
    require(healthCheckTimeoutMs >= 0) { "healthCheckTimeoutMs must be non-negative" }

    // healthName 중복 경고
    val duplicates = adapters.groupBy { it.healthName }
        .filter { it.value.size > 1 }
        .keys
    if (duplicates.isNotEmpty()) {
        log.warn("Duplicate healthName detected: $duplicates - last adapter wins")
    }

    // 빈 healthName 경고
    val emptyNames = adapters.filter { it.healthName.isBlank() }
    if (emptyNames.isNotEmpty()) {
        log.warn("Empty healthName detected for ${emptyNames.size} adapter(s)")
    }
    // Liveness: 앱 프로세스 실행 중 (어댑터 상태와 무관)
    get("/health") {
        call.respond(HealthResponse(status = "UP"))
    }

    // Readiness: 모든 어댑터 정상 여부 동적 체크
    get("/ready") {
        val checks = checkAdapters(adapters, healthCheckTimeoutMs)
        val allHealthy = checks.values.all { it }
        val status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

        call.respond(
            status,
            ReadinessResponse(
                status = if (allHealthy) "UP" else "DOWN",
                checks = checks,
            ),
        )
    }

    // Prometheus Metrics (RFC-IMPL-009)
    get("/metrics") {
        val registry = meterRegistry ?: call.application.getKoin().getOrNull<MeterRegistry>()

        if (registry != null) {
            val prometheusText = MetricsConfig.scrape(registry)
            if (prometheusText.isNotEmpty()) {
                call.response.header(HttpHeaders.ContentType, "text/plain; version=0.0.4; charset=utf-8")
                call.respondText(prometheusText)
            } else {
                call.respond(HttpStatusCode.NotImplemented, "Metrics not enabled")
            }
        } else {
            call.respond(HttpStatusCode.NotImplemented, "Metrics registry not available")
        }
    }
}

/**
 * 모든 어댑터 동시 체크 (병렬 실행)
 *
 * - 예외 발생 → false
 * - 타임아웃 → false
 * - CancellationException → 재전파 (structured concurrency 준수)
 */
private suspend fun checkAdapters(
    adapters: List<HealthCheckable>,
    timeoutMs: Long,
): Map<String, Boolean> = coroutineScope {
    adapters.map { adapter ->
        async {
            adapter.healthName to safeHealthCheck(adapter, timeoutMs)
        }
    }.awaitAll().toMap()
}

/**
 * 안전한 헬스체크 (CancellationException 제외 모든 Throwable catch)
 *
 * - Exception → false (일반 예외)
 * - Error → false (OOM, StackOverflow 등)
 * - CancellationException → 재전파 (structured concurrency)
 */
private suspend fun safeHealthCheck(adapter: HealthCheckable, timeoutMs: Long): Boolean {
    return try {
        withTimeoutOrNull(timeoutMs) { adapter.healthCheck() } ?: false
    } catch (e: CancellationException) {
        // structured concurrency: CancellationException은 재전파
        throw e
    } catch (t: Throwable) {
        // Exception + Error 모두 catch (graceful degradation)
        log.debug("Health check failed for '${adapter.healthName}': ${t.message}")
        false
    }
}

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class ReadinessResponse(
    val status: String,
    val checks: Map<String, Boolean>,
)
