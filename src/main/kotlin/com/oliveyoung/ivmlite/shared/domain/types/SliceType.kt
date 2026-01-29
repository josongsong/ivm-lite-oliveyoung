package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * Slice 계산 방식을 정의
 * - CORE: 단일 엔티티 기반 (RawData -> Slice)
 * - JOINED: 여러 엔티티 조인 (N:M)
 * - DERIVED: 다른 Slice에서 파생 계산
 * - INDEX: 검색/필터용 파생 필드 (검색 인덱싱, 필터 facet, 정렬 기준)
 * - SUMMARY: 경량 참조용 슬라이스 (RFC-006 RefIndexSlice)
 * - ENRICHED: 외부 엔티티 JOIN 결과 포함 (RFC-006 RefIndexSlice)
 * - PRICE, INVENTORY, MEDIA, CATEGORY, PROMOTION, REVIEW, CUSTOM: 비즈니스 슬라이스
 */
@Serializable
enum class SliceType {
    CORE,
    JOINED,
    DERIVED,
    INDEX,
    SUMMARY,
    ENRICHED,
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
