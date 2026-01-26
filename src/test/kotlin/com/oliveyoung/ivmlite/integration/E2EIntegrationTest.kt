package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay

/**
 * E2E Integration Test (RFC-IMPL Phase B-3)
 *
 * 전체 플로우 테스트:
 * Ingest → Outbox 저장 → Polling Worker → Slice 생성 → Query
 *
 * 모든 컴포넌트가 InMemory 어댑터로 연결되어 실제 데이터 흐름 검증.
 */
class E2EIntegrationTest : StringSpec({

    // ==================== 공통 Setup ====================

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

    val slicingEngine = SlicingEngine(mockContractRegistry)
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
    val queryViewWorkflow = QueryViewWorkflow(sliceRepo)

    val workerConfig = WorkerConfig(
        enabled = true,
        pollIntervalMs = 20,
        idlePollIntervalMs = 50,
        batchSize = 10,
        maxBackoffMs = 200,
        backoffMultiplier = 2.0,
        jitterFactor = 0.0,
        shutdownTimeoutMs = 500,
    )

    val worker = OutboxPollingWorker(
        outboxRepo = outboxRepo,
        slicingWorkflow = slicingWorkflow,
        config = workerConfig,
    )

    afterEach {
        worker.stop()
        rawDataRepo.clear()
        outboxRepo.clear()
        sliceRepo.clear()
    }

    // ==================== E2E 시나리오 테스트 ====================

    "E2E: Ingest → Outbox → Polling → Slice → Query 전체 플로우" {
        val tenantId = TenantId("e2e-tenant")
        val entityKey = EntityKey("PRODUCT#e2e-tenant#product-001")
        val version = 1L
        val payload = """{"name": "E2E Test Product", "price": 29900}"""

        // Step 1: Ingest (RawData + Outbox 저장)
        val ingestResult = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload,
        )
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<Unit>>()

        // Step 2: Outbox에 PENDING 상태로 저장 확인
        val pendingBefore = outboxRepo.findPending(10)
        pendingBefore.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesBefore = (pendingBefore as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesBefore.size shouldBe 1
        entriesBefore[0].status shouldBe OutboxStatus.PENDING

        // Step 3: Worker 시작 → Polling → Slicing 자동 실행
        worker.start()
        delay(150) // Polling 여유 시간

        // Step 4: Outbox 처리 완료 확인 (PENDING → PROCESSED)
        val pendingAfter = outboxRepo.findPending(10)
        pendingAfter.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesAfter = (pendingAfter as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesAfter.size shouldBe 0 // PENDING 없음

        // Step 5: Slice 생성 확인 (Query로 검증)
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "default",
            entityKey = entityKey,
            version = version,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val viewResponse = (queryResult as QueryViewWorkflow.Result.Ok).value
        viewResponse.data.shouldContain("E2E Test Product")
        viewResponse.data.shouldContain("29900")

        // Step 6: Worker 메트릭 검증
        val metrics = worker.getMetrics()
        metrics.processed shouldBe 1
        metrics.failed shouldBe 0
    }

    "E2E: 여러 엔티티 Ingest → 배치 Polling → 모든 Slice 생성" {
        val tenantId = TenantId("e2e-batch-tenant")
        val entityCount = 5

        // Step 1: 여러 엔티티 Ingest
        repeat(entityCount) { i ->
            val entityKey = EntityKey("PRODUCT#e2e-batch-tenant#item-$i")
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i, "name": "Item $i"}""",
            )
        }

        // Step 2: Outbox에 5개 PENDING 확인
        val pendingBefore = outboxRepo.findPending(20)
        pendingBefore.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesBefore = (pendingBefore as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesBefore.size shouldBe entityCount

        // Step 3: Worker 시작 → 배치 처리
        worker.start()
        delay(300) // 배치 처리 여유 시간

        // Step 4: 모든 Outbox 처리 완료 확인
        val pendingAfter = outboxRepo.findPending(20)
        pendingAfter.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        val entriesAfter = (pendingAfter as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
        entriesAfter.size shouldBe 0

        // Step 5: 모든 Slice Query 성공
        repeat(entityCount) { i ->
            val entityKey = EntityKey("PRODUCT#e2e-batch-tenant#item-$i")
            val queryResult = queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "v1",
                entityKey = entityKey,
                version = 1L,
                requiredSliceTypes = listOf(SliceType.CORE),
            )
            queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
            val viewResponse = (queryResult as QueryViewWorkflow.Result.Ok).value
            viewResponse.data.shouldContain("Item $i")
        }

        // Step 6: 메트릭 검증
        val metrics = worker.getMetrics()
        metrics.processed shouldBe entityCount
    }

    "E2E: Worker 없이 수동 Slicing 후 Query" {
        val tenantId = TenantId("e2e-manual")
        val entityKey = EntityKey("PRODUCT#e2e-manual#manual-001")

        // Step 1: Ingest
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Manual Slice Test"}""",
        )

        // Step 2: 수동 Slicing (Worker 없이)
        val sliceResult = slicingWorkflow.execute(tenantId, entityKey, 1L)
        sliceResult.shouldBeInstanceOf<SlicingWorkflow.Result.Ok<*>>()

        // Step 3: Query 성공
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "manual",
            entityKey = entityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as QueryViewWorkflow.Result.Ok).value
        response.data.shouldContain("Manual Slice Test")
    }

    "E2E: 멱등성 - 같은 데이터 2번 Ingest → 정상 처리" {
        val tenantId = TenantId("e2e-idempotent")
        val entityKey = EntityKey("PRODUCT#e2e-idempotent#idem-001")
        val payload = """{"name": "Idempotent Test"}"""

        // 첫 번째 Ingest
        val result1 = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload,
        )
        result1.shouldBeInstanceOf<IngestWorkflow.Result.Ok<Unit>>()

        // 두 번째 Ingest (멱등)
        val result2 = ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload,
        )
        result2.shouldBeInstanceOf<IngestWorkflow.Result.Ok<Unit>>()

        // Worker 실행
        worker.start()
        delay(150)

        // Query 성공 (한 번만 처리됨)
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "idem",
            entityKey = entityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
    }

    "E2E: Slice 없이 Query → 실패 (fail-closed)" {
        val tenantId = TenantId("e2e-no-slice")
        val entityKey = EntityKey("PRODUCT#e2e-no-slice#missing-001")

        // Slice 없이 바로 Query
        val queryResult = queryViewWorkflow.execute(
            tenantId = tenantId,
            viewId = "fail",
            entityKey = entityKey,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
    }

    // ==================== 버전 업데이트 시나리오 ====================

    "E2E: 동일 엔티티 v1→v2→v3 업데이트 → 각 버전 독립 Query" {
        val tenantId = TenantId("e2e-version")
        val entityKey = EntityKey("PRODUCT#e2e-version#prod-001")

        // v1, v2, v3 순차 Ingest
        val versions = listOf(
            1L to """{"name": "Product V1", "price": 10000}""",
            2L to """{"name": "Product V2", "price": 15000}""",
            3L to """{"name": "Product V3", "price": 20000}""",
        )

        versions.forEach { (version, payload) ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = payload,
            )
        }

        // Worker로 모두 처리
        worker.start()
        delay(300)

        // 각 버전 독립 Query 가능
        versions.forEach { (version, _) ->
            val result = queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "version-test",
                entityKey = entityKey,
                version = version,
                requiredSliceTypes = listOf(SliceType.CORE),
            )
            result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
            val viewResponse = (result as QueryViewWorkflow.Result.Ok).value
            viewResponse.data.shouldContain("Product V$version")
        }

        // 메트릭: 3개 처리
        worker.getMetrics().processed shouldBe 3
    }

    // ==================== 멀티 테넌트 격리 ====================

    "E2E: 멀티 테넌트 격리 - 다른 테넌트 데이터 접근 불가" {
        val tenantA = TenantId("tenant-A")
        val tenantB = TenantId("tenant-B")
        val entityKeyA = EntityKey("PRODUCT#tenant-A#shared-id")
        val entityKeyB = EntityKey("PRODUCT#tenant-B#shared-id")

        // 각 테넌트에 동일 ID로 다른 데이터 Ingest
        ingestWorkflow.execute(
            tenantId = tenantA,
            entityKey = entityKeyA,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Tenant A Product", "secret": "A-SECRET"}""",
        )
        ingestWorkflow.execute(
            tenantId = tenantB,
            entityKey = entityKeyB,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = """{"name": "Tenant B Product", "secret": "B-SECRET"}""",
        )

        worker.start()
        delay(200)

        // Tenant A Query → A 데이터만
        val resultA = queryViewWorkflow.execute(
            tenantId = tenantA,
            viewId = "isolation",
            entityKey = entityKeyA,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        resultA.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        val viewResponseA = (resultA as QueryViewWorkflow.Result.Ok).value
        viewResponseA.data.shouldContain("Tenant A Product")
        viewResponseA.data.shouldContain("A-SECRET")

        // Tenant A로 Tenant B 데이터 접근 시도 → 실패
        val crossAccess = queryViewWorkflow.execute(
            tenantId = tenantA,
            viewId = "isolation",
            entityKey = entityKeyB,
            version = 1L,
            requiredSliceTypes = listOf(SliceType.CORE),
        )
        crossAccess.shouldBeInstanceOf<QueryViewWorkflow.Result.Err>()
    }

    // ==================== Hash 결정성 검증 ====================

    "E2E: Hash 결정성 - 동일 입력 → 동일 Slice Hash" {
        val tenantId = TenantId("e2e-hash")
        val payload = """{"z": 1, "a": 2, "m": 3}"""

        // 첫 번째 Ingest
        val entityKey1 = EntityKey("PRODUCT#e2e-hash#hash-001")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey1,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload,
        )

        // 두 번째 Ingest (다른 entityKey, 같은 payload)
        val entityKey2 = EntityKey("PRODUCT#e2e-hash#hash-002")
        ingestWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey2,
            version = 1L,
            schemaId = "product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload,
        )

        worker.start()
        delay(200)

        // 두 Slice의 Hash 비교 (batchGet 사용)
        val key1 = SliceRepositoryPort.SliceKey(tenantId, entityKey1, 1L, SliceType.CORE)
        val key2 = SliceRepositoryPort.SliceKey(tenantId, entityKey2, 1L, SliceType.CORE)

        val result1 = sliceRepo.batchGet(tenantId, listOf(key1))
        val result2 = sliceRepo.batchGet(tenantId, listOf(key2))

        result1.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()
        result2.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<*>>()

        @Suppress("UNCHECKED_CAST")
        val slices1 = (result1 as SliceRepositoryPort.Result.Ok<List<com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord>>).value
        @Suppress("UNCHECKED_CAST")
        val slices2 = (result2 as SliceRepositoryPort.Result.Ok<List<com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord>>).value

        // 동일 payload → 동일 Hash (결정성 보장)
        slices1[0].hash shouldBe slices2[0].hash
    }

    // ==================== Worker 복구 시나리오 ====================

    "E2E: Worker 재시작 → 미처리 Outbox 정상 처리" {
        val tenantId = TenantId("e2e-recovery")

        // Ingest 3개 (Worker 없이)
        repeat(3) { i ->
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = EntityKey("PRODUCT#e2e-recovery#item-$i"),
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = """{"index": $i}""",
            )
        }

        // PENDING 3개 확인 (Worker 시작 전 미처리 상태)
        val pending = outboxRepo.findPending(10)
        pending.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        (pending as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value.size shouldBe 3

        // Worker 시작 → 미처리 항목 자동 처리
        worker.start()
        delay(300)

        // 모든 Outbox 처리 완료
        val afterRecovery = outboxRepo.findPending(10)
        afterRecovery.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
        (afterRecovery as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value.size shouldBe 0

        // 모든 Slice 생성 확인
        repeat(3) { i ->
            val result = queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "recovery",
                entityKey = EntityKey("PRODUCT#e2e-recovery#item-$i"),
                version = 1L,
                requiredSliceTypes = listOf(SliceType.CORE),
            )
            result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
        }
    }
})
