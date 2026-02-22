package com.oliveyoung.ivmlite.apps.lambda

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkFailureRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkPluginRegistry
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkLedger
import com.oliveyoung.ivmlite.sinks.contract.LedgerStatus
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * SinkStreamProcessor E2E 테스트
 *
 * Lambda 인프라(Koin, DynamoDB SDK) 의존 없이
 * SinkStreamHandler의 코어 로직을 완전 검증.
 *
 * RFC-020 전체 경로:
 * - R1: DELETE (REMOVE 이벤트 → plugin.delete())
 * - R2: 버전 충돌 (idempotencyKey에 version 포함)
 * - R3: 실패 저장 (NonRetryable/PoisonPill → FailureRepository)
 * - R4: 멱등성 (Ledger tryStart/complete/fail)
 * - R5: SinkEvent 상태 갱신 (COMPLETED/FAILED)
 */
class SinkStreamProcessorE2ETest : StringSpec({

    // ===== Test Infrastructure =====

    lateinit var ledger: InMemorySinkLedger
    lateinit var failureRepo: InMemorySinkFailureRepository
    lateinit var pluginRegistry: InMemorySinkPluginRegistry
    lateinit var statusStore: ConcurrentHashMap<String, String>
    lateinit var processor: SinkStreamProcessor

    beforeTest {
        ledger = InMemorySinkLedger()
        failureRepo = InMemorySinkFailureRepository()
        pluginRegistry = InMemorySinkPluginRegistry()
        statusStore = ConcurrentHashMap()

        val statusUpdater = SinkEventStatusUpdater { sinkEventId, _, newStatus ->
            statusStore[sinkEventId] = newStatus
        }

        processor = SinkStreamProcessor(
            pluginRegistry = pluginRegistry,
            sinkLedger = ledger,
            failureRepository = failureRepo,
            statusUpdater = statusUpdater,
        )
    }

    // ===== INSERT 정상 흐름 =====

    "INSERT PENDING → 단일 target 성공 → COMPLETED" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val record = insertRecord(
            id = "evt-001", tenantId = "t1", entityKey = "product:1",
            version = 1L, viewType = "core",
            payload = """{"name":"Product A","price":10000}""",
            targets = listOf("opensearch"),
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        result.errors shouldBe 0
        statusStore["evt-001"] shouldBe "COMPLETED"
    }

    "INSERT PENDING → 멀티 target 모두 성공 → COMPLETED" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        pluginRegistry.register("s3", successPlugin("s3"))

        val record = insertRecord(
            id = "evt-002", tenantId = "t1", entityKey = "product:2",
            version = 1L, viewType = "core",
            payload = """{"name":"Multi Target"}""",
            targets = listOf("opensearch", "s3"),
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        result.errors shouldBe 0
        statusStore["evt-002"] shouldBe "COMPLETED"
    }

    "INSERT → PENDING 아닌 상태는 스킵" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val record = insertRecord(
            id = "evt-skip", tenantId = "t1", entityKey = "product:3",
            version = 1L, viewType = "core",
            payload = """{"name":"Already Done"}""",
            targets = listOf("opensearch"),
            status = "COMPLETED",
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 0
        statusStore["evt-skip"] shouldBe null
    }

    // ===== Ledger 멱등성 (R4) =====

    "R4: 동일 이벤트 재처리 → Ledger COMPLETED → SKIPPED" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val record = insertRecord(
            id = "evt-idem", tenantId = "t1", entityKey = "product:idem",
            version = 1L, viewType = "core",
            payload = """{"name":"Idempotent"}""",
            targets = listOf("opensearch"),
        )

        // 1차 처리
        val result1 = processor.processBatch(listOf(record))
        result1.processed shouldBe 1
        statusStore["evt-idem"] shouldBe "COMPLETED"

        // 2차 처리 (멱등 스킵)
        statusStore.clear()
        val result2 = processor.processBatch(listOf(record))
        result2.processed shouldBe 1 // record 자체는 처리됨 (PENDING 체크 통과)
        result2.errors shouldBe 0
        statusStore["evt-idem"] shouldBe "COMPLETED" // 모두 SKIPPED면 allTargetsSucceeded = true
    }

    // ===== NonRetryable 실패 (R3, R5) =====

    "R3/R5: NonRetryable 실패 → FAILED + FailureRepository 저장" {
        pluginRegistry.register("opensearch", failingPlugin(
            "opensearch",
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                message = "Bad mapping"
            )
        ))

        val record = insertRecord(
            id = "evt-nonfail", tenantId = "t1", entityKey = "product:fail1",
            version = 1L, viewType = "core",
            payload = """{"name":"Fail Product"}""",
            targets = listOf("opensearch"),
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        result.errors shouldBe 1
        statusStore["evt-nonfail"] shouldBe "FAILED"
        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].target shouldBe "opensearch"
        failureRepo.allRecords()[0].errorReasonCode shouldBe "PLUGIN_EXECUTION_FAILED"
    }

    "R3: PoisonPill 에러 → FAILED + FailureRepository 저장" {
        pluginRegistry.register("opensearch", failingPlugin(
            "opensearch",
            SinkError.PoisonPillError(
                reasonCode = ErrorReasonCode.DESERIALIZATION_FAILED,
                message = "Malformed payload"
            )
        ))

        val record = insertRecord(
            id = "evt-poison", tenantId = "t1", entityKey = "product:poison",
            version = 1L, viewType = "core",
            payload = """{"broken":true}""",
            targets = listOf("opensearch"),
        )

        val result = processor.processBatch(listOf(record))

        result.errors shouldBe 1
        statusStore["evt-poison"] shouldBe "FAILED"
        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].errorCategory shouldBe "POISON_PILL"
    }

    // ===== Retryable 실패 → Lambda throw (R5) =====

    "R5: Retryable 실패 → RetryableSinkException throw (Lambda 재시도)" {
        pluginRegistry.register("opensearch", failingPlugin(
            "opensearch",
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "Connection timed out"
            )
        ))

        val record = insertRecord(
            id = "evt-retry", tenantId = "t1", entityKey = "product:retry",
            version = 1L, viewType = "core",
            payload = """{"name":"Retry Product"}""",
            targets = listOf("opensearch"),
        )

        shouldThrow<RetryableSinkException> {
            processor.processBatch(listOf(record))
        }

        // Retryable → 상태 업데이트 안 함 (PENDING 유지)
        statusStore["evt-retry"] shouldBe null
        // Retryable → FailureRepository에 저장 안 함
        failureRepo.size() shouldBe 0
    }

    // ===== 멀티 target 부분 실패 =====

    "멀티 target: 일부 NonRetryable → FAILED (Retryable 없으면)" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        pluginRegistry.register("s3", failingPlugin(
            "s3",
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PERMISSION_DENIED,
                message = "S3 access denied"
            )
        ))

        val record = insertRecord(
            id = "evt-partial", tenantId = "t1", entityKey = "product:partial",
            version = 1L, viewType = "core",
            payload = """{"name":"Partial Fail"}""",
            targets = listOf("opensearch", "s3"),
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        result.errors shouldBe 1
        statusStore["evt-partial"] shouldBe "FAILED"
        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].target shouldBe "s3"
    }

    "멀티 target: Retryable 있으면 → throw (NonRetryable도 저장됨)" {
        pluginRegistry.register("opensearch", failingPlugin(
            "opensearch",
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                message = "Bad request"
            )
        ))
        pluginRegistry.register("s3", failingPlugin(
            "s3",
            SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "S3 timeout"
            )
        ))

        val record = insertRecord(
            id = "evt-mixed", tenantId = "t1", entityKey = "product:mixed",
            version = 1L, viewType = "core",
            payload = """{"name":"Mixed Fail"}""",
            targets = listOf("opensearch", "s3"),
        )

        shouldThrow<RetryableSinkException> {
            processor.processBatch(listOf(record))
        }

        // Retryable → 상태 업데이트 안 함
        statusStore["evt-mixed"] shouldBe null
        // NonRetryable 실패는 FailureRepository에 저장됨
        failureRepo.size() shouldBe 1
    }

    // ===== 미등록 target =====

    "미등록 target → NON_RETRYABLE_FAILURE" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        // "unknown" 미등록

        val record = insertRecord(
            id = "evt-unknown", tenantId = "t1", entityKey = "product:unknown",
            version = 1L, viewType = "core",
            payload = """{"name":"Unknown Target"}""",
            targets = listOf("opensearch", "unknown"),
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        result.errors shouldBe 1
        statusStore["evt-unknown"] shouldBe "FAILED"
    }

    // ===== REMOVE 이벤트 (R1) =====

    "R1: REMOVE → supportsDelete Plugin만 delete() 호출" {
        pluginRegistry.register("opensearch", successPlugin("opensearch", supportsDelete = true))
        pluginRegistry.register("legacy", successPlugin("legacy", supportsDelete = false))

        val record = removeRecord(
            tenantId = "t1", entityKey = "product:del-1",
            viewType = "core", targets = listOf("opensearch", "legacy"),
        )

        val result = processor.processBatch(listOf(record))

        result.deleted shouldBe 1
        result.errors shouldBe 0
    }

    "R1: REMOVE → delete() 실패 시 에러 카운트 증가" {
        pluginRegistry.register("opensearch", deleteFailingPlugin("opensearch"))

        val record = removeRecord(
            tenantId = "t1", entityKey = "product:del-fail",
            viewType = "core", targets = listOf("opensearch"),
        )

        val result = processor.processBatch(listOf(record))

        result.deleted shouldBe 0
        result.errors shouldBe 1
    }

    // ===== 배치 다건 처리 =====

    "배치: 여러 INSERT 레코드 처리" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val records = (1..3).map { i ->
            insertRecord(
                id = "evt-batch-$i", tenantId = "t1", entityKey = "product:batch-$i",
                version = 1L, viewType = "core",
                payload = """{"name":"Batch $i"}""",
                targets = listOf("opensearch"),
            )
        }

        val result = processor.processBatch(records)

        result.processed shouldBe 3
        result.errors shouldBe 0
        statusStore.size shouldBe 3
        (1..3).forEach { i ->
            statusStore["evt-batch-$i"] shouldBe "COMPLETED"
        }
    }

    "배치: INSERT + REMOVE 혼합" {
        pluginRegistry.register("opensearch", successPlugin("opensearch", supportsDelete = true))

        val records = listOf(
            insertRecord(
                id = "evt-mix-1", tenantId = "t1", entityKey = "product:mix-1",
                version = 1L, viewType = "core",
                payload = """{"name":"Insert"}""",
                targets = listOf("opensearch"),
            ),
            removeRecord(
                tenantId = "t1", entityKey = "product:mix-2",
                viewType = "core", targets = listOf("opensearch"),
            ),
        )

        val result = processor.processBatch(records)

        result.processed shouldBe 1
        result.deleted shouldBe 1
        result.errors shouldBe 0
    }

    // ===== 빈 배치 =====

    "빈 배치 → 아무것도 안 함" {
        val result = processor.processBatch(emptyList())

        result.processed shouldBe 0
        result.deleted shouldBe 0
        result.errors shouldBe 0
    }

    // ===== 버전 순서 (R2) =====

    "R2: v1, v2 별도 idempotencyKey → 각각 처리" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val payload = """{"name":"Versioned"}"""
        val r1 = insertRecord(
            id = "evt-v1", tenantId = "t1", entityKey = "product:ver",
            version = 1L, viewType = "core", payload = payload,
            targets = listOf("opensearch"),
        )
        val r2 = insertRecord(
            id = "evt-v2", tenantId = "t1", entityKey = "product:ver",
            version = 2L, viewType = "core", payload = payload,
            targets = listOf("opensearch"),
        )

        val result = processor.processBatch(listOf(r1, r2))

        result.processed shouldBe 2
        result.errors shouldBe 0
        statusStore["evt-v1"] shouldBe "COMPLETED"
        statusStore["evt-v2"] shouldBe "COMPLETED"
    }

    // ===== Ledger 상태 검증 =====

    "Ledger: 성공 처리 후 COMPLETED 상태 확인" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val payload = """{"name":"Ledger Check"}"""
        val viewData = buildJsonObject { put("name", "Ledger Check") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:ledger", 1L, "core", digest)

        val record = insertRecord(
            id = "evt-ledger", tenantId = "t1", entityKey = "product:ledger",
            version = 1L, viewType = "core", payload = payload,
            targets = listOf("opensearch"),
        )

        processor.processBatch(listOf(record))

        val status = ledger.getStatus("opensearch", idempotencyKey)
        status.isRight() shouldBe true
        status.getOrNull() shouldNotBe null
        status.getOrNull()!!.status shouldBe LedgerStatus.COMPLETED
    }

    "Ledger: NonRetryable 실패 후 FAILED 상태" {
        pluginRegistry.register("opensearch", failingPlugin(
            "opensearch",
            SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                message = "Bad request"
            )
        ))

        val payload = """{"name":"Ledger Fail"}"""
        val viewData = buildJsonObject { put("name", "Ledger Fail") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:ledger-fail", 1L, "core", digest)

        val record = insertRecord(
            id = "evt-ledger-fail", tenantId = "t1", entityKey = "product:ledger-fail",
            version = 1L, viewType = "core", payload = payload,
            targets = listOf("opensearch"),
        )

        processor.processBatch(listOf(record))

        val status = ledger.getStatus("opensearch", idempotencyKey)
        status.getOrNull()!!.status shouldBe LedgerStatus.FAILED
    }

    // ===== Deploy → Sink 전체 경로 (Ingest → SinkEvent → Processor) =====

    "전체 경로: IngestionOrchestrator → SinkEvent → SinkStreamProcessor → Plugin 실행" {
        // 이 테스트는 Ingest 파이프라인 없이 SinkEvent DynamoDB 이미지를 직접 구성
        // 실제 운영 경로: Ingest → DynamoDB 저장 → Streams → Lambda → SinkStreamProcessor
        pluginRegistry.register("opensearch", successPlugin("opensearch"))
        pluginRegistry.register("s3", successPlugin("s3"))

        // 3개 상품 동시 Sink 처리
        val records = (1..3).map { i ->
            insertRecord(
                id = "evt-e2e-$i", tenantId = "oliveyoung", entityKey = "product:SKU-$i",
                version = 1L, viewType = "view.product.core.v1",
                payload = buildJsonObject {
                    put("name", "Product $i")
                    put("price", i * 10000)
                    put("category", "skincare")
                }.toString(),
                targets = listOf("opensearch", "s3"),
            )
        }

        val result = processor.processBatch(records)

        result.processed shouldBe 3
        result.deleted shouldBe 0
        result.errors shouldBe 0
        (1..3).forEach { i ->
            statusStore["evt-e2e-$i"] shouldBe "COMPLETED"
        }
        failureRepo.size() shouldBe 0
    }

    // ===== jobId 전파 =====

    "jobId가 SinkPayload metadata에 포함됨" {
        var capturedMetadata: Map<String, String>? = null
        val capturingPlugin = object : SinkPlugin {
            override val pluginId = "capturing"
            override val capabilities = defaultCapabilities()
            override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
                capturedMetadata = (payloads.first() as SinkPayload.V1).metadata
                val results = payloads.map { p ->
                    SinkResult(p.idempotencyKey, SinkStatus.SUCCESS, Instant.now().toString())
                }
                return Either.Right(BatchResult(results, emptyList(), emptyList()))
            }
        }
        pluginRegistry.register("capturing", capturingPlugin)

        val record = insertRecord(
            id = "evt-job", tenantId = "t1", entityKey = "product:job1",
            version = 1L, viewType = "core",
            payload = """{"name":"With Job"}""",
            targets = listOf("capturing"),
            jobId = "JOB-12345",
        )

        processor.processBatch(listOf(record))

        capturedMetadata shouldNotBe null
        capturedMetadata!!["jobId"] shouldBe "JOB-12345"
    }

    // ===== MODIFY 이벤트 =====

    "MODIFY + PENDING → INSERT와 동일하게 처리" {
        pluginRegistry.register("opensearch", successPlugin("opensearch"))

        val record = StreamRecord(
            eventName = "MODIFY",
            newImage = SinkEventImage(
                id = "evt-modify", sk = "VERSION#1", tenantId = "t1",
                entityKey = "product:modify", version = 1L, viewType = "core",
                payload = """{"name":"Modified"}""",
                targets = listOf("opensearch"), status = "PENDING",
            ),
            oldImage = null,
        )

        val result = processor.processBatch(listOf(record))

        result.processed shouldBe 1
        statusStore["evt-modify"] shouldBe "COMPLETED"
    }
})

