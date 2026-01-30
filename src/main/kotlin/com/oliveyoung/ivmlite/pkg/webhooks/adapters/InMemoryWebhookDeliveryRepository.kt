package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryFilter
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryStats
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory Webhook Delivery Repository
 *
 * 개발/테스트용 인메모리 전송 기록 저장소.
 */
class InMemoryWebhookDeliveryRepository : WebhookDeliveryRepositoryPort {
    private val store = ConcurrentHashMap<UUID, WebhookDelivery>()

    override suspend fun save(delivery: WebhookDelivery): WebhookDelivery {
        store[delivery.id] = delivery
        return delivery
    }

    override suspend fun findById(id: UUID): WebhookDelivery? = store[id]

    override suspend fun findByWebhookId(webhookId: UUID, limit: Int, offset: Int): List<WebhookDelivery> =
        store.values
            .filter { it.webhookId == webhookId }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)

    override suspend fun findByFilter(filter: DeliveryFilter): List<WebhookDelivery> =
        store.values
            .filter { delivery ->
                (filter.webhookId == null || delivery.webhookId == filter.webhookId) &&
                    (filter.status == null || delivery.status == filter.status) &&
                    (filter.eventType == null || delivery.eventType == filter.eventType) &&
                    (filter.fromDate == null || delivery.createdAt >= filter.fromDate) &&
                    (filter.toDate == null || delivery.createdAt <= filter.toDate)
            }
            .sortedByDescending { it.createdAt }
            .drop(filter.offset)
            .take(filter.limit)

    override suspend fun findPendingRetries(before: Instant): List<WebhookDelivery> =
        store.values
            .filter {
                it.status == DeliveryStatus.RETRYING &&
                    it.nextRetryAt != null &&
                    it.nextRetryAt <= before
            }
            .sortedBy { it.nextRetryAt }

    override suspend fun getStats(webhookId: UUID): DeliveryStats {
        val deliveries = store.values.filter { it.webhookId == webhookId }
        return calculateStats(deliveries)
    }

    override suspend fun getOverallStats(): DeliveryStats =
        calculateStats(store.values.toList())

    override suspend fun deleteOlderThan(before: Instant): Int {
        val toDelete = store.values.filter { it.createdAt < before }.map { it.id }
        toDelete.forEach { store.remove(it) }
        return toDelete.size
    }

    private fun calculateStats(deliveries: List<WebhookDelivery>): DeliveryStats {
        if (deliveries.isEmpty()) return DeliveryStats.EMPTY

        val total = deliveries.size.toLong()
        val success = deliveries.count { it.status == DeliveryStatus.SUCCESS }.toLong()
        val failed = deliveries.count { it.status == DeliveryStatus.FAILED }.toLong()
        val pending = deliveries.count { it.status == DeliveryStatus.PENDING }.toLong()
        val retrying = deliveries.count { it.status == DeliveryStatus.RETRYING }.toLong()

        val latencies = deliveries.mapNotNull { it.latencyMs }
        val avgLatency = if (latencies.isNotEmpty()) latencies.average() else 0.0

        val successRate = if (total > 0) (success.toDouble() / total) * 100 else 0.0

        return DeliveryStats(
            total = total,
            success = success,
            failed = failed,
            pending = pending,
            retrying = retrying,
            averageLatencyMs = avgLatency,
            successRate = successRate
        )
    }

    // 테스트용: 저장소 초기화
    fun clear() = store.clear()
}
