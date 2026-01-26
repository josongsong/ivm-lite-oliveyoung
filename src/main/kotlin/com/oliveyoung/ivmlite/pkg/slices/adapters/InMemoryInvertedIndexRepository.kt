package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InvariantViolation
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory Inverted Index Repository (테스트/로컬 개발용)
 * RFC-IMPL-010 GAP-G: HealthCheckable 구현
 */
class InMemoryInvertedIndexRepository : InvertedIndexRepositoryPort, HealthCheckable {

    override val healthName: String = "inverted-index-repo"

    override suspend fun healthCheck(): Boolean = true  // InMemory는 항상 정상
    // key: tenant|refKey|refVersion|targetKey|sliceType|indexType|indexValue
    private val store = ConcurrentHashMap<String, InvertedIndexEntry>()

    override suspend fun putAllIdempotent(entries: List<InvertedIndexEntry>): InvertedIndexRepositoryPort.Result<Unit> {
        for (e in entries) {
            val k = key(e)
            val prev = store.putIfAbsent(k, e)
            if (prev != null && prev.sliceHash != e.sliceHash) {
                return InvertedIndexRepositoryPort.Result.Err(InvariantViolation("InvertedIndex hash mismatch for $k"))
            }
        }
        return InvertedIndexRepositoryPort.Result.Ok(Unit)
    }

    override suspend fun listTargets(tenantId: TenantId, refPk: String, limit: Int): InvertedIndexRepositoryPort.Result<List<InvertedIndexEntry>> {
        val out = store.values
            .asSequence()
            .filter { it.tenantId == tenantId }
            .filter { it.refEntityKey.value.startsWith(refPk) || it.refEntityKey.value == refPk }
            .take(limit)
            .toList()
        return InvertedIndexRepositoryPort.Result.Ok(out)
    }

    private fun key(e: InvertedIndexEntry): String =
        "${e.tenantId.value}|${e.refEntityKey.value}|${e.refVersion.value}|${e.targetEntityKey.value}|${e.sliceType.name}|${e.indexType}|${e.indexValue}"

    // 테스트용 헬퍼: indexType과 indexValue로 조회
    fun queryByIndexForTest(tenantId: TenantId, indexType: String, indexValue: String): List<InvertedIndexEntry> {
        return store.values
            .filter { it.tenantId == tenantId }
            .filter { it.indexType == indexType }
            .filter { it.indexValue == indexValue }
            .toList()
    }
}
