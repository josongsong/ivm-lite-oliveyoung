package com.oliveyoung.ivmlite.pkg.contracts.domain

/**
 * FallbackPolicy: 슬라이스 누락 시 폴백 정책 (RFC-003)
 */
enum class FallbackPolicy {
    /** 폴백 없음 - 누락 시 그대로 반영 */
    NONE,

    /** 기본값 사용 - 슬라이스별 기본값으로 대체 */
    DEFAULT_VALUE,
}
