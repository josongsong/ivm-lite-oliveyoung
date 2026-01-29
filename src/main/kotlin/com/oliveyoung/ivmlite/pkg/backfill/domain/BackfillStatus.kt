package com.oliveyoung.ivmlite.pkg.backfill.domain

/**
 * Backfill Job 상태
 */
enum class BackfillStatus {
    /** 생성됨, 실행 대기 중 */
    PENDING,
    
    /** Dry Run 중 (영향도 분석) */
    DRY_RUN,
    
    /** 실행 중 */
    RUNNING,
    
    /** 일시 정지 */
    PAUSED,
    
    /** 성공적으로 완료 */
    COMPLETED,
    
    /** 실패 (재시도 가능) */
    FAILED,
    
    /** 사용자에 의해 취소됨 */
    CANCELLED;
    
    fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, CANCELLED)
    fun isActive(): Boolean = this in setOf(RUNNING, PAUSED, DRY_RUN)
}
