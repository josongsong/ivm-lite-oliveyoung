package com.oliveyoung.ivmlite.pkg.contracts

import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.AllowAllStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.domain.ChangeSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.domain.FallbackPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.InvertedIndexContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.JoinSpecContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.MissingPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.PartialPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.ResponseMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
/**
 * GatedContractRegistryAdapter 단위 테스트 (RFC-IMPL-010 Phase D-6)
 *
 * Decorator 패턴: delegate 호출 전 gate 검증
 * - gate 통과 → delegate 호출
 * - gate 실패 → delegate 호출 안함, Err 반환
 */
class GatedContractRegistryAdapterTest : StringSpec({
    val ref = ContractRef("test.v1", SemVer.parse("1.0.0"))
    fun createMeta(status: ContractStatus) = ContractMeta(
        kind = "CHANGESET",
        id = "test.v1",
        version = SemVer.parse("1.0.0"),
        status = status,
    )
    fun createChangeSetContract(status: ContractStatus) = ChangeSetContract(
        meta = createMeta(status),
        entityKeyFormat = "{ENTITY_TYPE}#{tenantId}#{entityId}",
        externalizeThresholdBytes = 100000,
        fanoutEnabled = false,
    )
    fun createJoinSpecContract(status: ContractStatus) = JoinSpecContract(
        meta = createMeta(status).copy(kind = "JOIN_SPEC"),
        maxJoinDepth = 1,
        maxFanout = 10000,
        invertedIndexRef = ContractRef("inverted-index.v1", SemVer.parse("1.0.0")),
    )
    fun createInvertedIndexContract(status: ContractStatus) = InvertedIndexContract(
        meta = createMeta(status).copy(kind = "INVERTED_INDEX"),
        pkPattern = "INV#{ref_type}#{ref_value}",
        skPattern = "TARGET#{target_type}#{target_id}",
        padWidth = 12,
        separator = "#",
        maxTargetsPerRef = 500000,
    )
    fun createRuleSetContract(status: ContractStatus) = RuleSetContract(
        meta = createMeta(status).copy(kind = "RULE_SET"),
        entityType = "PRODUCT",
        impactMap = emptyMap(),
        joins = emptyList(),
        slices = emptyList(),
    )
    fun createViewDefinitionContract(status: ContractStatus) = ViewDefinitionContract(
        meta = createMeta(status).copy(kind = "VIEW_DEFINITION"),
        requiredSlices = listOf(SliceType.CORE),
        optionalSlices = listOf(SliceType.PRICE),
        missingPolicy = MissingPolicy.FAIL_CLOSED,
        partialPolicy = PartialPolicy(
            allowed = false,
            optionalOnly = true,
            responseMeta = ResponseMeta(includeMissingSlices = false, includeUsedContracts = false),
        ),
        fallbackPolicy = FallbackPolicy.NONE,
        ruleSetRef = ContractRef("rule-set.v1", SemVer.parse("1.0.0")),
    )
    // ==================== loadChangeSetContract 테스트 ====================
    "loadChangeSetContract - ACTIVE → gate 통과, delegate 호출" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createChangeSetContract(ContractStatus.ACTIVE)
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadChangeSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        (result as ContractRegistryPort.Result.Ok).value.meta.status shouldBe ContractStatus.ACTIVE
        coVerify(exactly = 1) { delegate.loadChangeSetContract(ref) }
    }
    "loadChangeSetContract - DEPRECATED → 경고 로그 + delegate 호출" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createChangeSetContract(ContractStatus.DEPRECATED)
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadChangeSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        (result as ContractRegistryPort.Result.Ok).value.meta.status shouldBe ContractStatus.DEPRECATED
    }
    "loadChangeSetContract - DRAFT → gate 실패, delegate 호출 안함" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createChangeSetContract(ContractStatus.DRAFT)
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadChangeSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        result.error.shouldBeInstanceOf<DomainError.ContractStatusError>()
    }
    "loadChangeSetContract - ARCHIVED → gate 실패, Err 반환" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createChangeSetContract(ContractStatus.ARCHIVED)
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadChangeSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        result.error.shouldBeInstanceOf<DomainError.ContractStatusError>()
    }
    // ==================== loadJoinSpecContract 테스트 ====================
    "loadJoinSpecContract - ACTIVE → Ok" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createJoinSpecContract(ContractStatus.ACTIVE)
        coEvery { delegate.loadJoinSpecContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadJoinSpecContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        coVerify(exactly = 1) { delegate.loadJoinSpecContract(ref) }
    }
    "loadJoinSpecContract - DRAFT → Err" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createJoinSpecContract(ContractStatus.DRAFT)
        coEvery { delegate.loadJoinSpecContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadJoinSpecContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
    }
    // ==================== loadInvertedIndexContract 테스트 ====================
    "loadInvertedIndexContract - ACTIVE → Ok" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createInvertedIndexContract(ContractStatus.ACTIVE)
        coEvery { delegate.loadInvertedIndexContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadInvertedIndexContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        coVerify(exactly = 1) { delegate.loadInvertedIndexContract(ref) }
    }
    "loadInvertedIndexContract - ARCHIVED → Err" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createInvertedIndexContract(ContractStatus.ARCHIVED)
        coEvery { delegate.loadInvertedIndexContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadInvertedIndexContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
    }
    // ==================== loadRuleSetContract 테스트 ====================
    "loadRuleSetContract - ACTIVE → Ok" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createRuleSetContract(ContractStatus.ACTIVE)
        coEvery { delegate.loadRuleSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadRuleSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        coVerify(exactly = 1) { delegate.loadRuleSetContract(ref) }
    }
    "loadRuleSetContract - DRAFT → Err" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createRuleSetContract(ContractStatus.DRAFT)
        coEvery { delegate.loadRuleSetContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadRuleSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
    }
    // ==================== loadViewDefinitionContract 테스트 ====================
    "loadViewDefinitionContract - ACTIVE → Ok" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createViewDefinitionContract(ContractStatus.ACTIVE)
        coEvery { delegate.loadViewDefinitionContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadViewDefinitionContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        coVerify(exactly = 1) { delegate.loadViewDefinitionContract(ref) }
    }
    "loadViewDefinitionContract - ARCHIVED → Err" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val contract = createViewDefinitionContract(ContractStatus.ARCHIVED)
        coEvery { delegate.loadViewDefinitionContract(ref) } returns ContractRegistryPort.Result.Ok(contract)
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val result = adapter.loadViewDefinitionContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
    }
    // ==================== delegate 에러 전파 테스트 ====================
    "delegate가 Err 반환 시 gate 검사 안함, Err 그대로 전파" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val error = DomainError.NotFoundError("Contract", "test.v1")
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Err(error)
        val result = adapter.loadChangeSetContract(ref)
        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        result.error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }
    // ==================== HealthCheckable 위임 테스트 ====================
    "healthCheck - delegate가 HealthCheckable이면 위임" {
        val delegate = mockk<ContractRegistryPort>(moreInterfaces = arrayOf(com.oliveyoung.ivmlite.shared.ports.HealthCheckable::class))
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        coEvery { (delegate as com.oliveyoung.ivmlite.shared.ports.HealthCheckable).healthCheck() } returns true
        adapter.healthCheck() shouldBe true
        coVerify(exactly = 1) { (delegate as com.oliveyoung.ivmlite.shared.ports.HealthCheckable).healthCheck() }
    }
    "healthCheck - delegate가 HealthCheckable 아니면 true 반환" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        adapter.healthCheck() shouldBe true
    }
    "healthCheck - delegate가 unhealthy면 false 반환" {
        val delegate = mockk<ContractRegistryPort>(moreInterfaces = arrayOf(com.oliveyoung.ivmlite.shared.ports.HealthCheckable::class))
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        coEvery { (delegate as com.oliveyoung.ivmlite.shared.ports.HealthCheckable).healthCheck() } returns false
        adapter.healthCheck() shouldBe false
    }
    "healthName은 contract-registry-gated" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        adapter.healthName shouldBe "contract-registry-gated"
    }
    // ==================== 수학적 완결성: 5 Contract × 4 Status = 20 조합 ====================
    "수학적 완결성 - 모든 Contract 타입 × 모든 Status 조합 (20개)" {
        val allowedStatuses = setOf(ContractStatus.ACTIVE, ContractStatus.DEPRECATED)
        // ChangeSet × 4 Status
        ContractStatus.entries.forEach { status ->
            val delegate = mockk<ContractRegistryPort>()
            val gate = DefaultContractStatusGate
            coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(createChangeSetContract(status))
            val adapter = GatedContractRegistryAdapter(delegate, gate)
            val result = adapter.loadChangeSetContract(ref)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
            } else {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
            }
        }
        // JoinSpec × 4 Status
        ContractStatus.entries.forEach { status ->
            val delegate = mockk<ContractRegistryPort>()
            val gate = DefaultContractStatusGate
            coEvery { delegate.loadJoinSpecContract(ref) } returns ContractRegistryPort.Result.Ok(createJoinSpecContract(status))
            val adapter = GatedContractRegistryAdapter(delegate, gate)
            val result = adapter.loadJoinSpecContract(ref)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
            } else {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
            }
        }
        // InvertedIndex × 4 Status
        ContractStatus.entries.forEach { status ->
            val delegate = mockk<ContractRegistryPort>()
            val gate = DefaultContractStatusGate
            coEvery { delegate.loadInvertedIndexContract(ref) } returns ContractRegistryPort.Result.Ok(createInvertedIndexContract(status))
            val adapter = GatedContractRegistryAdapter(delegate, gate)
            val result = adapter.loadInvertedIndexContract(ref)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
            } else {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
            }
        }
        // RuleSet × 4 Status
        ContractStatus.entries.forEach { status ->
            val delegate = mockk<ContractRegistryPort>()
            val gate = DefaultContractStatusGate
            coEvery { delegate.loadRuleSetContract(ref) } returns ContractRegistryPort.Result.Ok(createRuleSetContract(status))
            val adapter = GatedContractRegistryAdapter(delegate, gate)
            val result = adapter.loadRuleSetContract(ref)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
            } else {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
            }
        }
        // ViewDefinition × 4 Status
        ContractStatus.entries.forEach { status ->
            val delegate = mockk<ContractRegistryPort>()
            val gate = DefaultContractStatusGate
            coEvery { delegate.loadViewDefinitionContract(ref) } returns ContractRegistryPort.Result.Ok(createViewDefinitionContract(status))
            val adapter = GatedContractRegistryAdapter(delegate, gate)
            val result = adapter.loadViewDefinitionContract(ref)
            if (status in allowedStatuses) {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
            } else {
                result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
            }
        }
    }
    // ==================== AllowAllStatusGate 사용 시 ====================
    "AllowAllStatusGate - 모든 Status 허용" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = AllowAllStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        ContractStatus.entries.forEach { status ->
            coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Ok(createChangeSetContract(status))
            val result = adapter.loadChangeSetContract(ref)
            result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        }
    }
    // ==================== 모든 Contract 타입에 대한 delegate Err 전파 ====================
    "모든 Contract 타입 - delegate Err 시 그대로 전파" {
        val delegate = mockk<ContractRegistryPort>()
        val gate = DefaultContractStatusGate
        val adapter = GatedContractRegistryAdapter(delegate, gate)
        val error = DomainError.ContractError("parse error")
        coEvery { delegate.loadChangeSetContract(ref) } returns ContractRegistryPort.Result.Err(error)
        coEvery { delegate.loadJoinSpecContract(ref) } returns ContractRegistryPort.Result.Err(error)
        coEvery { delegate.loadInvertedIndexContract(ref) } returns ContractRegistryPort.Result.Err(error)
        coEvery { delegate.loadRuleSetContract(ref) } returns ContractRegistryPort.Result.Err(error)
        coEvery { delegate.loadViewDefinitionContract(ref) } returns ContractRegistryPort.Result.Err(error)
        adapter.loadChangeSetContract(ref).shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        adapter.loadJoinSpecContract(ref).shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        adapter.loadInvertedIndexContract(ref).shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        adapter.loadRuleSetContract(ref).shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        adapter.loadViewDefinitionContract(ref).shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
    }
})
