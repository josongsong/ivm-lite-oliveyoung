package com.oliveyoung.ivmlite.apps.admin.application

import java.time.Instant

/**
 * Admin Dashboard DTOs
 *
 * AdminDashboardService에서 사용하는 도메인 모델들.
 * SRP: 데이터 전송 객체만 담당.
 */

data class DashboardData(
    val sinkEvent: SinkEventStats,
    val worker: WorkerStatus,
    val database: DatabaseStats,
    val timestamp: Instant
)

/** SinkEvent 통계 (DynamoDB Streams 기반, Outbox 대체) */
data class SinkEventStats(
    val total: SinkEventTotalStats,
    val byStatus: Map<String, Long>,
    val details: List<SinkEventStatDetail>
)

data class SinkEventTotalStats(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val completed: Long
)

data class SinkEventStatDetail(
    val status: String,
    val viewType: String,
    val count: Long,
    val oldest: Instant?,
    val newest: Instant?
)

data class WorkerStatus(
    val running: Boolean,
    val processed: Long,
    val failed: Long,
    val polls: Long,
    val lastPollTime: Long?
)

data class DatabaseStats(
    val rawDataCount: Long,
    val sinkEventCount: Long,
    val contractsCount: Long = 0L,
    val note: String
)

/** SinkEvent 기반 (Outbox 대체) */
data class RecentSinkEventItem(
    val id: String,
    val entityKey: String,
    val viewType: String,
    val status: String,
    val createdAt: Instant?,
    val processedAt: Instant?
)

data class FailedSinkEventItem(
    val id: String,
    val entityKey: String,
    val viewType: String,
    val createdAt: Instant?
)

data class HourlyStatsData(
    val items: List<HourlyStatItem>,
    val hours: Int
)

data class HourlyStatItem(
    val hour: Instant,
    val pending: Long,
    val processing: Long,
    val processed: Long,
    val failed: Long,
    val total: Long
)

data class StaleOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val claimedAt: Instant?,
    val claimedBy: String?,
    val ageSeconds: Long
)

/** SinkEvent 상세 (Outbox 대체) */
data class SinkEventEntryDetail(
    val id: String,
    val idempotencyKey: String,
    val entityKey: String,
    val viewType: String,
    val status: String,
    val createdAt: Instant,
    val processedAt: Instant?,
    val sinkTargets: List<String>
)

