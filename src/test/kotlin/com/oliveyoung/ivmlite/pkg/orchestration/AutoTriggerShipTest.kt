package com.oliveyoung.ivmlite.pkg.orchestration

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import io.mockk.mockk
import io.mockk.coEvery

/**
 * RFC-IMPL-013: 자동 Ship 트리거 테스트
 *
 * Slicing 완료 후 SinkRule 기반으로 ShipRequested outbox가 자동 생성되는지 검증
 */
class AutoTriggerShipTest : StringSpec({

    val rawDataRepo = InMemoryRawDataRepository()
    val sliceRepo = InMemorySliceRepository()
    val outboxRepo = InMemoryOutboxRepository()
    val invertedIndexRepo = InMemoryInvertedIndexRepository()
    val sinkRuleRegistry = InMemorySinkRuleRegistry()
    
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
                    hash = "test-hash",
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
    
    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)

    val testConfig = WorkerConfig(
        enabled = true,
        pollIntervalMs = 50,
        idlePollIntervalMs = 100,
        batchSize = 10,
        maxBackoffMs = 500,
        backoffMultiplier = 2.0,
        jitterFactor = 0.0,
        shutdownTimeoutMs = 1000,
    )

    afterEach {
        rawDataRepo.clear()
        sliceRepo.clear()
        outboxRepo.clear()
        sinkRuleRegistry.clear()
    }

    "Slicing 완료 후 ShipRequested outbox 자동 생성" {
        // 기본 rule 제거 후 테스트용 rule만 등록
        sinkRuleRegistry.clear()
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.opensearch.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = "http://localhost:9200",
                indexPattern = "products-{tenantId}"
            ),
            docId = DocIdSpec("{tenantId}__{entityKey}")
        ))

        val tenantId = TenantId("tenant-auto-ship")
        val entityKey = EntityKey("PRODUCT:sku-123")

        // 1. RawData 저장 → RawDataIngested outbox 생성
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Auto Ship Test"}""",
        )

        // 2. Worker 실행 (SinkRuleRegistry 주입)
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
            sinkRuleRegistry = sinkRuleRegistry,
        )

        worker.start()
        delay(500)
        worker.stop()

        // 3. 검증: ShipRequested outbox가 생성되었는지 (모든 상태 조회)
        val allEntries = outboxRepo.findAllEntries()
        val shipRequested = allEntries.filter { 
            it.aggregateType == AggregateType.SLICE && 
            it.eventType == "ShipRequested" 
        }
        
        shipRequested shouldHaveSize 1
        shipRequested[0].payload shouldContain "opensearch"
        shipRequested[0].payload shouldContain "sinkrule.opensearch.product"
    }

    "SinkRuleRegistry 없으면 자동 Ship 비활성화" {
        val tenantId = TenantId("tenant-no-sink")
        val entityKey = EntityKey("PRODUCT:sku-no-sink")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "No Sink Test"}""",
        )

        // sinkRuleRegistry 없이 Worker 실행
        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
            // sinkRuleRegistry = null (기본값)
        )

        worker.start()
        delay(300)
        worker.stop()

        // ShipRequested가 생성되지 않음
        val allEntries = outboxRepo.findAllEntries()
        val shipRequested = allEntries.filter { it.eventType == "ShipRequested" }
        
        shipRequested shouldHaveSize 0
    }

    "매칭되는 SinkRule 없으면 ShipRequested 생성 안됨" {
        // BRAND만 처리하는 SinkRule
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.opensearch.brand",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("BRAND")  // PRODUCT 아님!
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = "http://localhost:9200"
            ),
            docId = DocIdSpec("{entityKey}")
        ))

        val tenantId = TenantId("tenant-no-match")
        val entityKey = EntityKey("PRODUCT:sku-no-match")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "No Match Test"}""",
        )

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
            sinkRuleRegistry = sinkRuleRegistry,
        )

        worker.start()
        delay(300)
        worker.stop()

        // PRODUCT에 매칭되는 rule 없음 → ShipRequested 없음
        val shipRequested = outboxRepo.findAllEntries().filter { it.eventType == "ShipRequested" }
        shipRequested shouldHaveSize 0
    }

    "Multi-Sink: 여러 SinkRule 매칭 시 각각 ShipRequested 생성" {
        // OpenSearch rule
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.opensearch.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = "http://localhost:9200"
            ),
            docId = DocIdSpec("{entityKey}")
        ))

        // Personalize rule
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.personalize.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.PERSONALIZE,
                endpoint = "arn:aws:personalize:test"
            ),
            docId = DocIdSpec("{entityKey}")
        ))

        val tenantId = TenantId("tenant-multi-sink")
        val entityKey = EntityKey("PRODUCT:sku-multi")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Multi Sink Test"}""",
        )

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
            sinkRuleRegistry = sinkRuleRegistry,
        )

        worker.start()
        delay(500)
        worker.stop()

        // OpenSearch + Personalize 각각 ShipRequested
        val shipRequested = outboxRepo.findAllEntries().filter { it.eventType == "ShipRequested" }
        shipRequested shouldHaveSize 2
        
        val sinks = shipRequested.map { 
            it.payload.substringAfter("\"sink\":\"").substringBefore("\"") 
        }.toSet()
        sinks shouldBe setOf("opensearch", "personalize")
    }

    "entityKey 형식: ENTITY_TYPE:key" {
        sinkRuleRegistry.register(SinkRule(
            id = "sinkrule.opensearch.category",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("CATEGORY")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = "http://localhost:9200"
            ),
            docId = DocIdSpec("{entityKey}")
        ))

        val tenantId = TenantId("tenant-category")
        val entityKey = EntityKey("CATEGORY:electronics")

        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "category.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Electronics"}""",
        )

        val worker = OutboxPollingWorker(
            outboxRepo = outboxRepo,
            slicingWorkflow = slicingWorkflow,
            config = testConfig,
            sinkRuleRegistry = sinkRuleRegistry,
        )

        worker.start()
        delay(300)
        worker.stop()

        val shipRequested = outboxRepo.findAllEntries().filter { it.eventType == "ShipRequested" }
        shipRequested shouldHaveSize 1
    }
})

// InMemoryOutboxRepository extension for test - 모든 상태 조회
private fun InMemoryOutboxRepository.findAllEntries(): List<com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry> {
    // InMemoryOutboxRepository의 store에 직접 접근하여 모든 상태 조회
    return try {
        val storeField = this::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = storeField.get(this) as Map<*, com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry>
        store.values.toList()
    } catch (e: Exception) {
        emptyList()
    }
}
