package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.contracts.domain.IndexSpec
import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexBuilder
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RFC-IMPL-010 Phase D-9: InvertedIndexBuilder 전수 테스트
 *
 * 7개 엣지/코너 케이스 전수:
 * 1. 단일 필드 인덱스 생성
 * 2. 배열 필드 인덱스 (여러 InvertedIndexEntry)
 * 3. 동일 Slice → 동일 Index 집합 (결정성)
 * 4. 빈 값 → 인덱스 생성 안함
 * 5. null 값 → 인덱스 생성 안함
 * 6. indexValue canonicalization (trim, lowercase)
 * 7. tombstone slice → tombstone=true index
 */
class InvertedIndexBuilderTest {
    private val builder = InvertedIndexBuilder()

    @Test
    fun `단일 필드 인덱스 생성 - brand_id 필드`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"BR001","name":"Product A"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(1, indexes.size)
        val index = indexes[0]
        assertEquals(TenantId("t1"), index.tenantId)
        assertEquals(EntityKey("product#P001"), index.refEntityKey)
        assertEquals(EntityKey("product#P001"), index.targetEntityKey)
        assertEquals("brand", index.indexType)
        assertEquals("br001", index.indexValue) // canonicalized
        assertEquals(SliceType.CORE, index.sliceType)
        assertEquals("hash123", index.sliceHash)
        assertFalse(index.tombstone)
    }

    @Test
    fun `배열 필드 인덱스 - category_ids 배열 → 여러 InvertedIndexEntry`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"category_ids":["CAT1","CAT2","CAT3"]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "category", selector = "$.category_ids[*]"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(3, indexes.size)
        val indexValues = indexes.map { it.indexValue }.sorted()
        assertEquals(listOf("cat1", "cat2", "cat3"), indexValues)

        indexes.forEach { index ->
            assertEquals("category", index.indexType)
            assertEquals(EntityKey("product#P001"), index.targetEntityKey)
        }
    }

    @Test
    fun `동일 Slice → 동일 Index 집합 (결정성 보장)`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"BR001","category_ids":["CAT1","CAT2"]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
            IndexSpec(type = "category", selector = "$.category_ids[*]"),
        )

        // When
        val indexes1 = builder.build(slice, indexSpecs)
        val indexes2 = builder.build(slice, indexSpecs)

        // Then
        assertEquals(indexes1.size, indexes2.size)
        assertEquals(3, indexes1.size) // 1 brand + 2 categories

        val sorted1 = indexes1.sortedBy { it.indexType + it.indexValue }
        val sorted2 = indexes2.sortedBy { it.indexType + it.indexValue }

        sorted1.zip(sorted2).forEach { (idx1, idx2) ->
            assertEquals(idx1.indexType, idx2.indexType)
            assertEquals(idx1.indexValue, idx2.indexValue)
            assertEquals(idx1.sliceHash, idx2.sliceHash)
        }
    }

    @Test
    fun `빈 문자열 값 → 인덱스 생성 안함`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"","name":"Product"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(0, indexes.size)
    }

    @Test
    fun `null 값 → 인덱스 생성 안함`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":null,"name":"Product"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(0, indexes.size)
    }

    @Test
    fun `indexValue canonicalization - trim과 lowercase 적용`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"  BR001  ","category_ids":["  CAT1  ","CAT2  "]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
            IndexSpec(type = "category", selector = "$.category_ids[*]"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(3, indexes.size)

        val brandIndex = indexes.find { it.indexType == "brand" }!!
        assertEquals("br001", brandIndex.indexValue)

        val categoryValues = indexes.filter { it.indexType == "category" }
            .map { it.indexValue }
            .sorted()
        assertEquals(listOf("cat1", "cat2"), categoryValues)
    }

    @Test
    fun `tombstone slice → tombstone=true index`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 2L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"BR001"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
            tombstone = Tombstone.create(version = 2L, reason = DeleteReason.USER_DELETE),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(1, indexes.size)
        val index = indexes[0]
        assertTrue(index.tombstone)
        assertEquals("br001", index.indexValue)
    }

    @Test
    fun `중첩 필드 인덱스 - product_name 추출`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"product":{"name":"Widget","id":"W001"}}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "product_name", selector = "$.product.name"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(1, indexes.size)
        assertEquals("product_name", indexes[0].indexType)
        assertEquals("widget", indexes[0].indexValue)
    }

    @Test
    fun `배열 내부 중첩 필드 - items_star_name 추출`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("order#O001"),
            version = 1L,
            sliceType = SliceType.INVENTORY,
            data = """{"items":[{"name":"Item1","qty":2},{"name":"Item2","qty":1}]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "item_name", selector = "$.items[*].name"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(2, indexes.size)
        val names = indexes.map { it.indexValue }.sorted()
        assertEquals(listOf("item1", "item2"), names)
    }

    @Test
    fun `존재하지 않는 필드 → 인덱스 생성 안함`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"name":"Product"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(0, indexes.size)
    }

    @Test
    fun `빈 JSON → 인덱스 생성 안함`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = "{}",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(0, indexes.size)
    }

    @Test
    fun `잘못된 JSON → 인덱스 생성 안함`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = "not-a-json",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(0, indexes.size)
    }

    @Test
    fun `여러 IndexSpec → 모든 인덱스 생성`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand_id":"BR001","category_ids":["CAT1","CAT2"],"supplier_id":"SUP001"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brand_id"),
            IndexSpec(type = "category", selector = "$.category_ids[*]"),
            IndexSpec(type = "supplier", selector = "$.supplier_id"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then
        assertEquals(4, indexes.size) // 1 brand + 2 categories + 1 supplier

        val byType = indexes.groupBy { it.indexType }
        assertEquals(1, byType["brand"]?.size)
        assertEquals(2, byType["category"]?.size)
        assertEquals(1, byType["supplier"]?.size)
    }

    // ===== RFC-IMPL-013: references 필드 테스트 =====

    @Test
    fun `references 필드가 있으면 역방향 인덱스도 생성`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BR001","name":"Product A"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",  // FK 참조 → 역방향 인덱스 자동 생성
                maxFanout = 10000,
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 정방향 + 역방향 = 2개
        assertEquals(2, indexes.size)

        // 정방향 인덱스 확인
        val forwardIndex = indexes.find { it.indexType == "brand" }!!
        assertEquals("br001", forwardIndex.indexValue)
        assertEquals(EntityKey("PRODUCT#tenant1#P001"), forwardIndex.targetEntityKey)
        assertEquals(EntityKey("PRODUCT#tenant1#P001"), forwardIndex.refEntityKey)

        // 역방향 인덱스 확인
        val reverseIndex = indexes.find { it.indexType == "product_by_brand" }!!
        assertEquals("br001", reverseIndex.indexValue)
        assertEquals(EntityKey("PRODUCT#tenant1#P001"), reverseIndex.targetEntityKey)
        assertEquals(EntityKey("BRAND#tenant1#br001"), reverseIndex.refEntityKey) // FK 엔티티 키
    }

    @Test
    fun `references가 없으면 정방향 인덱스만 생성`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"tag":"summer"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "tag",
                selector = "$.tag",
                references = null,  // FK 참조 없음 → 정방향만
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 정방향만 1개
        assertEquals(1, indexes.size)
        assertEquals("tag", indexes[0].indexType)
    }

    @Test
    fun `배열 필드 + references → 각 값마다 정방향+역방향 생성`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"categoryIds":["CAT1","CAT2"]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "category",
                selector = "$.categoryIds[*]",
                references = "CATEGORY",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 2 값 × (정방향 + 역방향) = 4개
        assertEquals(4, indexes.size)

        val forwardIndexes = indexes.filter { it.indexType == "category" }
        assertEquals(2, forwardIndexes.size)

        val reverseIndexes = indexes.filter { it.indexType == "product_by_category" }
        assertEquals(2, reverseIndexes.size)

        // 역방향 인덱스의 refEntityKey 확인
        val refEntityKeys = reverseIndexes.map { it.refEntityKey.value }.sorted()
        assertEquals(listOf("CATEGORY#tenant1#cat1", "CATEGORY#tenant1#cat2"), refEntityKeys)
    }

    @Test
    fun `EntityKey 전체 형식 파싱 - selector가 전체 EntityKey 반환 시 entityId 추출`() {
        // Given: selector가 전체 EntityKey를 반환하는 경우 (예: $.brandId = "BRAND#tenant#BR001")
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BRAND#tenant1#BR001"}""",  // 전체 EntityKey 형식
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 역방향 인덱스의 indexValue는 entityId만 저장되어야 함
        val reverseIndex = indexes.find { it.indexType == "product_by_brand" }!!
        // canonicalizeIndexValue가 먼저 적용되어 lowercase됨
        assertEquals(EntityKey("brand#tenant1#br001"), reverseIndex.refEntityKey)  // 전체 EntityKey (lowercase)
        assertEquals("br001", reverseIndex.indexValue)  // entityId만 (lowercase)
        
        // 정방향 인덱스는 전체 값 저장
        val forwardIndex = indexes.find { it.indexType == "brand" }!!
        assertEquals("brand#tenant1#br001", forwardIndex.indexValue)  // 전체 값 (lowercase)
    }

    @Test
    fun `엣지 케이스 - 빈 entityId는 역방향 인덱스 생성 안 함`() {
        // Given: entityId만 빈 문자열인 경우 (entityId 필드가 빈 문자열)
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":""}""",  // 빈 문자열 (entityId만)
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 빈 값은 isNullOrBlank() 체크로 인해 인덱스 생성 안 됨
        assertEquals(0, indexes.size, "빈 값은 인덱스 생성 안 됨")
    }
    
    @Test
    fun `엣지 케이스 - EntityKey 형식에서 빈 entityId는 역방향 인덱스 생성 안 함`() {
        // Given: EntityKey 형식이지만 entityId가 빈 문자열인 경우
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BRAND#tenant1#"}""",  // 마지막 # 뒤에 아무것도 없음
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 정방향 인덱스는 생성되지만 역방향 인덱스는 생성 안 됨 (빈 entityId)
        // canonicalizeIndexValue("BRAND#tenant1#") → "brand#tenant1#" (trim은 앞뒤 공백만 제거)
        // split("#") → ["brand", "tenant1", ""]
        // parts[2]가 빈 문자열이므로 역방향 인덱스 생성 안 됨
        val forwardIndexes = indexes.filter { it.indexType == "brand" }
        val reverseIndexes = indexes.filter { it.indexType == "product_by_brand" }
        
        // 정방향 인덱스는 전체 값 저장
        assertEquals(1, forwardIndexes.size, "정방향 인덱스는 생성되어야 함")
        assertEquals(0, reverseIndexes.size, "역방향 인덱스는 빈 entityId로 인해 생성 안 됨")
    }

    @Test
    fun `에러 케이스 - 잘못된 JSON 형식은 빈 리스트 반환`() {
        // Given: 잘못된 JSON 형식
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId": invalid json}""",  // 잘못된 JSON
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: JSON 파싱 실패 시 빈 리스트 반환
        assertEquals(0, indexes.size)
    }

    @Test
    fun `에러 케이스 - 존재하지 않는 selector path는 빈 리스트 반환`() {
        // Given: selector path가 존재하지 않음
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"name":"Product A"}""",  // brandId 필드 없음
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",  // 존재하지 않는 필드
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 필드가 없으면 빈 리스트 반환
        assertEquals(0, indexes.size)
    }

    @Test
    fun `에러 케이스 - 배열이 아닌 값에 배열 패턴 사용 시 빈 리스트 반환`() {
        // Given: 배열이 아닌 값에 [*] 패턴 사용
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BR001"}""",  // 배열이 아닌 문자열
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId[*]",  // 배열 패턴이지만 실제로는 문자열
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 배열이 아니면 빈 리스트 반환
        assertEquals(0, indexes.size)
    }

    @Test
    fun `성능 케이스 - 대용량 배열 처리`() {
        // Given: 큰 배열 (100개 항목)
        val items = (1..100).joinToString(",") { """{"name":"Item $it"}""" }
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"items":[$items]}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "item_name",
                selector = "$.items[*].name",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 모든 항목에 대해 인덱스 생성
        assertEquals(100, indexes.size)
        indexes.forEach { index ->
            assertEquals("item_name", index.indexType)
            assertTrue(index.indexValue.startsWith("item"))
        }
    }

    @Test
    fun `엣지 케이스 - Object 타입 값은 인덱스 생성 안 함`() {
        // Given: selector가 Object를 가리킴
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brand":{"id":"BR001","name":"Brand Name"}}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brand",  // Object 전체
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: Object는 인덱스 생성 안 함
        assertEquals(0, indexes.size)
    }

    @Test
    fun `엣지 케이스 - tenantId 불일치 시 경고하지만 역방향 인덱스는 생성`() {
        // Given: refEntityKey의 tenantId가 slice.tenantId와 다른 경우
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BRAND#tenant2#BR001"}""",  // 다른 tenantId
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 역방향 인덱스는 생성되지만 tenantId 불일치 (경고 로그는 실제로는 남음)
        val reverseIndexes = indexes.filter { it.indexType == "product_by_brand" }
        assertEquals(1, reverseIndexes.size, "역방향 인덱스는 생성되어야 함 (cross-tenant 참조 허용)")
        
        // refEntityKey는 원본 EntityKey 사용 (tenant2 포함)
        assertEquals(EntityKey("brand#tenant2#br001"), reverseIndexes[0].refEntityKey)
        // 하지만 InvertedIndexEntry의 tenantId는 slice.tenantId 사용
        assertEquals(TenantId("tenant1"), reverseIndexes[0].tenantId)
    }

    @Test
    fun `엣지 케이스 - EntityKey에 해시가 4개 이상 있어도 parts 2번째 사용`() {
        // Given: 비표준 EntityKey 형식 (방어적 코딩)
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BRAND#tenant1#BR001#extra#parts"}""",  // 4개 이상의 #
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: parts[2]만 사용 (BR001)
        val reverseIndex = indexes.find { it.indexType == "product_by_brand" }!!
        assertEquals("br001", reverseIndex.indexValue)  // parts[2]만 추출
    }

    @Test
    fun `엣지 케이스 - EntityKey에 해시가 2개 미만이면 전체 값을 entityId로 사용`() {
        // Given: 비표준 EntityKey 형식 (방어적 코딩)
        val slice = SliceRecord(
            tenantId = TenantId("tenant1"),
            entityKey = EntityKey("PRODUCT#tenant1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BRAND#tenant1"}""",  // #이 1개만
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(
                type = "brand",
                selector = "$.brandId",
                references = "BRAND",
            ),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: 전체 값을 entityId로 사용 (방어적 코딩)
        val reverseIndex = indexes.find { it.indexType == "product_by_brand" }!!
        assertEquals("brand#tenant1", reverseIndex.indexValue.lowercase())  // 전체 값 사용
    }

    @Test
    fun `역방향 인덱스 indexType 형식 확인`() {
        // Given
        val slice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("PRODUCT#t1#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"brandId":"BR001"}""",
            hash = "hash123",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "brand", selector = "$.brandId", references = "BRAND"),
        )

        // When
        val indexes = builder.build(slice, indexSpecs)

        // Then: indexType = "{entityType}_by_{references}" (소문자)
        val reverseIndex = indexes.find { it.indexType.contains("_by_") }!!
        assertEquals("product_by_brand", reverseIndex.indexType)
    }

    @Test
    fun `엣지 케이스 - 동일 indexValue가 다른 sliceType에서 생성 → 별도 엔트리`() {
        // Given: 2개의 슬라이스가 같은 categoryId 필드를 포함
        val coreSlice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CORE,
            data = """{"title":"Product A","categoryId":"CAT123"}""",
            hash = "hash-core",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val categorySlice = SliceRecord(
            tenantId = TenantId("t1"),
            entityKey = EntityKey("product#P001"),
            version = 1L,
            sliceType = SliceType.CATEGORY,
            data = """{"categoryId":"CAT123","categoryPath":"/root/cat123"}""",
            hash = "hash-category",
            ruleSetId = "ruleset-v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
        )

        val indexSpecs = listOf(
            IndexSpec(type = "category", selector = "$.categoryId"),
        )

        // When: 각 슬라이스에서 인덱스 생성
        val coreIndexes = builder.build(coreSlice, indexSpecs)
        val categoryIndexes = builder.build(categorySlice, indexSpecs)

        // Then: 동일한 indexValue("cat123")지만 sliceType이 다르므로 별도 엔트리
        assertEquals(1, coreIndexes.size)
        assertEquals(1, categoryIndexes.size)

        val coreIndex = coreIndexes[0]
        val categoryIndex = categoryIndexes[0]

        // 동일한 indexValue
        assertEquals("cat123", coreIndex.indexValue)
        assertEquals("cat123", categoryIndex.indexValue)

        // 다른 sliceType
        assertEquals(SliceType.CORE, coreIndex.sliceType)
        assertEquals(SliceType.CATEGORY, categoryIndex.sliceType)

        // 다른 sliceHash
        assertEquals("hash-core", coreIndex.sliceHash)
        assertEquals("hash-category", categoryIndex.sliceHash)

        // 동일한 targetEntityKey
        assertEquals(EntityKey("product#P001"), coreIndex.targetEntityKey)
        assertEquals(EntityKey("product#P001"), categoryIndex.targetEntityKey)
    }
}
