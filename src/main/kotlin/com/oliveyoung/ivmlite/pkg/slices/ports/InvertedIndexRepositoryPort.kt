package com.oliveyoung.ivmlite.pkg.slices.ports

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry

interface InvertedIndexRepositoryPort {
    suspend fun putAllIdempotent(entries: List<InvertedIndexEntry>): Result<Unit>
    suspend fun listTargets(tenantId: TenantId, refPk: String, limit: Int): Result<List<InvertedIndexEntry>>

    /**
     * RFC-IMPL-012: Fanout을 위한 역참조 조회
     *
     * 특정 인덱스 타입과 값으로 영향받는 엔티티들을 조회.
     * 예: indexType="product_by_brand", indexValue="BR001" → 해당 브랜드를 참조하는 모든 Product
     *
     * @param tenantId 테넌트 ID
     * @param indexType 인덱스 타입 (예: "product_by_brand")
     * @param indexValue 인덱스 값 (예: "BR001")
     * @param limit 최대 반환 개수
     * @param cursor 페이지네이션 커서 (null이면 처음부터)
     * @return 영향받는 엔티티 키 목록과 다음 커서
     */
    suspend fun queryByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
        limit: Int = 1000,
        cursor: String? = null,
    ): Result<FanoutQueryResult> {
        // 기본 구현: 빈 결과 (하위 호환성)
        return Result.Ok(FanoutQueryResult(emptyList(), null))
    }

    /**
     * RFC-IMPL-012: Fanout 대상 수 조회 (circuit breaker용)
     *
     * @param tenantId 테넌트 ID
     * @param indexType 인덱스 타입
     * @param indexValue 인덱스 값
     * @return 영향받는 엔티티 수
     */
    suspend fun countByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
    ): Result<Long> {
        // 기본 구현: 전체 조회 후 카운트 (비효율적, 구현체에서 최적화 필요)
        return when (val result = queryByIndexType(tenantId, indexType, indexValue, Int.MAX_VALUE)) {
            is Result.Ok -> Result.Ok(result.value.entries.size.toLong())
            is Result.Err -> Result.Err(result.error)
        }
    }
}

/**
 * Fanout 쿼리 결과
 */
data class FanoutQueryResult(
    /**
     * 영향받는 엔티티 목록
     */
    val entries: List<FanoutTarget>,

    /**
     * 다음 페이지 커서 (null이면 마지막)
     */
    val nextCursor: String?,
)

/**
 * Fanout 대상 엔티티
 */
data class FanoutTarget(
    /**
     * 영향받는 엔티티 키
     */
    val entityKey: EntityKey,

    /**
     * 현재 버전 (재슬라이싱 시 version+1로 생성)
     */
    val currentVersion: Long,
)
