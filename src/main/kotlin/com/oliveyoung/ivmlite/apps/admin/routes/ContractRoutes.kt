package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.File

/**
 * Contract Routes (계약 관리 API)
 *
 * GET /contracts: 전체 Contract 목록
 * GET /contracts/schemas: Entity Schema 목록
 * GET /contracts/rulesets: RuleSet 목록
 * GET /contracts/views: ViewDefinition 목록
 * GET /contracts/{kind}/{id}: 특정 Contract 상세 조회
 */
fun Route.contractRoutes() {
    val contractRegistry by inject<ContractRegistryPort>()

    /**
     * GET /contracts
     * 전체 Contract 목록 조회
     */
    get("/contracts") {
        try {
            val contracts = loadAllContracts()
            call.respond(HttpStatusCode.OK, ContractListResponse(
                contracts = contracts,
                total = contracts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to load contracts", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "CONTRACT_ERROR", message = "Failed to load contracts: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/schemas
     * Entity Schema 목록
     */
    get("/contracts/schemas") {
        try {
            val contracts = loadAllContracts().filter { it.kind == "ENTITY_SCHEMA" }
            call.respond(HttpStatusCode.OK, ContractListResponse(
                contracts = contracts,
                total = contracts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to load schemas", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "SCHEMA_ERROR", message = "Failed to load schemas: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/rulesets
     * RuleSet 목록
     */
    get("/contracts/rulesets") {
        try {
            val contracts = loadAllContracts().filter { it.kind == "RULESET" }
            call.respond(HttpStatusCode.OK, ContractListResponse(
                contracts = contracts,
                total = contracts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to load rulesets", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RULESET_ERROR", message = "Failed to load rulesets: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/views
     * ViewDefinition 목록
     */
    get("/contracts/views") {
        try {
            val contracts = loadAllContracts().filter { it.kind == "VIEW_DEFINITION" }
            call.respond(HttpStatusCode.OK, ContractListResponse(
                contracts = contracts,
                total = contracts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to load view definitions", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "VIEW_ERROR", message = "Failed to load view definitions: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/sinks
     * Sink Rule 목록
     */
    get("/contracts/sinks") {
        try {
            val contracts = loadAllContracts().filter { it.kind == "SINK_RULE" }
            call.respond(HttpStatusCode.OK, ContractListResponse(
                contracts = contracts,
                total = contracts.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to load sink rules", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "SINK_ERROR", message = "Failed to load sink rules: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/{kind}/{id}
     * 특정 Contract 상세 조회 (YAML 원문 포함)
     */
    get("/contracts/{kind}/{id}") {
        try {
            val kind = call.parameters["kind"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "MISSING_KIND", message = "Kind is required"))
                return@get
            }
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "MISSING_ID", message = "ID is required"))
                return@get
            }

            val contract = loadAllContracts().find { 
                it.kind.equals(kind, ignoreCase = true) && it.id == id 
            }
            
            if (contract == null) {
                call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Contract not found: $kind/$id"))
                return@get
            }

            call.respond(HttpStatusCode.OK, contract)
        } catch (e: Exception) {
            call.application.log.error("Failed to load contract", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "CONTRACT_ERROR", message = "Failed to load contract: ${e.message}")
            )
        }
    }

    /**
     * GET /contracts/stats
     * Contract 통계
     */
    get("/contracts/stats") {
        try {
            val contracts = loadAllContracts()
            val byKind = contracts.groupBy { it.kind }.mapValues { it.value.size }
            val byStatus = contracts.groupBy { it.status }.mapValues { it.value.size }
            
            call.respond(HttpStatusCode.OK, ContractStatsResponse(
                total = contracts.size,
                byKind = byKind,
                byStatus = byStatus
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get contract stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "STATS_ERROR", message = "Failed to get contract stats: ${e.message}")
            )
        }
    }
}

/**
 * 모든 Contract YAML 파일 로드
 */
private fun loadAllContracts(): List<ContractDto> {
    val contracts = mutableListOf<ContractDto>()
    
    // Classpath에서 contracts 디렉토리 로드
    val resourceStream = object {}.javaClass.getResourceAsStream("/contracts/v1") 
    if (resourceStream != null) {
        resourceStream.close()
    }
    
    // JAR 내부 또는 개발 환경에서 모두 동작하도록 처리
    val contractFiles = listOf(
        "entity-product.v1.yaml",
        "entity-brand.v1.yaml",
        "entity-category.v1.yaml",
        "ruleset.v1.yaml",
        "ruleset-product-doc001.v1.yaml",
        "view-definition.v1.yaml",
        "view-product-core.v1.yaml",
        "view-product-detail.v1.yaml",
        "view-product-search.v1.yaml",
        "view-product-cart.v1.yaml",
        "view-brand-detail.v1.yaml",
        "sinkrule-opensearch-product.v1.yaml",
        "join-spec.v1.yaml",
        "changeset.v1.yaml"
    )
    
    val yaml = org.yaml.snakeyaml.Yaml()
    
    for (fileName in contractFiles) {
        try {
            val stream = object {}.javaClass.getResourceAsStream("/contracts/v1/$fileName")
            if (stream != null) {
                val content = stream.bufferedReader().use { it.readText() }
                @Suppress("UNCHECKED_CAST")
                val map = yaml.load<Map<String, Any?>>(content) as? Map<String, Any?> ?: continue
                
                val kind = map["kind"]?.toString() ?: continue
                val id = map["id"]?.toString() ?: continue
                val version = map["version"]?.toString() ?: "1.0.0"
                val status = map["status"]?.toString() ?: "ACTIVE"
                
                contracts.add(ContractDto(
                    kind = kind,
                    id = id,
                    version = version,
                    status = status,
                    fileName = fileName,
                    content = content,
                    parsed = map
                ))
            }
        } catch (e: Exception) {
            // Skip invalid files
        }
    }
    
    return contracts
}

// ==================== Response DTOs ====================

@Serializable
data class ContractListResponse(
    val contracts: List<ContractDto>,
    val total: Int
)

@Serializable
data class ContractDto(
    val kind: String,
    val id: String,
    val version: String,
    val status: String,
    val fileName: String,
    val content: String,
    val parsed: Map<String, @Serializable(with = AnySerializer::class) Any?>
)

@Serializable
data class ContractStatsResponse(
    val total: Int,
    val byKind: Map<String, Int>,
    val byStatus: Map<String, Int>
)

/**
 * Any 타입을 위한 Serializer
 */
object AnySerializer : kotlinx.serialization.KSerializer<Any?> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Any")
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any?) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: throw kotlinx.serialization.SerializationException("This serializer only works with Json")
        
        val jsonElement = toJsonElement(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any? {
        throw kotlinx.serialization.SerializationException("Deserialization is not supported")
    }
    
    private fun toJsonElement(value: Any?): kotlinx.serialization.json.JsonElement {
        return when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is String -> kotlinx.serialization.json.JsonPrimitive(value)
            is Number -> kotlinx.serialization.json.JsonPrimitive(value)
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
            is Map<*, *> -> kotlinx.serialization.json.JsonObject(
                value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) }
            )
            is List<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElement(it) })
            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
    }
}
