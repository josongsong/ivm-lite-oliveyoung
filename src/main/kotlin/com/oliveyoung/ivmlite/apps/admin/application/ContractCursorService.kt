package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.Mark

/**
 * Contract Cursor Service (Phase 1: DX Platform)
 *
 * 커서 위치 기반 의미론적 정보 제공.
 * Monaco Editor에서 커서가 이동할 때마다 해당 위치의 의미를 분석.
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 1
 */
class ContractCursorService(
    private val graphService: ContractGraphService
) {
    private val yaml = Yaml()

    /**
     * 커서 위치에 해당하는 Context 조회
     *
     * @param yamlContent YAML 원문
     * @param line 커서 라인 (1-based)
     * @param column 커서 컬럼 (1-based)
     */
    fun getCursorContext(
        yamlContent: String,
        line: Int,
        column: Int
    ): Either<DomainError, CursorContext> = either {
        val astPath = findAstPath(yamlContent, line, column)
        val semanticNode = buildSemanticNode(yamlContent, astPath)

        CursorContext(
            line = line,
            column = column,
            astPath = astPath,
            semanticNode = semanticNode
        )
    }

    /**
     * YAML 커서 위치에서 AST 경로 추출
     */
    @Suppress("UNCHECKED_CAST")
    private fun findAstPath(yamlContent: String, line: Int, column: Int): List<String> {
        val lines = yamlContent.lines()
        if (line < 1 || line > lines.size) return emptyList()

        val currentLine = lines[line - 1]
        val path = mutableListOf<String>()

        // 현재 라인의 들여쓰기 레벨 계산
        val currentIndent = currentLine.takeWhile { it == ' ' }.length

        // 현재 라인의 키 추출
        val keyMatch = Regex("""^\s*-?\s*(\w+)\s*:""").find(currentLine)
        val currentKey = keyMatch?.groupValues?.get(1)

        // 위로 올라가며 부모 키들 수집
        for (i in (line - 2) downTo 0) {
            val prevLine = lines[i]
            val prevIndent = prevLine.takeWhile { it == ' ' }.length

            if (prevIndent < currentIndent) {
                val prevKeyMatch = Regex("""^\s*-?\s*(\w+)\s*:""").find(prevLine)
                prevKeyMatch?.groupValues?.get(1)?.let { key ->
                    path.add(0, key)
                }
            }
        }

        // 현재 키 추가
        currentKey?.let { path.add(it) }

        // 배열 인덱스 처리
        if (currentLine.trimStart().startsWith("-")) {
            // 배열 요소인 경우 인덱스 계산
            var arrayIndex = 0
            for (i in (line - 2) downTo 0) {
                val prevLine = lines[i]
                val prevIndent = prevLine.takeWhile { it == ' ' }.length
                if (prevIndent < currentIndent && !prevLine.trimStart().startsWith("-")) {
                    break
                }
                if (prevIndent == currentIndent && prevLine.trimStart().startsWith("-")) {
                    arrayIndex++
                }
            }
            if (path.isNotEmpty()) {
                path.add(path.size - 1, arrayIndex.toString())
            }
        }

        return path
    }

    /**
     * AST 경로에서 SemanticNode 생성
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildSemanticNode(yamlContent: String, astPath: List<String>): SemanticNode? {
        if (astPath.isEmpty()) return null

        return try {
            val parsed = yaml.load<Map<String, Any?>>(yamlContent) as? Map<String, Any?> ?: return null
            val kind = ContractKind.fromString(parsed["kind"]?.toString() ?: "") ?: return null
            val contractId = parsed["id"]?.toString() ?: return null

            when {
                // fields 섹션
                astPath.contains("fields") -> buildFieldNode(parsed, astPath, contractId)

                // slices 섹션
                astPath.contains("slices") -> buildSliceNode(parsed, astPath, contractId)

                // requiredSlices / optionalSlices
                astPath.any { it in listOf("requiredSlices", "optionalSlices") } ->
                    buildViewSliceRefNode(parsed, astPath)

                // impactMap
                astPath.contains("impactMap") -> buildImpactMapNode(parsed, astPath)

                // 최상위 키
                astPath.size == 1 -> buildTopLevelNode(parsed, astPath.first(), kind)

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildFieldNode(
        parsed: Map<String, Any?>,
        astPath: List<String>,
        contractId: String
    ): SemanticNode? {
        val fields = parsed["fields"] as? List<Map<String, Any?>> ?: return null

        // 배열 인덱스 찾기
        val indexStr = astPath.find { it.all { c -> c.isDigit() } }
        val index = indexStr?.toIntOrNull() ?: return null

        if (index >= fields.size) return null
        val field = fields[index]

        val fieldName = field["name"]?.toString() ?: return null
        val fieldType = field["type"]?.toString() ?: "unknown"
        val required = field["required"] as? Boolean ?: false

        // 이 필드를 사용하는 곳 찾기
        val usedBy = findFieldUsages(fieldName, contractId)

        return SemanticNode(
            type = SemanticNodeType.FIELD,
            name = fieldName,
            dataType = fieldType,
            required = required,
            description = field["description"]?.toString(),
            usedBy = usedBy,
            impact = ImpactInfo(
                slicesAffected = findAffectedSlices(fieldName, parsed),
                regenRequired = true
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSliceNode(
        parsed: Map<String, Any?>,
        astPath: List<String>,
        contractId: String
    ): SemanticNode? {
        val slices = parsed["slices"] as? List<Map<String, Any?>> ?: return null

        // 배열 인덱스 찾기
        val indexStr = astPath.find { it.all { c -> c.isDigit() } }
        val index = indexStr?.toIntOrNull() ?: 0

        if (index >= slices.size) return null
        val slice = slices[index]

        val sliceType = slice["type"]?.toString() ?: return null

        // 이 Slice를 사용하는 View 찾기
        val usedByViews = findSliceUsages(sliceType)

        return SemanticNode(
            type = SemanticNodeType.SLICE,
            name = sliceType,
            dataType = null,
            required = null,
            description = "Slice type: $sliceType",
            usedBy = usedByViews,
            impact = ImpactInfo(
                slicesAffected = listOf(sliceType),
                regenRequired = true
            )
        )
    }

    private fun buildViewSliceRefNode(
        parsed: Map<String, Any?>,
        astPath: List<String>
    ): SemanticNode? {
        val isRequired = astPath.contains("requiredSlices")
        val sliceType = astPath.lastOrNull { !it.all { c -> c.isDigit() } && it !in listOf("requiredSlices", "optionalSlices") }
            ?: return null

        return SemanticNode(
            type = SemanticNodeType.SLICE_REF,
            name = sliceType,
            dataType = null,
            required = isRequired,
            description = if (isRequired) "Required slice" else "Optional slice",
            usedBy = UsageInfo(),
            impact = ImpactInfo(slicesAffected = listOf(sliceType), regenRequired = false)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildImpactMapNode(
        parsed: Map<String, Any?>,
        astPath: List<String>
    ): SemanticNode? {
        val impactMap = parsed["impactMap"] as? Map<String, List<String>> ?: return null

        // 현재 선택된 Slice 타입 찾기
        val sliceType = astPath.lastOrNull { impactMap.containsKey(it) } ?: return null
        val paths = impactMap[sliceType] ?: emptyList()

        return SemanticNode(
            type = SemanticNodeType.IMPACT_MAP,
            name = sliceType,
            dataType = null,
            required = null,
            description = "Impact paths: ${paths.joinToString(", ")}",
            usedBy = UsageInfo(),
            impact = ImpactInfo(slicesAffected = listOf(sliceType), regenRequired = true)
        )
    }

    private fun buildTopLevelNode(
        parsed: Map<String, Any?>,
        key: String,
        kind: ContractKind
    ): SemanticNode {
        val value = parsed[key]?.toString() ?: ""

        return SemanticNode(
            type = SemanticNodeType.PROPERTY,
            name = key,
            dataType = when (key) {
                "kind" -> "ContractKind"
                "id" -> "String"
                "version" -> "SemVer"
                "status" -> "ContractStatus"
                "entityType" -> "String"
                else -> null
            },
            required = key in listOf("kind", "id", "version"),
            description = "Contract $key: $value",
            usedBy = UsageInfo(),
            impact = ImpactInfo(slicesAffected = emptyList(), regenRequired = false)
        )
    }

    /**
     * 필드를 사용하는 곳 찾기 (Graph 기반)
     */
    private fun findFieldUsages(fieldName: String, contractId: String): UsageInfo {
        return try {
            val graph = graphService.buildGraph().getOrNull() ?: return UsageInfo()
            val affectedNodes = graph.computeAffectedNodes(contractId, depth = 3)

            val ruleSets = affectedNodes
                .mapNotNull { graph.nodes[it] }
                .filter { it.kind == ContractKind.RULESET }
                .map { DependencyRef(it.id, it.kind, RefRelation.USES) }

            val views = affectedNodes
                .mapNotNull { graph.nodes[it] }
                .filter { it.kind == ContractKind.VIEW_DEFINITION }
                .map { DependencyRef(it.id, it.kind, RefRelation.REQUIRES) }

            UsageInfo(ruleSets = ruleSets, views = views)
        } catch (e: Exception) {
            UsageInfo()
        }
    }

    /**
     * Slice를 사용하는 View 찾기
     */
    private fun findSliceUsages(sliceType: String): UsageInfo {
        return try {
            val descriptors = graphService.loadAllDescriptors().getOrNull() ?: return UsageInfo()

            val views = descriptors
                .filter { it.kind == ContractKind.VIEW_DEFINITION }
                .filter { it.semanticInfo.slicesRequired.contains(sliceType) }
                .map { DependencyRef(it.id, it.kind, RefRelation.REQUIRES) }

            UsageInfo(views = views)
        } catch (e: Exception) {
            UsageInfo()
        }
    }

    /**
     * 필드 변경 시 영향받는 Slice 찾기
     */
    @Suppress("UNCHECKED_CAST")
    private fun findAffectedSlices(fieldName: String, parsed: Map<String, Any?>): List<String> {
        val impactMap = parsed["impactMap"] as? Map<String, List<String>> ?: return emptyList()

        return impactMap.entries
            .filter { (_, paths) -> paths.any { it.contains(fieldName) } }
            .map { it.key }
    }
}

// ==================== Domain Models ====================

/**
 * 커서 컨텍스트
 */
data class CursorContext(
    val line: Int,
    val column: Int,
    val astPath: List<String>,
    val semanticNode: SemanticNode?
)

/**
 * 의미론적 노드
 */
data class SemanticNode(
    val type: SemanticNodeType,
    val name: String,
    val dataType: String?,
    val required: Boolean?,
    val description: String?,
    val usedBy: UsageInfo,
    val impact: ImpactInfo
)

/**
 * 노드 타입
 */
enum class SemanticNodeType {
    FIELD,
    SLICE,
    SLICE_REF,
    VIEW,
    RULE,
    IMPACT_MAP,
    PROPERTY
}

/**
 * 사용처 정보
 */
data class UsageInfo(
    val ruleSets: List<DependencyRef> = emptyList(),
    val views: List<DependencyRef> = emptyList(),
    val sinks: List<DependencyRef> = emptyList()
)

/**
 * 영향 정보
 */
data class ImpactInfo(
    val slicesAffected: List<String>,
    val regenRequired: Boolean
)
