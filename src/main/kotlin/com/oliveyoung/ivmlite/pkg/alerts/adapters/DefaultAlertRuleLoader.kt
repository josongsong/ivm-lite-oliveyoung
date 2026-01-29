package com.oliveyoung.ivmlite.pkg.alerts.adapters

import com.oliveyoung.ivmlite.pkg.alerts.domain.*
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import java.time.Duration

/**
 * 기본 Alert Rule Loader
 * 
 * 하드코딩된 기본 규칙들을 제공한다.
 * TODO: YAML 파일에서 로드하도록 확장 가능
 */
class DefaultAlertRuleLoader : AlertRuleLoaderPort {
    
    private val rules: MutableList<AlertRule> = mutableListOf()
    
    init {
        loadDefaultRules()
    }
    
    override fun loadAll(): List<AlertRule> = rules.toList()
    
    override fun findById(id: String): AlertRule? = rules.find { it.id == id }
    
    override fun reload() {
        rules.clear()
        loadDefaultRules()
    }
    
    private fun loadDefaultRules() {
        rules.addAll(listOf(
            // P0: DLQ 임계치 초과
            AlertRule(
                id = "dlq-threshold",
                name = "DLQ 메시지 누적",
                description = "Dead Letter Queue에 처리 실패 메시지가 10개 이상 누적되었습니다.",
                condition = AlertCondition.Threshold(
                    metricName = "outbox.dlq.count",
                    operator = ThresholdOperator.GT,
                    value = 10.0
                ),
                severity = AlertSeverity.CRITICAL,
                channels = setOf(NotificationChannel.SLACK, NotificationChannel.UI),
                cooldown = Duration.ofMinutes(5)
            ),
            
            // P0: Worker 중지
            AlertRule(
                id = "worker-down",
                name = "Worker 중지됨",
                description = "Outbox Polling Worker가 중지되었습니다. 메시지 처리가 불가능합니다.",
                condition = AlertCondition.BooleanCheck(
                    metricName = "worker.running",
                    expectedValue = false
                ),
                severity = AlertSeverity.CRITICAL,
                channels = setOf(NotificationChannel.SLACK, NotificationChannel.UI),
                cooldown = Duration.ofMinutes(1)
            ),
            
            // P1: 높은 실패율
            AlertRule(
                id = "high-failure-rate",
                name = "높은 처리 실패율",
                description = "최근 1분간 처리 실패율이 10%를 초과했습니다.",
                condition = AlertCondition.RateCheck(
                    metricName = "outbox.failed.rate",
                    windowSeconds = 60,
                    operator = ThresholdOperator.GT,
                    value = 0.1
                ),
                severity = AlertSeverity.WARNING,
                channels = setOf(NotificationChannel.SLACK, NotificationChannel.UI),
                cooldown = Duration.ofMinutes(10)
            ),
            
            // P1: Stale 엔트리 누적
            AlertRule(
                id = "stale-entries",
                name = "Stale 엔트리 누적",
                description = "PROCESSING 상태로 5분 이상 경과한 엔트리가 20개 이상 있습니다.",
                condition = AlertCondition.Threshold(
                    metricName = "outbox.stale.count",
                    operator = ThresholdOperator.GT,
                    value = 20.0
                ),
                severity = AlertSeverity.WARNING,
                channels = setOf(NotificationChannel.UI),
                cooldown = Duration.ofMinutes(15)
            ),
            
            // P1: Pending 큐 과다
            AlertRule(
                id = "pending-queue-high",
                name = "Pending 큐 과다",
                description = "처리 대기 중인 메시지가 1000개를 초과했습니다. 처리 지연이 발생할 수 있습니다.",
                condition = AlertCondition.Threshold(
                    metricName = "outbox.pending.count",
                    operator = ThresholdOperator.GT,
                    value = 1000.0
                ),
                severity = AlertSeverity.WARNING,
                channels = setOf(NotificationChannel.SLACK, NotificationChannel.UI),
                cooldown = Duration.ofMinutes(10)
            ),
            
            // P2: 파이프라인 지연
            AlertRule(
                id = "pipeline-lag",
                name = "파이프라인 처리 지연",
                description = "End-to-End 처리 시간이 5분을 초과했습니다.",
                condition = AlertCondition.Threshold(
                    metricName = "pipeline.e2e.latency_seconds",
                    operator = ThresholdOperator.GT,
                    value = 300.0
                ),
                severity = AlertSeverity.WARNING,
                channels = setOf(NotificationChannel.UI),
                cooldown = Duration.ofMinutes(10)
            ),
            
            // P2: DB 연결 실패
            AlertRule(
                id = "db-connection-failed",
                name = "데이터베이스 연결 실패",
                description = "PostgreSQL 데이터베이스에 연결할 수 없습니다.",
                condition = AlertCondition.BooleanCheck(
                    metricName = "health.postgres.connected",
                    expectedValue = false
                ),
                severity = AlertSeverity.CRITICAL,
                channels = setOf(NotificationChannel.SLACK, NotificationChannel.UI),
                cooldown = Duration.ofMinutes(1)
            ),
            
            // Info: 대량 처리 완료
            AlertRule(
                id = "bulk-processing-complete",
                name = "대량 처리 완료",
                description = "Backfill 또는 대량 처리 작업이 완료되었습니다.",
                condition = AlertCondition.BooleanCheck(
                    metricName = "backfill.completed",
                    expectedValue = true
                ),
                severity = AlertSeverity.INFO,
                channels = setOf(NotificationChannel.UI),
                cooldown = Duration.ofMinutes(1),
                enabled = false  // 기본 비활성화
            )
        ))
    }
    
    /**
     * 동적으로 규칙 추가 (런타임)
     */
    fun addRule(rule: AlertRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
    }
    
    /**
     * 규칙 비활성화
     */
    fun disableRule(id: String) {
        val index = rules.indexOfFirst { it.id == id }
        if (index >= 0) {
            rules[index] = rules[index].copy(enabled = false)
        }
    }
}
