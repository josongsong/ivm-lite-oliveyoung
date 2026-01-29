package com.oliveyoung.ivmlite.pkg.alerts.domain

/**
 * Alert 상태
 */
enum class AlertStatus {
    /** 알림 발생 중 */
    FIRING,
    
    /** 사용자가 확인함 */
    ACKNOWLEDGED,
    
    /** 조건 해소되어 해결됨 */
    RESOLVED,
    
    /** 일시적으로 무음 처리 */
    SILENCED
}
