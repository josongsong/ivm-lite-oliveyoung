package com.oliveyoung.ivmlite.pkg.webhooks.domain

import java.time.Instant
import java.util.UUID

/**
 * 웹훅 엔티티
 *
 * 파이프라인 이벤트를 외부 시스템에 HTTP로 전송하기 위한 설정.
 *
 * @property id 고유 식별자
 * @property name 웹훅 이름 (관리자 식별용)
 * @property url 대상 URL (HTTPS 권장)
 * @property events 구독할 이벤트 타입 목록
 * @property filters 이벤트 필터 조건 (entityType, tenantId 등)
 * @property headers 요청 시 추가할 HTTP 헤더
 * @property payloadTemplate 커스텀 페이로드 템플릿 (null이면 기본 포맷)
 * @property isActive 활성화 여부
 * @property retryPolicy 재시도 정책
 * @property secretToken HMAC-SHA256 서명용 시크릿
 * @property createdAt 생성 시각
 * @property updatedAt 수정 시각
 */
data class Webhook(
    val id: UUID,
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
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "url must start with http:// or https://"
        }
    }

    /**
     * 주어진 이벤트를 구독하는지 확인
     */
    fun subscribesTo(event: WebhookEvent): Boolean = event in events

    /**
     * 필터 조건과 일치하는지 확인
     *
     * 모든 필터 조건이 일치해야 true.
     * 필터가 비어있으면 항상 true.
     */
    fun matchesFilter(eventContext: Map<String, String>): Boolean {
        if (filters.isEmpty()) return true

        return filters.all { (key, expectedValue) ->
            eventContext[key]?.equals(expectedValue, ignoreCase = true) == true
        }
    }

    /**
     * 이벤트와 필터 조건이 모두 일치하는지 확인
     */
    fun shouldTrigger(event: WebhookEvent, context: Map<String, String>): Boolean =
        isActive && subscribesTo(event) && matchesFilter(context)

    /**
     * 비활성화
     */
    fun deactivate(): Webhook = copy(isActive = false, updatedAt = Instant.now())

    /**
     * 활성화
     */
    fun activate(): Webhook = copy(isActive = true, updatedAt = Instant.now())

    /**
     * URL 업데이트
     */
    fun updateUrl(newUrl: String): Webhook {
        require(newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
            "url must start with http:// or https://"
        }
        return copy(url = newUrl, updatedAt = Instant.now())
    }

    /**
     * 이벤트 구독 목록 업데이트
     */
    fun updateEvents(newEvents: Set<WebhookEvent>): Webhook {
        require(newEvents.isNotEmpty()) { "events must not be empty" }
        return copy(events = newEvents, updatedAt = Instant.now())
    }

    /**
     * 시크릿 토큰 설정
     */
    fun setSecretToken(token: String?): Webhook =
        copy(secretToken = token, updatedAt = Instant.now())

    /**
     * URL 호스트 추출 (마스킹용)
     */
    fun getHost(): String {
        return try {
            java.net.URI(url).host ?: url
        } catch (e: Exception) {
            url.take(50)
        }
    }

    /**
     * 시크릿 토큰 마스킹 (UI 표시용)
     */
    fun getMaskedSecretToken(): String? {
        if (secretToken.isNullOrBlank()) return null
        if (secretToken.length <= 8) return "****"
        return secretToken.take(4) + "*".repeat(secretToken.length - 8) + secretToken.takeLast(4)
    }

    companion object {
        /**
         * 새 웹훅 생성
         */
        fun create(
            name: String,
            url: String,
            events: Set<WebhookEvent>,
            filters: Map<String, String> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
            payloadTemplate: String? = null,
            retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
            secretToken: String? = null
        ): Webhook {
            val now = Instant.now()
            return Webhook(
                id = UUID.randomUUID(),
                name = name,
                url = url,
                events = events,
                filters = filters,
                headers = headers,
                payloadTemplate = payloadTemplate,
                isActive = true,
                retryPolicy = retryPolicy,
                secretToken = secretToken,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
