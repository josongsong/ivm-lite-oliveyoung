package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceBuildRules
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinType
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceExecutionPlanner
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * RFC-018: SliceExecutionPlanner 단위 테스트
 */
class SliceExecutionPlannerTest {

    private fun ruleSet(slices: List<SliceDefinition>) = RuleSetContract(
        meta = ContractMeta(ContractKind.RULESET, "test", SemVer.parse("1.0.0"), ContractStatus.ACTIVE),
        entityType = "PRODUCT",
        impactMap = emptyMap(),
        slices = slices,
    )

    @Test
    fun `의존성 없으면 YAML 순서 유지`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.PRICE, SliceBuildRules.PassThrough(listOf("options")), emptyList()),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        plan.slices.map { it.type } shouldBe listOf(SliceType.CORE, SliceType.PRICE)
        plan.steps shouldHaveSize 2
    }

    @Test
    fun `ENRICHMENT 슬라이스는 CORE 이후 실행`() {
        val slices = listOf(
            SliceDefinition(SliceType.ENRICHED, SliceBuildRules.PassThrough(emptyList()), emptyList(), SliceKind.ENRICHMENT),
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        val types = plan.slices.map { it.type }
        types.indexOf(SliceType.CORE) shouldBe 0
        types.indexOf(SliceType.ENRICHED) shouldBe 1
        plan.steps.find { it.sliceRef == "ENRICHED" }!!.reason.contains("CORE") shouldBe true
    }

    @Test
    fun `computeClosure - ENRICHED만 impacted면 CORE 포함`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.ENRICHED, SliceBuildRules.PassThrough(emptyList()), emptyList(), SliceKind.ENRICHMENT),
        )
        val ruleSet = ruleSet(slices)
        val closure = SliceExecutionPlanner.computeClosure(ruleSet, setOf(SliceType.ENRICHED))
        closure shouldContain SliceType.CORE
        closure shouldContain SliceType.ENRICHED
    }

    @Test
    fun `toWaves - 독립 Slice는 동일 Wave`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.PRICE, SliceBuildRules.PassThrough(listOf("options")), emptyList()),
            SliceDefinition(SliceType.INDEX, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        val waves = plan.toWaves()
        waves shouldHaveSize 1
        waves[0] shouldHaveSize 3
    }

    @Test
    fun `toWaves - ENRICHED는 CORE 다음 Wave`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.ENRICHED, SliceBuildRules.PassThrough(emptyList()), emptyList(), SliceKind.ENRICHMENT),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        val waves = plan.toWaves()
        waves shouldHaveSize 2
        waves[0].map { it.type } shouldBe listOf(SliceType.CORE)
        waves[1].map { it.type } shouldBe listOf(SliceType.ENRICHED)
    }

    @Test
    fun `빈 slices → Ok 빈 plan`() {
        val plan = SliceExecutionPlanner.plan(ruleSet(emptyList())).shouldBeOk()
        plan.slices shouldHaveSize 0
        plan.steps shouldHaveSize 0
        plan.toWaves() shouldHaveSize 0
    }

    @Test
    fun `단일 slice → Ok`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        plan.slices shouldHaveSize 1
        plan.steps[0].reason shouldBe "root slice"
    }

    @Test
    fun `joins targetSliceType 동일 RuleSet 내 → 의존성 추가`() {
        val joinToCustom = JoinSpec(
            name = "ref",
            type = JoinType.LOOKUP,
            sourceFieldPath = "x",
            targetEntityType = "PRODUCT",
            targetKeyPattern = "X#{tenantId}#{value}",
            required = false,
            targetSliceType = "CUSTOM",
        )
        val joinToIndex = JoinSpec(
            name = "ref2",
            type = JoinType.LOOKUP,
            sourceFieldPath = "y",
            targetEntityType = "PRODUCT",
            targetKeyPattern = "Y#{tenantId}#{value}",
            required = false,
            targetSliceType = "INDEX",
        )
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.INDEX, SliceBuildRules.PassThrough(listOf("*")), listOf(joinToCustom)),
            SliceDefinition(SliceType.CUSTOM, SliceBuildRules.PassThrough(listOf("*")), listOf(joinToIndex)),
        )
        // INDEX -> CUSTOM, CUSTOM -> INDEX = cycle
        val result = SliceExecutionPlanner.plan(ruleSet(slices))
        result.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.types.Result.Err>()
        (result as com.oliveyoung.ivmlite.shared.domain.types.Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
        val msg = (result.error as DomainError.InvariantViolation).message ?: ""
        msg.contains("Slice dependency cycle detected") shouldBe true
        msg.contains("INDEX") shouldBe true
        msg.contains("CUSTOM") shouldBe true
        msg.contains("Fix:") shouldBe true
    }

    @Test
    fun `cycle 감지 시 에러 메시지에 수정 제안 포함`() {
        val joinAtoB = JoinSpec(
            name = "a",
            type = JoinType.LOOKUP,
            sourceFieldPath = "x",
            targetEntityType = "X",
            targetKeyPattern = "X#{tenantId}#{value}",
            required = false,
            targetSliceType = "PRICE",
        )
        val joinBtoA = JoinSpec(
            name = "b",
            type = JoinType.LOOKUP,
            sourceFieldPath = "y",
            targetEntityType = "Y",
            targetKeyPattern = "Y#{tenantId}#{value}",
            required = false,
            targetSliceType = "CORE",
        )
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), listOf(joinAtoB)),
            SliceDefinition(SliceType.PRICE, SliceBuildRules.PassThrough(listOf("*")), listOf(joinBtoA)),
        )
        val result = SliceExecutionPlanner.plan(ruleSet(slices))
        result.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.types.Result.Err>()
        val err = (result as com.oliveyoung.ivmlite.shared.domain.types.Result.Err).error as DomainError.InvariantViolation
        (err.message ?: "").contains("Fix:") shouldBe true
    }

    @Test
    fun `computeClosure - 빈 impactedTypes → 빈 closure`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.ENRICHED, SliceBuildRules.PassThrough(emptyList()), emptyList(), SliceKind.ENRICHMENT),
        )
        val closure = SliceExecutionPlanner.computeClosure(ruleSet(slices), emptySet())
        closure shouldHaveSize 0
    }

    @Test
    fun `computeClosure - 의존성 없으면 impactedTypes 그대로`() {
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.PRICE, SliceBuildRules.PassThrough(listOf("options")), emptyList()),
        )
        val closure = SliceExecutionPlanner.computeClosure(ruleSet(slices), setOf(SliceType.PRICE))
        closure shouldBe setOf(SliceType.PRICE)
    }

    @Test
    fun `joins targetSliceType 다른 엔티티 참조 → 동일 RuleSet 내 없으면 의존성 없음`() {
        // targetSliceType이 SUMMARY인데 slices에 SUMMARY 없으면 의존성 추가 안 함
        val joinToSummary = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "masterInfo.brand.code",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
            targetSliceType = "SUMMARY", // BRAND의 SUMMARY, PRODUCT RuleSet에는 없음
        )
        val slices = listOf(
            SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")), emptyList()),
            SliceDefinition(SliceType.ENRICHED, SliceBuildRules.PassThrough(emptyList()), listOf(joinToSummary), SliceKind.ENRICHMENT),
        )
        val plan = SliceExecutionPlanner.plan(ruleSet(slices)).shouldBeOk()
        // ENRICHED -> CORE (ENRICHMENT) 만 있고, SUMMARY는 sliceTypes에 없으므로 무시
        plan.slices.map { it.type }.indexOf(SliceType.CORE) shouldBe 0
        plan.slices.map { it.type }.indexOf(SliceType.ENRICHED) shouldBe 1
    }

    private fun <T> com.oliveyoung.ivmlite.shared.domain.types.Result<T>.shouldBeOk(): T {
        this.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<T>>()
        return (this as com.oliveyoung.ivmlite.shared.domain.types.Result.Ok).value
    }
}
