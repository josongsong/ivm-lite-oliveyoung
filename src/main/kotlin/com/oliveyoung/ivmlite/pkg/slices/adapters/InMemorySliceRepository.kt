package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InvariantViolation
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.NotFoundError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.util.concurrent.ConcurrentHashMap

class InMemorySliceRepository : SliceRepositoryPort, HealthCheckable {
    override val healthName: String = "slice"
    override suspend fun healthCheck(): Boolean = true
    private val store = ConcurrentHashMap<String, SliceRecord>()

    override suspend fun putAllIdempotent(slices: List<SliceRecord>): SliceRepositoryPort.Result<Unit> {
        for (s in slices) {
            val k = key(s.tenantId, s.entityKey, s.version, s.sliceType)
            val prev = store.putIfAbsent(k, s)
            if (prev != null && prev.hash != s.hash) {
                return SliceRepositoryPort.Result.Err(InvariantViolation("Slice hash mismatch for $k"))
            }
        }
        return SliceRepositoryPort.Result.Ok(Unit)
    }

    override suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceRepositoryPort.SliceKey>,
        includeTombstones: Boolean,
    ): SliceRepositoryPort.Result<List<SliceRecord>> {
        val out = mutableListOf<SliceRecord>()
        for (k in keys) {
            val kk = key(k.tenantId, k.entityKey, k.version, k.sliceType)
            val v = store[kk] ?: return SliceRepositoryPort.Result.Err(NotFoundError("Slice", kk))
            // tombstone 필터링: includeTombstones=false면 삭제된 slice는 NotFound 처리
            if (!includeTombstones && v.isDeleted) {
                return SliceRepositoryPort.Result.Err(NotFoundError("Slice", kk))
            }
            out += v
        }
        return SliceRepositoryPort.Result.Ok(out)
    }

    override suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean,
    ): SliceRepositoryPort.Result<List<SliceRecord>> {
        val result = store.values
            .filter { it.tenantId == tenantId && it.entityKey == entityKey && it.version == version }
            .filter { includeTombstones || !it.isDeleted }
        return SliceRepositoryPort.Result.Ok(result)
    }

    private fun key(t: TenantId, e: EntityKey, v: Long, st: SliceType): String =
        "${t.value}|${e.value}|$v|${st.toDbValue()}"

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
}
