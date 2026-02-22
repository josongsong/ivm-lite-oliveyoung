package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * Cross-domain orchestration workflow for runtime ingestion flow.
 *
 * RawData 저장만 수행 (Outbox 생성 제거됨).
 * NOTE: Runtime API는 IngestionOrchestrator 사용 (RawData → Slice → View → SinkEvent).
 * 이 클래스는 Admin Explorer 하위 호환성 유지용 (RawData 저장만).
 *
 * @see com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
 */
@Deprecated(
    message = "IngestionOrchestrator 사용 권장 (전체 파이프라인)",
    replaceWith = ReplaceWith("IngestionOrchestrator")
)
class IngestWorkflow(
    private val rawRepo: RawDataRepositoryPort,
) {
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        schemaId: String,
        schemaVersion: SemVer,
        payloadJson: String,
        @Suppress("UNUSED_PARAMETER") jobId: String? = null,
    ): Result<Unit> {
        // JSON 검증
        val canonical: String = CanonicalJson.canonicalizeOrNull(payloadJson)
            ?: return Result.Err(ContractError("invalid json payload"))

        // RFC-V4-002: raw_hash = SHA256(canonical(payload) + schema_id + schema_version)
        val hashInput: String = "$canonical|$schemaId|$schemaVersion"
        val hash: String = "sha256:${Hashing.sha256Hex(hashInput)}"

        val record = RawDataRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payload = canonical,
            payloadHash = hash,
        )

        // RawData 저장 (Outbox 제거됨)
        return rawRepo.putIdempotent(record)
    }
}
