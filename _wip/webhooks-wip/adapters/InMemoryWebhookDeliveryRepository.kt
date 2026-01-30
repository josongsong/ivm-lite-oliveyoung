package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryFilter
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryStats
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory 웹훅 전송 기록 저장소
 *
 * 개발/테스트용 메모리 기반 구현.
 */
class InMemoryWebhookDeliveryRepository : WebhookDeliveryRepositoryPort {

    private val deliveries = ConcurrentHashMap<UUID, WebhookDelivery>()

    override suspend fun save(delivery: WebhookDelivery): WebhookDeliveryRepositoryPort.Result<WebhookDelivery> {
        deliveries[delivery.id] = delivery
        return WebhookDeliveryRepositoryPort.Result.Ok(delivery)
    }

    override suspend fun findById(id: UUID): WebhookDeliveryRepositoryPort.Result<WebhookDelivery?> {
        return WebhookDeliveryRepositoryPort.Result.Ok(deliveries[id])
    }

    override suspend fun findByWebhookId(webhookId: UUID, limit: Int): WebhookDeliveryRepositoryPort.Result<List<WebhookDelivery>> {
        val result = deliveries.values
            .filter { it.webhookId == webhookId }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return WebhookDeliveryRepositoryPort.Result.Ok(result)
    }

    override suspend fun findByStatus(status: DeliveryStatus, limit: Int): WebhookDeliveryRepositoryPort.Result<List<WebhookDelivery>> {
        val result = deliveries.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return WebhookDeliveryRepositoryPort.Result.Ok(result)
    }

    override suspend fun findPendingRetries(now: Instant, limit: Int): WebhookDeliveryRepositoryPort.Result<List<WebhookDelivery>> {
        val result = deliveries.values
            .filter { it.status == DeliveryStatus.RETRYING && it.nextRetryAt != null && it.nextRetryAt.isBefore(now) }
            .sortedBy { it.nextRetryAt }
            .take(limit)
        return WebhookDeliveryRepositoryPort.Result.Ok(result)
    }

    override suspend fun findRecent(limit: Int): WebhookDeliveryRepositoryPort.Result<List<WebhookDelivery>> {
        val result = deliveries.values
            .sortedByDescending { it.createdAt }
            .take(limit)
        return WebhookDeliveryRepositoryPort.Result.Ok(result)
    }

    override suspend fun findByFilter(filter: DeliveryFilter): WebhookDeliveryRepositoryPort.Result<List<WebhookDelivery>> {
        var result = deliveries.values.asSequence()

        filter.webhookId?.let { id ->
            result = result.filter { it.webhookId == id }
        }
        filter.statuses?.let { statuses ->
            result = result.filter { it.status in statuses }
        }
        filter.eventTypes?.let { types ->
            result = result.filter { it.eventType in types }
        }
        filter.fromTime?.let { from ->
            result = result.filter { it.createdAt >= from }
        }
        filter.toTime?.let { to ->
            result = result.filter { it.createdAt <= to }
        }

        val finalResult = result
            .sortedByDescending { it.createdAt }
            .drop(filter.offset)
            .take(filter.limit)
            .toList()

        return WebhookDeliveryRepositoryPort.Result.Ok(finalResult)
    }

    override suspend fun getStats(webhookId: UUID?): WebhookDeliveryRepositoryPort.Result<DeliveryStats> {
        val all = if (webhookId != null) {
            deliveries.values.filter { it.webhookId == webhookId }
        } else {
            deliveries.values.toList()
        }

        val successCount = all.count { it.status == DeliveryStatus.SUCCESS }.toLong()
        val failedCount = all.count { it.status == DeliveryStatus.FAILED }.toLong()
        val retryingCount = all.count { it.status == DeliveryStatus.RETRYING }.toLong()
        val totalCount = all.size.toLong()

        val today = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val todayCount = all.count { it.createdAt.isAfter(today) }.toLong()

        val avgLatency = all.mapNotNull { it.latencyMs }.average().takeIf { !it.isNaN() }

        val successRate = if (totalCount > 0) {
            successCount.toDouble() / totalCount * 100
        } else 0.0

        val byStatus = all.groupBy { it.status }.mapValues { it.value.size.toLong() }
        val byEvent = all.groupBy { it.eventType }.mapValues { it.value.size.toLong() }

        return WebhookDeliveryRepositoryPort.Result.Ok(
            DeliveryStats(
                totalCount = totalCount,
                successCount = successCount,
                failedCount = failedCount,
                retryingCount = retryingCount,
                successRate = successRate,
                avgLatencyMs = avgLatency,
                todayCount = todayCount,
                byStatus = byStatus,
                byEvent = byEvent
            )
        )
    }

    override suspend fun deleteOlderThan(before: Instant): WebhookDeliveryRepositoryPort.Result<Int> {
        val toDelete = deliveries.values.filter { it.createdAt.isBefore(before) }
        toDelete.forEach { deliveries.remove(it.id) }
        return WebhookDeliveryRepositoryPort.Result.Ok(toDelete.size)
    }

    // 테스트용
    fun clear() = deliveries.clear()
    fun size() = deliveries.size
}
