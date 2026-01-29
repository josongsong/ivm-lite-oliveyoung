package com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters

import com.oliveyoung.ivmlite.pkg.workflow.canvas.domain.*
import com.oliveyoung.ivmlite.pkg.workflow.canvas.ports.WorkflowGraphBuilderPort
import org.yaml.snakeyaml.Yaml
import java.time.Instant

/**
 * 워크플로우 그래프 빌더
 *
 * Contract YAML 파일들을 분석하여 노드-엣지 그래프를 생성.
 * 자동 레이아웃 알고리즘을 사용하여 캔버스 좌표 계산.
 */
class WorkflowGraphBuilder : WorkflowGraphBuilderPort {

    companion object {
        // 레이아웃 상수
        private const val LAYER_GAP_Y = 150.0       // 레이어 간 세로 간격
        private const val NODE_GAP_X = 180.0        // 노드 간 가로 간격
        private const val ENTITY_GAP_X = 400.0      // 엔티티 그룹 간 가로 간격
        private const val CANVAS_PADDING = 50.0     // 캔버스 패딩
    }

    /**
     * Contract들로부터 워크플로우 그래프 빌드
     *
     * @param entityTypeFilter 필터링할 엔티티 타입 (null이면 전체)
     * @return 빌드된 워크플로우 그래프
     */
    override fun build(entityTypeFilter: String?): WorkflowGraph {
        val contracts = loadAllContracts()
        val nodes = mutableListOf<WorkflowNode>()
        val edges = mutableListOf<WorkflowEdge>()

        // Contract 종류별 분리
        val entitySchemas = contracts.filter { it.kind == "ENTITY_SCHEMA" }
            .filter { entityTypeFilter == null || it.entityType == entityTypeFilter }
        val ruleSets = contracts.filter { it.kind == "RULESET" }
        val viewDefinitions = contracts.filter { it.kind == "VIEW_DEFINITION" }
        val sinkRules = contracts.filter { it.kind == "SINKRULE" }

        // 엔티티별로 그래프 구성
        val entityTypes = mutableSetOf<String>()
        var entityIndex = 0

        // 중복 방지를 위한 생성된 노드/엣지 ID 추적
        val createdNodeIds = mutableSetOf<String>()
        val createdEdgeIds = mutableSetOf<String>()

        for (schema in entitySchemas) {
            val entityType = schema.entityType ?: continue
            entityTypes.add(entityType)
            val baseX = entityIndex * ENTITY_GAP_X + CANVAS_PADDING

            // Layer 0: RawData 노드
            val rawDataNode = createRawDataNode(schema, baseX, 0)
            nodes.add(rawDataNode)
            createdNodeIds.add(rawDataNode.id)

            // Layer 1: RuleSet 노드 (작은 규칙 노드)
            val ruleSet = ruleSets.find { it.entityType == entityType }
            val slices = ruleSet?.slices ?: emptyList()

            if (ruleSet != null) {
                val ruleSetNode = createRuleSetNode(ruleSet, baseX, 1)
                nodes.add(ruleSetNode)
                createdNodeIds.add(ruleSetNode.id)
                edges.add(WorkflowEdge.create(rawDataNode.id, ruleSetNode.id))

                // Layer 2: Slice 노드들
                slices.forEachIndexed { idx, sliceType ->
                    val sliceX = baseX + (idx - slices.size / 2.0) * NODE_GAP_X
                    val sliceNode = createSliceNode(entityType, sliceType, sliceX, 2)
                    nodes.add(sliceNode)
                    createdNodeIds.add(sliceNode.id)
                    edges.add(WorkflowEdge.animated(ruleSetNode.id, sliceNode.id))
                }
            }

            // Layer 3: ViewDefinition 노드 (작은 규칙 노드)
            // Layer 4: View 노드들
            val relatedViews = viewDefinitions.filter { it.entityType == entityType }

            if (relatedViews.isNotEmpty()) {
                // ViewDef 중간 노드 (여러 ViewDefinition을 하나의 규칙 노드로 대표)
                val viewDefNode = createViewDefNode(entityType, baseX, 3)
                nodes.add(viewDefNode)
                createdNodeIds.add(viewDefNode.id)

                // Slice → ViewDef 엣지
                slices.forEach { sliceType ->
                    val sliceNodeId = "${entityType}_${sliceType}"
                    edges.add(WorkflowEdge.create(sliceNodeId, viewDefNode.id))
                }

                // View 노드들
                relatedViews.forEachIndexed { idx, viewDef ->
                    val viewX = baseX + (idx - relatedViews.size / 2.0) * NODE_GAP_X
                    val viewNode = createViewNode(viewDef, viewX, 4)
                    nodes.add(viewNode)
                    createdNodeIds.add(viewNode.id)
                    edges.add(WorkflowEdge.create(viewDefNode.id, viewNode.id))
                }
            }

            // Layer 5: SinkRule 노드 (작은 규칙 노드)
            // Layer 6: Sink 노드
            val relatedSinks = sinkRules.filter { sink ->
                sink.inputEntityTypes?.contains(entityType) == true
            }

            if (relatedSinks.isNotEmpty()) {
                relatedSinks.forEachIndexed { idx, sinkRule ->
                    val sinkX = baseX + (idx - relatedSinks.size / 2.0) * NODE_GAP_X

                    // SinkRule 노드 (중복 방지)
                    val sinkRuleNodeId = "sinkrule_${sinkRule.id}"
                    if (sinkRuleNodeId !in createdNodeIds) {
                        val sinkRuleNode = createSinkRuleNode(sinkRule, sinkX, 5)
                        nodes.add(sinkRuleNode)
                        createdNodeIds.add(sinkRuleNodeId)

                        // Sink 노드 (중복 방지)
                        val sinkNodeId = "sink_${sinkRule.id}"
                        val sinkNode = createSinkNode(sinkRule, sinkX, 6)
                        nodes.add(sinkNode)
                        createdNodeIds.add(sinkNodeId)

                        val sinkEdge = WorkflowEdge.animated(sinkRuleNodeId, sinkNodeId)
                        edges.add(sinkEdge)
                        createdEdgeIds.add(sinkEdge.id)
                    }

                    // View → SinkRule 엣지 (모든 View에서 연결, 중복 방지)
                    relatedViews.forEach { viewDef ->
                        val viewNodeId = "view_${viewDef.viewName}"
                        val edgeId = "edge_${viewNodeId}_$sinkRuleNodeId"
                        if (edgeId !in createdEdgeIds) {
                            edges.add(WorkflowEdge.create(viewNodeId, sinkRuleNodeId))
                            createdEdgeIds.add(edgeId)
                        }
                    }
                }
            }

            entityIndex++
        }

        // 메타데이터 빌드
        val metadata = GraphMetadata(
            entityTypes = entityTypes.sorted(),
            totalNodes = nodes.size,
            totalEdges = edges.size,
            healthSummary = HealthSummary.fromNodes(nodes),
            lastUpdatedAt = Instant.now().toString()
        )

        return WorkflowGraph(nodes, edges, metadata)
    }

