package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Data Explorer Service
 *
 * Admin UI의 Data Explorer 기능을 위한 서비스.
 * RawData, Slice, View를 탐색하고 Lineage를 추적합니다.
 *
 * 핵심 기능:
 * - RawData 조회 (특정 버전 / 최신 버전)
 * - Slice 목록 조회 및 필터링
 * - View 조합 미리보기
 * - Lineage 그래프 (데이터 흐름 추적)
 * - 스마트 검색 (자동완성)
 */
class ExplorerService(
    private val rawDataRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val explorerRepo: ExplorerRepositoryPort,
    private val queryViewWorkflow: QueryViewWorkflow?,
    private val contractRegistry: ContractRegistryPort?,
    private val slicingWorkflow: SlicingWorkflow?
) {
    private val logger = LoggerFactory.getLogger(ExplorerService::class.java)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ==================== RawData 등록 (SDK DSL 활용) ====================

    /**
     * RawData 등록 + 선택적 Compile
     *
     * SDK DSL을 활용한 기깔나는 DX:
     * - ingestOnly: RawData만 저장
     * - ingestAndCompile: RawData 저장 + Slicing 즉시 실행
     */
    suspend fun ingest(
        tenantId: String,
        entityKey: String,
        schemaId: String,
        schemaVersion: String = "1.0.0",
        payload: String,
        compile: Boolean = false
    ): Result<IngestResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)
        val version = VersionGenerator.generate()

        val semVer = try {
            SemVer.parse(schemaVersion)
        } catch (e: Exception) {
            return Result.Err(DomainError.ValidationError("schemaVersion", "Invalid schema version: $schemaVersion"))
        }

        // JSON 검증 및 RawData 저장 (IngestionOrchestrator 대신 RawData만 저장)
        val canonical = CanonicalJson.canonicalizeOrNull(payload)
            ?: return Result.Err(ContractError("invalid json payload"))
        val hashInput = "$canonical|$schemaId|$semVer"
        val hash = "sha256:${Hashing.sha256Hex(hashInput)}"
        val record = RawDataRecord(
            tenantId = tenant,
            entityKey = entity,
            version = version,
            schemaId = schemaId,
            schemaVersion = semVer,
            payload = canonical,
            payloadHash = hash,
        )

        return try {
            when (val result = rawDataRepo.putIdempotent(record)) {
                is Result.Ok -> {
                    // 2. 선택적 Compile (Slicing)
                    var slicesCreated = 0
                    var actuallyCompiled = false
                    if (compile && slicingWorkflow != null) {
                        try {
                            when (val slicingResult = slicingWorkflow.execute(tenant, entity, version)) {
                                is Result.Ok -> {
                                    slicesCreated = slicingResult.value.size
                                    actuallyCompiled = true
                                }
                                is Result.Err -> {
                                    logger.warn("Slicing error: ${slicingResult.error}")
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Slicing failed but ingest succeeded: ${e.message}")
                        }
                    }

                    Result.Ok(
                        IngestResult(
                            tenantId = tenantId,
                            entityKey = entityKey,
                            version = version,
                            schemaId = schemaId,
                            schemaVersion = schemaVersion,
                            payloadHash = Hashing.sha256Hex(payload),
                            compiled = actuallyCompiled,
                            slicesCreated = slicesCreated,
                            timestamp = Instant.now().toString()
                        )
                    )
                }
                is Result.Err -> Result.Err(result.error)
            }
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to ingest RawData: ${e.message}"))
        }
    }

    /**
     * 배치 Ingest (여러 엔티티 동시 등록)
     */
    suspend fun ingestBatch(
        tenantId: String,
        items: List<IngestItem>
    ): Result<BatchIngestResult> {
        val results = mutableListOf<IngestResult>()
        val errors = mutableListOf<IngestError>()

        items.forEach { item ->
            when (val result = ingest(
                tenantId = tenantId,
                entityKey = item.entityKey,
                schemaId = item.schemaId,
                schemaVersion = item.schemaVersion,
                payload = item.payload,
                compile = item.compile
            )) {
                is Result.Ok -> results.add(result.value)
                is Result.Err -> errors.add(
                    IngestError(
                        entityKey = item.entityKey,
                        error = result.error.message ?: "Unknown error"
                    )
                )
            }
        }

        return Result.Ok(
            BatchIngestResult(
                tenantId = tenantId,
                succeeded = results,
                failed = errors,
                totalCount = items.size,
                successCount = results.size,
                failCount = errors.size
            )
        )
    }

    // ==================== RawData 조회 ====================

    /**
     * RawData 목록 조회 (페이지네이션 지원, DynamoDB via ExplorerRepositoryPort)
     */
    suspend fun listRawData(
        tenantId: String,
        entityPrefix: String? = null,
        limit: Int = 50,
        cursor: String? = null
    ): Result<RawDataListResult> {
        val safeLimit = limit.coerceIn(1, 100)
        val tenant = TenantId(tenantId)
        val entityType = entityPrefix?.takeIf { it.isNotBlank() }

        return try {
            explorerRepo.listRawData(tenant, entityType, safeLimit, cursor).fold(
                { err -> Result.Err(err) },
                { r ->
                    val entries = r.items.map { item ->
                        RawDataListItem(
                            entityId = item.entityKey,
                            version = item.version,
                            schemaRef = item.schemaId,
                            updatedAt = item.updatedAt
                        )
                    }
                    Result.Ok(
                        RawDataListResult(
                            entries = entries,
                            total = r.totalCount ?: entries.size,
                            hasMore = r.nextCursor != null,
                            nextCursor = r.nextCursor
                        )
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Failed to list RawData: ${e.message}", e)
            Result.Err(DomainError.StorageError("Failed to list RawData: ${e.message}"))
        }
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

        return try {
            val record = if (version != null) {
                when (val result = rawDataRepo.get(tenant, entity, version)) {
                    is Result.Ok -> result.value
                    is Result.Err -> return Result.Err(result.error)
                }
            } else {
                when (val result = rawDataRepo.getLatest(tenant, entity)) {
                    is Result.Ok -> result.value
                    is Result.Err -> return Result.Err(result.error)
                }
            }

            // 버전 히스토리 조회
            val versions = getVersionHistory(tenant, entity)

            Result.Ok(
                RawDataResult(
                    tenantId = record.tenantId.value,
                    entityKey = record.entityKey.value,
                    version = record.version,
                    schemaId = record.schemaId,
                    schemaVersion = record.schemaVersion.toString(),
                    payload = parseJsonSafe(record.payload),
                    payloadRaw = record.payload,
                    payloadHash = record.payloadHash,
                    versions = versions
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get RawData: ${e.message}"))
        }
    }

    /**
     * RawData 버전 목록 조회 (DynamoDB via ExplorerRepositoryPort)
     */
    private suspend fun getVersionHistory(tenantId: TenantId, entityKey: EntityKey): List<VersionInfo> {
        return try {
            explorerRepo.getVersionHistory(tenantId, entityKey, 20).fold(
                { emptyList() },
                { items -> items.map { VersionInfo(version = it.version, createdAt = it.createdAt, hash = it.payloadHash) } }
            )
        } catch (e: Exception) {
            logger.warn("Failed to get version history", e)
            emptyList()
        }
    }

    // ==================== Slice 조회 ====================

    /**
     * 특정 엔티티의 Slice 목록 조회
     */
    suspend fun getSlices(
        tenantId: String,
        entityKey: String,
        version: Long? = null,
        sliceType: String? = null
    ): Result<SlicesResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return try {
            val slices = if (version != null) {
                when (val result = sliceRepo.getByVersion(tenant, entity, version)) {
                    is Result.Ok -> result.value
                    is Result.Err -> return Result.Err(result.error)
                }
            } else {
                when (val result = sliceRepo.getLatestVersion(tenant, entity)) {
                    is Result.Ok -> result.value
                    is Result.Err -> return Result.Err(result.error)
                }
            }

            // SliceType 필터링 (옵션)
            val filteredSlices = if (sliceType != null) {
                val targetType = SliceType.fromDbValueOrNull(sliceType)
                if (targetType != null) {
                    slices.filter { it.sliceType == targetType }
                } else {
                    slices
                }
            } else {
                slices
            }

            Result.Ok(
                SlicesResult(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = version ?: slices.firstOrNull()?.version ?: 0L,
                    slices = filteredSlices.map { it.toExplorerSliceItem() },
                    count = filteredSlices.size
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get Slices: ${e.message}"))
        }
    }

    /**
     * Slice Range Query (프리픽스 검색)
     */
    suspend fun searchSlices(
        tenantId: String,
        keyPrefix: String,
        sliceType: String? = null,
        limit: Int = 50,
        cursor: String? = null
    ): Result<SliceSearchResult> {
        val tenant = TenantId(tenantId)
        val type = sliceType?.let { SliceType.fromDbValueOrNull(it) }

        return try {
            when (val res = sliceRepo.findByKeyPrefix(tenant, keyPrefix, type, limit, cursor)) {
                is Result.Ok -> {
                    Result.Ok(
                        SliceSearchResult(
                            items = res.value.items.map { it.toExplorerSliceItem() },
                            nextCursor = res.value.nextCursor,
                            hasMore = res.value.hasMore
                        )
                    )
                }
                is Result.Err -> Result.Err(res.error)
            }
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to search Slices: ${e.message}"))
        }
    }

    /**
     * 슬라이스 타입별 전체 목록 조회 (DynamoDB via ExplorerRepositoryPort)
     */
    suspend fun listSlicesByType(
        tenantId: String,
        sliceType: String,
        limit: Int = 50,
        cursor: String? = null
    ): Result<SliceListByTypeResult> {
        val safeLimit = limit.coerceIn(1, 100)
        val tenant = TenantId(tenantId)
        val sliceTypeEnum = SliceType.fromDbValueOrNull(sliceType)
            ?: return Result.Err(DomainError.ValidationError("sliceType", "Invalid slice type: $sliceType"))

        return try {
            explorerRepo.listSlicesByType(tenant, sliceTypeEnum, safeLimit, cursor).fold(
                { err -> Result.Err(err) },
                { r ->
                    val entries = r.items.map { item ->
                        SliceListItem(
                            entityId = item.sourceKey,
                            sliceType = item.sliceType,
                            version = item.version,
                            data = null,
                            dataRaw = "{}",
                            hash = "",
                            ruleSetId = "",
                            ruleSetVersion = "",
                            isDeleted = false,
                            updatedAt = item.updatedAt
                        )
                    }
                    Result.Ok(
                        SliceListByTypeResult(
                            tenantId = tenantId,
                            sliceType = sliceType,
                            entries = entries,
                            total = r.totalCount ?: entries.size,
                            hasMore = r.nextCursor != null,
                            nextCursor = r.nextCursor
                        )
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Failed to list slices by type: ${e.message}", e)
            Result.Err(DomainError.StorageError("Failed to list slices by type: ${e.message}"))
        }
    }

    /**
     * 사용 가능한 슬라이스 타입 목록 조회 (DynamoDB via ExplorerRepositoryPort)
     */
    suspend fun getSliceTypes(tenantId: String): Result<SliceTypesResult> {
        val tenant = TenantId(tenantId)
        return try {
            explorerRepo.getSliceTypes(tenant).fold(
                { err -> Result.Err(err) },
                { typeInfos ->
                    Result.Ok(
                        SliceTypesResult(
                            tenantId = tenantId,
                            types = typeInfos.map { SliceTypeInfo(type = it.sliceType, count = it.count) },
                            total = typeInfos.size
                        )
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Failed to get slice types: ${e.message}", e)
            Result.Err(DomainError.StorageError("Failed to get slice types: ${e.message}"))
        }
    }

    // ==================== View 조회 ====================

    /**
     * View 조합 미리보기
     */
    suspend fun getView(
        tenantId: String,
        entityKey: String,
        viewDefId: String
    ): Result<ViewResult> {
        val workflow = queryViewWorkflow
            ?: return Result.Err(DomainError.ConfigError("QueryViewWorkflow not configured"))

        return try {
            // 최신 버전 조회
            val tenant = TenantId(tenantId)
            val entity = EntityKey(entityKey)

            val latestVersion = when (val result = rawDataRepo.getLatest(tenant, entity)) {
                is Result.Ok -> result.value.version
                is Result.Err -> return Result.Err(result.error)
            }

            when (val result = workflow.execute(tenant, viewDefId, entity, latestVersion)) {
                is Result.Ok -> {
                    Result.Ok(
                        ViewResult(
                            tenantId = tenantId,
                            entityKey = entityKey,
                            viewDefId = viewDefId,
                            data = parseJsonSafe(result.value.data),
                            dataRaw = result.value.data,
                            slicesUsed = result.value.meta?.usedContracts ?: emptyList(),
                            version = latestVersion,
                            assembledAt = Instant.now().toString()
                        )
                    )
                }
                is Result.Err -> Result.Err(result.error)
            }
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get View: ${e.message}"))
        }
    }

    // ==================== Lineage 그래프 ====================

    /**
     * 데이터 Lineage 조회 (RawData -> Slices -> View)
     */
    suspend fun getLineage(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): Result<LineageResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return try {
            // 1. RawData 조회
            val rawData = try {
                when (val result = if (version != null) {
                    rawDataRepo.get(tenant, entity, version)
                } else {
                    rawDataRepo.getLatest(tenant, entity)
                }) {
                    is Result.Ok -> result.value
                    is Result.Err -> null
                }
            } catch (_: Exception) {
                null
            }

            // 2. Slices 조회
            val slices = try {
                val v = version ?: rawData?.version ?: 0L
                when (val result = sliceRepo.getByVersion(tenant, entity, v)) {
                    is Result.Ok -> result.value
                    is Result.Err -> emptyList()
                }
            } catch (_: Exception) {
                emptyList<SliceRecord>()
            }

            // 3. 노드 구성
            val nodes = mutableListOf<LineageNode>()
            val edges = mutableListOf<LineageEdge>()

            // RawData 노드
            if (rawData != null) {
                nodes.add(
                    LineageNode(
                        id = "raw-${rawData.version}",
                        type = "rawdata",
                        label = "RawData v${rawData.version}",
                        metadata = mapOf(
                            "schemaId" to rawData.schemaId,
                            "schemaVersion" to rawData.schemaVersion.toString(),
                            "payloadHash" to rawData.payloadHash
                        )
                    )
                )
            }

            // Slice 노드 및 엣지
            slices.forEach { slice ->
                val sliceId = "slice-${slice.sliceType.name}"
                nodes.add(
                    LineageNode(
                        id = sliceId,
                        type = "slice",
                        label = slice.sliceType.name,
                        metadata = mapOf(
                            "ruleSetId" to slice.ruleSetId,
                            "ruleSetVersion" to slice.ruleSetVersion.toString(),
                            "hash" to slice.hash,
                            "isDeleted" to slice.isDeleted.toString()
                        )
                    )
                )

                // RawData -> Slice 엣지
                if (rawData != null) {
                    edges.add(
                        LineageEdge(
                            id = "edge-raw-${slice.sliceType.name}",
                            source = "raw-${rawData.version}",
                            target = sliceId,
                            label = slice.ruleSetId
                        )
                    )
                }
            }

            // ViewDef 정보 조회 및 노드 추가 (optional)
            val viewDefs = contractRegistry?.let {
                try {
                    when (val result = it.listViewDefinitions()) {
                        is Result.Ok -> result.value
                        is Result.Err -> emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            viewDefs.forEach { viewDef ->
                val viewId = "view-${viewDef.meta.id}"
                nodes.add(
                    LineageNode(
                        id = viewId,
                        type = "view",
                        label = viewDef.meta.id,
                        metadata = mapOf(
                            "version" to viewDef.meta.version.toString()
                        )
                    )
                )

                // Slice -> View 엣지 (ViewDef가 참조하는 Slice 타입)
                viewDef.requiredSlices.forEach { sliceType ->
                    val sliceNode = nodes.find { it.id == "slice-${sliceType.name}" }
                    if (sliceNode != null) {
                        edges.add(
                            LineageEdge(
                                id = "edge-${sliceType.name}-${viewDef.meta.id}",
                                source = "slice-${sliceType.name}",
                                target = viewId,
                                label = "compose"
                            )
                        )
                    }
                }
            }

            Result.Ok(
                LineageResult(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = version ?: rawData?.version ?: 0L,
                    nodes = nodes,
                    edges = edges
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get lineage: ${e.message}"))
        }
    }

    // ==================== 스마트 검색 ====================

    /**
     * 통합 검색 (엔티티 키 검색, DynamoDB via ExplorerRepositoryPort)
     */
    suspend fun search(
        tenantId: String,
        query: String,
        limit: Int = 20
    ): Result<SearchResult> {
        val safeLimit = limit.coerceIn(1, 100)
        val tenant = TenantId(tenantId)
        return try {
            explorerRepo.searchEntities(tenant, query, safeLimit).fold(
                { err -> Result.Err(err) },
                { r ->
                    Result.Ok(
                        SearchResult(
                            query = query,
                            items = r.items.map { SearchItem(entityKey = it.entityKey, tenantId = tenantId, type = it.type) },
                            count = r.items.size
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to search: ${e.message}"))
        }
    }

    /**
     * 자동완성 (빠른 프리픽스 매칭, DynamoDB via ExplorerRepositoryPort)
     */
    suspend fun autocomplete(
        tenantId: String,
        prefix: String,
        limit: Int = 10
    ): Result<List<String>> {
        val safeLimit = limit.coerceIn(1, 50)
        val tenant = TenantId(tenantId)
        return try {
            explorerRepo.autocomplete(tenant, prefix, safeLimit).fold(
                { err -> Result.Err(err) },
                { items -> Result.Ok(items.map { it.value }) }
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to autocomplete: ${e.message}"))
        }
    }

    /**
     * 버전 비교 (Diff)
     */
    suspend fun diffVersions(
        tenantId: String,
        entityKey: String,
        fromVersion: Long,
        toVersion: Long
    ): Result<VersionDiffResult> {
        val fromRaw = when (val result = getRawData(tenantId, entityKey, fromVersion)) {
            is Result.Ok -> result.value
            is Result.Err -> return result
        }

        val toRaw = when (val result = getRawData(tenantId, entityKey, toVersion)) {
            is Result.Ok -> result.value
            is Result.Err -> return result
        }

        return Result.Ok(
            VersionDiffResult(
                entityKey = entityKey,
                fromVersion = fromVersion,
                toVersion = toVersion,
                fromData = fromRaw.payload,
                toData = toRaw.payload,
                changes = computeJsonDiff(fromRaw.payloadRaw, toRaw.payloadRaw)
            )
        )
    }

    // ==================== Helper Functions ====================

    private fun parseJsonSafe(jsonStr: String): JsonElement? {
        return try {
            json.parseToJsonElement(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEntityType(entityKey: String): String {
        return entityKey.split(":").firstOrNull()?.uppercase() ?: "UNKNOWN"
    }

    private fun computeJsonDiff(from: String, to: String): List<DiffChange> {
        return try {
            val fromJson = json.parseToJsonElement(from).jsonObject
            val toJson = json.parseToJsonElement(to).jsonObject

            val changes = mutableListOf<DiffChange>()
            val allKeys = fromJson.keys + toJson.keys

            allKeys.forEach { key ->
                val fromVal = fromJson[key]?.toString()
                val toVal = toJson[key]?.toString()

                when {
                    fromVal == null && toVal != null ->
                        changes.add(DiffChange(key, "added", null, toVal))
                    fromVal != null && toVal == null ->
                        changes.add(DiffChange(key, "removed", fromVal, null))
                    fromVal != toVal ->
                        changes.add(DiffChange(key, "modified", fromVal, toVal))
                }
            }

            changes
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun SliceRecord.toExplorerSliceItem(): ExplorerSliceItem {
        return ExplorerSliceItem(
            sliceType = this.sliceType.name,
            data = parseJsonSafe(this.data),
            dataRaw = this.data,
            hash = this.hash,
            ruleSetId = this.ruleSetId,
            ruleSetVersion = this.ruleSetVersion.toString(),
            isDeleted = this.isDeleted
        )
    }

}

// ==================== Result DTOs ====================

@Serializable
data class IngestResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payloadHash: String,
    val compiled: Boolean,
    val slicesCreated: Int,
    val timestamp: String
)

@Serializable
data class IngestItem(
    val entityKey: String,
    val schemaId: String,
    val schemaVersion: String = "1.0.0",
    val payload: String,
    val compile: Boolean = false
)

@Serializable
data class IngestError(
    val entityKey: String,
    val error: String
)

@Serializable
data class BatchIngestResult(
    val tenantId: String,
    val succeeded: List<IngestResult>,
    val failed: List<IngestError>,
    val totalCount: Int,
    val successCount: Int,
    val failCount: Int
)

@Serializable
data class RawDataResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val schemaId: String,
    val schemaVersion: String,
    val payload: JsonElement?,
    val payloadRaw: String,
    val payloadHash: String,
    val versions: List<VersionInfo>
)

@Serializable
data class VersionInfo(
    val version: Long,
    val createdAt: String?,
    val hash: String
)

@Serializable
data class SlicesResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val slices: List<ExplorerSliceItem>,
    val count: Int
)

@Serializable
data class ExplorerSliceItem(
    val sliceType: String,
    val data: JsonElement?,
    val dataRaw: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val isDeleted: Boolean
)

@Serializable
data class SliceSearchResult(
    val items: List<ExplorerSliceItem>,
    val nextCursor: String?,
    val hasMore: Boolean
)

@Serializable
data class ViewResult(
    val tenantId: String,
    val entityKey: String,
    val viewDefId: String,
    val data: JsonElement?,
    val dataRaw: String,
    val slicesUsed: List<String>,
    val version: Long,
    val assembledAt: String
)

@Serializable
data class LineageResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val nodes: List<LineageNode>,
    val edges: List<LineageEdge>
)

@Serializable
data class LineageNode(
    val id: String,
    val type: String, // rawdata, slice, view
    val label: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class LineageEdge(
    val id: String,
    val source: String,
    val target: String,
    val label: String? = null
)

@Serializable
data class SearchResult(
    val query: String,
    val items: List<SearchItem>,
    val count: Int
)

@Serializable
data class SearchItem(
    val entityKey: String,
    val tenantId: String,
    val type: String
)

@Serializable
data class VersionDiffResult(
    val entityKey: String,
    val fromVersion: Long,
    val toVersion: Long,
    val fromData: JsonElement?,
    val toData: JsonElement?,
    val changes: List<DiffChange>
)

@Serializable
data class DiffChange(
    val path: String,
    val type: String, // added, removed, modified
    val oldValue: String?,
    val newValue: String?
)

@Serializable
data class RawDataListResult(
    val entries: List<RawDataListItem>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class RawDataListItem(
    val entityId: String,
    val version: Long,
    val schemaRef: String,
    val updatedAt: String?
)

@Serializable
data class SliceListByTypeResult(
    val tenantId: String,
    val sliceType: String,
    val entries: List<SliceListItem>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

@Serializable
data class SliceListItem(
    val entityId: String,
    val sliceType: String,
    val version: Long,
    val data: JsonElement?,
    val dataRaw: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val isDeleted: Boolean,
    val updatedAt: String?
)

@Serializable
data class SliceTypesResult(
    val tenantId: String,
    val types: List<SliceTypeInfo>,
    val total: Int
)

@Serializable
data class SliceTypeInfo(
    val type: String,
    val count: Int
)
