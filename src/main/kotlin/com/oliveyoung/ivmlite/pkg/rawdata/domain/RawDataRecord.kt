package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * RawData는 "원문"이지만, 저장 직전에 Contract(SchemaRef) 검증을 통과해야 한다.
 * Domain은 JSON 라이브러리에 의존하지 않기 위해 payload를 String으로 취급한다.
 */
data class RawDataRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val schemaId: String,
    val schemaVersion: SemVer,
    val payload: String,
    val payloadHash: String,
)
