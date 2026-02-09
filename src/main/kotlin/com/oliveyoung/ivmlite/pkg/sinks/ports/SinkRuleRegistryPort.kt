package com.oliveyoung.ivmlite.pkg.sinks.ports

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkRule
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * SinkRuleRegistry Port - SinkRule 조회 (RFC-007, RFC-IMPL-013)
 *
 * Slice 생성 시 자동으로 SinkRule을 조회하여 ShipRequested outbox 생성에 사용.
 *
 * 구현체:
 * - ContractRegistrySinkRuleAdapter: Contract YAML에서 로드
 * - InMemorySinkRuleRegistry: 테스트용
 */
interface SinkRuleRegistryPort {

    /**
     * 엔티티 타입과 슬라이스 타입으로 매칭되는 SinkRule 조회
     *
     * @param entityType 엔티티 타입 (예: "PRODUCT", "BRAND")
     * @param sliceType 슬라이스 타입 (예: CORE)
     * @return 매칭되는 SinkRule 목록 (여러 sink로 동시 전송 가능)
     */
    suspend fun findByEntityAndSliceType(
        entityType: String,
        sliceType: SliceType
    ): Result<List<SinkRule>>

    /**
     * 엔티티 타입으로 매칭되는 모든 SinkRule 조회
     */
    suspend fun findByEntityType(entityType: String): Result<List<SinkRule>>

    /**
     * 모든 ACTIVE SinkRule 조회
     */
    suspend fun findAllActive(): Result<List<SinkRule>>

    /**
     * ID로 SinkRule 조회
     */
    suspend fun findById(id: String): Result<SinkRule?>
}
