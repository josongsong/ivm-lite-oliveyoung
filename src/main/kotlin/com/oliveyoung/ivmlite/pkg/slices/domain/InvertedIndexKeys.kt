package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * InvertedIndex 키 생성 유틸리티 (통합 버전)
 *
 * InvertedIndex는 "검색"이 아니라 JOIN fanout / impact 계산을 위한 reverse reference table이다.
 * (OpenSearch류 인덱스와 무관)
 *
 * ## 키 패턴
 * - PK: REF#{refEntityType}#{tenantId}#{refEntityId}#v{paddedVersion}
 * - SK: {targetEntityType}#{targetEntityId}
 *
 * ## 통합 변경 사항
 * - InvertedIndexContract 의존성 제거
 * - 기본값으로 동작하도록 단순화
 */
object InvertedIndexKeys {

    /** 기본 패딩 폭 (버전 숫자) */
    const val DEFAULT_PAD_WIDTH = 12

    /** 기본 구분자 */
    const val DEFAULT_SEPARATOR = "#"

    /**
     * 참조 엔티티의 PK 생성
     *
     * @param tenantId 테넌트 ID
     * @param refEntityKey 참조 엔티티 키 (예: "BRAND#tenant#br001")
     * @param refVersion 참조 엔티티 버전
     * @param padWidth 버전 패딩 폭 (기본값: 12)
     * @param separator 구분자 (기본값: "#")
     * @return PK 문자열
     */
    fun refPk(
        tenantId: TenantId,
        refEntityKey: EntityKey,
        refVersion: Long,
        padWidth: Int = DEFAULT_PAD_WIDTH,
        separator: String = DEFAULT_SEPARATOR,
    ): String {
        val (refType, _, refId) = splitEntityKey(refEntityKey)
        val v = refVersion.toString().padStart(padWidth, '0')
        return "REF$separator$refType$separator${tenantId.value}$separator$refId${separator}v$v"
    }

    /**
     * 타겟 엔티티의 SK 생성
     *
     * @param targetEntityKey 타겟 엔티티 키 (예: "PRODUCT#tenant#prod001")
     * @param separator 구분자 (기본값: "#")
     * @return SK 문자열
     */
    fun targetSk(
        targetEntityKey: EntityKey,
        separator: String = DEFAULT_SEPARATOR,
    ): String {
        val (tType, _, tId) = splitEntityKey(targetEntityKey)
        return "$tType$separator$tId"
    }

    /**
     * EntityKey를 분리
     *
     * 표준 포맷: {ENTITY_TYPE}#{tenantId}#{entityId}
     */
    fun splitEntityKey(key: EntityKey): Triple<String, String, String> {
        val parts = key.value.split('#')
        require(parts.size >= 3) { "Invalid entityKey: ${key.value}" }
        return Triple(parts[0], parts[1], parts[2])
    }

    // ===== Deprecated: InvertedIndexContract 기반 메서드 (하위 호환성) =====

    @Deprecated(
        "Use refPk(tenantId, refEntityKey, refVersion) instead",
        ReplaceWith("refPk(tenantId, refEntityKey, refVersion)")
    )
    @Suppress("DEPRECATION")
    fun refPk(
        contract: com.oliveyoung.ivmlite.pkg.contracts.domain.InvertedIndexContract,
        tenantId: TenantId,
        refEntityKey: EntityKey,
        refVersion: Long,
    ): String = refPk(tenantId, refEntityKey, refVersion, contract.padWidth, contract.separator)

    @Deprecated(
        "Use targetSk(targetEntityKey) instead",
        ReplaceWith("targetSk(targetEntityKey)")
    )
    @Suppress("DEPRECATION")
    fun targetSk(
        contract: com.oliveyoung.ivmlite.pkg.contracts.domain.InvertedIndexContract,
        targetEntityKey: EntityKey,
    ): String = targetSk(targetEntityKey, contract.separator)
}
