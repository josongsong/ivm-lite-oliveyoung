package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.InvertedIndexContract
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * InvertedIndex는 "검색"이 아니라 JOIN fanout / impact 계산을 위한 reverse reference table이다.
 * (OpenSearch류 인덱스와 무관)
 */
object InvertedIndexKeys {

    fun refPk(
        contract: InvertedIndexContract,
        tenantId: TenantId,
        refEntityKey: EntityKey,
        refVersion: Long,
    ): String {
        val (refType, _, refId) = splitEntityKey(refEntityKey)
        val v = refVersion.toString().padStart(contract.padWidth, '0')
        return "REF${contract.separator}$refType${contract.separator}${tenantId.value}${contract.separator}$refId${contract.separator}v$v"
    }

    fun targetSk(contract: InvertedIndexContract, targetEntityKey: EntityKey): String {
        val (tType, _, tId) = splitEntityKey(targetEntityKey)
        return "$tType${contract.separator}$tId"
    }

    private fun splitEntityKey(key: EntityKey): Triple<String, String, String> {
        // 표준: {ENTITY_TYPE}#{tenantId}#{entityId}
        val parts = key.value.split('#')
        require(parts.size >= 3) { "Invalid entityKey: ${key.value}" }
        return Triple(parts[0], parts[1], parts[2])
    }
}
