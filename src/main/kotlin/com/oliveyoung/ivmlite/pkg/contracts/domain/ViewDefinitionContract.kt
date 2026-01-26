package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * ViewDefinitionContract: 조회 정책 계약 (RFC-003)
 *
 * Contract is Law: ViewDefinition이 조회 정책의 SSOT.
 * - requiredSlices: 반드시 존재해야 하는 슬라이스
 * - optionalSlices: 없어도 되는 슬라이스
 * - missingPolicy: 필수 슬라이스 누락 시 정책
 * - partialPolicy: 부분 응답 세부 정책
 * - fallbackPolicy: 폴백 정책
 * - ruleSetRef: 참조할 RuleSet 계약
 */
data class ViewDefinitionContract(
    val meta: ContractMeta,

    /** 필수 슬라이스 목록 */
    val requiredSlices: List<SliceType>,

    /** 선택적 슬라이스 목록 */
    val optionalSlices: List<SliceType>,

    /** 필수 슬라이스 누락 시 정책 (기본값: FAIL_CLOSED) */
    val missingPolicy: MissingPolicy,

    /** 부분 응답 세부 정책 */
    val partialPolicy: PartialPolicy,

    /** 폴백 정책 */
    val fallbackPolicy: FallbackPolicy,

    /** 참조할 RuleSet 계약 */
    val ruleSetRef: ContractRef,
)
