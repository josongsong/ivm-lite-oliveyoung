package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.apps.admin.config.AdminConstants
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * RawData 탐색 서비스
 *
 * P0: SRP 준수 - RawData 관련 기능만 담당
 * - Ingest (등록)
 * - 목록 조회
 * - 상세 조회
 * - 버전 관리
 */
class RawDataExplorerService(
    private val rawDataRepo: RawDataRepositoryPort,
    private val explorerRepo: ExplorerRepositoryPort,
    private val ingestWorkflow: IngestWorkflow?,
    private val slicingWorkflow: SlicingWorkflow?
) {
    private val logger = LoggerFactory.getLogger(RawDataExplorerService::class.java)

    /**
     * RawData 등록 + 선택적 Compile
     */
    suspend fun ingest(
        tenantId: String,
        entityKey: String,
        schemaId: String,
        schemaVersion: String = "1.0.0",
        payload: String,
        compile: Boolean = false
    ): Result<IngestResult> {
        val workflow = ingestWorkflow
            ?: return Result.Err(DomainError.ConfigError("IngestWorkflow not configured"))

        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)
        val version = VersionGenerator.generate()

        val semVer = Result.catch { SemVer.parse(schemaVersion) }
            .let { result ->
                when (result) {
                    is Result.Ok -> result.value
                    is Result.Err -> return Result.Err(
                        DomainError.ValidationError("schemaVersion", "Invalid: $schemaVersion")
                    )
                }
            }

        return Result.catch {
            when (val result = workflow.execute(tenant, entity, version, schemaId, semVer, payload)) {
                is Result.Ok -> {
                    val (slicesCreated, compiled) = compileIfNeeded(tenant, entity, version, compile)
                    IngestResult(
                        tenantId = tenantId,
                        entityKey = entityKey,
                        version = version,
                        schemaId = schemaId,
                        schemaVersion = schemaVersion,
                        payloadHash = Hashing.sha256Hex(payload),
                        compiled = compiled,
                        slicesCreated = slicesCreated,
                        timestamp = Instant.now().toString()
                    )
                }
                is Result.Err -> throw result.error
            }
        }
    }

    private suspend fun compileIfNeeded(
        tenant: TenantId,
        entity: EntityKey,
        version: Long,
        compile: Boolean
    ): Pair<Int, Boolean> {
        if (!compile || slicingWorkflow == null) return Pair(0, false)

        return Result.catch {
            when (val slicingResult = slicingWorkflow.execute(tenant, entity, version)) {
                is Result.Ok -> Pair(slicingResult.value.size, true)
                is Result.Err -> {
                    logger.warn("Slicing error: ${slicingResult.error}")
                    Pair(0, false)
                }
            }
        }.getOrNull() ?: Pair(0, false)
    }

    /**
     * 배치 Ingest (여러 엔티티 병렬 등록)
     *
     * P0: 병렬 처리로 성능 최적화 (기존 순차 처리 → coroutineScope + async)
     */
    suspend fun ingestBatch(
        tenantId: String,
        items: List<IngestItem>
    ): Result<BatchIngestResult> = coroutineScope {
        val deferredResults = items.map { item ->
            async {
                val result = ingest(
                    tenantId = tenantId,
                    entityKey = item.entityKey,
                    schemaId = item.schemaId,
                    schemaVersion = item.schemaVersion,
                    payload = item.payload,
                    compile = item.compile
                )
                item.entityKey to result
            }
        }

        val allResults = deferredResults.awaitAll()
        val succeeded = mutableListOf<IngestResult>()
        val failed = mutableListOf<IngestError>()

        allResults.forEach { (entityKey, result) ->
            result
                .onSuccess { succeeded.add(it) }
                .onFailure { failed.add(IngestError(entityKey, it.message ?: "Unknown error")) }
        }

        Result.Ok(
            BatchIngestResult(
                tenantId = tenantId,
                succeeded = succeeded,
                failed = failed,
                totalCount = items.size,
                successCount = succeeded.size,
                failCount = failed.size
            )
        )
    }

    /**
     * RawData 목록 조회
     */
    suspend fun listRawData(
        tenantId: String,
        entityPrefix: String? = null,
        limit: Int = AdminConstants.DEFAULT_PAGE_SIZE,
        cursor: String? = null
    ): Result<RawDataListResult> {
        val safeLimit = limit.coerceIn(1, AdminConstants.MAX_PAGE_SIZE)
        val tenant = TenantId(tenantId)

        return explorerRepo.listRawData(tenant, entityPrefix, safeLimit, cursor)
            .fold(
                { error -> Result.Err(error) },
                { result ->
                    Result.Ok(
                        RawDataListResult(
                            entries = result.items.map { item ->
                                RawDataListItem(
                                    entityKey = item.entityKey,
                                    version = item.version,
                                    schemaRef = item.schemaId,
                                    updatedAt = item.updatedAt
                                )
                            },
                            total = result.totalCount ?: result.items.size,
                            hasMore = result.nextCursor != null,
                            nextCursor = result.nextCursor
                        )
                    )
                }
            )
    }

    /**
     * RawData 조회 (특정 버전 또는 최신)
     */
    suspend fun getRawData(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): Result<RawDataResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return Result.catch {
            val record = if (version != null) {
                when (val result = rawDataRepo.get(tenant, entity, version)) {
                    is Result.Ok -> result.value
                    is Result.Err -> throw result.error
                }
            } else {
                when (val result = rawDataRepo.getLatest(tenant, entity)) {
                    is Result.Ok -> result.value
                    is Result.Err -> throw result.error
                }
            }

            val versions = getVersionHistory(tenant, entity)

            RawDataResult(
                tenantId = record.tenantId.value,
                entityKey = record.entityKey.value,
                version = record.version,
                schemaId = record.schemaId,
                schemaVersion = record.schemaVersion.toString(),
                payload = ExplorerUtils.parseJsonSafe(record.payload),
                payloadRaw = record.payload,
                payloadHash = record.payloadHash,
                versions = versions
            )
        }
    }

    private suspend fun getVersionHistory(tenantId: TenantId, entityKey: EntityKey): List<VersionInfo> {
        return explorerRepo.getVersionHistory(tenantId, entityKey)
            .fold(
                { emptyList() },
                { items -> items.map { VersionInfo(it.version, it.createdAt, it.payloadHash) } }
            )
    }

}
