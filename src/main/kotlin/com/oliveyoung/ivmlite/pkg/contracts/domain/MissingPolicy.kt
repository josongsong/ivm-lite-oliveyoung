package com.oliveyoung.ivmlite.pkg.contracts.domain

/**
 * MissingPolicy: 필수 슬라이스 누락 시 정책 (RFC-003)
 * Contract is Law: fail-closed가 기본값.
 */
enum class MissingPolicy {
    /** 필수 슬라이스 하나라도 누락 시 즉시 실패 (기본값) */
    FAIL_CLOSED,

    /** 부분 응답 허용 - PartialPolicy와 함께 사용 */
    PARTIAL_ALLOWED,
}
