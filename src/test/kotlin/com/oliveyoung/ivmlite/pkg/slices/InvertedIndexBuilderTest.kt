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
