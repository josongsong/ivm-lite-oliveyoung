package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.IngestUnitOfWorkPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cross-domain orchestration workflow for runtime ingestion flow.
 *
 * RFC-V4-010: 외부 진입점은 *Workflow로 명명
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 *
 * ## Transactional Outbox 패턴
 * - IngestUnitOfWorkPort 사용 시: RawData + Outbox를 단일 트랜잭션으로 처리 (권장)
 * - 개별 Port 사용 시: 순차 실행 (테스트/레거시 호환용)
 *
 * NOTE: 현재는 rawdata 저장만 수행하지만, v1+에서는 ChangeSet/Slicing/Sink trigger 등을
 * 단계적으로 이어붙일 수 있는 "워크플로우"의 자리다.
 */
class IngestWorkflow private constructor(
    private val unitOfWork: IngestUnitOfWorkPort?,
    private val rawRepo: RawDataRepositoryPort?,
    private val outboxRepo: OutboxRepositoryPort?,
    private val tracer: Tracer,
) {
    /**
     * Primary constructor: UnitOfWork 사용 (트랜잭션 원자성 보장)
     */
    constructor(
        unitOfWork: IngestUnitOfWorkPort,
        tracer: Tracer = OpenTelemetry.noop().getTracer("ingest"),
    ) : this(unitOfWork, null, null, tracer)

    /**
     * Legacy constructor: 개별 Port 사용 (하위 호환성)
     *
     * NOTE: 트랜잭션 원자성이 보장되지 않음. IngestUnitOfWorkPort 사용 권장.
     */
    constructor(
        rawRepo: RawDataRepositoryPort,
        outboxRepo: OutboxRepositoryPort,
        tracer: Tracer = OpenTelemetry.noop().getTracer("ingest"),
    ) : this(null, rawRepo, outboxRepo, tracer)

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
                "transactional" to (unitOfWork != null).toString(),
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

            // Outbox 이벤트 생성
            val safePayload = buildOutboxPayload(tenantId.value, entityKey.value, version)
            val outboxEntry = OutboxEntry.create(
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "${tenantId.value}:${entityKey.value}",
                eventType = "RawDataIngested",
                payload = safePayload,
            )

            // === 트랜잭션 실행 ===
            if (unitOfWork != null) {
                // UnitOfWork: 단일 트랜잭션으로 원자적 처리
                when (val r = unitOfWork.executeIngest(record, outboxEntry)) {
                    is Result.Ok -> Result.Ok(Unit)
                    is Result.Err -> Result.Err(r.error)
                }
            } else {
                // Legacy: 순차 실행 (하위 호환성)
                val rawRepoNonNull = rawRepo ?: return@withSpanSuspend Result.Err(
                    ContractError("Neither unitOfWork nor rawRepo is configured")
                )
                val outboxRepoNonNull = outboxRepo ?: return@withSpanSuspend Result.Err(
                    ContractError("Neither unitOfWork nor outboxRepo is configured")
                )

                when (val r = rawRepoNonNull.putIdempotent(record)) {
                    is Result.Ok -> { /* continue */ }
                    is Result.Err -> return@withSpanSuspend Result.Err(r.error)
                }

                when (val r = outboxRepoNonNull.insert(outboxEntry)) {
                    is Result.Ok -> Result.Ok(Unit)
                    is Result.Err -> Result.Err(r.error)
                }
            }
        }
    }

    /**
     * Outbox payload를 안전하게 생성
     *
     * kotlinx.serialization 사용으로 자동 escape 처리 (XSS/Injection 방지)
     * NOTE: payloadVersion 필드로 스키마 버전 관리 (확장성 보장)
     */
    private fun buildOutboxPayload(tenantId: String, entityKey: String, version: Long): String {
        val payload = buildJsonObject {
            put("payloadVersion", "1.0")
            put("tenantId", tenantId)
            put("entityKey", entityKey)
            put("version", version)
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload)
    }
}
