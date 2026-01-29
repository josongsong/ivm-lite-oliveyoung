package com.oliveyoung.ivmlite.pkg.alerts.domain

/**
 * Alert 심각도 레벨
 */
enum class AlertSeverity {
    /** 즉시 조치 필요 (장애) */
    CRITICAL,
    
    /** 주의 필요 (성능 저하, 임계치 근접) */
    WARNING,
    
    /** 정보성 알림 */
    INFO;
    
    fun isHigherThan(other: AlertSeverity): Boolean = this.ordinal < other.ordinal
}
