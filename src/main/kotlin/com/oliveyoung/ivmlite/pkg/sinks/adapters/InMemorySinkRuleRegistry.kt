package com.oliveyoung.ivmlite.pkg.sinks.adapters
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.SliceType

/**
 * InMemory SinkRule Registry (개발/테스트용)
 *
 * 기본 SinkRule 제공:
 * - PRODUCT, BRAND, CATEGORY → OpenSearch
 */
class InMemorySinkRuleRegistry : SinkRuleRegistryPort {

    private val rules = mutableMapOf<String, SinkRule>()

    init {
        // 기본 OpenSearch SinkRule 등록
        registerDefaultRules()
    }

    private fun registerDefaultRules() {
        val opensearchRule = SinkRule(
            id = "sinkrule.opensearch.default",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                type = InputType.SLICE,
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT", "BRAND", "CATEGORY")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.OPENSEARCH,
                endpoint = System.getenv("OPENSEARCH_ENDPOINT") ?: "http://localhost:9200",
                indexPattern = "ivm-products-{tenantId}",
                auth = AuthSpec(
                    type = AuthType.BASIC,
                    username = System.getenv("OPENSEARCH_USERNAME"),
                    password = System.getenv("OPENSEARCH_PASSWORD")
                )
            ),
            docId = DocIdSpec(pattern = "{tenantId}__{entityKey}")
        )
        rules[opensearchRule.id] = opensearchRule
    }

    override suspend fun findByEntityAndSliceType(
        entityType: String,
        sliceType: SliceType
    ): Result<List<SinkRule>> {
        val matched = rules.values.filter { rule ->
            rule.status == SinkRuleStatus.ACTIVE &&
                rule.input.entityTypes.contains(entityType) &&
                rule.input.sliceTypes.contains(sliceType)
        }
        return Result.Ok(matched)
    }

    override suspend fun findByEntityType(entityType: String): Result<List<SinkRule>> {
        val matched = rules.values.filter { rule ->
            rule.status == SinkRuleStatus.ACTIVE &&
                rule.input.entityTypes.contains(entityType)
        }
        return Result.Ok(matched)
    }

    override suspend fun findAllActive(): Result<List<SinkRule>> {
        val active = rules.values.filter { it.status == SinkRuleStatus.ACTIVE }
        return Result.Ok(active)
    }

    override suspend fun findById(id: String): Result<SinkRule?> {
        return Result.Ok(rules[id])
    }

    // === Test Helpers ===

    fun register(rule: SinkRule) {
        rules[rule.id] = rule
    }

    fun clear() {
        rules.clear()
    }
}
