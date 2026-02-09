package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.adapters.InMemoryContractCache
import com.oliveyoung.ivmlite.shared.config.CacheConfig
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.util.concurrent.CompletableFuture

/**
 * RuleSetContract 모듈 간 연동 테스트 (RFC-IMPL Phase D-2)
 *
 * 검증 항목:
 * 1. ViewDefinitionContract → RuleSetContract 참조 무결성
 * 2. GatedContractRegistryAdapter에서 RuleSet 상태 검증
 * 3. 캐시 연동 (InMemoryContractCache)
 * 4. LocalYaml vs DynamoDB 일관성
 */
class RuleSetIntegrationTest : StringSpec({

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

    // ==================== ViewDefinitionContract → RuleSetContract 연동 ====================

    "ViewDefinition에서 ruleSetRef로 RuleSet 로드 → 정상 연동" {
        val viewDataJson = """{
            "requiredSlices": ["CORE", "PRICE"],
            "optionalSlices": ["INVENTORY"],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {
                "allowed": false,
                "optionalOnly": true,
                "responseMeta": {
                    "includeMissingSlices": true,
                    "includeUsedContracts": true
                }
            },
            "fallbackPolicy": "NONE",
            "ruleSetRef": {
                "id": "ruleset.core.v1",
                "version": "1.0.0"
            }
        }"""

        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": ["/title", "/brand"],
                "PRICE": ["/price"]
            },
            "joins": [],
            "slices": [
                {"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}},
                {"type": "PRICE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}
            ]
        }"""

        val mockClient = mockk<DynamoDbAsyncClient>()

        // ViewDefinition 로드
        every { mockClient.getItem(match<GetItemRequest> { it.key()["id"]?.s() == "view.product.v1" }) } returns
            CompletableFuture.completedFuture(
                GetItemResponse.builder()
                    .item(mapOf(
                        "id" to attr("view.product.v1"),
                        "version" to attr("1.0.0"),
                        "kind" to attr("VIEW_DEFINITION"),
                        "status" to attr("ACTIVE"),
                        "data" to attr(viewDataJson),
                    ))
                    .build()
            )

        // RuleSet 로드
        every { mockClient.getItem(match<GetItemRequest> { it.key()["id"]?.s() == "ruleset.core.v1" }) } returns
            CompletableFuture.completedFuture(
                GetItemResponse.builder()
                    .item(mapOf(
                        "id" to attr("ruleset.core.v1"),
                        "version" to attr("1.0.0"),
                        "kind" to attr("RULESET"),
                        "status" to attr("ACTIVE"),
                        "data" to attr(ruleSetDataJson),
                    ))
                    .build()
            )

        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)

        // 1. ViewDefinition 로드
        val viewRef = ContractRef("view.product.v1", SemVer.parse("1.0.0"))
        val viewResult = adapter.loadViewDefinitionContract(viewRef)
        viewResult.shouldBeInstanceOf<Result.Ok<*>>()
        val viewContract = (viewResult as Result.Ok).value

        // 2. ViewDefinition의 ruleSetRef로 RuleSet 로드
        val ruleSetResult = adapter.loadRuleSetContract(viewContract.ruleSetRef)
        ruleSetResult.shouldBeInstanceOf<Result.Ok<*>>()
        val ruleSetContract = (ruleSetResult as Result.Ok).value

        // 3. 참조 무결성 검증
        ruleSetContract.meta.id shouldBe "ruleset.core.v1"
        ruleSetContract.entityType shouldBe "PRODUCT"
        ruleSetContract.impactMap[SliceType.CORE] shouldBe listOf("/title", "/brand")
    }

    "ViewDefinition의 ruleSetRef가 존재하지 않는 RuleSet 참조 → NotFoundError" {
        val viewDataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {
                "allowed": false,
                "optionalOnly": true,
                "responseMeta": {
                    "includeMissingSlices": true,
                    "includeUsedContracts": true
                }
            },
            "fallbackPolicy": "NONE",
            "ruleSetRef": {
                "id": "ruleset.nonexistent.v1",
                "version": "1.0.0"
            }
        }"""

        val mockClient = mockk<DynamoDbAsyncClient>()

        // ViewDefinition은 존재
        every { mockClient.getItem(match<GetItemRequest> { it.key()["id"]?.s() == "view.product.v1" }) } returns
            CompletableFuture.completedFuture(
                GetItemResponse.builder()
                    .item(mapOf(
                        "id" to attr("view.product.v1"),
                        "version" to attr("1.0.0"),
                        "kind" to attr("VIEW_DEFINITION"),
                        "status" to attr("ACTIVE"),
                        "data" to attr(viewDataJson),
                    ))
                    .build()
            )

        // RuleSet은 없음
        every { mockClient.getItem(match<GetItemRequest> { it.key()["id"]?.s() == "ruleset.nonexistent.v1" }) } returns
            CompletableFuture.completedFuture(
                GetItemResponse.builder()
                    .item(emptyMap())
                    .build()
            )

        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)

        val viewRef = ContractRef("view.product.v1", SemVer.parse("1.0.0"))
        val viewResult = adapter.loadViewDefinitionContract(viewRef)
        viewResult.shouldBeInstanceOf<Result.Ok<*>>()
        val viewContract = (viewResult as Result.Ok).value

        // RuleSet 로드 실패
        val ruleSetResult = adapter.loadRuleSetContract(viewContract.ruleSetRef)
        ruleSetResult.shouldBeInstanceOf<Result.Err>()
        (ruleSetResult as Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    // ==================== GatedContractRegistryAdapter 연동 ====================

    "GatedAdapter - RuleSet ACTIVE → 정상 로드" {
        val ruleSetDataJson = """{
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
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val delegateAdapter = DynamoDBContractRegistryAdapter(mockClient, tableName)

        // ACTIVE, DEPRECATED 허용하는 gate
        val statusGate = DefaultContractStatusGate
        val gatedAdapter = GatedContractRegistryAdapter(delegateAdapter, statusGate)

        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = gatedAdapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "GatedAdapter - RuleSet DRAFT → ContractStatusError" {
        val ruleSetDataJson = """{
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
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val delegateAdapter = DynamoDBContractRegistryAdapter(mockClient, tableName)

        // ACTIVE만 허용하는 gate
        val statusGate = object : ContractStatusGate {
            override fun check(contractId: String, status: ContractStatus): ContractStatusGate.GateResult {
                return if (status == ContractStatus.ACTIVE) {
                    ContractStatusGate.GateResult.Ok
                } else {
                    ContractStatusGate.GateResult.Err(
                        DomainError.ContractStatusError(contractId, status)
                    )
                }
            }
        }
        val gatedAdapter = GatedContractRegistryAdapter(delegateAdapter, statusGate)

        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = gatedAdapter.loadRuleSetContract(ref)

        // DynamoDB adapter에서 ACTIVE만 허용하므로 이미 ContractError
        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    // ==================== 캐시 연동 ====================

    "캐시 연동 - RuleSet 두 번 로드 → 두 번째는 캐시 hit" {
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {"CORE": ["/title"]},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val cache = InMemoryContractCache(CacheConfig(maxSize = 100))
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)

        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        // 첫 번째 로드 (캐시 miss)
        val result1 = adapter.loadRuleSetContract(ref)
        result1.shouldBeInstanceOf<Result.Ok<*>>()
        cache.stats().misses shouldBe 1
        cache.stats().hits shouldBe 0

        // 두 번째 로드 (캐시 hit)
        val result2 = adapter.loadRuleSetContract(ref)
        result2.shouldBeInstanceOf<Result.Ok<*>>()
        cache.stats().hits shouldBe 1

        // 동일한 객체 반환 확인
        val contract1 = (result1 as Result.Ok).value
        val contract2 = (result2 as Result.Ok).value
        contract1.meta.id shouldBe contract2.meta.id
        contract1.entityType shouldBe contract2.entityType
    }

    "캐시 연동 - RuleSet 로드 실패 → 캐시에 저장하지 않음 (negative caching 금지)" {
        val mockClient = createMockClient(null) // NotFound
        val cache = InMemoryContractCache(CacheConfig(maxSize = 100))
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName, cache)

        val ref = ContractRef("ruleset.nonexistent.v1", SemVer.parse("1.0.0"))

        // 로드 실패
        val result = adapter.loadRuleSetContract(ref)
        result.shouldBeInstanceOf<Result.Err>()

        // 캐시에 저장되지 않음
        cache.size() shouldBe 0
    }

    // ==================== LocalYaml vs DynamoDB 일관성 ====================

    "LocalYaml vs DynamoDB - 동일한 RuleSet 로드 → 동일한 결과" {
        // LocalYaml
        val localAdapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))
        val localResult = localAdapter.loadRuleSetContract(ref)
        localResult.shouldBeInstanceOf<Result.Ok<*>>()
        val localContract = (localResult as Result.Ok).value

        // DynamoDB (동일한 YAML 내용을 JSON으로 변환)
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": ["/title", "/brand", "/price"],
                "PRICE": ["/price", "/salePrice", "/discount"],
                "INVENTORY": ["/stock", "/availability"],
                "MEDIA": ["/images", "/videos"],
                "CATEGORY": ["/categoryId", "/categoryPath"],
                "PROMOTION": ["/promotionIds", "/couponIds"],
                "REVIEW": ["/reviewCount", "/averageRating"]
            },
            "joins": [],
            "slices": [
                {"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}},
                {"type": "PRICE", "buildRules": {"type": "PassThrough", "fields": ["price", "salePrice", "discount"]}},
                {"type": "INVENTORY", "buildRules": {"type": "PassThrough", "fields": ["stock", "availability"]}},
                {"type": "MEDIA", "buildRules": {"type": "PassThrough", "fields": ["images", "videos"]}},
                {"type": "CATEGORY", "buildRules": {"type": "PassThrough", "fields": ["categoryId", "categoryPath"]}}
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.core.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val dynamoAdapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val dynamoResult = dynamoAdapter.loadRuleSetContract(ref)
        dynamoResult.shouldBeInstanceOf<Result.Ok<*>>()
        val dynamoContract = (dynamoResult as Result.Ok).value

        // 동일성 검증
        localContract.meta.id shouldBe dynamoContract.meta.id
        localContract.entityType shouldBe dynamoContract.entityType
        localContract.impactMap.keys shouldBe dynamoContract.impactMap.keys
        localContract.slices.size shouldBe dynamoContract.slices.size
    }

    // ==================== 결정성 검증 ====================

    "결정성 - 동일한 ref로 100번 로드 → 동일한 결과" {
        val localAdapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val results = (1..100).map {
            localAdapter.loadRuleSetContract(ref)
        }

        // 모두 성공
        results.forEach { it.shouldBeInstanceOf<Result.Ok<*>>() }

        // 모든 결과가 동일
        val first = (results[0] as Result.Ok).value
        results.forEach { result ->
            val contract = (result as Result.Ok).value
            contract.meta.id shouldBe first.meta.id
            contract.entityType shouldBe first.entityType
            contract.impactMap.size shouldBe first.impactMap.size
            contract.slices.size shouldBe first.slices.size
        }
    }

    // ==================== RFC-IMPL-010 D-9: indexes 파싱 테스트 (GAP-C 해결) ====================

    "LocalYaml - RuleSet indexes 로드 → 정상 파싱" {
        val localAdapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))
        val result = localAdapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // indexes 필드가 로드됨
        contract.indexes.size shouldBe 3
        contract.indexes[0].type shouldBe "brand"
        contract.indexes[0].selector shouldBe "$.brand"
        contract.indexes[1].type shouldBe "category"
        contract.indexes[1].selector shouldBe "$.categoryId"
        contract.indexes[2].type shouldBe "tag"
        contract.indexes[2].selector shouldBe "$.tags[*]"
    }

    "DynamoDB - indexes 정상 파싱" {
        val ruleSetDataJson = """{
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
            "data" to attr(ruleSetDataJson),
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
    }

    "DynamoDB - indexes 없으면 빈 리스트" {
        val ruleSetDataJson = """{
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
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.indexes.size shouldBe 0
    }

    "DynamoDB - index type 누락 → ContractError" {
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"selector": "$.brand"}
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - index selector 누락 → ContractError" {
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand"}
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - index selector가 $ 없음 → ContractError" {
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": [
                {"type": "brand", "selector": "brand"}
            ]
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - indexes가 배열이 아닌 경우 → ContractError (잘못된 형식)" {
        val ruleSetDataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [{"type": "CORE", "buildRules": {"type": "PassThrough", "fields": ["*"]}}],
            "indexes": "not-an-array"
        }"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(ruleSetDataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))
        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }
})
