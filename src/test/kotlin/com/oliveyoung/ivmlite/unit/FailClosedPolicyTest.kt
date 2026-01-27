package com.oliveyoung.ivmlite.unit

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangedPath
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceBuildRules
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * SOTA Fail-Closed 정책 테스트
 * 
 * L12 원칙:
 * - 알 수 없는 상황 → 즉시 실패 (fail-closed)
 * - 묵시적 허용 금지 (안전 우선)
 * - 명시적 매핑만 허용
 * 
 * 업계/학계 기준: 보안 시스템의 기본 원칙
 */
class FailClosedPolicyTest : StringSpec({

    val tenantId = TenantId("tenant-test")
    val entityKey = EntityKey("product:12345")
    
    // 테스트용 RuleSet (impactMap 포함)
    fun createRuleSet(impactMap: Map<SliceType, List<String>>): RuleSetContract {
        return RuleSetContract(
            meta = ContractMeta(
                kind = "RULE_SET",
                id = "ruleset.test.v1",
                version = SemVer.parse("1.0.0"),
                status = ContractStatus.ACTIVE,
            ),
            entityType = "PRODUCT",
            impactMap = impactMap,
            joins = emptyList(),
            slices = listOf(
                SliceDefinition(
                    type = SliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(listOf("*")),
                ),
            ),
            indexes = emptyList(),
        )
    }

    "ImpactCalculator: 매핑된 경로만 변경 → 정상 통과" {
        val calculator = ImpactCalculator()
        
        val changeSet = ChangeSet(
            changeSetId = "CS_test",
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "sha256:abc"),
                ChangedPath("/price", "sha256:def"),
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "sha256:xyz",
        )
        
        val ruleSet = createRuleSet(
            mapOf(
                SliceType.CORE to listOf("/name", "/price"),
            )
        )
        
        val result = calculator.calculate(changeSet, ruleSet)
        
        result.keys shouldContain "CORE"
        result["CORE"]!!.paths shouldContain "/name"
        result["CORE"]!!.paths shouldContain "/price"
    }
    
    "ImpactCalculator: 매핑되지 않은 경로 → UnmappedChangePathError (fail-closed)" {
        val calculator = ImpactCalculator()
        
        val changeSet = ChangeSet(
            changeSetId = "CS_test",
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/name", "sha256:abc"),
                ChangedPath("/unknown_field", "sha256:def"),  // 매핑 안 됨!
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "sha256:xyz",
        )
        
        val ruleSet = createRuleSet(
            mapOf(
                SliceType.CORE to listOf("/name"),  // /unknown_field 없음
            )
        )
        
        val error = shouldThrow<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        
        error.unmappedPaths shouldContain "/unknown_field"
    }
    
    "ImpactCalculator: 빈 changedPaths → 빈 impactMap (정상)" {
        val calculator = ImpactCalculator()
        
        val changeSet = ChangeSet(
            changeSetId = "CS_test",
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = emptyList(),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "sha256:xyz",
        )
        
        val ruleSet = createRuleSet(
            mapOf(SliceType.CORE to listOf("/name"))
        )
        
        val result = calculator.calculate(changeSet, ruleSet)
        
        result shouldBe emptyMap()
    }
    
    "ImpactCalculator: 하위 경로도 매핑됨 (prefix 매칭)" {
        val calculator = ImpactCalculator()
        
        val changeSet = ChangeSet(
            changeSetId = "CS_test",
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brand/name", "sha256:abc"),
                ChangedPath("/brand/code", "sha256:def"),
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "sha256:xyz",
        )
        
        val ruleSet = createRuleSet(
            mapOf(
                SliceType.CORE to listOf("/brand"),  // /brand 하위 모두 매핑
            )
        )
        
        val result = calculator.calculate(changeSet, ruleSet)
        
        result.keys shouldContain "CORE"
        result["CORE"]!!.paths shouldContain "/brand/name"
        result["CORE"]!!.paths shouldContain "/brand/code"
    }
    
    "ImpactCalculator: 유사 경로는 매핑 안 됨 (예: /brandnew ≠ /brand)" {
        val calculator = ImpactCalculator()
        
        val changeSet = ChangeSet(
            changeSetId = "CS_test",
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            changeType = ChangeType.UPDATE,
            changedPaths = listOf(
                ChangedPath("/brandnew", "sha256:abc"),  // /brand와 다름!
            ),
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
            payloadHash = "sha256:xyz",
        )
        
        val ruleSet = createRuleSet(
            mapOf(
                SliceType.CORE to listOf("/brand"),  // /brandnew는 매핑 안 됨
            )
        )
        
        val error = shouldThrow<DomainError.UnmappedChangePathError> {
            calculator.calculate(changeSet, ruleSet)
        }
        
        error.unmappedPaths shouldContain "/brandnew"
    }
    
    "ChangeSetBuilder: 결정적 ID 생성 (동일 입력 → 동일 ID)" {
        val builder = ChangeSetBuilder()
        
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
        cs1.changeSetId.startsWith("CS_") shouldBe true
    }
    
    "ChangeSetBuilder: CREATE 타입 (fromPayload=null)" {
        val builder = ChangeSetBuilder()
        
        val cs = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 0,
            toVersion = 1,
            fromPayload = null,
            toPayload = """{"name":"new"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs.changeType shouldBe ChangeType.CREATE
        cs.changedPaths shouldBe emptyList()
    }
    
    "ChangeSetBuilder: DELETE 타입 (toPayload=null)" {
        val builder = ChangeSetBuilder()
        
        val cs = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"name":"old"}""",
            toPayload = null,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs.changeType shouldBe ChangeType.DELETE
        cs.changedPaths shouldBe emptyList()
    }
    
    "ChangeSetBuilder: NO_CHANGE 타입 (동일 payload)" {
        val builder = ChangeSetBuilder()
        
        val cs = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"name":"same"}""",
            toPayload = """{"name":"same"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs.changeType shouldBe ChangeType.NO_CHANGE
        cs.changedPaths shouldBe emptyList()
    }
    
    "ChangeSetBuilder: changedPaths는 정렬됨 (결정성)" {
        val builder = ChangeSetBuilder()
        
        val cs = builder.build(
            tenantId = tenantId,
            entityType = "PRODUCT",
            entityKey = entityKey,
            fromVersion = 1,
            toVersion = 2,
            fromPayload = """{"z":"1","a":"2","m":"3"}""",
            toPayload = """{"z":"x","a":"y","m":"z"}""",
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )
        
        cs.changeType shouldBe ChangeType.UPDATE
        val paths = cs.changedPaths.map { it.path }
        paths shouldBe paths.sorted()  // 알파벳순 정렬 확인
    }
})
