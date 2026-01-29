package com.oliveyoung.ivmlite.pkg.health.domain

/**
 * Health 상태
 */
enum class HealthStatus {
    /** 정상 */
    HEALTHY,
    
    /** 성능 저하 (동작하지만 문제 있음) */
    DEGRADED,
    
    /** 비정상 (동작 불가) */
    UNHEALTHY,
    
    /** 상태 알 수 없음 */
    UNKNOWN;
    
    fun isOk(): Boolean = this == HEALTHY || this == DEGRADED
}
