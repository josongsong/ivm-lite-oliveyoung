package com.oliveyoung.ivmlite.pkg.alerts.domain

/**
 * 알림 채널 타입
 */
enum class NotificationChannel {
    /** Slack 웹훅 */
    SLACK,
    
    /** 이메일 */
    EMAIL,
    
    /** Admin UI WebSocket */
    UI,
    
    /** 외부 웹훅 */
    WEBHOOK
}
