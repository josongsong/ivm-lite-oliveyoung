package com.oliveyoung.ivmlite.pkg.contracts.domain

import com.oliveyoung.ivmlite.shared.domain.types.SemVer

/**
 * ContractDescriptor - Contract Intelligence Core Model
 *
 * Contract의 의미론적 정보를 담는 핵심 모델.
 * DX Platform의 모든 기능(Meaning Panel, Impact Graph, Why Engine)의 공통 입력.
 *
 * @see RFC: contract-editor-ui-enhancement.md Phase 0
 */
data class ContractDescriptor(
    /** Contract 종류 */
    val kind: ContractKind,
    /** Contract ID (unique identifier) */
    val id: String,
    /** Semantic version */
    val version: SemVer,
    /** 파일 경로 (nullable for dynamic contracts) */
    val filePath: String? = null,
    /** 원본 YAML 내용 */
    val rawYaml: String,
    /** 파싱된 데이터 (Map 형태) */
    val parsed: Map<String, Any?>,
    /** 의미론적 정보 */
    val semanticInfo: SemanticInfo,
    /** Contract 상태 */
    val status: ContractStatus = ContractStatus.ACTIVE
)

/**
 * Contract 종류
 */
enum class ContractKind(val yamlValue: String) {
    ENTITY_SCHEMA("ENTITY_SCHEMA"),
    RULESET("RULESET"),
    VIEW_DEFINITION("VIEW_DEFINITION"),
    SINK_RULE("SINKRULE"),
    JOIN_SPEC("JOIN_SPEC"),
    CHANGESET("CHANGESET");

    companion object {
        fun fromString(value: String): ContractKind? =
            entries.find { it.yamlValue.equals(value, ignoreCase = true) }
    }
}

/**
 * 의미론적 정보
 *
 * Contract가 시스템에서 어떤 의미를 갖는지 표현.
 */
data class SemanticInfo(
    /** Entity Type (ENTITY_SCHEMA, RULESET, VIEW_DEFINITION에서 사용) */
    val entityType: String? = null,
    /** 필드 정보 (ENTITY_SCHEMA용) */
    val fields: List<FieldInfo> = emptyList(),
    /** 생산하는 Slice 타입들 (RULESET용) */
    val slicesProduced: List<String> = emptyList(),
    /** 필요로 하는 Slice 타입들 (VIEW_DEFINITION용) */
    val slicesRequired: List<String> = emptyList(),
    /** View 이름 (VIEW_DEFINITION용) */
    val viewName: String? = null,
    /** 참조하는 다른 Contract들 */
    val dependencies: List<DependencyRef> = emptyList(),
    /** 이 Contract를 참조하는 Contract들 (역참조) */
    val dependents: List<DependencyRef> = emptyList(),
    /** Sink 관련 정보 */
    val sinkInfo: SinkInfo? = null
)

/**
 * 필드 정보 (ENTITY_SCHEMA)
 */
data class FieldInfo(
    /** 필드 이름 */
    val name: String,
    /** 필드 타입 */
    val type: String,
    /** 필수 여부 */
    val required: Boolean = false,
    /** 설명 */
    val description: String? = null,
    /** JSON Path (중첩 필드용) */
    val path: String? = null,
    /** 기본값 */
    val defaultValue: Any? = null
)

/**
 * Sink 관련 정보 (SINK_RULE)
 */
data class SinkInfo(
    /** 대상 시스템 타입 (e.g., "OPENSEARCH", "KAFKA") */
    val targetType: String,
    /** 입력 Entity 타입들 */
    val inputEntityTypes: List<String> = emptyList(),
    /** 입력 Slice 타입들 */
    val inputSliceTypes: List<String> = emptyList()
)

/**
 * Contract 참조 (Dependency Edge)
 */
data class DependencyRef(
    /** 참조되는 Contract ID */
    val id: String,
    /** 참조되는 Contract Kind */
    val kind: ContractKind,
    /** 관계 종류 */
    val relation: RefRelation = RefRelation.USES
)

/**
 * 참조 관계 종류
 */
enum class RefRelation {
    /** 정의 관계 (Schema → RuleSet) */
    DEFINES,
    /** 사용 관계 (RuleSet → Schema) */
    USES,
    /** 생산 관계 (RuleSet → Slice) */
    PRODUCES,
    /** 필요 관계 (View → Slice) */
    REQUIRES,
    /** 소비 관계 (Sink → Slice) */
    CONSUMES
}