    // ==================== 노드 생성 메서드 ====================

    private fun createRawDataNode(schema: ContractInfo, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "rawdata_${schema.entityType}",
            type = NodeType.RAWDATA,
            label = schema.entityType ?: "Unknown",
            entityType = schema.entityType,
            contractId = schema.id,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf(
                "fieldCount" to (schema.fields?.size ?: 0),
                "version" to schema.version
            )
        )
    }

    private fun createRuleSetNode(ruleSet: ContractInfo, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "ruleset_${ruleSet.entityType}",
            type = NodeType.RULESET,
            label = "RuleSet",
            entityType = ruleSet.entityType,
            contractId = ruleSet.id,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf(
                "sliceCount" to (ruleSet.slices?.size ?: 0),
                "version" to ruleSet.version
            )
        )
    }

    private fun createSliceNode(entityType: String, sliceType: String, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "${entityType}_${sliceType}",
            type = NodeType.SLICE,
            label = sliceType,
            entityType = entityType,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING)
        )
    }

    private fun createViewDefNode(entityType: String, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "viewdef_${entityType}",
            type = NodeType.VIEW_DEF,
            label = "ViewDef",
            entityType = entityType,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING)
        )
    }

    private fun createViewNode(viewDef: ContractInfo, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "view_${viewDef.viewName}",
            type = NodeType.VIEW,
            label = viewDef.viewName ?: viewDef.id,
            entityType = viewDef.entityType,
            contractId = viewDef.id,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf(
                "requiredSlices" to (viewDef.requiredSlices ?: emptyList<String>()),
                "version" to viewDef.version
            )
        )
    }

    private fun createSinkRuleNode(sinkRule: ContractInfo, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "sinkrule_${sinkRule.id}",
            type = NodeType.SINK_RULE,
            label = "SinkRule",
            contractId = sinkRule.id,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING)
        )
    }

    private fun createSinkNode(sinkRule: ContractInfo, x: Double, layer: Int): WorkflowNode {
        val targetType = sinkRule.targetType ?: "Unknown"
        return WorkflowNode(
            id = "sink_${sinkRule.id}",
            type = NodeType.SINK,
            label = targetType,
            contractId = sinkRule.id,
            status = NodeStatus.HEALTHY,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf(
                "targetType" to targetType
            )
        )
    }

    // ==================== Contract 로딩 ====================

    /**
     * 모든 Contract YAML 파일 로드
     * (ContractRoutes.kt의 loadAllContracts()와 유사)
     */
    private fun loadAllContracts(): List<ContractInfo> {
        val contracts = mutableListOf<ContractInfo>()
        val yaml = Yaml()

        val contractFiles = listOf(
            "entity-product.v1.yaml",
            "entity-brand.v1.yaml",
            "entity-category.v1.yaml",
            "ruleset.v1.yaml",
            "ruleset-product-doc001.v1.yaml",
            "view-definition.v1.yaml",
            "view-product-core.v1.yaml",
            "view-product-detail.v1.yaml",
            "view-product-search.v1.yaml",
            "view-product-cart.v1.yaml",
            "view-brand-detail.v1.yaml",
            "sinkrule-opensearch-product.v1.yaml"
        )

        for (fileName in contractFiles) {
            try {
                val stream = javaClass.getResourceAsStream("/contracts/v1/$fileName")
                if (stream != null) {
                    val content = stream.bufferedReader().use { it.readText() }
                    @Suppress("UNCHECKED_CAST")
                    val map = yaml.load<Map<String, Any?>>(content) as? Map<String, Any?> ?: continue
                    contracts.add(ContractInfo.from(map))
                }
            } catch (e: Exception) {
                // Skip invalid files
            }
        }

        return contracts
    }
}

