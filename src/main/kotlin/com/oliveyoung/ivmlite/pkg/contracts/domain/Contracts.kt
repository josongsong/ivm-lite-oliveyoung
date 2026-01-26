package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.types.SemVer

data class ContractMeta(
    val kind: String,
    val id: String,
    val version: SemVer,
    val status: ContractStatus,
)

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
    val invertedIndexRef: ContractRef,
)

data class ChangeSetContract(
    val meta: ContractMeta,
    val entityKeyFormat: String,
    val externalizeThresholdBytes: Int,
    val fanoutEnabled: Boolean,
)

data class ContractRef(val id: String, val version: SemVer)
