package com.oliveyoung.ivmlite.pkg.contracts.domain

/**
 * ContractFileRegistry - YAML Contract 파일 목록 단일 관리 (SSOT)
 *
 * EntityContractResolver와 LocalYamlContractRegistryAdapter가 동일 파일 목록을
 * 각각 하드코딩하던 DRY 위반을 해결.
 *
 * 새 Contract YAML 추가 시 이 파일만 수정하면 됨.
 */
object ContractFileRegistry {

    val ENTITY_SCHEMA_FILES = listOf(
        "entity-product.v1.yaml",
        "entity-brand.v1.yaml",
    )

    val RULESET_FILES = listOf(
        "ruleset-core.v1.yaml",
        "ruleset-product-oliveyoung.v1.yaml",
        "ruleset.brand.v1.yaml",
    )

    val VIEW_DEFINITION_FILES = listOf(
        "view-product-core.v1.yaml",
        "view-product-search.v1.yaml",
        "view-product-storefront.v1.yaml",
        "view-product-pdp.v1.yaml",
        "view-brand-detail.v1.yaml",
    )

    val RULESET_ID_TO_FILE = mapOf(
        "ruleset.core.v1" to "ruleset-core.v1.yaml",
        "ruleset.product.oliveyoung.v1" to "ruleset-product-oliveyoung.v1.yaml",
        "ruleset.brand.v1" to "ruleset.brand.v1.yaml",
    )

    val VIEWDEF_ID_TO_FILE = mapOf<String, String>()

    val SINK_RULE_FILES = listOf(
        "sinkrule-opensearch-product.v1.yaml",
        "sinkrule-s3-product.v1.yaml",
        "sinkrule-personalize-product.v1.yaml",
    )
}
