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
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.assertThrows

/**
 * DeployableContext 테스트
 *
 * 커버리지 대상:
 * - deploy(): executor 있을 때 성공, 없을 때 IllegalStateException
 * - deployAsync(): executor 있을 때 Either.Right, 없을 때 Either.Left
 */
class DeployableContextTest : DescribeSpec({

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
        sku = "SKU-CTX",
        name = "Context Test",
        price = 15000
    )

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    describe("deploy()") {

        it("executor 있을 때 정상 DeployResult 반환") {
            val ctx = DeployableContext(productInput, config, executor)

            val result = ctx.deploy()

            result.success shouldBe true
            result.entityKey shouldBe "product:SKU-CTX"
        }

        it("executor 없을 때 IllegalStateException") {
            val ctx = DeployableContext(productInput, config, null)

            assertThrows<IllegalStateException> {
                ctx.deploy()
            }
        }
    }

    describe("deployAsync()") {

        it("executor 있을 때 Either.Right 반환") {
            val ctx = DeployableContext(productInput, config, executor)

            val result = ctx.deployAsync()

            result.shouldBeInstanceOf<Either.Right<*>>()
            val job = (result as Either.Right).value
            job.state shouldBe DeployState.DONE
            job.entityKey shouldBe "product:SKU-CTX"
        }

        it("executor 없을 때 Either.Left(ConfigError) 반환") {
            val ctx = DeployableContext(productInput, config, null)

            val result = ctx.deployAsync()

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
            val error = (result as Either.Left).value
            error.shouldBeInstanceOf<DomainError.ConfigError>()
        }
    }
})
