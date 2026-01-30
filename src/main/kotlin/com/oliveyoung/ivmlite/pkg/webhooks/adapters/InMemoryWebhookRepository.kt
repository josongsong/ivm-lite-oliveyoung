package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory Webhook Repository
 *
 * 개발/테스트용 인메모리 웹훅 저장소.
 */
class InMemoryWebhookRepository : WebhookRepositoryPort {
    private val store = ConcurrentHashMap<UUID, Webhook>()

    override suspend fun save(webhook: Webhook): WebhookRepositoryPort.Result {
        store[webhook.id] = webhook
        return WebhookRepositoryPort.Result.Ok(webhook)
    }

    override suspend fun findById(id: UUID): Webhook? = store[id]

    override suspend fun findAll(): List<Webhook> =
        store.values.toList().sortedByDescending { it.createdAt }

    override suspend fun findActive(): List<Webhook> =
        store.values.filter { it.isActive }.sortedByDescending { it.createdAt }

    override suspend fun findByEvent(event: WebhookEvent): List<Webhook> =
        store.values.filter { event in it.events && it.isActive }.toList()

    override suspend fun delete(id: UUID): Boolean =
        store.remove(id) != null

    override suspend fun exists(id: UUID): Boolean =
        store.containsKey(id)

    override suspend fun count(): Long = store.size.toLong()

    // 테스트용: 저장소 초기화
    fun clear() = store.clear()
}
