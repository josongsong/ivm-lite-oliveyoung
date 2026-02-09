package com.oliveyoung.ivmlite.pkg.rawdata.ports

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

interface RawDataRepositoryPort {
    suspend fun putIdempotent(record: RawDataRecord): Result<Unit>
    suspend fun get(tenantId: TenantId, entityKey: EntityKey, version: Long): Result<RawDataRecord>

    /**
     * 최신 버전 RawData 조회 (RFC-IMPL-010 Phase D-4: Light JOIN)
     * entityKey로 최신 version의 RawDataRecord 반환
     */
    suspend fun getLatest(tenantId: TenantId, entityKey: EntityKey): Result<RawDataRecord>

    /**
     * 여러 엔티티의 최신 RawData 일괄 조회 (N+1 쿼리 최적화)
     *
     * JOIN 연산에서 여러 타겟을 한 번에 조회할 때 사용.
     * 단일 IN 쿼리로 N번의 개별 조회를 대체하여 DB 왕복 횟수 최소화.
     *
     * @param tenantId 테넌트 ID
     * @param entityKeys 조회할 엔티티 키 목록
     * @return entityKey -> RawDataRecord 맵 (존재하지 않는 키는 맵에 포함되지 않음)
     */
    suspend fun batchGetLatest(tenantId: TenantId, entityKeys: List<EntityKey>): Result<Map<EntityKey, RawDataRecord>>
}
