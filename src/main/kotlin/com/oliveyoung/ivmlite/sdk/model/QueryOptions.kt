package com.oliveyoung.ivmlite.sdk.model

import java.time.Duration

/**
 * 읽기 일관성 레벨
 * 
 * DB 중립적 추상화:
 * - DynamoDB: ConsistentRead 옵션 매핑
 * - PostgreSQL: 트랜잭션 격리 수준 힌트
 * - Redis: 읽기 우선순위
 */
enum class ReadConsistency {
    /**
     * 최종 일관성 (Eventual Consistency)
     * - 가장 빠름, 약간의 지연 허용
     * - DynamoDB: ConsistentRead = false
     * - PostgreSQL: READ COMMITTED
     */
    Eventual,

    /**
     * 강한 일관성 (Strong Consistency)
     * - 최신 데이터 보장
     * - DynamoDB: ConsistentRead = true
     * - PostgreSQL: REPEATABLE READ
     */
    Strong,

    /**
     * 세션 일관성 (Session Consistency)
     * - 같은 세션 내 일관성 보장
     * - Write-after-read 보장
     */
    Session,

    /**
     * Bounded Staleness
     * - 지정된 시간 내 일관성 보장
     * - CosmosDB 등에서 사용
     */
    BoundedStaleness
}

/**
 * Query 옵션
 */
data class QueryOptions(
    /** 읽기 일관성 */
    val consistency: ReadConsistency = ReadConsistency.Eventual,
    
    /** 쿼리 타임아웃 */
    val timeout: Duration = Duration.ofSeconds(30),
    
    /** 캐시 사용 여부 */
    val cacheEnabled: Boolean = true,
    
    /** 캐시 TTL */
    val cacheTtl: Duration = Duration.ofMinutes(5),
    
    /** 프로젝션 (조회할 Slice 목록, 비어있으면 전체) */
    val projections: List<String> = emptyList(),
    
    /** 응답에 메타데이터 포함 */
    val includeMetadata: Boolean = false,
    
    /** 실패 시 재시도 */
    val retryOnFailure: Boolean = true,
    
    /** 최대 재시도 횟수 */
    val maxRetries: Int = 3
) {
    companion object {
        /** 기본 옵션 */
        val DEFAULT = QueryOptions()
        
        /** 강한 일관성 (쓰기 직후 읽기) */
        val STRONG = QueryOptions(consistency = ReadConsistency.Strong)
        
        /** 캐시 비활성화 */
        val NO_CACHE = QueryOptions(cacheEnabled = false)
        
        /** 빠른 조회 (캐시 우선, 재시도 없음) */
        val FAST = QueryOptions(
            consistency = ReadConsistency.Eventual,
            cacheEnabled = true,
            retryOnFailure = false,
            timeout = Duration.ofSeconds(5)
        )
    }
}
