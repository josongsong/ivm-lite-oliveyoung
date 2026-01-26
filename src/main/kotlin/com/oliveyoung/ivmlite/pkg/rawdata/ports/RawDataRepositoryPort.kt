package com.oliveyoung.ivmlite.pkg.rawdata.ports

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

interface RawDataRepositoryPort {
    suspend fun putIdempotent(record: RawDataRecord): Result<Unit>
    suspend fun get(tenantId: TenantId, entityKey: EntityKey, version: Long): Result<RawDataRecord>

    /**
     * 최신 버전 RawData 조회 (RFC-IMPL-010 Phase D-4: Light JOIN)
     * entityKey로 최신 version의 RawDataRecord 반환
     */
    suspend fun getLatest(tenantId: TenantId, entityKey: EntityKey): Result<RawDataRecord>

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