/**
 * Contract 정보 (YAML 파싱 결과)
 */
internal data class ContractInfo(
    val kind: String,
    val id: String,
    val version: String,
    val status: String,
    val entityType: String? = null,
    val viewName: String? = null,
    val slices: List<String>? = null,
    val requiredSlices: List<String>? = null,
    val inputEntityTypes: List<String>? = null,
    val targetType: String? = null,
    val fields: List<Any>? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(map: Map<String, Any?>): ContractInfo {
            val kind = map["kind"]?.toString() ?: ""
            val id = map["id"]?.toString() ?: ""
            val version = map["version"]?.toString() ?: "1.0.0"
            val status = map["status"]?.toString() ?: "ACTIVE"
            val entityType = map["entityType"]?.toString()
            val viewName = map["viewName"]?.toString()

            // slices from RULESET
            val slicesRaw = map["slices"] as? List<Map<String, Any?>>
            val slices = slicesRaw?.mapNotNull { it["type"]?.toString() }

            // requiredSlices from VIEW_DEFINITION
            val requiredSlices = (map["requiredSlices"] as? List<*>)?.mapNotNull { it?.toString() }

            // input.entityTypes from SINKRULE
            val input = map["input"] as? Map<String, Any?>
            val inputEntityTypes = (input?.get("entityTypes") as? List<*>)?.mapNotNull { it?.toString() }

            // target.type from SINKRULE
            val target = map["target"] as? Map<String, Any?>
            val targetType = target?.get("type")?.toString()

            // fields from ENTITY_SCHEMA
            val fields = map["fields"] as? List<Any>

            return ContractInfo(
                kind = kind,
                id = id,
                version = version,
                status = status,
                entityType = entityType,
                viewName = viewName,
                slices = slices,
                requiredSlices = requiredSlices,
                inputEntityTypes = inputEntityTypes,
                targetType = targetType,
                fields = fields
            )
        }
    }
}
