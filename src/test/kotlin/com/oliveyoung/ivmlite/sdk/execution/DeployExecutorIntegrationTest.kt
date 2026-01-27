package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * DeployExecutor Integration Test (RFC-IMPL-011 Wave 5-L)
 *
 * InMemory 어댑터로 E2E 테스트
 * - executeSync: RawData Ingest → Compile → Ship
 * - executeAsync: RawData Ingest → Outbox 적재
 */
class DeployExecutorIntegrationTest : StringSpec({

    // ==================== Setup ====================

    val rawDataRepo = InMemoryRawDataRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val sliceRepo = InMemorySliceRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()

    // MockContractRegistry for testing
    val mockContractRegistry = object : ContractRegistryPort {
        override suspend fun loadChangeSetContract(ref: ContractRef) =
            ContractRegistryPort.Result.Err(DomainError.NotFoundError("ChangeSet", ref.id))
        override suspend fun loadJoinSpecContract(ref: ContractRef) =
            ContractRegistryPort.Result.Err(DomainError.NotFoundError("JoinSpec", ref.id))
        override suspend fun loadInvertedIndexContract(ref: ContractRef) =
            ContractRegistryPort.Result.Err(DomainError.NotFoundError("InvertedIndex", ref.id))
        override suspend fun loadRuleSetContract(ref: ContractRef): ContractRegistryPort.Result<RuleSetContract> {
            val ruleSet = RuleSetContract(
                meta = ContractMeta("RULE_SET", ref.id, ref.version, ContractStatus.ACTIVE),
                entityType = "PRODUCT",
                impactMap = mapOf(SliceType.CORE to listOf("*")),
                joins = emptyList(),
                slices = listOf(
                    SliceDefinition(SliceType.CORE, SliceBuildRules.PassThrough(listOf("*")))
                ),
            )
            return ContractRegistryPort.Result.Ok(ruleSet)
        }
        override suspend fun loadViewDefinitionContract(ref: ContractRef) =
            ContractRegistryPort.Result.Err(DomainError.NotFoundError("ViewDefinition", ref.id))
    }

    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = SlicingEngine(mockContractRegistry, joinExecutor)
    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)
    val slicingWorkflow = SlicingWorkflow(
        rawDataRepo,
        sliceRepo,
        slicingEngine,
        invertedIndexRepo,
        changeSetBuilder,
        impactCalculator,
        mockContractRegistry,
    )

    val openSearchSink = InMemorySinkAdapter("opensearch")
    val shipWorkflow = ShipWorkflow(sliceRepo, mapOf("opensearch" to openSearchSink))

    val executor = DeployExecutor(
        ingestWorkflow = ingestWorkflow,
        slicingWorkflow = slicingWorkflow,
        shipWorkflow = shipWorkflow,
        outboxRepository = outboxRepo
    )

    // ==================== Tests ====================

    "executeSync - Sync Compile + Async Ship" {
        val input = ProductInput(
            tenantId = "tenant1",
            sku = "PROD-001",
            name = "Test Product",
            price = 10000
        )

        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(
                mode = ShipMode.Async,
                sinks = listOf(
                    OpenSearchSinkSpec(index = "products")
                )
            ),
            cutoverMode = CutoverMode.Ready
        )

        val result = executor.executeSync(input, spec)

        // Verify result
        result.success shouldBe true
        result.entityKey shouldContain "product:PROD-001"
        result.version shouldNotBe null

        // Verify RawData saved
        val rawResult = rawDataRepo.get(
            TenantId("tenant1"),
            EntityKey("product:PROD-001"),
            result.version.toLong()
        )
        when (rawResult) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok -> { /* success */ }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Err -> {
                throw AssertionError("Expected RawData to be saved")
            }
        }

        // Verify Slice created (Sync Compile)
        val slices = sliceRepo.getByVersion(
            TenantId("tenant1"),
            EntityKey("product:PROD-001"),
            result.version.toLong()
        )
        when (slices) {
            is com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok -> {
                slices.value.size shouldBe 1
                slices.value[0].sliceType shouldBe SliceType.CORE
            }
            is com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Err -> {
                throw AssertionError("Expected slices to be created")
            }
        }

        // Verify Ship Outbox entry (Async Ship)
        val pendingOutbox = outboxRepo.findPending(limit = 100)
        when (pendingOutbox) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok -> {
                val shipEntries = pendingOutbox.value.filter { it.eventType == "ShipRequested" }
                shipEntries.size shouldBe 1
                shipEntries[0].status shouldBe OutboxStatus.PENDING
            }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Err -> {
                throw AssertionError("Expected outbox entries")
            }
        }
    }

    "executeSync - Async Compile + Async Ship" {
        val input = ProductInput(
            tenantId = "tenant2",
            sku = "PROD-002",
            name = "Test Product 2",
            price = 20000
        )

        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(
                mode = ShipMode.Async,
                sinks = listOf(
                    OpenSearchSinkSpec(index = "products")
                )
            ),
            cutoverMode = CutoverMode.Ready
        )

        val result = executor.executeSync(input, spec)

        // Verify result
        result.success shouldBe true

        // Verify RawData saved
        val rawResult = rawDataRepo.get(
            TenantId("tenant2"),
            EntityKey("product:PROD-002"),
            result.version.toLong()
        )
        when (rawResult) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok -> { /* success */ }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Err -> {
                throw AssertionError("Expected RawData to be saved")
            }
        }

        // Verify Compile Outbox entry (Async Compile)
        val pendingOutbox = outboxRepo.findPending(limit = 100)
        when (pendingOutbox) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok -> {
                val compileEntries = pendingOutbox.value.filter { it.eventType == "CompileRequested" }
                compileEntries.size shouldBe io.kotest.matchers.comparables.gt(0)
                compileEntries.last().status shouldBe OutboxStatus.PENDING
            }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Err -> {
                throw AssertionError("Expected outbox entries")
            }
        }
    }

    "executeAsync - RawData Ingest + Compile Outbox" {
        val input = ProductInput(
            tenantId = "tenant3",
            sku = "PROD-003",
            name = "Test Product 3",
            price = 30000
        )

        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(
                mode = ShipMode.Async,
                sinks = listOf(
                    OpenSearchSinkSpec(index = "products"),
                    PersonalizeSinkSpec(datasetArn = "arn:aws:personalize:...")
                )
            ),
            cutoverMode = CutoverMode.Ready
        )

        val job = executor.executeAsync(input, spec)

        // Verify job
        job.state shouldBe DeployState.QUEUED
        job.entityKey shouldContain "product:PROD-003"
        job.version shouldNotBe null
        job.jobId shouldNotBe null

        // Verify RawData saved
        val rawResult = rawDataRepo.get(
            TenantId("tenant3"),
            EntityKey("product:PROD-003"),
            job.version.toLong()
        )
        when (rawResult) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok -> { /* success */ }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Err -> {
                throw AssertionError("Expected RawData to be saved")
            }
        }

        // Verify Compile Outbox entry
        val pendingOutbox = outboxRepo.findPending(limit = 100)
        when (pendingOutbox) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok -> {
                val compileEntries = pendingOutbox.value.filter { it.eventType == "CompileRequested" }
                compileEntries.size shouldBe io.kotest.matchers.comparables.gt(0)
                compileEntries.last().status shouldBe OutboxStatus.PENDING
                compileEntries.last().aggregateId shouldContain "tenant3:product:PROD-003"
            }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Err -> {
                throw AssertionError("Expected outbox entries")
            }
        }
    }

    "executeSync - Sync Compile + Sync Ship (All Sync)" {
        val input = ProductInput(
            tenantId = "tenant4",
            sku = "PROD-004",
            name = "Test Product 4",
            price = 40000
        )

        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(
                mode = ShipMode.Sync,
                sinks = listOf(
                    OpenSearchSinkSpec(index = "products")
                )
            ),
            cutoverMode = CutoverMode.Ready
        )

        val result = executor.executeSync(input, spec)

        // Verify result
        result.success shouldBe true

        // Verify RawData saved
        val rawResult = rawDataRepo.get(
            TenantId("tenant4"),
            EntityKey("product:PROD-004"),
            result.version.toLong()
        )
        when (rawResult) {
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok -> { /* success */ }
            is com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Err -> {
                throw AssertionError("Expected RawData to be saved")
            }
        }

        // Verify Slice created (Sync Compile)
        val slices = sliceRepo.getByVersion(
            TenantId("tenant4"),
            EntityKey("product:PROD-004"),
            result.version.toLong()
        )
        when (slices) {
            is com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok -> {
                slices.value.size shouldBe 1
            }
            is com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Err -> {
                throw AssertionError("Expected slices to be created")
            }
        }

        // Sync Ship should NOT create Outbox entry (직접 Ship)
        // NOTE: 현재는 Ship stub이므로 검증 생략
    }
})
