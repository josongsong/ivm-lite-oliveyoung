package com.oliveyoung.ivmlite.pkg.backfill.domain

import java.time.Instant
import java.util.UUID

/**
 * Backfill Job 엔티티
 * 
 * 재처리 작업의 전체 라이프사이클을 관리한다.
 *
 * @property id 고유 식별자
 * @property name 작업 이름 (사람이 읽을 수 있는)
 * @property description 작업 설명
 * @property type 재처리 타입
 * @property scope 재처리 범위
 * @property status 현재 상태
 * @property priority 우선순위 (낮을수록 높음)
 * @property config 실행 설정
 * @property progress 진행 상황
 * @property createdBy 생성자
 * @property createdAt 생성 시각
 * @property startedAt 시작 시각
 * @property completedAt 완료 시각
 * @property failureReason 실패 사유
 * @property dryRunResult Dry Run 결과 (예상 영향도)
 */
data class BackfillJob(
    val id: UUID,
    val name: String,
    val description: String = "",
    val type: BackfillType,
    val scope: BackfillScope,
    val status: BackfillStatus,
    val priority: Int = 5,
    val config: BackfillConfig,
    val progress: BackfillProgress,
    val createdBy: String,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val failureReason: String? = null,
    val dryRunResult: DryRunResult? = null
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(priority in 1..10) { "priority must be between 1 and 10" }
    }
    
    companion object {
        /**
         * 새 Backfill Job 생성
         */
        fun create(
            name: String,
            type: BackfillType,
            scope: BackfillScope,
            createdBy: String,
            config: BackfillConfig = BackfillConfig(),
            description: String = "",
            priority: Int = 5
        ): BackfillJob = BackfillJob(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            type = type,
            scope = scope,
            status = BackfillStatus.PENDING,
            priority = priority,
            config = config,
            progress = BackfillProgress.empty(),
            createdBy = createdBy,
            createdAt = Instant.now()
        )
    }
    
    /**
     * Dry Run 시작
     */
    fun startDryRun(): BackfillJob {
        require(status == BackfillStatus.PENDING) { "Can only start dry run from PENDING status" }
        return copy(status = BackfillStatus.DRY_RUN)
    }
    
    /**
     * Dry Run 완료
     */
    fun completeDryRun(result: DryRunResult): BackfillJob {
        require(status == BackfillStatus.DRY_RUN) { "Can only complete dry run from DRY_RUN status" }
        return copy(
            status = BackfillStatus.PENDING,
            dryRunResult = result,
            progress = BackfillProgress.initialized(result.estimatedCount)
        )
    }
    
    /**
     * 실행 시작
     */
    fun start(totalCount: Long): BackfillJob {
        require(status == BackfillStatus.PENDING) { "Can only start from PENDING status" }
        val now = Instant.now()
        return copy(
            status = BackfillStatus.RUNNING,
            startedAt = now,
            progress = BackfillProgress.initialized(totalCount, now)
        )
    }
    
    /**
     * 일시 정지
     */
    fun pause(): BackfillJob {
        require(status == BackfillStatus.RUNNING) { "Can only pause RUNNING job" }
        return copy(status = BackfillStatus.PAUSED)
    }
    
    /**
     * 재개
     */
    fun resume(): BackfillJob {
        require(status == BackfillStatus.PAUSED) { "Can only resume PAUSED job" }
        return copy(status = BackfillStatus.RUNNING)
    }
    
    /**
     * 성공 완료
     */
    fun complete(): BackfillJob {
        require(status == BackfillStatus.RUNNING) { "Can only complete RUNNING job" }
        return copy(
            status = BackfillStatus.COMPLETED,
            completedAt = Instant.now()
        )
    }
    
    /**
     * 실패
     */
    fun fail(reason: String): BackfillJob {
        require(status in setOf(BackfillStatus.RUNNING, BackfillStatus.DRY_RUN)) {
            "Can only fail RUNNING or DRY_RUN job"
        }
        return copy(
            status = BackfillStatus.FAILED,
            completedAt = Instant.now(),
            failureReason = reason
        )
    }
    
    /**
     * 취소
     */
    fun cancel(): BackfillJob {
        require(!status.isTerminal()) { "Cannot cancel terminal job" }
        return copy(
            status = BackfillStatus.CANCELLED,
            completedAt = Instant.now()
        )
    }
    
    /**
     * 진행 상황 업데이트
     */
    fun updateProgress(newProgress: BackfillProgress): BackfillJob {
        return copy(progress = newProgress)
    }
    
    /**
     * 재시도 (실패한 작업)
     */
    fun retry(): BackfillJob {
        require(status == BackfillStatus.FAILED) { "Can only retry FAILED job" }
        return copy(
            status = BackfillStatus.PENDING,
            failureReason = null,
            completedAt = null
        )
    }
    
    /**
     * 작업 요약 문자열
     */
    fun toSummary(): String = buildString {
        append("[$status] $name")
        append(" (${type.name})")
        if (status == BackfillStatus.RUNNING) {
            append(" - ${progress.toSummary()}")
        }
    }
}

/**
 * Backfill 실행 설정
 */
data class BackfillConfig(
    /** 배치 크기 */
    val batchSize: Int = 100,
    
    /** 동시 처리 수 */
    val concurrency: Int = 4,
    
    /** 에러 발생 시 계속 진행 여부 */
    val continueOnError: Boolean = true,
    
    /** 최대 재시도 횟수 */
    val maxRetries: Int = 3,
    
    /** 재시도 간격 (ms) */
    val retryDelayMs: Long = 1000,
    
    /** 배치 간 딜레이 (throttling, ms) */
    val batchDelayMs: Long = 0,
    
    /** 드라이런 모드 (실제 처리하지 않음) */
    val dryRun: Boolean = false,
    
    /** 스케줄된 실행 시각 (null이면 즉시) */
    val scheduledAt: Instant? = null
)

/**
 * Dry Run 결과
 */
data class DryRunResult(
    /** 예상 처리 건수 */
    val estimatedCount: Long,
    
    /** 영향받는 엔티티 타입별 카운트 */
    val countByType: Map<String, Long>,
    
    /** 예상 소요 시간 */
    val estimatedDuration: java.time.Duration?,
    
    /** 샘플 엔티티 키 (미리보기용) */
    val sampleEntities: List<String>,
    
    /** 경고 메시지 */
    val warnings: List<String> = emptyList()
)
