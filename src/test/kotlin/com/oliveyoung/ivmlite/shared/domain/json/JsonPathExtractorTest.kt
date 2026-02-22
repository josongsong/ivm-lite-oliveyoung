package com.oliveyoung.ivmlite.shared.domain.json

import arrow.core.Either
import com.oliveyoung.ivmlite.shared.domain.json.JsonPathExtractor.ExtractResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * JsonPathExtractor 단위 테스트
 *
 * 커버리지:
 * - extractSingle: 단순 필드, 중첩, 배열 인덱스, 빈 payload, 잘못된 JSON
 * - extractMultiple: 배열 와일드카드, $ prefix, 빈 경로, 빈 payload
 * - extractNode: JsonNode 직접 추출
 * - 경로 정규화 (normalizePath)
 */
class JsonPathExtractorTest : DescribeSpec({

    val sampleJson = """
        {
            "name": "iPhone",
            "price": 1200000,
            "active": true,
            "brand": {
                "id": "BRAND-001",
                "name": "Apple"
            },
            "tags": ["electronics", "phone", "premium"],
            "items": [
                {"name": "Case", "price": 30000},
                {"name": "Charger", "price": 50000}
            ],
            "nullField": null
        }
    """.trimIndent()

    describe("extractSingle") {

        it("단순 필드 → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "name")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "iPhone"
        }

        it("숫자 필드 → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "price")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "1200000"
        }

        it("boolean 필드 → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "active")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "true"
        }

        it("중첩 필드 (dot notation) → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "brand.name")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "Apple"
        }

        it("배열 인덱스 → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "tags[0]")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "electronics"
        }

        it("배열 내 객체 필드 → Found") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "items[1].name")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            (result as ExtractResult.Found).value shouldBe "Charger"
        }

        it("존재하지 않는 필드 → NotFound") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "nonexistent")
            result.shouldBeInstanceOf<ExtractResult.NotFound>()
        }

        it("null 필드 → NotFound") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "nullField")
            result.shouldBeInstanceOf<ExtractResult.NotFound>()
        }

        it("빈 payload → NotFound") {
            val result = JsonPathExtractor.extractSingle("", "name")
            result.shouldBeInstanceOf<ExtractResult.NotFound>()
        }

        it("잘못된 JSON → ParseError") {
            val result = JsonPathExtractor.extractSingle("not-json", "name")
            result.shouldBeInstanceOf<ExtractResult.ParseError>()
        }

        it("배열 범위 초과 → NotFound") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "tags[99]")
            result.shouldBeInstanceOf<ExtractResult.NotFound>()
        }

        it("객체 필드 → JSON 문자열") {
            val result = JsonPathExtractor.extractSingle(sampleJson, "brand")
            result.shouldBeInstanceOf<ExtractResult.Found>()
            val value = (result as ExtractResult.Found).value
            // brand는 객체이므로 JSON 문자열로 반환
            value.contains("Apple") shouldBe true
        }
    }

    describe("extractMultiple") {

        it("배열 와일드카드 → 리스트") {
            val result = JsonPathExtractor.extractMultiple(sampleJson, "items[*].name")
            result.shouldBeInstanceOf<Either.Right<*>>()
            val values = (result as Either.Right).value
            values shouldBe listOf("Case", "Charger")
        }

        it("\$ prefix 경로 → 정상 추출") {
            val result = JsonPathExtractor.extractMultiple(sampleJson, "$.brand.id")
            result.shouldBeInstanceOf<Either.Right<*>>()
            val values = (result as Either.Right).value
            values shouldBe listOf("BRAND-001")
        }

        it("빈 payload → 빈 리스트") {
            val result = JsonPathExtractor.extractMultiple("", "name")
            result.shouldBeInstanceOf<Either.Right<*>>()
            (result as Either.Right).value shouldBe emptyList()
        }

        it("존재하지 않는 경로 → 빈 리스트") {
            val result = JsonPathExtractor.extractMultiple(sampleJson, "nonexistent")
            result.shouldBeInstanceOf<Either.Right<*>>()
            (result as Either.Right).value shouldBe emptyList()
        }

        it("잘못된 JSON → Left") {
            val result = JsonPathExtractor.extractMultiple("invalid-json", "name")
            result.shouldBeInstanceOf<Either.Left<*>>()
        }

        it("배열 전체 와일드카드 (items[*]) → 값 리스트") {
            val result = JsonPathExtractor.extractMultiple(sampleJson, "tags[*]")
            result.shouldBeInstanceOf<Either.Right<*>>()
            val values = (result as Either.Right).value
            values shouldBe listOf("electronics", "phone", "premium")
        }

        it("단일 값 → 1개 리스트") {
            val result = JsonPathExtractor.extractMultiple(sampleJson, "name")
            result.shouldBeInstanceOf<Either.Right<*>>()
            (result as Either.Right).value shouldBe listOf("iPhone")
        }
    }

    describe("extractNode") {

        it("빈 경로 → root 노드 반환") {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val root = mapper.readTree(sampleJson)

            val node = JsonPathExtractor.extractNode(root, "")
            node shouldBe root
        }

        it("\$ 경로 → root 노드 반환") {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val root = mapper.readTree(sampleJson)

            val node = JsonPathExtractor.extractNode(root, "$")
            node shouldBe root
        }
    }
})
