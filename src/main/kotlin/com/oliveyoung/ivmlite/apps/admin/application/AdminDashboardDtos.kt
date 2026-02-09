package com.oliveyoung.ivmlite.apps.admin.application

import java.time.Instant

/**
 * Admin Dashboard DTOs
 *
 * AdminDashboardService에서 사용하는 도메인 모델들.
 * SRP: 데이터 전송 객체만 담당.
 */

data class DashboardData(
    val outbox: OutboxStats,
    val worker: WorkerStatus,
    val database: DatabaseStats,
    val timestamp: Instant
)

data class OutboxStats(
    val total: OutboxTotalStats,
    val byStatus: Map<String, Long>,
    val byType: Map<String, Long>,
    val details: List<OutboxStatDetail>
)

data class OutboxTotalStats(
    val pending: Long,
    val processing: Long,
    val failed: Long,
    val processed: Long
)

data class OutboxStatDetail(
    val status: String,
    val aggregateType: String,
    val count: Long,
    val oldest: Instant?,
    val newest: Instant?,
    val avgLatencySeconds: Double?
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
    val outboxCount: Long,
    val note: String
)

data class RecentOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val status: String,
    val createdAt: Instant?,
    val processedAt: Instant?,
    val retryCount: Int
)

data class FailedOutboxItem(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val createdAt: Instant?,
    val retryCount: Int,
    val failureReason: String?
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

data class OutboxEntryDetail(
    val id: String,
    val idempotencyKey: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: String,
    val createdAt: Instant,
    val processedAt: Instant?,
    val claimedAt: Instant?,
    val claimedBy: String?,
    val retryCount: Int,
    val failureReason: String?,
    val priority: Int?,
    val entityVersion: Long?
)
