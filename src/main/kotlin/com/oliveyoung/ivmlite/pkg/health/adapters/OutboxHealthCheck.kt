package com.oliveyoung.ivmlite.pkg.health.adapters

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

/**
 * Outbox Queue Health Check
 */
class OutboxHealthCheck(
    private val dsl: DSLContext,
    private val config: OutboxHealthConfig = OutboxHealthConfig()
) : HealthCheckPort {
    
    private val logger = LoggerFactory.getLogger(OutboxHealthCheck::class.java)
    
    override val componentName = "Outbox"
    
    override suspend fun check(): ComponentHealth {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Status별 카운트
            val statusCounts = dsl.select(
                DSL.field("status"),
                DSL.count()
            )
                .from(DSL.table("outbox"))
                .groupBy(DSL.field("status"))
                .fetch()
                .associate { 
                    it.get(0, String::class.java)!! to it.get(1, Long::class.java)!! 
                }
            
            val pending = statusCounts["PENDING"] ?: 0L
            val processing = statusCounts["PROCESSING"] ?: 0L
            val failed = statusCounts["FAILED"] ?: 0L
            
            // DLQ 카운트
            val dlqCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("FAILED"))
                .and(DSL.field("retry_count").ge(5))
                .fetchOne(0, Long::class.java) ?: 0L
            
            // Stale 카운트 (5분 이상 PROCESSING)
            val staleCount = dsl.selectCount()
                .from(DSL.table("outbox"))
                .where(DSL.field("status").eq("PROCESSING"))
                .and(DSL.field("claimed_at").lessThan(
                    DSL.field("NOW() - INTERVAL '5 minutes'")
                ))
                .fetchOne(0, Long::class.java) ?: 0L
            
            val latency = System.currentTimeMillis() - startTime
            
            val details = mapOf<String, Any>(
                "pending" to pending,
                "processing" to processing,
                "failed" to failed,
                "dlq" to dlqCount,
                "stale" to staleCount
            )
            
            // 상태 판단
            val status = when {
                dlqCount > config.dlqCriticalThreshold -> HealthStatus.UNHEALTHY
                dlqCount > config.dlqWarningThreshold -> HealthStatus.DEGRADED
                staleCount > config.staleThreshold -> HealthStatus.DEGRADED
                pending > config.pendingWarningThreshold -> HealthStatus.DEGRADED
                else -> HealthStatus.HEALTHY
            }
            
            val message = when {
                dlqCount > config.dlqCriticalThreshold -> "Critical: $dlqCount messages in DLQ"
                dlqCount > config.dlqWarningThreshold -> "Warning: $dlqCount messages in DLQ"
                staleCount > config.staleThreshold -> "Warning: $staleCount stale entries"
                pending > config.pendingWarningThreshold -> "Warning: $pending pending messages"
                else -> null
            }
            
            ComponentHealth(
                name = componentName,
                status = status,
                latencyMs = latency,
                message = message,
                details = details
            )
        } catch (e: Exception) {
            logger.error("Outbox health check failed", e)
            ComponentHealth.unhealthy(componentName, e.message ?: "Check failed")
        }
    }
}

/**
 * Outbox Health Check 설정
 */
data class OutboxHealthConfig(
    val pendingWarningThreshold: Long = 1000,
    val dlqWarningThreshold: Long = 10,
    val dlqCriticalThreshold: Long = 50,
    val staleThreshold: Long = 20
)
