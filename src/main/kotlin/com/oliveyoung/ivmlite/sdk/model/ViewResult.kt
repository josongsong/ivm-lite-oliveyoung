package com.oliveyoung.ivmlite.sdk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * View 조회 결과
 * 
 * @example 성공 케이스
 * ```kotlin
 * val result = Ivm.query("product.pdp").key("SKU-001").get()
 * if (result.success) {
 *     println(result.data)           // JsonObject
 *     println(result["core"])        // core slice 데이터
 *     println(result.string("name")) // 특정 필드
 * }
 * ```
 * 
 * @example 에러 케이스
 * ```kotlin
 * val result = Ivm.query("product.pdp").key("INVALID").get()
 * if (!result.success) {
 *     println(result.error)    // 에러 메시지
 *     println(result.errorCode) // NOT_FOUND, TIMEOUT 등
 * }
 * ```
 */
data class ViewResult(
    /** 성공 여부 */
    val success: Boolean,
    
    /** View ID */
    val viewId: String,
    
    /** 테넌트 ID */
    val tenantId: String,
    
    /** 엔티티 키 */
    val entityKey: String,
    
    /** 버전 */
    val version: Long,
    
    /** 조회된 데이터 (성공 시) */
    val data: JsonObject = buildJsonObject { },
    
    /** 에러 메시지 (실패 시) */
    val error: String? = null,
    
    /** 에러 코드 (실패 시) */
    val errorCode: String? = null,
    
    /** 메타데이터 (옵션) */
    val meta: Meta? = null
) {
    /**
     * 메타데이터
     */
    data class Meta(
        /** 사용된 Slice 목록 */
        val slicesUsed: List<String> = emptyList(),
        
        /** 누락된 Slice 목록 (partial 응답 시) */
        val missingSlices: List<String> = emptyList(),
        
        /** 사용된 Contract 버전 */
        val contractsUsed: List<String> = emptyList(),
        
        /** 쿼리 소요 시간 (ms) */
        val queryTimeMs: Long = 0,
        
        /** 캐시 히트 여부 */
        val fromCache: Boolean = false,
        
        /** 적용된 일관성 레벨 */
        val consistency: String = "Eventual"
    )

    // ===== Convenience Accessors =====

    /**
     * Slice 데이터 접근 (by slice type)
     * 
     * @example
     * ```kotlin
     * val core = result["core"]      // CORE slice
     * val pricing = result["pricing"] // PRICING slice
     * ```
     */
    operator fun get(sliceType: String): JsonObject? {
        return data[sliceType.lowercase()]?.jsonObject
            ?: data[sliceType.uppercase()]?.jsonObject
            ?: data[sliceType]?.jsonObject
    }

    /**
     * 특정 필드 문자열 값 조회
     * 
     * @example
     * ```kotlin
     * val name = result.string("name")
     * val sku = result.string("core.sku")  // 중첩 경로
     * ```
     */
    fun string(path: String): String? {
        return navigatePath(path)?.jsonPrimitive?.content
    }

    /**
     * 특정 필드 숫자 값 조회
     */
    fun long(path: String): Long? {
        return navigatePath(path)?.jsonPrimitive?.long
    }

    /**
     * 특정 필드 존재 여부
     */
    fun has(path: String): Boolean {
        return navigatePath(path) != null
    }

    /**
     * 경로 탐색 (dot notation 지원)
     */
    private fun navigatePath(path: String): JsonElement? {
        val parts = path.split(".")
        var current: JsonElement? = data

        for (part in parts) {
            current = when (current) {
                is JsonObject -> current[part]
                else -> null
            }
            if (current == null) break
        }

        return current
    }

    // ===== Result Transformations =====

    /**
     * 성공 시 변환
     */
    fun <T> map(transform: (JsonObject) -> T): T? {
        return if (success) transform(data) else null
    }

    /**
     * 성공 시 변환, 실패 시 기본값
     */
    fun <T> mapOrDefault(default: T, transform: (JsonObject) -> T): T {
        return if (success) transform(data) else default
    }

    /**
     * 성공 시 변환, 실패 시 예외
     */
    fun <T> mapOrThrow(transform: (JsonObject) -> T): T {
        if (!success) {
            throw ViewQueryException(
                viewId = viewId,
                entityKey = entityKey,
                error = error ?: "Unknown error",
                errorCode = errorCode
            )
        }
        return transform(data)
    }

    /**
     * 에러 발생 시 예외 던지기
     */
    fun orThrow(): ViewResult {
        if (!success) {
            throw ViewQueryException(
                viewId = viewId,
                entityKey = entityKey,
                error = error ?: "Unknown error",
                errorCode = errorCode
            )
        }
        return this
    }

    companion object {
        /**
         * 성공 결과 생성
         */
        fun success(
            viewId: String,
            tenantId: String,
            entityKey: String,
            version: Long,
            data: JsonObject,
            meta: Meta? = null
        ) = ViewResult(
            success = true,
            viewId = viewId,
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            data = data,
            meta = meta
        )

        /**
         * 실패 결과 생성
         */
        fun failure(
            viewId: String,
            tenantId: String,
            entityKey: String,
            error: String,
            errorCode: String = "ERROR"
        ) = ViewResult(
            success = false,
            viewId = viewId,
            tenantId = tenantId,
            entityKey = entityKey,
            version = 0,
            error = error,
            errorCode = errorCode
        )

        /**
         * Not Found 결과
         */
        fun notFound(
            viewId: String,
            tenantId: String,
            entityKey: String
        ) = failure(
            viewId = viewId,
            tenantId = tenantId,
            entityKey = entityKey,
            error = "Entity not found: $entityKey",
            errorCode = "NOT_FOUND"
        )

        /**
         * 빈 결과 (기본값 사용 시)
         */
        fun empty(viewId: String, default: JsonObject) = ViewResult(
            success = true,
            viewId = viewId,
            tenantId = "",
            entityKey = "",
            version = 0,
            data = default
        )
    }
}

/**
 * View 조회 예외
 */
class ViewQueryException(
    val viewId: String,
    val entityKey: String,
    val error: String,
    val errorCode: String?
) : RuntimeException("Query failed for view '$viewId', entity '$entityKey': $error (code: $errorCode)")
