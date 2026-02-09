package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.slf4j.LoggerFactory

/**
 * Lineage 서비스
 *
 * P0: SRP 준수 - 데이터 흐름 그래프(Lineage) 기능만 담당
 * RawData → Slices → Views 관계를 그래프로 제공
 */
class LineageService(
    private val rawDataRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val contractRegistry: ContractRegistryPort?
) {
    private val logger = LoggerFactory.getLogger(LineageService::class.java)

    /**
     * 데이터 Lineage 조회 (RawData → Slices → View)
     */
    suspend fun getLineage(
        tenantId: String,
        entityKey: String,
        version: Long? = null
    ): Result<LineageResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return Result.catch {
            val rawData = fetchRawData(tenant, entity, version)
            val slices = fetchSlices(tenant, entity, version ?: rawData?.version ?: 0L)
            val viewDefs = fetchViewDefs()

            val nodes = buildNodes(rawData, slices, viewDefs)
            val edges = buildEdges(rawData, slices, viewDefs, nodes)

            LineageResult(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version ?: rawData?.version ?: 0L,
                nodes = nodes,
                edges = edges
            )
        }
    }

    private suspend fun fetchRawData(
        tenant: TenantId,
        entity: EntityKey,
        version: Long?
    ): RawDataInfo? {
        return try {
            val result = if (version != null) {
                rawDataRepo.get(tenant, entity, version)
            } else {
                rawDataRepo.getLatest(tenant, entity)
            }
            when (result) {
                is Result.Ok -> RawDataInfo(
                    version = result.value.version,
                    schemaId = result.value.schemaId,
                    schemaVersion = result.value.schemaVersion.toString(),
                    payloadHash = result.value.payloadHash
                )
                is Result.Err -> null
            }
        } catch (e: Exception) {
            logger.debug("RawData not found: ${e.message}")
            null
        }
    }

    private suspend fun fetchSlices(
        tenant: TenantId,
        entity: EntityKey,
        version: Long
    ): List<SliceInfo> {
        return try {
            when (val result = sliceRepo.getByVersion(tenant, entity, version)) {
                is Result.Ok -> result.value.map { record ->
                    SliceInfo(
                        sliceType = record.sliceType.name,
                        ruleSetId = record.ruleSetId,
                        ruleSetVersion = record.ruleSetVersion.toString(),
                        hash = record.hash,
                        isDeleted = record.isDeleted
                    )
                }
                is Result.Err -> emptyList()
            }
        } catch (e: Exception) {
            logger.debug("Slices not found: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchViewDefs(): List<ViewDefInfo> {
        if (contractRegistry == null) return emptyList()

        return try {
            when (val result = contractRegistry.listViewDefinitions()) {
                is Result.Ok -> result.value.map { viewDef ->
                    ViewDefInfo(
                        id = viewDef.meta.id,
                        version = viewDef.meta.version.toString(),
                        requiredSlices = viewDef.requiredSlices.map { it.name }
                    )
                }
                is Result.Err -> emptyList()
            }
        } catch (e: Exception) {
            logger.debug("ViewDefs not found: ${e.message}")
            emptyList()
        }
    }

    private fun buildNodes(
        rawData: RawDataInfo?,
        slices: List<SliceInfo>,
        viewDefs: List<ViewDefInfo>
    ): List<LineageNode> {
        val nodes = mutableListOf<LineageNode>()

        // RawData 노드
        if (rawData != null) {
            nodes.add(
                LineageNode(
                    id = "raw-${rawData.version}",
                    type = NODE_TYPE_RAWDATA,
                    label = "RawData v${rawData.version}",
                    metadata = mapOf(
                        "schemaId" to rawData.schemaId,
                        "schemaVersion" to rawData.schemaVersion,
                        "payloadHash" to rawData.payloadHash
                    )
                )
            )
        }

        // Slice 노드
        slices.forEach { slice ->
            nodes.add(
                LineageNode(
                    id = "slice-${slice.sliceType}",
                    type = NODE_TYPE_SLICE,
                    label = slice.sliceType,
                    metadata = mapOf(
                        "ruleSetId" to slice.ruleSetId,
                        "ruleSetVersion" to slice.ruleSetVersion,
                        "hash" to slice.hash,
                        "isDeleted" to slice.isDeleted.toString()
                    )
                )
            )
        }

        // View 노드
        viewDefs.forEach { viewDef ->
            nodes.add(
                LineageNode(
                    id = "view-${viewDef.id}",
                    type = NODE_TYPE_VIEW,
                    label = viewDef.id,
                    metadata = mapOf("version" to viewDef.version)
                )
            )
        }

        return nodes
    }

    private fun buildEdges(
        rawData: RawDataInfo?,
        slices: List<SliceInfo>,
        viewDefs: List<ViewDefInfo>,
        nodes: List<LineageNode>
    ): List<LineageEdge> {
        val edges = mutableListOf<LineageEdge>()

        // RawData → Slice 엣지
        if (rawData != null) {
            slices.forEach { slice ->
                edges.add(
                    LineageEdge(
                        id = "edge-raw-${slice.sliceType}",
                        source = "raw-${rawData.version}",
                        target = "slice-${slice.sliceType}",
                        label = slice.ruleSetId
                    )
                )
            }
        }

        // Slice → View 엣지
        viewDefs.forEach { viewDef ->
            viewDef.requiredSlices.forEach { sliceType ->
                val sliceNodeExists = nodes.any { it.id == "slice-$sliceType" }
                if (sliceNodeExists) {
                    edges.add(
                        LineageEdge(
                            id = "edge-$sliceType-${viewDef.id}",
                            source = "slice-$sliceType",
                            target = "view-${viewDef.id}",
                            label = EDGE_LABEL_COMPOSE
                        )
                    )
                }
            }
        }

        return edges
    }

    companion object {
        private const val NODE_TYPE_RAWDATA = "rawdata"
        private const val NODE_TYPE_SLICE = "slice"
        private const val NODE_TYPE_VIEW = "view"
        private const val EDGE_LABEL_COMPOSE = "compose"
    }

    // ==================== Internal DTOs ====================

    private data class RawDataInfo(
        val version: Long,
        val schemaId: String,
        val schemaVersion: String,
        val payloadHash: String
    )

    private data class SliceInfo(
        val sliceType: String,
        val ruleSetId: String,
        val ruleSetVersion: String,
        val hash: String,
        val isDeleted: Boolean
    )

    private data class ViewDefInfo(
        val id: String,
        val version: String,
        val requiredSlices: List<String>
    )
}
