package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import java.util.UUID

/**
 * Webhook Repository Port
 *
 * 웹훅 저장소 인터페이스.
 */
interface WebhookRepositoryPort {
    /**
     * 웹훅 저장
     */
    suspend fun save(webhook: Webhook): Result

    /**
     * ID로 웹훅 조회
     */
    suspend fun findById(id: UUID): Webhook?

    /**
     * 모든 웹훅 조회
     */
    suspend fun findAll(): List<Webhook>

    /**
     * 활성화된 웹훅만 조회
     */
    suspend fun findActive(): List<Webhook>

    /**
     * 특정 이벤트를 구독하는 웹훅 조회
     */
    suspend fun findByEvent(event: WebhookEvent): List<Webhook>

    /**
     * 웹훅 삭제
     */
    suspend fun delete(id: UUID): Boolean

    /**
     * 웹훅 존재 여부 확인
     */
    suspend fun exists(id: UUID): Boolean

    /**
     * 웹훅 개수
     */
    suspend fun count(): Long

    sealed class Result {
        data class Ok(val webhook: Webhook) : Result()
        data class Error(val message: String) : Result()
    }
}
