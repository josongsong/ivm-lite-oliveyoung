package com.oliveyoung.ivmlite.sdk.client

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
    ): IngestResponse {
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
            )
        } else {
            throw RuntimeException("Ingest failed: ${response.status}")
        }
    }

    /**
     * Slice API 호출
     */
    suspend fun slice(
        tenantId: String,
        entityKey: String,
        version: Long
    ): SliceResponse {
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
                ?: throw RuntimeException("Missing required field 'sliceTypes' in slice response")
            val count = (body["count"] as? Number)?.toInt()
                ?: throw RuntimeException("Missing required field 'count' in slice response")
            SliceResponse(
                success = true,
                sliceTypes = sliceTypes.map { it.toString() },
                count = count
            )
        } else {
            throw RuntimeException("Slice failed: ${response.status}")
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
    ): QueryResponse {
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
                ?: throw RuntimeException("Missing required field 'data' in query response")
            QueryResponse(
                success = true,
                data = data,
                meta = body["meta"] as? Map<*, *>
            )
        } else {
            throw RuntimeException("Query failed: ${response.status}")
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
