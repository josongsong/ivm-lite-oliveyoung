package com.oliveyoung.ivmlite.sinks.contract

import kotlinx.serialization.Serializable

/**
 * Sink Error (SOTA-grade)
 *
 * 핵심: Retryable/NonRetryable/PoisonPill 분류
 */
@Serializable
sealed class SinkError {
    abstract val category: ErrorCategory
    abstract val reasonCode: ErrorReasonCode
    abstract val message: String
    abstract val context: Map<String, String>

    /**
     * Retryable Error (재시도 가능)
     */
    @Serializable
    data class RetryableError(
        override val reasonCode: ErrorReasonCode,
        override val message: String,
        override val context: Map<String, String> = emptyMap()
    ) : SinkError() {
        override val category = ErrorCategory.RETRYABLE
    }

    /**
     * NonRetryable Error (재시도 불가)
     */
    @Serializable
    data class NonRetryableError(
        override val reasonCode: ErrorReasonCode,
        override val message: String,
        override val context: Map<String, String> = emptyMap()
    ) : SinkError() {
        override val category = ErrorCategory.NON_RETRYABLE
    }

    /**
     * Poison Pill (스키마/계약 파손)
     */
    @Serializable
    data class PoisonPillError(
        override val reasonCode: ErrorReasonCode,
        override val message: String,
        override val context: Map<String, String> = emptyMap()
    ) : SinkError() {
        override val category = ErrorCategory.POISON_PILL
    }
}

@Serializable
enum class ErrorCategory {
    RETRYABLE,
    NON_RETRYABLE,
    POISON_PILL
}

@Serializable
enum class ErrorReasonCode {
    // Retryable
    NETWORK_TIMEOUT,
    RATE_LIMIT_EXCEEDED,
    CIRCUIT_BREAKER_OPEN,
    TEMPORARY_UNAVAILABLE,

    // NonRetryable
    PERMISSION_DENIED,
    RESOURCE_NOT_FOUND,
    INVALID_CONFIGURATION,
    BUSINESS_RULE_VIOLATION,

    // Poison Pill
    DESERIALIZATION_FAILED,
    CONTRACT_VERSION_UNSUPPORTED,
    SCHEMA_VALIDATION_FAILED,
    REQUIRED_FIELD_MISSING,

    // Plugin Specific
    PLUGIN_EXECUTION_FAILED,
    UNKNOWN
}
