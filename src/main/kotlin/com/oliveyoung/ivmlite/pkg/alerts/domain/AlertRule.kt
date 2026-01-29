package com.oliveyoung.ivmlite.pkg.alerts.domain

import java.time.Duration

/**
 * Alert 규칙 정의
 * 
 * 메트릭 조건과 알림 설정을 정의한다.
 * YAML 파일에서 로드되거나 프로그래밍 방식으로 생성될 수 있다.
 *
 * @property id 규칙 고유 식별자 (예: "dlq-threshold")
 * @property name 사람이 읽을 수 있는 이름
 * @property description 규칙 설명
 * @property condition 조건 표현식 (예: "dlq.count > 10")
 * @property severity 심각도
 * @property channels 알림을 보낼 채널들
 * @property cooldown 재알림 방지 시간 (같은 조건 연속 발생 시)
 * @property enabled 규칙 활성화 여부
 * @property labels 추가 메타데이터
 */
data class AlertRule(
    val id: String,
    val name: String,
    val description: String = "",
    val condition: AlertCondition,
    val severity: AlertSeverity,
    val channels: Set<NotificationChannel>,
    val cooldown: Duration = Duration.ofMinutes(5),
    val enabled: Boolean = true,
    val labels: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Rule id must not be blank" }
        require(name.isNotBlank()) { "Rule name must not be blank" }
        require(channels.isNotEmpty()) { "At least one notification channel is required" }
    }
    
    companion object {
        /**
         * 빌더 스타일로 AlertRule 생성
         */
        fun builder(id: String, name: String) = AlertRuleBuilder(id, name)
    }
}

/**
 * Alert 조건 정의
 * 
 * 다양한 조건 타입을 지원:
 * - Threshold: 값이 임계치를 초과/미만
 * - Comparison: 두 메트릭 비교
 * - Boolean: 상태 기반 (running == false)
 * - Rate: 변화율 기반
 */
sealed class AlertCondition {
    abstract fun evaluate(metrics: MetricSnapshot): Boolean
    
    /**
     * 임계치 조건 (예: dlq.count > 10)
     */
    data class Threshold(
        val metricName: String,
        val operator: ThresholdOperator,
        val value: Double
    ) : AlertCondition() {
        override fun evaluate(metrics: MetricSnapshot): Boolean {
            val metricValue = metrics.getDouble(metricName) ?: return false
            return operator.compare(metricValue, value)
        }
    }
    
    /**
     * 불린 조건 (예: worker.running == false)
     */
    data class BooleanCheck(
        val metricName: String,
        val expectedValue: Boolean
    ) : AlertCondition() {
        override fun evaluate(metrics: MetricSnapshot): Boolean {
            val metricValue = metrics.getBoolean(metricName) ?: return false
            return metricValue == expectedValue
        }
    }
    
    /**
     * 변화율 조건 (예: failure_rate > 0.1)
     */
    data class RateCheck(
        val metricName: String,
        val windowSeconds: Long,
        val operator: ThresholdOperator,
        val value: Double
    ) : AlertCondition() {
        override fun evaluate(metrics: MetricSnapshot): Boolean {
            val rate = metrics.getRate(metricName, windowSeconds) ?: return false
            return operator.compare(rate, value)
        }
    }
    
    /**
     * 복합 조건 (AND)
     */
    data class And(val conditions: List<AlertCondition>) : AlertCondition() {
        override fun evaluate(metrics: MetricSnapshot): Boolean =
            conditions.all { it.evaluate(metrics) }
    }
    
    /**
     * 복합 조건 (OR)
     */
    data class Or(val conditions: List<AlertCondition>) : AlertCondition() {
        override fun evaluate(metrics: MetricSnapshot): Boolean =
            conditions.any { it.evaluate(metrics) }
    }
}

enum class ThresholdOperator {
    GT { override fun compare(a: Double, b: Double) = a > b },
    GTE { override fun compare(a: Double, b: Double) = a >= b },
    LT { override fun compare(a: Double, b: Double) = a < b },
    LTE { override fun compare(a: Double, b: Double) = a <= b },
    EQ { override fun compare(a: Double, b: Double) = a == b },
    NEQ { override fun compare(a: Double, b: Double) = a != b };
    
    abstract fun compare(a: Double, b: Double): Boolean
}

/**
 * 메트릭 스냅샷 (Alert 평가용)
 */
data class MetricSnapshot(
    val values: Map<String, Any>,
    val rates: Map<String, Map<Long, Double>> = emptyMap(), // metricName -> (windowSeconds -> rate)
    val timestamp: java.time.Instant = java.time.Instant.now()
) {
    fun getDouble(name: String): Double? = when (val v = values[name]) {
        is Number -> v.toDouble()
        else -> null
    }
    
    fun getBoolean(name: String): Boolean? = values[name] as? Boolean
    
    fun getLong(name: String): Long? = when (val v = values[name]) {
        is Number -> v.toLong()
        else -> null
    }
    
    fun getString(name: String): String? = values[name]?.toString()
    
    fun getRate(name: String, windowSeconds: Long): Double? = rates[name]?.get(windowSeconds)
}

/**
 * AlertRule 빌더
 */
class AlertRuleBuilder(private val id: String, private val name: String) {
    private var description: String = ""
    private var condition: AlertCondition? = null
    private var severity: AlertSeverity = AlertSeverity.WARNING
    private var channels: MutableSet<NotificationChannel> = mutableSetOf(NotificationChannel.UI)
    private var cooldown: Duration = Duration.ofMinutes(5)
    private var enabled: Boolean = true
    private var labels: MutableMap<String, String> = mutableMapOf()
    
    fun description(desc: String) = apply { this.description = desc }
    fun condition(cond: AlertCondition) = apply { this.condition = cond }
    fun severity(sev: AlertSeverity) = apply { this.severity = sev }
    fun channels(vararg chs: NotificationChannel) = apply { this.channels.addAll(chs) }
    fun cooldown(dur: Duration) = apply { this.cooldown = dur }
    fun enabled(flag: Boolean) = apply { this.enabled = flag }
    fun label(key: String, value: String) = apply { this.labels[key] = value }
    
    // DSL-style condition builders
    fun whenMetric(metricName: String) = ConditionBuilder(metricName)
    
    inner class ConditionBuilder(private val metricName: String) {
        fun greaterThan(value: Double) = apply {
            condition = AlertCondition.Threshold(metricName, ThresholdOperator.GT, value)
        }
        fun lessThan(value: Double) = apply {
            condition = AlertCondition.Threshold(metricName, ThresholdOperator.LT, value)
        }
        fun equals(value: Boolean) = apply {
            condition = AlertCondition.BooleanCheck(metricName, value)
        }
    }
    
    fun build(): AlertRule {
        requireNotNull(condition) { "Condition is required" }
        return AlertRule(
            id = id,
            name = name,
            description = description,
            condition = condition!!,
            severity = severity,
            channels = channels.toSet(),
            cooldown = cooldown,
            enabled = enabled,
            labels = labels.toMap()
        )
    }
}
