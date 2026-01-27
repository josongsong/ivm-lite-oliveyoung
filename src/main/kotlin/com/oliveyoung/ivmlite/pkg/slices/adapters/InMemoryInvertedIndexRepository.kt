package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InvariantViolation
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.FanoutQueryResult
import com.oliveyoung.ivmlite.pkg.slices.ports.FanoutTarget
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory Inverted Index Repository (테스트/로컬 개발용)
 * RFC-IMPL-010 GAP-G: HealthCheckable 구현
 * RFC-IMPL-012: Fanout 역참조 조회 지원
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

    /**
     * RFC-IMPL-012: Fanout을 위한 역참조 조회
     *
     * indexType과 indexValue로 영향받는 엔티티들을 조회.
     * cursor 기반 페이지네이션 지원.
     */
    override suspend fun queryByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
        limit: Int,
        cursor: String?,
    ): InvertedIndexRepositoryPort.Result<FanoutQueryResult> {
        // 1. 필터링: indexType + indexValue + tombstone=false
        val filtered = store.values
            .asSequence()
            .filter { it.tenantId == tenantId }
            .filter { it.indexType == indexType }
            .filter { it.indexValue.equals(indexValue, ignoreCase = true) }
            .filter { !it.tombstone }
            .distinctBy { it.targetEntityKey.value }  // 중복 제거 (같은 엔티티가 여러 버전 가질 수 있음)
            .sortedBy { it.targetEntityKey.value }  // 결정적 순서
            .toList()

        // 2. 커서 기반 페이지네이션
        val startIndex = if (cursor != null) {
            filtered.indexOfFirst { it.targetEntityKey.value > cursor }.takeIf { it >= 0 } ?: filtered.size
        } else {
            0
        }

        val page = filtered.drop(startIndex).take(limit)
        val nextCursor = if (startIndex + limit < filtered.size) {
            page.lastOrNull()?.targetEntityKey?.value
        } else {
            null
        }

        // 3. FanoutTarget으로 변환
        // RFC-IMPL-013: 역방향 인덱스에서 targetEntityKey (참조하는 엔티티)를 반환해야 함
        // refEntityKey는 참조되는 엔티티(예: BRAND), targetEntityKey는 참조하는 엔티티(예: PRODUCT)
        val targets = page.map { entry ->
            FanoutTarget(
                entityKey = entry.targetEntityKey,  // 재슬라이싱할 대상
                currentVersion = entry.targetVersion.value,
            )
        }

        return InvertedIndexRepositoryPort.Result.Ok(FanoutQueryResult(targets, nextCursor))
    }

    /**
     * RFC-IMPL-012: Fanout 대상 수 조회 (최적화된 버전)
     */
    override suspend fun countByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
    ): InvertedIndexRepositoryPort.Result<Long> {
        val count = store.values
            .asSequence()
            .filter { it.tenantId == tenantId }
            .filter { it.indexType == indexType }
            .filter { it.indexValue.equals(indexValue, ignoreCase = true) }
            .filter { !it.tombstone }
            .distinctBy { it.targetEntityKey.value }  // targetEntityKey 기준 중복 제거
            .count()
            .toLong()

        return InvertedIndexRepositoryPort.Result.Ok(count)
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

    // 테스트용 헬퍼: 전체 클리어
    fun clear() {
        store.clear()
    }

    // 테스트용 헬퍼: 저장된 엔트리 수
    fun size(): Int = store.size
}
