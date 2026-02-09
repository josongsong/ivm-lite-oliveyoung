package com.oliveyoung.ivmlite.pkg.orchestration
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.runBlocking

/**
 * IngestWorkflow 단위 테스트 (RFC-IMPL-003)
 * 
 * 테스트 범위:
 * - 결정성: 동일 입력 → 동일 해시
 * - 멱등성: 같은 데이터 2번 저장 → OK
 * - 충돌: 같은 key/version, 다른 payload → Err
 */
class IngestWorkflowTest : StringSpec({

    val testTracer = OpenTelemetry.noop().getTracer("test")
    val rawRepository = InMemoryRawDataRepository()
    val outboxRepository = InMemoryOutboxRepository()
    val workflow = IngestWorkflow(rawRepository, outboxRepository, testTracer)

    val tenantId = TenantId("tenant-1")
    val entityKey = EntityKey("PRODUCT#tenant-1#product-123")
    val schemaId = "product.v1"
    val schemaVersion = SemVer.parse("1.0.0")

    "성공: 새 데이터 저장" {
        val result = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name": "Product A", "price": 100}"""
        )
        
        result.shouldBeInstanceOf<Result.Ok<Unit>>()
    }

    "결정성: 동일 JSON 구조 다른 whitespace → 동일 해시" {
        // 첫 번째 저장
        val result1 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"name":"Test","value":42}"""
        )
        result1.shouldBeInstanceOf<Result.Ok<Unit>>()

        // 같은 JSON, 다른 whitespace → 멱등 OK (해시 동일해야 함)
        val result2 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 2L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{ "name" : "Test" , "value" : 42 }"""
        )
        result2.shouldBeInstanceOf<Result.Ok<Unit>>()
    }

    "멱등성: 같은 데이터 2번 저장 → OK" {
        val payload = """{"id": "idempotent-test", "count": 1}"""
        
        val result1 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 3L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = payload
        )
        result1.shouldBeInstanceOf<Result.Ok<Unit>>()

        val result2 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 3L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = payload
        )
        result2.shouldBeInstanceOf<Result.Ok<Unit>>()
    }

    "충돌: 같은 key/version, 다른 payload → Err" {
        // 첫 번째 저장
        val result1 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 4L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"data": "original"}"""
        )
        result1.shouldBeInstanceOf<Result.Ok<Unit>>()

        // 같은 key/version, 다른 payload → 충돌
        val result2 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 4L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"data": "different"}"""
        )
        result2.shouldBeInstanceOf<Result.Err>()
    }

    "충돌: 같은 payload, 다른 schemaVersion → Err" {
        val payload = """{"test": "schema-conflict"}"""
        
        // 첫 번째 저장
        val result1 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 5L,
            schemaId = schemaId,
            schemaVersion = SemVer.parse("1.0.0"),
            payloadJson = payload
        )
        result1.shouldBeInstanceOf<Result.Ok<Unit>>()

        // 같은 payload, 다른 schemaVersion → 충돌
        val result2 = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 5L,
            schemaId = schemaId,
            schemaVersion = SemVer.parse("2.0.0"),
            payloadJson = payload
        )
        result2.shouldBeInstanceOf<Result.Err>()
    }

    "실패: 잘못된 JSON → Err" {
        val result = workflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = 999L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """not a valid json"""
        )
        result.shouldBeInstanceOf<Result.Err>()
    }

    "Outbox 통합: Ingest 성공 시 Outbox에 이벤트 저장" {
        val freshRawRepo = InMemoryRawDataRepository()
        val freshOutboxRepo = InMemoryOutboxRepository()
        val freshWorkflow = IngestWorkflow(freshRawRepo, freshOutboxRepo)

        val result = freshWorkflow.execute(
            tenantId = TenantId("outbox-tenant"),
            entityKey = EntityKey("outbox-entity"),
            version = 1L,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = """{"test": "outbox"}"""
        )
        result.shouldBeInstanceOf<Result.Ok<Unit>>()

        // Outbox에 이벤트가 저장되었는지 확인
        val pending = runBlocking { freshOutboxRepo.findPending(10) }
        pending.shouldBeInstanceOf<Result.Ok<*>>()
        val entries = (pending as Result.Ok).value
        entries shouldHaveSize 1
        entries[0].aggregateType shouldBe AggregateType.RAW_DATA
        entries[0].aggregateId shouldBe "outbox-tenant:outbox-entity"
        entries[0].eventType shouldBe "RawDataIngested"
        entries[0].status shouldBe OutboxStatus.PENDING
    }
})
