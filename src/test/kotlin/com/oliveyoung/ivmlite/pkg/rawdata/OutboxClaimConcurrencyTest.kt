package com.oliveyoung.ivmlite.pkg.rawdata
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.OutboxStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Outbox Claim 동시성 테스트
 * 
 * 여러 worker가 동시에 claim할 때:
 * 1. 중복 claim이 발생하지 않음
 * 2. 모든 엔트리가 정확히 한 번만 처리됨
 * 3. ACID 보장
 */
class OutboxClaimConcurrencyTest : StringSpec({

    "여러 worker가 동시에 claim해도 중복 없음" {
        val repo = InMemoryOutboxRepository()
        
        // 100개의 PENDING 엔트리 생성
        val entries = (1..100).map { createEntry() }
        repo.insertAll(entries)
        
        val claimedIds = ConcurrentHashMap.newKeySet<UUID>()
        val duplicateCount = AtomicInteger(0)
        
        // 10개 worker가 동시에 claim 시도
        val workers = (1..10).map { workerId ->
            async(Dispatchers.Default) {
                repeat(20) { // 각 worker가 20번씩 claim 시도
                    val result = repo.claimOne(type = null, workerId = "worker-$workerId")
                    if (result is Result.Ok) {
                        val entry = result.value ?: return@repeat
                        val wasNew = claimedIds.add(entry.id)
                        if (!wasNew) {
                            duplicateCount.incrementAndGet()
                        }
                    }
                    delay(1) // 약간의 지연
                }
            }
        }
        
        workers.awaitAll()
        
        // 검증: 중복 없음
        duplicateCount.get() shouldBe 0
        
        // 모든 엔트리가 정확히 한 번 claim됨
        claimedIds.size shouldBe 100
    }

    "대량 batch claim 시 중복 없음" {
        val repo = InMemoryOutboxRepository()
        
        // 1000개의 PENDING 엔트리 생성
        val entries = (1..1000).map { createEntry() }
        repo.insertAll(entries)
        
        val allClaimed = ConcurrentHashMap.newKeySet<UUID>()
        
        // 20개 worker가 동시에 batch claim (각각 100개씩)
        val workers = (1..20).map { workerId ->
            async(Dispatchers.Default) {
                val claimed = mutableListOf<UUID>()
                repeat(10) { // 10번 × 100개 = 최대 1000개 시도
                    val result = repo.claim(limit = 100, type = null, workerId = "worker-$workerId")
                    if (result is Result.Ok) {
                        claimed.addAll(result.value.map { it.id })
                    }
                }
                claimed
            }
        }
        
        val results = workers.awaitAll()
        results.flatten().forEach { allClaimed.add(it) }
        
        // 총 1000개 모두 claim됨 (중복 없이)
        allClaimed.size shouldBe 1000
        
        // PENDING 남은 것 없음
        val remaining = repo.findPending(limit = 10)
        remaining shouldBe Result.Ok(emptyList())
    }

    "claim 후 markProcessed가 원자적으로 동작" {
        val repo = InMemoryOutboxRepository()
        
        val entries = (1..50).map { createEntry() }
        repo.insertAll(entries)
        
        val processedIds = ConcurrentHashMap.newKeySet<UUID>()
        
        // 5개 worker가 claim → process → markProcessed 전체 플로우 실행
        val workers = (1..5).map { workerId ->
            async(Dispatchers.Default) {
                repeat(20) {
                    val claimed = repo.claimOne(type = null, workerId = "worker-$workerId")
                    if (claimed is Result.Ok) {
                        val entry = claimed.value ?: return@repeat
                        
                        // 가상 처리 시간
                        delay((1..5).random().toLong())
                        
                        // markProcessed
                        repo.markProcessed(listOf(entry.id))
                        processedIds.add(entry.id)
                    }
                }
            }
        }
        
        workers.awaitAll()
        
        // 모든 엔트리가 처리됨
        processedIds.size shouldBe 50
        
        // 모든 엔트리가 PROCESSED 상태
        entries.forEach { original ->
            val current = repo.findById(original.id)
            if (current is Result.Ok) {
                current.value.status shouldBe OutboxStatus.PROCESSED
            }
        }
    }

    "stale recovery와 claim이 동시에 실행되어도 안전" {
        val repo = InMemoryOutboxRepository()
        
        val now = Instant.now()
        
        // 일부는 stale PROCESSING, 일부는 PENDING
        val staleEntries = (1..20).map { 
            createEntry(
                status = OutboxStatus.PROCESSING,
                claimedAt = now.minusSeconds(600), // 10분 전 claim
                claimedBy = "dead-worker",
            )
        }
        val pendingEntries = (1..30).map { createEntry() }
        
        repo.insertAll(staleEntries + pendingEntries)
        
        val allProcessed = ConcurrentHashMap.newKeySet<UUID>()
        
        // Recovery worker와 일반 worker가 동시에 실행
        val recoveryWorker = async(Dispatchers.Default) {
            repeat(10) {
                repo.recoverStaleProcessing(olderThanSeconds = 300)
                delay(5)
            }
        }
        
        val claimWorkers = (1..5).map { workerId ->
            async(Dispatchers.Default) {
                repeat(20) {
                    val claimed = repo.claimOne(type = null, workerId = "worker-$workerId")
                    if (claimed is Result.Ok) {
                        val entry = claimed.value ?: return@repeat
                        repo.markProcessed(listOf(entry.id))
                        allProcessed.add(entry.id)
                    }
                    delay(2)
                }
            }
        }
        
        (listOf(recoveryWorker) + claimWorkers).awaitAll()
        
        // 모든 엔트리가 처리됨 (stale + pending)
        allProcessed.size shouldBe 50
    }

    "type 필터와 동시성 조합" {
        val repo = InMemoryOutboxRepository()
        
        // RAW_DATA 50개, SLICE 50개
        val rawDataEntries = (1..50).map { createEntry(aggregateType = AggregateType.RAW_DATA) }
        val sliceEntries = (1..50).map { createEntry(aggregateType = AggregateType.SLICE) }
        
        repo.insertAll(rawDataEntries + sliceEntries)
        
        val rawDataClaimed = ConcurrentHashMap.newKeySet<UUID>()
        val sliceClaimed = ConcurrentHashMap.newKeySet<UUID>()
        
        // RAW_DATA 전용 worker
        val rawDataWorkers = (1..3).map { workerId ->
            async(Dispatchers.Default) {
                repeat(30) {
                    val claimed = repo.claimOne(type = AggregateType.RAW_DATA, workerId = "raw-worker-$workerId")
                    if (claimed is Result.Ok) {
                        val entry = claimed.value ?: return@repeat
                        rawDataClaimed.add(entry.id)
                    }
                    delay(1)
                }
            }
        }
        
        // SLICE 전용 worker
        val sliceWorkers = (1..3).map { workerId ->
            async(Dispatchers.Default) {
                repeat(30) {
                    val claimed = repo.claimOne(type = AggregateType.SLICE, workerId = "slice-worker-$workerId")
                    if (claimed is Result.Ok) {
                        val entry = claimed.value ?: return@repeat
                        sliceClaimed.add(entry.id)
                    }
                    delay(1)
                }
            }
        }
        
        (rawDataWorkers + sliceWorkers).awaitAll()
        
        // 각각 50개씩 처리
        rawDataClaimed.size shouldBe 50
        sliceClaimed.size shouldBe 50
        
        // 교차 없음
        rawDataClaimed.intersect(sliceClaimed) shouldHaveSize 0
    }

    "빈 outbox에서 다수 worker claim 시도 - 예외 없음" {
        val repo = InMemoryOutboxRepository()
        // 비어있는 상태에서 시작
        
        val workers = (1..20).map { workerId ->
            async(Dispatchers.Default) {
                repeat(50) {
                    val result = repo.claimOne(type = null, workerId = "worker-$workerId")
                    result shouldBe Result.Ok(null)
                }
            }
        }
        
        workers.awaitAll()
        // 예외 없이 완료
    }

    "markFailed 동시 호출 시 retryCount 정확히 증가" {
        val repo = InMemoryOutboxRepository()
        
        val entry = createEntry()
        repo.insert(entry)
        
        // 5개 worker가 동시에 같은 entry에 markFailed 시도
        // (실제로는 claim된 entry에만 호출하지만, 동시성 테스트용)
        val workers = (1..5).map {
            async(Dispatchers.Default) {
                repo.markFailed(entry.id, "Error $it")
            }
        }
        
        workers.awaitAll()
        
        // retryCount가 5번 증가했는지 확인
        val result = repo.findById(entry.id)
        if (result is Result.Ok) {
            result.value.retryCount shouldBe 5
        }
    }
})

private fun createEntry(
    status: OutboxStatus = OutboxStatus.PENDING,
    aggregateType: AggregateType = AggregateType.RAW_DATA,
    claimedAt: Instant? = null,
    claimedBy: String? = null,
): OutboxEntry {
    val aggregateId = "tenant-1:entity-${UUID.randomUUID()}"
    val eventType = "TestEvent"
    val payload = """{"random": "${UUID.randomUUID()}"}"""
    return OutboxEntry(
        id = UUID.randomUUID(),
        idempotencyKey = OutboxEntry.generateIdempotencyKey(aggregateId, eventType, payload),
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        status = status,
        createdAt = Instant.now(),
        claimedAt = claimedAt,
        claimedBy = claimedBy,
    )
}
