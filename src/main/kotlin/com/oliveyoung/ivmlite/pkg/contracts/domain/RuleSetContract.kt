package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * RuleSet 계약 도메인 모델 (RFC-IMPL Phase D-2)
 *
 * 슬라이싱 규칙의 SSOT(Single Source of Truth).
 * Contract is Law: RuleSet이 슬라이싱 규칙의 유일한 정의 소스.
 */
data class RuleSetContract(
    val meta: ContractMeta,
    val entityType: String,
    val impactMap: Map<SliceType, List<String>>,
    val joins: List<JoinSpec>,
    val slices: List<SliceDefinition>,
    val indexes: List<IndexSpec> = emptyList(),
)

/**
 * 조인 사양
 */
data class JoinSpec(
    val sourceSlice: SliceType,
    val targetEntity: String,
    val joinPath: String,
    val cardinality: JoinCardinality,
)

/**
 * 조인 카디널리티
 */
enum class JoinCardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY,
}

/**
 * 슬라이스 정의 (RFC-IMPL-010 Phase D-4: joins 추가)
 */
data class SliceDefinition(
    val type: SliceType,
    val buildRules: SliceBuildRules,
    val joins: List<com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec> = emptyList(),
)

/**
 * 슬라이스 빌드 규칙 (sealed class)
 *
 * - PassThrough: 필드 그대로 통과
 * - MapFields: 필드 매핑 변환
 */
sealed class SliceBuildRules {
    /**
     * PassThrough: 지정된 필드들을 그대로 통과
     * fields: ["*"] 는 모든 필드 통과
     */
    data class PassThrough(val fields: List<String>) : SliceBuildRules()

    /**
     * MapFields: 소스 필드를 타겟 필드로 매핑
     * mappings: { "sourceField" -> "targetField" }
     */
    data class MapFields(val mappings: Map<String, String>) : SliceBuildRules()
}

/**
 * RFC-IMPL-010 Phase D-9: 인덱스 사양
 *
 * Inverted Index 생성을 위한 정의.
 * Contract is Law: RuleSet.indexes가 인덱스 정의의 SSOT.
 */
data class IndexSpec(
    val type: String,
    val selector: String,
)
