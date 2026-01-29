package com.oliveyoung.ivmlite.pkg.health.domain

import java.time.Instant

/**
 * 개별 컴포넌트의 Health 상태
 *
 * @property name 컴포넌트 이름 (예: "PostgreSQL", "Worker")
 * @property status 현재 상태
 * @property latencyMs 응답 시간 (ms)
 * @property message 상태 메시지
 * @property details 상세 정보
 * @property checkedAt 체크 시각
 * @property error 에러 메시지 (UNHEALTHY인 경우)
 */
data class ComponentHealth(
    val name: String,
    val status: HealthStatus,
    val latencyMs: Long = 0,
    val message: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val checkedAt: Instant = Instant.now(),
    val error: String? = null
) {
    companion object {
        fun healthy(name: String, latencyMs: Long = 0, details: Map<String, Any> = emptyMap()) =
            ComponentHealth(name, HealthStatus.HEALTHY, latencyMs, details = details)
        
        fun degraded(name: String, message: String, latencyMs: Long = 0) =
            ComponentHealth(name, HealthStatus.DEGRADED, latencyMs, message)
        
        fun unhealthy(name: String, error: String) =
            ComponentHealth(name, HealthStatus.UNHEALTHY, error = error)
        
        fun unknown(name: String) =
            ComponentHealth(name, HealthStatus.UNKNOWN)
    }
}

/**
 * 전체 시스템 Health 상태
 *
 * @property overall 전체 상태 (가장 심각한 상태를 반영)
 * @property components 개별 컴포넌트 상태
 * @property timestamp 체크 시각
 * @property version 애플리케이션 버전
 * @property uptime 가동 시간 (초)
 */
data class SystemHealth(
    val overall: HealthStatus,
    val components: List<ComponentHealth>,
    val timestamp: Instant = Instant.now(),
    val version: String = "unknown",
    val uptime: Long = 0
) {
    companion object {
        /**
         * 컴포넌트들에서 전체 상태 계산
         */
        fun from(components: List<ComponentHealth>, version: String = "unknown", uptime: Long = 0): SystemHealth {
            val overall = when {
                components.any { it.status == HealthStatus.UNHEALTHY } -> HealthStatus.UNHEALTHY
                components.any { it.status == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
                components.all { it.status == HealthStatus.HEALTHY } -> HealthStatus.HEALTHY
                else -> HealthStatus.UNKNOWN
            }
            return SystemHealth(overall, components, version = version, uptime = uptime)
        }
    }
    
    /**
     * Liveness probe용 (단순 alive 체크)
     */
    fun isAlive(): Boolean = overall != HealthStatus.UNHEALTHY
    
    /**
     * Readiness probe용 (서비스 가능 여부)
     */
    fun isReady(): Boolean = overall == HealthStatus.HEALTHY || overall == HealthStatus.DEGRADED
}
