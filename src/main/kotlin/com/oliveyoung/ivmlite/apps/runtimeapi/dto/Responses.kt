package com.oliveyoung.ivmlite.apps.runtimeapi.dto

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * API Response DTOs
 */

@Serializable
data class IngestResponse(
    val success: Boolean,
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val payloadHash: String? = null,
)

@Serializable
data class SliceResponse(
    val success: Boolean,
    val sliceTypes: List<String>,
    val count: Int = sliceTypes.size,
)

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
