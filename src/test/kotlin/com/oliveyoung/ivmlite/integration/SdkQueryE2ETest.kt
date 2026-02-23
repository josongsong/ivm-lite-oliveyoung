package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
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
import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.sdk.IvmContext
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SDK Query Path E2E Test
 *
 * Deploy(Ingest) → SDK Query API 전체 경로 검증:
 * - get(): 단일 엔티티 조회
 * - list(): 범위 검색 + 페이지네이션
 * - count(): 카운트 조회
 * - stream(): 자동 페이지네이션 스트림
 * - exists() / getOrNull() / getOrDefault(): 편의 API
 * - TypedQuery: ViewRef<T> 기반 타입 세이프 조회
 */
class SdkQueryE2ETest : StringSpec(init@{
    tags(IntegrationTag)

    val tenantId = "sdk-query-e2e"

    // ===== Infrastructure Setup =====
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

    val sinkRuleRegistry = InMemorySinkRuleRegistry()
    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))
    val productRuleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
    val productViewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
    val productViewDefVersion = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

    val orchestrator = IngestionOrchestrator(
        workflow = workflow,
        sinkEventRepo = sinkEventRepo,
        transactionPort = NoOpTransactionAdapter(),
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val queryWorkflow = QueryViewWorkflow(
        sliceRepo = sliceRepo,
        contractRegistry = contractRegistry
    )

    val executor = DeployExecutor(
        orchestrator = orchestrator,
        contractResolver = contractResolver
    )

    fun ingestProduct(entityKey: String, name: String, price: Int, version: Long = 1L) {
        val cmd = IngestionCommand(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            data = buildJsonObject {
                put("name", name)
                put("price", price)
                put("category", "skincare")
            },
            ruleSetRef = productRuleSetRef,
            viewDefId = productViewDefId,
            viewDefVersion = productViewDefVersion,
            version = version,
            jobId = null
        )
        val result = kotlinx.coroutines.runBlocking { orchestrator.ingest(cmd) }
        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    beforeSpec {
        // SDK 초기화 (executor + queryWorkflow 모두 주입)
        val context = IvmContext.builder()
            .executor(executor)
            .queryWorkflow(queryWorkflow)
            .config {
                tenantId("sdk-query-e2e")
            }
            .build()
        Ivm.initialize(context)
    }

    afterSpec {
        Ivm.reset()
    }

    beforeTest {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
    }

    // ===== get() 테스트 =====

    "SDK get(): Ingest 후 단일 엔티티 조회 성공" {
        ingestProduct("product:q-001", "Vitamin C Serum", 29000)

        val result = Ivm.query("view.product.core.v1")
            .key("product:q-001")
            .version(1L)
            .get()

        result.success shouldBe true
        result.viewId shouldBe "view.product.core.v1"
        result.entityKey shouldBe "product:q-001"
        result.data shouldNotBe null
    }

    "SDK get(): 존재하지 않는 엔티티 조회 시 실패" {
        val result = Ivm.query("view.product.core.v1")
            .key("product:nonexistent")
            .version(1L)
            .get()

        result.success shouldBe false
    }

    "SDK exists(): 존재 여부 확인" {
        ingestProduct("product:exists-001", "Exists Test", 10000)

        val exists = Ivm.query("view.product.core.v1")
            .key("product:exists-001")
            .version(1L)
            .exists()

        exists shouldBe true

        val notExists = Ivm.query("view.product.core.v1")
            .key("product:not-exists")
            .version(1L)
            .exists()

        notExists shouldBe false
    }

    "SDK getOrNull(): 성공 시 결과, 실패 시 null" {
        ingestProduct("product:ornull-001", "OrNull Test", 15000)

        val found = Ivm.query("view.product.core.v1")
            .key("product:ornull-001")
            .version(1L)
            .getOrNull()

        found shouldNotBe null
        found!!.success shouldBe true

        val notFound = Ivm.query("view.product.core.v1")
            .key("product:ornull-missing")
            .version(1L)
            .getOrNull()

        notFound shouldBe null
    }

    "SDK getOrDefault(): 실패 시 기본값 반환" {
        val defaultData = buildJsonObject { put("fallback", true) }

        val result = Ivm.query("view.product.core.v1")
            .key("product:default-missing")
            .version(1L)
            .getOrDefault(defaultData)

        result.data shouldBe defaultData
    }

    // ===== list() 테스트 =====

    "SDK list(): 여러 엔티티 범위 검색" {
        ingestProduct("product:list-001", "Product A", 10000)
        ingestProduct("product:list-002", "Product B", 20000)
        ingestProduct("product:list-003", "Product C", 30000)

        val page = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:list-") }
            .limit(10)
            .list()

        page.items shouldHaveAtLeastSize 3
        page.queryTimeMs shouldBeGreaterThanOrEqual 0
    }

    "SDK list(): 페이지네이션 동작" {
        // 5개 Ingest
        for (i in 1..5) {
            ingestProduct("product:page-${i.toString().padStart(3, '0')}", "Paged $i", i * 1000)
        }

        // 2개씩 조회
        val page1 = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:page-") }
            .limit(2)
            .list()

        page1.items shouldHaveSize 2
        page1.hasMore shouldBe true
        page1.nextCursor shouldNotBe null

        // 다음 페이지
        val page2 = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:page-") }
            .limit(2)
            .after(page1.nextCursor)
            .list()

        page2.items shouldHaveSize 2
    }

    // ===== count() 테스트 =====

    "SDK count(): 엔티티 개수 조회" {
        ingestProduct("product:cnt-001", "Count A", 10000)
        ingestProduct("product:cnt-002", "Count B", 20000)
        ingestProduct("product:cnt-003", "Count C", 30000)

        val count = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:cnt-") }
            .count()

        count shouldBeGreaterThanOrEqual 3L
    }

    "SDK count(): 데이터 없으면 0" {
        val count = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:empty-prefix-") }
            .count()

        count shouldBe 0L
    }

    // ===== stream() 테스트 =====

    "SDK stream(): 자동 페이지네이션으로 전체 결과 스트림" {
        for (i in 1..5) {
            ingestProduct("product:stream-${i.toString().padStart(3, '0')}", "Stream $i", i * 1000)
        }

        val allResults = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:stream-") }
            .limit(2) // 페이지당 2개 → 자동으로 3페이지 순회
            .stream()
            .toList()

        allResults shouldHaveAtLeastSize 5
        allResults.forEach { it.success shouldBe true }
    }

    "SDK stream(): take로 조기 종료" {
        for (i in 1..10) {
            ingestProduct("product:take-${i.toString().padStart(3, '0')}", "Take $i", i * 1000)
        }

        val partial = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:take-") }
            .limit(3)
            .stream()
            .take(5)
            .toList()

        partial shouldHaveSize 5
    }

    // ===== 버전 조회 =====

    "SDK get(): 여러 버전 중 특정 버전 조회" {
        ingestProduct("product:ver-001", "Version 1", 10000, version = 1L)
        ingestProduct("product:ver-001", "Version 2", 20000, version = 2L)

        val v1 = Ivm.query("view.product.core.v1")
            .key("product:ver-001")
            .version(1L)
            .get()

        v1.success shouldBe true
        v1.version shouldBe 1L

        val v2 = Ivm.query("view.product.core.v1")
            .key("product:ver-001")
            .version(2L)
            .get()

        v2.success shouldBe true
        v2.version shouldBe 2L
    }

    // ===== Deploy → Query 통합 시나리오 =====

    "SDK 전체 경로: Ivm.product{}.deploy() → Ivm.query().get() (SOTA: Ivm.query 단축)" {
        val deployResult = Ivm.product {
            tenantId("sdk-query-e2e")
            sku("SDK-E2E-001")
            name("SDK E2E Product")
            price(39900)
            currency("KRW")
            category("skincare")
        }.deploy()

        deployResult.success shouldBe true

        // Deploy 후 Query
        val queryResult = Ivm.query("view.product.core.v1")
            .key(deployResult.entityKey)
            .version(deployResult.version.toLongOrNull() ?: 1L)
            .get()

        queryResult.success shouldBe true
        queryResult.entityKey shouldBe deployResult.entityKey
    }

    // ===== first() / firstOrThrow() =====

    "SDK first(): 첫 번째 결과만 반환" {
        ingestProduct("product:first-001", "First A", 10000)
        ingestProduct("product:first-002", "First B", 20000)

        val first = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:first-") }
            .limit(10)
            .first()

        first shouldNotBe null
        first!!.success shouldBe true
    }

    "SDK first(): 결과 없으면 null" {
        val first = Ivm.query("view.product.core.v1")
            .range { keyPrefix("product:no-match-") }
            .limit(10)
            .first()

        first shouldBe null
    }

    // ===== TypedQuery (ViewRef<T>) 테스트 =====

    "TypedQuery: Views.Product.Core로 타입 세이프 조회" {
        ingestProduct("product:typed-001", "Typed Product", 25000)

        val result: com.oliveyoung.ivmlite.sdk.schema.ProductCoreData =
            Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
                .key("product:typed-001")
                .version(1L)
                .get()

        result.name shouldBe "Typed Product"
    }

    "TypedQuery: getRaw()로 원시 ViewResult 접근" {
        ingestProduct("product:raw-001", "Raw Product", 18000)

        val raw = Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
            .key("product:raw-001")
            .version(1L)
            .getRaw()

        raw.success shouldBe true
        raw.viewId shouldBe "view.product.core.v1"
    }

    "TypedQuery: getOrNull() 성공/실패" {
        ingestProduct("product:typed-null-001", "Nullable", 12000)

        val found = Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
            .key("product:typed-null-001")
            .version(1L)
            .getOrNull()

        found shouldNotBe null
        found!!.name shouldBe "Nullable"

        val missing = Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
            .key("product:typed-missing")
            .version(1L)
            .getOrNull()

        missing shouldBe null
    }

    "TypedQuery: exists() 동작" {
        ingestProduct("product:typed-exists-001", "Exists Typed", 9000)

        val exists = Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
            .key("product:typed-exists-001")
            .version(1L)
            .exists()

        exists shouldBe true
    }

    "TypedQuery: count() 동작" {
        ingestProduct("product:typed-cnt-001", "TC A", 1000)
        ingestProduct("product:typed-cnt-002", "TC B", 2000)

        val count = Ivm.query(com.oliveyoung.ivmlite.sdk.schema.Views.Product.Core)
            .range { keyPrefix("product:typed-cnt-") }
            .count()

        count shouldBeGreaterThanOrEqual 2L
    }
})
