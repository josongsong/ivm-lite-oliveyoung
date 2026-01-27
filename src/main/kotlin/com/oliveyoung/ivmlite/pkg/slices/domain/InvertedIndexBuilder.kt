package com.oliveyoung.ivmlite.pkg.slices.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.domain.IndexSpec
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong
import org.slf4j.LoggerFactory

/**
 * RFC-IMPL-010 Phase D-9: InvertedIndexBuilder (통합 버전)
 *
 * Slice 생성 시 정방향/역방향 인덱스 동시 생성.
 * Contract is Law: RuleSet.indexes가 인덱스 정의의 SSOT.
 *
 * ## 인덱스 생성 규칙
 * - 정방향 인덱스: 항상 생성 (검색용)
 *   - indexType: spec.type (예: "brand")
 *   - indexValue: 추출된 값 (예: "br001")
 * 
 * - 역방향 인덱스: IndexSpec.references가 있을 때만 생성 (Fanout용)
 *   - indexType: "{entityType}_by_{references}" (예: "product_by_brand")
 *   - indexValue: FK 값 (예: "br001")
 *   - refEntityKey: 참조되는 엔티티 (예: "BRAND#tenant#br001")
 *   - targetEntityKey: 현재 엔티티 (예: "PRODUCT#tenant#prod001")
 *
 * 결정성: 동일 Slice → 동일 Index 집합
 */
class InvertedIndexBuilder {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(InvertedIndexBuilder::class.java)

    /**
     * SliceRecord에서 IndexSpec 기반 InvertedIndexEntry 생성
     *
     * @param slice 슬라이스 레코드
     * @param indexSpecs 인덱스 사양 리스트
     * @param entityType 현재 엔티티 타입 (역방향 인덱스 생성에 필요)
     * @return 생성된 InvertedIndexEntry 리스트 (정방향 + 역방향)
     */
    fun build(
        slice: SliceRecord,
        indexSpecs: List<IndexSpec>,
        entityType: String = extractEntityType(slice.entityKey),
    ): List<InvertedIndexEntry> {
        return indexSpecs.flatMap specLoop@{ spec ->
            val values = extractValues(slice.data, spec.selector)
            values.flatMap valueLoop@{ value ->
                if (value.isNullOrBlank()) return@valueLoop emptyList()

                val canonicalValue = canonicalizeIndexValue(value)
                val isTombstone = slice.tombstone?.isDeleted ?: false

                buildList {
                    // 1. 정방향 인덱스 (검색용) - 항상 생성
                    add(
                        InvertedIndexEntry(
                            tenantId = slice.tenantId,
                            refEntityKey = slice.entityKey,
                            refVersion = VersionLong(slice.version),
                            targetEntityKey = slice.entityKey,
                            targetVersion = VersionLong(slice.version),
                            indexType = spec.type,
                            indexValue = canonicalValue,
                            sliceType = slice.sliceType,
                            sliceHash = slice.hash,
                            tombstone = isTombstone,
                        )
                    )

                    // 2. 역방향 인덱스 (Fanout용) - references가 있을 때만 생성
                    if (spec.references != null) {
                        val reverseIndexType = "${entityType.lowercase()}_by_${spec.references.lowercase()}"
                        
                        // RFC-IMPL-013: indexValue는 entityId만 저장 (조회 시 매칭을 위해)
                        // selector가 EntityKey 전체를 반환할 수도 있고, entityId만 반환할 수도 있음
                        // - EntityKey 형식: "BRAND#tenant#roundlab" → entityId = "roundlab"
                        // - entityId만: "roundlab" → 그대로 사용
                        if (canonicalValue.contains("#")) {
                            // EntityKey 형식인 경우 파싱
                            val parts = canonicalValue.split("#")
                            val extractedEntityId = extractEntityIdFromParts(parts, canonicalValue)
                            
                            // 빈 entityId 체크 - 역방향 인덱스만 스킵 (정방향은 이미 생성됨)
                            if (extractedEntityId.isNotBlank()) {
                                val parsedRefEntityKey = EntityKey(canonicalValue)  // 전체 EntityKey 사용
                                
                                // tenantId 불일치 검증 (방어적 코딩)
                                val refTenantId = if (parts.size >= 2) parts[1] else null
                                if (refTenantId != null && refTenantId != slice.tenantId.value) {
                                    // tenantId 불일치 시 경고 로그 (데이터 일관성 문제)
                                    // 실제 운영에서는 이런 케이스가 발생하지 않아야 함
                                    // cross-tenant 참조가 필요한 경우 별도 설계 필요
                                    log.warn(
                                        "TenantId mismatch in reverse index: slice.tenantId={}, refEntityKey.tenantId={}, refEntityKey={}",
                                        slice.tenantId.value,
                                        refTenantId,
                                        canonicalValue
                                    )
                                }
                                
                                // 역방향 인덱스 생성
                                add(
                                    InvertedIndexEntry(
                                        tenantId = slice.tenantId,
                                        refEntityKey = parsedRefEntityKey,
                                        refVersion = VersionLong(0), // 참조 엔티티의 버전은 조회 시점에 결정
                                        targetEntityKey = slice.entityKey,
                                        targetVersion = VersionLong(slice.version),
                                        indexType = reverseIndexType,
                                        indexValue = extractedEntityId.lowercase(),  // entityId만 저장 (조회 매칭용)
                                        sliceType = slice.sliceType,
                                        sliceHash = slice.hash,
                                        tombstone = isTombstone,
                                    )
                                )
                            }
                            // 빈 entityId면 역방향 인덱스 생성 안 함 (정방향은 이미 생성됨)
                        } else {
                            // entityId만 있는 경우
                            // 빈 entityId 체크 - 역방향 인덱스만 스킵
                            if (canonicalValue.isNotBlank()) {
                                val builtRefEntityKey = buildRefEntityKey(spec.references, slice.tenantId.value, canonicalValue)
                                
                                // 역방향 인덱스 생성
                                add(
                                    InvertedIndexEntry(
                                        tenantId = slice.tenantId,
                                        refEntityKey = builtRefEntityKey,
                                        refVersion = VersionLong(0),
                                        targetEntityKey = slice.entityKey,
                                        targetVersion = VersionLong(slice.version),
                                        indexType = reverseIndexType,
                                        indexValue = canonicalValue,  // 이미 lowercase됨
                                        sliceType = slice.sliceType,
                                        sliceHash = slice.hash,
                                        tombstone = isTombstone,
                                    )
                                )
                            }
                            // 빈 entityId면 역방향 인덱스 생성 안 함 (정방향은 이미 생성됨)
                        }
                    }
                }
            }
        }
    }

