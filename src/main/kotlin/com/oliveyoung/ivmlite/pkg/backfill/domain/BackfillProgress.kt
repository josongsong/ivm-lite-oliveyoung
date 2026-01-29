package com.oliveyoung.ivmlite.pkg.backfill.domain

import java.time.Duration
import java.time.Instant

/**
 * Backfill 진행 상황
 */
data class BackfillProgress(
    /** 전체 처리 대상 수 */
    val total: Long,
    
    /** 처리 완료 수 */
    val processed: Long = 0,
    
    /** 성공 수 */
    val succeeded: Long = 0,
    
    /** 실패 수 */
    val failed: Long = 0,
    
    /** 스킵 수 (이미 처리됨 등) */
    val skipped: Long = 0,
    
    /** 현재 처리 중인 엔티티 */
    val currentEntity: String? = null,
    
    /** 시작 시각 */
    val startedAt: Instant? = null,
    
    /** 마지막 업데이트 시각 */
    val lastUpdatedAt: Instant = Instant.now(),
    
    /** 처리 속도 (records/sec) */
    val throughput: Double = 0.0,
    
    /** 예상 남은 시간 */
    val estimatedRemaining: Duration? = null,
    
    /** 최근 에러 메시지들 */
    val recentErrors: List<String> = emptyList()
) {
    init {
        require(total >= 0) { "total must be non-negative" }
        require(processed >= 0) { "processed must be non-negative" }
        require(processed <= total) { "processed cannot exceed total" }
    }
    
    companion object {
        fun empty() = BackfillProgress(total = 0)
        
        fun initialized(total: Long, startedAt: Instant = Instant.now()) = BackfillProgress(
            total = total,
            startedAt = startedAt
        )
    }
    
    /**
     * 진행률 (0.0 ~ 1.0)
     */
    val progressRatio: Double
        get() = if (total > 0) processed.toDouble() / total else 0.0
    
    /**
     * 진행률 퍼센트 (0 ~ 100)
     */
    val progressPercent: Double
        get() = progressRatio * 100
    
    /**
     * 완료 여부
     */
    val isComplete: Boolean
        get() = processed >= total
    
    /**
     * 경과 시간
     */
    fun elapsed(): Duration = startedAt?.let { Duration.between(it, Instant.now()) } ?: Duration.ZERO
    
    /**
     * 성공 처리
     */
    fun recordSuccess(entity: String? = null): BackfillProgress {
        val newProcessed = processed + 1
        val newSucceeded = succeeded + 1
        val newThroughput = calculateThroughput(newProcessed)
        val remaining = estimateRemaining(newProcessed, newThroughput)
        
        return copy(
            processed = newProcessed,
            succeeded = newSucceeded,
            currentEntity = entity,
            throughput = newThroughput,
            estimatedRemaining = remaining,
            lastUpdatedAt = Instant.now()
        )
    }
    
    /**
     * 실패 처리
     */
    fun recordFailure(entity: String? = null, error: String? = null): BackfillProgress {
        val newProcessed = processed + 1
        val newFailed = failed + 1
        val newThroughput = calculateThroughput(newProcessed)
        val remaining = estimateRemaining(newProcessed, newThroughput)
        
        val newErrors = if (error != null) {
            (listOf(error) + recentErrors).take(10)
        } else {
            recentErrors
        }
        
        return copy(
            processed = newProcessed,
            failed = newFailed,
            currentEntity = entity,
            throughput = newThroughput,
            estimatedRemaining = remaining,
            recentErrors = newErrors,
            lastUpdatedAt = Instant.now()
        )
    }
    
    /**
     * 스킵 처리
     */
    fun recordSkip(): BackfillProgress {
        return copy(
            processed = processed + 1,
            skipped = skipped + 1,
            lastUpdatedAt = Instant.now()
        )
    }
    
    private fun calculateThroughput(currentProcessed: Long): Double {
        val elapsed = elapsed().toMillis()
        return if (elapsed > 0) {
            (currentProcessed * 1000.0) / elapsed
        } else {
            0.0
        }
    }
    
    private fun estimateRemaining(currentProcessed: Long, currentThroughput: Double): Duration? {
        if (currentThroughput <= 0) return null
        val remaining = total - currentProcessed
        val seconds = (remaining / currentThroughput).toLong()
        return Duration.ofSeconds(seconds)
    }
    
    /**
     * 진행 상황 문자열
     */
    fun toSummary(): String = buildString {
        append("${progressPercent.toInt()}% ")
        append("($processed/$total)")
        if (failed > 0) append(" [${failed} failed]")
        if (skipped > 0) append(" [${skipped} skipped]")
        estimatedRemaining?.let { append(" ETA: ${formatDuration(it)}") }
    }
    
    private fun formatDuration(d: Duration): String = when {
        d.toMinutes() < 1 -> "${d.seconds}s"
        d.toHours() < 1 -> "${d.toMinutes()}m"
        d.toDays() < 1 -> "${d.toHours()}h ${d.toMinutesPart()}m"
        else -> "${d.toDays()}d ${d.toHoursPart()}h"
    }
}
