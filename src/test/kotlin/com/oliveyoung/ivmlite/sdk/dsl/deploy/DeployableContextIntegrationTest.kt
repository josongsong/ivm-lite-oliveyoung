package com.oliveyoung.ivmlite.sdk.dsl.deploy

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * DeployableContext 통합 테스트
 *
 * DeployableContext의 모든 public API를 실제 DeployExecutor 연동으로 검증:
 * - deploy(): 동기 배포
 * - deployAsync(): 비동기 배포
 * - executor 미설정 시 에러 처리
 */
class DeployableContextIntegrationTest : DescribeSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()

    val contractRegistry = LocalYamlContractRegistryAdapter("/contracts/v1")
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(
        SlicingEngine(contractRegistry, joinExecutor)
    )
    val viewComposer = ViewComposer()

    val workflow = IngestionWorkflow(
        rawDataRepo = rawDataRepo,
        sliceRepo = sliceRepo,
        slicingEngine = slicingEngine,
        viewComposer = viewComposer
    )

    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = InMemorySinkRuleRegistry(),
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val executor = DeployExecutor(orchestrator, contractResolver)

    val config = IvmClientConfig()

    val productInput = ProductInput(
        tenantId = "test-tenant",
        sku = "SKU-CTX-001",
        name = "Context Test Product",
        price = 29000,
        category = "skincare"
    )

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("deploy() - 동기 배포") {

        it("성공: DeployResult.success=true 반환") {
            val ctx = DeployableContext(productInput, config, executor)

            val result = ctx.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-CTX-001"
            result.error shouldBe null
        }
    }

    describe("deployAsync() - 비동기 배포") {

        it("성공: Either.Right<DeployJob> 반환") {
            val ctx = DeployableContext(productInput, config, executor)

            val result = ctx.deployAsync()

            result.shouldBeInstanceOf<Either.Right<DeployJob>>()
            val job = (result as Either.Right).value
            job.entityKey shouldBe "product:SKU-CTX-001"
            job.state shouldBe DeployState.DONE
        }
    }

    describe("executor 미설정 시 에러 처리") {

        it("deploy(): executor 없으면 IllegalStateException") {
            val ctx = DeployableContext(productInput, config, executor = null)

            shouldThrow<IllegalStateException> {
                ctx.deploy()
            }
        }

        it("deployAsync(): executor 없으면 Either.Left<DomainError>") {
            val ctx = DeployableContext(productInput, config, executor = null)

            val result = ctx.deployAsync()

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
        }
    }

    describe("연속 배포") {

        it("동일 엔티티 2번 deploy → 모두 성공 (멱등성)") {
            val ctx = DeployableContext(productInput, config, executor)

            val r1 = ctx.deploy()
            val r2 = ctx.deploy()

            r1.success shouldBe true
            r2.success shouldBe true
        }
    }
})
