package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.time.Instant

/**
 * Why Engine Service (Phase 3: DX Platform)
 *
 * "왜 안 됐지?" 질문에 답하는 핵심 서비스.
 * - Contract 실패 원인 분석
 * - Cause Chain 생성
 * - Fix Suggestion 제공
 * - Contract 설명 생성
 */
class WhyEngineService(
    private val graphService: ContractGraphService
) {
    // ==================== Public API ====================

    /**
     * 실패 원인 분석
     *
     * @param contractId 분석 대상 Contract ID
     * @param symptom 증상 설명 (e.g., "Slice 생성 안됨", "View 필드 누락")
     */
    fun explainFailure(
        contractId: String,
        symptom: String
    ): Either<DomainError, WhyExplanation> = either {
        val allDescriptors = graphService.loadAllDescriptors().bind()
        val targetDescriptor = allDescriptors.find { it.id == contractId }

        val causeChain = buildCauseChain(symptom, targetDescriptor, allDescriptors)

        WhyExplanation(
            symptom = symptom,
            causeChain = causeChain,
            lastEvaluated = Instant.now()
        )
    }

    /**
     * Contract 설명 생성
     *
     * @param kind Contract 종류
     * @param id Contract ID
     */
    fun explainContract(
        kind: ContractKind,
        id: String
    ): Either<DomainError, ContractExplanation> = either {
        val allDescriptors = graphService.loadAllDescriptors().bind()
        val graph = graphService.buildGraph().bind()

        val descriptor = allDescriptors.find { it.kind == kind && it.id == id }
            ?: raise(DomainError.NotFoundError("Contract", "$kind/$id"))

        // 의존성 (이 Contract가 참조하는 것들)
        val deps = graph.edges
            .filter { edge -> edge.from == descriptor.id }
            .mapNotNull { edge ->
                allDescriptors.find { d -> d.id == edge.to }?.let { d ->
                    WhyContractRef(id = d.id, kind = d.kind.name)
                }
            }

        // 종속성 (이 Contract를 참조하는 것들)
        val dependentsList = graph.edges
            .filter { edge -> edge.to == descriptor.id }
            .mapNotNull { edge ->
                allDescriptors.find { d -> d.id == edge.from }?.let { d ->
                    WhyContractRef(id = d.id, kind = d.kind.name)
                }
            }

        ContractExplanation(
            id = descriptor.id,
            kind = descriptor.kind,
            summary = generateSummary(descriptor),
            purpose = generatePurpose(descriptor),
            inputs = extractInputs(descriptor),
            outputs = extractOutputs(descriptor),
            dependencies = deps,
            dependents = dependentsList
        )
    }

    // ==================== Cause Chain Building ====================

    private fun buildCauseChain(
        symptom: String,
        target: ContractDescriptor?,
        allDescriptors: List<ContractDescriptor>
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = 1

        // 1. Contract 존재 여부
        if (target == null) {
            causes.add(Cause(
                order = order++,
                description = "지정된 Contract를 찾을 수 없습니다",
                expected = "Contract가 등록되어 있어야 함",
                actual = "Contract 미존재",
                relatedContract = null,
                fixSuggestion = "Contract ID를 확인하거나, 새 Contract를 생성하세요"
            ))
            return causes
        }

        // 2. 증상별 분석
        val symptomLower = symptom.lowercase()

        when {
            symptomLower.contains("slice") && symptomLower.contains("생성") -> {
                causes.addAll(analyzeSliceCreation(target, allDescriptors, order))
            }
            symptomLower.contains("view") && symptomLower.contains("누락") -> {
                causes.addAll(analyzeViewFieldMissing(target, allDescriptors, order))
            }
            symptomLower.contains("검증") || symptomLower.contains("validation") -> {
                causes.addAll(analyzeValidationFailure(target, allDescriptors, order))
            }
            symptomLower.contains("참조") || symptomLower.contains("ref") -> {
                causes.addAll(analyzeReferenceIssue(target, allDescriptors, order))
            }
            symptomLower.contains("sink") || symptomLower.contains("전송") -> {
                causes.addAll(analyzeSinkIssue(target, allDescriptors, order))
            }
            else -> {
                causes.addAll(analyzeGeneric(target, allDescriptors, order))
            }
        }

        // 3. 최종 해결책
        if (causes.isEmpty()) {
            causes.add(Cause(
                order = order,
                description = "명확한 원인을 찾지 못했습니다",
                expected = null,
                actual = symptom,
                relatedContract = WhyContractRef(target.id, target.kind.name),
                fixSuggestion = "로그를 확인하거나, Contract 시뮬레이션을 실행해보세요"
            ))
        }

        return causes
    }

    private fun analyzeSliceCreation(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        // EntitySchema 존재 여부
        val entityType = target.semanticInfo.entityType
        if (entityType != null) {
            val hasSchema = allDescriptors.any {
                it.kind == ContractKind.ENTITY_SCHEMA && it.semanticInfo.entityType == entityType
            }
            if (!hasSchema) {
                causes.add(Cause(
                    order = order++,
                    description = "EntityType '$entityType'에 대한 ENTITY_SCHEMA가 없습니다",
                    expected = "ENTITY_SCHEMA가 정의되어 있어야 함",
                    actual = "ENTITY_SCHEMA 미존재",
                    relatedContract = null,
                    fixSuggestion = "ENTITY_SCHEMA Contract를 먼저 생성하세요"
                ))
            }
        }

        // RuleSet 확인
        if (target.kind == ContractKind.RULESET) {
            val slicesProduced = target.semanticInfo.slicesProduced
            if (slicesProduced.isEmpty()) {
                causes.add(Cause(
                    order = order++,
                    description = "RuleSet에 Slice 정의가 없습니다",
                    expected = "최소 1개의 Slice 정의",
                    actual = "0개의 Slice",
                    relatedContract = WhyContractRef(target.id, target.kind.name),
                    fixSuggestion = "slices 섹션에 Slice 정의를 추가하세요"
                ))
            }
        }

        return causes
    }

    private fun analyzeViewFieldMissing(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        if (target.kind == ContractKind.VIEW_DEFINITION) {
            val requiredSlices = target.semanticInfo.slicesRequired
            val entityType = target.semanticInfo.entityType

            // 필요한 Slice가 생성되는지 확인
            val availableSlices = allDescriptors
                .filter { it.kind == ContractKind.RULESET && it.semanticInfo.entityType == entityType }
                .flatMap { it.semanticInfo.slicesProduced }
                .toSet()

            val missingSlices = requiredSlices.filter { it !in availableSlices }
            if (missingSlices.isNotEmpty()) {
                causes.add(Cause(
                    order = order++,
                    description = "View에 필요한 Slice가 생성되지 않습니다: ${missingSlices.joinToString()}",
                    expected = "requiredSlices의 모든 Slice가 RuleSet에서 생성되어야 함",
                    actual = "누락된 Slice: ${missingSlices.joinToString()}",
                    relatedContract = WhyContractRef(target.id, target.kind.name),
                    fixSuggestion = "해당 EntityType의 RuleSet에 누락된 Slice 정의를 추가하세요"
                ))
            }
        }

        return causes
    }

    private fun analyzeValidationFailure(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        // 필수 필드 확인
        if (target.semanticInfo.entityType == null && target.kind != ContractKind.SINK_RULE) {
            causes.add(Cause(
                order = order++,
                description = "entityType 필드가 누락되었습니다",
                expected = "entityType 필드 필수",
                actual = "entityType 없음",
                relatedContract = WhyContractRef(target.id, target.kind.name),
                fixSuggestion = "entityType 필드를 추가하세요"
            ))
        }

        return causes
    }

    private fun analyzeReferenceIssue(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        // 참조 무결성 검사
        for (ref in target.semanticInfo.dependencies) {
            val exists = allDescriptors.any { it.id == ref.id }
            if (!exists) {
                causes.add(Cause(
                    order = order++,
                    description = "참조하는 Contract '${ref.id}'를 찾을 수 없습니다",
                    expected = "참조 대상이 존재해야 함",
                    actual = "Contract '${ref.id}' 미존재",
                    relatedContract = WhyContractRef(target.id, target.kind.name),
                    fixSuggestion = "참조 대상 Contract를 먼저 생성하거나, 참조를 제거하세요"
                ))
            }
        }

        return causes
    }

    private fun analyzeSinkIssue(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        if (target.kind == ContractKind.SINK_RULE) {
            // Sink 타겟 View 존재 확인
            val sourceViews = target.semanticInfo.dependencies
                .filter { it.kind == ContractKind.VIEW_DEFINITION }

            if (sourceViews.isEmpty()) {
                causes.add(Cause(
                    order = order++,
                    description = "Sink Rule에 소스 View가 지정되지 않았습니다",
                    expected = "최소 1개의 소스 View 필요",
                    actual = "소스 View 없음",
                    relatedContract = WhyContractRef(target.id, target.kind.name),
                    fixSuggestion = "sourceView 또는 sourceViews 필드를 추가하세요"
                ))
            }
        }

        return causes
    }

    private fun analyzeGeneric(
        target: ContractDescriptor,
        allDescriptors: List<ContractDescriptor>,
        startOrder: Int
    ): List<Cause> {
        val causes = mutableListOf<Cause>()
        var order = startOrder

        // 일반적인 검사
        causes.add(Cause(
            order = order++,
            description = "Contract '${target.id}' (${target.kind}) 분석 중",
            expected = null,
            actual = null,
            relatedContract = WhyContractRef(target.id, target.kind.name),
            fixSuggestion = "시뮬레이션을 실행하여 구체적인 에러를 확인하세요"
        ))

        return causes
    }

    // ==================== Explanation Helpers ====================

    private fun generateSummary(descriptor: ContractDescriptor): String {
        return when (descriptor.kind) {
            ContractKind.ENTITY_SCHEMA -> "EntityType '${descriptor.semanticInfo.entityType}'의 스키마 정의"
            ContractKind.RULESET -> "EntityType '${descriptor.semanticInfo.entityType}'에서 ${descriptor.semanticInfo.slicesProduced.size}개 Slice 생성"
            ContractKind.VIEW_DEFINITION -> "EntityType '${descriptor.semanticInfo.entityType}'의 View 정의"
            ContractKind.SINK_RULE -> "외부 시스템으로 데이터 전송 규칙"
            else -> "Contract '${descriptor.id}' (${descriptor.kind.name})"
        }
    }

    private fun generatePurpose(descriptor: ContractDescriptor): String {
        return when (descriptor.kind) {
            ContractKind.ENTITY_SCHEMA -> "원본 데이터의 구조를 정의하고, 필드 타입과 제약조건을 명시합니다"
            ContractKind.RULESET -> "원본 데이터를 의미있는 Slice로 분할하고, 변경 추적을 가능하게 합니다"
            ContractKind.VIEW_DEFINITION -> "여러 Slice를 조합하여 하나의 일관된 View를 생성합니다"
            ContractKind.SINK_RULE -> "View 데이터를 외부 시스템(Kafka, API 등)으로 전송합니다"
            else -> "Contract 정의"
        }
    }

    private fun extractInputs(descriptor: ContractDescriptor): List<String> {
        return when (descriptor.kind) {
            ContractKind.ENTITY_SCHEMA -> listOf("RawData (JSON)")
            ContractKind.RULESET -> listOf("RawData (JSON)", "ENTITY_SCHEMA")
            ContractKind.VIEW_DEFINITION -> descriptor.semanticInfo.slicesRequired.map { "Slice: $it" }
            ContractKind.SINK_RULE -> descriptor.semanticInfo.dependencies
                .filter { it.kind == ContractKind.VIEW_DEFINITION }
                .map { "View: ${it.id}" }
            else -> emptyList()
        }
    }

    private fun extractOutputs(descriptor: ContractDescriptor): List<String> {
        return when (descriptor.kind) {
            ContractKind.ENTITY_SCHEMA -> listOf("스키마 검증 결과")
            ContractKind.RULESET -> descriptor.semanticInfo.slicesProduced.map { "Slice: $it" }
            ContractKind.VIEW_DEFINITION -> listOf("View: ${descriptor.id}")
            ContractKind.SINK_RULE -> listOf("외부 시스템 전송")
            else -> emptyList()
        }
    }
}

// ==================== Domain Models ====================

/**
 * "왜 안 됐지?" 질문에 대한 답변
 */
data class WhyExplanation(
    val symptom: String,
    val causeChain: List<Cause>,
    val lastEvaluated: Instant?
)

/**
 * 원인 체인의 한 단계
 */
data class Cause(
    val order: Int,
    val description: String,
    val expected: String?,
    val actual: String?,
    val relatedContract: WhyContractRef?,
    val fixSuggestion: String?
)

/**
 * Contract에 대한 설명
 */
data class ContractExplanation(
    val id: String,
    val kind: ContractKind,
    val summary: String,
    val purpose: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val dependencies: List<WhyContractRef>,
    val dependents: List<WhyContractRef>
)

/**
 * Contract 참조
 */
data class WhyContractRef(
    val id: String,
    val kind: String
)
