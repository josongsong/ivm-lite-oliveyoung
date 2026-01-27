package com.oliveyoung.ivmlite.unit

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * SOTA 결정성(Determinism) 속성 테스트
 * 
 * L12 원칙:
 * - 동일 입력 → 동일 출력 (결정성)
 * - 재실행해도 동일 결과 (멱등성)
 * - 순서 무관 (교환법칙)
 * 
 * 학계 기준: Property-Based Testing으로 edge case 커버
 */
class DeterminismPropertyTest : StringSpec({

    "CanonicalJson: 키 순서가 달라도 동일한 canonical 출력" {
        val json1 = """{"b":2,"a":1,"c":3}"""
        val json2 = """{"a":1,"b":2,"c":3}"""
        val json3 = """{"c":3,"a":1,"b":2}"""
        
        val c1 = CanonicalJson.canonicalize(json1)
        val c2 = CanonicalJson.canonicalize(json2)
        val c3 = CanonicalJson.canonicalize(json3)
        
        c1 shouldBe c2
        c2 shouldBe c3
        c1 shouldBe """{"a":1,"b":2,"c":3}"""
    }
    
    "CanonicalJson: 중첩 객체도 키 정렬" {
        val json1 = """{"outer":{"z":1,"a":2},"inner":{"b":3,"a":4}}"""
        val json2 = """{"inner":{"a":4,"b":3},"outer":{"a":2,"z":1}}"""
        
        val c1 = CanonicalJson.canonicalize(json1)
        val c2 = CanonicalJson.canonicalize(json2)
        
        c1 shouldBe c2
    }
    
    "CanonicalJson: 배열 순서는 유지" {
        val json = """{"items":[3,1,2]}"""
        val canonical = CanonicalJson.canonicalize(json)
        
        // 배열 순서는 변경하지 않음 (의미적으로 다름)
        canonical shouldBe """{"items":[3,1,2]}"""
    }
    
    "CanonicalJson: 특수문자 포함 문자열 처리" {
        val json = """{"msg":"Hello\nWorld\t!"}"""
        val canonical = CanonicalJson.canonicalize(json)
        
        // 재파싱 가능해야 함
        val reparsed = CanonicalJson.canonicalize(canonical)
        canonical shouldBe reparsed
    }
    
    "Hashing: 동일 입력 → 동일 해시" {
        val input = "test-input-string"
        
        val hash1 = Hashing.sha256Hex(input)
        val hash2 = Hashing.sha256Hex(input)
        val hash3 = Hashing.sha256Hex(input)
        
        hash1 shouldBe hash2
        hash2 shouldBe hash3
    }
    
    "Hashing: 다른 입력 → 다른 해시" {
        val hash1 = Hashing.sha256Hex("input-a")
        val hash2 = Hashing.sha256Hex("input-b")
        
        hash1 shouldNotBe hash2
    }
    
    "Hashing: 빈 문자열도 결정적" {
        val hash1 = Hashing.sha256Hex("")
        val hash2 = Hashing.sha256Hex("")
        
        hash1 shouldBe hash2
        hash1.length shouldBe 64  // SHA-256 = 64 hex chars
    }
    
    "ChangeSetBuilder: 동일 입력 → 동일 changeSetId (결정성)" {
        val builder = ChangeSetBuilder()
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product:12345")
        
        val cs1 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"name":"old"}""",
            toPayload = """{"name":"new"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        val cs2 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"name":"old"}""",
            toPayload = """{"name":"new"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs1.changeSetId shouldBe cs2.changeSetId
        cs1.changedPaths shouldBe cs2.changedPaths
        cs1.payloadHash shouldBe cs2.payloadHash
    }
    
    "ChangeSetBuilder: 다른 버전 → 다른 changeSetId" {
        val builder = ChangeSetBuilder()
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product:12345")
        
        val cs1 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = null,
            toPayload = """{"name":"new"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        val cs2 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 2,
            toVersion = 3,
            fromPayload = null,
            toPayload = """{"name":"new"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs1.changeSetId shouldNotBe cs2.changeSetId
    }
    
    "ChangeSetBuilder: JSON diff 결정성 - 키 순서 무관" {
        val builder = ChangeSetBuilder()
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product:12345")
        
        // 같은 내용, 다른 키 순서
        val cs1 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"a":1,"b":2}""",
            toPayload = """{"a":1,"b":3}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        val cs2 = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"b":2,"a":1}""",  // 키 순서 다름
            toPayload = """{"b":3,"a":1}""",    // 키 순서 다름
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        // changeSetId는 동일 (입력 파라미터 기반)
        cs1.changeSetId shouldBe cs2.changeSetId
        // changedPaths는 정렬되어 있어야 함
        cs1.changedPaths.map { it.path } shouldBe cs1.changedPaths.map { it.path }.sorted()
    }
    
    "Property: Hashing은 입력 길이와 무관하게 고정 길이 출력".config(enabled = true) {
        checkAll(Arb.string(0..1000)) { input ->
            val hash = Hashing.sha256Hex(input)
            hash.length shouldBe 64
        }
    }
    
    "Property: CanonicalJson 재적용해도 동일 (멱등성)".config(enabled = true) {
        val testCases = listOf(
            """{"a":1}""",
            """{"z":1,"a":2}""",
            """{"nested":{"b":1,"a":2}}""",
            """[1,2,3]""",
            """{"arr":[{"z":1},{"a":2}]}""",
        )
        
        testCases.forEach { json ->
            val c1 = CanonicalJson.canonicalize(json)
            val c2 = CanonicalJson.canonicalize(c1)
            val c3 = CanonicalJson.canonicalize(c2)
            
            c1 shouldBe c2
            c2 shouldBe c3
        }
    }
    
    "Edge Case: 빈 객체/배열 처리" {
        CanonicalJson.canonicalize("{}") shouldBe "{}"
        CanonicalJson.canonicalize("[]") shouldBe "[]"
        CanonicalJson.canonicalize("""{"empty":{}}""") shouldBe """{"empty":{}}"""
    }
    
    "Edge Case: Unicode 문자열 처리" {
        val json = """{"name":"한글테스트","emoji":"🚀"}"""
        val c1 = CanonicalJson.canonicalize(json)
        val c2 = CanonicalJson.canonicalize(c1)
        
        c1 shouldBe c2
        c1.contains("한글테스트") shouldBe true
    }
    
    "Edge Case: 숫자 정밀도 (JSON 스펙)" {
        // JSON 스펙: 숫자는 IEEE 754 double로 표현
        val json = """{"int":123456789012345678,"float":1.23456789012345678}"""
        val c1 = CanonicalJson.canonicalize(json)
        val c2 = CanonicalJson.canonicalize(c1)
        
        c1 shouldBe c2
    }
    
    "Edge Case: null 값 처리" {
        val json = """{"a":null,"b":1}"""
        val canonical = CanonicalJson.canonicalize(json)
        
        canonical shouldBe """{"a":null,"b":1}"""
    }
    
    "Edge Case: boolean 값 처리" {
        val json = """{"t":true,"f":false}"""
        val canonical = CanonicalJson.canonicalize(json)
        
        canonical shouldBe """{"f":false,"t":true}"""  // 키 정렬됨
    }
})
