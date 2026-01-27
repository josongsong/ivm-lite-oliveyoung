package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * JooqOutboxRepository 통합 테스트 (PostgreSQL Testcontainers)
 *
 * RFC-IMPL Phase B-4: Outbox 어댑터 실제 DB 검증
 *
 * Docker 없으면 자동 스킵됨.
 * 실행: ./gradlew integrationTest
 */
@EnabledIf(DockerEnabledCondition::class)
class JooqOutboxRepositoryIntegrationTest : StringSpec({

    tags(IntegrationTag)

    val dsl = PostgresTestContainer.start()
    val repository: OutboxRepositoryPort = JooqOutboxRepository(dsl)

    beforeEach {
        dsl.execute("TRUNCATE TABLE outbox CASCADE")
    }

    "insert - 새 엔트리 저장 성공" {
        val entry = createTestEntry("tenant-1", "entity-1")

        val result = runBlocking { repository.insert(entry) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<OutboxEntry>>()
        (result as OutboxRepositoryPort.Result.Ok).value.id shouldBe entry.id
    }

    "insert - 중복 ID → IdempotencyViolation" {
        val entry = createTestEntry("tenant-1", "entity-2")
        runBlocking { repository.insert(entry) }

        val result = runBlocking { repository.insert(entry) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Err>()
        (result as OutboxRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.IdempotencyViolation>()
    }

    "insertAll - 배치 저장 성공" {
        val entries = (1..5).map { createTestEntry("tenant-1", "entity-batch-$it") }

        val result = runBlocking { repository.insertAll(entries) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<List<OutboxEntry>>>()
        (result as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 5
    }

    "findById - 존재하는 엔트리 조회" {
        val entry = createTestEntry("tenant-1", "entity-3")
        runBlocking { repository.insert(entry) }

        val result = runBlocking { repository.findById(entry.id) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<OutboxEntry>>()
        val found = (result as OutboxRepositoryPort.Result.Ok).value
        found.aggregateId shouldBe entry.aggregateId
        found.eventType shouldBe entry.eventType
    }

    "findById - 존재하지 않는 엔트리 → NotFoundError" {
        val result = runBlocking { repository.findById(java.util.UUID.randomUUID()) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Err>()
        (result as OutboxRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "findPending - PENDING 상태만 조회" {
        val pending1 = createTestEntry("tenant-1", "pending-1")
        val pending2 = createTestEntry("tenant-1", "pending-2")
        runBlocking { repository.insertAll(listOf(pending1, pending2)) }
        runBlocking { repository.markProcessed(listOf(pending1.id)) }

        val result = runBlocking { repository.findPending(10) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<List<OutboxEntry>>>()
        val pendingList = (result as OutboxRepositoryPort.Result.Ok).value
        pendingList shouldHaveSize 1
        pendingList[0].id shouldBe pending2.id
    }

    "findPending - limit 적용" {
        val entries = (1..10).map { createTestEntry("tenant-1", "limit-$it") }
        runBlocking { repository.insertAll(entries) }

        val result = runBlocking { repository.findPending(3) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<List<OutboxEntry>>>()
        (result as OutboxRepositoryPort.Result.Ok).value shouldHaveSize 3
    }

    "findPendingByType - 특정 AggregateType만 조회" {
        val rawDataEntry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:entity-raw",
            eventType = "RawDataIngested",
            payload = """{"test": true}""",
        )
        val sliceEntry = OutboxEntry.create(
            aggregateType = AggregateType.SLICE,
            aggregateId = "tenant-1:entity-slice",
            eventType = "SliceCreated",
            payload = """{"test": true}""",
        )
        runBlocking { repository.insertAll(listOf(rawDataEntry, sliceEntry)) }

        val result = runBlocking { repository.findPendingByType(AggregateType.RAW_DATA, 10) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<List<OutboxEntry>>>()
        val list = (result as OutboxRepositoryPort.Result.Ok).value
        list shouldHaveSize 1
        list[0].aggregateType shouldBe AggregateType.RAW_DATA
    }

    "markProcessed - 상태 변경 및 processedAt 설정" {
        val entry = createTestEntry("tenant-1", "to-process")
        runBlocking { repository.insert(entry) }

        val result = runBlocking { repository.markProcessed(listOf(entry.id)) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<Int>>()
        (result as OutboxRepositoryPort.Result.Ok).value shouldBe 1

        // 상태 확인
        val found = runBlocking { repository.findById(entry.id) }
        found.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<OutboxEntry>>()
        (found as OutboxRepositoryPort.Result.Ok).value.status shouldBe OutboxStatus.PROCESSED
        found.value.processedAt shouldNotBe null
    }

    "markFailed - FAILED 상태 및 retryCount 증가" {
        val entry = createTestEntry("tenant-1", "to-fail")
        runBlocking { repository.insert(entry) }

        val result = runBlocking { repository.markFailed(entry.id, "Test failure") }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<OutboxEntry>>()
        val failed = (result as OutboxRepositoryPort.Result.Ok).value
        failed.status shouldBe OutboxStatus.FAILED
        failed.retryCount shouldBe 1
    }

    "resetToPending - FAILED → PENDING 복구" {
        val entry = createTestEntry("tenant-1", "to-reset")
        runBlocking { repository.insert(entry) }
        runBlocking { repository.markFailed(entry.id, "First failure") }

        val result = runBlocking { repository.resetToPending(entry.id) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<OutboxEntry>>()
        (result as OutboxRepositoryPort.Result.Ok).value.status shouldBe OutboxStatus.PENDING
    }

    "resetToPending - MAX_RETRY_COUNT 초과시 에러" {
        val entry = createTestEntry("tenant-1", "max-retry")
        runBlocking { repository.insert(entry) }

        // MAX_RETRY_COUNT(3)만큼 실패 처리
        repeat(OutboxEntry.MAX_RETRY_COUNT) {
            runBlocking { repository.markFailed(entry.id, "Failure $it") }
            if (it < OutboxEntry.MAX_RETRY_COUNT - 1) {
                runBlocking { repository.resetToPending(entry.id) }
            }
        }

        val result = runBlocking { repository.resetToPending(entry.id) }

        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Err>()
        (result as OutboxRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
    }
})

private fun createTestEntry(
    tenantId: String,
    entityKey: String,
): OutboxEntry {
    return OutboxEntry.create(
        aggregateType = AggregateType.RAW_DATA,
        aggregateId = "$tenantId:$entityKey",
        eventType = "RawDataIngested",
        payload = """{"payloadVersion":"1.0","tenantId": "$tenantId", "entityKey": "$entityKey", "version": 1}""",
    )
}
