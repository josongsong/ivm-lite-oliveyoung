package com.oliveyoung.ivmlite.shared.domain.errors

/**
 * 도메인 에러 계층 구조.
 * sealed class로 정의하여 when 분기에서 exhaustive 검사 가능.
 */
sealed class DomainError(message: String) : RuntimeException(message) {

    /** 에러 코드 (API 응답용) */
    abstract val errorCode: String

    /** HTTP 상태 코드 매핑 */
    abstract fun toHttpStatus(): Int

    // ==================== 계약/검증 ====================

    /** 스키마/계약 검증 실패 */
    data class ContractError(val msg: String) : DomainError(msg) {
        override val errorCode: String = "ERR_CONTRACT"
        override fun toHttpStatus(): Int = 400
    }

    /** 계약 무결성 검증 실패 (checksum 불일치) */
    data class ContractIntegrityError(
        val contractId: String,
        val expected: String,
        val actual: String,
    ) : DomainError("Contract integrity violation for '$contractId': expected=$expected, actual=$actual") {
        override val errorCode: String = "ERR_CONTRACT_INTEGRITY"
        override fun toHttpStatus(): Int = 500
    }

    /** 필드 유효성 검증 실패 */
    data class ValidationError(
        val field: String,
        val msg: String,
    ) : DomainError("Validation failed for '$field': $msg") {
        override val errorCode: String = "ERR_VALIDATION"
        override fun toHttpStatus(): Int = 400
    }

    /** 설정 검증 실패 (SOTA급 설정 검증) */
    data class ConfigError(val msg: String) : DomainError("Configuration validation failed: $msg") {
        override val errorCode: String = "ERR_CONFIG"
        override fun toHttpStatus(): Int = 500
    }

    // ==================== 저장소 ====================

    /** 엔티티 조회 실패 */
    data class NotFoundError(
        val entity: String,
        val key: String,
    ) : DomainError("$entity not found: $key") {
        override val errorCode: String = "ERR_NOT_FOUND"
        override fun toHttpStatus(): Int = 404
    }

    /** 멱등성 위반 (같은 key/version, 다른 payload) */
    data class IdempotencyViolation(val msg: String) : DomainError(msg) {
        override val errorCode: String = "ERR_IDEMPOTENCY"
        override fun toHttpStatus(): Int = 409
    }

    /** DB/저장소 오류 */
    data class StorageError(val msg: String) : DomainError(msg) {
        override val errorCode: String = "ERR_STORAGE"
        override fun toHttpStatus(): Int = 500
    }

    /** 불변 조건 위반 (레거시 호환) */
    data class InvariantViolation(val details: String) : DomainError(details) {
        override val errorCode: String = "ERR_INVARIANT"
        override fun toHttpStatus(): Int = 500
    }

    /** 계약 상태 검증 실패 (RFC-003: DRAFT/ARCHIVED 차단) */
    data class ContractStatusError(
        val contractId: String,
        val status: com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus,
    ) : DomainError("Contract '$contractId' has invalid status: $status (only ACTIVE or DEPRECATED allowed)") {
        override val errorCode: String = "ERR_CONTRACT_STATUS"
        override fun toHttpStatus(): Int = 400
    }

    /** 매핑되지 않은 변경 경로 (RFC-001/003 fail-closed) */
    data class UnmappedChangePathError(
        val unmappedPaths: List<String>,
    ) : DomainError("Unmapped change paths (fail-closed): ${unmappedPaths.joinToString(", ")}") {
        override val errorCode: String = "ERR_UNMAPPED_CHANGE_PATH"
        override fun toHttpStatus(): Int = 400
    }

    // ==================== JOIN ====================

    /** Light JOIN 실행 실패 (RFC-IMPL-010 Phase D-4) */
    data class JoinError(val msg: String) : DomainError(msg) {
        override val errorCode: String = "ERR_JOIN"
        override fun toHttpStatus(): Int = 400
    }

    // ==================== View ====================

    /** 필수 슬라이스 누락 (RFC-IMPL-010 GAP-D: ViewDefinition 정책) */
    data class MissingSliceError(
        val missingSlices: List<String>,
        val reason: String,
    ) : DomainError("Missing slices: ${missingSlices.joinToString(", ")} - $reason") {
        override val errorCode: String = "ERR_MISSING_SLICE"
        override fun toHttpStatus(): Int = 404
    }

    // ==================== 외부 서비스 ====================

    /** 외부 서비스 호출 실패 */
    data class ExternalServiceError(
        val service: String,
        val msg: String,
    ) : DomainError("[$service] $msg") {
        override val errorCode: String = "ERR_EXTERNAL_SERVICE"
        override fun toHttpStatus(): Int = 502
    }

    // ==================== 미지원 기능 ====================

    /** 지원하지 않는 기능 */
    data class NotSupportedError(val msg: String) : DomainError("Not supported: $msg") {
        override val errorCode: String = "ERR_NOT_SUPPORTED"
        override fun toHttpStatus(): Int = 501
    }
}
