package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * Outbox에 저장되는 집계 타입
 * - RAW_DATA: 원시 데이터 Ingest 이벤트
 * - SLICE: Slice 계산 완료 이벤트
 * - CHANGESET: ChangeSet 생성 이벤트
 */
@Serializable
enum class AggregateType {
    RAW_DATA,
    SLICE,
    CHANGESET;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): AggregateType =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw DomainError.ValidationError("aggregateType", "Unknown AggregateType: $value")

        fun fromDbValueOrNull(value: String): AggregateType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
