package com.oliveyoung.ivmlite.pkg.webhooks.ports

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.util.UUID

/**
 * 웹훅 저장소 포트
 *
 * 웹훅 엔티티의 영속성을 담당한다.
 */
interface WebhookRepositoryPort {

    /**
     * 웹훅 저장 (insert or update)
     */
    suspend fun save(webhook: Webhook): Result<Webhook>

    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<Webhook?>

    /**
     * 전체 조회
     */
    suspend fun findAll(): Result<List<Webhook>>

    /**
     * 활성 웹훅만 조회
     */
    suspend fun findAllActive(): Result<List<Webhook>>

    /**
     * 특정 이벤트를 구독하는 활성 웹훅 조회
     */
    suspend fun findByEvent(event: WebhookEvent): Result<List<Webhook>>

    /**
     * 이름으로 검색
     */
    suspend fun findByName(name: String): Result<Webhook?>

    /**
     * 삭제
     */
    suspend fun delete(id: UUID): Result<Boolean>

    /**
     * 통계
     */
    suspend fun getStats(): Result<WebhookStats>

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

        fun getOrThrow(): T = when (this) {
            is Ok -> value
            is Err -> throw RuntimeException(error.toString())
        }
    }
}

/**
 * 웹훅 통계
 */
data class WebhookStats(
    val totalCount: Int,
    val activeCount: Int,
    val inactiveCount: Int,
    val byEvent: Map<WebhookEvent, Int>
)
