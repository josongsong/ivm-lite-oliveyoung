package com.oliveyoung.ivmlite.shared.domain.determinism

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Canonical JSON for determinism.
 *
 * 결정성 보장:
 * - 키 알파벳순 정렬
 * - 안정적인 직렬화
 */
object CanonicalJson {
  private val mapper: ObjectMapper = jacksonObjectMapper().apply {
    enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
  }

  fun canonicalize(json: String): String = canonicalize(mapper.readTree(json))

  fun canonicalize(node: JsonNode): String {
    val sorted = sortKeys(node)
    return mapper.writeValueAsString(sorted)
  }

  /**
   * JSON 노드의 키를 재귀적으로 정렬
   */
  private fun sortKeys(node: JsonNode): JsonNode = when {
    node.isObject -> {
      val sorted = mapper.createObjectNode()
      node.fieldNames().asSequence()
        .sortedBy { it }
        .forEach { key ->
          sorted.set<JsonNode>(key, sortKeys(node.get(key)))
        }
      sorted
    }
    node.isArray -> {
      val sorted = mapper.createArrayNode()
      node.forEach { element ->
        sorted.add(sortKeys(element))
      }
      sorted
    }
    else -> node
  }
}
