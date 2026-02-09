package com.oliveyoung.ivmlite.pkg.alerts.ports

import com.oliveyoung.ivmlite.pkg.alerts.domain.Alert
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertSeverity
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertStatus
import com.oliveyoung.ivmlite.shared.domain.types.Result
import java.time.Instant
import java.util.UUID

/**
 * Alert Repository Port
 *
 * Alert 엔티티의 영속성을 담당한다.
 */
interface AlertRepositoryPort {

    /**
     * Alert 저장 (insert or update)
     */
    suspend fun save(alert: Alert): Result<Alert>

    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<Alert?>

    /**
     * Rule ID로 활성 Alert 조회 (FIRING 또는 ACKNOWLEDGED)
     */
    suspend fun findActiveByRuleId(ruleId: String): Result<Alert?>

    /**
     * 상태별 조회
     */
    suspend fun findByStatus(status: AlertStatus, limit: Int = 100): Result<List<Alert>>

    /**
     * 활성 Alert 전체 조회 (FIRING, ACKNOWLEDGED)
     */
    suspend fun findAllActive(): Result<List<Alert>>

    /**
     * 조건부 조회 (필터링)
     */
    suspend fun findByFilter(filter: AlertFilter): Result<List<Alert>>

    /**
     * 최근 Alert 조회
     */
    suspend fun findRecent(limit: Int = 50): Result<List<Alert>>

    /**
     * 만료된 SILENCED Alert 조회
     */
    suspend fun findExpiredSilenced(now: Instant = Instant.now()): Result<List<Alert>>

    /**
     * 통계 조회
     */
    suspend fun getStats(): Result<AlertStats>

    /**
     * 삭제 (해결된 오래된 Alert 정리용)
     */
    suspend fun deleteOlderThan(before: Instant): Result<Int>
}

/**
 * Alert 필터 조건
 */
data class AlertFilter(
    val statuses: Set<AlertStatus>? = null,
    val severities: Set<AlertSeverity>? = null,
    val ruleIds: Set<String>? = null,
    val fromTime: Instant? = null,
    val toTime: Instant? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

/**
 * Alert 통계
 */
data class AlertStats(
    val totalActive: Int,
    val byStatus: Map<AlertStatus, Int>,
    val bySeverity: Map<AlertSeverity, Int>,
    val recentFiringCount24h: Int
)
