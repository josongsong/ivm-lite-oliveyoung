package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.yaml.snakeyaml.Yaml

/**
 * Semantic Diff Service (Phase 2: DX Platform)
 *
 * Contract 변경 사항의 의미론적 분석.
 * 단순 텍스트 diff가 아닌 Contract 구조 기반 분석.
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 2
 */
class SemanticDiffService(
    private val graphService: ContractGraphService
) {
    private val yaml = Yaml()

    /**
     * 의미론적 Diff 계산
     *
     * @param before 변경 전 YAML
     * @param after 변경 후 YAML
     * @return 의미론적 변경 분석 결과
     */
    @Suppress("UNCHECKED_CAST")
    fun computeDiff(before: String, after: String): Either<DomainError, SemanticDiffResult> = either {
        val beforeParsed = try {
            yaml.load<Map<String, Any?>>(before) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            raise(DomainError.ValidationError("before", "YAML 파싱 실패: ${e.message}"))
        }

        val afterParsed = try {
            yaml.load<Map<String, Any?>>(after) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            raise(DomainError.ValidationError("after", "YAML 파싱 실패: ${e.message}"))
        }

        val kind = ContractKind.fromString(afterParsed["kind"]?.toString() ?: beforeParsed["kind"]?.toString() ?: "")

        val changes = mutableListOf<SemanticChange>()

        // Top-level 속성 변경 감지
        changes.addAll(detectTopLevelChanges(beforeParsed, afterParsed))

        // Kind별 변경 감지
        when (kind) {
            ContractKind.ENTITY_SCHEMA -> {
                changes.addAll(detectFieldChanges(beforeParsed, afterParsed))
            }
            ContractKind.RULESET -> {
                changes.addAll(detectSliceChanges(beforeParsed, afterParsed))
                changes.addAll(detectImpactMapChanges(beforeParsed, afterParsed))
            }
            ContractKind.VIEW_DEFINITION -> {
                changes.addAll(detectViewSliceChanges(beforeParsed, afterParsed))
            }
            else -> {}
        }

        // Breaking 여부 판정
        val hasBreaking = changes.any { it.breaking }

        // 영향받는 Slice 계산
        val affectedSlices = calculateAffectedSlices(changes, beforeParsed, afterParsed)

        // 영향받는 View 계산
        val affectedViews = calculateAffectedViews(affectedSlices)

        SemanticDiffResult(
            changes = changes,
            breaking = hasBreaking,
            regenRequired = changes.any { it.type in listOf(
                ChangeType.FIELD_REMOVED,
                ChangeType.FIELD_TYPE_CHANGED,
                ChangeType.SLICE_REMOVED,
                ChangeType.RULE_CHANGED
            ) },
            affectedSlices = affectedSlices,
            affectedViews = affectedViews
        )
    }

    // ==================== Top-level Changes ====================

    private fun detectTopLevelChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<SemanticChange> {
        val changes = mutableListOf<SemanticChange>()
        val topLevelKeys = setOf("kind", "id", "version", "status", "entityType")

        for (key in topLevelKeys) {
            val beforeVal = before[key]?.toString()
            val afterVal = after[key]?.toString()

            if (beforeVal != afterVal) {
                if (beforeVal == null && afterVal != null) {
                    changes.add(SemanticChange(
                        type = ChangeType.PROPERTY_ADDED,
                        target = key,
                        before = null,
                        after = afterVal,
                        breaking = key == "entityType"
                    ))
                } else if (beforeVal != null && afterVal == null) {
                    changes.add(SemanticChange(
                        type = ChangeType.PROPERTY_REMOVED,
                        target = key,
                        before = beforeVal,
                        after = null,
                        breaking = key in setOf("kind", "id", "entityType")
                    ))
                } else {
                    changes.add(SemanticChange(
                        type = ChangeType.PROPERTY_CHANGED,
                        target = key,
                        before = beforeVal,
                        after = afterVal,
                        breaking = key == "entityType"
                    ))
                }
            }
        }

        return changes
    }

    // ==================== Field Changes (ENTITY_SCHEMA) ====================

    @Suppress("UNCHECKED_CAST")
    private fun detectFieldChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<SemanticChange> {
        val changes = mutableListOf<SemanticChange>()

        val beforeFields = (before["fields"] as? List<Map<String, Any?>>)
            ?.associateBy { it["name"]?.toString() ?: "" } ?: emptyMap()
        val afterFields = (after["fields"] as? List<Map<String, Any?>>)
            ?.associateBy { it["name"]?.toString() ?: "" } ?: emptyMap()

        val allFieldNames = beforeFields.keys + afterFields.keys

        for (name in allFieldNames) {
            val beforeField = beforeFields[name]
            val afterField = afterFields[name]

            when {
                beforeField == null && afterField != null -> {
                    // 필드 추가
                    changes.add(SemanticChange(
                        type = ChangeType.FIELD_ADDED,
                        target = "fields.$name",
                        before = null,
                        after = afterField["type"]?.toString(),
                        breaking = false
                    ))
                }
                beforeField != null && afterField == null -> {
                    // 필드 제거 (Breaking!)
                    changes.add(SemanticChange(
                        type = ChangeType.FIELD_REMOVED,
                        target = "fields.$name",
                        before = beforeField["type"]?.toString(),
                        after = null,
                        breaking = true
                    ))
                }
                beforeField != null && afterField != null -> {
                    // 타입 변경
                    val beforeType = beforeField["type"]?.toString()
                    val afterType = afterField["type"]?.toString()
                    if (beforeType != afterType) {
                        changes.add(SemanticChange(
                            type = ChangeType.FIELD_TYPE_CHANGED,
                            target = "fields.$name",
                            before = beforeType,
                            after = afterType,
                            breaking = true
                        ))
                    }

                    // Required 변경
                    val beforeRequired = beforeField["required"] as? Boolean ?: false
                    val afterRequired = afterField["required"] as? Boolean ?: false
                    if (beforeRequired != afterRequired) {
                        changes.add(SemanticChange(
                            type = ChangeType.FIELD_REQUIRED_CHANGED,
                            target = "fields.$name",
                            before = beforeRequired.toString(),
                            after = afterRequired.toString(),
                            breaking = !beforeRequired && afterRequired // optional → required는 Breaking
                        ))
                    }
                }
            }
        }

        return changes
    }

    // ==================== Slice Changes (RULESET) ====================

    @Suppress("UNCHECKED_CAST")
    private fun detectSliceChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<SemanticChange> {
        val changes = mutableListOf<SemanticChange>()

        val beforeSlices = (before["slices"] as? List<Map<String, Any?>>)
            ?.associateBy { it["type"]?.toString() ?: "" } ?: emptyMap()
        val afterSlices = (after["slices"] as? List<Map<String, Any?>>)
            ?.associateBy { it["type"]?.toString() ?: "" } ?: emptyMap()

        val allSliceTypes = beforeSlices.keys + afterSlices.keys

        for (sliceType in allSliceTypes) {
            val beforeSlice = beforeSlices[sliceType]
            val afterSlice = afterSlices[sliceType]

            when {
                beforeSlice == null && afterSlice != null -> {
                    changes.add(SemanticChange(
                        type = ChangeType.SLICE_ADDED,
                        target = "slices.$sliceType",
                        before = null,
                        after = sliceType,
                        breaking = false
                    ))
                }
                beforeSlice != null && afterSlice == null -> {
                    changes.add(SemanticChange(
                        type = ChangeType.SLICE_REMOVED,
                        target = "slices.$sliceType",
                        before = sliceType,
                        after = null,
                        breaking = true
                    ))
                }
                beforeSlice != null && afterSlice != null -> {
                    // buildRules 비교
                    val beforeRules = beforeSlice["buildRules"]?.toString()
                    val afterRules = afterSlice["buildRules"]?.toString()
                    if (beforeRules != afterRules) {
                        changes.add(SemanticChange(
                            type = ChangeType.RULE_CHANGED,
                            target = "slices.$sliceType.buildRules",
                            before = beforeRules,
                            after = afterRules,
                            breaking = false // 규칙 변경은 breaking 아님
                        ))
                    }
                }
            }
        }

        return changes
    }

    // ==================== ImpactMap Changes (RULESET) ====================

    @Suppress("UNCHECKED_CAST")
    private fun detectImpactMapChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<SemanticChange> {
        val changes = mutableListOf<SemanticChange>()

        val beforeMap = before["impactMap"] as? Map<String, List<String>> ?: emptyMap()
        val afterMap = after["impactMap"] as? Map<String, List<String>> ?: emptyMap()

        val allSlices = beforeMap.keys + afterMap.keys

        for (sliceType in allSlices) {
            val beforePaths = beforeMap[sliceType] ?: emptyList()
            val afterPaths = afterMap[sliceType] ?: emptyList()

            if (beforePaths.toSet() != afterPaths.toSet()) {
                changes.add(SemanticChange(
                    type = ChangeType.IMPACT_MAP_CHANGED,
                    target = "impactMap.$sliceType",
                    before = beforePaths.joinToString(", "),
                    after = afterPaths.joinToString(", "),
                    breaking = false
                ))
            }
        }

        return changes
    }

    // ==================== View Slice Changes (VIEW_DEFINITION) ====================

    @Suppress("UNCHECKED_CAST")
    private fun detectViewSliceChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<SemanticChange> {
        val changes = mutableListOf<SemanticChange>()

        val beforeRequired = (before["requiredSlices"] as? List<String>)?.toSet() ?: emptySet()
        val afterRequired = (after["requiredSlices"] as? List<String>)?.toSet() ?: emptySet()

        val beforeOptional = (before["optionalSlices"] as? List<String>)?.toSet() ?: emptySet()
        val afterOptional = (after["optionalSlices"] as? List<String>)?.toSet() ?: emptySet()

        // Required 추가/삭제
        for (slice in afterRequired - beforeRequired) {
            val wasOptional = slice in beforeOptional
            changes.add(SemanticChange(
                type = if (wasOptional) ChangeType.SLICE_REF_PROMOTED else ChangeType.SLICE_REF_ADDED,
                target = "requiredSlices.$slice",
                before = if (wasOptional) "optional" else null,
                after = "required",
                breaking = !wasOptional // 새로운 required는 breaking
            ))
        }

        for (slice in beforeRequired - afterRequired) {
            val isNowOptional = slice in afterOptional
            changes.add(SemanticChange(
                type = if (isNowOptional) ChangeType.SLICE_REF_DEMOTED else ChangeType.SLICE_REF_REMOVED,
                target = "requiredSlices.$slice",
                before = "required",
                after = if (isNowOptional) "optional" else null,
                breaking = !isNowOptional // 완전 삭제는 breaking
            ))
        }

        return changes
    }

    // ==================== Impact Calculation ====================

    @Suppress("UNCHECKED_CAST")
    private fun calculateAffectedSlices(
        changes: List<SemanticChange>,
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<String> {
        val affected = mutableSetOf<String>()

        // 필드 변경 시 impactMap에서 영향받는 Slice 찾기
        val impactMap = (after["impactMap"] ?: before["impactMap"]) as? Map<String, List<String>> ?: emptyMap()

        for (change in changes) {
            when (change.type) {
                ChangeType.FIELD_ADDED,
                ChangeType.FIELD_REMOVED,
                ChangeType.FIELD_TYPE_CHANGED,
                ChangeType.FIELD_REQUIRED_CHANGED -> {
                    val fieldName = change.target.removePrefix("fields.")
                    for ((sliceType, paths) in impactMap) {
                        if (paths.any { it.contains(fieldName) }) {
                            affected.add(sliceType)
                        }
                    }
                }

                ChangeType.SLICE_ADDED,
                ChangeType.SLICE_REMOVED,
                ChangeType.RULE_CHANGED -> {
                    val sliceType = change.target.split(".").getOrNull(1)
                    sliceType?.let { affected.add(it) }
                }

                else -> {}
            }
        }

        return affected.toList()
    }

    private fun calculateAffectedViews(affectedSlices: List<String>): List<String> {
        if (affectedSlices.isEmpty()) return emptyList()

        return try {
            val descriptors = graphService.loadAllDescriptors().getOrNull() ?: return emptyList()

            descriptors
                .filter { it.kind == ContractKind.VIEW_DEFINITION }
                .filter { view -> view.semanticInfo.slicesRequired.any { it in affectedSlices } }
                .map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ==================== Domain Models ====================

/**
 * 의미론적 Diff 결과
 */
data class SemanticDiffResult(
    val changes: List<SemanticChange>,
    val breaking: Boolean,
    val regenRequired: Boolean,
    val affectedSlices: List<String>,
    val affectedViews: List<String>
)

/**
 * 의미론적 변경
 */
data class SemanticChange(
    val type: ChangeType,
    val target: String,
    val before: String?,
    val after: String?,
    val breaking: Boolean
)

/**
 * 변경 타입
 */
enum class ChangeType {
    // Top-level
    PROPERTY_ADDED,
    PROPERTY_REMOVED,
    PROPERTY_CHANGED,

    // Fields (ENTITY_SCHEMA)
    FIELD_ADDED,
    FIELD_REMOVED,
    FIELD_TYPE_CHANGED,
    FIELD_REQUIRED_CHANGED,

    // Slices (RULESET)
    SLICE_ADDED,
    SLICE_REMOVED,
    RULE_CHANGED,
    IMPACT_MAP_CHANGED,

    // View Slice Refs (VIEW_DEFINITION)
    SLICE_REF_ADDED,
    SLICE_REF_REMOVED,
    SLICE_REF_PROMOTED,   // optional → required
    SLICE_REF_DEMOTED,    // required → optional
}
