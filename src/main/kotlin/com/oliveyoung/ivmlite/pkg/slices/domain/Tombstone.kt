package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.serialization.Serializable

/**
 * Slice 삭제 사유 (RFC-001/003: 증분 업데이트 시 삭제된 결과 표현)
 */
@Serializable
enum class DeleteReason {
    /** 사용자 직접 삭제 요청 */
    USER_DELETE,
    /** 정책에 의한 숨김 처리 */
    POLICY_HIDE,
    /** 유효성 검증 실패로 인한 삭제 */
    VALIDATION_FAIL,
    /** 보관 처리 (아카이브) */
    ARCHIVED;

    fun toDbValue(): String = name.lowercase()

    companion object {
        fun fromDbValue(value: String): DeleteReason =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw DomainError.ValidationError("deleteReason", "Unknown DeleteReason: $value")

        fun fromDbValueOrNull(value: String): DeleteReason? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Tombstone: 삭제된 Slice 표현 (불변)
 *
 * RFC-IMPL-010 Phase D-1:
 * - 결정성: 동일 version → 동일 tombstone 상태
 * - 불변성: tombstone 설정 후 변경 불가
 * - 멱등성: 재삭제해도 동일 상태
 *
 * @property isDeleted 삭제 여부 (항상 true, 존재하면 삭제된 것)
 * @property deletedAtVersion 삭제 시점 버전
 * @property deleteReason 삭제 사유
 */
@Serializable
data class Tombstone(
    val isDeleted: Boolean = true,
    val deletedAtVersion: Long,
    val deleteReason: DeleteReason,
) {
    init {
        require(isDeleted) { "Tombstone.isDeleted must be true" }
        require(deletedAtVersion > 0) { "deletedAtVersion must be positive" }
    }

    companion object {
        /**
         * Tombstone 생성 팩토리
         */
        fun create(version: Long, reason: DeleteReason): Tombstone =
            Tombstone(
                isDeleted = true,
                deletedAtVersion = version,
                deleteReason = reason,
            )
    }
}
