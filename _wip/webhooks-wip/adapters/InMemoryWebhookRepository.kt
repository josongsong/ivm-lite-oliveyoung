package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookStats
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory 웹훅 저장소
 *
 * 개발/테스트용 메모리 기반 구현.
 * 프로덕션에서는 JooqWebhookRepository 사용 권장.
 */
class InMemoryWebhookRepository : WebhookRepositoryPort {

    private val webhooks = ConcurrentHashMap<UUID, Webhook>()

    override suspend fun save(webhook: Webhook): WebhookRepositoryPort.Result<Webhook> {
        webhooks[webhook.id] = webhook
        return WebhookRepositoryPort.Result.Ok(webhook)
    }

    override suspend fun findById(id: UUID): WebhookRepositoryPort.Result<Webhook?> {
        return WebhookRepositoryPort.Result.Ok(webhooks[id])
    }

    override suspend fun findAll(): WebhookRepositoryPort.Result<List<Webhook>> {
        val result = webhooks.values
            .sortedByDescending { it.createdAt }
        return WebhookRepositoryPort.Result.Ok(result)
    }

    override suspend fun findAllActive(): WebhookRepositoryPort.Result<List<Webhook>> {
        val result = webhooks.values
            .filter { it.isActive }
            .sortedByDescending { it.createdAt }
        return WebhookRepositoryPort.Result.Ok(result)
    }

    override suspend fun findByEvent(event: WebhookEvent): WebhookRepositoryPort.Result<List<Webhook>> {
        val result = webhooks.values
            .filter { it.isActive && it.subscribesTo(event) }
            .sortedByDescending { it.createdAt }
        return WebhookRepositoryPort.Result.Ok(result)
    }

    override suspend fun findByName(name: String): WebhookRepositoryPort.Result<Webhook?> {
        val result = webhooks.values.find { it.name.equals(name, ignoreCase = true) }
        return WebhookRepositoryPort.Result.Ok(result)
    }

    override suspend fun delete(id: UUID): WebhookRepositoryPort.Result<Boolean> {
        val removed = webhooks.remove(id)
        return WebhookRepositoryPort.Result.Ok(removed != null)
    }

    override suspend fun getStats(): WebhookRepositoryPort.Result<WebhookStats> {
        val all = webhooks.values
        val active = all.filter { it.isActive }
        val inactive = all.filter { !it.isActive }

        val byEvent = mutableMapOf<WebhookEvent, Int>()
        active.forEach { webhook ->
            webhook.events.forEach { event ->
                byEvent[event] = (byEvent[event] ?: 0) + 1
            }
        }

        return WebhookRepositoryPort.Result.Ok(
            WebhookStats(
                totalCount = all.size,
                activeCount = active.size,
                inactiveCount = inactive.size,
                byEvent = byEvent
            )
        )
    }

    // 테스트용
    fun clear() = webhooks.clear()
    fun size() = webhooks.size
}
