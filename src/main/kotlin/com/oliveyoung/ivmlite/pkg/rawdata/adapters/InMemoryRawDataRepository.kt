package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InvariantViolation
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.NotFoundError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트/로컬 개발용.
 * idempotency 규칙: 동일 key/version에 대해 hash가 동일하면 OK, 다르면 InvariantViolation.
 */
class InMemoryRawDataRepository : RawDataRepositoryPort, HealthCheckable {
    override val healthName: String = "rawdata"
    override suspend fun healthCheck(): Boolean = true
    private val store = ConcurrentHashMap<String, RawDataRecord>()

    override suspend fun putIdempotent(record: RawDataRecord): Result<Unit> {
        val k = key(record.tenantId, record.entityKey, record.version)
        val prev = store.putIfAbsent(k, record)
        if (prev == null) return Result.Ok(Unit)
        // RFC-IMPL-003: v1 최소 불변식
        // 동일 (tenantId, entityKey, version) 재시도 시 payloadHash 뿐 아니라 schemaId/schemaVersion도 동일해야 함
        return if (
            prev.payloadHash == record.payloadHash &&
            prev.schemaId == record.schemaId &&
            prev.schemaVersion == record.schemaVersion
        ) {
            Result.Ok(Unit)
        } else {
            Result.Err(InvariantViolation("RawData invariant mismatch for $k"))
        }
    }

    override suspend fun get(tenantId: TenantId, entityKey: EntityKey, version: Long): Result<RawDataRecord> {
        val k = key(tenantId, entityKey, version)
        val v = store[k] ?: return Result.Err(NotFoundError("RawData", k))
        return Result.Ok(v)
    }

    override suspend fun getLatest(tenantId: TenantId, entityKey: EntityKey): Result<RawDataRecord> {
        val prefix = "${tenantId.value}|${entityKey.value}|"
        val latest = store.entries
            .filter { it.key.startsWith(prefix) }
            .maxByOrNull { it.value.version }
            ?.value
            ?: return Result.Err(NotFoundError("RawData", "${tenantId.value}|${entityKey.value}|*"))
        return Result.Ok(latest)
    }

    override suspend fun batchGetLatest(
        tenantId: TenantId,
        entityKeys: List<EntityKey>,
    ): Result<Map<EntityKey, RawDataRecord>> {
        if (entityKeys.isEmpty()) {
            return Result.Ok(emptyMap())
        }

        val resultMap = mutableMapOf<EntityKey, RawDataRecord>()
        for (entityKey in entityKeys) {
            val prefix = "${tenantId.value}|${entityKey.value}|"
            val latest = store.entries
                .filter { it.key.startsWith(prefix) }
                .maxByOrNull { it.value.version }
                ?.value
            if (latest != null) {
                resultMap[entityKey] = latest
            }
        }
        return Result.Ok(resultMap)
    }

    private fun key(t: TenantId, e: EntityKey, v: Long): String = "${t.value}|${e.value}|$v"

    // ==================== 테스트 헬퍼 ====================

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
}
