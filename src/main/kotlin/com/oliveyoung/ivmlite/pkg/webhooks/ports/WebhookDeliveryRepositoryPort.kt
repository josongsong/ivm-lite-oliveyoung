package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import java.time.Instant
import java.util.UUID

/**
 * Webhook Delivery Repository Port
 *
 * 웹훅 전송 기록 저장소 인터페이스.
 */
interface WebhookDeliveryRepositoryPort {
    /**
     * 전송 기록 저장
     */
    suspend fun save(delivery: WebhookDelivery): WebhookDelivery

    /**
     * ID로 전송 기록 조회
     */
    suspend fun findById(id: UUID): WebhookDelivery?

    /**
     * 웹훅별 전송 기록 조회
     */
    suspend fun findByWebhookId(webhookId: UUID, limit: Int = 100, offset: Int = 0): List<WebhookDelivery>

    /**
     * 필터를 적용한 전송 기록 조회
     */
    suspend fun findByFilter(filter: DeliveryFilter): List<WebhookDelivery>

    /**
     * 재시도 대기 중인 전송 기록 조회
     */
    suspend fun findPendingRetries(before: Instant): List<WebhookDelivery>

    /**
     * 웹훅별 통계 조회
     */
    suspend fun getStats(webhookId: UUID): DeliveryStats

    /**
     * 전체 통계 조회
     */
    suspend fun getOverallStats(): DeliveryStats

    /**
     * 오래된 전송 기록 삭제 (정리)
     */
    suspend fun deleteOlderThan(before: Instant): Int
}

/**
 * 전송 기록 필터
 */
data class DeliveryFilter(
    val webhookId: UUID? = null,
    val status: DeliveryStatus? = null,
    val eventType: WebhookEvent? = null,
    val fromDate: Instant? = null,
    val toDate: Instant? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

/**
 * 전송 통계
 */
data class DeliveryStats(
    val total: Long,
    val success: Long,
    val failed: Long,
    val pending: Long,
    val retrying: Long,
    val averageLatencyMs: Double,
    val successRate: Double
) {
    companion object {
        val EMPTY = DeliveryStats(
            total = 0,
            success = 0,
            failed = 0,
            pending = 0,
            retrying = 0,
            averageLatencyMs = 0.0,
            successRate = 0.0
        )
    }
}
