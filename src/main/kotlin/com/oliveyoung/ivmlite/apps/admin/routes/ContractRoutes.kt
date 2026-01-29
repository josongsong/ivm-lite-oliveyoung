package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.koin.ktor.ext.inject

/**
 * Contract Routes (계약 관리 API)
 *
 * SOTA 리팩토링:
 * - Service 레이어로 비즈니스 로직 분리
 * - 동적 Contract 파일 로딩 (하드코딩 제거)
 * - StatusPages로 에러 처리 (try-catch 제거)
 *
 * GET /contracts: 전체 Contract 목록
 * GET /contracts/schemas: Entity Schema 목록
 * GET /contracts/rulesets: RuleSet 목록
 * GET /contracts/views: ViewDefinition 목록
 * GET /contracts/sinks: Sink Rule 목록
 * GET /contracts/{kind}/{id}: 특정 Contract 상세 조회
 * GET /contracts/stats: Contract 통계
 */
fun Route.contractRoutes() {
    val contractService by inject<AdminContractService>()

    /**
     * GET /contracts
     * 전체 Contract 목록 조회
     */
    get("/contracts") {
        when (val result = contractService.getAllContracts()) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, ContractListResponse(
                    contracts = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/schemas
     * Entity Schema 목록
     */
    get("/contracts/schemas") {
        when (val result = contractService.getByKind(ContractKind.ENTITY_SCHEMA)) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, ContractListResponse(
                    contracts = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/rulesets
     * RuleSet 목록
     */
    get("/contracts/rulesets") {
        when (val result = contractService.getByKind(ContractKind.RULESET)) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, ContractListResponse(
                    contracts = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/views
     * ViewDefinition 목록
     */
    get("/contracts/views") {
        when (val result = contractService.getByKind(ContractKind.VIEW_DEFINITION)) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, ContractListResponse(
                    contracts = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/sinks
     * Sink Rule 목록
     */
    get("/contracts/sinks") {
        when (val result = contractService.getByKind(ContractKind.SINK_RULE)) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, ContractListResponse(
                    contracts = result.value.map { it.toResponse() },
                    total = result.value.size
                ))
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/{kind}/{id}
     * 특정 Contract 상세 조회 (YAML 원문 포함)
     */
    get("/contracts/{kind}/{id}") {
        val kindParam = call.parameters["kind"]
            ?: throw IllegalArgumentException("Kind is required")
        val id = call.parameters["id"]
            ?: throw IllegalArgumentException("ID is required")

        val kind = ContractKind.fromString(kindParam)
            ?: throw IllegalArgumentException("Invalid contract kind: $kindParam")

        when (val result = contractService.getById(kind, id)) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }

    /**
     * GET /contracts/stats
     * Contract 통계
     */
    get("/contracts/stats") {
        when (val result = contractService.getStats()) {
            is AdminContractService.Result.Ok -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is AdminContractService.Result.Err -> {
                throw result.error
            }
        }
    }
}

// ==================== Response DTOs ====================

@Serializable
data class ContractListResponse(
    val contracts: List<ContractResponse>,
    val total: Int
)

@Serializable
data class ContractResponse(
    val kind: String,
    val id: String,
    val version: String,
    val status: String,
    val fileName: String,
    val content: String,
    val parsed: JsonObject
)

@Serializable
data class ContractStatsResponse(
    val total: Int,
    val byKind: Map<String, Int>,
    val byStatus: Map<String, Int>
)

// ==================== Domain → DTO 변환 ====================

private fun ContractInfo.toResponse() = ContractResponse(
    kind = kind,
    id = id,
    version = version,
    status = status,
    fileName = fileName,
    content = content,
    parsed = parsed.toJsonObject()
)

private fun ContractStats.toResponse() = ContractStatsResponse(
    total = total,
    byKind = byKind,
    byStatus = byStatus
)

/**
 * Map<String, Any?> → JsonObject 변환
 */
private fun Map<String, Any?>.toJsonObject(): JsonObject {
    return JsonObject(this.mapValues { (_, value) -> value.toJsonElement() })
}

private fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(
            this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() }
        )
        is List<*> -> JsonArray(this.map { it.toJsonElement() })
        else -> JsonPrimitive(this.toString())
    }
}
