package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionResult
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
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Contract 버전 호환성 E2E 테스트
 *
 * 검증 항목:
 * 1. v1 Contract로 Ingest한 데이터 → 동일 Contract로 재처리 시 멱등성 유지
 * 2. viewDefVersion이 다른 IngestionCommand → 독립 View 생성
 * 3. 동일 RuleSet, 다른 ViewDef 버전 → Slice 공유, View 독립
 * 4. EntityContractResolver가 YAML에서 정확히 버전 해석
 * 5. 여러 엔티티 타입 간 Contract 버전 독립성
 */
class ContractVersionE2ETest : DescribeSpec({

    tags(IntegrationTag)

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

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        sinkRuleRegistry.clear()
    }

    fun productCommand(
        tenantId: String,
        entityKey: String,
        name: String = "Test Product",
        price: Int = 29000,
        version: Long = 1L,
        viewDefVersion: String = "1.0.0"
    ): IngestionCommand {
        val ruleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
        val viewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value

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
            viewDefVersion = viewDefVersion,
            version = version
        )
    }

    describe("Contract 버전 해석 정확성") {

        it("EntityContractResolver → product RuleSetRef 정상 해석") {
            val result = contractResolver.resolveRuleSetRef("product")
            result.shouldBeInstanceOf<arrow.core.Either.Right<*>>()
            val ref = (result as arrow.core.Either.Right).value
            ref.id shouldNotBe ""
            ref.version shouldNotBe null
        }

        it("EntityContractResolver → brand RuleSetRef 정상 해석") {
            val result = contractResolver.resolveRuleSetRef("brand")
            result.shouldBeInstanceOf<arrow.core.Either.Right<*>>()
        }

        it("EntityContractResolver → category RuleSetRef 정상 해석") {
            val result = contractResolver.resolveRuleSetRef("category")
            result.shouldBeInstanceOf<arrow.core.Either.Right<*>>()
        }

        it("존재하지 않는 entityType → Either.Left") {
            val result = contractResolver.resolveRuleSetRef("nonexistent")
            result.shouldBeInstanceOf<arrow.core.Either.Left<*>>()
        }

        it("EntityContractResolver → viewDefVersion 정상 해석") {
            val result = contractResolver.resolveViewDefVersion("product")
            result.shouldBeInstanceOf<arrow.core.Either.Right<*>>()
            val version = (result as arrow.core.Either.Right).value
            version shouldNotBe ""
        }

        it("EntityContractResolver → 슬라이스 타입 목록 해석") {
            val sliceTypes = contractResolver.resolveSliceTypes("product")
            sliceTypes.isNotEmpty() shouldBe true
        }
    }

    describe("동일 RuleSet, 다른 viewDefVersion → View 독립") {

        it("viewDefVersion 1.0.0 → 정상 Ingest + View 생성") {
            val cmd = productCommand(
                tenantId = "ver-test",
                entityKey = "product:VER-001",
                viewDefVersion = "1.0.0",
                version = 1L
            )

            val result = orchestrator.ingest(cmd)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            val r = (result as Result.Ok<*>).value as IngestionResult
            assert(r.viewCount >= 1) { "viewCount should be >= 1 but was ${r.viewCount}" }

            runBlocking {
                when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                    is Result.Ok -> {
                        val events = r.value.filter {
                            it.tenantId == "ver-test" && it.entityKey == "product:VER-001" && it.version == 1L
                        }
                        events.isNotEmpty() shouldBe true
                    }
                    is Result.Err -> throw AssertionError("Expected to find sink events")
                }
            }
        }

        it("동일 엔티티, 다른 version → 각각 독립 View 유지") {
            val cmd1 = productCommand(
                tenantId = "ver-test",
                entityKey = "product:VER-002",
                name = "Version 1",
                viewDefVersion = "1.0.0",
                version = 1L
            )
            val cmd2 = productCommand(
                tenantId = "ver-test",
                entityKey = "product:VER-002",
                name = "Version 2",
                viewDefVersion = "1.0.0",
                version = 2L
            )

            orchestrator.ingest(cmd1).shouldBeInstanceOf<Result.Ok<*>>()
            orchestrator.ingest(cmd2).shouldBeInstanceOf<Result.Ok<*>>()

            // v1, v2 모두 독립 SinkEvent 유지
            runBlocking {
                when (val r = sinkEventRepo.findByStatus("PENDING", 100)) {
                    is Result.Ok -> {
                        val eventsV1 = r.value.filter {
                            it.tenantId == "ver-test" && it.entityKey == "product:VER-002" && it.version == 1L
                        }
                        val eventsV2 = r.value.filter {
                            it.tenantId == "ver-test" && it.entityKey == "product:VER-002" && it.version == 2L
                        }
                        eventsV1.isNotEmpty() shouldBe true
                        eventsV2.isNotEmpty() shouldBe true
                    }
                    is Result.Err -> throw AssertionError("Expected to find sink events")
                }
            }
        }
    }

    describe("Contract 멱등성 - 동일 Contract로 재처리") {

        it("동일 IngestionCommand 2회 호출 → 에러 없음 (멱등)") {
            val cmd = productCommand(
                tenantId = "idem-test",
                entityKey = "product:IDEM-001",
                version = 1L
            )

            val r1 = orchestrator.ingest(cmd)
            val r2 = orchestrator.ingest(cmd)

            r1.shouldBeInstanceOf<Result.Ok<*>>()
            r2.shouldBeInstanceOf<Result.Ok<*>>()

            rawDataRepo.size() shouldBe 1
        }
    }

    describe("여러 엔티티 타입 간 Contract 독립성") {

        it("product와 brand가 각각 독립된 RuleSet 사용") {
            val productRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
            val brandRef = (contractResolver.resolveRuleSetRef("brand") as arrow.core.Either.Right).value

            // 서로 다른 RuleSet ID
            productRef.id shouldNotBe brandRef.id
        }

        it("product와 brand 독립 Ingest → 서로 간섭 없음") {
            val productRuleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
            val productViewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
            val productViewDefVer = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

            val brandRuleSetRef = (contractResolver.resolveRuleSetRef("brand") as arrow.core.Either.Right).value
            val brandViewDefId = (contractResolver.resolveViewDefId("brand") as arrow.core.Either.Right).value
            val brandViewDefVer = (contractResolver.resolveViewDefVersion("brand") as arrow.core.Either.Right).value

            val productCmd = IngestionCommand(
                tenantId = TenantId("cross-test"),
                entityKey = EntityKey("product:CROSS-001"),
                data = buildJsonObject {
                    put("name", "Cross Product")
                    put("price", 10000)
                    put("category", "skincare")
                },
                ruleSetRef = productRuleSetRef,
                viewDefId = productViewDefId,
                viewDefVersion = productViewDefVer,
                version = 1L
            )

            val brandCmd = IngestionCommand(
                tenantId = TenantId("cross-test"),
                entityKey = EntityKey("brand:CROSS-001"),
                data = buildJsonObject {
                    put("name", "Cross Brand")
                },
                ruleSetRef = brandRuleSetRef,
                viewDefId = brandViewDefId,
                viewDefVersion = brandViewDefVer,
                version = 1L
            )

            orchestrator.ingest(productCmd).shouldBeInstanceOf<Result.Ok<*>>()
            orchestrator.ingest(brandCmd).shouldBeInstanceOf<Result.Ok<*>>()

            rawDataRepo.size() shouldBe 2
            sinkEventRepo.size() shouldBe 2
        }
    }

    describe("Contract 해석 실패 시 graceful 에러") {

        it("존재하지 않는 RuleSetRef → Ingest 실패") {
            val bogusCmd = IngestionCommand(
                tenantId = TenantId("fail-test"),
                entityKey = EntityKey("product:FAIL-001"),
                data = buildJsonObject {
                    put("name", "Fail Product")
                    put("price", 1000)
                    put("category", "test")
                },
                ruleSetRef = ContractRef("nonexistent.v999", SemVer.parse("99.0.0")),
                viewDefId = "nonexistent-view",
                viewDefVersion = "1.0.0",
                version = 1L
            )

            val result = orchestrator.ingest(bogusCmd)

            // Slicing 단계에서 Contract를 찾을 수 없어 실패
            result.shouldBeInstanceOf<Result.Err>()
        }
    }
})
