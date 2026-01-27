package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.types.SemVer

data class ContractMeta(
    val kind: String,
    val id: String,
    val version: SemVer,
    val status: ContractStatus,
)

/**
 * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
 * RuleSet.indexes의 IndexSpec.references로 통합되었습니다.
 * 역방향 인덱스는 IndexSpec.references 필드를 통해 자동 생성됩니다.
 *
 * Migration: inverted-index.v1.yaml 삭제, RuleSet indexes에 references 추가
 */
@Deprecated("Use IndexSpec.references instead", level = DeprecationLevel.WARNING)
data class InvertedIndexContract(
    val meta: ContractMeta,
    val pkPattern: String,
    val skPattern: String,
    val padWidth: Int,
    val separator: String,
    val maxTargetsPerRef: Int,
)

data class JoinSpecContract(
    val meta: ContractMeta,
    val maxJoinDepth: Int,
    val maxFanout: Int,
    @Deprecated("invertedIndexRef는 더 이상 사용되지 않습니다. IndexSpec.references로 통합됨")
    val invertedIndexRef: ContractRef? = null,
)

data class ChangeSetContract(
    val meta: ContractMeta,
    val entityKeyFormat: String,
    val externalizeThresholdBytes: Int,
    val fanoutEnabled: Boolean,
)

data class ContractRef(val id: String, val version: SemVer)
