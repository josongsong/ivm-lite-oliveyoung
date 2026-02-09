package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
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
 * RuleSetContract 로딩 테스트 (RFC-IMPL Phase D-2)
 *
 * TDD 전수 테스트:
 * 1. 정상 로드 → RuleSetContract 반환
 * 2. 파일 없음 → Err(NotFoundError)
 * 3. 필수 필드 누락 (entityType, slices) → Err(ContractError)
 * 4. impactMap 파싱 (sliceType → paths 매핑)
 * 5. joins 파싱 (JoinSpec 리스트)
 * 6. slices 파싱 (SliceDefinition 리스트)
 * 7. sliceBuildRules: MapFields, PassThrough 타입 구분
 * 8. status 검증 (ACTIVE만 허용)
 */
class RuleSetContractTest : StringSpec({

    // ==================== LocalYamlContractRegistryAdapter 테스트 ====================

    "LocalYaml - 정상 로드 → RuleSetContract 반환" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "ruleset.core.v1"
        contract.meta.status shouldBe ContractStatus.ACTIVE
        contract.entityType shouldBe "PRODUCT"
    }

    "LocalYaml - impactMap 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.impactMap[SliceType.CORE] shouldBe listOf("/title", "/brand", "/price")
    }

    "LocalYaml - slices 파싱 검증 (PassThrough)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.slices.isNotEmpty() shouldBe true
        val coreSlice = contract.slices.first { it.type == SliceType.CORE }
        coreSlice.buildRules.shouldBeInstanceOf<SliceBuildRules.PassThrough>()
        (coreSlice.buildRules as SliceBuildRules.PassThrough).fields shouldBe listOf("*")
    }

    // ==================== DynamoDBContractRegistryAdapter 테스트 ====================

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

    "DynamoDB - 정상 로드 → RuleSetContract 반환" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": ["/title", "/brand"],
                "PRICE": ["/price", "/salePrice"]
            },
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
            "id" to attr("ruleset.core.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "ruleset.core.v1"
        contract.meta.status shouldBe ContractStatus.ACTIVE
        contract.entityType shouldBe "PRODUCT"
    }

    "DynamoDB - 존재하지 않는 계약 → NotFoundError" {
        val mockClient = createMockClient(null)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("not-exists", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "DynamoDB - entityType 누락 → ContractError" {
        val dataJson = """{
            "impactMap": {},
            "joins": [],
            "slices": []
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

    "DynamoDB - slices 누락 → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": []
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

    "DynamoDB - impactMap 파싱 검증" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": ["/title", "/brand"],
                "PRICE": ["/price"]
            },
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
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
        contract.impactMap[SliceType.CORE] shouldBe listOf("/title", "/brand")
        contract.impactMap[SliceType.PRICE] shouldBe listOf("/price")
    }

    "DynamoDB - joins 파싱 검증" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [
                {
                    "sourceSlice": "CORE",
                    "targetEntity": "CATEGORY",
                    "joinPath": "/categoryId",
                    "cardinality": "MANY_TO_ONE"
                }
            ],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
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
        contract.joins.size shouldBe 1
        contract.joins[0].sourceSlice shouldBe SliceType.CORE
        contract.joins[0].targetEntity shouldBe "CATEGORY"
        contract.joins[0].joinPath shouldBe "/categoryId"
        contract.joins[0].cardinality shouldBe JoinCardinality.MANY_TO_ONE
    }

    "DynamoDB - sliceBuildRules MapFields 타입 파싱" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "PRICE",
                    "buildRules": {
                        "type": "MapFields",
                        "mappings": {
                            "originalPrice": "price",
                            "discountedPrice": "salePrice"
                        }
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
        val priceSlice = contract.slices.first { it.type == SliceType.PRICE }
        priceSlice.buildRules.shouldBeInstanceOf<SliceBuildRules.MapFields>()
        val mappings = (priceSlice.buildRules as SliceBuildRules.MapFields).mappings
        mappings["originalPrice"] shouldBe "price"
        mappings["discountedPrice"] shouldBe "salePrice"
    }

    "DynamoDB - sliceBuildRules PassThrough 타입 파싱" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": ["title", "brand", "description"]
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
        val coreSlice = contract.slices.first { it.type == SliceType.CORE }
        coreSlice.buildRules.shouldBeInstanceOf<SliceBuildRules.PassThrough>()
        (coreSlice.buildRules as SliceBuildRules.PassThrough).fields shouldBe listOf("title", "brand", "description")
    }

    "DynamoDB - DRAFT status → ContractError (ACTIVE만 허용)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("DRAFT"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - DEPRECATED status → ContractError (ACTIVE만 허용)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("DEPRECATED"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - data 누락 → ContractError" {
        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            // data 누락
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - malformed JSON → ContractError" {
        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr("{invalid json"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - 알 수 없는 buildRules type → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "UnknownType",
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

    "DynamoDB - 빈 slices 배열 → 정상 로드 (빈 리스트)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": []
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
        contract.slices shouldBe emptyList()
    }

    "DynamoDB - impactMap 빈 객체 → 정상 로드 (빈 맵)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
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
        contract.impactMap shouldBe emptyMap()
    }

    // ==================== RFC-IMPL-013: indexes 파싱 테스트 ====================

    "DynamoDB - indexes 파싱 검증 (type, selector)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand", "selector": "$.brand"},
                {"type": "category", "selector": "$.categoryId"}
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
        contract.indexes.size shouldBe 2
        contract.indexes[0].type shouldBe "brand"
        contract.indexes[0].selector shouldBe "$.brand"
        contract.indexes[1].type shouldBe "category"
        contract.indexes[1].selector shouldBe "$.categoryId"
    }

    "DynamoDB - indexes.references 파싱 검증 (역방향 인덱스용)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand", "selector": "$.brand", "references": "BRAND"},
                {"type": "tag", "selector": "$.tags[*]"}
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
        contract.indexes.size shouldBe 2
        
        // references가 있는 인덱스 → 역방향 인덱스 자동 생성
        contract.indexes[0].references shouldBe "BRAND"
        
        // references가 없는 인덱스 → 정방향만 생성
        contract.indexes[1].references shouldBe null
    }

    "DynamoDB - indexes.maxFanout 파싱 검증 (기본값 포함)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand", "selector": "$.brand", "references": "BRAND", "maxFanout": 5000},
                {"type": "category", "selector": "$.categoryId", "references": "CATEGORY"}
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
        contract.indexes.size shouldBe 2
        
        // 명시적 maxFanout
        contract.indexes[0].maxFanout shouldBe 5000
        
        // 기본값 (10000)
        contract.indexes[1].maxFanout shouldBe 10000
    }

    "DynamoDB - indexes 배열이 잘못된 형식 (객체가 아닌 문자열) → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": "invalid"  // 배열이 아닌 문자열
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

    "DynamoDB - indexes의 필수 필드 누락 (type 없음) → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"selector": "$.brand"}  // type 누락
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

    "DynamoDB - indexes의 필수 필드 누락 (selector 없음) → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand"}  // selector 누락
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

    "DynamoDB - indexes.maxFanout이 음수 → 파싱은 성공하지만 런타임 검증 필요" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand", "selector": "$.brand", "maxFanout": -1}
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

        // 파싱은 성공 (음수도 int로 파싱 가능)
        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.indexes.size shouldBe 1
        // 음수 값이 그대로 저장됨 (런타임 검증은 FanoutConfig에서 수행)
        contract.indexes[0].maxFanout shouldBe -1
    }

    "DynamoDB - indexes 빈 배열 → 정상 로드 (빈 리스트)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": []
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
        contract.indexes shouldBe emptyList()
    }

    "DynamoDB - indexes 필드 없음 → 기본값 (빈 리스트)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
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
        contract.indexes shouldBe emptyList()
    }

    "LocalYaml - indexes.references 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        
        // ruleset.v1.yaml에 정의된 indexes 확인
        contract.indexes.isNotEmpty() shouldBe true
        
        // brand 인덱스: references=BRAND
        val brandIndex = contract.indexes.firstOrNull { it.type == "brand" }
        brandIndex shouldNotBe null
        brandIndex!!.references shouldBe "BRAND"
        brandIndex.maxFanout shouldBe 10000
        
        // category 인덱스: references=CATEGORY
        val categoryIndex = contract.indexes.firstOrNull { it.type == "category" }
        categoryIndex shouldNotBe null
        categoryIndex!!.references shouldBe "CATEGORY"
        categoryIndex.maxFanout shouldBe 50000
        
        // tag 인덱스: references=null (검색용만)
        val tagIndex = contract.indexes.firstOrNull { it.type == "tag" }
        tagIndex shouldNotBe null
        tagIndex!!.references shouldBe null
    }

    "DynamoDB - 알 수 없는 SliceType → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "UNKNOWN_SLICE_TYPE",
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
})
