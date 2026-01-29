package com.oliveyoung.ivmlite.unit

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.util.UUID

/**
 * Outbox 멱등성 및 결정성 테스트
 * 
 * SOTA 원칙:
 * - 멱등성: 동일 이벤트 중복 처리 방지
 * - 결정성: 동일 입력 → 동일 idempotencyKey
 * - 원자성: insertAll은 all-or-nothing
 * - 실패 추적: failureReason 기록
 */
class OutboxIdempotencyTest : StringSpec({

    lateinit var repo: InMemoryOutboxRepository
    
    beforeTest {
        repo = InMemoryOutboxRepository()
    }

    "idempotencyKey: 동일 입력 → 동일 key (결정성)" {
        val aggregateId = "tenant-1:product-123"
        val eventType = "RawDataIngested"
        val payload = """{"version":1}"""
        
        val key1 = OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload)
        val key2 = OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload)
        val key3 = OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload)
        
        key1 shouldBe key2
        key2 shouldBe key3
        key1.startsWith("idem_") shouldBe true
    }
    
    "idempotencyKey: 다른 입력 → 다른 key" {
        val key1 = OutboxEntry.generateIdempotencyKey("t:e1", "Event", "{}")
        val key2 = OutboxEntry.generateIdempotencyKey("t:e2", "Event", "{}")
        val key3 = OutboxEntry.generateIdempotencyKey("t:e1", "OtherEvent", "{}")
        val key4 = OutboxEntry.generateIdempotencyKey("t:e1", "Event", """{"diff":true}""")
        
        key1 shouldNotBe key2
        key1 shouldNotBe key3
        key1 shouldNotBe key4
    }
    
    "OutboxEntry.create: 동일 입력으로 두 번 생성해도 동일 idempotencyKey" {
        val entry1 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:product-123",
            eventType = "RawDataIngested",
            payload = """{"version":1}""",
        )
        
        val entry2 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "tenant-1:product-123",
            eventType = "RawDataIngested",
            payload = """{"version":1}""",
        )
        
        // ID는 다름 (UUID.randomUUID)
        entry1.id shouldNotBe entry2.id
        
        // idempotencyKey는 동일 (결정적)
        entry1.idempotencyKey shouldBe entry2.idempotencyKey
    }
    
    "OutboxEntry.create: timestamp 주입 가능 (테스트 용이성)" {
        val fixedTime = Instant.parse("2025-01-01T00:00:00Z")
        
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            timestamp = fixedTime,
        )
        
        entry.createdAt shouldBe fixedTime
    }
    
    "markFailed: failureReason 기록" {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )
        
        val failed = entry.markFailed("Connection timeout")
        
        failed.status shouldBe OutboxStatus.FAILED
        failed.retryCount shouldBe 1
        failed.failureReason shouldBe "Connection timeout"
    }
    
    "markFailed: 연속 실패 시 마지막 reason만 유지" {
        var entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )
        
        entry = entry.markFailed("First error")
        entry = entry.markFailed("Second error")
        entry = entry.markFailed("Third error")
        
        entry.retryCount shouldBe 3
        entry.failureReason shouldBe "Third error"
    }
    
    "InMemoryOutboxRepository: idempotencyKey 중복 시 insertAll 실패" {
        val entry1 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )
        
        // 첫 번째 삽입 성공
        val result1 = repo.insert(entry1)
        result1.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<*>>()
        
        // 동일 idempotencyKey로 다른 UUID의 entry 생성
        val entry2 = OutboxEntry(
            id = UUID.randomUUID(),  // 다른 ID
            idempotencyKey = entry1.idempotencyKey,  // 동일 key
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now(),
        )
        
        // 중복 삽입 시도
        val result2 = repo.insertAll(listOf(entry2))
        result2.shouldBeInstanceOf<OutboxRepositoryPort.Result.Err>()
        (result2 as OutboxRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.IdempotencyViolation>()
    }
    
    "InMemoryOutboxRepository: insertAll 원자성 - 일부 중복이면 전체 실패" {
        // 첫 번째 entry 삽입
        val entry1 = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e1",
            eventType = "Test",
            payload = "{}",
        )
        repo.insert(entry1)
        
        // 새 entry와 중복 entry를 함께 시도
        val newEntry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e2",
            eventType = "Test",
            payload = "{}",
        )
        val duplicateEntry = OutboxEntry(
            id = UUID.randomUUID(),
            idempotencyKey = entry1.idempotencyKey,
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e1",
            eventType = "Test",
            payload = "{}",
            status = OutboxStatus.PENDING,
            createdAt = Instant.now(),
        )
        
        val result = repo.insertAll(listOf(newEntry, duplicateEntry))
        
        // 전체 실패
        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Err>()
        
        // 원자성: newEntry도 삽입되지 않음
        repo.size() shouldBe 1  // entry1만 존재
    }
    
    "InMemoryOutboxRepository: markFailed로 reason 저장" {
        val entry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "t:e",
            eventType = "Test",
            payload = "{}",
        )
        repo.insert(entry)
        
        val result = repo.markFailed(entry.id, "Processing failed: timeout")
        
        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<*>>()
        val failed = (result as OutboxRepositoryPort.Result.Ok).value
        failed.failureReason shouldBe "Processing failed: timeout"
        failed.retryCount shouldBe 1
    }
    
    "InMemoryOutboxRepository: findPending은 PENDING만 반환" {
        val pending1 = OutboxEntry.create(AggregateType.RAW_DATA, "t:e1", "Test", "{}")
        val pending2 = OutboxEntry.create(AggregateType.RAW_DATA, "t:e2", "Test", "{}")
        val processed = OutboxEntry.create(AggregateType.RAW_DATA, "t:e3", "Test", "{}")
            .markProcessed()
        val failed = OutboxEntry.create(AggregateType.RAW_DATA, "t:e4", "Test", "{}")
            .markFailed("error")
        
        // processed와 failed는 직접 저장 (상태 변경 후)
        repo.insert(pending1)
        repo.insert(pending2)
        repo.insert(processed.copy(id = UUID.randomUUID(), idempotencyKey = "idem_processed"))
        repo.insert(failed.copy(id = UUID.randomUUID(), idempotencyKey = "idem_failed"))
        
        val result = repo.findPending(10)
        result.shouldBeInstanceOf<OutboxRepositoryPort.Result.Ok<*>>()
        
        val pending = (result as OutboxRepositoryPort.Result.Ok).value
        pending.size shouldBe 2
        pending.all { it.status == OutboxStatus.PENDING } shouldBe true
    }
    
    "Edge Case: 빈 idempotencyKey → 검증 실패" {
        try {
            OutboxEntry(
                id = UUID.randomUUID(),
                idempotencyKey = "",  // 빈 문자열
                aggregateType = AggregateType.RAW_DATA,
                aggregateId = "t:e",
                eventType = "Test",
                payload = "{}",
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
            )
            throw AssertionError("Should have thrown")
        } catch (e: IllegalArgumentException) {
            e.message?.contains("idempotencyKey") shouldBe true
        }
    }
    
    "Edge Case: 대용량 payload도 동일 idempotencyKey 생성" {
        val largePayload = """{"data":"${"x".repeat(100_000)}"}"""
        
        val key1 = OutboxEntry.generateIdempotencyKey("t:e", "Test", largePayload)
        val key2 = OutboxEntry.generateIdempotencyKey("t:e", "Test", largePayload)
        
        key1 shouldBe key2
        key1.length shouldBe 37  // "idem_" + 32 hex chars
    }
    
    "Concurrent insertAll: 동시 삽입 시 하나만 성공" {
        val entries1 = listOf(
            OutboxEntry.create(AggregateType.RAW_DATA, "t:concurrent", "Test", """{"batch":1}"""),
        )
        val entries2 = listOf(
            OutboxEntry.create(AggregateType.RAW_DATA, "t:concurrent", "Test", """{"batch":1}"""),
        )
        
        // 동일 idempotencyKey를 가진 두 배치
        entries1[0].idempotencyKey shouldBe entries2[0].idempotencyKey
        
        // 동시 삽입 시도
        coroutineScope {
            val results = listOf(
                async { repo.insertAll(entries1) },
                async { repo.insertAll(entries2) },
            ).awaitAll()

            // 하나만 성공
            val successes = results.count { result ->
                result is OutboxRepositoryPort.Result.Ok<*>
            }
            val failures = results.count { result ->
                result is OutboxRepositoryPort.Result.Err
            }

            successes shouldBe 1
            failures shouldBe 1
        }
        
        // 결과: 정확히 1개만 저장됨
        repo.size() shouldBe 1
    }
})
