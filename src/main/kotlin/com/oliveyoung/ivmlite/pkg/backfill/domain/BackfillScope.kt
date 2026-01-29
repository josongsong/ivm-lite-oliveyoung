package com.oliveyoung.ivmlite.pkg.backfill.domain

import java.time.Instant

/**
 * Backfill 범위 정의
 * 
 * 어떤 데이터를 재처리할지 조건을 정의한다.
 * 모든 조건은 AND로 결합된다.
 */
data class BackfillScope(
    /** 테넌트 ID 필터 (null이면 전체) */
    val tenantIds: Set<String>? = null,
    
    /** 엔티티 타입 필터 (예: PRODUCT, BRAND) */
    val entityTypes: Set<String>? = null,
    
    /** 특정 엔티티 키 목록 */
    val entityKeys: Set<String>? = null,
    
    /** 엔티티 키 패턴 (SQL LIKE) */
    val entityKeyPattern: String? = null,
    
    /** 생성 시간 범위 - 시작 */
    val fromTime: Instant? = null,
    
    /** 생성 시간 범위 - 종료 */
    val toTime: Instant? = null,
    
    /** 슬라이스 타입 필터 */
    val sliceTypes: Set<String>? = null,
    
    /** 뷰 ID 필터 */
    val viewIds: Set<String>? = null,
    
    /** 스키마 ID 필터 */
    val schemaIds: Set<String>? = null,
    
    /** 버전 범위 - 최소 */
    val minVersion: Long? = null,
    
    /** 버전 범위 - 최대 */
    val maxVersion: Long? = null
) {
    init {
        // 최소한 하나의 조건은 있어야 함 (전체 재처리 방지)
        require(
            tenantIds != null ||
            entityTypes != null ||
            entityKeys != null ||
            entityKeyPattern != null ||
            fromTime != null ||
            toTime != null ||
            sliceTypes != null ||
            viewIds != null ||
            schemaIds != null
        ) { "At least one scope condition is required" }
    }
    
    companion object {
        /**
         * 특정 엔티티 키 목록으로 Scope 생성
         */
        fun forEntities(vararg keys: String) = BackfillScope(entityKeys = keys.toSet())
        
        /**
         * 시간 범위로 Scope 생성
         */
        fun forTimeRange(from: Instant, to: Instant) = BackfillScope(fromTime = from, toTime = to)
        
        /**
         * 엔티티 타입으로 Scope 생성
         */
        fun forEntityType(type: String) = BackfillScope(entityTypes = setOf(type))
        
        /**
         * 오늘 데이터
         */
        fun today(): BackfillScope {
            val now = Instant.now()
            val startOfDay = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            return BackfillScope(fromTime = startOfDay, toTime = now)
        }
        
        /**
         * 최근 N시간
         */
        fun lastHours(hours: Long): BackfillScope {
            val now = Instant.now()
            return BackfillScope(
                fromTime = now.minusSeconds(hours * 3600),
                toTime = now
            )
        }
    }
    
    /**
     * 범위 설명 문자열
     */
    fun describe(): String = buildString {
        val conditions = mutableListOf<String>()
        
        tenantIds?.let { conditions.add("tenants: ${it.joinToString()}") }
        entityTypes?.let { conditions.add("types: ${it.joinToString()}") }
        entityKeys?.let { conditions.add("keys: ${it.size} items") }
        entityKeyPattern?.let { conditions.add("pattern: $it") }
        fromTime?.let { conditions.add("from: $it") }
        toTime?.let { conditions.add("to: $it") }
        sliceTypes?.let { conditions.add("slices: ${it.joinToString()}") }
        viewIds?.let { conditions.add("views: ${it.joinToString()}") }
        
        append(conditions.joinToString(" AND "))
    }
}
