package com.oliveyoung.ivmlite.pkg.alerts.ports

import com.oliveyoung.ivmlite.pkg.alerts.domain.Alert
import com.oliveyoung.ivmlite.pkg.alerts.domain.NotificationChannel

/**
 * Notifier Port
 * 
 * Alert 알림 발송을 담당한다.
 * 채널별로 다른 어댑터 구현체가 있다.
 */
interface NotifierPort {
    
    /**
     * 지원하는 채널
     */
    val channel: NotificationChannel
    
    /**
     * Alert 발송
     * 
     * @param alert 발송할 Alert
     * @return 성공 시 true, 실패 시 false
     */
    suspend fun send(alert: Alert): Boolean
    
    /**
     * 해결 알림 발송 (선택적)
     */
    suspend fun sendResolved(alert: Alert): Boolean = true
    
    /**
     * 채널 활성화 여부
     */
    fun isEnabled(): Boolean = true
}

/**
 * Notifier 설정
 */
data class NotifierConfig(
    /** Slack Webhook URL */
    val slackWebhookUrl: String? = null,
    
    /** 이메일 수신자 목록 */
    val emailRecipients: List<String> = emptyList(),
    
    /** SMTP 설정 */
    val smtpHost: String? = null,
    val smtpPort: Int = 587,
    val smtpUser: String? = null,
    val smtpPassword: String? = null,
    
    /** 외부 Webhook URL */
    val webhookUrl: String? = null,
    
    /** 채널별 활성화 여부 */
    val enabledChannels: Set<NotificationChannel> = setOf(NotificationChannel.UI)
)
