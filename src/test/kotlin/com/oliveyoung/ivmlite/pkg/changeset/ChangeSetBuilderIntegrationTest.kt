package com.oliveyoung.ivmlite.pkg.changeset

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ChangeSetBuilder + ImpactCalculator 통합 테스트
 *
 * RFC-IMPL-010 Phase D-7: ImpactCalculator와 ChangeSetBuilder 연동 검증
 */
class ChangeSetBuilderIntegrationTest {
    private val builder = ChangeSetBuilder()
    private val calculator = ImpactCalculator()

    @Test
    fun `UPDATE 타입 - changedPaths 기반 impactMap 계산 후 ChangeSet 생성`() {
        // given
        val fromPayload = """{"title":"Old Title","price":1000}"""
        val toPayload = """{"title":"New Title","price":2000}"""
        val ruleSet = RuleSetContract(
            meta = ContractMeta(
                kind = "RuleSet",
                id = "rs-1",
                version = SemVer(1, 0, 0),
                status = ContractStatus.ACTIVE,
            ),
            entityType = "Product",
            impactMap = mapOf(
                SliceType.CORE to listOf("/title"),
                SliceType.PRICE to listOf("/price"),
            ),
            joins = emptyList(),
            slices = emptyList(),
        )

        // when: ChangeSet 먼저 빌드 (impactMap 없이)
        val tempChangeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // ImpactCalculator로 impactMap 계산
        val impactMap = calculator.calculate(tempChangeSet, ruleSet)
        val impactedSliceTypes = impactMap.keys.toSet()

        // 최종 ChangeSet 재생성
        val finalChangeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = impactedSliceTypes,
            impactMap = impactMap,
        )

        // then
        assertEquals(ChangeType.UPDATE, finalChangeSet.changeType)
        assertEquals(2, finalChangeSet.changedPaths.size)
        assertEquals(2, finalChangeSet.impactedSliceTypes.size)
        assertTrue(finalChangeSet.impactedSliceTypes.contains("CORE"))
        assertTrue(finalChangeSet.impactedSliceTypes.contains("PRICE"))
        assertEquals(2, finalChangeSet.impactMap.size)
    }

    @Test
    fun `CREATE 타입 - impactMap 계산 스킵 (모든 슬라이스 영향)`() {
        val toPayload = """{"title":"New Product","price":1000}"""

        // when
        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 0,
            toVersion = 1,
            fromPayload = null,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // then
        assertEquals(ChangeType.CREATE, changeSet.changeType)
        assertTrue(changeSet.changedPaths.isEmpty())
    }

    @Test
    fun `NO_CHANGE 타입 - changedPaths 빈 배열, impactMap 빈 맵`() {
        val payload = """{"title":"Same","price":1000}"""

        // when
        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = payload,
            toPayload = payload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // then
        assertEquals(ChangeType.NO_CHANGE, changeSet.changeType)
        assertTrue(changeSet.changedPaths.isEmpty())
        assertTrue(changeSet.impactMap.isEmpty())
    }
}
