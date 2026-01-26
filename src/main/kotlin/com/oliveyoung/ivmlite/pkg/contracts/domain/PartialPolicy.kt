package com.oliveyoung.ivmlite.pkg.contracts.domain

/**
 * PartialPolicy: 부분 응답 허용 시 세부 정책 (RFC-003)
 * MissingPolicy.PARTIAL_ALLOWED일 때만 유효.
 */
data class PartialPolicy(
    /** 부분 응답 허용 여부 */
    val allowed: Boolean,

    /** true면 optionalSlices만 누락 허용, false면 required도 허용 */
    val optionalOnly: Boolean,

    /** 응답 메타데이터 포함 설정 */
    val responseMeta: ResponseMeta,
)

/**
 * ResponseMeta: 응답에 포함할 메타데이터 설정
 */
data class ResponseMeta(
    /** 누락된 슬라이스 목록 포함 여부 */
    val includeMissingSlices: Boolean,

    /** 사용된 계약 정보 포함 여부 */
    val includeUsedContracts: Boolean,
)
