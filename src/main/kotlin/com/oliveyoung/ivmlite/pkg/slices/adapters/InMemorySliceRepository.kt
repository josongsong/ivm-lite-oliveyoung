package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InvariantViolation
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.NotFoundError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
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

    override suspend fun putAllIdempotent(slices: List<SliceRecord>): Result<Unit> {
        for (s in slices) {
            val k = key(s.tenantId, s.entityKey, s.version, s.sliceType)
            val prev = store.putIfAbsent(k, s)
            if (prev != null && prev.hash != s.hash) {
                return Result.Err(InvariantViolation("Slice hash mismatch for $k"))
            }
        }
        return Result.Ok(Unit)
    }

    override suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceRepositoryPort.SliceKey>,
        includeTombstones: Boolean,
    ): Result<List<SliceRecord>> {
        val out = mutableListOf<SliceRecord>()
        for (k in keys) {
            val kk = key(k.tenantId, k.entityKey, k.version, k.sliceType)
            val v = store[kk] ?: return Result.Err(NotFoundError("Slice", kk))
            // tombstone 필터링: includeTombstones=false면 삭제된 slice는 NotFound 처리
            if (!includeTombstones && v.isDeleted) {
                return Result.Err(NotFoundError("Slice", kk))
            }
            out += v
        }
        return Result.Ok(out)
    }

    override suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean,
    ): Result<List<SliceRecord>> {
        val result = store.values
            .filter { it.tenantId == tenantId && it.entityKey == entityKey && it.version == version }
            .filter { includeTombstones || !it.isDeleted }
        return Result.Ok(result)
    }

    override suspend fun findByKeyPrefix(
        tenantId: TenantId,
        keyPrefix: String,
        sliceType: SliceType?,
        limit: Int,
        cursor: String?,
    ): Result<SliceRepositoryPort.RangeQueryResult> {
        // 커서 파싱 (형식: entityKey|version)
        val startKey = cursor?.let {
            val parts = it.split("|")
            if (parts.size >= 2) Pair(parts[0], parts[1].toLongOrNull() ?: 0L) else null
        }
        
        val filtered = store.values
            .filter { it.tenantId == tenantId }
            .filter { it.entityKey.value.startsWith(keyPrefix) }
            .filter { sliceType == null || it.sliceType == sliceType }
            .filter { !it.isDeleted }
            .sortedWith(compareBy({ it.entityKey.value }, { it.version }))
            .let { list ->
                if (startKey != null) {
                    list.dropWhile { 
                        it.entityKey.value < startKey.first || 
                        (it.entityKey.value == startKey.first && it.version <= startKey.second) 
                    }
                } else {
                    list
                }
            }
        
        val items = filtered.take(limit + 1)
        val hasMore = items.size > limit
        val resultItems = if (hasMore) items.dropLast(1) else items
        
        val nextCursor = if (hasMore && resultItems.isNotEmpty()) {
            val last = resultItems.last()
            "${last.entityKey.value}|${last.version}"
        } else {
            null
        }
        
        return Result.Ok(SliceRepositoryPort.RangeQueryResult(
            items = resultItems,
            nextCursor = nextCursor,
            hasMore = hasMore
        ))
    }

    override suspend fun count(
        tenantId: TenantId,
        keyPrefix: String?,
        sliceType: SliceType?,
    ): Result<Long> {
        val count = store.values
            .filter { it.tenantId == tenantId }
            .filter { keyPrefix == null || it.entityKey.value.startsWith(keyPrefix) }
            .filter { sliceType == null || it.sliceType == sliceType }
            .filter { !it.isDeleted }
            .count()
            .toLong()
        
        return Result.Ok(count)
    }

    override suspend fun getLatestVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        sliceType: SliceType?,
    ): Result<List<SliceRecord>> {
        val allSlices = store.values
            .filter { it.tenantId == tenantId && it.entityKey == entityKey }
            .filter { sliceType == null || it.sliceType == sliceType }
            .filter { !it.isDeleted }
        
        if (allSlices.isEmpty()) {
            return Result.Ok(emptyList())
        }
        
        val latestVersion = allSlices.maxOf { it.version }
        val result = allSlices.filter { it.version == latestVersion }
        
        return Result.Ok(result)
    }

    private fun key(t: TenantId, e: EntityKey, v: Long, st: SliceType): String =
        "${t.value}|${e.value}|$v|${st.toDbValue()}"

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
    
    fun getAllByTenant(tenantId: TenantId): List<SliceRecord> =
        store.values.filter { it.tenantId == tenantId }
}