// ===== Test Helpers =====

private fun defaultCapabilities() = PluginCapabilities(
    supportedContractVersions = setOf("1.0"),
    supportsBatch = true,
    maxBatchSize = 10,
)

private fun successPlugin(id: String, supportsDelete: Boolean = false): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val supportsDelete = supportsDelete
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val results = payloads.map { p ->
            SinkResult(p.idempotencyKey, SinkStatus.SUCCESS, Instant.now().toString())
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }

    override suspend fun delete(
        tenantId: String, entityKey: String, metadata: Map<String, String>
    ): Either<SinkError, SinkResult> = Either.Right(
        SinkResult("$tenantId:$entityKey:delete", SinkStatus.SUCCESS, Instant.now().toString())
    )
}

private fun failingPlugin(id: String, error: SinkError): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val capabilities = defaultCapabilities()
    override suspend fun executeBatch(payloads: List<SinkPayload>) = Either.Left(error)
}

private fun deleteFailingPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val supportsDelete = true
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>) =
        Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))

    override suspend fun delete(
        tenantId: String, entityKey: String, metadata: Map<String, String>
    ) = Either.Left(SinkError.RetryableError(
        reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
        message = "Delete timeout"
    ))
}

private fun insertRecord(
    id: String,
    tenantId: String,
    entityKey: String,
    version: Long,
    viewType: String,
    payload: String,
    targets: List<String>,
    status: String = "PENDING",
    jobId: String? = null,
) = StreamRecord(
    eventName = "INSERT",
    newImage = SinkEventImage(
        id = id, sk = "VERSION#${Instant.now().toEpochMilli()}",
        tenantId = tenantId, entityKey = entityKey,
        version = version, viewType = viewType,
        payload = payload, targets = targets,
        status = status, jobId = jobId,
    ),
    oldImage = null,
)

private fun removeRecord(
    tenantId: String,
    entityKey: String,
    viewType: String,
    targets: List<String>,
) = StreamRecord(
    eventName = "REMOVE",
    newImage = null,
    oldImage = SinkEventImage(
        id = "", sk = "", tenantId = tenantId,
        entityKey = entityKey, version = 0L,
        viewType = viewType, payload = "{}",
        targets = targets, status = "PENDING",
    ),
)
