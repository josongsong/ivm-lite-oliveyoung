package com.oliveyoung.ivmlite.pkg.alerts.adapters

import com.oliveyoung.ivmlite.pkg.alerts.domain.Alert
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertSeverity
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertStatus
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertFilter
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertStats
import com.oliveyoung.ivmlite.shared.domain.types.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory Alert Repository
 * 
 * 개발/테스트용 메모리 기반 구현.
 * 프로덕션에서는 JooqAlertRepository 사용 권장.
 */
class InMemoryAlertRepository : AlertRepositoryPort {
    
    private val alerts = ConcurrentHashMap<UUID, Alert>()
    
    override suspend fun save(alert: Alert): Result<Alert> {
        alerts[alert.id] = alert
        return Result.Ok(alert)
    }
    
    override suspend fun findById(id: UUID): Result<Alert?> {
        return Result.Ok(alerts[id])
    }
    
    override suspend fun findActiveByRuleId(ruleId: String): Result<Alert?> {
        val active = alerts.values.find { 
            it.ruleId == ruleId && it.isActive()
        }
        return Result.Ok(active)
    }
    
    override suspend fun findByStatus(status: AlertStatus, limit: Int): Result<List<Alert>> {
        val result = alerts.values
            .filter { it.status == status }
            .sortedByDescending { it.firedAt }
            .take(limit)
        return Result.Ok(result)
    }
    
    override suspend fun findAllActive(): Result<List<Alert>> {
        val active = alerts.values
            .filter { it.isActive() }
            .sortedByDescending { it.firedAt }
        return Result.Ok(active)
    }
    
    override suspend fun findByFilter(filter: AlertFilter): Result<List<Alert>> {
        var result = alerts.values.asSequence()
        
        filter.statuses?.let { statuses ->
            result = result.filter { it.status in statuses }
        }
        filter.severities?.let { severities ->
            result = result.filter { it.severity in severities }
        }
        filter.ruleIds?.let { ruleIds ->
            result = result.filter { it.ruleId in ruleIds }
        }
        filter.fromTime?.let { from ->
            result = result.filter { it.firedAt >= from }
        }
        filter.toTime?.let { to ->
            result = result.filter { it.firedAt <= to }
        }
        
        val finalResult = result
            .sortedByDescending { it.firedAt }
            .drop(filter.offset)
            .take(filter.limit)
            .toList()
        
        return Result.Ok(finalResult)
    }
    
    override suspend fun findRecent(limit: Int): Result<List<Alert>> {
        val recent = alerts.values
            .sortedByDescending { it.firedAt }
            .take(limit)
        return Result.Ok(recent)
    }
    
    override suspend fun findExpiredSilenced(now: Instant): Result<List<Alert>> {
        val expired = alerts.values.filter { it.isSilenceExpired(now) }
        return Result.Ok(expired)
    }
    
    override suspend fun getStats(): Result<AlertStats> {
        val allAlerts = alerts.values
        val active = allAlerts.filter { it.isActive() }
        val yesterday = Instant.now().minus(24, ChronoUnit.HOURS)
        
        val stats = AlertStats(
            totalActive = active.size,
            byStatus = allAlerts.groupBy { it.status }.mapValues { it.value.size },
            bySeverity = active.groupBy { it.severity }.mapValues { it.value.size },
            recentFiringCount24h = allAlerts.count { 
                it.status == AlertStatus.FIRING && it.firedAt.isAfter(yesterday)
            }
        )
        return Result.Ok(stats)
    }
    
    override suspend fun deleteOlderThan(before: Instant): Result<Int> {
        val toDelete = alerts.values.filter { 
            it.status == AlertStatus.RESOLVED && it.resolvedAt != null && it.resolvedAt.isBefore(before)
        }
        toDelete.forEach { alerts.remove(it.id) }
        return Result.Ok(toDelete.size)
    }
    
    // 테스트용
    fun clear() = alerts.clear()
    fun size() = alerts.size
}
