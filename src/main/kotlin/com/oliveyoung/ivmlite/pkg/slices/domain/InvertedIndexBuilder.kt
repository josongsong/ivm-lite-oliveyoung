package com.oliveyoung.ivmlite.pkg.slices.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.domain.IndexSpec
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong

/**
 * RFC-IMPL-010 Phase D-9: InvertedIndexBuilder
 *
 * Slice 생성 시 Inverted Index 동시 생성.
 * Contract is Law: RuleSet.indexes가 인덱스 정의의 SSOT.
 *
 * 결정성: 동일 Slice → 동일 Index 집합
 */
class InvertedIndexBuilder {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * SliceRecord에서 IndexSpec 기반 InvertedIndexEntry 생성
     *
     * @param slice 슬라이스 레코드
     * @param indexSpecs 인덱스 사양 리스트
     * @return 생성된 InvertedIndexEntry 리스트
     */
    fun build(
        slice: SliceRecord,
        indexSpecs: List<IndexSpec>,
    ): List<InvertedIndexEntry> {
        return indexSpecs.flatMap { spec ->
            val values = extractValues(slice.data, spec.selector)
            values.mapNotNull { value ->
                if (value.isNullOrBlank()) return@mapNotNull null

                InvertedIndexEntry(
                    tenantId = slice.tenantId,
                    refEntityKey = slice.entityKey,
                    refVersion = VersionLong(slice.version),
                    targetEntityKey = slice.entityKey,
                    targetVersion = VersionLong(slice.version),
                    indexType = spec.type,
                    indexValue = canonicalizeIndexValue(value),
                    sliceType = slice.sliceType,
                    sliceHash = slice.hash,
                    tombstone = slice.tombstone?.isDeleted ?: false,
                )
            }
        }
    }

    /**
     * 인덱스 값 정규화: trim + lowercase
     *
     * 결정성 보장을 위한 canonicalization.
     */
    private fun canonicalizeIndexValue(value: String): String =
        value.trim().lowercase()

    /**
     * JSON selector 기반 값 추출
     *
     * 지원 패턴:
     * - "$.brand_id" → ["BR001"]
     * - "$.category_ids[*]" → ["CAT1", "CAT2", "CAT3"]
     * - "$.product.name" → ["Product Name"]
     *
     * @param json JSON 문자열
     * @param selector JSON Path 선택자 ($ prefix)
     * @return 추출된 문자열 값 리스트 (null/blank 포함 가능)
     */
    private fun extractValues(json: String, selector: String): List<String> {
        if (json.isBlank()) {
            return emptyList()
        }

        val root = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            return emptyList()
        }

        // $ prefix 제거
        val path = selector.removePrefix("$").removePrefix(".")

        if (path.isEmpty()) {
            return emptyList()
        }

        val node = extractNode(root, path) ?: return emptyList()

        return nodeToStringValues(node)
    }

    /**
     * JSON Node에서 path 기반 노드 추출
     *
     * 지원 패턴:
     * - "brand_id" → root.brand_id
     * - "product.name" → root.product.name
     * - "category_ids[*]" → root.category_ids (배열)
     * - "items[*].name" → [root.items[0].name, root.items[1].name, ...]
     */
    private fun extractNode(root: JsonNode, path: String): JsonNode? {
        // 배열 내부 필드 추출 패턴: items[*].name
        if (path.contains("[*]")) {
            return extractArrayField(root, path)
        }

        // 중첩 필드: product.name
        val parts = path.split(".")
        var current: JsonNode? = root

        for (part in parts) {
            current = current?.get(part)
            if (current == null) {
                return null
            }
        }

        return current
    }

    /**
     * 배열 내부 필드 추출: items[*].name → ArrayNode["a", "b"]
     */
    private fun extractArrayField(root: JsonNode, path: String): JsonNode? {
        val parts = path.split("[*]", limit = 2)
        if (parts.size != 2) {
            return null
        }

        val arrayPath = parts[0]
        val afterArray = parts[1].removePrefix(".")

        val array = if (arrayPath.isEmpty()) root else extractNode(root, arrayPath)

        if (array == null || !array.isArray) {
            return null
        }

        // items[*] 로만 끝나는 경우 → 배열 전체 반환
        if (afterArray.isEmpty()) {
            return array
        }

        val result = mapper.createArrayNode()
        array.forEach { item ->
            val value = extractNode(item, afterArray)
            if (value != null && !value.isNull) {
                result.add(value)
            }
        }

        return result
    }

    /**
     * JsonNode를 String 값 리스트로 변환
     *
     * - Primitive → [stringValue]
     * - Array → [item1, item2, ...]
     * - Object/null → []
     */
    private fun nodeToStringValues(node: JsonNode): List<String> {
        return when {
            node.isNull -> emptyList()
            node.isArray -> {
                node.mapNotNull { item ->
                    when {
                        item.isTextual -> item.asText()
                        item.isNumber -> item.asText()
                        item.isBoolean -> item.asText()
                        else -> null
                    }
                }
            }
            node.isTextual -> listOf(node.asText())
            node.isNumber -> listOf(node.asText())
            node.isBoolean -> listOf(node.asText())
            else -> emptyList()
        }
    }
}
