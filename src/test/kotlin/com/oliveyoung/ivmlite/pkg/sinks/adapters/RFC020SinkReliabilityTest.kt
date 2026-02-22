package com.oliveyoung.ivmlite.pkg.sinks.adapters

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.BatchResult
import com.oliveyoung.ivmlite.sinks.contract.ErrorCategory
import com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode
import com.oliveyoung.ivmlite.sinks.contract.InMemorySinkLedger
import com.oliveyoung.ivmlite.sinks.contract.LedgerStatus
import com.oliveyoung.ivmlite.sinks.contract.PluginCapabilities
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin
import com.oliveyoung.ivmlite.sinks.contract.SinkResult
import com.oliveyoung.ivmlite.sinks.contract.SinkStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * RFC-020: Sink Reliability & Data Consistency 단위 테스트
 *
 * 검증 항목:
 * - R1: DELETE 지원 (supportsDelete + delete())
 * - R2: 버전 충돌 방지 (OpenSearch external versioning - 구조 검증)
 * - R3: 실패 레코드 관리 (InMemorySinkFailureRepository)
 * - R4: SinkLedger 멱등성 (tryStart/complete/fail)
 */
class RFC020SinkReliabilityTest : FunSpec({

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // R1: DELETE 지원
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("R1: SinkPlugin 기본 supportsDelete = false") {
        val plugin = createSuccessPlugin("basic-plugin", supportsDelete = false)
        plugin.supportsDelete shouldBe false
    }

    test("R1: DELETE 지원 Plugin은 supportsDelete = true") {
        val plugin = createSuccessPlugin("delete-plugin", supportsDelete = true)
        plugin.supportsDelete shouldBe true
    }

    test("R1: delete() 미지원 Plugin은 NonRetryableError 반환") {
        val plugin = object : SinkPlugin {
            override val pluginId = "no-delete-plugin"
            override val capabilities = defaultCapabilities()
            override suspend fun executeBatch(payloads: List<SinkPayload>) =
                Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))
        }

        val result = plugin.delete("tenant1", "product:123")
        result.isLeft() shouldBe true
        val error = (result as Either.Left).value
        error shouldBe SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
            message = "Delete not supported by no-delete-plugin"
        )
    }

    test("R1: delete() 지원 Plugin은 성공 반환") {
        val plugin = createSuccessPlugin("opensearch-test", supportsDelete = true)

        val result = plugin.delete("tenant1", "product:123")
        result.isRight() shouldBe true
        result.getOrNull()!!.status shouldBe SinkStatus.SUCCESS
    }

    test("R1: PluginRegistry에서 supportsDelete Plugin만 delete 호출") {
        val deletePlugin = createSuccessPlugin("opensearch", supportsDelete = true)
        val noDeletePlugin = createSuccessPlugin("legacy", supportsDelete = false)
        val registry = InMemorySinkPluginRegistry(
            mapOf("opensearch" to deletePlugin, "legacy" to noDeletePlugin)
        )

        val targets = listOf("opensearch", "legacy")
        var deleteCount = 0

        targets.forEach { target ->
            val plugin = registry.resolve(target)
            if (plugin != null && plugin.supportsDelete) {
                plugin.delete("t1", "p:1")
                deleteCount++
            }
        }

        deleteCount shouldBe 1
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // R3: 실패 레코드 관리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("R3: 실패 레코드 저장 및 조회") {
        val repo = InMemorySinkFailureRepository()

        val record = com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "event-001",
            target = "opensearch",
            errorCategory = ErrorCategory.NON_RETRYABLE.name,
            errorReasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED.name,
            errorMessage = "OpenSearch unexpected status: 400",
            payload = """{"id":"123"}""",
            attemptCount = 1,
            createdAt = Instant.now().toString(),
        )

        val saveResult = repo.save(record)
        saveResult.isRight() shouldBe true
        repo.size() shouldBe 1

        val findResult = repo.findByTarget("opensearch")
        findResult.isRight() shouldBe true
        findResult.getOrNull()!!.size shouldBe 1
        findResult.getOrNull()!![0].sinkEventId shouldBe "event-001"
    }

    test("R3: 실패 레코드 상태 업데이트 (RETRIED)") {
        val repo = InMemorySinkFailureRepository()

        val record = com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "event-002",
            target = "s3",
            errorCategory = ErrorCategory.NON_RETRYABLE.name,
            errorReasonCode = ErrorReasonCode.PERMISSION_DENIED.name,
            errorMessage = "S3 access denied",
            payload = """{"id":"456"}""",
            attemptCount = 1,
            createdAt = Instant.now().toString(),
        )

        repo.save(record)
        repo.updateStatus("event-002", "s3", com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RETRIED)

        val records = repo.findByTarget("s3").getOrNull()!!
        records[0].status shouldBe com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RETRIED
    }

    test("R3: NonRetryable 에러만 실패 레코드에 저장 (Retryable은 저장 안함)") {
        val repo = InMemorySinkFailureRepository()

        val retryableError = SinkError.RetryableError(
            reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
            message = "timeout"
        )

        val nonRetryableError = SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
            message = "bad request"
        )

        // NonRetryable 에러는 실패 테이블에 저장
        val nonRetryable: SinkError = nonRetryableError
        shouldSaveFailure(nonRetryable) shouldBe true

        repo.save(
            com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
                sinkEventId = "event-003",
                target = "opensearch",
                errorCategory = nonRetryable.category.name,
                errorReasonCode = nonRetryable.reasonCode.name,
                errorMessage = nonRetryable.message,
                payload = "{}",
                attemptCount = 1,
                createdAt = Instant.now().toString(),
            )
        )

        repo.size() shouldBe 1

        // Retryable 에러는 Lambda throw로 재시도 → 실패 테이블 저장 안 함
        val retryable: SinkError = retryableError
        shouldSaveFailure(retryable) shouldBe false

        repo.size() shouldBe 1 // 여전히 1개
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // R4: SinkLedger 멱등성
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("R4: Ledger tryStart → 첫 호출은 true") {
        val ledger = InMemorySinkLedger()

        val result = ledger.tryStart("opensearch", "key-001", "digest-abc", "1.0")
        result.isRight() shouldBe true
        result.getOrNull() shouldBe true
    }

    test("R4: Ledger complete 후 tryStart → false (재처리 방지)") {
        val ledger = InMemorySinkLedger()
        val idempotencyKey = "key-002"
        val digest = "digest-xyz"

        // 1. tryStart
        ledger.tryStart("opensearch", idempotencyKey, digest, "1.0")

        // 2. complete
        ledger.complete(
            "opensearch", idempotencyKey,
            SinkResult(idempotencyKey = idempotencyKey, status = SinkStatus.SUCCESS, processedAt = Instant.now().toString())
        )

        // 3. 재시도 → false
        val retry = ledger.tryStart("opensearch", idempotencyKey, digest, "1.0")
        retry.isRight() shouldBe true
        retry.getOrNull() shouldBe false
    }

    test("R4: Ledger fail 후 tryStart → true (재시도 허용)") {
        val ledger = InMemorySinkLedger()
        val idempotencyKey = "key-003"
        val digest = "digest-123"

        // 1. tryStart
        ledger.tryStart("opensearch", idempotencyKey, digest, "1.0")

        // 2. fail
        ledger.fail(
            "opensearch", idempotencyKey,
            SinkError.RetryableError(reasonCode = ErrorReasonCode.NETWORK_TIMEOUT, message = "timeout"),
            1
        )

        // 3. 재시도 → true (FAILED 상태이므로 재시도 가능)
        val retry = ledger.tryStart("opensearch", idempotencyKey, digest, "1.0")
        retry.isRight() shouldBe true
        retry.getOrNull() shouldBe true
    }

    test("R4: Ledger getStatus → COMPLETED 확인") {
        val ledger = InMemorySinkLedger()
        val idempotencyKey = "key-004"
        val digest = "digest-final"

        ledger.tryStart("s3", idempotencyKey, digest, "1.0")
        ledger.complete(
            "s3", idempotencyKey,
            SinkResult(idempotencyKey = idempotencyKey, status = SinkStatus.SUCCESS, processedAt = Instant.now().toString())
        )

        val status = ledger.getStatus("s3", idempotencyKey)
        status.isRight() shouldBe true
        status.getOrNull() shouldNotBe null
        status.getOrNull()!!.status shouldBe LedgerStatus.COMPLETED
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // R2: 외부 버전화 (구조 검증)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("R2: SinkPayload.V1에 entityVersion 포함 확인") {
        val viewData = buildJsonObject { put("name", "Product A") }
        val digest = SinkPayload.computePayloadDigest(viewData)

        val payload = SinkPayload.V1(
            correlationId = "corr-001",
            timestamp = Instant.now().toString(),
            idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:1", 3L, "core", digest),
            payloadDigest = digest,
            tenantId = "t1",
            entityKey = "product:1",
            entityVersion = 3L,
            viewType = "core",
            viewData = viewData,
        )

        payload.entityVersion shouldBe 3L
    }

    test("R2: idempotencyKey는 버전 포함 (동일 엔티티 다른 버전 → 다른 키)") {
        val key1 = SinkPayload.generateIdempotencyKey("t1", "product:1", 1L, "core", "digest123")
        val key2 = SinkPayload.generateIdempotencyKey("t1", "product:1", 2L, "core", "digest123")
        val key3 = SinkPayload.generateIdempotencyKey("t1", "product:1", 1L, "core", "digest123")

        key1 shouldNotBe key2 // 다른 버전 → 다른 키
        key1 shouldBe key3    // 동일 버전 → 동일 키 (멱등성)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 통합: Ledger + Plugin + FailureRepo 흐름
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("통합: 정상 흐름 - Ledger tryStart → Plugin execute → Ledger complete") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val plugin = createSuccessPlugin("opensearch-test", supportsDelete = true)
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to plugin))

        val viewData = buildJsonObject { put("name", "Product A") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:1", 1L, "core", digest)
        val target = "opensearch"

        // 1. Ledger tryStart
        val canProcess = ledger.tryStart(target, idempotencyKey, digest, "1.0")
        canProcess.getOrNull() shouldBe true

        // 2. Plugin execute
        val sinkPayload = SinkPayload.V1(
            correlationId = "event-100",
            timestamp = Instant.now().toString(),
            idempotencyKey = idempotencyKey,
            payloadDigest = digest,
            tenantId = "t1",
            entityKey = "product:1",
            entityVersion = 1L,
            viewType = "core",
            viewData = viewData,
        )

        val result = registry.resolve(target)!!.execute(sinkPayload)
        result.isRight() shouldBe true

        // 3. Ledger complete
        ledger.complete(target, idempotencyKey, result.getOrNull()!!)

        // 4. 검증: Ledger COMPLETED, 실패 없음
        ledger.getStatus(target, idempotencyKey).getOrNull()!!.status shouldBe LedgerStatus.COMPLETED
        failureRepo.size() shouldBe 0
    }

    test("통합: 실패 흐름 - Ledger tryStart → Plugin fail → Ledger fail + Failure 저장") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val plugin = createFailingPlugin("opensearch-fail", SinkError.NonRetryableError(
            reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
            message = "Bad request"
        ))
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to plugin))

        val viewData = buildJsonObject { put("name", "Product B") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:2", 1L, "core", digest)
        val target = "opensearch"
        val sinkEventId = "event-200"

        // 1. Ledger tryStart
        val canProcess = ledger.tryStart(target, idempotencyKey, digest, "1.0")
        canProcess.getOrNull() shouldBe true

        // 2. Plugin execute → 실패
        val sinkPayload = SinkPayload.V1(
            correlationId = sinkEventId,
            timestamp = Instant.now().toString(),
            idempotencyKey = idempotencyKey,
            payloadDigest = digest,
            tenantId = "t1",
            entityKey = "product:2",
            entityVersion = 1L,
            viewType = "core",
            viewData = viewData,
        )

        val result = registry.resolve(target)!!.execute(sinkPayload)
        result.isLeft() shouldBe true

        val error = (result as Either.Left).value

        // 3. Ledger fail
        ledger.fail(target, idempotencyKey, error, 1)

        // 4. NonRetryable → 실패 레코드 저장
        if (shouldSaveFailure(error)) {
            failureRepo.save(
                com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
                    sinkEventId = sinkEventId,
                    target = target,
                    errorCategory = error.category.name,
                    errorReasonCode = error.reasonCode.name,
                    errorMessage = error.message,
                    payload = "{}",
                    attemptCount = 1,
                    createdAt = Instant.now().toString(),
                )
            )
        }

        // 5. 검증
        ledger.getStatus(target, idempotencyKey).getOrNull()!!.status shouldBe LedgerStatus.FAILED
        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].errorCategory shouldBe ErrorCategory.NON_RETRYABLE.name
    }

    test("통합: 멱등 재실행 - 완료된 이벤트 재처리 시 스킵") {
        val ledger = InMemorySinkLedger()
        val plugin = createSuccessPlugin("opensearch-test")
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to plugin))

        val viewData = buildJsonObject { put("name", "Product C") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:3", 1L, "core", digest)
        val target = "opensearch"

        // 1차 처리
        ledger.tryStart(target, idempotencyKey, digest, "1.0")
        val sinkPayload = SinkPayload.V1(
            correlationId = "event-300",
            timestamp = Instant.now().toString(),
            idempotencyKey = idempotencyKey,
            payloadDigest = digest,
            tenantId = "t1",
            entityKey = "product:3",
            entityVersion = 1L,
            viewType = "core",
            viewData = viewData,
        )
        val result = registry.resolve(target)!!.execute(sinkPayload)
        ledger.complete(target, idempotencyKey, result.getOrNull()!!)

        // 2차 처리 (멱등 스킵)
        val retry = ledger.tryStart(target, idempotencyKey, digest, "1.0")
        retry.getOrNull() shouldBe false // COMPLETED → 스킵
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: 멀티 타겟 시나리오
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: 멀티 타겟 - 모든 target 성공 → 전체 COMPLETED") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val registry = InMemorySinkPluginRegistry(mapOf(
            "opensearch" to createSuccessPlugin("opensearch"),
            "s3" to createSuccessPlugin("s3"),
        ))

        val viewData = buildJsonObject { put("name", "Multi Target Product") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val targets = listOf("opensearch", "s3")
        var allSucceeded = true

        targets.forEach { target ->
            val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:10", 1L, "core", digest)
            val canProcess = ledger.tryStart(target, idempotencyKey, digest, "1.0")
            canProcess.getOrNull() shouldBe true

            val payload = createPayload("t1", "product:10", 1L, "core", viewData, "evt-multi-ok")
            val result = registry.resolve(target)!!.execute(payload)
            result.fold(
                { allSucceeded = false },
                { ledger.complete(target, idempotencyKey, it) }
            )
        }

        allSucceeded shouldBe true
        failureRepo.size() shouldBe 0
        // R5: 전부 성공 → COMPLETED
        val status = if (allSucceeded) "COMPLETED" else "FAILED"
        status shouldBe "COMPLETED"
    }

    test("E2E: 멀티 타겟 - 일부 NonRetryable 실패 → FAILED + 실패 저장") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val registry = InMemorySinkPluginRegistry(mapOf(
            "opensearch" to createSuccessPlugin("opensearch"),
            "s3" to createFailingPlugin("s3-fail", SinkError.NonRetryableError(
                reasonCode = ErrorReasonCode.PERMISSION_DENIED,
                message = "S3 access denied",
            )),
        ))

        val viewData = buildJsonObject { put("name", "Partial Fail Product") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val targets = listOf("opensearch", "s3")
        var allSucceeded = true
        var hasRetryable = false
        val sinkEventId = "evt-partial-fail"

        targets.forEach { target ->
            val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:11", 1L, "core", digest)
            ledger.tryStart(target, idempotencyKey, digest, "1.0")

            val payload = createPayload("t1", "product:11", 1L, "core", viewData, sinkEventId)
            val result = registry.resolve(target)!!.execute(payload)

            result.fold(
                { error ->
                    ledger.fail(target, idempotencyKey, error, 1)
                    allSucceeded = false
                    when (error) {
                        is SinkError.RetryableError -> hasRetryable = true
                        is SinkError.NonRetryableError, is SinkError.PoisonPillError -> {
                            failureRepo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
                                sinkEventId = sinkEventId, target = target,
                                errorCategory = error.category.name,
                                errorReasonCode = error.reasonCode.name,
                                errorMessage = error.message, payload = "{}",
                                attemptCount = 1, createdAt = Instant.now().toString(),
                            ))
                        }
                    }
                },
                { ledger.complete(target, idempotencyKey, it) }
            )
        }

        allSucceeded shouldBe false
        hasRetryable shouldBe false
        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].target shouldBe "s3"
        // R5: NonRetryable 실패 → FAILED (Retryable 없으므로 throw 안 함)
        val status = if (hasRetryable) "PENDING" else if (allSucceeded) "COMPLETED" else "FAILED"
        status shouldBe "FAILED"
    }

    test("E2E: 멀티 타겟 - Retryable 실패 → Lambda throw (재시도 트리거)") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val registry = InMemorySinkPluginRegistry(mapOf(
            "opensearch" to createSuccessPlugin("opensearch"),
            "s3" to createFailingPlugin("s3-timeout", SinkError.RetryableError(
                reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                message = "S3 timeout",
            )),
        ))

        val viewData = buildJsonObject { put("name", "Retryable Product") }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val targets = listOf("opensearch", "s3")
        var hasRetryable = false

        targets.forEach { target ->
            val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:12", 1L, "core", digest)
            ledger.tryStart(target, idempotencyKey, digest, "1.0")

            val payload = createPayload("t1", "product:12", 1L, "core", viewData, "evt-retryable")
            val result = registry.resolve(target)!!.execute(payload)

            result.fold(
                { error ->
                    ledger.fail(target, idempotencyKey, error, 1)
                    when (error) {
                        is SinkError.RetryableError -> hasRetryable = true
                        is SinkError.NonRetryableError, is SinkError.PoisonPillError -> {
                            failureRepo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
                                sinkEventId = "evt-retryable", target = target,
                                errorCategory = error.category.name,
                                errorReasonCode = error.reasonCode.name,
                                errorMessage = error.message, payload = "{}",
                                attemptCount = 1, createdAt = Instant.now().toString(),
                            ))
                        }
                    }
                },
                { ledger.complete(target, idempotencyKey, it) }
            )
        }

        hasRetryable shouldBe true
        // Retryable 있으면 실패 테이블 저장 안 함 → Lambda throw로 DynamoDB Streams 재시도
        failureRepo.size() shouldBe 0
        // R5: Retryable 실패 → 상태 갱신 안 함 (PENDING 유지)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: PoisonPill 에러
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: PoisonPill 에러 → 실패 저장 + 재시도 안 함") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()
        val plugin = createFailingPlugin("opensearch-poison", SinkError.PoisonPillError(
            reasonCode = ErrorReasonCode.DESERIALIZATION_FAILED,
            message = "Malformed JSON payload",
        ))
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to plugin))

        val viewData = buildJsonObject { put("broken", true) }
        val digest = SinkPayload.computePayloadDigest(viewData)
        val idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:99", 1L, "core", digest)
        val sinkEventId = "evt-poison"

        ledger.tryStart("opensearch", idempotencyKey, digest, "1.0")
        val payload = createPayload("t1", "product:99", 1L, "core", viewData, sinkEventId)
        val result = registry.resolve("opensearch")!!.execute(payload)

        result.isLeft() shouldBe true
        val error = (result as Either.Left).value
        error.category shouldBe ErrorCategory.POISON_PILL

        ledger.fail("opensearch", idempotencyKey, error, 1)
        // PoisonPill → 실패 저장 (재시도 무의미)
        shouldSaveFailure(error) shouldBe true
        failureRepo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = sinkEventId, target = "opensearch",
            errorCategory = error.category.name,
            errorReasonCode = error.reasonCode.name,
            errorMessage = error.message, payload = "{}",
            attemptCount = 1, createdAt = Instant.now().toString(),
        ))

        failureRepo.size() shouldBe 1
        failureRepo.allRecords()[0].errorCategory shouldBe ErrorCategory.POISON_PILL.name
        ledger.getStatus("opensearch", idempotencyKey).getOrNull()!!.status shouldBe LedgerStatus.FAILED
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: Batch 처리 (executeBatch)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: executeBatch 다건 처리 → partial failure") {
        val plugin = createPartialFailPlugin("opensearch-partial")

        val payloads = (1..5).map { i ->
            val viewData = buildJsonObject { put("name", "Product $i") }
            val digest = SinkPayload.computePayloadDigest(viewData)
            SinkPayload.V1(
                correlationId = "batch-$i",
                timestamp = Instant.now().toString(),
                idempotencyKey = SinkPayload.generateIdempotencyKey("t1", "product:$i", 1L, "core", digest),
                payloadDigest = digest,
                tenantId = "t1", entityKey = "product:$i", entityVersion = 1L,
                viewType = "core", viewData = viewData,
            )
        }

        val result = plugin.executeBatch(payloads)
        result.isRight() shouldBe true
        val batchResult = result.getOrNull()!!

        // 짝수 인덱스 성공(0,2,4), 홀수 인덱스 실패(1,3)
        batchResult.succeeded.size shouldBe 3
        batchResult.nonRetryableFailed.size shouldBe 2
        batchResult.retryableFailed.size shouldBe 0
        batchResult.hasFailures shouldBe true
        batchResult.totalCount shouldBe 5
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: Ledger digest 충돌
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: Ledger digest 충돌 → 동일 key, 다른 digest → error") {
        val ledger = InMemorySinkLedger()

        // 1차: digest-A로 tryStart
        val result1 = ledger.tryStart("opensearch", "key-conflict", "digest-A", "1.0")
        result1.getOrNull() shouldBe true

        // 완료
        ledger.complete("opensearch", "key-conflict",
            SinkResult(idempotencyKey = "key-conflict", status = SinkStatus.SUCCESS, processedAt = Instant.now().toString()))

        // 2차: 같은 key, 다른 digest → error (business rule violation)
        val result2 = ledger.tryStart("opensearch", "key-conflict", "digest-B", "1.0")
        result2.isLeft() shouldBe true
        val error = (result2 as Either.Left).value
        error.reasonCode shouldBe ErrorReasonCode.BUSINESS_RULE_VIOLATION
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: REMOVE + DELETE 시나리오
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: REMOVE → delete 실패 시 에러 카운트 증가") {
        val failingDeletePlugin = createDeleteFailingPlugin("opensearch-del-fail")
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to failingDeletePlugin))

        val targets = listOf("opensearch")
        var errorCount = 0
        var deleteCount = 0

        targets.forEach { target ->
            val plugin = registry.resolve(target) ?: return@forEach
            if (!plugin.supportsDelete) return@forEach

            val result = plugin.delete("t1", "product:deleted")
            result.fold(
                { errorCount++ },
                { deleteCount++ }
            )
        }

        errorCount shouldBe 1
        deleteCount shouldBe 0
    }

    test("E2E: REMOVE → ALREADY_PROCESSED (이미 삭제된 문서)") {
        val alreadyDeletedPlugin = createAlreadyDeletedPlugin("opensearch-idempotent")
        val registry = InMemorySinkPluginRegistry(mapOf("opensearch" to alreadyDeletedPlugin))

        val result = registry.resolve("opensearch")!!.delete("t1", "product:gone")
        result.isRight() shouldBe true
        result.getOrNull()!!.status shouldBe SinkStatus.ALREADY_PROCESSED
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: 미등록 target / 빈 targets
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: 미등록 target → 스킵 (에러 카운트 증가)") {
        val registry = InMemorySinkPluginRegistry(mapOf(
            "opensearch" to createSuccessPlugin("opensearch"),
        ))

        val targets = listOf("opensearch", "unknown-target", "another-missing")
        var processedCount = 0
        var skipCount = 0

        targets.forEach { target ->
            val plugin = registry.resolve(target)
            if (plugin == null) {
                skipCount++
                return@forEach
            }
            processedCount++
        }

        processedCount shouldBe 1
        skipCount shouldBe 2
    }

    test("E2E: 빈 sinkTargets → 아무것도 실행 안 함") {
        val ledger = InMemorySinkLedger()
        val failureRepo = InMemorySinkFailureRepository()

        val targets = emptyList<String>()
        var processedCount = 0

        targets.forEach { _ -> processedCount++ }

        processedCount shouldBe 0
        failureRepo.size() shouldBe 0
        ledger.size() shouldBe 0
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: 버전 순서 역전 시 Ledger 동작
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: v1, v2 순서 처리 → 각각 별도 idempotencyKey → 둘 다 처리") {
        val ledger = InMemorySinkLedger()
        val plugin = createSuccessPlugin("opensearch")

        val viewData = buildJsonObject { put("name", "Versioned Product") }
        val digest = SinkPayload.computePayloadDigest(viewData)

        // v1 처리
        val keyV1 = SinkPayload.generateIdempotencyKey("t1", "product:20", 1L, "core", digest)
        ledger.tryStart("opensearch", keyV1, digest, "1.0").getOrNull() shouldBe true
        val payloadV1 = createPayload("t1", "product:20", 1L, "core", viewData, "evt-v1")
        val resultV1 = plugin.execute(payloadV1)
        ledger.complete("opensearch", keyV1, resultV1.getOrNull()!!)

        // v2 처리
        val keyV2 = SinkPayload.generateIdempotencyKey("t1", "product:20", 2L, "core", digest)
        ledger.tryStart("opensearch", keyV2, digest, "1.0").getOrNull() shouldBe true
        val payloadV2 = createPayload("t1", "product:20", 2L, "core", viewData, "evt-v2")
        val resultV2 = plugin.execute(payloadV2)
        ledger.complete("opensearch", keyV2, resultV2.getOrNull()!!)

        // 둘 다 COMPLETED (각각 다른 키)
        keyV1 shouldNotBe keyV2
        ledger.getStatus("opensearch", keyV1).getOrNull()!!.status shouldBe LedgerStatus.COMPLETED
        ledger.getStatus("opensearch", keyV2).getOrNull()!!.status shouldBe LedgerStatus.COMPLETED
    }

    test("E2E: v2 먼저 처리, v1 늦게 도착 → 둘 다 Ledger에 기록 (OpenSearch external versioning이 구버전 drop)") {
        val ledger = InMemorySinkLedger()
        val plugin = createSuccessPlugin("opensearch")

        val viewData = buildJsonObject { put("name", "Out of Order Product") }
        val digest = SinkPayload.computePayloadDigest(viewData)

        // v2 먼저 도착
        val keyV2 = SinkPayload.generateIdempotencyKey("t1", "product:21", 2L, "core", digest)
        ledger.tryStart("opensearch", keyV2, digest, "1.0")
        val payloadV2 = createPayload("t1", "product:21", 2L, "core", viewData, "evt-v2-first")
        ledger.complete("opensearch", keyV2, plugin.execute(payloadV2).getOrNull()!!)

        // v1 늦게 도착 → Ledger는 다른 키이므로 통과 (OpenSearch가 version_type:external로 drop)
        val keyV1 = SinkPayload.generateIdempotencyKey("t1", "product:21", 1L, "core", digest)
        val canProcess = ledger.tryStart("opensearch", keyV1, digest, "1.0")
        canProcess.getOrNull() shouldBe true // Ledger 레벨에선 허용 (다른 키)

        // 실제 OpenSearch에서는 v1 < v2이므로 409 Conflict → ALREADY_PROCESSED
        // 이 부분은 OpenSearch 외부 버전화가 담당 (Ledger는 키 기반 멱등성만 책임)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // E2E: Failure Repository 재처리 흐름
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    test("E2E: 실패 저장 → 상태 RETRIED → RESOLVED 전이") {
        val repo = InMemorySinkFailureRepository()

        // 1. 실패 저장
        repo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "evt-lifecycle", target = "opensearch",
            errorCategory = ErrorCategory.NON_RETRYABLE.name,
            errorReasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED.name,
            errorMessage = "Bad mapping", payload = """{"id":"lc-1"}""",
            attemptCount = 1, createdAt = Instant.now().toString(),
        ))

        repo.allRecords()[0].status shouldBe com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.FAILED

        // 2. 재처리 시도 → RETRIED
        repo.updateStatus("evt-lifecycle", "opensearch", com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RETRIED)
        repo.allRecords()[0].status shouldBe com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RETRIED

        // 3. 해결 → RESOLVED
        repo.updateStatus("evt-lifecycle", "opensearch", com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RESOLVED)
        repo.allRecords()[0].status shouldBe com.oliveyoung.ivmlite.pkg.sinks.ports.FailureStatus.RESOLVED
    }

    test("E2E: 실패 레코드 다건 저장 → target별 조회") {
        val repo = InMemorySinkFailureRepository()
        val now = Instant.now()

        // opensearch 실패 2건
        repo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "evt-a", target = "opensearch",
            errorCategory = ErrorCategory.NON_RETRYABLE.name,
            errorReasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED.name,
            errorMessage = "err-a", payload = "{}", attemptCount = 1,
            createdAt = now.toString(),
        ))
        repo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "evt-b", target = "opensearch",
            errorCategory = ErrorCategory.POISON_PILL.name,
            errorReasonCode = ErrorReasonCode.DESERIALIZATION_FAILED.name,
            errorMessage = "err-b", payload = "{}", attemptCount = 1,
            createdAt = now.plusSeconds(1).toString(),
        ))

        // s3 실패 1건
        repo.save(com.oliveyoung.ivmlite.pkg.sinks.ports.SinkFailureRecord(
            sinkEventId = "evt-c", target = "s3",
            errorCategory = ErrorCategory.NON_RETRYABLE.name,
            errorReasonCode = ErrorReasonCode.PERMISSION_DENIED.name,
            errorMessage = "err-c", payload = "{}", attemptCount = 1,
            createdAt = now.toString(),
        ))

        repo.size() shouldBe 3

        val osRecords = repo.findByTarget("opensearch").getOrNull()!!
        osRecords.size shouldBe 2

        val s3Records = repo.findByTarget("s3").getOrNull()!!
        s3Records.size shouldBe 1
        s3Records[0].sinkEventId shouldBe "evt-c"

        val emptyRecords = repo.findByTarget("personalize").getOrNull()!!
        emptyRecords.size shouldBe 0
    }

    test("통합: REMOVE 이벤트 → supportsDelete Plugin만 delete 호출") {
        val opensearchPlugin = createSuccessPlugin("opensearch", supportsDelete = true)
        val legacyPlugin = createSuccessPlugin("legacy", supportsDelete = false)
        val registry = InMemorySinkPluginRegistry(
            mapOf("opensearch" to opensearchPlugin, "legacy" to legacyPlugin)
        )

        val sinkTargets = listOf("opensearch", "legacy")
        val deleteResults = mutableListOf<String>()

        sinkTargets.forEach { target ->
            val plugin = registry.resolve(target) ?: return@forEach
            if (plugin.supportsDelete) {
                val result = plugin.delete("t1", "product:1")
                if (result.isRight()) {
                    deleteResults.add(target)
                }
            }
        }

        deleteResults shouldBe listOf("opensearch") // legacy는 호출 안 됨
    }
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Test Helpers
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private fun defaultCapabilities() = PluginCapabilities(
    supportedContractVersions = setOf("1.0"),
    supportsBatch = true,
    maxBatchSize = 10,
)

