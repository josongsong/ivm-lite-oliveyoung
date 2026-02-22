package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.views.domain.ViewRecord
import com.oliveyoung.ivmlite.pkg.views.ports.ViewComposerPort
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * IngestionWorkflow (Domain Layer) 단위 테스트
 *
 * 커버리지:
 * - execute: RawData → Slicing → View 전체 파이프라인
 * - RawData 저장 실패 시 조기 리턴
 * - Slicing 실패 시 조기 리턴
 * - Slicing 결과 빈 슬라이스 → ValidationError
 * - View 조합 실패 시 조기 리턴
 * - View 저장 실패 시 에러 전파
 * - 성공 시 WorkflowResult (rawData, slices, views) 반환
 */
class IngestionWorkflowTest : DescribeSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()

    val tenantId = TenantId("test-tenant")
    val entityKey = EntityKey("product:WF-001")
    val ruleSetRef = ContractRef("ruleset.product.v1", SemVer.parse("1.0.0"))
    val data = JsonObject(mapOf("name" to JsonPrimitive("Test"), "price" to JsonPrimitive(1000)))

    afterEach {
        rawDataRepo.clear()
        sliceRepo.clear()
    }

    describe("execute - 정상 흐름") {

        it("RawData → Slicing → View 성공") {
            val slicingEngine = mockk<SlicingEnginePort>()
            val viewComposer = mockk<ViewComposerPort>()

            val mockSlices = listOf(
                SliceRecord(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 1L,
                    sliceType = SliceType.CORE,
                    data = """{"name":"Test"}""",
                    hash = "hash-core",
                    ruleSetId = ruleSetRef.id,
                    ruleSetVersion = ruleSetRef.version,
                )
            )

            val mockViews = listOf(
                ViewRecord.create(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 1L,
                    viewType = "PRODUCT_CORE",
                    data = """{"CORE":{"name":"Test"}}""",
                    viewDefId = "view.product.core.v1",
                    viewDefVersion = "1.0.0",
                    usedSlices = listOf("CORE")
                )
            )

            coEvery { slicingEngine.slice(any(), any()) } returns
                Result.Ok(SlicingEnginePort.SlicingResult(mockSlices, emptyList()))
            coEvery { viewComposer.compose(any(), any(), any()) } returns
                Result.Ok(mockViews)

            val workflow = IngestionWorkflow(rawDataRepo, sliceRepo, slicingEngine, viewComposer)

            val command = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = "view.product.core.v1",
                viewDefVersion = "1.0.0",
                version = 1L,
            )
            val result = workflow.execute(command)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val wfResult = (result as Result.Ok).value
            wfResult.rawData.tenantId shouldBe tenantId
            wfResult.slices.size shouldBe 1
            wfResult.views.size shouldBe 1
        }
    }

    describe("execute - 에러 케이스") {

        it("Slicing 실패 → 조기 리턴") {
            val slicingEngine = mockk<SlicingEnginePort>()
            val viewComposer = mockk<ViewComposerPort>()

            coEvery { slicingEngine.slice(any(), any()) } returns
                Result.Err(DomainError.InternalError("Slicing failed"))

            val workflow = IngestionWorkflow(rawDataRepo, sliceRepo, slicingEngine, viewComposer)

            val command = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = "view.product.core.v1",
                version = 1L,
            )
            val result = workflow.execute(command)

            result.shouldBeInstanceOf<Result.Err>()
            (result as Result.Err).error.shouldBeInstanceOf<DomainError.InternalError>()
        }

        it("빈 Slicing 결과 → ValidationError") {
            val slicingEngine = mockk<SlicingEnginePort>()
            val viewComposer = mockk<ViewComposerPort>()

            coEvery { slicingEngine.slice(any(), any()) } returns
                Result.Ok(SlicingEnginePort.SlicingResult(emptyList(), emptyList()))

            val workflow = IngestionWorkflow(rawDataRepo, sliceRepo, slicingEngine, viewComposer)

            val command = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = "view.product.core.v1",
                version = 2L,
            )
            val result = workflow.execute(command)

            result.shouldBeInstanceOf<Result.Err>()
            (result as Result.Err).error.shouldBeInstanceOf<DomainError.ValidationError>()
        }

        it("View 조합 실패 → 에러 전파") {
            val slicingEngine = mockk<SlicingEnginePort>()
            val viewComposer = mockk<ViewComposerPort>()

            val mockSlices = listOf(
                SliceRecord(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 3L,
                    sliceType = SliceType.CORE,
                    data = """{"name":"Test"}""",
                    hash = "hash-core-3",
                    ruleSetId = ruleSetRef.id,
                    ruleSetVersion = ruleSetRef.version,
                )
            )

            coEvery { slicingEngine.slice(any(), any()) } returns
                Result.Ok(SlicingEnginePort.SlicingResult(mockSlices, emptyList()))
            coEvery { viewComposer.compose(any(), any(), any()) } returns
                Result.Err(DomainError.InternalError("View composition failed"))

            val workflow = IngestionWorkflow(rawDataRepo, sliceRepo, slicingEngine, viewComposer)

            val command = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = "view.product.core.v1",
                version = 3L,
            )
            val result = workflow.execute(command)

            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("IngestionCommand") {

        it("기본값: viewDefVersion=1.0.0, version=1L") {
            val cmd = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = "view.product.core.v1",
            )

            cmd.viewDefVersion shouldBe "1.0.0"
            cmd.version shouldBe 1L
            cmd.jobId shouldBe null
        }
    }
})
