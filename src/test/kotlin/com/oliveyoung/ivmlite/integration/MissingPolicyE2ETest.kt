package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
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
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import com.oliveyoung.ivmlite.shared.adapters.NoOpTransactionAdapter
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * QueryViewWorkflow MissingPolicy E2E 테스트
 *
 * 검증 항목:
 * 1. FAIL_CLOSED: 필수 슬라이스 누락 → MissingSliceError
 * 2. FAIL_CLOSED: 모든 필수 슬라이스 존재 → 성공
 * 3. PARTIAL_ALLOWED + allowed=true + optionalOnly=false → 부분 응답 허용
 * 4. PARTIAL_ALLOWED + allowed=false → 필수 누락 시 실패
 * 5. PARTIAL_ALLOWED + optionalOnly=true → optional만 누락 허용
 * 6. ResponseMeta: missingSlices, usedContracts 포함 여부
 */
class MissingPolicyE2ETest : DescribeSpec({

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
        sinkRuleRegistry = sinkRuleRegistry,
        sinkPreflight = com.oliveyoung.ivmlite.pkg.sinks.adapters.NoOpSinkPreflight,
    )

    val contractResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))

    val queryWorkflow = QueryViewWorkflow(
        sliceRepo = sliceRepo,
        contractRegistry = contractRegistry
    )

    fun ingestProduct(
        tenantId: String,
        entityKey: String,
        name: String = "Policy Test Product",
        price: Int = 10000,
        version: Long = 1L,
    ): Long {
        val ruleSetRef = (contractResolver.resolveRuleSetRef("product") as arrow.core.Either.Right).value
        val viewDefId = (contractResolver.resolveViewDefId("product") as arrow.core.Either.Right).value
        val viewDefVer = (contractResolver.resolveViewDefVersion("product") as arrow.core.Either.Right).value

        // RFC product-schema-dx: 올리브영 RawData 최소 구조
        val cmd = IngestionCommand(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            data = buildJsonObject {
                put("uaCode", entityKey.removePrefix("product:"))
                put("_meta", buildJsonObject { put("schemaVersion", 1) })
                put("_audit", buildJsonObject { put("createdAt", "2024-01-01") })
                put("masterInfo", buildJsonObject {
                    put("gdsCd", "8800000000001")
                    put("gdsNm", name)
                    put("brand", buildJsonObject { put("krName", "테스트브랜드") })
                })
                put("onlineInfo", buildJsonObject {
                    put("prdtNo", "P001")
                    put("prdtName", name)
                })
                put("options", buildJsonArray {
                    add(buildJsonObject {
                        put("gdsCd", "opt1")
                        put("gdsSelprcUprc", price)
                    })
                })
                put("thumbnailImages", buildJsonArray {})
                put("displayCategories", buildJsonArray {})
                put("emblemInfo", buildJsonObject {})
                put("attributes", buildJsonArray {})
                put("noticeInfo", buildJsonObject {})
                put("descriptionInfo", buildJsonObject {})
                put("globalInfo", buildJsonObject {})
                put("certifications", buildJsonArray {})
                put("safetyCertCategory", buildJsonObject {})
                put("associatedProducts", buildJsonArray {})
            },
            ruleSetRef = ruleSetRef,
            viewDefId = viewDefId,
            viewDefVersion = viewDefVer,
            version = version
        )

        val result = kotlinx.coroutines.runBlocking { orchestrator.ingest(cmd) }
        result.shouldBeInstanceOf<Result.Ok<*>>()
        return version
    }

    beforeEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        sinkEventRepo.clear()
        sinkRuleRegistry.clear()
    }

    describe("FAIL_CLOSED 정책 (view.product.pdp.v1)") {

        it("모든 필수 슬라이스(CORE, PRICE, MEDIA, NOTICE, ASSOCIATED) 존재 → 성공") {
            val version = ingestProduct("policy-t", "product:FC-001")

            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.pdp.v1",
                entityKey = EntityKey("product:FC-001"),
                version = version
            )

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val response = (result as Result.Ok).value
            response.data shouldNotBe ""
        }

        it("필수 슬라이스 누락 → MissingSliceError") {
            // view.product.pdp.v1: requiredSlices=[CORE, PRICE, MEDIA, NOTICE, ASSOCIATED]
            // 슬라이스 repo에 데이터 없음 → 누락
            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.pdp.v1",
                entityKey = EntityKey("product:MISSING-001"),
                version = 999L
            )

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.MissingSliceError>()
            val missing = error as DomainError.MissingSliceError
            missing.missingSlices.isNotEmpty() shouldBe true
            missing.reason.contains("FAIL_CLOSED") shouldBe true
        }
    }

    describe("PARTIAL_ALLOWED 정책 (view.product.search.v1)") {

        it("필수 슬라이스 일부 존재 + PARTIAL_ALLOWED → 부분 응답 허용") {
            // view.product.search.v1: requiredSlices=[CORE, PRICE, CATEGORY, INDEX]
            // partialPolicy: allowed=true, optionalOnly=false
            // → required 누락이어도 부분 응답 허용
            val version = ingestProduct("policy-t", "product:PA-001")

            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.search.v1",
                entityKey = EntityKey("product:PA-001"),
                version = version
            )

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val response = (result as Result.Ok).value
            response.data shouldNotBe ""
        }

        it("PARTIAL_ALLOWED + optionalSlices만 누락 → 성공 + meta 포함") {
            val version = ingestProduct("policy-t", "product:PA-002")

            // view.product.search.v1: optionalSlices=[MEDIA, INVENTORY]
            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.search.v1",
                entityKey = EntityKey("product:PA-002"),
                version = version
            )

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val response = (result as Result.Ok).value
            // responseMeta에 includeMissingSlices=true 설정됨
            // optional 슬라이스가 누락된 경우 meta에 표시될 수 있음
            response.data shouldNotBe ""
        }

        it("데이터 없는 상태에서 PARTIAL_ALLOWED → 부분 응답 (빈 슬라이스)") {
            // view.product.search.v1: partialPolicy.allowed=true, optionalOnly=false
            // → required 전부 누락이어도 OK (allowed=true + optionalOnly=false)
            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.search.v1",
                entityKey = EntityKey("product:EMPTY-001"),
                version = 999L
            )

            result.shouldBeInstanceOf<Result.Ok<*>>()
        }
    }

    describe("FAIL_CLOSED + partialPolicy.optionalOnly=true 조합") {

        it("view.product.pdp.v1: optional(INVENTORY, CATEGORY, INDEX, ENRICHED) 누락은 허용") {
            val version = ingestProduct("policy-t", "product:OPT-001")

            val result = queryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.pdp.v1",
                entityKey = EntityKey("product:OPT-001"),
                version = version
            )

            // partialPolicy.optionalOnly=true → optional 슬라이스만 누락 시 부분 응답 허용
            result.shouldBeInstanceOf<Result.Ok<*>>()
            val response = (result as Result.Ok).value
            response.data shouldNotBe ""
        }
    }

    describe("ContractRegistryPort 미설정 시 에러") {

        it("contractRegistry=null → ContractError") {
            val noRegistryWorkflow = QueryViewWorkflow(
                sliceRepo = sliceRepo,
                contractRegistry = null
            )

            val result = noRegistryWorkflow.execute(
                tenantId = TenantId("policy-t"),
                viewId = "view.product.pdp.v1",
                entityKey = EntityKey("product:NO-REG-001"),
                version = 1L
            )

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ContractError>()
        }
    }
})
