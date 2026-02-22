package com.oliveyoung.ivmlite.pkg.changeset

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * ChangeSetBuilder RFC8785 CanonicalJson 결정성 테스트
 *
 * changeset.v1 계약-구현 정합성 검증:
 * - valueHash, payloadHash가 canonical JSON 기반으로 결정적 생성되는지
 * - 키 순서/공백 등 직렬화 차이에 무관하게 동일 해시
 */
class ChangeSetBuilderDeterminismTest : StringSpec({

    val builder = ChangeSetBuilder()
    val tenantId = TenantId("t1")
    val entityKey = EntityKey("product:p1")

    "valueHash 결정성 - 키 순서 다른 동일 객체 → 동일 valueHash" {
        // 같은 /b 변경, from/to 각각 키 순서 다름
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":1,"b":100}""",
            toPayload = """{"a":1,"b":200}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"b":100,"a":1}""",
            toPayload = """{"b":200,"a":1}""",
            emptySet(), emptyMap()
        )

        cs1.changedPaths shouldHaveSize 1
        cs2.changedPaths shouldHaveSize 1
        cs1.changedPaths.first().path shouldBe "/b"
        cs2.changedPaths.first().path shouldBe "/b"
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "payloadHash 결정성 - 키 순서 다른 동일 payload → 동일 payloadHash" {
        val payload1 = """{"title":"상품A","price":10000}"""
        val payload2 = """{"price":10000,"title":"상품A"}"""

        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = payload1,
            toPayload = payload1,
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = payload2,
            toPayload = payload2,
            emptySet(), emptyMap()
        )

        cs1.payloadHash shouldBe cs2.payloadHash
    }

    "payloadHash 결정성 - CREATE 타입, 키 순서 다른 toPayload" {
        val to1 = """{"name":"신상품","price":5000}"""
        val to2 = """{"price":5000,"name":"신상품"}"""

        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 0, toVersion = 1,
            fromPayload = null,
            toPayload = to1,
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 0, toVersion = 1,
            fromPayload = null,
            toPayload = to2,
            emptySet(), emptyMap()
        )

        cs1.changeType shouldBe ChangeType.CREATE
        cs2.changeType shouldBe ChangeType.CREATE
        cs1.payloadHash shouldBe cs2.payloadHash
    }

    "valueHash - 중첩 객체 키 순서 무관" {
        val from1 = """{"options":[{"price":100,"name":"A"}]}"""
        val from2 = """{"options":[{"name":"A","price":100}]}"""
        val to = """{"options":[{"price":200,"name":"A"}]}"""

        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = from1, toPayload = to,
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = from2, toPayload = to,
            emptySet(), emptyMap()
        )

        val path1 = cs1.changedPaths.find { it.path == "/options/0/price" }
        val path2 = cs2.changedPaths.find { it.path == "/options/0/price" }
        path1 shouldNotBe null
        path2 shouldNotBe null
        path1!!.valueHash shouldBe path2!!.valueHash
    }

    "valueHash - primitive number 동일 값 → 동일 해시" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":100}""",
            toPayload = """{"x":999}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":100}""",
            toPayload = """{"x":999}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "valueHash - boolean 변경 (canonical 기반 결정적)" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"active":true}""",
            toPayload = """{"active":false}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"active":true}""",
            toPayload = """{"active":false}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths shouldHaveSize 1
        cs1.changedPaths.first().path shouldBe "/active"
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
        cs1.changedPaths.first().valueHash.startsWith("sha256:") shouldBe true
        cs1.changedPaths.first().valueHash.length shouldBe 71
    }

    "valueHash - null 추가 (결정적)" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"opt":"value"}""",
            toPayload = """{"opt":null}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"opt":"value"}""",
            toPayload = """{"opt":null}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths shouldHaveSize 1
        cs1.changedPaths.first().path shouldBe "/opt"
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "키 순서만 다른 동일 payload → changedPaths 빈 배열 (diff는 canonical 기반)" {
        // changeType은 문자열 동등으로 UPDATE이지만, diff 결과는 canonical 비교로 동일
        val p1 = """{"a":1,"b":2,"c":3}"""
        val p2 = """{"c":3,"a":1,"b":2}"""

        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = p1,
            toPayload = p2,
            emptySet(), emptyMap()
        )

        // diff가 canonical 기반이므로 의미적으로 동일하면 changedPaths 빈 배열
        cs.changedPaths shouldHaveSize 0
    }

    "changeSetId 결정성 - 동일 입력 → 동일 ID" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":1}""",
            toPayload = """{"x":2}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":1}""",
            toPayload = """{"x":2}""",
            emptySet(), emptyMap()
        )
        cs1.changeSetId shouldBe cs2.changeSetId
    }

    "changedPaths 정렬 - path 알파벳순" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":1,"b":2,"c":3}""",
            toPayload = """{"a":10,"b":20,"c":30}""",
            emptySet(), emptyMap()
        )
        val paths = cs.changedPaths.map { it.path }
        paths shouldBe paths.sorted()
    }

    "Edge - 빈 객체에 필드 추가 (leaf path)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"empty":{}}""",
            toPayload = """{"empty":{"x":1}}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/empty/x"  // leaf-level path
    }

    "Edge - 빈 배열에 요소 추가 (index path)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"items":[]}""",
            toPayload = """{"items":[{"id":1}]}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/items/0"  // index 기반
    }

    "Edge - DELETE 타입 payloadHash (빈 문자열 해시)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"deleted":true}""",
            toPayload = null,
            emptySet(), emptyMap()
        )
        cs.changeType shouldBe ChangeType.DELETE
        cs.payloadHash.startsWith("sha256:") shouldBe true
        cs.payloadHash.length shouldBe 71  // sha256("") = 64 hex
    }

    "Corner - 배열 순서 다르면 changedPaths 있음 (배열은 순서 의미 있음)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"ids":[1,2,3]}""",
            toPayload = """{"ids":[3,2,1]}""",
            emptySet(), emptyMap()
        )
        // index 0: 1→3, index 1: 2→2(동일), index 2: 3→1 → 2개 변경
        cs.changedPaths shouldHaveSize 2
        cs.changedPaths.map { it.path }.toSet() shouldBe setOf("/ids/0", "/ids/2")
    }

    "Corner - Unicode/한글 필드 valueHash 결정성" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"name":"상품A"}""",
            toPayload = """{"name":"상품B"}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"name":"상품A"}""",
            toPayload = """{"name":"상품B"}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "Corner - valueHash 형식 sha256: + 64 hex" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":1}""",
            toPayload = """{"x":2}""",
            emptySet(), emptyMap()
        )
        val vh = cs.changedPaths.first().valueHash
        vh.startsWith("sha256:") shouldBe true
        vh.substring(7).length shouldBe 64
        vh.substring(7).all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
    }

    // ==================== 추가 엣지/코너 케이스 ====================

    "Edge - 빈 문자열 변경" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"msg":""}""",
            toPayload = """{"msg":"hello"}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/msg"
    }

    "Edge - 숫자 0, 음수" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":0,"b":-1}""",
            toPayload = """{"a":1,"b":0}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 2
        cs.changedPaths.map { it.path }.toSet() shouldBe setOf("/a", "/b")
    }

    "Edge - 깊은 중첩 (3단계)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":{"b":{"c":1}}}""",
            toPayload = """{"a":{"b":{"c":2}}}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/a/b/c"
    }

    "Edge - nodeType 변경 (객체→배열)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"x":{"a":1}}""",
            toPayload = """{"x":[1,2,3]}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/x"
    }

    "Edge - 필드 삭제 (객체→null)" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":1,"b":2}""",
            toPayload = """{"a":1}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":1,"b":2}""",
            toPayload = """{"a":1}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths shouldHaveSize 1
        cs1.changedPaths.first().path shouldBe "/b"
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "Edge - 배열 요소 삭제 (길이 축소)" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"items":[1,2,3]}""",
            toPayload = """{"items":[1,2]}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path shouldBe "/items/2"
    }

    "Edge - NO_CHANGE 동일 문자열 참조" {
        val payload = """{"same":true}"""
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = payload,
            toPayload = payload,
            emptySet(), emptyMap()
        )
        cs.changeType shouldBe ChangeType.NO_CHANGE
        cs.changedPaths shouldHaveSize 0
    }

    "Corner - 이모지/특수문자 valueHash 결정성" {
        val cs1 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"emoji":"🎉"}""",
            toPayload = """{"emoji":"🔥"}""",
            emptySet(), emptyMap()
        )
        val cs2 = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"emoji":"🎉"}""",
            toPayload = """{"emoji":"🔥"}""",
            emptySet(), emptyMap()
        )
        cs1.changedPaths.first().valueHash shouldBe cs2.changedPaths.first().valueHash
    }

    "Corner - RFC6901 슬래시 포함 필드명" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a/b":"old"}""",
            toPayload = """{"a/b":"new"}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 1
        cs.changedPaths.first().path.contains("~1") shouldBe true
    }

    "Corner - 다중 필드 동시 변경" {
        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = """{"a":1,"b":2,"c":3,"d":4}""",
            toPayload = """{"a":10,"b":20,"c":3,"d":40}""",
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 3
        cs.changedPaths.map { it.path }.toSet() shouldBe setOf("/a", "/b", "/d")
    }

    "Corner - 공백/줄바꿈 다른 JSON → canonical 동일" {
        val p1 = """{"a":1,"b":2}"""
        val p2 = """{ "a": 1, "b": 2 }"""

        val cs = builder.build(
            tenantId, "PRODUCT", entityKey,
            fromVersion = 1, toVersion = 2,
            fromPayload = p1,
            toPayload = p2,
            emptySet(), emptyMap()
        )
        cs.changedPaths shouldHaveSize 0
    }
})
