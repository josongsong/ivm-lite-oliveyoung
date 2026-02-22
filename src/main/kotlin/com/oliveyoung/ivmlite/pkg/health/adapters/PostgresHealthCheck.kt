package com.oliveyoung.ivmlite.pkg.health.adapters

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * PostgreSQL Health Check
 */
class PostgresHealthCheck(
    private val database: Database
) : HealthCheckPort {

    private val logger = LoggerFactory.getLogger(PostgresHealthCheck::class.java)

    override val componentName = "PostgreSQL"

    override suspend fun check(): ComponentHealth {
        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    exec("SELECT 1") {}
                }
                val latency = System.currentTimeMillis() - startTime

                val details = mutableMapOf<String, Any>()

                try {
                    transaction(database) {
                        val activeConnections = exec(
                            "SELECT count(*) FROM pg_stat_activity WHERE state = 'active'"
                        ) { rs ->
                            if (rs.next()) rs.getInt(1) else 0
                        } ?: 0
                        details["active_connections"] = activeConnections

                        val maxConnections = exec("SHOW max_connections") { rs ->
                            if (rs.next()) rs.getString(1).toIntOrNull() ?: 100 else 100
                        } ?: 100
                        details["max_connections"] = maxConnections

                        val connectionUsage = activeConnections.toDouble() / maxConnections * 100
                        details["connection_usage_percent"] = "%.1f".format(connectionUsage)

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
                    }
                } catch (e: Exception) {
                    ComponentHealth.healthy(componentName, latency)
                }
            } catch (e: Exception) {
                logger.error("PostgreSQL health check failed", e)
                ComponentHealth.unhealthy(componentName, e.message ?: "Connection failed")
            }
        }
    }
}
