package com.oliveyoung.ivmlite.pkg.webhooks.domain

import java.time.Instant
import java.util.UUID

/**
 * Webhook Entity
 *
 * 웹훅 정의를 나타내는 도메인 엔티티.
 */
data class Webhook(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val url: String,
    val events: Set<WebhookEvent>,
    val filters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val payloadTemplate: String? = null,
    val isActive: Boolean = true,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val secretToken: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
        require(events.isNotEmpty()) { "events must not be empty" }
    }

    /**
     * 이벤트가 이 웹훅을 트리거해야 하는지 확인
     */
    fun shouldTrigger(event: WebhookEvent, eventFilters: Map<String, String>): Boolean {
        if (!isActive) return false
        if (event !in events) return false
        return matchesFilter(eventFilters)
    }

    /**
     * 필터 조건 매칭
     */
    private fun matchesFilter(eventFilters: Map<String, String>): Boolean {
        if (filters.isEmpty()) return true

        return filters.all { (key, expectedValue) ->
            val actualValue = eventFilters[key]
            actualValue != null && (expectedValue == "*" || actualValue == expectedValue)
        }
    }

    /**
     * 웹훅 업데이트
     */
    fun update(
        name: String? = null,
        url: String? = null,
        events: Set<WebhookEvent>? = null,
        filters: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        payloadTemplate: String? = null,
        isActive: Boolean? = null,
        retryPolicy: RetryPolicy? = null,
        secretToken: String? = null
    ): Webhook = copy(
        name = name ?: this.name,
        url = url ?: this.url,
        events = events ?: this.events,
        filters = filters ?: this.filters,
        headers = headers ?: this.headers,
        payloadTemplate = payloadTemplate ?: this.payloadTemplate,
        isActive = isActive ?: this.isActive,
        retryPolicy = retryPolicy ?: this.retryPolicy,
        secretToken = secretToken ?: this.secretToken,
        updatedAt = Instant.now()
    )

    /**
     * 시크릿 토큰 마스킹 (보안)
     */
    fun getMaskedSecretToken(): String? {
        if (secretToken == null) return null
        if (secretToken.length <= 8) return "****"
        return secretToken.take(4) + "****" + secretToken.takeLast(4)
    }

    /**
     * 활성화/비활성화
     */
    fun activate(): Webhook = copy(isActive = true, updatedAt = Instant.now())
    fun deactivate(): Webhook = copy(isActive = false, updatedAt = Instant.now())
}
