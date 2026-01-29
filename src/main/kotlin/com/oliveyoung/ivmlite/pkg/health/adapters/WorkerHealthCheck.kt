package com.oliveyoung.ivmlite.pkg.health.adapters

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker

/**
 * Outbox Polling Worker Health Check
 */
class WorkerHealthCheck(
    private val worker: OutboxPollingWorker?
) : HealthCheckPort {
    
    override val componentName = "Worker"
    
    override suspend fun check(): ComponentHealth {
        if (worker == null) {
            return ComponentHealth(
                name = componentName,
                status = HealthStatus.UNKNOWN,
                message = "Worker not configured"
            )
        }
        
        val metrics = worker.getMetrics()
        val isRunning = worker.isRunning()
        
        val details = mapOf<String, Any>(
            "running" to isRunning,
            "processed" to metrics.processed,
            "failed" to metrics.failed,
            "polls" to metrics.polls,
            "current_backoff_ms" to metrics.currentBackoffMs
        )
        
        // 상태 판단
        val status = when {
            !isRunning -> HealthStatus.UNHEALTHY
            metrics.currentBackoffMs > 10000 -> HealthStatus.DEGRADED  // 10초 이상 backoff
            metrics.failed > metrics.processed * 0.1 -> HealthStatus.DEGRADED  // 10% 이상 실패
            else -> HealthStatus.HEALTHY
        }
        
        val message = when {
            !isRunning -> "Worker is not running"
            metrics.currentBackoffMs > 10000 -> "Worker is backing off: ${metrics.currentBackoffMs}ms"
            metrics.failed > metrics.processed * 0.1 -> "High failure rate"
            else -> null
        }
        
        return ComponentHealth(
            name = componentName,
            status = status,
            message = message,
            details = details
        )
    }
}
