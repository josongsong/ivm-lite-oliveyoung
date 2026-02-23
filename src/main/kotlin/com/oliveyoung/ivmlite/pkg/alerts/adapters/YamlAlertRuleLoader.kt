package com.oliveyoung.ivmlite.pkg.alerts.adapters

import com.oliveyoung.ivmlite.pkg.alerts.domain.*
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * YAML 파일에서 AlertRule 로드
 *
 * 리소스 경로 또는 파일 경로에서 YAML 규칙을 로드.
 *
 * YAML 형식 예시:
 * ```yaml
 * rules:
 *   - id: dlq-threshold
 *     name: SinkEvent 실패 누적
 *     description: 처리 실패한 SinkEvent가 10개 이상
 *     condition:
 *       type: threshold
 *       metricName: sink_event.failed.count
 *       operator: GT
 *       value: 10.0
 *     severity: CRITICAL
 *     channels: [SLACK, UI]
 *     cooldownMinutes: 5
 *     enabled: true
 *   - id: boolean-check
 *     condition:
 *       type: boolean
 *       metricName: health.postgres.connected
 *       expectedValue: false
 * ```
 */
class YamlAlertRuleLoader(
    private val resourcePath: String = "/alerts/rules.yaml",
) : AlertRuleLoaderPort {

    private val log = LoggerFactory.getLogger(YamlAlertRuleLoader::class.java)
    private var cachedRules: List<AlertRule> = emptyList()

    init {
        reload()
    }

    override fun loadAll(): List<AlertRule> = cachedRules

    override fun findById(id: String): AlertRule? = cachedRules.find { it.id == id }

    override fun reload() {
        cachedRules = loadFromYaml()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromYaml(): List<AlertRule> {
        return try {
            val stream = javaClass.getResourceAsStream(resourcePath)
                ?: javaClass.getResourceAsStream("/$resourcePath")
                ?: return emptyList()

            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(stream) ?: return emptyList()
            val rulesRaw = data["rules"] as? List<Map<String, Any>> ?: return emptyList()

            rulesRaw.mapNotNull { parseRule(it) }
        } catch (e: Exception) {
            log.warn("Failed to load alert rules from {}: {}", resourcePath, e.message)
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRule(raw: Map<String, Any>): AlertRule? {
        return try {
            val id = raw["id"]?.toString() ?: return null
            val name = raw["name"]?.toString() ?: id
            val description = raw["description"]?.toString() ?: ""
            val condition = parseCondition(raw["condition"] as? Map<String, Any> ?: return null)
            val severity = AlertSeverity.valueOf((raw["severity"]?.toString() ?: "WARNING").uppercase())
            val channelsRaw = raw["channels"]
            val channels = when (channelsRaw) {
                is List<*> -> channelsRaw.mapNotNull { NotificationChannel.entries.find { e -> e.name == it.toString() } }.toSet()
                is String -> setOf(NotificationChannel.entries.find { it.name == channelsRaw } ?: NotificationChannel.UI)
                else -> setOf(NotificationChannel.UI)
            }
            if (channels.isEmpty()) return null

            val cooldownMinutes = (raw["cooldownMinutes"] as? Number)?.toLong() ?: 5L
            val enabled = (raw["enabled"] as? Boolean) ?: true

            AlertRule(
                id = id,
                name = name,
                description = description,
                condition = condition,
                severity = severity,
                channels = channels,
                cooldown = Duration.of(cooldownMinutes, ChronoUnit.MINUTES),
                enabled = enabled,
            )
        } catch (e: Exception) {
            log.warn("Failed to parse alert rule {}: {}", raw["id"], e.message)
            null
        }
    }

    private fun parseCondition(raw: Map<String, Any>): AlertCondition {
        val type = raw["type"]?.toString()?.uppercase() ?: "THRESHOLD"
        return when (type) {
            "THRESHOLD" -> AlertCondition.Threshold(
                metricName = raw["metricName"]?.toString() ?: "",
                operator = ThresholdOperator.valueOf((raw["operator"]?.toString() ?: "GT").uppercase()),
                value = (raw["value"] as? Number)?.toDouble() ?: 0.0,
            )
            "BOOLEAN" -> AlertCondition.BooleanCheck(
                metricName = raw["metricName"]?.toString() ?: "",
                expectedValue = raw["expectedValue"] as? Boolean ?: false,
            )
            "RATE" -> AlertCondition.RateCheck(
                metricName = raw["metricName"]?.toString() ?: "",
                windowSeconds = (raw["windowSeconds"] as? Number)?.toLong() ?: 60L,
                operator = ThresholdOperator.valueOf((raw["operator"]?.toString() ?: "GT").uppercase()),
                value = (raw["value"] as? Number)?.toDouble() ?: 0.0,
            )
            else -> AlertCondition.Threshold(
                metricName = raw["metricName"]?.toString() ?: "",
                operator = ThresholdOperator.GT,
                value = (raw["value"] as? Number)?.toDouble() ?: 0.0,
            )
        }
    }
}
