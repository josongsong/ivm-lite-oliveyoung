package com.oliveyoung.ivmlite.pkg.sinks.domain

import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * SinkRule - Slice를 어떤 Sink로 라우팅할지 정의 (RFC-007, RFC-IMPL-013)
 *
 * Contract YAML에서 로드되어 Slice 생성 시 자동으로 ShipRequested outbox 생성에 사용됨.
 *
 * 핵심 원칙:
 * - Slice 생성 → SinkRule 매칭 → ShipRequested outbox 자동 생성
 * - SDK에서 별도로 ship.to { } 호출 불필요
 * - 최소한의 라우팅 정보만 (Minimal Coupling, RFC-IMPL-012)
 */
data class SinkRule(
    val id: String,
    val version: String,
    val status: SinkRuleStatus,
    val input: SinkRuleInput,
    val target: SinkRuleTarget,
    val docId: DocIdSpec,
    val commit: CommitSpec = CommitSpec()
)

enum class SinkRuleStatus {
    ACTIVE,
    INACTIVE,
    DEPRECATED
}

/**
 * 입력 조건: 어떤 Slice를 Ship할지
 */
data class SinkRuleInput(
    val type: InputType = InputType.SLICE,
    val sliceTypes: List<SliceType> = listOf(SliceType.CORE),
    val entityTypes: List<String> = emptyList(),
    val filter: Map<String, String> = emptyMap()
)

enum class InputType {
    SLICE,
    VIEW
}

/**
 * 타겟: 어디로 보낼지 (최소한의 라우팅 정보)
 */
data class SinkRuleTarget(
    val type: SinkTargetType,
    val endpoint: String,
    val indexPattern: String? = null,  // OpenSearch용
    val datasetArn: String? = null,    // Personalize용
    val auth: AuthSpec? = null
)

enum class SinkTargetType {
    OPENSEARCH,
    PERSONALIZE,
    KAFKA,
    S3
}

data class AuthSpec(
    val type: AuthType = AuthType.NONE,
    val username: String? = null,
    val password: String? = null
)

enum class AuthType {
    NONE,
    BASIC,
    IAM
}

/**
 * 문서 ID 패턴 (멱등성 보장)
 */
data class DocIdSpec(
    val pattern: String = "{tenantId}__{entityKey}"
)

/**
 * 커밋 설정
 */
data class CommitSpec(
    val batchSize: Int = 1000,
    val timeoutMs: Long = 30000
)
