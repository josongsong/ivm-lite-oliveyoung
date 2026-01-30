package com.oliveyoung.ivmlite.pkg.webhooks.domain

/**
 * 웹훅 전송 상태
 */
enum class DeliveryStatus {
    /** 전송 대기 중 */
    PENDING,

    /** 전송 성공 (2xx 응답) */
    SUCCESS,

    /** 전송 실패 (최대 재시도 초과) */
    FAILED,

    /** 재시도 대기 중 */
    RETRYING,

    /** Circuit Breaker로 인해 스킵됨 */
    CIRCUIT_OPEN,

    /** Rate Limit으로 인해 스킵됨 */
    RATE_LIMITED;

    fun isTerminal(): Boolean = this == SUCCESS || this == FAILED

    fun isRetryable(): Boolean = this == RETRYING || this == PENDING
}
