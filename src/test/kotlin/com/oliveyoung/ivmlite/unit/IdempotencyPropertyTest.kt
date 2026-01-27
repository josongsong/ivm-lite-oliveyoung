package com.oliveyoung.ivmlite.unit

import com.oliveyoung.ivmlite.pkg.contracts.domain.IndexSpec
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexBuilder
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * SOTA 멱등성(Idempotency) 속성 테스트
 * 
 * L12 원칙:
 * - f(f(x)) = f(x) (멱등성)
 * - 동일 입력 → 동일 결과 (결정성)
 * - 재실행 안전 (at-least-once delivery 호환)
 * 
 * 업계 기준: 분산 시스템에서 메시지 중복 처리 안전성
 */
class IdempotencyPropertyTest : StringSpec({

    val tenantId = TenantId("tenant-test")
    val entityKey = EntityKey("product:12345")

    "RawDataRecord: 동일 입력 → 동일 payloadHash" {
        val payload = """{"name":"Test Product","price":1000}"""
        val schemaId = "schema.product.v1"
        val schemaVersion = SemVer.parse("1.0.0")
        
        val canonical = CanonicalJson.canonicalize(payload)
        val hashInput = canonical + "|" + schemaId + "|" + schemaVersion.toString()
        val hash1 = "sha256:" + Hashing.sha256Hex(hashInput)
        val hash2 = "sha256:" + Hashing.sha256Hex(hashInput)
        
        hash1 shouldBe hash2
        
        val record1 = RawDataRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payload = canonical,
            payloadHash = hash1,
        )
        
        val record2 = RawDataRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payload = canonical,
            payloadHash = hash2,
        )
        
        record1.payloadHash shouldBe record2.payloadHash
    }
    
    "SliceRecord: 동일 data → 동일 hash (슬라이싱 멱등성)" {
        val data = """{"name":"Test","brand_id":"BR001"}"""
        val canonical = CanonicalJson.canonicalize(data)
        val hash = Hashing.sha256Tagged(canonical)
        
        val slice1 = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            sliceType = SliceType.CORE,
            data = canonical,
            hash = hash,
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )
        
        val slice2 = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            sliceType = SliceType.CORE,
            data = canonical,
            hash = hash,
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )
        
        slice1.hash shouldBe slice2.hash
        slice1 shouldBe slice2
    }
    
    "SliceRecord: 키 순서 다른 JSON도 canonical 후 동일 hash" {
        val data1 = """{"name":"Test","brand_id":"BR001"}"""
        val data2 = """{"brand_id":"BR001","name":"Test"}"""
        
        val canonical1 = CanonicalJson.canonicalize(data1)
        val canonical2 = CanonicalJson.canonicalize(data2)
        
        canonical1 shouldBe canonical2
        
        val hash1 = Hashing.sha256Tagged(canonical1)
        val hash2 = Hashing.sha256Tagged(canonical2)
        
        hash1 shouldBe hash2
    }
    
    "InvertedIndexBuilder: 동일 Slice → 동일 Indexes" {
        val builder = InvertedIndexBuilder()
        val data = """{"brand_id":"BR001","category_ids":["CAT1","CAT2"]}"""
        val canonical = CanonicalJson.canonicalize(data)
        
        val slice = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            sliceType = SliceType.CORE,
            data = canonical,
            hash = Hashing.sha256Tagged(canonical),
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )
        
        val indexSpecs = listOf(
            IndexSpec(type = "BRAND_ID", selector = "$.brand_id"),
            IndexSpec(type = "CATEGORY_ID", selector = "$.category_ids[*]"),
        )
        
        val indexes1 = builder.build(slice, indexSpecs)
        val indexes2 = builder.build(slice, indexSpecs)
        
        indexes1 shouldHaveSize indexes2.size
        indexes1.zip(indexes2).forEach { (i1, i2) ->
            i1.indexType shouldBe i2.indexType
            i1.indexValue shouldBe i2.indexValue
            i1.sliceHash shouldBe i2.sliceHash
        }
    }
    
    "InvertedIndex: indexValue는 정규화됨 (trim + lowercase)" {
        val builder = InvertedIndexBuilder()
        
        // 대소문자, 공백 다른 입력
        val data1 = """{"brand_id":"  BR001  "}"""
        val data2 = """{"brand_id":"br001"}"""
        
        val slice1 = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            sliceType = SliceType.CORE,
            data = data1,
            hash = Hashing.sha256Tagged(data1),
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )
        
        val slice2 = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1,
            sliceType = SliceType.CORE,
            data = data2,
            hash = Hashing.sha256Tagged(data2),
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )
        
        val indexSpecs = listOf(IndexSpec(type = "BRAND_ID", selector = "$.brand_id"))
        
        val indexes1 = builder.build(slice1, indexSpecs)
        val indexes2 = builder.build(slice2, indexSpecs)
        
        // 정규화 후 동일한 indexValue
        indexes1.first().indexValue shouldBe "br001"
        indexes2.first().indexValue shouldBe "br001"
        indexes1.first().indexValue shouldBe indexes2.first().indexValue
    }
    
    "Hash 태깅: prefix로 알고리즘 식별 가능" {
        val input = "test-input"
        val tagged = Hashing.sha256Tagged(input)
        
        tagged.startsWith("sha256:") shouldBe true
        tagged.length shouldBe 7 + 64  // "sha256:" + 64 hex chars
    }
    
    "재처리 시나리오: 동일 RawData 2번 처리해도 동일 결과" {
        val payload = """{"product_id":"P001","name":"상품명","price":10000}"""
        val schemaId = "schema.product.v1"
        val schemaVersion = SemVer.parse("1.0.0")
        
        // 첫 번째 처리
        val canonical1 = CanonicalJson.canonicalize(payload)
        val hashInput1 = canonical1 + "|" + schemaId + "|" + schemaVersion.toString()
        val payloadHash1 = "sha256:" + Hashing.sha256Hex(hashInput1)
        
        // 두 번째 처리 (동일 입력)
        val canonical2 = CanonicalJson.canonicalize(payload)
        val hashInput2 = canonical2 + "|" + schemaId + "|" + schemaVersion.toString()
        val payloadHash2 = "sha256:" + Hashing.sha256Hex(hashInput2)
        
        // 결과 동일 (멱등성)
        canonical1 shouldBe canonical2
        payloadHash1 shouldBe payloadHash2
    }
    
    "버전 충돌 감지: 동일 version, 다른 payload → 다른 hash" {
        val payload1 = """{"name":"Original"}"""
        val payload2 = """{"name":"Modified"}"""
        
        val hash1 = Hashing.sha256Tagged(CanonicalJson.canonicalize(payload1))
        val hash2 = Hashing.sha256Tagged(CanonicalJson.canonicalize(payload2))
        
        // 다른 payload → 다른 hash → IdempotencyViolation 감지 가능
        (hash1 != hash2) shouldBe true
    }
    
    "Edge Case: 빈 payload 처리" {
        val emptyPayload = "{}"
        val canonical = CanonicalJson.canonicalize(emptyPayload)
        val hash = Hashing.sha256Tagged(canonical)
        
        canonical shouldBe "{}"
        hash.startsWith("sha256:") shouldBe true
    }
    
    "Edge Case: 배열만 있는 payload" {
        val arrayPayload = """[1,2,3]"""
        val canonical = CanonicalJson.canonicalize(arrayPayload)
        val hash = Hashing.sha256Tagged(canonical)
        
        canonical shouldBe "[1,2,3]"
        hash.startsWith("sha256:") shouldBe true
    }
    
    "Edge Case: 깊은 중첩 구조" {
        val deepPayload = """{"l1":{"l2":{"l3":{"l4":{"l5":"value"}}}}}"""
        val canonical = CanonicalJson.canonicalize(deepPayload)
        
        // 재적용해도 동일
        val canonical2 = CanonicalJson.canonicalize(canonical)
        canonical shouldBe canonical2
    }
})
