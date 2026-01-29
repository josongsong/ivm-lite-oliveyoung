package com.oliveyoung.ivmlite.apps.admin.dto

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Admin API Error DTO
 *
 * SOTA 에러 트레이싱:
 * - requestId: 요청별 고유 ID (로그 추적용)
 * - timestamp: 에러 발생 시각
 * - path: API 경로
 * - trace: 스택트레이스 (dev 환경에서만)
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val timestamp: String? = null,
    val path: String? = null,
    val details: Map<String, String>? = null,
    val trace: List<String>? = null,
) {
    companion object {
        /**
         * DomainError를 ApiError로 변환
         */
        fun from(error: DomainError): ApiError = when (error) {
            is DomainError.ValidationError -> ApiError(
                code = error.errorCode,
                message = error.message ?: "Validation failed",
                details = mapOf("field" to error.field),
            )
            is DomainError.NotFoundError -> ApiError(
                code = error.errorCode,
                message = error.message ?: "Not found",
                details = mapOf("entity" to error.entity, "key" to error.key),
            )
            is DomainError.ExternalServiceError -> ApiError(
                code = error.errorCode,
                message = error.message ?: "External service error",
                details = mapOf("service" to error.service),
            )
            else -> ApiError(
                code = error.errorCode,
                message = error.message ?: "Unknown error",
            )
        }

        /**
         * 에러 트레이싱 정보 추가
         */
        fun from(
            error: DomainError,
            requestId: String,
            path: String,
            includeTrace: Boolean = false
        ): ApiError {
            val base = from(error)
            return base.copy(
                requestId = requestId,
                timestamp = Instant.now().toString(),
                path = path,
                trace = if (includeTrace) {
                    error.stackTrace.take(10).map { it.toString() }
                } else null
            )
        }

        /**
         * 일반 Exception에서 ApiError 생성
         */
        fun fromException(
            code: String,
            message: String,
            requestId: String,
            path: String,
            cause: Throwable? = null,
            includeTrace: Boolean = false
        ): ApiError = ApiError(
            code = code,
            message = message,
            requestId = requestId,
            timestamp = Instant.now().toString(),
            path = path,
            trace = if (includeTrace && cause != null) {
                cause.stackTrace.take(10).map { it.toString() }
            } else null
        )

        /**
         * 새 requestId 생성
         */
        fun generateRequestId(): String = UUID.randomUUID().toString().take(8)
    }
}

/**
 * DomainError를 Ktor HttpStatusCode로 변환
 */
fun DomainError.toKtorStatus(): HttpStatusCode = HttpStatusCode.fromValue(this.toHttpStatus())
