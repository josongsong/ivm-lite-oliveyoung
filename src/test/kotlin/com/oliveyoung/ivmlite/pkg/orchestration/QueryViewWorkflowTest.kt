package com.oliveyoung.ivmlite.pkg.orchestration

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.FallbackPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.MissingPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.PartialPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.ResponseMeta
import com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain as stringContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import io.mockk.coEvery

/**
 * QueryViewWorkflow 단위 테스트 (RFC-IMPL-005)
 */
class QueryViewWorkflowTest : StringSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)
    val slicingEngine = mockk<SlicingEnginePort>().also { engine ->
        coEvery { engine.slice(any(), any()) } answers {
            val rawData = firstArg<com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord>()
            val slices = listOf(
                SliceRecord(
                    tenantId = rawData.tenantId,
                    entityKey = rawData.entityKey,
                    version = rawData.version,
                    sliceType = SliceType.CORE,
                    data = rawData.payload,
                    hash = Hashing.sha256Tagged(rawData.payload),
                    ruleSetId = "test-ruleset",
                    ruleSetVersion = SemVer.parse("1.0.0"),
                ),
            )
            SlicingEnginePort.Result.Ok(SlicingEnginePort.SlicingResult(slices, emptyList()))
        }
    }
    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())
    val contractRegistry = mockk<ContractRegistryPort>()
    val slicingWorkflow = SlicingWorkflow(
        rawDataRepo,
        sliceRepo,
        slicingEngine,
        invertedIndexRepo,
        changeSetBuilder,
        impactCalculator,
        contractRegistry,
    )
    val queryViewWorkflow = QueryViewWorkflow(sliceRepo)

    val tenantId = TenantId("tenant-1")
    val entityKey = EntityKey("PRODUCT#tenant-1#query-test")
    val schemaId = "product.v1"
    val schemaVersion = SemVer.parse("1.0.0")

    "성공: slice 조회" {
        // Setup: Ingest → Slicing
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name": "Query Test"}"""
        )
        slicingWorkflow.execute(tenantId, entityKey, 1L)

        // Query
        val result = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "default",
            entityKey = entityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE)
        )
        
        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (result as QueryViewWorkflow.Result.Ok).value
        response.data stringContain "viewId"
        response.data stringContain "Query Test"
    }

    "결정성: sliceTypes 순서 무관하게 동일 결과" {
        // Setup
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name": "Determinism Test"}"""
        )
        slicingWorkflow.execute(tenantId, entityKey, 2L)

        // Query with same sliceTypes (v1은 CORE만)
        val result1 = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "v1",
            entityKey = entityKey,
            version = 2L,
            requiredSliceTypes = listOf(SliceType.CORE)
        )
        
        val result2 = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "v1",
            entityKey = entityKey,
            version = 2L,
            requiredSliceTypes = listOf(SliceType.CORE)
        )
        
        result1.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        result2.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        (result1 as QueryViewWorkflow.Result.Ok).value shouldBe 
            (result2 as QueryViewWorkflow.Result.Ok).value
    }

    "실패: missing slice → Err (fail-closed)" {
        val result = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "test",
            entityKey = EntityKey("PRODUCT#tenant-1#not-exists"),
            version = 999L,
            requiredSliceTypes = listOf(SliceType.CORE)
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
    }

    // ==================== RFC-IMPL-010 D-1: Tombstone 연동 ====================

    "실패: tombstone slice 조회 시 NotFound (기본 동작)" {
        // Setup: 직접 tombstone slice 저장
        val tombstoneEntityKey = EntityKey("PRODUCT#tenant-1#tombstone-test")
        val data = """{"name": "Deleted Product"}"""
        val tombstoneSlice = SliceRecord(
            tenantId = tenantId,
            entityKey = tombstoneEntityKey,
            version = 1L,
            sliceType = SliceType.CORE,
            data = data,
            hash = Hashing.sha256Tagged(data),
            ruleSetId = "ruleset.core.v1",
            ruleSetVersion = SemVer.parse("1.0.0"),
            tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE),
        )
        sliceRepo.putAllIdempotent(listOf(tombstoneSlice))

        // Query: tombstone slice는 기본적으로 NotFound
        val result = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "default",
            entityKey = tombstoneEntityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
    }

    // ==================== RFC-IMPL-010 GAP-D: v2 API (ViewDefinitionContract 기반) ====================

    "v2: ViewDefinitionContract 기반 조회 - FAIL_CLOSED 정책" {
        // Setup: ViewDefinitionContract mock
        val viewDef = createViewDefinition(
            viewId = "view.product.v1",
            requiredSlices = listOf(SliceType.CORE),
            optionalSlices = emptyList(),
            missingPolicy = MissingPolicy.FAIL_CLOSED,
        )
        val mockRegistry = mockk<ContractRegistryPort>()
        coEvery { mockRegistry.loadViewDefinitionContract(any()) } returns ContractRegistryPort.Result.Ok(viewDef)

        val v2Workflow = QueryViewWorkflow(sliceRepo, mockRegistry)

        // Setup: Slice 저장
        val v2EntityKey = EntityKey("PRODUCT#tenant-1#v2-test")
        val sliceData = """{"name": "V2 Test Product"}"""
        sliceRepo.putAllIdempotent(listOf(
            SliceRecord(
                tenantId = tenantId,
                entityKey = v2EntityKey,
                version = 1L,
                sliceType = SliceType.CORE,
                data = sliceData,
                hash = Hashing.sha256Tagged(sliceData),
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            )
        ))

        // v2 execute (sliceTypes 없음 - ViewDefinition에서 결정)
        val result = v2Workflow.execute(
            tenantId = tenantId,
            viewId = "view.product.v1",
            entityKey = v2EntityKey,
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (result as QueryViewWorkflow.Result.Ok).value
        response.data stringContain "V2 Test Product"
    }

    "v2: FAIL_CLOSED - 필수 슬라이스 누락 시 MissingSliceError" {
        val viewDef = createViewDefinition(
            viewId = "view.strict.v1",
            requiredSlices = listOf(SliceType.CORE, SliceType.PRICE),
            optionalSlices = emptyList(),
            missingPolicy = MissingPolicy.FAIL_CLOSED,
        )
        val mockRegistry = mockk<ContractRegistryPort>()
        coEvery { mockRegistry.loadViewDefinitionContract(any()) } returns ContractRegistryPort.Result.Ok(viewDef)

        val v2Workflow = QueryViewWorkflow(sliceRepo, mockRegistry)

        // Setup: CORE만 저장 (PRICE 누락)
        val v2EntityKey = EntityKey("PRODUCT#tenant-1#v2-missing-test")
        val sliceData = """{"name": "Missing Price"}"""
        sliceRepo.putAllIdempotent(listOf(
            SliceRecord(
                tenantId = tenantId,
                entityKey = v2EntityKey,
                version = 1L,
                sliceType = SliceType.CORE,
                data = sliceData,
                hash = Hashing.sha256Tagged(sliceData),
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            )
        ))

        val result = v2Workflow.execute(
            tenantId = tenantId,
            viewId = "view.strict.v1",
            entityKey = v2EntityKey,
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
        val error = (result as QueryViewWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.MissingSliceError>()
        (error as DomainError.MissingSliceError).missingSlices shouldContain "PRICE"
    }

    "v2: PARTIAL_ALLOWED - 필수 슬라이스 누락 허용 (optionalOnly=false)" {
        val viewDef = createViewDefinition(
            viewId = "view.partial.v1",
            requiredSlices = listOf(SliceType.CORE, SliceType.PRICE),
            optionalSlices = emptyList(),
            missingPolicy = MissingPolicy.PARTIAL_ALLOWED,
            partialAllowed = true,
            optionalOnly = false,
            includeMissingSlices = true,
        )
        val mockRegistry = mockk<ContractRegistryPort>()
        coEvery { mockRegistry.loadViewDefinitionContract(any()) } returns ContractRegistryPort.Result.Ok(viewDef)

        val v2Workflow = QueryViewWorkflow(sliceRepo, mockRegistry)

        // Setup: CORE만 저장 (PRICE 누락)
        val v2EntityKey = EntityKey("PRODUCT#tenant-1#v2-partial-test")
        val sliceData = """{"name": "Partial Product"}"""
        sliceRepo.putAllIdempotent(listOf(
            SliceRecord(
                tenantId = tenantId,
                entityKey = v2EntityKey,
                version = 1L,
                sliceType = SliceType.CORE,
                data = sliceData,
                hash = Hashing.sha256Tagged(sliceData),
                ruleSetId = "ruleset.core.v1",
                ruleSetVersion = SemVer.parse("1.0.0"),
            )
        ))

        val result = v2Workflow.execute(
            tenantId = tenantId,
            viewId = "view.partial.v1",
            entityKey = v2EntityKey,
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (result as QueryViewWorkflow.Result.Ok).value
        response.data stringContain "Partial Product"
        // meta에 missingSlices 포함
        response.meta shouldNotBe null
        response.meta!!.missingSlices!! shouldContain "PRICE"
    }

    "v2: contractRegistry 없으면 ContractError" {
        val noRegistryWorkflow = QueryViewWorkflow(sliceRepo, null)

        val result = noRegistryWorkflow.execute(
            tenantId = tenantId,
            viewId = "any",
            entityKey = EntityKey("any"),
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
        val error = (result as QueryViewWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "v2: ViewDefinitionContract 로드 실패 → Err 전파" {
        val mockRegistry = mockk<ContractRegistryPort>()
        coEvery { mockRegistry.loadViewDefinitionContract(any()) } returns ContractRegistryPort.Result.Err(
            DomainError.NotFoundError("ViewDefinition", "view.not.exists")
        )

        val v2Workflow = QueryViewWorkflow(sliceRepo, mockRegistry)

        val result = v2Workflow.execute(
            tenantId = tenantId,
            viewId = "view.not.exists",
            entityKey = EntityKey("any"),
            version = 1L,
        )

        result.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
        val error = (result as QueryViewWorkflow.Result.Err).error
        error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }
})

// ==================== Helper Functions ====================

private fun createViewDefinition(
    viewId: String,
    requiredSlices: List<SliceType>,
    optionalSlices: List<SliceType>,
    missingPolicy: MissingPolicy,
    partialAllowed: Boolean = false,
    optionalOnly: Boolean = true,
    includeMissingSlices: Boolean = false,
    includeUsedContracts: Boolean = false,
): ViewDefinitionContract = ViewDefinitionContract(
    meta = ContractMeta(
        kind = "VIEW_DEFINITION",
        id = viewId,
        version = SemVer.parse("1.0.0"),
        status = ContractStatus.ACTIVE,
    ),
    requiredSlices = requiredSlices,
    optionalSlices = optionalSlices,
    missingPolicy = missingPolicy,
    partialPolicy = PartialPolicy(
        allowed = partialAllowed,
        optionalOnly = optionalOnly,
        responseMeta = ResponseMeta(
            includeMissingSlices = includeMissingSlices,
            includeUsedContracts = includeUsedContracts,
        ),
    ),
    fallbackPolicy = FallbackPolicy.NONE,
    ruleSetRef = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0")),
)
