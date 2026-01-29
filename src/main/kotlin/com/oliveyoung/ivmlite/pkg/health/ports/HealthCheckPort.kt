package com.oliveyoung.ivmlite.pkg.health.ports

import com.oliveyoung.ivmlite.pkg.health.domain.ComponentHealth

/**
 * Health Check Port
 * 
 * 개별 컴포넌트의 Health Check를 담당한다.
 */
interface HealthCheckPort {
    
    /**
     * 컴포넌트 이름
     */
    val componentName: String
    
    /**
     * Health Check 실행
     */
    suspend fun check(): ComponentHealth
    
    /**
     * 체크 활성화 여부
     */
    fun isEnabled(): Boolean = true
}
