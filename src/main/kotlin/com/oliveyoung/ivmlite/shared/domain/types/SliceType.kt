package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * Slice 계산 방식을 정의
 * - CORE: 단일 엔티티 기반 (RawData -> Slice)
 * - JOINED: 여러 엔티티 조인 (N:M)
 * - DERIVED: 다른 Slice에서 파생 계산
 * - PRICE, INVENTORY, MEDIA, CATEGORY, PROMOTION, REVIEW, CUSTOM: 비즈니스 슬라이스
 */
@Serializable
enum class SliceType {
    CORE,
    JOINED,
    DERIVED,
    PRICE,
    INVENTORY,
    MEDIA,
    CATEGORY,
    PROMOTION,
    REVIEW,
    CUSTOM;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): SliceType =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw DomainError.ValidationError("sliceType", "Unknown SliceType: $value")

        fun fromDbValueOrNull(value: String): SliceType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
