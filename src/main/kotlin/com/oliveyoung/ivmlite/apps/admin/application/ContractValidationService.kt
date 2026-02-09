package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.MarkedYAMLException
import org.yaml.snakeyaml.error.YAMLException

/**
 * Contract Validation Service (Phase 1: DX Platform)
 *
 * 다단계 Contract 검증 서비스.
 * L0: Syntax (YAML 파싱)
 * L1: Shape (필수 필드)
 * L2: Semantic (비즈니스 규칙)
 * L3: Cross-Ref (참조 무결성)
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 1
 */
class ContractValidationService(
    private val graphService: ContractGraphService
) {
    private val yaml = Yaml()

    /**
     * 다단계 검증 수행
     */
    fun validate(yamlContent: String): Either<DomainError, ValidationResult> = either {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        // L0: Syntax
        val syntaxErrors = validateL0Syntax(yamlContent)
        errors.addAll(syntaxErrors)

        // L0 실패 시 조기 종료
        if (syntaxErrors.isNotEmpty()) {
            return@either ValidationResult(
                valid = false,
                errors = errors,
                warnings = warnings
            )
        }

        // 파싱 성공 시 추가 검증
        @Suppress("UNCHECKED_CAST")
        val parsed = yaml.load<Map<String, Any?>>(yamlContent) as Map<String, Any?>

        // L1: Shape
        errors.addAll(validateL1Shape(parsed, yamlContent))

        // L2: Semantic
        errors.addAll(validateL2Semantic(parsed, yamlContent))

        // L3: Cross-Ref
        errors.addAll(validateL3CrossRef(parsed, yamlContent))

        ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * L0: YAML Syntax 검증
     */
    private fun validateL0Syntax(yamlContent: String): List<ValidationError> {
        return try {
            yaml.load<Any>(yamlContent)
            emptyList()
        } catch (e: YAMLException) {
            // YAMLException message에서 line/column 추출
            val markMatch = Regex("""line (\d+), column (\d+)""").find(e.message ?: "")
            val mark = markMatch?.let {
                Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            } ?: Pair(1, 1)

            listOf(ValidationError(
                level = ValidationLevel.L0_SYNTAX,
                line = mark.first,
                column = mark.second,
                message = "YAML syntax error: ${e.message}",
                fix = null
            ))
        } catch (e: Exception) {
            listOf(ValidationError(
                level = ValidationLevel.L0_SYNTAX,
                line = 1,
                column = 1,
                message = "Parse error: ${e.message}",
                fix = null
            ))
        }
    }

    /**
     * L1: Shape 검증 (필수 필드)
     */
    private fun validateL1Shape(parsed: Map<String, Any?>, yamlContent: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 공통 필수 필드
        val requiredFields = listOf("kind", "id", "version")
        for (field in requiredFields) {
            if (!parsed.containsKey(field) || parsed[field] == null) {
                errors.add(ValidationError(
                    level = ValidationLevel.L1_SHAPE,
                    line = 1,
                    column = 1,
                    message = "Required field '$field' is missing",
                    fix = QuickFix(
                        description = "Add '$field' field",
                        replacement = "$field: "
                    )
                ))
            }
        }

        // Kind별 추가 필수 필드
        val kind = ContractKind.fromString(parsed["kind"]?.toString() ?: "")
        when (kind) {
            ContractKind.ENTITY_SCHEMA -> {
                if (!parsed.containsKey("entityType")) {
                    errors.add(createMissingFieldError("entityType", yamlContent))
                }
                if (!parsed.containsKey("fields")) {
                    errors.add(createMissingFieldError("fields", yamlContent))
                }
            }
            ContractKind.RULESET -> {
                if (!parsed.containsKey("entityType")) {
                    errors.add(createMissingFieldError("entityType", yamlContent))
                }
                if (!parsed.containsKey("slices")) {
                    errors.add(createMissingFieldError("slices", yamlContent))
                }
            }
            ContractKind.VIEW_DEFINITION -> {
                if (!parsed.containsKey("requiredSlices") && !parsed.containsKey("optionalSlices")) {
                    errors.add(ValidationError(
                        level = ValidationLevel.L1_SHAPE,
                        line = 1,
                        column = 1,
                        message = "VIEW_DEFINITION must have 'requiredSlices' or 'optionalSlices'",
                        fix = QuickFix("Add requiredSlices", "requiredSlices: []")
                    ))
                }
            }
            else -> {}
        }

        return errors
    }

    /**
     * L2: Semantic 검증 (비즈니스 규칙)
     */
    @Suppress("UNCHECKED_CAST")
    private fun validateL2Semantic(parsed: Map<String, Any?>, yamlContent: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val kind = ContractKind.fromString(parsed["kind"]?.toString() ?: "") ?: return errors

        when (kind) {
            ContractKind.ENTITY_SCHEMA -> {
                val fields = parsed["fields"] as? List<Map<String, Any?>> ?: return errors

                // 필드 이름 중복 검사
                val fieldNames = fields.mapNotNull { it["name"]?.toString() }
                val duplicates = fieldNames.groupingBy { it }.eachCount().filter { it.value > 1 }
                for ((name, count) in duplicates) {
                    errors.add(ValidationError(
                        level = ValidationLevel.L2_SEMANTIC,
                        line = findLineNumber(yamlContent, "name: $name"),
                        column = 1,
                        message = "Duplicate field name: '$name' (appears $count times)",
                        fix = null
                    ))
                }

                // 필드 타입 검증
                val validTypes = setOf("string", "int", "long", "double", "boolean", "array", "object", "datetime")
                for (field in fields) {
                    val fieldType = field["type"]?.toString()?.lowercase()
                    if (fieldType != null && fieldType !in validTypes) {
                        errors.add(ValidationError(
                            level = ValidationLevel.L2_SEMANTIC,
                            line = findLineNumber(yamlContent, "type: $fieldType"),
                            column = 1,
                            message = "Unknown field type: '$fieldType'. Valid types: ${validTypes.joinToString()}",
                            fix = QuickFix("Change to 'string'", "type: string")
                        ))
                    }
                }
            }

            ContractKind.RULESET -> {
                val slices = parsed["slices"] as? List<Map<String, Any?>> ?: return errors

                // 최소 1개 slice 필요
                if (slices.isEmpty()) {
                    errors.add(ValidationError(
                        level = ValidationLevel.L2_SEMANTIC,
                        line = findLineNumber(yamlContent, "slices:"),
                        column = 1,
                        message = "RuleSet must have at least one slice",
                        fix = null
                    ))
                }

                // Slice 타입 중복 검사
                val sliceTypes = slices.mapNotNull { it["type"]?.toString() }
                val duplicates = sliceTypes.groupingBy { it }.eachCount().filter { it.value > 1 }
                for ((type, count) in duplicates) {
                    errors.add(ValidationError(
                        level = ValidationLevel.L2_SEMANTIC,
                        line = findLineNumber(yamlContent, "type: $type"),
                        column = 1,
                        message = "Duplicate slice type: '$type' (appears $count times)",
                        fix = null
                    ))
                }
            }

            ContractKind.VIEW_DEFINITION -> {
                val required = parsed["requiredSlices"] as? List<*> ?: emptyList<Any>()
                val optional = parsed["optionalSlices"] as? List<*> ?: emptyList<Any>()

                // required와 optional 중복 검사
                val overlap = required.intersect(optional.toSet())
                if (overlap.isNotEmpty()) {
                    errors.add(ValidationError(
                        level = ValidationLevel.L2_SEMANTIC,
                        line = findLineNumber(yamlContent, "requiredSlices:"),
                        column = 1,
                        message = "Slices cannot be both required and optional: ${overlap.joinToString()}",
                        fix = null
                    ))
                }
            }

            else -> {}
        }

        return errors
    }

    /**
     * L3: Cross-Reference 검증 (참조 무결성)
     */
    @Suppress("UNCHECKED_CAST")
    private fun validateL3CrossRef(parsed: Map<String, Any?>, yamlContent: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val kind = ContractKind.fromString(parsed["kind"]?.toString() ?: "") ?: return errors

        // 모든 계약 로드
        val allDescriptors = graphService.loadAllDescriptors().getOrNull() ?: return errors

        when (kind) {
            ContractKind.VIEW_DEFINITION -> {
                val entityType = parsed["entityType"]?.toString()
                val requiredSlices = parsed["requiredSlices"] as? List<String> ?: emptyList()

                // 해당 EntityType의 RuleSet에서 정의된 Slice 찾기
                val availableSlices = allDescriptors
                    .filter { it.kind == ContractKind.RULESET && it.semanticInfo.entityType == entityType }
                    .flatMap { it.semanticInfo.slicesProduced }
                    .toSet()

                // 존재하지 않는 Slice 참조 검사
                for (sliceType in requiredSlices) {
                    if (sliceType !in availableSlices && availableSlices.isNotEmpty()) {
                        errors.add(ValidationError(
                            level = ValidationLevel.L3_CROSS_REF,
                            line = findLineNumber(yamlContent, sliceType),
                            column = 1,
                            message = "Slice '$sliceType' is not defined in any RuleSet for entityType '$entityType'. Available: ${availableSlices.joinToString()}",
                            fix = null
                        ))
                    }
                }
            }

            ContractKind.RULESET -> {
                val slices = parsed["slices"] as? List<Map<String, Any?>> ?: return errors

                for (slice in slices) {
                    val joins = slice["joins"] as? List<Map<String, Any?>> ?: continue

                    for (join in joins) {
                        val targetEntityType = join["targetEntityType"]?.toString() ?: continue

                        // 대상 EntityType이 존재하는지 확인
                        val exists = allDescriptors.any {
                            it.kind == ContractKind.ENTITY_SCHEMA && it.semanticInfo.entityType == targetEntityType
                        }

                        if (!exists) {
                            errors.add(ValidationError(
                                level = ValidationLevel.L3_CROSS_REF,
                                line = findLineNumber(yamlContent, "targetEntityType: $targetEntityType"),
                                column = 1,
                                message = "Referenced entityType '$targetEntityType' not found",
                                fix = null
                            ))
                        }
                    }
                }
            }

            else -> {}
        }

        return errors
    }

    // ==================== Helpers ====================

    private fun createMissingFieldError(field: String, yamlContent: String): ValidationError {
        return ValidationError(
            level = ValidationLevel.L1_SHAPE,
            line = 1,
            column = 1,
            message = "Required field '$field' is missing",
            fix = QuickFix("Add '$field' field", "$field: ")
        )
    }

    private fun findLineNumber(yamlContent: String, searchText: String): Int {
        val lines = yamlContent.lines()
        for ((index, line) in lines.withIndex()) {
            if (line.contains(searchText)) {
                return index + 1
            }
        }
        return 1
    }
}

// ==================== Domain Models ====================

/**
 * 검증 결과
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<String>
)

/**
 * 검증 에러
 */
data class ValidationError(
    val level: ValidationLevel,
    val line: Int,
    val column: Int,
    val message: String,
    val fix: QuickFix?
)

/**
 * 검증 레벨
 */
enum class ValidationLevel {
    L0_SYNTAX,     // YAML 파싱 에러
    L1_SHAPE,      // 필수 필드 누락
    L2_SEMANTIC,   // 비즈니스 규칙 위반
    L3_CROSS_REF,  // 참조 계약 미존재
    L4_RUNTIME     // 런타임 시뮬레이션 실패
}

/**
 * Quick Fix 제안
 */
data class QuickFix(
    val description: String,
    val replacement: String
)
