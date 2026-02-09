package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import org.yaml.snakeyaml.Yaml
import java.time.Duration
import java.time.Instant

/**
 * SimulationService (Phase 5: Simulation)
 *
 * Contract 변경 시뮬레이션을 단계별로 수행하고 결과를 반환.
 * - Stage 1: RawData 파싱
 * - Stage 2: ChangeSet 생성
 * - Stage 3: Slice 생성
 * - Stage 4: View 조합
 */
class SimulationService {
    private val yaml = Yaml()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * 전체 파이프라인 시뮬레이션
     */
    suspend fun simulate(
        yamlContent: String,
        sampleData: String,
    ): Either<DomainError, PipelineSimulationResult> = either {
        val stages = mutableListOf<SimulationStage>()
        val errors = mutableListOf<SimulationError>()

        // Stage 1: RawData 파싱
        val stage1Start = Instant.now()
        val rawDataNode = try {
            mapper.readTree(sampleData)
        } catch (e: Exception) {
            errors.add(
                SimulationError(
                    stage = "RawData",
                    message = "JSON 파싱 실패: ${e.message}",
                    line = null
                )
            )
            stages.add(
                SimulationStage(
                    name = "RawData",
                    status = StageStatus.FAILED,
                    output = null,
                    duration = Duration.between(stage1Start, Instant.now())
                )
            )
            return@either PipelineSimulationResult(
                stages = stages,
                finalOutput = null,
                errors = errors
            )
        }

        stages.add(
            SimulationStage(
                name = "RawData",
                status = StageStatus.SUCCESS,
                output = rawDataNode,
                duration = Duration.between(stage1Start, Instant.now())
            )
        )

        // Stage 2: RuleSet 파싱 및 ChangeSet 생성
        val stage2Start = Instant.now()
        val ruleSet = try {
            parseRuleSet(yamlContent)
        } catch (e: Exception) {
            errors.add(
                SimulationError(
                    stage = "ChangeSet",
                    message = "RuleSet 파싱 실패: ${e.message}",
                    line = null
                )
            )
            stages.add(
                SimulationStage(
                    name = "ChangeSet",
                    status = StageStatus.FAILED,
                    output = null,
                    duration = Duration.between(stage2Start, Instant.now())
                )
            )
            return@either PipelineSimulationResult(
                stages = stages,
                finalOutput = null,
                errors = errors
            )
        }

        val changeSetNode = mapper.createObjectNode().apply {
            put("entityKey", rawDataNode.path("id").asText("unknown"))
            put("entityType", ruleSet.entityType)
            put("version", 1)
            put("slicesExpected", ruleSet.slices.size)
        }

        stages.add(
            SimulationStage(
                name = "ChangeSet",
                status = StageStatus.SUCCESS,
                output = changeSetNode,
                duration = Duration.between(stage2Start, Instant.now())
            )
        )

        // Stage 3: Slice 생성
        val stage3Start = Instant.now()
        val slicesNode = mapper.createObjectNode()
        var sliceFailures = 0

        for (sliceDef in ruleSet.slices) {
            try {
                val sliceData = applySliceRules(sampleData, sliceDef)
                val sliceJson = mapper.readTree(sliceData)
                slicesNode.set<JsonNode>(sliceDef.type.name, sliceJson)
            } catch (e: Exception) {
                sliceFailures++
                errors.add(
                    SimulationError(
                        stage = "Slices",
                        message = "Slice ${sliceDef.type.name} 생성 실패: ${e.message}",
                        line = null
                    )
                )
                // partial 결과 표시
                slicesNode.putNull(sliceDef.type.name)
            }
        }

        val sliceStatus = when {
            sliceFailures == ruleSet.slices.size -> StageStatus.FAILED
            sliceFailures > 0 -> StageStatus.PARTIAL
            else -> StageStatus.SUCCESS
        }

        stages.add(
            SimulationStage(
                name = "Slices",
                status = sliceStatus,
                output = slicesNode,
                duration = Duration.between(stage3Start, Instant.now())
            )
        )

        // Stage 4: View 조합 (간단한 병합)
        val stage4Start = Instant.now()
        val viewNode = mapper.createObjectNode()

        try {
            // 모든 슬라이스를 병합
            slicesNode.fields().forEach { (sliceType, sliceData) ->
                if (!sliceData.isNull) {
                    sliceData.fields().forEach { (fieldName, fieldValue) ->
                        viewNode.set<JsonNode>(fieldName, fieldValue)
                    }
                }
            }
            // 메타데이터 추가
            viewNode.put("_entityKey", rawDataNode.path("id").asText("unknown"))
            viewNode.put("_entityType", ruleSet.entityType)

            stages.add(
                SimulationStage(
                    name = "View",
                    status = if (sliceStatus == StageStatus.FAILED) StageStatus.FAILED else StageStatus.SUCCESS,
                    output = viewNode,
                    duration = Duration.between(stage4Start, Instant.now())
                )
            )
        } catch (e: Exception) {
            errors.add(
                SimulationError(
                    stage = "View",
                    message = "View 조합 실패: ${e.message}",
                    line = null
                )
            )
            stages.add(
                SimulationStage(
                    name = "View",
                    status = StageStatus.FAILED,
                    output = null,
                    duration = Duration.between(stage4Start, Instant.now())
                )
            )
        }

        PipelineSimulationResult(
            stages = stages,
            finalOutput = if (errors.isEmpty()) viewNode else null,
            errors = errors
        )
    }