private fun createSuccessPlugin(
    id: String,
    supportsDelete: Boolean = false
): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val supportsDelete = supportsDelete
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val results = payloads.map { payload ->
            SinkResult(
                idempotencyKey = payload.idempotencyKey,
                status = SinkStatus.SUCCESS,
                processedAt = Instant.now().toString(),
            )
        }
        return Either.Right(BatchResult(results, emptyList(), emptyList()))
    }

    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> = Either.Right(
        SinkResult(
            idempotencyKey = "$tenantId:$entityKey:delete",
            status = SinkStatus.SUCCESS,
            processedAt = Instant.now().toString(),
        )
    )
}

private fun shouldSaveFailure(error: SinkError): Boolean = when (error) {
    is SinkError.NonRetryableError, is SinkError.PoisonPillError -> true
    is SinkError.RetryableError -> false
}

private fun createPayload(
    tenantId: String,
    entityKey: String,
    entityVersion: Long,
    viewType: String,
    viewData: JsonObject,
    correlationId: String,
): SinkPayload.V1 {
    val digest = SinkPayload.computePayloadDigest(viewData)
    return SinkPayload.V1(
        correlationId = correlationId,
        timestamp = Instant.now().toString(),
        idempotencyKey = SinkPayload.generateIdempotencyKey(tenantId, entityKey, entityVersion, viewType, digest),
        payloadDigest = digest,
        tenantId = tenantId,
        entityKey = entityKey,
        entityVersion = entityVersion,
        viewType = viewType,
        viewData = viewData,
    )
}

