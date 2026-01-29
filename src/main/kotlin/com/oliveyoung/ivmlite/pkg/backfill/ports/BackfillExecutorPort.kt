package com.oliveyoung.ivmlite.pkg.backfill.ports

import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * Backfill Executor Port
 * 
 * 실제 재처리 로직을 실행하는 책임.
 * BackfillType별로 다른 구현체가 필요할 수 있다.
 */
interface BackfillExecutorPort {
    
    /**
     * 지원하는 Backfill 타입
     */
    val supportedTypes: Set<BackfillType>
    
    /**
     * Dry Run 실행 (영향도 분석)
     * 
     * @param scope 재처리 범위
     * @return 예상 영향도
     */
    suspend fun dryRun(scope: BackfillScope): Result<DryRunResult>
    
    /**
     * 범위에 해당하는 엔티티 키 목록 조회
     * 
     * @param scope 재처리 범위
     * @return 엔티티 키 Iterator (메모리 효율성)
     */
    suspend fun resolveScope(scope: BackfillScope): Result<ScopeResolution>
    
    /**
     * 단일 엔티티 재처리
     * 
     * @param entityKey 엔티티 키
     * @param type 재처리 타입
     * @param config 설정
     * @return 처리 결과
     */
    suspend fun processEntity(
        entityKey: String,
        type: BackfillType,
        config: BackfillConfig
    ): Result<EntityProcessResult>
    
    /**
     * 배치 재처리
     * 
     * @param entityKeys 엔티티 키 목록
     * @param type 재처리 타입
     * @param config 설정
     * @return 배치 처리 결과
     */
    suspend fun processBatch(
        entityKeys: List<String>,
        type: BackfillType,
        config: BackfillConfig
    ): Result<BatchProcessResult>
    
    // ==================== Result Types ====================
    
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}

/**
 * Scope 해석 결과
 */
data class ScopeResolution(
    /** 전체 엔티티 수 */
    val totalCount: Long,
    
    /** 엔티티 키 시퀀스 (Lazy evaluation) */
    val entityKeys: Sequence<String>,
    
    /** 타입별 카운트 (선택적) */
    val countByType: Map<String, Long> = emptyMap()
)

/**
 * 단일 엔티티 처리 결과
 */
data class EntityProcessResult(
    val entityKey: String,
    val success: Boolean,
    val message: String? = null,
    val slicesCreated: Int = 0,
    val outboxEntriesCreated: Int = 0,
    val durationMs: Long = 0
)

/**
 * 배치 처리 결과
 */
data class BatchProcessResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val results: List<EntityProcessResult>,
    val durationMs: Long
) {
    val successRate: Double
        get() = if (total > 0) succeeded.toDouble() / total else 0.0
}
