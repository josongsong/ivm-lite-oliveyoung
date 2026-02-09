package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.util.concurrent.CompletableFuture

/**
 * RFC-IMPL-016: RefIndexSlice 패턴 Contract 테스트
 *
 * TDD 전수 테스트:
 * 1. SliceKind 파싱 (STANDARD, REF_INDEX, ENRICHMENT)
 * 2. Brand RuleSet 로드 (CORE + SUMMARY)
 * 3. Product RuleSet ENRICHED 슬라이스 파싱
 * 4. MapFields 배열 형태 매핑 파싱
 * 5. 잘못된 SliceKind → ContractError
 */
class RefIndexSliceContractTest : StringSpec({

    val tableName = "test-contract-registry"

    fun createMockClient(responseItem: Map<String, AttributeValue>?): DynamoDbAsyncClient {
        val mockClient = mockk<DynamoDbAsyncClient>()
        every { mockClient.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
            GetItemResponse.builder()
                .item(responseItem ?: emptyMap())
                .build(),
        )
        return mockClient
    }

    fun attr(value: String): AttributeValue = AttributeValue.builder().s(value).build()

    // ==================== SliceKind 파싱 테스트 ====================

    "SliceKind.fromDbValue - STANDARD" {
        SliceKind.fromDbValue("standard") shouldBe SliceKind.STANDARD
        SliceKind.fromDbValue("STANDARD") shouldBe SliceKind.STANDARD
    }

    "SliceKind.fromDbValue - REF_INDEX" {
        SliceKind.fromDbValue("ref_index") shouldBe SliceKind.REF_INDEX
        SliceKind.fromDbValue("REF_INDEX") shouldBe SliceKind.REF_INDEX
    }

    "SliceKind.fromDbValue - ENRICHMENT" {
        SliceKind.fromDbValue("enrichment") shouldBe SliceKind.ENRICHMENT
        SliceKind.fromDbValue("ENRICHMENT") shouldBe SliceKind.ENRICHMENT
    }

    "SliceKind.fromDbValue - 알 수 없는 값 → 예외" {
        try {
            SliceKind.fromDbValue("UNKNOWN")
            throw AssertionError("예외가 발생해야 함")
        } catch (e: DomainError.ValidationError) {
            e.message?.contains("Unknown SliceKind") shouldBe true
        }
    }

    "SliceKind.toDbValue - lowercase 변환" {
        SliceKind.STANDARD.toDbValue() shouldBe "standard"
        SliceKind.REF_INDEX.toDbValue() shouldBe "ref_index"
        SliceKind.ENRICHMENT.toDbValue() shouldBe "enrichment"
    }

    // ==================== LocalYaml - Brand RuleSet 테스트 ====================

    "LocalYaml - Brand RuleSet 로드" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "ruleset.brand.v1"
        contract.entityType shouldBe "BRAND"
    }

    "LocalYaml - Brand SUMMARY 슬라이스는 REF_INDEX sliceKind" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val summarySlice = contract.slices.find { it.type == SliceType.SUMMARY }
        summarySlice shouldNotBe null
        summarySlice!!.sliceKind shouldBe SliceKind.REF_INDEX
    }

    "LocalYaml - Brand SUMMARY 슬라이스는 MapFields 빌드 규칙" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val summarySlice = contract.slices.find { it.type == SliceType.SUMMARY }
        summarySlice shouldNotBe null
        summarySlice!!.buildRules.shouldBeInstanceOf<SliceBuildRules.MapFields>()

        val mappings = (summarySlice.buildRules as SliceBuildRules.MapFields).mappings
        mappings["brandId"] shouldBe "id"
        mappings["name"] shouldBe "name"
        mappings["logoUrl"] shouldBe "logoUrl"
    }

    "LocalYaml - Brand CORE 슬라이스는 STANDARD sliceKind (기본값)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val coreSlice = contract.slices.find { it.type == SliceType.CORE }
        coreSlice shouldNotBe null
        coreSlice!!.sliceKind shouldBe SliceKind.STANDARD
    }

    // ==================== LocalYaml - Product ENRICHED 테스트 ====================

    "LocalYaml - Product RuleSet에 ENRICHED 슬라이스 존재" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.1.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val enrichedSlice = contract.slices.find { it.type == SliceType.ENRICHED }
        enrichedSlice shouldNotBe null
        enrichedSlice!!.sliceKind shouldBe SliceKind.ENRICHMENT
    }

    "LocalYaml - Product ENRICHED 슬라이스는 joins 포함" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.1.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val enrichedSlice = contract.slices.find { it.type == SliceType.ENRICHED }
        enrichedSlice shouldNotBe null
        enrichedSlice!!.joins.isNotEmpty() shouldBe true

        val brandJoin = enrichedSlice.joins.find { it.name == "brand" }
        brandJoin shouldNotBe null
        brandJoin!!.targetEntityType shouldBe "BRAND"
        brandJoin.required shouldBe false
    }

    "LocalYaml - Product impactMap에 ENRICHED 포함" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.1.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        contract.impactMap[SliceType.ENRICHED] shouldNotBe null
        contract.impactMap[SliceType.ENRICHED]!!.contains("/masterInfo/brand/code") shouldBe true
    }

    // ==================== DynamoDB - SliceKind 파싱 테스트 ====================

    "DynamoDB - sliceKind 없으면 STANDARD (기본값)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": ["*"]
                    }
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.slices[0].sliceKind shouldBe SliceKind.STANDARD
    }

    "DynamoDB - sliceKind=REF_INDEX 파싱" {
        val dataJson = """{
            "entityType": "BRAND",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "SUMMARY",
                    "sliceKind": "REF_INDEX",
                    "buildRules": {
                        "type": "MapFields",
                        "mappings": {"brandId": "id", "name": "name"}
                    }
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.brand.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.slices[0].sliceKind shouldBe SliceKind.REF_INDEX
    }

    "DynamoDB - sliceKind=ENRICHMENT 파싱" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "ENRICHED",
                    "sliceKind": "ENRICHMENT",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": []
                    },
                    "joins": [
                        {
                            "name": "brand",
                            "type": "LOOKUP",
                            "sourceFieldPath": "masterInfo.brand.code",
                            "targetEntityType": "BRAND",
                            "targetKeyPattern": "BRAND#{tenantId}#{value}",
                            "required": false
                        }
                    ]
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.product.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.product.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.slices[0].sliceKind shouldBe SliceKind.ENRICHMENT
        contract.slices[0].joins.size shouldBe 1
        contract.slices[0].joins[0].name shouldBe "brand"
        contract.slices[0].joins[0].required shouldBe false
    }

    "DynamoDB - 잘못된 sliceKind → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "sliceKind": "UNKNOWN_KIND",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": ["*"]
                    }
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    // ==================== DynamoDB - MapFields 배열 형태 매핑 테스트 ====================

    "DynamoDB - MapFields 배열 형태 매핑 파싱 (RFC-IMPL-016 신규)" {
        val dataJson = """{
            "entityType": "BRAND",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "SUMMARY",
                    "sliceKind": "REF_INDEX",
                    "buildRules": {
                        "type": "MapFields",
                        "mappings": [
                            {"from": "brandId", "to": "id"},
                            {"from": "name", "to": "name"},
                            {"from": "logoUrl", "to": "logoUrl"}
                        ]
                    }
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.brand.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        val summarySlice = contract.slices.first { it.type == SliceType.SUMMARY }

        summarySlice.buildRules.shouldBeInstanceOf<SliceBuildRules.MapFields>()
        val mappings = (summarySlice.buildRules as SliceBuildRules.MapFields).mappings

        mappings["brandId"] shouldBe "id"
        mappings["name"] shouldBe "name"
        mappings["logoUrl"] shouldBe "logoUrl"
    }

    "DynamoDB - MapFields 객체 형태 매핑 파싱 (기존 방식 호환)" {
        val dataJson = """{
            "entityType": "BRAND",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "SUMMARY",
                    "buildRules": {
                        "type": "MapFields",
                        "mappings": {
                            "brandId": "id",
                            "name": "name"
                        }
                    }
                }
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.brand.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        val summarySlice = contract.slices.first { it.type == SliceType.SUMMARY }

        summarySlice.buildRules.shouldBeInstanceOf<SliceBuildRules.MapFields>()
        val mappings = (summarySlice.buildRules as SliceBuildRules.MapFields).mappings

        mappings["brandId"] shouldBe "id"
        mappings["name"] shouldBe "name"
    }

    // ==================== SliceType 테스트 ====================

    "SliceType - SUMMARY 존재" {
        val summaryType = SliceType.valueOf("SUMMARY")
        summaryType shouldBe SliceType.SUMMARY
    }

    "SliceType - ENRICHED 존재" {
        val enrichedType = SliceType.valueOf("ENRICHED")
        enrichedType shouldBe SliceType.ENRICHED
    }

    "SliceType.fromDbValue - SUMMARY" {
        SliceType.fromDbValue("summary") shouldBe SliceType.SUMMARY
        SliceType.fromDbValue("SUMMARY") shouldBe SliceType.SUMMARY
    }

    "SliceType.fromDbValue - ENRICHED" {
        SliceType.fromDbValue("enriched") shouldBe SliceType.ENRICHED
        SliceType.fromDbValue("ENRICHED") shouldBe SliceType.ENRICHED
    }
})