private fun createFailingPlugin(
    id: String,
    error: SinkError
): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> =
        Either.Left(error)
}

private fun createPartialFailPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> {
        val succeeded = mutableListOf<SinkResult>()
        val nonRetryableFailed = mutableListOf<BatchResult.FailedItem>()

        payloads.forEachIndexed { idx, payload ->
            if (idx % 2 == 0) {
                succeeded.add(SinkResult(
                    idempotencyKey = payload.idempotencyKey,
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                ))
            } else {
                nonRetryableFailed.add(BatchResult.FailedItem(
                    idempotencyKey = payload.idempotencyKey,
                    error = SinkError.NonRetryableError(
                        reasonCode = ErrorReasonCode.PLUGIN_EXECUTION_FAILED,
                        message = "Item $idx failed",
                    )
                ))
            }
        }

        return Either.Right(BatchResult(succeeded, emptyList(), nonRetryableFailed))
    }
}

private fun createDeleteFailingPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val supportsDelete = true
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> =
        Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))

    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> = Either.Left(SinkError.RetryableError(
        reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
        message = "Delete timeout for $tenantId:$entityKey",
    ))
}

private fun createAlreadyDeletedPlugin(id: String): SinkPlugin = object : SinkPlugin {
    override val pluginId = id
    override val supportsDelete = true
    override val capabilities = defaultCapabilities()

    override suspend fun executeBatch(payloads: List<SinkPayload>): Either<SinkError, BatchResult> =
        Either.Right(BatchResult(emptyList(), emptyList(), emptyList()))

    override suspend fun delete(
        tenantId: String,
        entityKey: String,
        metadata: Map<String, String>
    ): Either<SinkError, SinkResult> = Either.Right(SinkResult(
        idempotencyKey = "$tenantId:$entityKey:delete",
        status = SinkStatus.ALREADY_PROCESSED,
        processedAt = Instant.now().toString(),
    ))
}
