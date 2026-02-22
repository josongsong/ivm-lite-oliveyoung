package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Admin Contract Service (RFC-022)
 *
 * ContractRegistryPort + SinkRuleRegistryPort 기반 Contract 조회.
 * DynamoDB/LocalYaml 모두 지원.
 */
class AdminContractService(
    private val contractRegistry: ContractRegistryPort,
    private val sinkRuleRegistry: SinkRuleRegistryPort,
) {

    // ==================== Public API ====================

    fun getAllContracts(): Result<List<ContractInfo>> {
        return try {
            Result.Ok(loadAllContractsInternal())
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contracts: ${e.message}"))
        }
    }

    fun getByKind(kind: ContractKind): Result<List<ContractInfo>> {
        return try {
            val contracts = loadAllContractsInternal().filter { it.kind == kind.wireValue }
            Result.Ok(contracts)
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contracts by kind: ${e.message}"))
        }
    }

    fun getById(kind: ContractKind, id: String): Result<ContractInfo> {
        return try {
            val contract = loadAllContractsInternal().find {
                it.kind.equals(kind.wireValue, ignoreCase = true) && it.id == id
            }
            if (contract != null) {
                Result.Ok(contract)
            } else {
                Result.Err(DomainError.NotFoundError("Contract", "${kind.wireValue}/$id"))
            }
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contract: ${e.message}"))
        }
    }

    fun getStats(): Result<ContractStats> {
        return try {
            val contracts = loadAllContractsInternal()
            val byKind = contracts.groupBy { it.kind }.mapValues { it.value.size }
            val byStatus = contracts.groupBy { it.status }.mapValues { it.value.size }

            Result.Ok(
                ContractStats(
                    total = contracts.size,
                    byKind = byKind,
                    byStatus = byStatus
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get contract stats: ${e.message}"))
        }
    }

    // ==================== Private: ContractRegistryPort + SinkRuleRegistryPort 기반 ====================

    private fun loadAllContractsInternal(): List<ContractInfo> = runBlocking {
        val contracts = mutableListOf<ContractInfo>()

        // RuleSet
        when (val r = contractRegistry.listContractRefs(ContractKind.RULESET, ContractStatus.ACTIVE)) {
            is Result.Ok -> r.value.forEach { ref ->
                when (val load = contractRegistry.loadRuleSetContract(ref)) {
                    is Result.Ok -> {
                        val rs = load.value
                        val parsed = buildMap<String, Any?> {
                            put("kind", "RULESET")
                            put("id", rs.meta.id)
                            put("version", rs.meta.version.toString())
                            put("status", rs.meta.status.name)
                            put("entityType", rs.entityType)
                            put("slices", rs.slices.map { mapOf("type" to it.type.name) })
                            put("impactMap", rs.impactMap.mapKeys { it.key.name }.mapValues { it.value })
                        }
                        contracts.add(ContractInfo(
                            kind = ContractKind.RULESET.wireValue,
                            id = rs.meta.id,
                            version = rs.meta.version.toString(),
                            status = rs.meta.status.name,
                            fileName = "registry:${rs.meta.id}",
                            content = parsed.toJsonObject().toString(),
                            parsed = parsed
                        ))
                    }
                    is Result.Err -> {}
                }
            }
            is Result.Err -> {}
        }

        // ViewDefinition
        when (val r = contractRegistry.listViewDefinitions(ContractStatus.ACTIVE)) {
            is Result.Ok -> r.value.forEach { vd ->
                val parsed = buildMap<String, Any?> {
                    put("kind", "VIEW_DEFINITION")
                    put("id", vd.meta.id)
                    put("version", vd.meta.version.toString())
                    put("status", vd.meta.status.name)
                    put("entityType", vd.entityType)
                    put("viewName", vd.viewName)
                    put("requiredSlices", vd.requiredSlices.map { it.name })
                    put("optionalSlices", vd.optionalSlices.map { it.name })
                }
                contracts.add(ContractInfo(
                    kind = ContractKind.VIEW_DEFINITION.wireValue,
                    id = vd.meta.id,
                    version = vd.meta.version.toString(),
                    status = vd.meta.status.name,
                    fileName = "registry:${vd.meta.id}",
                    content = parsed.toJsonObject().toString(),
                    parsed = parsed
                ))
            }
            is Result.Err -> {}
        }

        // ENTITY_SCHEMA (RuleSet에서 entityType 추출, 중복 제거)
        val entityTypesSeen = mutableSetOf<String>()
        when (val r = contractRegistry.listContractRefs(ContractKind.RULESET, ContractStatus.ACTIVE)) {
            is Result.Ok -> r.value.forEach { ref ->
                when (val load = contractRegistry.loadRuleSetContract(ref)) {
                    is Result.Ok -> {
                        val rs = load.value
                        if (entityTypesSeen.add(rs.entityType)) {
                            val parsed = buildMap<String, Any?> {
                                put("kind", "ENTITY_SCHEMA")
                                put("id", "entity-${rs.entityType}.v1")
                                put("version", "1.0.0")
                                put("status", "ACTIVE")
                                put("entityType", rs.entityType)
                                put("fields", emptyList<Map<String, Any?>>())
                            }
                            contracts.add(ContractInfo(
                                kind = ContractKind.ENTITY_SCHEMA.wireValue,
                                id = "entity-${rs.entityType}.v1",
                                version = "1.0.0",
                                status = "ACTIVE",
                                fileName = "registry:entity-${rs.entityType}",
                                content = parsed.toJsonObject().toString(),
                                parsed = parsed
                            ))
                        }
                    }
                    is Result.Err -> {}
                }
            }
            is Result.Err -> {}
        }

        // CHANGESET
        when (val r = contractRegistry.listContractRefs(ContractKind.CHANGESET, ContractStatus.ACTIVE)) {
            is Result.Ok -> r.value.forEach { ref ->
                when (val load = contractRegistry.loadChangeSetContract(ref)) {
                    is Result.Ok -> {
                        val c = load.value
                        val parsed = buildMap<String, Any?> {
                            put("kind", "CHANGESET")
                            put("id", c.meta.id)
                            put("version", c.meta.version.toString())
                            put("status", c.meta.status.name)
                        }
                        contracts.add(ContractInfo(
                            kind = ContractKind.CHANGESET.wireValue,
                            id = c.meta.id,
                            version = c.meta.version.toString(),
                            status = c.meta.status.name,
                            fileName = "registry:${c.meta.id}",
                            content = parsed.toJsonObject().toString(),
                            parsed = parsed
                        ))
                    }
                    is Result.Err -> {}
                }
            }
            is Result.Err -> {}
        }

        // JOIN_SPEC
        when (val r = contractRegistry.listContractRefs(ContractKind.JOIN_SPEC, ContractStatus.ACTIVE)) {
            is Result.Ok -> r.value.forEach { ref ->
                when (val load = contractRegistry.loadJoinSpecContract(ref)) {
                    is Result.Ok -> {
                        val j = load.value
                        val parsed = buildMap<String, Any?> {
                            put("kind", "JOIN_SPEC")
                            put("id", j.meta.id)
                            put("version", j.meta.version.toString())
                            put("status", j.meta.status.name)
                        }
                        contracts.add(ContractInfo(
                            kind = ContractKind.JOIN_SPEC.wireValue,
                            id = j.meta.id,
                            version = j.meta.version.toString(),
                            status = j.meta.status.name,
                            fileName = "registry:${j.meta.id}",
                            content = parsed.toJsonObject().toString(),
                            parsed = parsed
                        ))
                    }
                    is Result.Err -> {}
                }
            }
            is Result.Err -> {}
        }

        // SINK_RULE
        when (val r = sinkRuleRegistry.findAllActive()) {
            is Result.Ok -> r.value.forEach { sr ->
                val parsed = buildMap<String, Any?> {
                    put("kind", "SINK_RULE")
                    put("id", sr.id)
                    put("version", sr.version)
                    put("status", sr.status.name)
                    put("input", mapOf(
                        "entityTypes" to sr.input.entityTypes,
                        "sliceTypes" to sr.input.sliceTypes.map { it.name }
                    ))
                    put("target", mapOf("type" to sr.target.type.name))
                }
                contracts.add(ContractInfo(
                    kind = ContractKind.SINK_RULE.wireValue,
                    id = sr.id,
                    version = sr.version,
                    status = sr.status.name,
                    fileName = "registry:${sr.id}",
                    content = parsed.toJsonObject().toString(),
                    parsed = parsed
                ))
            }
            is Result.Err -> {}
        }

        contracts
    }

    private fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(
        mapValues { (_, v) -> v.toJsonElement() }
    )

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(
            (this as Map<String, Any?>).mapValues { (_, v) -> v.toJsonElement() }
        )
        is List<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}

// ==================== Domain Models ====================

data class ContractInfo(
    val kind: String,
    val id: String,
    val version: String,
    val status: String,
    val fileName: String,
    val content: String,
    val parsed: Map<String, Any?>
)

data class ContractStats(
    val total: Int,
    val byKind: Map<String, Int>,
    val byStatus: Map<String, Int>
)
