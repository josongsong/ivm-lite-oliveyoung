package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.health.application.HealthService
import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.SystemHealth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject

/**
 * Health Routes (시스템 상태 API)
 *
 * GET /health: 전체 시스템 상태
 * GET /health/live: Liveness probe
 * GET /health/ready: Readiness probe
 * GET /health/{component}: 개별 컴포넌트 상태
 */
fun Route.healthRoutes() {
    val healthService by inject<HealthService>()
    
    /**
     * GET /health
     * 전체 시스템 상태
     */
    get("/health") {
        try {
            val health = healthService.checkAll()
            
            val statusCode = when (health.overall) {
                com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.HEALTHY -> HttpStatusCode.OK
                com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.DEGRADED -> HttpStatusCode.OK
                com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.UNHEALTHY -> HttpStatusCode.ServiceUnavailable
                com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.UNKNOWN -> HttpStatusCode.OK
            }
            
            call.respond(statusCode, health.toDto())
        } catch (e: Exception) {
            call.application.log.error("Health check failed", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "HEALTH_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /health/live
     * Kubernetes Liveness Probe
     */
    get("/health/live") {
        if (healthService.liveness()) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "dead"))
        }
    }
    
    /**
     * GET /health/ready
     * Kubernetes Readiness Probe
     */
    get("/health/ready") {
        try {
            if (healthService.readiness()) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "not ready"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "status" to "not ready",
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    /**
     * GET /health/{component}
     * 개별 컴포넌트 상태
     */
    get("/health/{component}") {
        try {
            val componentName = call.parameters["component"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "MISSING_COMPONENT", message = "Component name required"))
                return@get
            }
            
            val health = healthService.checkComponent(componentName)
            if (health != null) {
                val statusCode = when (health.status) {
                    com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.HEALTHY -> HttpStatusCode.OK
                    com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.DEGRADED -> HttpStatusCode.OK
                    com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.UNHEALTHY -> HttpStatusCode.ServiceUnavailable
                    com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus.UNKNOWN -> HttpStatusCode.OK
                }
                call.respond(statusCode, health.toDto())
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(code = "NOT_FOUND", message = "Component not found: $componentName")
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "HEALTH_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /health/components
     * 사용 가능한 컴포넌트 목록
     */
    get("/health/components") {
        call.respond(HttpStatusCode.OK, mapOf(
            "components" to healthService.getComponentNames()
        ))
    }
    
    /**
     * GET /health/version
     * 버전 정보
     */
    get("/health/version") {
        call.respond(HttpStatusCode.OK, mapOf(
            "version" to healthService.getVersion(),
            "uptime" to healthService.getUptime()
        ))
    }
}

// ==================== DTOs ====================

@Serializable
data class SystemHealthDto(
    val overall: String,
    val components: List<ComponentHealthDto>,
    val timestamp: String,
    val version: String,
    val uptime: Long
)

@Serializable
data class ComponentHealthDto(
    val name: String,
    val status: String,
    val latencyMs: Long,
    val message: String? = null,
    val details: Map<String, String> = emptyMap(),
    val checkedAt: String,
    val error: String? = null
)

private fun SystemHealth.toDto() = SystemHealthDto(
    overall = overall.name,
    components = components.map { it.toDto() },
    timestamp = timestamp.toString(),
    version = version,
    uptime = uptime
)

private fun ComponentHealth.toDto() = ComponentHealthDto(
    name = name,
    status = status.name,
    latencyMs = latencyMs,
    message = message,
    details = details.mapValues { it.value.toString() },
    checkedAt = checkedAt.toString(),
    error = error
)
