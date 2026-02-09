package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

/**
 * Contract Graph Service
 *
 * Contract Dependency Graph를 빌드하고 쿼리하는 서비스.
 * Phase 0의 핵심 구현체.
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 0
 */
class ContractGraphService {

    /**
     * 전체 Contract Dependency Graph 빌드
     */
    fun buildGraph(): Either<DomainError, ContractGraph> = either {
        val descriptors = loadAllDescriptors().bind()
        buildGraphFromDescriptors(descriptors)
    }

    /**
     * 특정 Contract의 Impact Graph (downstream)
     */
    fun getImpactGraph(contractId: String, depth: Int = 2): Either<DomainError, ContractGraph> = either {
        val fullGraph = buildGraph().bind()
        if (contractId !in fullGraph.nodes) {
            raise(DomainError.NotFoundError("Contract", contractId))
        }
        fullGraph.subgraphFrom(contractId, depth)
    }

    /**
     * 특정 Contract의 Dependency Graph (upstream)
     */
    fun getDependencyGraph(contractId: String, depth: Int = 2): Either<DomainError, ContractGraph> = either {
        val fullGraph = buildGraph().bind()
        if (contractId !in fullGraph.nodes) {
            raise(DomainError.NotFoundError("Contract", contractId))
        }
        fullGraph.reverseSubgraphTo(contractId, depth)
    }

    /**
     * 변경 영향 분석
     */
    fun analyzeImpact(contractId: String): Either<DomainError, ImpactAnalysis> = either {
        val fullGraph = buildGraph().bind()
        if (contractId !in fullGraph.nodes) {
            raise(DomainError.NotFoundError("Contract", contractId))
        }

        val affectedNodes = fullGraph.computeAffectedNodes(contractId, depth = 5)
        val affectedByKind = affectedNodes.mapNotNull { fullGraph.nodes[it] }
            .groupBy { it.kind }
            .mapValues { it.value.map { node -> node.id } }

        ImpactAnalysis(
            changedContractId = contractId,
            affectedCount = affectedNodes.size,
            affectedByKind = affectedByKind,
            affectedContracts = affectedNodes.toList()
        )
    }

    /**
     * 모든 ContractDescriptor 로드
     */
    fun loadAllDescriptors(): Either<DomainError, List<ContractDescriptor>> =
        try {
            Either.Right(loadDescriptorsInternal())
        } catch (e: Exception) {
            Either.Left(DomainError.StorageError("Failed to load contracts: ${e.message}"))
        }

    /**
     * 특정 Contract의 Descriptor 조회
     */
    fun getDescriptor(kind: ContractKind, id: String): Either<DomainError, ContractDescriptor> = either {
        val descriptors = loadAllDescriptors().bind()
        val descriptor = descriptors.find { it.kind == kind && it.id == id }
        descriptor ?: raise(DomainError.NotFoundError("Contract", "$kind/$id"))
    }

    // ==================== Private Helpers ====================

