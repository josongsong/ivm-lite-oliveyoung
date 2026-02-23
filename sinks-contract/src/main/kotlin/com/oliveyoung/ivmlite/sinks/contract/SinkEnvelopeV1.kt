package com.oliveyoung.ivmlite.sinks.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sink Envelope V1 - 표준 Sink 페이로드 계약
 *
 * RFC-017: Sink Plugin Architecture
 *
 * 버저닝 규칙 (LOCK):
 * - 신규 필드는 OPTIONAL로만 추가 가능
 * - required 필드 추가 금지
 * - unknown 필드는 무시 (직렬화 정책)
 */
@Serializable
data class SinkEnvelopeV1(
    /**
     * Envelope 버전 (계약 진화 추적)
     */
    val envelopeVersion: Int = 1,

    /**
     * Sink 타겟 식별자
     * 예: "s3-sink", "kinesis-sink", "opensearch-sink"
     */
    val target: String,

    /**
     * 엔진 생성 시각 (Epoch Milliseconds)
     */
    val producedAtEpochMs: Long,

    /**
     * 분산 추적 ID (선택)
     */
    val traceId: String? = null,

    /**
     * 상관관계 ID (선택)
     */
    val correlationId: String? = null,

    // ===== Payload 정보 =====

    /**
     * 페이로드 버전 (IVM 버전)
     */
    val payloadVersion: Long,

    /**
     * 엔티티 타입
     * 예: "product", "brand", "category"
     */
    val entityType: String,

    /**
     * 슬라이스 타입
     * 예: "core", "detail", "search"
     */
    val sliceType: String,

    /**
     * 뷰 이름
     * 예: "view-product-core", "view-product-search"
     */
    val viewName: String,

    /**
     * 실제 뷰 데이터 (JSON)
     */
    val viewData: JsonObject,

    /**
     * 추가 메타데이터 (확장용)
     */
    val metadata: Map<String, String> = emptyMap()
)
