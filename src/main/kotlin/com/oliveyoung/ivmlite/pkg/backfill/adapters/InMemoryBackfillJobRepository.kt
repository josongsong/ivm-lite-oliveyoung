package com.oliveyoung.ivmlite.pkg.backfill.adapters

import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillJob
import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillStatus
import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillType
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillJobRepositoryPort
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillStats
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory Backfill Job Repository
 * 
 * 개발/테스트용 메모리 기반 구현.
 */
class InMemoryBackfillJobRepository : BackfillJobRepositoryPort {
    
    private val jobs = ConcurrentHashMap<UUID, BackfillJob>()
    
    override suspend fun save(job: BackfillJob): BackfillJobRepositoryPort.Result<BackfillJob> {
        jobs[job.id] = job
        return BackfillJobRepositoryPort.Result.Ok(job)
    }
    
    override suspend fun findById(id: UUID): BackfillJobRepositoryPort.Result<BackfillJob?> {
        return BackfillJobRepositoryPort.Result.Ok(jobs[id])
    }
    
    override suspend fun findByStatus(
        status: BackfillStatus,
        limit: Int
    ): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val result = jobs.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return BackfillJobRepositoryPort.Result.Ok(result)
    }
    
    override suspend fun findActive(): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val active = jobs.values
            .filter { it.status.isActive() }
            .sortedBy { it.priority }
        return BackfillJobRepositoryPort.Result.Ok(active)
    }
    
    override suspend fun findPending(limit: Int): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val pending = jobs.values
            .filter { it.status == BackfillStatus.PENDING }
            .filter { it.config.scheduledAt == null || it.config.scheduledAt.isBefore(Instant.now()) }
            .sortedBy { it.priority }
            .take(limit)
        return BackfillJobRepositoryPort.Result.Ok(pending)
    }
    
    override suspend fun findScheduledBefore(time: Instant): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val scheduled = jobs.values
            .filter { it.status == BackfillStatus.PENDING }
            .filter { it.config.scheduledAt != null && it.config.scheduledAt.isBefore(time) }
            .sortedBy { it.config.scheduledAt }
        return BackfillJobRepositoryPort.Result.Ok(scheduled)
    }
    
    override suspend fun findRecent(limit: Int): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val recent = jobs.values
            .sortedByDescending { it.createdAt }
            .take(limit)
        return BackfillJobRepositoryPort.Result.Ok(recent)
    }
    
    override suspend fun findByType(
        type: BackfillType,
        limit: Int
    ): BackfillJobRepositoryPort.Result<List<BackfillJob>> {
        val result = jobs.values
            .filter { it.type == type }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return BackfillJobRepositoryPort.Result.Ok(result)
    }
    
    override suspend fun getStats(): BackfillJobRepositoryPort.Result<BackfillStats> {
        val allJobs = jobs.values.toList()
        val today = Instant.now().truncatedTo(ChronoUnit.DAYS)
        
        val stats = BackfillStats(
            totalJobs = allJobs.size,
            activeJobs = allJobs.count { it.status.isActive() },
            pendingJobs = allJobs.count { it.status == BackfillStatus.PENDING },
            completedToday = allJobs.count { 
                it.status == BackfillStatus.COMPLETED && 
                it.completedAt?.isAfter(today) == true 
            },
            failedToday = allJobs.count { 
                it.status == BackfillStatus.FAILED && 
                it.completedAt?.isAfter(today) == true 
            },
            byType = allJobs.groupBy { it.type }.mapValues { it.value.size },
            byStatus = allJobs.groupBy { it.status }.mapValues { it.value.size }
        )
        return BackfillJobRepositoryPort.Result.Ok(stats)
    }
    
    override suspend fun deleteCompletedBefore(before: Instant): BackfillJobRepositoryPort.Result<Int> {
        val toDelete = jobs.values.filter { 
            it.status.isTerminal() && it.completedAt?.isBefore(before) == true
        }
        toDelete.forEach { jobs.remove(it.id) }
        return BackfillJobRepositoryPort.Result.Ok(toDelete.size)
    }
    
    // 테스트용
    fun clear() = jobs.clear()
    fun size() = jobs.size
}
