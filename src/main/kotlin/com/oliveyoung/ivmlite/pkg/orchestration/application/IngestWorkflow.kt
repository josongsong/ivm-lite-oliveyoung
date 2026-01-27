package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Cross-domain orchestration workflow for runtime ingestion flow.
 *
 * RFC-V4-010: 외부 진입점은 *Workflow로 명명
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 * 
 * NOTE: 현재는 rawdata 저장만 수행하지만, v1+에서는 ChangeSet/Slicing/Sink trigger 등을
 * 단계적으로 이어붙일 수 있는 "워크플로우"의 자리다.
 */
class IngestWorkflow(
    private val rawRepo: RawDataRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("ingest"),
) {
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        schemaId: String,
        schemaVersion: SemVer,
        payloadJson: String,
    ): Result<Unit> {
        return tracer.withSpanSuspend(
            "IngestWorkflow.execute",
            mapOf(
                "tenant_id" to tenantId.value,
                "entity_key" to entityKey.value,
                "version" to version.toString(),
                "schema_id" to schemaId,
                "schema_version" to schemaVersion.toString(),
            ),
        ) {
            // v4: schema validation은 Port로 분리되어야 하나, 초기 스캐폴딩에서는 JSON 파싱 가능 여부만 체크.
            val canonical = try {
                CanonicalJson.canonicalize(payloadJson)
            } catch (e: Exception) {
                return@withSpanSuspend Result.Err(ContractError("invalid json payload: ${e.message}"))
            }
            // RFC-V4-002: raw_hash = SHA256(canonical(payload) + schema_id + schema_version)
            val hashInput = canonical + "|" + schemaId + "|" + schemaVersion.toString()
            val hash = "sha256:" + Hashing.sha256Hex(hashInput)
            val record = RawDataRecord(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                schemaId = schemaId,
                schemaVersion = schemaVersion,
                payload = canonical,
                payloadHash = hash,
            )
            // RawData 저장
            when (val r = rawRepo.putIdempotent(record)) {
                is RawDataRepositoryPort.Result.Ok -> { /* continue */ }
                is RawDataRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // Outbox에 이벤트 저장 (Transactional Outbox 패턴)
            // JSON escape 적용하여 안전한 payload 생성
            val safePayload = buildOutboxPayload(tenantId.value, entityKey.value, version)
            val outboxEntry = OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "${tenantId.value}:${entityKey.value}",
                eventType = "RawDataIngested",
                payload = safePayload,
            )
            when (val r = outboxRepo.insert(outboxEntry)) {
                is OutboxRepositoryPort.Result.Ok -> Result.Ok(Unit)
                is OutboxRepositoryPort.Result.Err -> Result.Err(r.error)
            }
        }
    }

    /**
     * Outbox payload를 안전하게 생성 (JSON escape 적용)
     * 
     * NOTE: XSS/Injection 방지를 위해 특수문자 escape
     * NOTE: payloadVersion 필드로 스키마 버전 관리 (확장성 보장)
     */
    private fun buildOutboxPayload(tenantId: String, entityKey: String, version: Long): String {
        val safeTenantId = escapeJsonString(tenantId)
        val safeEntityKey = escapeJsonString(entityKey)
        return """{"payloadVersion":"1.0","tenantId":"$safeTenantId","entityKey":"$safeEntityKey","version":$version}"""
    }
    
    /**
     * JSON 문자열 escape (RFC 8259 준수)
     */
    private fun escapeJsonString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
