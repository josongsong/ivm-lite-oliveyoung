package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 동시성 E2E 테스트
 *
 * 검증 항목:
 * 1. 동일 entityKey의 동일 버전 동시 Ingest → 멱등성 보장 (둘 다 성공)
 * 2. 동일 entityKey의 다른 버전 동시 Ingest → 각각 독립 처리
 * 3. 서로 다른 entityKey 동시 Ingest → 간섭 없음
 * 4. 다중 tenant 동시 Ingest → 격리 보장
 * 5. 대량 동시 Ingest → 데이터 무결성
 */
class ConcurrencyE2ETest : DescribeSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val sinkEventRepo = InMemorySinkEventRepository()
    val sinkRuleRegistry = InMemorySinkRuleRegistry()

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
        sinkRuleRegistry = sinkRuleRegistry
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))

    fun productCommand(
        tenantId: String,
        entityKey: String,
        name: String = "Concurrent Product",
        price: Int = 10000,
        version: Long = 1L,
    ): IngestionCommand {
        val ruleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
        val viewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
        val viewDefVer = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

        return IngestionCommand(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            data = buildJsonObject {
                put("name", name)
                put("price", price)
                put("category", "skincare")
            },
            ruleSetRef = ruleSetRef,
            viewDefId = viewDefId,
            viewDefVersion = viewDefVer,
            version = version
        )
    }

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        sinkRuleRegistry.clear()
    }

    describe("동일 entityKey 동시 Ingest") {

        it("동일 버전 동시 2회 → 멱등성 보장 (둘 다 성공)") {
            val cmd = productCommand("conc-t", "product:SAME-001", version = 1L)

            val results = coroutineScope {
                val d1 = async { orchestrator.ingest(cmd) }
                val d2 = async { orchestrator.ingest(cmd) }
                listOf(d1, d2).awaitAll()
            }

            // 둘 다 성공 (putIfAbsent + 멱등성)
            results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }
            rawDataRepo.size() shouldBe 1
        }

        it("다른 버전 동시 Ingest → 각각 독립 처리") {
            val cmd1 = productCommand("conc-t", "product:DIFF-VER-001", name = "V1", version = 1L)
            val cmd2 = productCommand("conc-t", "product:DIFF-VER-001", name = "V2", version = 2L)

            val results = coroutineScope {
                val d1 = async { orchestrator.ingest(cmd1) }
                val d2 = async { orchestrator.ingest(cmd2) }
                listOf(d1, d2).awaitAll()
            }

            results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }
            rawDataRepo.size() shouldBe 2
        }
    }

    describe("서로 다른 entityKey 동시 Ingest") {

        it("10개 서로 다른 entityKey 동시 처리 → 전부 성공") {
            val commands = (1..10).map { i ->
                productCommand(
                    "conc-t",
                    "product:MULTI-$i",
                    name = "Product $i",
                    price = i * 1000,
                    version = 1L
                )
            }

            val results = coroutineScope {
                commands.map { cmd -> async { orchestrator.ingest(cmd) } }.awaitAll()
            }

            results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }
            rawDataRepo.size() shouldBe 10
        }
    }

    describe("다중 tenant 동시 Ingest") {

        it("5개 tenant 동시 Ingest → 격리 보장") {
            val commands = (1..5).map { i ->
                productCommand(
                    "tenant-$i",
                    "product:TENANT-$i",
                    name = "Tenant $i Product",
                    version = 1L
                )
            }

            val results = coroutineScope {
                commands.map { cmd -> async { orchestrator.ingest(cmd) } }.awaitAll()
            }

            results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }
            rawDataRepo.size() shouldBe 5

            // 각 tenant 데이터 독립 확인
            (1..5).forEach { i ->
                val r = rawDataRepo.getLatest(
                    TenantId("tenant-$i"),
                    EntityKey("product:TENANT-$i")
                )
                r.shouldBeInstanceOf<Result.Ok<*>>()
            }
        }

        it("동일 entityKey, 다른 tenant 동시 Ingest → 간섭 없음") {
            val commands = (1..5).map { i ->
                productCommand(
                    "tenant-$i",
                    "product:SHARED-KEY",
                    name = "Tenant $i",
                    version = 1L
                )
            }

            val results = coroutineScope {
                commands.map { cmd -> async { orchestrator.ingest(cmd) } }.awaitAll()
            }

            results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }
            rawDataRepo.size() shouldBe 5
        }
    }

    describe("대량 동시 Ingest 무결성") {

        it("50개 동시 Ingest → 데이터 무결성 유지") {
            val commands = (1..50).map { i ->
                productCommand(
                    "bulk-t",
                    "product:BULK-${String.format("%03d", i)}",
                    name = "Bulk $i",
                    price = i * 100,
                    version = 1L
                )
            }

            val results = coroutineScope {
                commands.map { cmd -> async { orchestrator.ingest(cmd) } }.awaitAll()
            }

            val successCount = results.count { it is Result.Ok<*> }
            successCount shouldBe 50
            rawDataRepo.size() shouldBe 50
        }
    }
})
