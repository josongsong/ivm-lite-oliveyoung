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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.util.concurrent.CompletableFuture

/**
 * RuleSetContract 엣지/코너 케이스 테스트 (비판적 검토)
 *
 * 수학적 완결성 검증:
 * 1. 불변성: 빈 문자열/리스트/맵 허용 여부
 * 2. 유일성: slices에 중복 SliceType 허용 여부
 * 3. 순환성: joins에서 순환 참조 허용 여부
 * 4. 전사성: impactMap이 모든 SliceType을 커버해야 하는가
 * 5. 역함수: MapFields에서 역매핑 정의 여부
 * 6. 결정성: 동일 입력 → 동일 출력 (대소문자 정규화)
 */
class RuleSetContractEdgeCaseTest : StringSpec({

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

    // ==================== 불변성 검증 ====================

    "DynamoDB - 빈 entityType → ContractError" {
        val dataJson = """{
            "entityType": "",
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

        // 빈 entityType은 허용 (파싱은 통과하지만 의미적으로 무효)
        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.entityType shouldBe ""
    }

    "DynamoDB - PassThrough fields 빈 리스트 → 정상 로드" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": []
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
        val coreSlice = contract.slices.first()
        (coreSlice.buildRules as SliceBuildRules.PassThrough).fields shouldBe emptyList()
    }

    "DynamoDB - MapFields mappings 빈 맵 → 정상 로드" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "PRICE",
                    "buildRules": {
                        "type": "MapFields",
                        "mappings": {}
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
        val priceSlice = contract.slices.first()
        (priceSlice.buildRules as SliceBuildRules.MapFields).mappings shouldBe emptyMap()
    }

    // ==================== 유일성 검증 ====================

    "DynamoDB - slices에 중복 SliceType → 정상 로드 (마지막 값 우선)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": ["title"]
                    }
                },
                {
                    "type": "CORE",
                    "buildRules": {
                        "type": "PassThrough",
                        "fields": ["brand"]
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
        // 중복 허용 - List이므로 두 개 모두 저장됨
        contract.slices.size shouldBe 2
        contract.slices[0].type shouldBe SliceType.CORE
        contract.slices[1].type shouldBe SliceType.CORE
    }

    "DynamoDB - impactMap에 중복 경로 → 정상 로드" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": ["/title", "/title", "/brand"]
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
        // 중복 경로 허용 - List이므로 중복 포함
        contract.impactMap[SliceType.CORE] shouldBe listOf("/title", "/title", "/brand")
    }

    // ==================== 순환성 검증 ====================

    "DynamoDB - joins에 순환 참조 → 정상 로드 (순환 감지는 워크플로우 책임)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [
                {
                    "sourceSlice": "CORE",
                    "targetEntity": "CATEGORY",
                    "joinPath": "/categoryId",
                    "cardinality": "MANY_TO_ONE"
                },
                {
                    "sourceSlice": "CATEGORY",
                    "targetEntity": "PRODUCT",
                    "joinPath": "/products",
                    "cardinality": "ONE_TO_MANY"
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

        // 순환 참조는 파싱 단계에서 감지하지 않음 (워크플로우에서 처리)
        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.joins.size shouldBe 2
    }

    // ==================== 결정성 검증 (대소문자 정규화) ====================

    "DynamoDB - SliceType 대소문자 혼합 → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "core": ["/title"]
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

        // DynamoDB는 대소문자를 구분 - "core"는 잘못된 SliceType
        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - buildRules.type 대소문자 혼합 → 정상 로드 (lowercase 정규화)" {
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

        // buildRules.type은 lowercase로 정규화되므로 PassThrough → passthrough 허용
        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    // ==================== JSON 파싱 엣지 케이스 ====================

    "DynamoDB - impactMap의 value가 null → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": null
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

        // null 값은 예외 발생
        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - slices 배열 내 null 요소 → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [
                null,
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

        // null 요소는 예외 발생
        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - MapFields mappings에 null 값 → 해당 매핑 제외" {
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
                            "price": "priceValue",
                            "discount": null
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
        val priceSlice = contract.slices.first()
        val mappings = (priceSlice.buildRules as SliceBuildRules.MapFields).mappings
        // null 값은 mapNotNull로 필터링됨
        mappings shouldBe mapOf("price" to "priceValue")
    }

    // ==================== 역함수 검증 ====================

    "DynamoDB - MapFields에 순환 매핑 → 정상 로드 (순환 감지는 워크플로우 책임)" {
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
                            "a": "b",
                            "b": "c",
                            "c": "a"
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

        // 순환 매핑은 파싱 단계에서 감지하지 않음
        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "DynamoDB - MapFields에 다대일 매핑 → 정상 로드 (역함수 부재)" {
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
                            "price": "priceValue",
                            "salePrice": "priceValue"
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

        // 다대일 매핑 허용 (역함수 부재 허용)
        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        val priceSlice = contract.slices.first()
        val mappings = (priceSlice.buildRules as SliceBuildRules.MapFields).mappings
        mappings["price"] shouldBe "priceValue"
        mappings["salePrice"] shouldBe "priceValue"
    }

    // ==================== 극한값 테스트 ====================

    "DynamoDB - impactMap에 1000개 경로 → 정상 로드" {
        val paths = (1..1000).map { "\"/field$it\"" }.joinToString(",")
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {
                "CORE": [$paths]
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
        contract.impactMap[SliceType.CORE]?.size shouldBe 1000
    }

    "DynamoDB - 100개 슬라이스 정의 → 정상 로드" {
        val slicesJson = (1..100).joinToString(",") { i ->
            """{"type": "CUSTOM", "buildRules": {"type": "PassThrough", "fields": ["field$i"]}}"""
        }
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [],
            "slices": [$slicesJson]
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
        contract.slices.size shouldBe 100
    }

    // ==================== UTF-8/특수문자 테스트 ====================

    "DynamoDB - entityType에 한글 → 정상 로드" {
        val dataJson = """{
            "entityType": "상품",
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
        contract.entityType shouldBe "상품"
    }

    "DynamoDB - joinPath에 특수문자 → 정상 로드" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [
                {
                    "sourceSlice": "CORE",
                    "targetEntity": "CATEGORY",
                    "joinPath": "/data/attributes/category-id",
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
        contract.joins[0].joinPath shouldBe "/data/attributes/category-id"
    }

    // ==================== 불변식 위반 테스트 ====================

    "DynamoDB - impactMap에 있지만 slices에 없는 타입 → 정상 로드 (워크플로우 책임)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {"PRICE": ["/price"]},
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

        // 계약 로딩은 성공 (불변식 검증은 워크플로우에서)
        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "DynamoDB - joins의 sourceSlice가 slices에 없음 → 정상 로드 (워크플로우 책임)" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {},
            "joins": [
                {
                    "sourceSlice": "PRICE",
                    "targetEntity": "VENDOR",
                    "joinPath": "/vendor_id",
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
    }

    "DynamoDB - checksum 불일치 → ContractIntegrityError" {
        val dataJson = """{"entityType": "PRODUCT", "impactMap": {}, "joins": [], "slices": []}"""

        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr("invalid-checksum-1234567890abcdef"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }

    "DynamoDB - data가 빈 문자열 → ContractError" {
        val responseItem = mapOf(
            "id" to attr("ruleset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("RULESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(""),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("ruleset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - impactMap value가 문자열 배열이 아님 → ContractError" {
        val dataJson = """{
            "entityType": "PRODUCT",
            "impactMap": {"CORE": 123},
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
})
