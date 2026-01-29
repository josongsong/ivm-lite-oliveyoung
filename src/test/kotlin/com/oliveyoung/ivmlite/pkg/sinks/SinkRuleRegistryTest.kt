package com.oliveyoung.ivmlite.pkg.sinks

import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * SinkRuleRegistry 테스트 (RFC-IMPL-013)
 */
class SinkRuleRegistryTest : StringSpec({

    lateinit var registry: InMemorySinkRuleRegistry

    beforeEach {
        registry = InMemorySinkRuleRegistry()
    }

    "기본 OpenSearch SinkRule이 등록되어 있음" {
        val result = registry.findAllActive()
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<List<SinkRule>>>()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules shouldHaveSize 1
        rules[0].id shouldBe "sinkrule.opensearch.default"
        rules[0].target.type shouldBe SinkTargetType.OPENSEARCH
    }

    "PRODUCT entityType으로 SinkRule 조회" {
        val result = registry.findByEntityType("PRODUCT")
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<List<SinkRule>>>()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules shouldHaveSize 1
        rules[0].input.entityTypes shouldContain "PRODUCT"
    }

    "PRODUCT + CORE sliceType으로 SinkRule 조회" {
        val result = registry.findByEntityAndSliceType("PRODUCT", SliceType.CORE)
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<List<SinkRule>>>()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules shouldHaveSize 1
    }

    "존재하지 않는 entityType → 빈 리스트" {
        val result = registry.findByEntityType("UNKNOWN")
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<List<SinkRule>>>()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules.shouldBeEmpty()
    }

    "DERIVED sliceType → 빈 리스트 (기본 rule은 CORE만)" {
        val result = registry.findByEntityAndSliceType("PRODUCT", SliceType.DERIVED)
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<List<SinkRule>>>()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules.shouldBeEmpty()
    }

    "커스텀 SinkRule 등록" {
        val customRule = SinkRule(
            id = "sinkrule.personalize.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.DERIVED),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.PERSONALIZE,
                endpoint = "arn:aws:personalize:us-east-1:123456789:dataset/test"
            ),
            docId = DocIdSpec("{tenantId}__{entityKey}")
        )
        
        registry.register(customRule)
        
        val result = registry.findByEntityAndSliceType("PRODUCT", SliceType.DERIVED)
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules shouldHaveSize 1
        rules[0].target.type shouldBe SinkTargetType.PERSONALIZE
    }

    "INACTIVE SinkRule은 조회되지 않음" {
        val inactiveRule = SinkRule(
            id = "sinkrule.inactive",
            version = "1.0.0",
            status = SinkRuleStatus.INACTIVE,
            input = SinkRuleInput(entityTypes = listOf("PRODUCT")),
            target = SinkRuleTarget(
                type = SinkTargetType.S3,
                endpoint = "s3://bucket"
            ),
            docId = DocIdSpec("{entityKey}")
        )
        
        registry.register(inactiveRule)
        
        val result = registry.findByEntityType("PRODUCT")
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        // INACTIVE rule은 포함되지 않음 (기본 opensearch rule만)
        rules.none { it.id == "sinkrule.inactive" } shouldBe true
    }

    "ID로 SinkRule 조회" {
        val result = registry.findById("sinkrule.opensearch.default")
        
        result.shouldBeInstanceOf<SinkRuleRegistryPort.Result.Ok<SinkRule?>>()
        val rule = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rule?.id shouldBe "sinkrule.opensearch.default"
    }

    "clear 후 빈 상태" {
        registry.clear()
        
        val result = registry.findAllActive()
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        rules.shouldBeEmpty()
    }

    "Multi-Sink: 하나의 entityType에 여러 SinkRule" {
        // Personalize rule 추가
        val personalizeRule = SinkRule(
            id = "sinkrule.personalize.product",
            version = "1.0.0",
            status = SinkRuleStatus.ACTIVE,
            input = SinkRuleInput(
                sliceTypes = listOf(SliceType.CORE),
                entityTypes = listOf("PRODUCT")
            ),
            target = SinkRuleTarget(
                type = SinkTargetType.PERSONALIZE,
                endpoint = "arn:aws:personalize:test"
            ),
            docId = DocIdSpec("{entityKey}")
        )
        
        registry.register(personalizeRule)
        
        val result = registry.findByEntityAndSliceType("PRODUCT", SliceType.CORE)
        val rules = (result as SinkRuleRegistryPort.Result.Ok).value
        
        // OpenSearch + Personalize
        rules shouldHaveSize 2
        rules.map { it.target.type }.toSet() shouldBe setOf(
            SinkTargetType.OPENSEARCH,
            SinkTargetType.PERSONALIZE
        )
    }
})
