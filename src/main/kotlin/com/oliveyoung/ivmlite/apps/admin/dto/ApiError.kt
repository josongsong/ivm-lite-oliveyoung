package com.oliveyoung.ivmlite.apps.admin.dto

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * Admin API Error DTO
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
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
    }
}

/**
 * DomainError를 Ktor HttpStatusCode로 변환
 */
fun DomainError.toKtorStatus(): HttpStatusCode = HttpStatusCode.fromValue(this.toHttpStatus())
