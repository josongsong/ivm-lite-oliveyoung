package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * EntityContractResolver - EntityType별 Contract 동적 해석 (Contract is Law)
 *
 * ContractRegistryPort를 통해 RuleSet/ViewDefinition을 로드하여
 * 엔티티 타입별 적절한 계약을 동적으로 결정한다.
 *
 * - Runtime API: LocalYaml (개발) 또는 DynamoDB (production)
 * - Lambda: allModules → LocalYaml
 */
class EntityContractResolver(
    private val contractRegistry: ContractRegistryPort,
) {
    private val logger = LoggerFactory.getLogger(EntityContractResolver::class.java)

    /** EntityType -> RuleSetRef 매핑 (lazy - 한 번만 빌드) */
    private val ruleSetByEntityType: Map<String, ContractRef> by lazy { loadEntityRuleSetMappings() }

    /** EntityType -> ViewDefinition (id + version) 매핑 (lazy - 한 번만 빌드) */
    private val viewDefByEntityType: Map<String, ViewDefInfo> by lazy { loadEntityViewDefMappings() }

    /** EntityType -> RuleSet의 Slice 타입 목록 (lazy) */
    private val sliceTypesByEntityType: Map<String, List<String>> by lazy { loadSliceTypeMappings() }

    /** EntityType -> ViewDefinition ID 전체 목록 (lazy) */
    private val allViewDefsByEntityType: Map<String, List<String>> by lazy { loadAllViewDefMappings() }

    internal data class ViewDefInfo(val id: String, val version: String)

    /**
     * EntityType에서 RuleSetRef 해석
     *
     * EntitySchema YAML의 ruleSetRef 필드에서 조회.
     * @param entityType 엔티티 타입 (예: "product", "brand", "category")
     * @return ContractRef 또는 DomainError
     */
    fun resolveRuleSetRef(entityType: String): arrow.core.Either<DomainError, ContractRef> {
        val ref = ruleSetByEntityType[entityType.uppercase()]
            ?: return arrow.core.Either.Left(
                DomainError.ContractError("No RuleSet mapping for entityType: $entityType")
            )
        return arrow.core.Either.Right(ref)
    }

    /**
     * EntityType에서 ViewDefinition ID 해석
     *
     * ViewDefinition YAML의 entityType 필드로 필터.
     * 동일 entityType에 여러 ViewDef가 있으면 첫 번째 매칭 사용.
     *
     * @param entityType 엔티티 타입 (예: "product", "brand", "category")
     * @return ViewDefinition ID 또는 DomainError
     */
    fun resolveViewDefId(entityType: String): arrow.core.Either<DomainError, String> {
        val info = viewDefByEntityType[entityType.uppercase()]
            ?: return arrow.core.Either.Left(
                DomainError.ContractError("No ViewDefinition for entityType: $entityType")
            )
        return arrow.core.Either.Right(info.id)
    }

    /**
     * EntityType에서 ViewDefinition version 해석
     */
    fun resolveViewDefVersion(entityType: String): arrow.core.Either<DomainError, String> {
        val info = viewDefByEntityType[entityType.uppercase()]
            ?: return arrow.core.Either.Left(
                DomainError.ContractError("No ViewDefinition for entityType: $entityType")
            )
        return arrow.core.Either.Right(info.version)
    }

    /**
     * EntityType에서 RuleSet의 Slice 타입 목록 해석
     *
     * RuleSet YAML의 slices[].type 필드에서 추출.
     */
    fun resolveSliceTypes(entityType: String): List<String> {
        return sliceTypesByEntityType[entityType.uppercase()] ?: emptyList()
    }

    /**
     * EntityType에서 모든 ViewDefinition ID 목록 해석
     */
    fun resolveViewDefIds(entityType: String): List<String> {
        return allViewDefsByEntityType[entityType.uppercase()] ?: emptyList()
    }

    /**
     * 등록된 모든 EntityType → RuleSetRef 매핑 반환
     *
     * FanoutWorkflow 등에서 모든 RuleSet을 순회할 때 사용.
     */
    fun getAllRuleSetRefs(): Map<String, ContractRef> {
        return ruleSetByEntityType
    }

    /**
     * ContractRegistryPort에서 RuleSet 로드 → entityType → ruleSetRef 매핑 빌드
     *
     * entityType이 동일한 RuleSet이 여러 개면 id 오름차순으로 나중 것이 우선
     * (ruleset.product.oliveyoung.v1이 ruleset.core.v1 덮어씀)
     */
    private fun loadEntityRuleSetMappings(): Map<String, ContractRef> {
        return runBlocking {
            val refsResult = contractRegistry.listContractRefs(ContractKind.RULESET, ContractStatus.ACTIVE)
            val refs = when (refsResult) {
                is Result.Ok -> refsResult.value.sortedBy { it.id }
                is Result.Err -> {
                    logger.warn("Failed to list RuleSet contracts: {}", refsResult.error)
                    return@runBlocking emptyMap()
                }
            }

            val mappings = mutableMapOf<String, ContractRef>()
            for (ref in refs) {
                when (val result = contractRegistry.loadRuleSetContract(ref)) {
                    is Result.Ok -> {
                        val entityType = result.value.entityType.uppercase()
                        mappings[entityType] = ref
                        logger.debug("EntitySchema mapping: {} -> {}", entityType, ref)
                    }
                    is Result.Err -> {
                        logger.warn("Failed to load RuleSet {}: {}", ref, result.error)
                    }
                }
            }
            logger.info("Loaded {} EntitySchema → RuleSet mappings: {}", mappings.size, mappings.keys)
            mappings
        }
    }

    /**
     * ContractRegistryPort에서 ViewDefinition 로드 → entityType → viewDefId 매핑 빌드
     */
    private fun loadEntityViewDefMappings(): Map<String, ViewDefInfo> {
        return runBlocking {
            val result = contractRegistry.listViewDefinitions(ContractStatus.ACTIVE)
            val contracts = when (result) {
                is Result.Ok -> result.value
                is Result.Err -> {
                    logger.warn("Failed to list ViewDefinitions: {}", result.error)
                    return@runBlocking emptyMap()
                }
            }

            val mappings = mutableMapOf<String, ViewDefInfo>()
            for (contract in contracts) {
                val entityType = contract.entityType?.uppercase() ?: continue
                if (entityType !in mappings) {
                    mappings[entityType] = ViewDefInfo(contract.meta.id, contract.meta.version.toString())
                    logger.debug("ViewDefinition mapping: {} -> {} (v{})", entityType, contract.meta.id, contract.meta.version)
                }
            }
            logger.info("Loaded {} ViewDefinition mappings: {}", mappings.size, mappings)
            mappings
        }
    }

    /**
     * RuleSet 로드 시 entityType → slice 타입 목록 매핑 빌드
     */
    private fun loadSliceTypeMappings(): Map<String, List<String>> {
        return runBlocking {
            val refsResult = contractRegistry.listContractRefs(ContractKind.RULESET, ContractStatus.ACTIVE)
            val refs = when (refsResult) {
                is Result.Ok -> refsResult.value
                is Result.Err -> return@runBlocking emptyMap()
            }

            val mappings = mutableMapOf<String, List<String>>()
            for (ref in refs) {
                when (val result = contractRegistry.loadRuleSetContract(ref)) {
                    is Result.Ok -> {
                        val entityType = result.value.entityType.uppercase()
                        val sliceTypes = result.value.slices.map { it.type.name }
                        if (entityType !in mappings) {
                            mappings[entityType] = sliceTypes
                            logger.debug("RuleSet slice types: {} -> {}", entityType, sliceTypes)
                        }
                    }
                    else -> {}
                }
            }
            logger.info("Loaded {} RuleSet → SliceType mappings", mappings.size)
            mappings
        }
    }

    /**
     * ViewDefinition 로드 → entityType → 전체 ViewDef ID 목록 빌드
     */
    private fun loadAllViewDefMappings(): Map<String, List<String>> {
        return runBlocking {
            val result = contractRegistry.listViewDefinitions(ContractStatus.ACTIVE)
            val contracts = when (result) {
                is Result.Ok -> result.value
                is Result.Err -> return@runBlocking emptyMap()
            }

            val mappings = mutableMapOf<String, MutableList<String>>()
            for (contract in contracts) {
                val entityType = contract.entityType?.uppercase() ?: continue
                mappings.getOrPut(entityType) { mutableListOf() }.add(contract.meta.id)
            }
            logger.info("Loaded {} ViewDefinition full mappings", mappings.size)
            mappings
        }
    }
}
