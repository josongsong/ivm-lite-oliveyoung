package com.oliveyoung.ivmlite.sinks.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sink Contract (SOTA-grade)
 *
 * 핵심 원칙:
 * 1. Idempotency: idempotencyKey로 재처리 안전성 보장
 * 2. Ordering: orderingKey로 순서 보장 (선택)
 * 3. Integrity: payloadDigest로 무결성 검증
 * 4. Evolution: 버전별 계약 분리
 */
@Serializable
sealed interface SinkPayload {
    val contractVersion: String
    val correlationId: String
    val timestamp: String

    // SOTA 필수 필드
    val idempotencyKey: String     // 재처리 안전성
    val orderingKey: String?       // 순서 보장 (선택)
    val payloadDigest: String      // 무결성 검증

    /**
     * v1.0 계약 (현재)
     */
    @Serializable
    data class V1(
        override val contractVersion: String = "1.0",
        override val correlationId: String,
        override val timestamp: String,
        override val idempotencyKey: String,
        override val orderingKey: String? = null,
        override val payloadDigest: String,

        // 비즈니스 필드
        val tenantId: String,
        val entityKey: String,
        val entityVersion: Long,
        val viewType: String,
        val viewData: JsonObject,
        val metadata: Map<String, String> = emptyMap()
    ) : SinkPayload

    companion object {
        /**
         * Idempotency Key 생성 (결정적)
         *
         * 형식: {tenantId}:{entityKey}:{entityVersion}:{viewType}:{digest}
         */
        fun generateIdempotencyKey(
            tenantId: String,
            entityKey: String,
            entityVersion: Long,
            viewType: String,
            payloadDigest: String
        ): String {
            return "$tenantId:$entityKey:$entityVersion:$viewType:${payloadDigest.take(16)}"
        }

        /**
         * Ordering Key 생성 (엔티티 단위)
         *
         * 형식: {tenantId}:{entityKey}
         */
        fun generateOrderingKey(tenantId: String, entityKey: String): String {
            return "$tenantId:$entityKey"
        }

        /**
         * Payload Digest 생성 (정규화 + SHA-256)
         *
         * JSON 정규화 규칙:
         * 1. Key 정렬
         * 2. 공백 제거
         * 3. null 값 제거
         * 4. 숫자 표현 통일
         */
        fun computePayloadDigest(viewData: JsonObject): String {
            val canonical = canonicalizeJson(viewData)
            return sha256(canonical)
        }

        private fun canonicalizeJson(json: JsonObject): String {
            // RFC 8785 (JSON Canonicalization Scheme) 준수
            // 1. Key 정렬 (알파벳순)
            // 2. 공백 제거
            // 3. Unicode escape 정규화
            val sortedKeys = json.keys.sorted()
            val canonical = sortedKeys.joinToString(",", "{", "}") { key ->
                val value = json[key]
                """"$key":${value.toString()}"""
            }
            return canonical
        }

        private fun sha256(input: String): String {
            // SHA-256 해시 (java.security.MessageDigest)
            val bytes = input.toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
