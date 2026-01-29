package com.oliveyoung.ivmlite.pkg.backfill.ports

import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillJob
import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillStatus
import com.oliveyoung.ivmlite.pkg.backfill.domain.BackfillType
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import java.time.Instant
import java.util.UUID

/**
 * Backfill Job Repository Port
 * 
 * Backfill Job의 영속성을 담당한다.
 */
interface BackfillJobRepositoryPort {
    
    /**
     * Job 저장 (insert or update)
     */
    suspend fun save(job: BackfillJob): Result<BackfillJob>
    
    /**
     * ID로 조회
     */
    suspend fun findById(id: UUID): Result<BackfillJob?>
    
    /**
     * 상태별 조회
     */
    suspend fun findByStatus(status: BackfillStatus, limit: Int = 50): Result<List<BackfillJob>>
    
    /**
     * 활성 Job 조회 (RUNNING, PAUSED, DRY_RUN)
     */
    suspend fun findActive(): Result<List<BackfillJob>>
    
    /**
     * 대기 중인 Job 조회 (우선순위 순)
     */
    suspend fun findPending(limit: Int = 10): Result<List<BackfillJob>>
    
    /**
     * 스케줄된 Job 조회 (실행 시각이 도래한)
     */
    suspend fun findScheduledBefore(time: Instant): Result<List<BackfillJob>>
    
    /**
     * 최근 Job 조회
     */
    suspend fun findRecent(limit: Int = 20): Result<List<BackfillJob>>
    
    /**
     * 타입별 조회
     */
    suspend fun findByType(type: BackfillType, limit: Int = 50): Result<List<BackfillJob>>
    
    /**
     * 통계 조회
     */
    suspend fun getStats(): Result<BackfillStats>
    
    /**
     * 오래된 완료 Job 삭제
     */
    suspend fun deleteCompletedBefore(before: Instant): Result<Int>
    
    // ==================== Result Type ====================
    
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}

/**
 * Backfill 통계
 */
data class BackfillStats(
    val totalJobs: Int,
    val activeJobs: Int,
    val pendingJobs: Int,
    val completedToday: Int,
    val failedToday: Int,
    val byType: Map<BackfillType, Int>,
    val byStatus: Map<BackfillStatus, Int>
)
