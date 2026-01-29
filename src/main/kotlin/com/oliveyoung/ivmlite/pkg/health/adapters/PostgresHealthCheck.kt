package com.oliveyoung.ivmlite.pkg.health.adapters

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

/**
 * PostgreSQL Health Check
 */
class PostgresHealthCheck(
    private val dsl: DSLContext
) : HealthCheckPort {
    
    private val logger = LoggerFactory.getLogger(PostgresHealthCheck::class.java)
    
    override val componentName = "PostgreSQL"
    
    override suspend fun check(): ComponentHealth {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 연결 테스트
            dsl.selectOne().fetch()
            val latency = System.currentTimeMillis() - startTime
            
            // 추가 정보 수집
            val details = mutableMapOf<String, Any>()
            
            try {
                // 활성 연결 수
                val activeConnections = dsl.fetchOne(
                    "SELECT count(*) FROM pg_stat_activity WHERE state = 'active'"
                )?.get(0, Int::class.java) ?: 0
                details["active_connections"] = activeConnections
                
                // 최대 연결 수
                val maxConnections = dsl.fetchOne(
                    "SHOW max_connections"
                )?.get(0, Int::class.java) ?: 100
                details["max_connections"] = maxConnections
                
                // 연결 사용률
                val connectionUsage = (activeConnections.toDouble() / maxConnections) * 100
                details["connection_usage_percent"] = "%.1f".format(connectionUsage)
                
                // 상태 판단
                val status = when {
                    latency > 1000 -> HealthStatus.DEGRADED
                    connectionUsage > 80 -> HealthStatus.DEGRADED
                    else -> HealthStatus.HEALTHY
                }
                
                val message = when (status) {
                    HealthStatus.DEGRADED -> when {
                        latency > 1000 -> "High latency: ${latency}ms"
                        connectionUsage > 80 -> "High connection usage: ${connectionUsage}%"
                        else -> null
                    }
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
                // 기본 연결은 성공했지만 상세 정보 수집 실패
                ComponentHealth.healthy(componentName, latency)
            }
        } catch (e: Exception) {
            logger.error("PostgreSQL health check failed", e)
            ComponentHealth.unhealthy(componentName, e.message ?: "Connection failed")
        }
    }
}
