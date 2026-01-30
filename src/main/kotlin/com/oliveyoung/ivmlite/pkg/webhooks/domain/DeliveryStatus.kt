package com.oliveyoung.ivmlite.pkg.webhooks.domain

/**
 * Webhook Delivery Status
 *
 * 웹훅 전송 상태를 나타내는 enum.
 */
enum class DeliveryStatus {
    /** 전송 대기 중 */
    PENDING,

    /** 전송 성공 */
    SUCCESS,

    /** 전송 실패 (재시도 없음) */
    FAILED,

    /** 재시도 중 */
    RETRYING,

    /** Circuit Breaker Open 상태로 전송 차단 */
    CIRCUIT_OPEN,

    /** Rate Limit 초과로 전송 차단 */
    RATE_LIMITED;

    fun isTerminal(): Boolean = this == SUCCESS || this == FAILED
    fun isRetryable(): Boolean = this == RETRYING || this == PENDING

    companion object {
        fun fromString(value: String): DeliveryStatus =
            entries.find { it.name == value } ?: PENDING
    }
}
