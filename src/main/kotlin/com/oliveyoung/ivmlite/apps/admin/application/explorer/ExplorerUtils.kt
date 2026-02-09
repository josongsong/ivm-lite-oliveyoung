package com.oliveyoung.ivmlite.apps.admin.application.explorer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Explorer 공통 유틸리티
 *
 * DRY 원칙: 모든 Explorer 서비스에서 공유하는 헬퍼 함수
 */
object ExplorerUtils {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * JSON 문자열을 안전하게 파싱
     *
     * @param jsonStr JSON 문자열
     * @return 파싱된 JsonElement, 실패 시 null
     */
    fun parseJsonSafe(jsonStr: String): JsonElement? =
        runCatching { json.parseToJsonElement(jsonStr) }.getOrNull()

    /**
     * JSON 문자열을 pretty print 형식으로 변환
     *
     * @param jsonStr JSON 문자열
     * @return 포맷된 JSON 문자열, 실패 시 원본 반환
     */
    fun prettyPrint(jsonStr: String): String =
        parseJsonSafe(jsonStr)?.let { json.encodeToString(JsonElement.serializer(), it) } ?: jsonStr
}
