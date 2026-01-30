package com.oliveyoung.ivmlite.sdk.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HTTP 기반 IvmClient (E2E 테스트용)
 * 실제 HTTP API를 호출하는 SDK 구현
 */
class HttpIvmClient(
    private val config: IvmClientConfig
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Ingest API 호출
     */
    suspend fun ingest(
        tenantId: String,
        entityKey: String,
        version: Long,
        schemaId: String,
        schemaVersion: String,
        payload: JsonObject
    ): Either<DomainError, IngestResponse> {
        val response = httpClient.post("${config.baseUrl}/api/v1/ingest") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", version)
                put("schemaId", schemaId)
                put("schemaVersion", schemaVersion)
                put("payload", payload)
            })
        }

        return if (response.status.isSuccess()) {
            IngestResponse(
                success = true,
                tenantId = tenantId,
                entityKey = entityKey,
                version = version
            ).right()
        } else {
            DomainError.StorageError("Ingest failed: ${response.status}").left()
        }
    }

    /**
     * Slice API 호출
     */
    suspend fun slice(
        tenantId: String,
        entityKey: String,
        version: Long
    ): Either<DomainError, SliceResponse> {
        val response = httpClient.post("${config.baseUrl}/api/v1/slice") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("tenantId", tenantId)
                put("entityKey", entityKey)
                put("version", version)
            })
        }

        return if (response.status.isSuccess()) {
            val body = response.body<Map<String, Any>>()
            val sliceTypes = (body["sliceTypes"] as? List<*>)
                ?: return DomainError.ValidationError("sliceTypes", "Missing required field 'sliceTypes' in slice response").left()
            val count = (body["count"] as? Number)?.toInt()
                ?: return DomainError.ValidationError("count", "Missing required field 'count' in slice response").left()
            SliceResponse(
                success = true,
                sliceTypes = sliceTypes.map { it.toString() },
                count = count
            ).right()
        } else {
            DomainError.StorageError("Slice failed: ${response.status}").left()
        }
    }

    /**
     * Query API v2 호출
     */
    suspend fun queryV2(
        tenantId: String,
        viewId: String,
        entityKey: String,
        version: Long
    ): Either<DomainError, QueryResponse> {
        val response = httpClient.post("${config.baseUrl}/api/v2/query") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("tenantId", tenantId)
                put("viewId", viewId)
                put("entityKey", entityKey)
                put("version", version)
            })
        }

        return if (response.status.isSuccess()) {
            val body = response.body<Map<String, Any>>()
            val data = body["data"] as? String
                ?: return DomainError.ValidationError("data", "Missing required field 'data' in query response").left()
            QueryResponse(
                success = true,
                data = data,
                meta = body["meta"] as? Map<*, *>
            ).right()
        } else {
            DomainError.StorageError("Query failed: ${response.status}").left()
        }
    }

    fun close() {
        runBlocking {
            httpClient.close()
        }
    }
}

data class IngestResponse(
    val success: Boolean,
    val tenantId: String,
    val entityKey: String,
    val version: Long
)

data class SliceResponse(
    val success: Boolean,
    val sliceTypes: List<String>,
    val count: Int
)

data class QueryResponse(
    val success: Boolean,
    val data: String,
    val meta: Map<*, *>?
)
