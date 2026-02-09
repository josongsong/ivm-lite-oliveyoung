package com.oliveyoung.ivmlite.pkg.alerts.application

import com.oliveyoung.ivmlite.pkg.alerts.domain.*
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.NotifierPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Alert Engine
 * 
 * 주기적으로 메트릭을 수집하고 규칙을 평가하여 Alert를 발생/해제한다.
 *
 * Features:
 * - Rule-based alert evaluation
 * - Cooldown management (중복 알림 방지)
 * - Multi-channel notification dispatch
 * - Auto-resolve when condition clears
 * - Silence/Acknowledge support
 */
class AlertEngine(
    private val metricCollector: MetricCollector,
    private val ruleLoader: AlertRuleLoaderPort,
    private val alertRepository: AlertRepositoryPort,
    private val notifiers: List<NotifierPort>,
    private val config: AlertEngineConfig = AlertEngineConfig()
) {
    private val logger = LoggerFactory.getLogger(AlertEngine::class.java)
    
    // 활성 Alert 캐시 (ruleId -> Alert)
    private val activeAlerts = ConcurrentHashMap<String, Alert>()
    
    // Cooldown 추적 (ruleId -> lastFiredAt)
    private val cooldowns = ConcurrentHashMap<String, Instant>()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var evaluationJob: Job? = null
    private val running = AtomicBoolean(false)
    
    // WebSocket 리스너들 (UI 실시간 알림용)
    private val alertListeners = mutableListOf<AlertListener>()
    
    /**
     * Alert 리스너 인터페이스
     */
    interface AlertListener {
        suspend fun onAlert(alert: Alert)
        suspend fun onResolved(alert: Alert)
    }
    
    /**
     * Engine 시작
     */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) {
            logger.warn("AlertEngine is already running")
            return false
        }
        
        logger.info("Starting AlertEngine [interval={}ms]", config.evaluationIntervalMs)
        
        // 기존 활성 Alert 로드
        scope.launch {
            loadActiveAlerts()
        }
        
        // 주기적 평가 시작
        evaluationJob = scope.launch {
            evaluationLoop()
        }
        
        return true
    }
    
    /**
     * Engine 정지
     */
    suspend fun stop(): Boolean {
        if (!running.get()) {
            return false
        }
        
        logger.info("Stopping AlertEngine")
        running.set(false)
        evaluationJob?.cancelAndJoin()
        
        return true
    }
    
    fun isRunning(): Boolean = running.get()
    
    /**
     * 리스너 등록 (WebSocket 등)
     */
    fun addListener(listener: AlertListener) {
        alertListeners.add(listener)
    }
    
    fun removeListener(listener: AlertListener) {
        alertListeners.remove(listener)
    }
    
    /**
     * 수동 평가 트리거 (테스트/디버깅용)
     */
    suspend fun evaluateNow(): EvaluationResult {
        return evaluate()
    }
    
    /**
     * Alert 확인 처리
     */
    suspend fun acknowledge(alertId: java.util.UUID, by: String): Alert? {
        val alert = when (val r = alertRepository.findById(alertId)) {
            is Result.Ok -> r.value
            is Result.Err -> return null
        } ?: return null
        
        if (!alert.isActive()) return null
        
        val acknowledged = alert.acknowledge(by)
        alertRepository.save(acknowledged)
        activeAlerts[alert.ruleId] = acknowledged
        
        logger.info("Alert acknowledged: {} by {}", alertId, by)
        return acknowledged
    }
    
    /**
     * Alert 무음 처리
     */
    suspend fun silence(alertId: java.util.UUID, duration: Duration): Alert? {
        val alert = when (val r = alertRepository.findById(alertId)) {
            is Result.Ok -> r.value
            is Result.Err -> return null
        } ?: return null
        
        val silenced = alert.silence(duration)
        alertRepository.save(silenced)
        activeAlerts.remove(alert.ruleId)
        
        logger.info("Alert silenced: {} for {}", alertId, duration)
        return silenced
    }
    
    /**
     * 활성 Alert 목록
     */
    fun getActiveAlerts(): List<Alert> = activeAlerts.values.toList()
    
    // ==================== Internal ====================
    
    private suspend fun loadActiveAlerts() {
        when (val result = alertRepository.findAllActive()) {
            is Result.Ok -> {
                result.value.forEach { alert ->
                    activeAlerts[alert.ruleId] = alert
                }
                logger.info("Loaded {} active alerts", result.value.size)
            }
            is Result.Err -> {
                logger.error("Failed to load active alerts: {}", result.error)
            }
        }
    }
    
    private suspend fun evaluationLoop() {
        while (running.get()) {
            try {
                evaluate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Evaluation error", e)
            }
            
            delay(config.evaluationIntervalMs)
        }
    }
    
    private suspend fun evaluate(): EvaluationResult {
        val metrics = metricCollector.collect()
        val rules = ruleLoader.loadEnabled()
        
        var fired = 0
        var resolved = 0
        
        for (rule in rules) {
            try {
                val shouldFire = rule.condition.evaluate(metrics)
                val existing = activeAlerts[rule.id]
                
                when {
                    // 새로 발생
                    shouldFire && existing == null -> {
                        if (canFire(rule)) {
                            fireAlert(rule, metrics)
                            fired++
                        }
                    }
                    
                    // 이미 발생 중 - occurrence 증가
                    shouldFire && existing != null && existing.isActive() -> {
                        val updated = existing.incrementOccurrence()
                        alertRepository.save(updated)
                        activeAlerts[rule.id] = updated
                    }
                    
                    // 조건 해소 - 해결
                    !shouldFire && existing != null && existing.isActive() -> {
                        resolveAlert(existing)
                        resolved++
                    }
                }
            } catch (e: Exception) {
                logger.error("Error evaluating rule {}: {}", rule.id, e.message)
            }
        }
        
        // 만료된 silence 처리
        handleExpiredSilences()
        
        return EvaluationResult(
            rulesEvaluated = rules.size,
            alertsFired = fired,
            alertsResolved = resolved,
            activeCount = activeAlerts.size
        )
    }
    
    private fun canFire(rule: AlertRule): Boolean {
        val lastFired = cooldowns[rule.id] ?: return true
        return Instant.now().isAfter(lastFired.plus(rule.cooldown))
    }
    
    private suspend fun fireAlert(rule: AlertRule, metrics: MetricSnapshot) {
        val context = buildContext(rule, metrics)
        val alert = Alert.fire(rule, context)
        
        // 저장
        alertRepository.save(alert)
        activeAlerts[rule.id] = alert
        cooldowns[rule.id] = Instant.now()
        
        logger.warn("🚨 Alert fired: {} [{}]", rule.name, rule.severity)
        
        // 알림 발송
        dispatchNotifications(alert, rule.channels)
        
        // 리스너 알림
        alertListeners.forEach { 
            try { it.onAlert(alert) } catch (e: Exception) { /* ignore */ }
        }
    }
    
    private suspend fun resolveAlert(alert: Alert) {
        val resolved = alert.resolve()
        alertRepository.save(resolved)
        activeAlerts.remove(alert.ruleId)
        
        logger.info("✅ Alert resolved: {} (duration={})", alert.name, alert.duration())
        
        // 해결 알림 발송
        notifiers.forEach { notifier ->
            try {
                notifier.sendResolved(resolved)
            } catch (e: Exception) {
                logger.warn("Failed to send resolved notification: {}", e.message)
            }
        }
        
        // 리스너 알림
        alertListeners.forEach {
            try { it.onResolved(resolved) } catch (e: Exception) { /* ignore */ }
        }
    }
    
    private suspend fun dispatchNotifications(alert: Alert, channels: Set<NotificationChannel>) {
        channels.forEach { channel ->
            val notifier = notifiers.find { it.channel == channel && it.isEnabled() }
            if (notifier != null) {
                scope.launch {
                    try {
                        val success = notifier.send(alert)
                        if (!success) {
                            logger.warn("Notification failed for channel: {}", channel)
                        }
                    } catch (e: Exception) {
                        logger.error("Notification error for channel {}: {}", channel, e.message)
                    }
                }
            }
        }
    }
    
    private suspend fun handleExpiredSilences() {
        when (val result = alertRepository.findExpiredSilenced()) {
            is Result.Ok -> {
                result.value.forEach { alert ->
                    // Silence 만료 → 다시 평가 대상으로
                    val metrics = metricCollector.collect()
                    val rule = ruleLoader.findById(alert.ruleId)
                    
                    if (rule != null && rule.condition.evaluate(metrics)) {
                        // 여전히 조건 충족 → 다시 FIRING
                        val refired = alert.copy(
                            status = AlertStatus.FIRING,
                            silencedUntil = null
                        )
                        alertRepository.save(refired)
                        activeAlerts[rule.id] = refired
                        dispatchNotifications(refired, rule.channels)
                    } else {
                        // 조건 해소 → RESOLVED
                        resolveAlert(alert)
                    }
                }
            }
            is Result.Err -> {
                logger.warn("Failed to find expired silences: {}", result.error)
            }
        }
    }
    
    private fun buildContext(rule: AlertRule, metrics: MetricSnapshot): Map<String, String> {
        val context = mutableMapOf<String, String>()
        
        // 관련 메트릭 값들 추출
        when (val cond = rule.condition) {
            is AlertCondition.Threshold -> {
                metrics.getDouble(cond.metricName)?.let {
                    context[cond.metricName] = it.toString()
                    context["threshold"] = "${cond.operator.name} ${cond.value}"
                }
            }
            is AlertCondition.BooleanCheck -> {
                metrics.getBoolean(cond.metricName)?.let {
                    context[cond.metricName] = it.toString()
                }
            }
            is AlertCondition.RateCheck -> {
                metrics.getRate(cond.metricName, cond.windowSeconds)?.let {
                    context["${cond.metricName}.rate"] = "%.4f".format(it)
                }
            }
            else -> {}
        }
        
        context["evaluated_at"] = metrics.timestamp.toString()
        
        return context
    }
    
    /**
     * 평가 결과
     */
    data class EvaluationResult(
        val rulesEvaluated: Int,
        val alertsFired: Int,
        val alertsResolved: Int,
        val activeCount: Int
    )
}

/**
 * AlertEngine 설정
 */
data class AlertEngineConfig(
    /** 평가 주기 (ms) */
    val evaluationIntervalMs: Long = 10_000L,  // 10초
    
    /** 해결된 Alert 보관 기간 */
    val resolvedRetentionDays: Int = 30,
    
    /** 최대 활성 Alert 수 */
    val maxActiveAlerts: Int = 1000
)
