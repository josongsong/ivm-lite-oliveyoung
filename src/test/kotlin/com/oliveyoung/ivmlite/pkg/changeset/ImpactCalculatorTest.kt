package com.oliveyoung.ivmlite.pkg.changeset

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangedPath
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC-IMPL-010 Phase D-7: ImpactCalculator 테스트
 *
 * ChangeSet + RuleSet → ImpactMap 계산
 * fail-closed: 매핑 안 된 변경 경로 → 에러
 */
class ImpactCalculatorTest {

    private val calculator = ImpactCalculator()

    @Test
    fun `변경 경로가 impactMap에 매핑되면 정확한 슬라이스 반환`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/price", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name", "/description"),
                SliceType.PRICE to listOf("/price", "/discount"),
                SliceType.INVENTORY to listOf("/stock", "/warehouse")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.containsKey("CORE"))
        assertTrue(result.containsKey("PRICE"))
    }

    @Test
    fun `변경 경로가 impactMap에 없으면 fail-closed로 에러`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/unknownField", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name", "/description")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When & Then
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }

        assertTrue(exception.unmappedPaths.contains("/unknownField"))
    }

    @Test
    fun `한 필드가 여러 슬라이스에 영향을 주면 모두 반환`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name", "/description"),
                SliceType.CUSTOM to listOf("/name", "/tags"),
                SliceType.PRICE to listOf("/price")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.containsKey("CORE"))
        assertTrue(result.containsKey("CUSTOM"))
    }

    @Test
    fun `빈 ChangeSet이면 빈 ImpactMap 반환`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = emptyList(),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prefix 매칭으로 하위 경로 변경도 매칭`() {
        // Given: /brand/name 변경 → /brand prefix로 매칭
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/name", "hash1"),
                ChangedPath("/brand/logo", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand", "/title"),
                SliceType.PRICE to listOf("/price")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(2, result["CORE"]?.paths?.size)
        assertTrue(result["CORE"]?.paths?.contains("/brand/name") == true)
        assertTrue(result["CORE"]?.paths?.contains("/brand/logo") == true)
    }

    @Test
    fun `impactMap에 빈 배열이면 해당 슬라이스 영향 없음`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name"),
                SliceType.PRICE to emptyList()
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertTrue(!result.containsKey("PRICE"))
    }

    @Test
    fun `여러 changedPaths가 동일 슬라이스를 매칭하면 중복 제거`() {
        // Given
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/description", "hash2"),
                ChangedPath("/title", "hash3")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name", "/description", "/title")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: CORE 슬라이스 1개만 (중복 없음)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(3, result["CORE"]?.paths?.size)
    }

    @Test
    fun `startsWith 오작동 방지 - 유사 prefix는 매칭 안 됨`() {
        // Given: /brandnew 변경, impactMap에 /brand만 있음
        // CRITICAL: /brand는 /brandnew를 매칭하면 안 됨 (다른 필드)
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brandnew", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand", "/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When & Then: /brandnew는 /brand와 다른 필드이므로 fail-closed
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/brandnew"))
    }

    @Test
    fun `부분 매칭과 unmapped 혼재 시 fail-closed`() {
        // Given: /name은 매칭, /unknown은 unmapped
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/unknown", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When & Then: 하나라도 unmapped면 전체 fail
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/unknown"))
        assertEquals(1, exception.unmappedPaths.size)
    }

    @Test
    fun `빈 impactMap에 changedPaths 있으면 fail-closed`() {
        // Given: 모든 변경이 unmapped
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = emptyMap(),
            joins = emptyList(),
            slices = emptyList()
        )

        // When & Then
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/name"))
    }

    @Test
    fun `changedPaths 순서 무관 - 결정성 보장`() {
        // Given: 동일 경로, 다른 순서
        val changeSet1 = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/price", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val changeSet2 = ChangeSet(
            changeSetId = "cs-2",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/price", "hash2"),
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name"),
                SliceType.PRICE to listOf("/price")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result1 = calculator.calculate(changeSet1, ruleSet)
        val result2 = calculator.calculate(changeSet2, ruleSet)

        // Then: 순서 무관, 동일 결과
        assertEquals(result1.keys, result2.keys)
        assertEquals(result1["CORE"]?.paths?.toSet(), result2["CORE"]?.paths?.toSet())
        assertEquals(result1["PRICE"]?.paths?.toSet(), result2["PRICE"]?.paths?.toSet())
    }

    @Test
    fun `changedPaths 중복 경로 - 중복 제거 처리`() {
        // Given: 동일 경로가 2번 나옴
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: 중복 경로 자동 제거
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(1, result["CORE"]?.paths?.size)
        assertEquals("/name", result["CORE"]?.paths?.first())
    }

    @Test
    fun `특수문자 포함 경로 - 정확히 매칭`() {
        // Given: 특수문자가 포함된 경로
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/meta-data/og:title", "hash1"),
                ChangedPath("/data_source", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/meta-data/og:title", "/data_source")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: 특수문자 경로도 정확히 매칭
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(2, result["CORE"]?.paths?.size)
    }

    @Test
    fun `깊은 중첩 경로 정확히 매칭`() {
        // Given: 깊은 중첩 경로
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/meta/seo/openGraph/title", "hash1"),
                ChangedPath("/meta/seo/keywords", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/meta/seo")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: /meta/seo prefix로 모두 매칭
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(2, result["CORE"]?.paths?.size)
    }

    @Test
    fun `대소문자 구분 - Brand와 brand는 다른 필드`() {
        // Given: /Brand (대문자) 변경
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/Brand", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When & Then: 대소문자 다르므로 unmapped
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/Brand"))
    }

    @Test
    fun `trailing slash 정규화 안 됨 - 정확히 일치해야 매칭`() {
        // Given: /brand/ (trailing slash)
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: /brand/는 /brand/로 시작하므로 매칭됨
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
    }

    @Test
    fun `여러 슬라이스에 동일 경로 - 모든 슬라이스에 distinct하게 포함`() {
        // Given: /brand가 CORE, CATEGORY 모두에 매핑
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("tenant1"),
            entityType = "product",
            entityKey = EntityKey("P001"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/name", "hash1"),
                ChangedPath("/brand/logo", "hash2"),
                ChangedPath("/brand/name", "hash3") // 중복
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "payload-hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand"),
                SliceType.CATEGORY to listOf("/brand")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // When
        val result = calculator.calculate(changeSet, ruleSet)

        // Then: 각 슬라이스마다 중복 제거된 paths
        assertEquals(2, result.size)
        assertEquals(2, result["CORE"]?.paths?.size) // /brand/name, /brand/logo
        assertEquals(2, result["CATEGORY"]?.paths?.size)
        assertTrue(result["CORE"]?.paths?.toSet() == setOf("/brand/name", "/brand/logo"))
    }

    // Helper
    private fun createMeta() = ContractMeta(
        kind = "RuleSet",
        id = "ruleset.product.v1",
        version = SemVer(1, 0, 0),
        status = ContractStatus.ACTIVE
    )
}
