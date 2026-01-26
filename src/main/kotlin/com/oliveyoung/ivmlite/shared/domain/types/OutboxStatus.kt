package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * Outbox 엔트리 처리 상태
 * - PENDING: 처리 대기 중
 * - PROCESSED: 처리 완료
 * - FAILED: 처리 실패 (재시도 대상)
 */
@Serializable
enum class OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): OutboxStatus =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw DomainError.ValidationError("outboxStatus", "Unknown OutboxStatus: $value")

        fun fromDbValueOrNull(value: String): OutboxStatus? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
