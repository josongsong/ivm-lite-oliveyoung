package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.time.Instant
import java.util.UUID

/**
 * 웹훅 전송 기록 저장소 포트
 *
 * 웹훅 전송 기록의 영속성을 담당한다.
 */
interface WebhookDeliveryRepositoryPort {

    /**
     * 전송 기록 저장 (insert or update)
     */
    suspend fun save(delivery: WebhookDelivery): Result<WebhookDelivery>

    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<WebhookDelivery?>

    /**
     * 웹훅 ID로 조회 (최신순)
     */
    suspend fun findByWebhookId(webhookId: UUID, limit: Int = 100): Result<List<WebhookDelivery>>

    /**
     * 상태별 조회
     */
    suspend fun findByStatus(status: DeliveryStatus, limit: Int = 100): Result<List<WebhookDelivery>>

    /**
     * 재시도 대상 조회 (nextRetryAt이 지난 RETRYING 상태)
     */
    suspend fun findPendingRetries(now: Instant = Instant.now(), limit: Int = 100): Result<List<WebhookDelivery>>

    /**
     * 최근 전송 기록 조회
     */
    suspend fun findRecent(limit: Int = 50): Result<List<WebhookDelivery>>

    /**
     * 필터로 조회
     */
    suspend fun findByFilter(filter: DeliveryFilter): Result<List<WebhookDelivery>>

    /**
     * 통계 조회
     */
    suspend fun getStats(webhookId: UUID? = null): Result<DeliveryStats>

    /**
     * 오래된 기록 삭제
     */
    suspend fun deleteOlderThan(before: Instant): Result<Int>

    // ==================== Result Type ====================

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()

        fun <R> map(transform: (T) -> R): Result<R> = when (this) {
            is Ok -> Ok(transform(value))
            is Err -> this
        }

        fun getOrNull(): T? = when (this) {
            is Ok -> value
            is Err -> null
        }
    }
}

/**
 * 전송 기록 필터
 */
data class DeliveryFilter(
    val webhookId: UUID? = null,
    val statuses: Set<DeliveryStatus>? = null,
    val eventTypes: Set<WebhookEvent>? = null,
    val fromTime: Instant? = null,
    val toTime: Instant? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

/**
 * 전송 통계
 */
data class DeliveryStats(
    val totalCount: Long,
    val successCount: Long,
    val failedCount: Long,
    val retryingCount: Long,
    val successRate: Double,
    val avgLatencyMs: Double?,
    val todayCount: Long,
    val byStatus: Map<DeliveryStatus, Long>,
    val byEvent: Map<WebhookEvent, Long>
)
