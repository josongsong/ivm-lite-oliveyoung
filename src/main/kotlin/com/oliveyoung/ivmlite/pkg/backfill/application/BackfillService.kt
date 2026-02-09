package com.oliveyoung.ivmlite.pkg.backfill.application

import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import com.oliveyoung.ivmlite.pkg.backfill.ports.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Backfill Service
 * 
 * Backfill 작업의 생성, 실행, 모니터링을 담당하는 Application Service.
 *
 * Features:
 * - Dry Run 지원
 * - 일시정지/재개
 * - 진행 상황 실시간 추적
 * - 동시 실행 제한
 */
class BackfillService(
    private val jobRepository: BackfillJobRepositoryPort,
    private val executor: BackfillExecutorPort,
    private val config: BackfillServiceConfig = BackfillServiceConfig()
) {
    private val logger = LoggerFactory.getLogger(BackfillService::class.java)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<UUID, Job>()
    private val progressListeners = mutableListOf<ProgressListener>()
    
    /**
     * 진행 상황 리스너
     */
    interface ProgressListener {
        suspend fun onProgress(jobId: UUID, progress: BackfillProgress)
        suspend fun onStatusChange(jobId: UUID, status: BackfillStatus)
    }
    
    /**
     * 리스너 등록
     */
    fun addProgressListener(listener: ProgressListener) {
        progressListeners.add(listener)
    }
    
    // ==================== Job 생성 ====================
    
    /**
     * Backfill Job 생성
     */
    suspend fun createJob(request: CreateBackfillRequest): Result<BackfillJob> {
        // 동시 실행 제한 확인
        val activeCount = runningJobs.size
        if (activeCount >= config.maxConcurrentJobs) {
            return Result.Err(DomainError.ValidationError(
                field = "maxConcurrentJobs",
                msg = "Maximum concurrent jobs limit reached: $activeCount/${config.maxConcurrentJobs}"
            ))
        }
        
        // Job 생성
        val job = BackfillJob.create(
            name = request.name,
            type = request.type,
            scope = request.scope,
            createdBy = request.createdBy,
            config = request.config,
            description = request.description,
            priority = request.priority
        )
        
        // 저장
        return when (val r = jobRepository.save(job)) {
            is Result.Ok -> {
                logger.info("Created backfill job: {} [{}]", job.name, job.id)
                Result.Ok(r.value)
            }
            is Result.Err -> {
                Result.Err(r.error)
            }
        }
    }
    
    // ==================== Dry Run ====================
    
    /**
     * Dry Run 실행
     */
    suspend fun dryRun(jobId: UUID): Result<DryRunResult> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status != BackfillStatus.PENDING) {
            return Result.Err(DomainError.ValidationError("status", "Can only dry run PENDING jobs"))
        }
        
        // 상태 변경
        val dryRunJob = job.startDryRun()
        jobRepository.save(dryRunJob)
        notifyStatusChange(jobId, BackfillStatus.DRY_RUN)
        
        // Dry Run 실행
        return when (val result = executor.dryRun(job.scope)) {
            is Result.Ok -> {
                val completedJob = dryRunJob.completeDryRun(result.value)
                jobRepository.save(completedJob)
                notifyStatusChange(jobId, BackfillStatus.PENDING)
                Result.Ok(result.value)
            }
            is Result.Err -> {
                val failedJob = dryRunJob.fail("Dry run failed: ${result.error}")
                jobRepository.save(failedJob)
                notifyStatusChange(jobId, BackfillStatus.FAILED)
                Result.Err(result.error)
            }
        }
    }
    
    // ==================== Job 실행 ====================
    
    /**
     * Job 시작
     */
    suspend fun startJob(jobId: UUID): Result<BackfillJob> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status != BackfillStatus.PENDING) {
            return Result.Err(DomainError.ValidationError("status", "Can only start PENDING jobs"))
        }
        
        // Scope 해석
        val resolution = when (val r = executor.resolveScope(job.scope)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }
        
        // 시작
        val startedJob = job.start(resolution.totalCount)
        jobRepository.save(startedJob)
        notifyStatusChange(jobId, BackfillStatus.RUNNING)
        
        // 비동기 실행
        val coroutineJob = scope.launch {
            executeJob(startedJob, resolution)
        }
        runningJobs[jobId] = coroutineJob
        
        logger.info("Started backfill job: {} [{}] - {} entities", job.name, jobId, resolution.totalCount)
        return Result.Ok(startedJob)
    }
    
    /**
     * Job 일시정지
     */
    suspend fun pauseJob(jobId: UUID): Result<BackfillJob> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status != BackfillStatus.RUNNING) {
            return Result.Err(DomainError.ValidationError("status", "Can only pause RUNNING jobs"))
        }
        
        val pausedJob = job.pause()
        jobRepository.save(pausedJob)
        notifyStatusChange(jobId, BackfillStatus.PAUSED)
        
        logger.info("Paused backfill job: {} [{}]", job.name, jobId)
        return Result.Ok(pausedJob)
    }
    
    /**
     * Job 재개
     */
    suspend fun resumeJob(jobId: UUID): Result<BackfillJob> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status != BackfillStatus.PAUSED) {
            return Result.Err(DomainError.ValidationError("status", "Can only resume PAUSED jobs"))
        }
        
        val resumedJob = job.resume()
        jobRepository.save(resumedJob)
        notifyStatusChange(jobId, BackfillStatus.RUNNING)
        
        logger.info("Resumed backfill job: {} [{}]", job.name, jobId)
        return Result.Ok(resumedJob)
    }
    
    /**
     * Job 취소
     */
    suspend fun cancelJob(jobId: UUID): Result<BackfillJob> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status.isTerminal()) {
            return Result.Err(DomainError.ValidationError("status", "Cannot cancel terminal jobs"))
        }
        
        // 실행 중인 코루틴 취소
        runningJobs[jobId]?.cancel()
        runningJobs.remove(jobId)
        
        val cancelledJob = job.cancel()
        jobRepository.save(cancelledJob)
        notifyStatusChange(jobId, BackfillStatus.CANCELLED)
        
        logger.info("Cancelled backfill job: {} [{}]", job.name, jobId)
        return Result.Ok(cancelledJob)
    }
    
    /**
     * 실패한 Job 재시도
     */
    suspend fun retryJob(jobId: UUID): Result<BackfillJob> {
        val job = findJob(jobId) ?: return Result.Err(DomainError.NotFoundError("BackfillJob", jobId.toString()))
        
        if (job.status != BackfillStatus.FAILED) {
            return Result.Err(DomainError.ValidationError("status", "Can only retry FAILED jobs"))
        }
        
        val retriedJob = job.retry()
        jobRepository.save(retriedJob)
        notifyStatusChange(jobId, BackfillStatus.PENDING)
        
        logger.info("Retrying backfill job: {} [{}]", job.name, jobId)
        return Result.Ok(retriedJob)
    }
    
    // ==================== 조회 ====================
    
    /**
     * Job 조회
     */
    suspend fun getJob(jobId: UUID): BackfillJob? = findJob(jobId)
    
    /**
     * 활성 Job 목록
     */
    suspend fun getActiveJobs(): List<BackfillJob> {
        return when (val r = jobRepository.findActive()) {
            is Result.Ok -> r.value
            is Result.Err -> emptyList()
        }
    }
    
    /**
     * 최근 Job 목록
     */
    suspend fun getRecentJobs(limit: Int = 20): List<BackfillJob> {
        return when (val r = jobRepository.findRecent(limit)) {
            is Result.Ok -> r.value
            is Result.Err -> emptyList()
        }
    }
    
    /**
     * 통계
     */
    suspend fun getStats(): BackfillStats? {
        return when (val r = jobRepository.getStats()) {
            is Result.Ok -> r.value
            is Result.Err -> null
        }
    }
    
    // ==================== Internal ====================
    
    private suspend fun findJob(id: UUID): BackfillJob? {
        return when (val r = jobRepository.findById(id)) {
            is Result.Ok -> r.value
            is Result.Err -> null
        }
    }
    
    private suspend fun executeJob(job: BackfillJob, resolution: ScopeResolution) {
        var currentJob = job
        var processed = 0L
        
        try {
            val entityKeys = resolution.entityKeys.chunked(job.config.batchSize)
            
            for (batch in entityKeys) {
                // 일시정지 확인
                while (true) {
                    val latestJob = findJob(job.id) ?: break
                    currentJob = latestJob
                    
                    when (latestJob.status) {
                        BackfillStatus.PAUSED -> {
                            delay(1000) // 대기
                            continue
                        }
                        BackfillStatus.CANCELLED -> {
                            return // 종료
                        }
                        BackfillStatus.RUNNING -> break
                        else -> return
                    }
                }
                
                // 배치 처리
                val result = executor.processBatch(batch, job.type, job.config)
                
                when (result) {
                    is Result.Ok -> {
                        val batchResult = result.value
                        processed += batchResult.total
                        
                        // 진행 상황 업데이트
                        var newProgress = currentJob.progress
                        for (entityResult in batchResult.results) {
                            newProgress = if (entityResult.success) {
                                newProgress.recordSuccess(entityResult.entityKey)
                            } else {
                                newProgress.recordFailure(entityResult.entityKey, entityResult.message)
                            }
                        }
                        
                        currentJob = currentJob.updateProgress(newProgress)
                        jobRepository.save(currentJob)
                        notifyProgress(job.id, newProgress)
                    }
                    is Result.Err -> {
                        if (!job.config.continueOnError) {
                            throw RuntimeException("Batch failed: ${result.error}")
                        }
                    }
                }
                
                // Throttling
                if (job.config.batchDelayMs > 0) {
                    delay(job.config.batchDelayMs)
                }
            }
            
            // 완료
            val completedJob = currentJob.complete()
            jobRepository.save(completedJob)
            notifyStatusChange(job.id, BackfillStatus.COMPLETED)
            
            logger.info(
                "Completed backfill job: {} [{}] - {}/{} succeeded",
                job.name, job.id, currentJob.progress.succeeded, currentJob.progress.total
            )
            
        } catch (e: CancellationException) {
            logger.info("Backfill job cancelled: {} [{}]", job.name, job.id)
            throw e
        } catch (e: Exception) {
            logger.error("Backfill job failed: {} [{}]", job.name, job.id, e)
            val failedJob = currentJob.fail(e.message ?: "Unknown error")
            jobRepository.save(failedJob)
            notifyStatusChange(job.id, BackfillStatus.FAILED)
        } finally {
            runningJobs.remove(job.id)
        }
    }
    
    private suspend fun notifyProgress(jobId: UUID, progress: BackfillProgress) {
        progressListeners.forEach {
            try { it.onProgress(jobId, progress) } catch (e: Exception) { /* ignore */ }
        }
    }
    
    private suspend fun notifyStatusChange(jobId: UUID, status: BackfillStatus) {
        progressListeners.forEach {
            try { it.onStatusChange(jobId, status) } catch (e: Exception) { /* ignore */ }
        }
    }
}

/**
 * Backfill Job 생성 요청
 */
data class CreateBackfillRequest(
    val name: String,
    val type: BackfillType,
    val scope: BackfillScope,
    val createdBy: String,
    val config: BackfillConfig = BackfillConfig(),
    val description: String = "",
    val priority: Int = 5
)

/**
 * BackfillService 설정
 */
data class BackfillServiceConfig(
    /** 최대 동시 실행 Job 수 */
    val maxConcurrentJobs: Int = 3,
    
    /** 완료된 Job 보관 기간 (일) */
    val retentionDays: Int = 30
)
