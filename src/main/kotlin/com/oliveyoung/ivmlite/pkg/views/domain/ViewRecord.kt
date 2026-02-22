package com.oliveyoung.ivmlite.pkg.views.domain

import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import java.time.Instant

/**
 * View - Slice 조합으로 생성된 최종 데이터
 *
 * RFC-003: ViewDefinition 기반 Slice 조합
 * - requiredSlices: 필수 Slice (없으면 FAIL_CLOSED)
 * - optionalSlices: 선택적 Slice (없어도 OK)
 *
 * 멱등성: 동일 입력 → 동일 hash → 중복 저장 방지
 */
data class ViewRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val viewType: String,              // 예: "PRODUCT_DETAIL", "PRODUCT_SEARCH"
    val data: String,                  // JSON (조합된 View 데이터)
    val hash: String,                  // SHA-256 hash (멱등성 키)
    val viewDefId: String,             // ViewDefinition ID
    val viewDefVersion: String,        // ViewDefinition 버전
    val usedSlices: List<String>,      // 사용된 SliceType 목록
    val createdAt: Instant = Instant.now()
) {
    init {
        require(tenantId.value.isNotBlank()) { "tenantId must not be blank" }
        require(entityKey.value.isNotBlank()) { "entityKey must not be blank" }
        require(version > 0) { "version must be positive" }
        require(viewType.isNotBlank()) { "viewType must not be blank" }
        require(data.isNotBlank()) { "data must not be blank" }
        require(hash.isNotBlank()) { "hash must not be blank" }
        require(viewDefId.isNotBlank()) { "viewDefId must not be blank" }
        require(viewDefVersion.isNotBlank()) { "viewDefVersion must not be blank" }
    }

    companion object {
        /**
         * View 생성 (결정적 hash 계산)
         *
         * hash = SHA-256(viewType + data + viewDefVersion)
         * → 동일 입력 = 동일 hash = 멱등성 보장
         */
        fun create(
            tenantId: TenantId,
            entityKey: EntityKey,
            version: Long,
            viewType: String,
            data: String,
            viewDefId: String,
            viewDefVersion: String,
            usedSlices: List<String>
        ): ViewRecord {
            val hash = calculateHash(viewType, data, viewDefVersion)
            return ViewRecord(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                viewType = viewType,
                data = data,
                hash = hash,
                viewDefId = viewDefId,
                viewDefVersion = viewDefVersion,
                usedSlices = usedSlices
            )
        }

        /**
         * 결정적 hash 계산
         */
        fun calculateHash(viewType: String, data: String, viewDefVersion: String): String {
            val input = "$viewType|$data|$viewDefVersion"
            return Hashing.sha256Hex(input)
        }
    }
}

/**
 * ViewKey - View 조회용 복합 키
 */
data class ViewKey(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val viewType: String
)
