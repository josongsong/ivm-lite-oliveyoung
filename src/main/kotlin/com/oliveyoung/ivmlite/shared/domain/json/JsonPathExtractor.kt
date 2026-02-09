package com.oliveyoung.ivmlite.shared.domain.json

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * JsonPathExtractor - JSON 경로 추출 유틸리티 (SSOT)
 *
 * RFC-003: 결정성 보장 - 동일 입력 → 동일 출력
 * fail-closed 원칙 적용: 파싱 실패 시 명시적 에러 반환
 *
 * 지원 패턴:
 * - "name" → root.name
 * - "product.name" → root.product.name
 * - "items[0].name" → root.items[0].name (배열 인덱스)
 * - "items[*].name" → [root.items[0].name, ...] (배열 전체)
 *
 * 사용처:
 * - JoinExecutor: JOIN 시 소스 필드 추출
 * - SlicingEngine: 필드 매핑 시 값 추출
 * - InvertedIndexBuilder: 인덱스 셀렉터 기반 값 추출
 */
object JsonPathExtractor {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * 추출 결과 타입
     */
    sealed class ExtractResult {
        data class Found(val value: String) : ExtractResult()
        data object NotFound : ExtractResult()
        data class ParseError(val message: String) : ExtractResult()
    }

    /**
     * JSON payload에서 경로 기반 단일 값 추출
     *
     * @param payload JSON 문자열
     * @param path 추출 경로 (dot notation: "product.name", 배열: "items[0].name")
     * @return ExtractResult (Found/NotFound/ParseError)
     */
    fun extractSingle(payload: String, path: String): ExtractResult {
        if (payload.isBlank()) {
            return ExtractResult.NotFound
        }

        return try {
            val root = mapper.readTree(payload)
            val value = extractNode(root, path)
            nodeToSingleResult(value)
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            ExtractResult.ParseError("JSON parsing failed: ${e.message}")
        } catch (e: IllegalArgumentException) {
            ExtractResult.ParseError("Invalid path syntax: ${e.message}")
        }
    }

    /**
     * JSON payload에서 경로 기반 다중 값 추출
     *
     * @param payload JSON 문자열
     * @param path 추출 경로 ($ prefix 허용: "$.brand_id", 배열: "items[*].name")
     * @return Either<DomainError, List<String>> - 파싱 실패 시 Left
     */
    fun extractMultiple(payload: String, path: String): Either<DomainError, List<String>> {
        if (payload.isBlank()) {
            return emptyList<String>().right()
        }

        val normalizedPath = normalizePath(path)
        if (normalizedPath.isEmpty()) {
            return emptyList<String>().right()
        }

        return try {
            val root = mapper.readTree(payload)
            val node = extractNode(root, normalizedPath)
            nodeToStringList(node).right()
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            DomainError.InvariantViolation("JSON parsing failed: ${e.message}").left()
        } catch (e: IllegalArgumentException) {
            DomainError.InvariantViolation("Invalid path syntax: ${e.message}").left()
        }
    }

    /**
     * JsonNode에서 직접 경로 기반 값 추출 (이미 파싱된 경우)
     *
     * @param root 파싱된 JsonNode
     * @param path 추출 경로
     * @return 추출된 JsonNode (없으면 null)
     */
    fun extractNode(root: JsonNode, path: String): JsonNode? {
        val normalizedPath = normalizePath(path)
        if (normalizedPath.isEmpty()) {
            return root
        }

        // 배열 와일드카드 패턴: items[*].name
        if (normalizedPath.contains("[*]")) {
            return extractArrayWildcard(root, normalizedPath)
        }

        // 일반 경로 (인덱스 포함 가능)
        return extractSimplePath(root, normalizedPath)
    }

    /**
     * 일반 경로 추출 (배열 인덱스 지원)
     *
     * 지원 패턴:
     * - "name" → root.name
     * - "product.name" → root.product.name
     * - "items[0].name" → root.items[0].name
     */
    private fun extractSimplePath(root: JsonNode, path: String): JsonNode? {
        val parts = path.split(".")
        var current: JsonNode? = root

        for (part in parts) {
            current = extractPart(current, part) ?: return null
        }

        return current
    }

    /**
     * 단일 경로 부분 추출 (배열 인덱스 처리)
     */
    private fun extractPart(node: JsonNode?, part: String): JsonNode? {
        if (node == null) return null

        // 배열 인덱스 패턴: items[0]
        if (part.contains("[") && part.endsWith("]")) {
            val fieldName = part.substringBefore("[")
            val indexStr = part.substringAfter("[").substringBefore("]")
            val index = indexStr.toIntOrNull() ?: return null

            val arrayNode = if (fieldName.isEmpty()) node else node.get(fieldName)
            if (arrayNode == null || !arrayNode.isArray) {
                return null
            }
            return arrayNode.get(index)
        }

        return node.get(part)
    }

    /**
     * 배열 와일드카드 추출: items[*].name → ArrayNode
     */
    private fun extractArrayWildcard(root: JsonNode, path: String): JsonNode? {
        val parts = path.split("[*]", limit = 2)
        if (parts.size != 2) {
            return null
        }

        val arrayPath = parts[0].removeSuffix(".")
        val afterArray = parts[1].removePrefix(".")

        val array = if (arrayPath.isEmpty()) root else extractSimplePath(root, arrayPath)

        if (array == null || !array.isArray) {
            return null
        }

        // items[*] 만 있는 경우 → 배열 전체 반환
        if (afterArray.isEmpty()) {
            return array
        }

        // 배열 내 각 요소에서 필드 추출
        val result = mapper.createArrayNode()
        array.forEach { item ->
            val value = extractSimplePath(item, afterArray)
            if (value != null && !value.isNull) {
                result.add(value)
            }
        }

        return result
    }

    /**
     * 경로 정규화: $ prefix 제거
     */
    private fun normalizePath(path: String): String {
        return path.removePrefix("$").removePrefix(".")
    }

    /**
     * JsonNode를 단일 ExtractResult로 변환
     */
    private fun nodeToSingleResult(node: JsonNode?): ExtractResult {
        return when {
            node == null || node.isNull -> ExtractResult.NotFound
            node.isTextual -> ExtractResult.Found(node.asText())
            node.isNumber -> ExtractResult.Found(node.asText())
            node.isBoolean -> ExtractResult.Found(node.asText())
            else -> ExtractResult.Found(mapper.writeValueAsString(node)) // 객체/배열은 JSON string
        }
    }

    /**
     * JsonNode를 String 리스트로 변환
     */
    private fun nodeToStringList(node: JsonNode?): List<String> {
        return when {
            node == null || node.isNull -> emptyList()
            node.isArray -> node.mapNotNull { item ->
                when {
                    item.isTextual -> item.asText()
                    item.isNumber -> item.asText()
                    item.isBoolean -> item.asText()
                    else -> null
                }
            }
            node.isTextual -> listOf(node.asText())
            node.isNumber -> listOf(node.asText())
            node.isBoolean -> listOf(node.asText())
            else -> emptyList()
        }
    }
}
