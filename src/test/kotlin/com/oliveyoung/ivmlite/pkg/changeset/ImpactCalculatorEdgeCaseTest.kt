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
 * ImpactCalculator 엣지 케이스 전수 테스트
 *
 * CRITICAL 버그 검증:
 * - trailing slash 버그
 * - 루트 경로 처리
 * - 배열 인덱스
 * - 깊은 중첩
 */
class ImpactCalculatorEdgeCaseTest {

    private val calculator = ImpactCalculator()

    @Test
    fun `CRITICAL - trailing slash가 있는 impactPath 처리`() {
        // GIVEN: impactMap에 /brand/ (trailing slash 포함)
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand/")  // trailing slash!
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN: /brand/ prefix로 /brand/name 매칭되어야 함
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
    }

    @Test
    fun `루트 경로 변경 - 모든 슬라이스 영향`() {
        // GIVEN: changedPath가 / (루트)
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/"),  // 루트 감시
                SliceType.PRICE to listOf("/price")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN: 루트 변경은 / prefix 매칭
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
    }

    @Test
    fun `배열 인덱스 경로 - 정확히 매칭`() {
        // GIVEN: /items/0, /items/1 변경
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/items/0/name", "hash1"),
                ChangedPath("/items/1/price", "hash2")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/items")  // prefix로 모든 배열 요소 커버
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
        assertEquals(2, result["CORE"]?.paths?.size)
    }

    @Test
    fun `깊은 중첩 경로 - prefix 매칭`() {
        // GIVEN: 매우 깊은 중첩
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/a/b/c/d/e/f/value", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/a/b/c")  // 상위 경로로 매칭
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN
        assertEquals(1, result.size)
        assertTrue(result.containsKey("CORE"))
    }

    @Test
    fun `impactMap 경로들이 하위 관계 - 모두 독립적으로 처리`() {
        // GIVEN: /brand와 /brand/name이 각각 다른 슬라이스에
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/brand"),
                SliceType.CUSTOM to listOf("/brand/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN: 둘 다 매칭
        assertEquals(2, result.size)
        assertTrue(result.containsKey("CORE"))  // /brand prefix 매칭
        assertTrue(result.containsKey("CUSTOM"))  // /brand/name 정확 매칭
    }

    @Test
    fun `impactMap 자체가 빈 리스트들만 - 모든 변경 unmapped`() {
        // GIVEN: 모든 슬라이스가 빈 경로 리스트
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1")
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to emptyList(),
                SliceType.PRICE to emptyList()
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN & THEN: fail-closed
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/name"))
    }

    @Test
    fun `대소문자 구분 - 정확히 일치해야 매칭`() {
        // GIVEN: /Name (대문자)
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/Name", "hash1")  // 대문자
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name")  // 소문자
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN & THEN: 대소문자 다르면 unmapped
        val exception = assertThrows<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        assertTrue(exception.unmappedPaths.contains("/Name"))
    }

    @Test
    fun `중복 제거 검증 - 같은 경로가 여러 번 나와도 paths는 중복 제거`() {
        // GIVEN: /name이 2번
        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "hash1"),
                ChangedPath("/name", "hash2")  // 같은 경로, 다른 해시
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/name")
            ),
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN: distinct()로 중복 제거됨 (BUG FIX)
        assertEquals(1, result.size)
        assertEquals(1, result["CORE"]?.paths?.size)
        assertEquals("/name", result["CORE"]?.paths?.first())
    }

    @Test
    fun `EDGE - impactMap 키가 많고 changedPaths가 많을 때 성능`() {
        // GIVEN: 100개 슬라이스 x 10개 경로, 50개 변경
        val changedPaths = (1..50).map { ChangedPath("/field$it", "hash$it") }
        val impactMap = (1..100).map { idx ->
            val sliceType = if (idx <= 10) SliceType.values()[idx % SliceType.values().size]
            else SliceType.CUSTOM
            sliceType to (1..10).map { "/field${(idx - 1) * 10 + it}" }
        }.toMap()

        val changeSet = ChangeSet(
            changeSetId = "cs-1",
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = changedPaths,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "hash"
        )

        val ruleSet = RuleSetContract(
            meta = createMeta(),
            entityType = "Product",
            impactMap = impactMap,
            joins = emptyList(),
            slices = emptyList()
        )

        // WHEN
        val result = calculator.calculate(changeSet, ruleSet)

        // THEN: 성능 테스트 (시간 측정은 하지 않고 정상 동작만 확인)
        assertTrue(result.isNotEmpty())
    }

    private fun createMeta() = ContractMeta(
        kind = "RuleSet",
        id = "rs-1",
        version = SemVer(1, 0, 0),
        status = ContractStatus.ACTIVE
    )
}
