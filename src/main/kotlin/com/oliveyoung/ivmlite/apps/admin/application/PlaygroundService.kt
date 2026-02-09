package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.catch
import arrow.core.raise.either
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceBuildRules
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.MarkedYAMLException

// Same package - no import needed for AdminContractService, ContractKind

/**
 * Contract Playground Service
 *
 * YAML Contract를 실시간으로 편집/검증/시뮬레이션하는 서비스.
 * - validate: YAML 문법 + 스키마 검증
 * - simulate: 샘플 데이터로 슬라이싱 시뮬레이션
 * - diff: 현재 계약과 비교
 * - tryOnRealData: 실제 RawData로 드라이런
 */
class PlaygroundService(
    private val contractRegistry: ContractRegistryPort?,
    private val contractService: AdminContractService?,
    private val rawDataRepo: RawDataRepositoryPort?,
) {
    private val yaml = Yaml()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    // ==================== Public API ====================

    /**
     * YAML 문법 + 스키마 검증
     */
    fun validateYaml(yamlContent: String): Either<DomainError, ValidationResult> = either {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        // 1. YAML 문법 검증
        val parsed = try {
            @Suppress("UNCHECKED_CAST")
            yaml.load<Map<String, Any?>>(yamlContent) as? Map<String, Any?>
        } catch (e: MarkedYAMLException) {
            errors.add(
                ValidationError(
                    level = ValidationLevel.L0_SYNTAX,
                    line = e.problemMark?.line?.plus(1) ?: 0,
                    column = e.problemMark?.column?.plus(1) ?: 0,
                    message = e.problem ?: "YAML 파싱 오류",
                    fix = null
                )
            )
            null
        } catch (e: Exception) {
            errors.add(
                ValidationError(
                    level = ValidationLevel.L0_SYNTAX,
                    line = 1,
                    column = 1,
                    message = e.message ?: "알 수 없는 YAML 오류",
                    fix = null
                )
            )
            null
        }

        if (parsed == null) {
            return@either ValidationResult(
                valid = false,
                errors = errors,
                warnings = warnings
            )
        }

        // 2. 스키마 검증
        validateSchema(parsed, errors, warnings)

        ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * 샘플 데이터로 슬라이싱 시뮬레이션
     */
    suspend fun simulate(
        yamlContent: String,
        sampleData: String,
    ): Either<DomainError, SimulationResult> = either {
        // 1. YAML 검증
        val validationResult = validateYaml(yamlContent).bind()
        if (!validationResult.valid) {
            return@either SimulationResult(
                success = false,
                slices = emptyList(),
                errors = validationResult.errors.map { "${it.line}:${it.column} - ${it.message}" },
                rawDataPreview = null
            )
        }

        // 2. RuleSet 파싱
        val ruleSet = try {
            parseRuleSet(yamlContent)
        } catch (e: Exception) {
            throw DomainError.ValidationError("yaml", "RuleSet 파싱 실패: ${e.message}")
        }

        // 3. 샘플 데이터 검증
        val payload = try {
            mapper.readTree(sampleData)
            sampleData
        } catch (e: Exception) {
            throw DomainError.ValidationError("sampleData", "JSON 파싱 실패: ${e.message}")
        }

        // 4. 슬라이싱 시뮬레이션 (실제 엔진 없이 직접 수행)
        val simulatedSlices = mutableListOf<SimulatedSlice>()
        val simulationErrors = mutableListOf<String>()

        for (sliceDef in ruleSet.slices) {
            try {
                val sliceData = applySliceRules(payload, sliceDef)
                simulatedSlices.add(
                    SimulatedSlice(
                        type = sliceDef.type.name,
                        data = sliceData,
                        hash = computeHash(sliceData),
                        fields = extractFields(sliceData)
                    )
                )
            } catch (e: Exception) {
                simulationErrors.add("슬라이스 ${sliceDef.type.name} 생성 실패: ${e.message}")
            }
        }

        SimulationResult(
            success = simulationErrors.isEmpty(),
            slices = simulatedSlices,
            errors = simulationErrors,
            rawDataPreview = RawDataPreview(
                payload = payload,
                entityType = ruleSet.entityType,
                sliceCount = simulatedSlices.size
            )
        )
    }

    /**
     * 현재 계약과 변경사항 비교
     */
    fun diff(
        contractId: String,
        newYamlContent: String,
    ): Either<DomainError, DiffResult> = either {
        // 1. 기존 계약 조회
        val existingContract = contractService?.let { service ->
            when (val result = service.getById(ContractKind.RULESET, contractId)) {
                is Result.Ok -> result.value.content
                is Result.Err -> null
            }
        }

        if (existingContract == null) {
            return@either DiffResult(
                added = emptyList(),
                removed = emptyList(),
                modified = emptyList(),
                summary = "기존 계약을 찾을 수 없습니다. 새 계약으로 처리됩니다."
            )
        }

        // 2. YAML 비교
        @Suppress("UNCHECKED_CAST")
        val oldParsed = yaml.load<Map<String, Any?>>(existingContract) as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val newParsed = yaml.load<Map<String, Any?>>(newYamlContent) as? Map<String, Any?> ?: emptyMap()

        val added = mutableListOf<DiffItem>()
        val removed = mutableListOf<DiffItem>()
        val modified = mutableListOf<DiffItem>()

        compareObjects(oldParsed, newParsed, "", added, removed, modified)

        DiffResult(
            added = added,
            removed = removed,
            modified = modified,
            summary = buildDiffSummary(added, removed, modified)
        )
    }

    /**
     * 실제 데이터로 드라이런 테스트
     */
    suspend fun tryOnRealData(
        yamlContent: String,
        entityKey: String,
        tenantId: String = "oliveyoung",
    ): Either<DomainError, TryResult> {
        // 1. RawData 조회
        val repo = rawDataRepo
            ?: return DomainError.NotFoundError("RawDataRepository", "not configured").left()

        val rawData = when (val result = repo.getLatest(TenantId(tenantId), EntityKey(entityKey))) {
            is Result.Ok<RawDataRecord> -> result.value
            is Result.Err -> null
        }

        if (rawData == null) {
            return DomainError.NotFoundError("RawData", entityKey).left()
        }

        // 2. 시뮬레이션 실행
        return simulate(yamlContent, rawData.payload).map { simResult ->
            TryResult(
                success = simResult.success,
                entityKey = entityKey,
                version = rawData.version,
                slices = simResult.slices,
                errors = simResult.errors
            )
        }
    }

    // ==================== Private Helpers ====================

    /**
     * 스키마 검증
     */
    private fun validateSchema(
        parsed: Map<String, Any?>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
    ) {
        // 필수 필드 검증
        val kind = parsed["kind"]?.toString()
        if (kind == null) {
            errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "필수 필드 'kind' 누락", null))
        } else if (kind != "RULESET" && kind != "VIEW_DEFINITION" && kind != "ENTITY_SCHEMA" && kind != "SINK_RULE") {
            warnings.add("알 수 없는 kind: $kind (RULESET, VIEW_DEFINITION, ENTITY_SCHEMA, SINK_RULE 중 하나여야 함)")
        }

        val id = parsed["id"]?.toString()
        if (id == null) {
            errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "필수 필드 'id' 누락", null))
        }

        // RuleSet 특화 검증
        if (kind == "RULESET") {
            val entityType = parsed["entityType"]?.toString()
            if (entityType == null) {
                errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "RULESET에 필수 필드 'entityType' 누락", null))
            }

            @Suppress("UNCHECKED_CAST")
            val slices = parsed["slices"] as? List<Map<String, Any?>>
            if (slices == null || slices.isEmpty()) {
                errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "RULESET에 'slices' 정의 필요", null))
            } else {
                slices.forEachIndexed { index, slice ->
                    val sliceType = slice["type"]?.toString()
                    if (sliceType == null) {
                        errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "slices[$index]에 'type' 필드 누락", null))
                    }

                    val buildRules = slice["buildRules"] as? Map<*, *>
                    if (buildRules == null) {
                        errors.add(ValidationError(ValidationLevel.L1_SHAPE, 1, 1, "slices[$index]에 'buildRules' 필드 누락", null))
                    }
                }
            }
        }
    }

    /**
     * RuleSet YAML 파싱
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRuleSet(yamlContent: String): RuleSetContract {
        val parsed = yaml.load<Map<String, Any?>>(yamlContent) as Map<String, Any?>

        val id = parsed["id"]?.toString() ?: throw IllegalArgumentException("id 필수")
        val version = parsed["version"]?.toString() ?: "1.0.0"
        val entityType = parsed["entityType"]?.toString() ?: throw IllegalArgumentException("entityType 필수")

        val slicesList = parsed["slices"] as? List<Map<String, Any?>> ?: emptyList()
        val slices = slicesList.map { sliceMap ->
            val typeName = sliceMap["type"]?.toString() ?: throw IllegalArgumentException("slice type 필수")
            val buildRulesMap = sliceMap["buildRules"] as? Map<String, Any?> ?: emptyMap()

            val buildRules = when {
                buildRulesMap.containsKey("passThrough") -> {
                    val fields = buildRulesMap["passThrough"] as? List<String> ?: listOf("*")
                    SliceBuildRules.PassThrough(fields)
                }
                buildRulesMap.containsKey("mapFields") -> {
                    val mappings = buildRulesMap["mapFields"] as? Map<String, String> ?: emptyMap()
                    SliceBuildRules.MapFields(mappings)
                }
                else -> SliceBuildRules.PassThrough(listOf("*"))
            }

            SliceDefinition(
                type = SliceType.valueOf(typeName),
                buildRules = buildRules
            )
        }

        return RuleSetContract(
            meta = ContractMeta(
                kind = "RULESET",
                id = id,
                version = SemVer.parse(version),
                status = com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus.ACTIVE
            ),
            entityType = entityType,
            impactMap = emptyMap(),
            joins = emptyList(),
            slices = slices,
            indexes = emptyList()
        )
    }

    /**
     * 슬라이스 규칙 적용
     */
    private fun applySliceRules(payload: String, sliceDef: SliceDefinition): String {
        val source = mapper.readTree(payload)
        val target = mapper.createObjectNode()

        when (val rules = sliceDef.buildRules) {
            is SliceBuildRules.PassThrough -> {
                if (rules.fields.contains("*")) {
                    return payload
                }
                rules.fields.forEach { field ->
                    source.get(field)?.let { target.set<com.fasterxml.jackson.databind.JsonNode>(field, it) }
                }
            }
            is SliceBuildRules.MapFields -> {
                rules.mappings.forEach { (sourcePath, targetField) ->
                    extractJsonPath(source, sourcePath)?.let {
                        target.set<com.fasterxml.jackson.databind.JsonNode>(targetField, it)
                    }
                }
            }
        }

        return mapper.writeValueAsString(target)
    }

    /**
     * JSON path에서 값 추출
     */
    private fun extractJsonPath(
        source: com.fasterxml.jackson.databind.JsonNode,
        path: String,
    ): com.fasterxml.jackson.databind.JsonNode? {
        val parts = path.split(".")
        var current: com.fasterxml.jackson.databind.JsonNode? = source
        for (part in parts) {
            current = current?.get(part)
            if (current == null) break
        }
        return current
    }

    /**
     * 해시 계산
     */
    private fun computeHash(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * JSON에서 필드 목록 추출
     */
    private fun extractFields(jsonData: String): List<String> {
        return try {
            val node = mapper.readTree(jsonData)
            if (node.isObject) {
                node.fieldNames().asSequence().toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 객체 비교 (재귀)
     */
    private fun compareObjects(
        old: Map<String, Any?>,
        new: Map<String, Any?>,
        prefix: String,
        added: MutableList<DiffItem>,
        removed: MutableList<DiffItem>,
        modified: MutableList<DiffItem>,
    ) {
        val allKeys = old.keys + new.keys

        for (key in allKeys) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val oldValue = old[key]
            val newValue = new[key]

            when {
                oldValue == null && newValue != null -> {
                    added.add(DiffItem(path, null, newValue.toString()))
                }
                oldValue != null && newValue == null -> {
                    removed.add(DiffItem(path, oldValue.toString(), null))
                }
                oldValue != newValue -> {
                    @Suppress("UNCHECKED_CAST")
                    if (oldValue is Map<*, *> && newValue is Map<*, *>) {
                        compareObjects(
                            oldValue as Map<String, Any?>,
                            newValue as Map<String, Any?>,
                            path,
                            added,
                            removed,
                            modified
                        )
                    } else {
                        modified.add(DiffItem(path, oldValue?.toString(), newValue?.toString()))
                    }
                }
            }
        }
    }

    /**
     * Diff 요약 생성
     */
    private fun buildDiffSummary(
        added: List<DiffItem>,
        removed: List<DiffItem>,
        modified: List<DiffItem>,
    ): String {
        val parts = mutableListOf<String>()
        if (added.isNotEmpty()) parts.add("+${added.size} 추가")
        if (removed.isNotEmpty()) parts.add("-${removed.size} 삭제")
        if (modified.isNotEmpty()) parts.add("~${modified.size} 수정")
        return if (parts.isEmpty()) "변경사항 없음" else parts.joinToString(", ")
    }
}

// ==================== Domain Models ====================
// ValidationResult, ValidationError는 ContractValidationService.kt에서 정의됨

data class SimulationResult(
    val success: Boolean,
    val slices: List<SimulatedSlice>,
    val errors: List<String>,
    val rawDataPreview: RawDataPreview?,
)

data class SimulatedSlice(
    val type: String,
    val data: String,
    val hash: String,
    val fields: List<String>,
)

data class RawDataPreview(
    val payload: String,
    val entityType: String,
    val sliceCount: Int,
)

data class DiffResult(
    val added: List<DiffItem>,
    val removed: List<DiffItem>,
    val modified: List<DiffItem>,
    val summary: String,
)

data class DiffItem(
    val path: String,
    val oldValue: String?,
    val newValue: String?,
)

data class TryResult(
    val success: Boolean,
    val entityKey: String,
    val version: Long,
    val slices: List<SimulatedSlice>,
    val errors: List<String>,
)
