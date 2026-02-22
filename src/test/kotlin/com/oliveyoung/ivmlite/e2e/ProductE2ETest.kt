package com.oliveyoung.ivmlite.e2e

import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.LocalYamlSinkRuleRegistryAdapter
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.pkg.views.application.ViewComposer
import com.oliveyoung.ivmlite.pkg.views.ports.ViewComposerPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Product E2E 테스트 (product-schema-dx-proposal Phase 1.5)
 *
 * parse → validate → ingest → view compose → sink dry-run 원플로우 검증.
 *
 * 실행:
 * ```bash
 * ./gradlew productE2E
 * ./gradlew productE2E -Dsample=.tmp/product/UA11279226.json
 * ```
 */
class ProductE2ETest : StringSpec({

    val samplePath = System.getProperty("sample") ?: ".tmp/product/UA11279226.json"
    val sampleFile = File(samplePath)

    "productE2E - parse→validate→ingest→view compose→sink dry-run" {
        if (!sampleFile.exists()) {
            error("Sample file not found: $samplePath\n   Create .tmp/product/ or run: ./gradlew productE2E -Dsample=/path/to/product.json")
        }

        runBlocking {
            // 1. parse - JSON 파싱
            val json = Json { ignoreUnknownKeys = true }
            val payload = sampleFile.readText()
            val data = json.parseToJsonElement(payload).jsonObject

            // uaCode에서 entityKey 추출 (PRODUCT:tenantId:entityId)
            val uaCode = data["uaCode"]?.toString()?.trim('"') ?: "UNKNOWN"
            val tenantId = TenantId("oliveyoung")
            val entityKey = EntityKey("PRODUCT:oliveyoung:$uaCode")

            // 2. validate - rule/view/slice 존재성
            val contractRegistry = GatedContractRegistryAdapter(
                delegate = LocalYamlContractRegistryAdapter("/contracts/v1"),
                statusGate = DefaultContractStatusGate,
            )
            val ruleSetRef = ContractRef("ruleset.product.oliveyoung.v1", SemVer.parse("1.0.0"))
            val viewDefId = "view.product.search.v1"
            val viewDefVersion = "1.0.0"

            val ruleSetResult = contractRegistry.loadRuleSetContract(ruleSetRef)
            ruleSetResult.shouldBeInstanceOf<Result.Ok<*>>()
            (ruleSetResult as Result.Ok).value.slices.shouldNotBeEmpty()

            // 3. ingest - 로컬 저장 + slicing + view compose
            val rawRepo: RawDataRepositoryPort = InMemoryRawDataRepository()
            val sliceRepo: SliceRepositoryPort = InMemorySliceRepository()

            val slicingEngine = SlicingEngine(
                contractRegistry = contractRegistry,
                joinExecutor = JoinExecutor(rawRepo),
            )
            val slicingEnginePort: SlicingEnginePort = DefaultSlicingEngineAdapter(delegate = slicingEngine)
            val viewComposer: ViewComposerPort = ViewComposer()

            val ingestionWorkflow = IngestionWorkflow(
                rawDataRepo = rawRepo,
                sliceRepo = sliceRepo,
                slicingEngine = slicingEnginePort,
                viewComposer = viewComposer,
            )

            val command = IngestionCommand(
                tenantId = tenantId,
                entityKey = entityKey,
                data = data,
                ruleSetRef = ruleSetRef,
                viewDefId = viewDefId,
                viewDefVersion = viewDefVersion,
                version = 1L,
            )

            val result = ingestionWorkflow.execute(command)
            result.shouldBeInstanceOf<Result.Ok<*>>()

            val workflowResult = (result as Result.Ok).value
            workflowResult.slices.shouldNotBeEmpty()
            workflowResult.views.shouldNotBeEmpty()

            // 4. sink payload dry-run - SinkRule 매칭 확인
            val sinkRuleRegistry: SinkRuleRegistryPort = LocalYamlSinkRuleRegistryAdapter("/contracts/v1")
            val sinkRulesResult = sinkRuleRegistry.findByEntityAndSliceType("PRODUCT", SliceType.CORE)
            sinkRulesResult.shouldBeInstanceOf<Result.Ok<*>>()
            (sinkRulesResult as Result.Ok).value.shouldNotBeEmpty()

            // View 데이터 비어있지 않음
            val viewData = workflowResult.views.first().data
            viewData.shouldContain("CORE")
            viewData.shouldContain("PRICE")
        }
    }
})
