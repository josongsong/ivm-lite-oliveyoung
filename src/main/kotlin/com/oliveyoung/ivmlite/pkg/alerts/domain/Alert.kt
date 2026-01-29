package com.oliveyoung.ivmlite.pkg.alerts.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 발생한 Alert 엔티티
 * 
 * AlertRule의 조건이 충족되면 생성되어 상태를 추적한다.
 *
 * @property id 고유 식별자
 * @property ruleId 발생 원인 규칙 ID
 * @property name Alert 이름 (규칙에서 복사)
 * @property description 상세 설명
 * @property severity 심각도
 * @property status 현재 상태
 * @property context 발생 시점의 메트릭/컨텍스트 정보
 * @property firedAt 최초 발생 시각
 * @property acknowledgedAt 확인 시각
 * @property acknowledgedBy 확인한 사용자
 * @property resolvedAt 해결 시각
 * @property silencedUntil 무음 해제 시각
 * @property occurrences 발생 횟수 (연속 발생 카운트)
 * @property labels 추가 메타데이터
 */
data class Alert(
    val id: UUID,
    val ruleId: String,
    val name: String,
    val description: String,
    val severity: AlertSeverity,
    val status: AlertStatus,
    val context: Map<String, String>,
    val firedAt: Instant,
    val acknowledgedAt: Instant? = null,
    val acknowledgedBy: String? = null,
    val resolvedAt: Instant? = null,
    val silencedUntil: Instant? = null,
    val occurrences: Int = 1,
    val labels: Map<String, String> = emptyMap()
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(occurrences >= 1) { "occurrences must be at least 1" }
    }
    
    companion object {
        /**
         * 새 Alert 생성 (FIRING 상태)
         */
        fun fire(
            rule: AlertRule,
            context: Map<String, String>,
            timestamp: Instant = Instant.now()
        ): Alert = Alert(
            id = UUID.randomUUID(),
            ruleId = rule.id,
            name = rule.name,
            description = rule.description,
            severity = rule.severity,
            status = AlertStatus.FIRING,
            context = context,
            firedAt = timestamp,
            labels = rule.labels
        )
    }
    
    /**
     * 사용자가 Alert 확인
     */
    fun acknowledge(by: String, at: Instant = Instant.now()): Alert {
        require(status == AlertStatus.FIRING) { "Can only acknowledge FIRING alerts" }
        return copy(
            status = AlertStatus.ACKNOWLEDGED,
            acknowledgedAt = at,
            acknowledgedBy = by
        )
    }
    
    /**
     * Alert 해결 (조건이 더 이상 충족되지 않음)
     */
    fun resolve(at: Instant = Instant.now()): Alert {
        require(status != AlertStatus.RESOLVED) { "Alert is already resolved" }
        return copy(
            status = AlertStatus.RESOLVED,
            resolvedAt = at
        )
    }
    
    /**
     * 일시적으로 무음 처리
     */
    fun silence(duration: Duration, at: Instant = Instant.now()): Alert {
        return copy(
            status = AlertStatus.SILENCED,
            silencedUntil = at.plus(duration)
        )
    }
    
    /**
     * 동일 조건 재발생 (카운트 증가)
     */
    fun incrementOccurrence(): Alert {
        require(status == AlertStatus.FIRING || status == AlertStatus.ACKNOWLEDGED) {
            "Cannot increment resolved or silenced alerts"
        }
        return copy(occurrences = occurrences + 1)
    }
    
    /**
     * 무음 상태 해제 여부 확인
     */
    fun isSilenceExpired(now: Instant = Instant.now()): Boolean =
        status == AlertStatus.SILENCED && silencedUntil != null && now.isAfter(silencedUntil)
    
    /**
     * 활성 상태 여부 (FIRING 또는 ACKNOWLEDGED)
     */
    fun isActive(): Boolean = status == AlertStatus.FIRING || status == AlertStatus.ACKNOWLEDGED
    
    /**
     * Alert 지속 시간
     */
    fun duration(now: Instant = Instant.now()): Duration = Duration.between(firedAt, resolvedAt ?: now)
    
    /**
     * 알림 메시지 생성
     */
    fun toMessage(): String = buildString {
        append("[${severity.name}] $name")
        if (description.isNotBlank()) {
            append("\n$description")
        }
        if (context.isNotEmpty()) {
            append("\nContext: ")
            append(context.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        append("\nFired at: $firedAt")
        if (occurrences > 1) {
            append(" (${occurrences}x)")
        }
    }
    
    /**
     * Slack 메시지 포맷
     */
    fun toSlackPayload(): Map<String, Any> {
        val emoji = when (severity) {
            AlertSeverity.CRITICAL -> "🚨"
            AlertSeverity.WARNING -> "⚠️"
            AlertSeverity.INFO -> "ℹ️"
        }
        
        val color = when (severity) {
            AlertSeverity.CRITICAL -> "#dc3545"  // red
            AlertSeverity.WARNING -> "#ffc107"   // yellow
            AlertSeverity.INFO -> "#17a2b8"      // blue
        }
        
        return mapOf(
            "attachments" to listOf(
                mapOf(
                    "color" to color,
                    "blocks" to listOf(
                        mapOf(
                            "type" to "section",
                            "text" to mapOf(
                                "type" to "mrkdwn",
                                "text" to "$emoji *$name*\n$description"
                            )
                        ),
                        mapOf(
                            "type" to "section",
                            "fields" to context.map { (k, v) ->
                                mapOf(
                                    "type" to "mrkdwn",
                                    "text" to "*$k*\n$v"
                                )
                            }
                        ),
                        mapOf(
                            "type" to "context",
                            "elements" to listOf(
                                mapOf(
                                    "type" to "mrkdwn",
                                    "text" to "Rule: `$ruleId` | Fired: $firedAt"
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
