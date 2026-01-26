package com.oliveyoung.ivmlite.shared.ports

import kotlin.reflect.KClass

/**
 * Contract Cache Port (RFC-IMPL-010 Phase C-1)
 *
 * DynamoDB Contract Registry 캐싱 인터페이스.
 * - TTL 기반 자동 만료
 * - LRU eviction (maxSize 초과 시)
 * - negative caching 금지 (null/error 결과 캐싱하지 않음)
 */
interface ContractCachePort {

    /**
     * 캐시에서 값 조회.
     * TTL 만료된 경우 null 반환 (자동 eviction).
     *
     * @param key 캐시 키 (예: "CHANGESET:changeset.v1@1.0.0")
     * @param type 기대 타입
     * @return 캐시된 값 또는 null
     */
    suspend fun <T : Any> get(key: String, type: KClass<T>): T?

    /**
     * 캐시에 값 저장.
     * maxSize 초과 시 LRU 정책으로 오래된 항목 제거.
     *
     * @param key 캐시 키
     * @param value 저장할 값 (null 불가 - negative caching 금지)
     */
    suspend fun <T : Any> put(key: String, value: T)

    /**
     * 특정 키 무효화
     */
    suspend fun invalidate(key: String)

    /**
     * 전체 캐시 무효화
     */
    suspend fun invalidateAll()

    /**
     * 캐시 통계 조회 (모니터링용)
     */
    fun stats(): CacheStats

    /**
     * 캐시 키 생성 헬퍼
     */
    companion object {
        fun key(kind: String, id: String, version: String): String =
            "$kind:$id@$version"
    }
}

/**
 * 캐시 통계 (RFC-IMPL-010 observability)
 */
data class CacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val size: Int,
) {
    val hitRate: Double
        get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses)
}
