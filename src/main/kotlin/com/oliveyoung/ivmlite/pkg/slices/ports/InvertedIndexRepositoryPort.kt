package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry

interface InvertedIndexRepositoryPort {
    suspend fun putAllIdempotent(entries: List<InvertedIndexEntry>): Result<Unit>
    suspend fun listTargets(tenantId: TenantId, refPk: String, limit: Int): Result<List<InvertedIndexEntry>>

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
