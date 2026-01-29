package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * RFC-006: Slice의 용도/특성을 정의
 *
 * SliceType이 "무엇을 저장하는가"라면, SliceKind는 "어떻게 사용되는가"를 정의.
 *
 * - STANDARD: 일반 슬라이스 (기본값)
 * - REF_INDEX: 경량 참조용 슬라이스 (다른 엔티티의 ENRICHED에서 LOOKUP)
 *              예: Brand SUMMARY → Product ENRICHED에서 참조
 * - ENRICHMENT: 외부 엔티티 JOIN 결과를 담는 슬라이스
 *               예: Product ENRICHED (Brand 정보 포함)
 *
 * 설계 원칙:
 * - REF_INDEX: 자주 참조되는 필드만 포함 (name, logoUrl 등)
 * - ENRICHMENT: 원본 엔티티 필드 없이 JOIN 결과만 포함
 * - View에서 CORE + ENRICHED 조합으로 최종 데이터 구성
 */
@Serializable
enum class SliceKind {
    /**
     * 일반 슬라이스 (기본값)
     * 특별한 용도 없이 데이터 저장용
     */
    STANDARD,

    /**
     * 경량 참조용 슬라이스 (RFC-006 RefIndexSlice)
     *
     * 특성:
     * - 다른 엔티티의 ENRICHED 슬라이스에서 LOOKUP 대상
     * - 최소 필드만 포함 (id, name 등)
     * - 변경 시 참조하는 ENRICHED만 재빌드 (효율성)
     */
    REF_INDEX,

    /**
     * 외부 엔티티 JOIN 결과 슬라이스 (RFC-006 RefIndexSlice)
     *
     * 특성:
     * - REF_INDEX 슬라이스를 LOOKUP하여 JOIN
     * - 원본 엔티티의 필드는 미포함 (CORE와 분리)
     * - Brand 변경 → ENRICHED만 재빌드 (CORE 유지)
     */
    ENRICHMENT;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): SliceKind =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw DomainError.ValidationError("sliceKind", "Unknown SliceKind: $value")

        fun fromDbValueOrNull(value: String): SliceKind? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
