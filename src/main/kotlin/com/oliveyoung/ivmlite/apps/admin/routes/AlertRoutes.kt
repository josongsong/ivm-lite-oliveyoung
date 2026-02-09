package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngine
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertSeverity
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertStatus
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertFilter
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Duration
import java.util.UUID

/**
 * Alert Routes (알림 관리 API)
 *
 * GET /alerts: 알림 목록
 * GET /alerts/active: 활성 알림
 * GET /alerts/{id}: 알림 상세
 * POST /alerts/{id}/acknowledge: 알림 확인
 * POST /alerts/{id}/silence: 알림 무음
 * GET /alerts/rules: 규칙 목록
 * GET /alerts/stats: 통계
 */
fun Route.alertRoutes() {
    val alertEngine by inject<AlertEngine>()
    val alertRepository by inject<AlertRepositoryPort>()
    val ruleLoader by inject<AlertRuleLoaderPort>()
    
    /**
     * GET /alerts
     * 알림 목록 조회
     */
    get("/alerts") {
        try {
            val status = call.request.queryParameters["status"]
                ?.let { AlertStatus.valueOf(it.uppercase()) }
            val severity = call.request.queryParameters["severity"]
                ?.let { AlertSeverity.valueOf(it.uppercase()) }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            
            val filter = AlertFilter(
                statuses = status?.let { setOf(it) },
                severities = severity?.let { setOf(it) },
                limit = limit
            )
            
            when (val result = alertRepository.findByFilter(filter)) {
                is Result.Ok -> {
                    val response = AlertListResponse(
                        alerts = result.value.map { it.toDto() },
                        total = result.value.size
                    )
                    call.respond(HttpStatusCode.OK, response)
                }
                is Result.Err -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(code = "ALERT_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to get alerts", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "ALERT_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /alerts/active
     * 활성 알림 조회
     */
    get("/alerts/active") {
        try {
            val activeAlerts = alertEngine.getActiveAlerts()
            call.respond(HttpStatusCode.OK, AlertListResponse(
                alerts = activeAlerts.map { it.toDto() },
                total = activeAlerts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get active alerts", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "ALERT_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /alerts/{id}
     * 알림 상세 조회
     */
    get("/alerts/{id}") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid alert ID"))
                return@get
            }
            
            when (val result = alertRepository.findById(id)) {
                is Result.Ok -> {
                    val alert = result.value
                    if (alert != null) {
                        call.respond(HttpStatusCode.OK, alert.toDto())
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Alert not found"))
                    }
                }
                is Result.Err -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(code = "ALERT_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "INVALID_ID", message = "Invalid alert ID format")
            )
        }
    }
    
    /**
     * POST /alerts/{id}/acknowledge
     * 알림 확인 처리
     */
    post("/alerts/{id}/acknowledge") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid alert ID"))
                return@post
            }
            
            val request = call.receive<AcknowledgeRequest>()
            val acknowledged = alertEngine.acknowledge(id, request.by)
            
            if (acknowledged != null) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "alert" to acknowledged.toDto()
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Alert not found or not active"))
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to acknowledge alert", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "ACKNOWLEDGE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /alerts/{id}/silence
     * 알림 무음 처리
     */
    post("/alerts/{id}/silence") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid alert ID"))
                return@post
            }
            
            val durationMinutes = call.request.queryParameters["duration"]?.toLongOrNull() ?: 60
            val silenced = alertEngine.silence(id, Duration.ofMinutes(durationMinutes))
            
            if (silenced != null) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "alert" to silenced.toDto(),
                    "silencedUntil" to silenced.silencedUntil?.toString()
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Alert not found"))
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to silence alert", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "SILENCE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /alerts/rules
     * 알림 규칙 목록
     */
    get("/alerts/rules") {
        try {
            val rules = ruleLoader.loadAll()
            call.respond(HttpStatusCode.OK, AlertRulesResponse(
                rules = rules.map { rule ->
                    AlertRuleDto(
                        id = rule.id,
                        name = rule.name,
                        description = rule.description,
                        severity = rule.severity.name,
                        channels = rule.channels.map { it.name },
                        cooldownMinutes = rule.cooldown.toMinutes(),
                        enabled = rule.enabled
                    )
                },
                total = rules.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get alert rules", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RULES_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /alerts/stats
     * 알림 통계
     */
    get("/alerts/stats") {
        try {
            when (val result = alertRepository.getStats()) {
                is Result.Ok -> {
                    call.respond(HttpStatusCode.OK, result.value)
                }
                is Result.Err -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(code = "STATS_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to get alert stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "STATS_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /alerts/evaluate
     * 수동 평가 트리거 (테스트/디버깅용)
     */
    post("/alerts/evaluate") {
        try {
            val result = alertEngine.evaluateNow()
            call.respond(HttpStatusCode.OK, mapOf(
                "rulesEvaluated" to result.rulesEvaluated,
                "alertsFired" to result.alertsFired,
                "alertsResolved" to result.alertsResolved,
                "activeCount" to result.activeCount
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to evaluate alerts", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "EVALUATE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
}

// ==================== DTOs ====================

@Serializable
data class AlertListResponse(
    val alerts: List<AlertDto>,
    val total: Int
)

@Serializable
data class AlertDto(
    val id: String,
    val ruleId: String,
    val name: String,
    val description: String,
    val severity: String,
    val status: String,
    val context: Map<String, String>,
    val firedAt: String,
    val acknowledgedAt: String? = null,
    val acknowledgedBy: String? = null,
    val resolvedAt: String? = null,
    val silencedUntil: String? = null,
    val occurrences: Int
)

@Serializable
data class AcknowledgeRequest(
    val by: String
)

@Serializable
data class AlertRulesResponse(
    val rules: List<AlertRuleDto>,
    val total: Int
)

@Serializable
data class AlertRuleDto(
    val id: String,
    val name: String,
    val description: String,
    val severity: String,
    val channels: List<String>,
    val cooldownMinutes: Long,
    val enabled: Boolean
)

private fun com.oliveyoung.ivmlite.pkg.alerts.domain.Alert.toDto() = AlertDto(
    id = id.toString(),
    ruleId = ruleId,
    name = name,
    description = description,
    severity = severity.name,
    status = status.name,
    context = context,
    firedAt = firedAt.toString(),
    acknowledgedAt = acknowledgedAt?.toString(),
    acknowledgedBy = acknowledgedBy,
    resolvedAt = resolvedAt?.toString(),
    silencedUntil = silencedUntil?.toString(),
    occurrences = occurrences
)