    private fun buildGraphFromDescriptors(descriptors: List<ContractDescriptor>): ContractGraph {
        val nodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // 1. 모든 Contract를 노드로 추가
        for (descriptor in descriptors) {
            val layer = when (descriptor.kind) {
                ContractKind.ENTITY_SCHEMA -> 0
                ContractKind.RULESET -> 1
                ContractKind.VIEW_DEFINITION -> 3
                ContractKind.SINK_RULE -> 4
                else -> 2
            }

            nodes[descriptor.id] = GraphNode(
                id = descriptor.id,
                kind = descriptor.kind,
                label = descriptor.semanticInfo.viewName
                    ?: descriptor.semanticInfo.entityType
                    ?: descriptor.id,
                entityType = descriptor.semanticInfo.entityType,
                layer = layer,
                metadata = mapOf(
                    "version" to descriptor.version.toString(),
                    "fieldsCount" to descriptor.semanticInfo.fields.size,
                    "slicesProduced" to descriptor.semanticInfo.slicesProduced,
                    "slicesRequired" to descriptor.semanticInfo.slicesRequired
                )
            )
        }

        // 2. Slice 가상 노드 추가 (RuleSet이 생산하는 Slice들)
        for (descriptor in descriptors.filter { it.kind == ContractKind.RULESET }) {
            val entityType = descriptor.semanticInfo.entityType ?: continue
            for (sliceType in descriptor.semanticInfo.slicesProduced) {
                val sliceId = "${entityType}_${sliceType}"
                if (sliceId !in nodes) {
                    nodes[sliceId] = GraphNode(
                        id = sliceId,
                        kind = ContractKind.RULESET, // Slice는 RuleSet의 산출물
                        label = sliceType,
                        entityType = entityType,
                        layer = 2,
                        metadata = mapOf("isVirtualSlice" to true)
                    )
                }
            }
        }

        // 3. 엣지 생성
        for (descriptor in descriptors) {
            when (descriptor.kind) {
                ContractKind.ENTITY_SCHEMA -> {
                    // Schema → RuleSet (같은 entityType)
                    val entityType = descriptor.semanticInfo.entityType ?: continue
                    descriptors
                        .filter { it.kind == ContractKind.RULESET && it.semanticInfo.entityType == entityType }
                        .forEach { ruleSet ->
                            edges.add(GraphEdge(
                                from = descriptor.id,
                                to = ruleSet.id,
                                kind = EdgeKind.DEFINES,
                                label = "defines"
                            ))
                        }
                }

                ContractKind.RULESET -> {
                    // RuleSet → Slices (생산)
                    val entityType = descriptor.semanticInfo.entityType ?: continue
                    for (sliceType in descriptor.semanticInfo.slicesProduced) {
                        val sliceId = "${entityType}_${sliceType}"
                        edges.add(GraphEdge(
                            from = descriptor.id,
                            to = sliceId,
                            kind = EdgeKind.PRODUCES,
                            label = "produces"
                        ))
                    }
                }

                ContractKind.VIEW_DEFINITION -> {
                    // Slices → View (필요)
                    val entityType = descriptor.semanticInfo.entityType ?: continue
                    for (sliceType in descriptor.semanticInfo.slicesRequired) {
                        val sliceId = "${entityType}_${sliceType}"
                        if (sliceId in nodes) {
                            edges.add(GraphEdge(
                                from = sliceId,
                                to = descriptor.id,
                                kind = EdgeKind.REQUIRES,
                                label = "requires"
                            ))
                        }
                    }
                }

                ContractKind.SINK_RULE -> {
                    // Slices → Sink (소비)
                    val sinkInfo = descriptor.semanticInfo.sinkInfo ?: continue
                    for (entityType in sinkInfo.inputEntityTypes) {
                        for (sliceType in sinkInfo.inputSliceTypes.ifEmpty { listOf("CORE") }) {
                            val sliceId = "${entityType}_${sliceType}"
                            if (sliceId in nodes) {
                                edges.add(GraphEdge(
                                    from = sliceId,
                                    to = descriptor.id,
                                    kind = EdgeKind.CONSUMES,
                                    label = "consumes"
                                ))
                            }
                        }
                    }
                }

                else -> {}
            }
        }

        return ContractGraph(
            nodes = nodes,
            edges = edges,
            metadata = GraphMetadata(
                totalNodes = nodes.size,
                totalEdges = edges.size,
                entityTypes = descriptors.mapNotNull { it.semanticInfo.entityType }.distinct().sorted(),
                lastUpdatedAt = Instant.now().toString()
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadDescriptorsInternal(): List<ContractDescriptor> {
        val descriptors = mutableListOf<ContractDescriptor>()
        val yaml = Yaml()
        val resourcePath = "/contracts/v1"

        try {
            val resourceUrl = javaClass.getResource(resourcePath) ?: return descriptors
            val uri = resourceUrl.toURI()

            val files = if (uri.scheme == "jar") {
                loadFileNamesFromJar(uri)
            } else {
                loadFileNamesFromFileSystem(uri)
            }

            for (fileName in files) {
                try {
                    val stream = javaClass.getResourceAsStream("$resourcePath/$fileName")
                    if (stream != null) {
                        val content = stream.bufferedReader().use { it.readText() }
                        val map = yaml.load<Map<String, Any?>>(content) as? Map<String, Any?> ?: continue
                        parseDescriptor(map, content, fileName)?.let { descriptors.add(it) }
                    }
                } catch (e: Exception) {
                    // Skip invalid files
                }
            }
        } catch (e: Exception) {
            // Return what we have
        }

        return descriptors
    }

    private fun loadFileNamesFromJar(uri: java.net.URI): List<String> {
        return listOf(
            "entity-product.v1.yaml",
            "entity-brand.v1.yaml",
            "entity-category.v1.yaml",
            "ruleset.v1.yaml",
            "ruleset-brand.v1.yaml",
            "ruleset-product-doc001.v1.yaml",
            "view-definition.v1.yaml",
            "view-product-core.v1.yaml",
            "view-product-detail.v1.yaml",
            "view-product-search.v1.yaml",
            "view-product-cart.v1.yaml",
            "view-brand-detail.v1.yaml",
            "sinkrule-opensearch-product.v1.yaml"
        )
    }

    private fun loadFileNamesFromFileSystem(uri: java.net.URI): List<String> {
        return try {
            val path = Paths.get(uri)
            if (Files.exists(path) && Files.isDirectory(path)) {
                Files.list(path)
                    .filter { it.toString().endsWith(".yaml") || it.toString().endsWith(".yml") }
                    .map { it.fileName.toString() }
                    .toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDescriptor(map: Map<String, Any?>, rawYaml: String, fileName: String): ContractDescriptor? {
        val kindStr = map["kind"]?.toString() ?: return null
        val kind = ContractKind.fromString(kindStr) ?: return null
        val id = map["id"]?.toString() ?: return null
        val versionStr = map["version"]?.toString() ?: "1.0.0"
        val version = SemVer.parse(versionStr) ?: SemVer(1, 0, 0)

        val semanticInfo = when (kind) {
            ContractKind.ENTITY_SCHEMA -> parseEntitySchemaSemantics(map)
            ContractKind.RULESET -> parseRuleSetSemantics(map)
            ContractKind.VIEW_DEFINITION -> parseViewDefinitionSemantics(map)
            ContractKind.SINK_RULE -> parseSinkRuleSemantics(map)
            else -> SemanticInfo()
        }

        return ContractDescriptor(
            kind = kind,
            id = id,
            version = version,
            filePath = fileName,
            rawYaml = rawYaml,
            parsed = map,
            semanticInfo = semanticInfo
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntitySchemaSemantics(map: Map<String, Any?>): SemanticInfo {
        val entityType = map["entityType"]?.toString()
        val fieldsRaw = map["fields"] as? List<Map<String, Any?>> ?: emptyList()

        val fields = fieldsRaw.map { fieldMap ->
            FieldInfo(
                name = fieldMap["name"]?.toString() ?: "",
                type = fieldMap["type"]?.toString() ?: "string",
                required = fieldMap["required"] as? Boolean ?: false,
                description = fieldMap["description"]?.toString(),
                path = fieldMap["path"]?.toString()
            )
        }

        return SemanticInfo(
            entityType = entityType,
            fields = fields
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRuleSetSemantics(map: Map<String, Any?>): SemanticInfo {
        val entityType = map["entityType"]?.toString()
        val slicesRaw = map["slices"] as? List<Map<String, Any?>> ?: emptyList()
        val slicesProduced = slicesRaw.mapNotNull { it["type"]?.toString() }

        return SemanticInfo(
            entityType = entityType,
            slicesProduced = slicesProduced
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseViewDefinitionSemantics(map: Map<String, Any?>): SemanticInfo {
        val entityType = map["entityType"]?.toString()
        val viewName = map["viewName"]?.toString()
        val requiredSlices = (map["requiredSlices"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        return SemanticInfo(
            entityType = entityType,
            viewName = viewName,
            slicesRequired = requiredSlices
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSinkRuleSemantics(map: Map<String, Any?>): SemanticInfo {
        val input = map["input"] as? Map<String, Any?>
        val target = map["target"] as? Map<String, Any?>

        val inputEntityTypes = (input?.get("entityTypes") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val inputSliceTypes = (input?.get("sliceTypes") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val targetType = target?.get("type")?.toString() ?: "UNKNOWN"

        return SemanticInfo(
            sinkInfo = SinkInfo(
                targetType = targetType,
                inputEntityTypes = inputEntityTypes,
                inputSliceTypes = inputSliceTypes
            )
        )
    }
}

/**
 * Impact Analysis 결과
 */
data class ImpactAnalysis(
    val changedContractId: String,
    val affectedCount: Int,
    val affectedByKind: Map<ContractKind, List<String>>,
    val affectedContracts: List<String>
)