    /**
     * EntityKey 파트에서 entityId 추출
     * 
     * RFC-003: EntityKey 포맷은 {ENTITY_TYPE}#{tenantId}#{entityId} (고정)
     * 엣지 케이스 처리:
     * - parts.size >= 3: parts[2] 사용 (표준)
     * - parts.size < 3: 전체 값을 entityId로 사용 (비표준, 방어적 코딩)
     * - parts[2]가 빈 문자열: 빈 문자열 반환 (호출자가 체크해야 함)
     */
    private fun extractEntityIdFromParts(parts: List<String>, fallback: String): String {
        return if (parts.size >= 3) {
            parts[2]  // 표준 포맷: {TYPE}#{tenantId}#{entityId}
        } else {
            fallback  // 비표준 포맷 (방어적 코딩)
        }
    }

    /**
     * 참조 엔티티 키 생성
     * 
     * 표준 포맷: {ENTITY_TYPE}#{tenantId}#{entityId}
     */
    private fun buildRefEntityKey(refEntityType: String, tenantId: String, entityId: String): EntityKey {
        return EntityKey("${refEntityType.uppercase()}#$tenantId#$entityId")
    }

    /**
     * EntityKey에서 엔티티 타입 추출
     * 
     * 표준 포맷: {ENTITY_TYPE}#{tenantId}#{entityId}
     */
    private fun extractEntityType(entityKey: EntityKey): String {
        val parts = entityKey.value.split("#")
        return if (parts.isNotEmpty()) parts[0] else "UNKNOWN"
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
            log.debug("Failed to parse JSON for selector extraction: selector={}, error={}", selector, e.message)
            return emptyList()
        }

        // $ prefix 제거
        val path = selector.removePrefix("$").removePrefix(".")

        if (path.isEmpty()) {
            log.debug("Empty path after removing $ prefix: selector={}", selector)
            return emptyList()
        }

        val node = extractNode(root, path)
        if (node == null) {
            log.debug("Node not found for path: selector={}, path={}", selector, path)
            return emptyList()
        }

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
            log.debug("Invalid array field pattern (should contain [*]): path={}", path)
            return null
        }

        val arrayPath = parts[0]
        val afterArray = parts[1].removePrefix(".")

        val array = if (arrayPath.isEmpty()) root else extractNode(root, arrayPath)

        if (array == null) {
            log.debug("Array path not found: path={}, arrayPath={}", path, arrayPath)
            return null
        }
        
        if (!array.isArray) {
            log.debug("Path does not point to an array: path={}, arrayPath={}, nodeType={}", path, arrayPath, array.nodeType)
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
