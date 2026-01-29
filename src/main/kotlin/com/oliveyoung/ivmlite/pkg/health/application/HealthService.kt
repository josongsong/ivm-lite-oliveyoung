package com.oliveyoung.ivmlite.pkg.health.application

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth
import com.oliveyoung.ivmlite.pkg.health.domain.HealthStatus
import com.oliveyoung.ivmlite.pkg.health.domain.SystemHealth
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Health Service
 * 
 * 모든 컴포넌트의 Health Check를 수행하고 종합 상태를 반환한다.
 */
class HealthService(
    private val healthChecks: List<HealthCheckPort>,
    private val config: HealthServiceConfig = HealthServiceConfig()
) {
    private val logger = LoggerFactory.getLogger(HealthService::class.java)
    private val startTime = Instant.now()
    private val version: String = loadVersion()
    
    /**
     * 전체 시스템 Health Check
     */
    suspend fun checkAll(): SystemHealth {
        val components = coroutineScope {
            healthChecks
                .filter { it.isEnabled() }
                .map { check ->
                    async {
                        try {
                            withTimeout(config.checkTimeoutMs) {
                                check.check()
                            }
                        } catch (e: Exception) {
                            logger.warn("Health check timeout for {}", check.componentName)
                            ComponentHealth(
                                name = check.componentName,
                                status = HealthStatus.UNKNOWN,
                                message = "Check timeout",
                                error = e.message
                            )
                        }
                    }
                }
                .awaitAll()
        }
        
        val uptime = java.time.Duration.between(startTime, Instant.now()).seconds
        
        return SystemHealth.from(components, version, uptime)
    }
    
    /**
     * 특정 컴포넌트 Health Check
     */
    suspend fun checkComponent(name: String): ComponentHealth? {
        val check = healthChecks.find { it.componentName.equals(name, ignoreCase = true) }
            ?: return null
        
        return try {
            withTimeout(config.checkTimeoutMs) {
                check.check()
            }
        } catch (e: Exception) {
            ComponentHealth.unhealthy(name, "Check failed: ${e.message}")
        }
    }
    
    /**
     * Liveness Probe (단순 alive 체크)
     */
    fun liveness(): Boolean = true
    
    /**
     * Readiness Probe (서비스 가능 여부)
     * 
     * 핵심 컴포넌트만 체크 (빠른 응답)
     */
    suspend fun readiness(): Boolean {
        // PostgreSQL 연결만 확인
        val pgCheck = healthChecks.find { it.componentName == "PostgreSQL" }
            ?: return true
        
        return try {
            withTimeout(config.readinessTimeoutMs) {
                val result = pgCheck.check()
                result.status.isOk()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 컴포넌트 목록
     */
    fun getComponentNames(): List<String> = healthChecks.map { it.componentName }
    
    /**
     * 버전 정보
     */
    fun getVersion(): String = version
    
    /**
     * Uptime (초)
     */
    fun getUptime(): Long = java.time.Duration.between(startTime, Instant.now()).seconds
    
    private fun loadVersion(): String {
        return try {
            javaClass.getResourceAsStream("/VERSION")
                ?.bufferedReader()
                ?.readLine()
                ?.trim()
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

/**
 * HealthService 설정
 */
data class HealthServiceConfig(
    /** 개별 체크 타임아웃 (ms) */
    val checkTimeoutMs: Long = 5000,
    
    /** Readiness 체크 타임아웃 (ms) */
    val readinessTimeoutMs: Long = 2000
)