    /**
     * 샘플 데이터 자동 생성
     */
    fun generateSample(kind: ContractKind, id: String): Either<DomainError, SampleData> = either {
        // 샘플 생성 (Contract 조회 없이 기본 샘플)
        val sample = mapper.createObjectNode()
        sample.put("id", "sample-${System.currentTimeMillis()}")
        sample.put("name", "Sample Item")
        sample.put("price", 10000)
        sample.put("category", "SAMPLE")
        sample.put("createdAt", Instant.now().toString())

        SampleData(
            contractId = id,
            contractKind = kind.name,
            data = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample),
            fields = listOf("id", "name", "price", "category", "createdAt")
        )
    }

    // ==================== Private Helpers ====================

    @Suppress("UNCHECKED_CAST")
    private fun parseRuleSet(yamlContent: String): SimulationRuleSet {
        val parsed = yaml.load<Map<String, Any?>>(yamlContent) as Map<String, Any?>

        val slices = (parsed["slices"] as? List<Map<String, Any?>>)?.map { sliceMap ->
            val typeStr = sliceMap["type"] as? String ?: "CORE"
            val buildRulesMap = sliceMap["buildRules"] as? Map<String, Any?>

            SimulationSliceDefinition(
                type = SliceType.valueOf(typeStr),
                passThrough = (buildRulesMap?.get("passThrough") as? List<String>) ?: emptyList(),
                fieldMappings = (buildRulesMap?.get("fieldMappings") as? Map<String, String>) ?: emptyMap(),
                computedFields = (buildRulesMap?.get("computedFields") as? Map<String, String>) ?: emptyMap(),
                condition = sliceMap["condition"] as? String
            )
        } ?: emptyList()

        return SimulationRuleSet(
            id = parsed["id"] as? String ?: "unknown",
            entityType = parsed["entityType"] as? String ?: "UNKNOWN",
            slices = slices
        )
    }

    private fun applySliceRules(payload: String, sliceDef: SimulationSliceDefinition): String {
        val sourceNode = mapper.readTree(payload)
        val resultNode = mapper.createObjectNode()

        // passThrough 필드 복사
        sliceDef.passThrough.forEach { fieldName: String ->
            sourceNode.get(fieldName)?.let { value ->
                resultNode.set<JsonNode>(fieldName, value)
            }
        }

        // fieldMappings 적용
        sliceDef.fieldMappings.forEach { (targetName: String, sourcePath: String) ->
            val value = resolveJsonPath(sourceNode, sourcePath)
            value?.let { resultNode.set<JsonNode>(targetName, it) }
        }

        // computedFields 적용 (단순 표현식만 지원)
        sliceDef.computedFields.forEach { (fieldName: String, expression: String) ->
            val computed = evaluateExpression(sourceNode, expression)
            if (computed != null) {
                resultNode.put(fieldName, computed)
            }
        }

        return mapper.writeValueAsString(resultNode)
    }

    private fun resolveJsonPath(node: JsonNode, path: String): JsonNode? {
        var current: JsonNode? = node
        for (part in path.split(".")) {
            current = current?.get(part)
            if (current == null) break
        }
        return current
    }

    private fun evaluateExpression(node: JsonNode, expression: String): String? {
        // 단순 필드 참조만 지원
        return if (expression.startsWith("$.")) {
            resolveJsonPath(node, expression.substring(2))?.asText()
        } else {
            expression
        }
    }
}

// ==================== DTOs ====================

data class PipelineSimulationResult(
    val stages: List<SimulationStage>,
    val finalOutput: JsonNode?,
    val errors: List<SimulationError>,
)

data class SimulationStage(
    val name: String,
    val status: StageStatus,
    val output: JsonNode?,
    val duration: Duration,
)

enum class StageStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
}

data class SimulationError(
    val stage: String,
    val message: String,
    val line: Int?,
)

data class SampleData(
    val contractId: String,
    val contractKind: String,
    val data: String,
    val fields: List<String>,
)

// Internal DTOs for parsing
data class SimulationRuleSet(
    val id: String,
    val entityType: String,
    val slices: List<SimulationSliceDefinition>,
)

data class SimulationSliceDefinition(
    val type: SliceType,
    val passThrough: List<String>,
    val fieldMappings: Map<String, String>,
    val computedFields: Map<String, String>,
    val condition: String?,
)
