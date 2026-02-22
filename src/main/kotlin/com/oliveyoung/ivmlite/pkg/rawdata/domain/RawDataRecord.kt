package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.json.JsonObject

/**
 * RawData는 "원문"이지만, 저장 직전에 Contract(SchemaRef) 검증을 통과해야 한다.
 * Domain은 JSON 라이브러리에 의존하지 않기 위해 payload를 String으로 취급한다.
 */
data class RawDataRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val schemaId: String,
    val schemaVersion: SemVer,
    val payload: String,
    val payloadHash: String,
) {
    companion object {
        /**
         * RawData 생성 헬퍼
         *
         * @param tenantId 테넌트 ID
         * @param entityKey 엔티티 키
         * @param data JSON 데이터
         * @param schemaId 스키마 ID (기본값: "entity.product.v1")
         * @param schemaVersion 스키마 버전 (기본값: 1.0.0)
         * @return RawDataRecord
         */
        fun create(
            tenantId: TenantId,
            entityKey: EntityKey,
            data: JsonObject,
            schemaId: String = "entity.product.v1",
            schemaVersion: SemVer = SemVer.parse("1.0.0"),
            version: Long = 1L
        ): RawDataRecord {
            val payload = data.toString()
            val payloadHash = Hashing.sha256Hex(payload)

            return RawDataRecord(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                schemaId = schemaId,
                schemaVersion = schemaVersion,
                payload = payload,
                payloadHash = payloadHash
            )
        }
    }
}
