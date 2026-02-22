package com.oliveyoung.ivmlite.sdk.execution

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * EntityContractResolver 단위 테스트
 *
 * Contract is Law: ContractRegistryPort에서 EntityType별 RuleSet/ViewDef 동적 해석 검증
 */
class EntityContractResolverTest : DescribeSpec({

    val resolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/contracts/v1"))

    describe("resolveRuleSetRef") {

        it("PRODUCT -> ruleset.product.oliveyoung.v1") {
            val result = resolver.resolveRuleSetRef("product")

            result.shouldBeInstanceOf<Either.Right<ContractRef>>()
            result.value.id shouldBe "ruleset.product.oliveyoung.v1"
            result.value.version.toString() shouldBe "1.0.0"
        }

        it("BRAND -> ruleset.brand.v1") {
            val result = resolver.resolveRuleSetRef("brand")

            result.shouldBeInstanceOf<Either.Right<ContractRef>>()
            result.value.id shouldBe "ruleset.brand.v1"
            result.value.version.toString() shouldBe "1.0.0"
        }

        it("case-insensitive") {
            val lower = resolver.resolveRuleSetRef("product")
            val upper = resolver.resolveRuleSetRef("PRODUCT")
            val mixed = resolver.resolveRuleSetRef("Product")

            lower.shouldBeInstanceOf<Either.Right<ContractRef>>()
            upper.shouldBeInstanceOf<Either.Right<ContractRef>>()
            mixed.shouldBeInstanceOf<Either.Right<ContractRef>>()

            lower.value.id shouldBe upper.value.id
            lower.value.id shouldBe mixed.value.id
        }

        it("unknown entityType -> ContractError") {
            val result = resolver.resolveRuleSetRef("unknown_entity")

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
            result.value.shouldBeInstanceOf<DomainError.ContractError>()
        }
    }

    describe("resolveViewDefId") {

        it("PRODUCT -> view.product.core.v1") {
            val result = resolver.resolveViewDefId("product")

            result.shouldBeInstanceOf<Either.Right<String>>()
            result.value shouldBe "view.product.core.v1"
        }

        it("BRAND -> view.brand.detail.v1") {
            val result = resolver.resolveViewDefId("brand")

            result.shouldBeInstanceOf<Either.Right<String>>()
            result.value shouldBe "view.brand.detail.v1"
        }

        it("case-insensitive") {
            val lower = resolver.resolveViewDefId("brand")
            val upper = resolver.resolveViewDefId("BRAND")

            lower.shouldBeInstanceOf<Either.Right<String>>()
            upper.shouldBeInstanceOf<Either.Right<String>>()

            lower.value shouldBe upper.value
        }

        it("unknown entityType -> ContractError") {
            val result = resolver.resolveViewDefId("nonexistent")

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
            result.value.shouldBeInstanceOf<DomainError.ContractError>()
        }
    }

    describe("resolveViewDefVersion") {

        it("PRODUCT -> version 반환") {
            val result = resolver.resolveViewDefVersion("product")

            result.shouldBeInstanceOf<Either.Right<String>>()
            result.value shouldBe "1.0.0"
        }

        it("BRAND -> version 반환") {
            val result = resolver.resolveViewDefVersion("brand")

            result.shouldBeInstanceOf<Either.Right<String>>()
        }

        it("unknown entityType -> ContractError") {
            val result = resolver.resolveViewDefVersion("nonexistent")

            result.shouldBeInstanceOf<Either.Left<DomainError>>()
            result.value.shouldBeInstanceOf<DomainError.ContractError>()
        }
    }

    describe("resolveSliceTypes") {

        it("PRODUCT -> SliceType 목록 반환 (비어있지 않음)") {
            val sliceTypes = resolver.resolveSliceTypes("product")

            sliceTypes.isNotEmpty() shouldBe true
        }

        it("BRAND -> SliceType 목록 반환") {
            val sliceTypes = resolver.resolveSliceTypes("brand")

            sliceTypes.isNotEmpty() shouldBe true
        }

        it("case-insensitive") {
            val lower = resolver.resolveSliceTypes("product")
            val upper = resolver.resolveSliceTypes("PRODUCT")

            lower shouldBe upper
        }

        it("unknown entityType -> 빈 목록") {
            val sliceTypes = resolver.resolveSliceTypes("nonexistent")

            sliceTypes shouldBe emptyList()
        }
    }

    describe("resolveViewDefIds") {

        it("PRODUCT -> ViewDef ID 목록 반환") {
            val viewDefIds = resolver.resolveViewDefIds("product")

            viewDefIds.isNotEmpty() shouldBe true
        }

        it("unknown entityType -> 빈 목록") {
            val viewDefIds = resolver.resolveViewDefIds("nonexistent")

            viewDefIds shouldBe emptyList()
        }
    }

    describe("getAllRuleSetRefs") {

        it("전체 RuleSet 매핑 반환") {
            val allRefs = resolver.getAllRuleSetRefs()

            allRefs.isNotEmpty() shouldBe true
            allRefs.containsKey("PRODUCT") shouldBe true
            allRefs.containsKey("BRAND") shouldBe true
            allRefs["PRODUCT"]!!.id shouldBe "ruleset.product.oliveyoung.v1"
        }
    }

    describe("invalid resourceRoot") {

        it("nonexistent path -> empty cache, all resolutions fail") {
            val badResolver = EntityContractResolver(LocalYamlContractRegistryAdapter("/nonexistent/path"))

            val ruleSetResult = badResolver.resolveRuleSetRef("product")
            val viewDefResult = badResolver.resolveViewDefId("product")
            val versionResult = badResolver.resolveViewDefVersion("product")

            ruleSetResult.shouldBeInstanceOf<Either.Left<DomainError>>()
            viewDefResult.shouldBeInstanceOf<Either.Left<DomainError>>()
            versionResult.shouldBeInstanceOf<Either.Left<DomainError>>()

            badResolver.resolveSliceTypes("product") shouldBe emptyList()
            badResolver.resolveViewDefIds("product") shouldBe emptyList()
            badResolver.getAllRuleSetRefs() shouldBe emptyMap()
        }
    }
})
