package com.oliveyoung.ivmlite.pkg.contracts

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
 * ViewDefinitionContract 로딩 테스트 (RFC-IMPL Phase D-5)
 *
 * TDD 전수 테스트:
 * 1. 정상 로드 → ViewDefinitionContract 반환
 * 2. 필수 필드 누락 → Err(ContractError)
 * 3. requiredSlices 파싱 (SliceType 리스트)
 * 4. optionalSlices 파싱
 * 5. missingPolicy: FAIL_CLOSED, PARTIAL_ALLOWED
 * 6. partialPolicy.allowed, optionalOnly, responseMeta 파싱
 * 7. fallbackPolicy: NONE, DEFAULT_VALUE
 * 8. ruleSetRef 파싱 (ContractRef)
 */
class ViewDefinitionContractTest : StringSpec({

    // ==================== LocalYamlContractRegistryAdapter 테스트 ====================

    "LocalYaml - 정상 로드 → ViewDefinitionContract 반환" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.meta.id shouldBe "view.product.pdp.v1"
        contract.meta.status shouldBe ContractStatus.ACTIVE
    }

    "LocalYaml - requiredSlices 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.requiredSlices shouldBe listOf(SliceType.CORE)
    }

    "LocalYaml - optionalSlices 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.optionalSlices shouldBe emptyList()
    }

    "LocalYaml - missingPolicy 파싱 검증 (FAIL_CLOSED)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.missingPolicy shouldBe MissingPolicy.FAIL_CLOSED
    }

    "LocalYaml - partialPolicy 전체 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.partialPolicy.allowed shouldBe false
        contract.partialPolicy.optionalOnly shouldBe true
        contract.partialPolicy.responseMeta.includeMissingSlices shouldBe true
        contract.partialPolicy.responseMeta.includeUsedContracts shouldBe false
    }

    "LocalYaml - fallbackPolicy 파싱 검증 (NONE)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.fallbackPolicy shouldBe FallbackPolicy.NONE
    }

    "LocalYaml - ruleSetRef 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.ruleSetRef.id shouldBe "ruleset.core.v1"
        contract.ruleSetRef.version shouldBe SemVer.parse("1.0.0")
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

    "DynamoDB - 정상 로드 → ViewDefinitionContract 반환" {
        val dataJson = """{
            "requiredSlices": ["CORE", "PRICE"],
            "optionalSlices": ["MEDIA"],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {
                "allowed": false,
                "optionalOnly": true,
                "responseMeta": {
                    "includeMissingSlices": true,
                    "includeUsedContracts": false
                }
            },
            "fallbackPolicy": "NONE",
            "ruleSetRef": {
                "id": "ruleset.core.v1",
                "version": "1.0.0"
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("view.product.pdp.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.meta.id shouldBe "view.product.pdp.v1"
        contract.meta.status shouldBe ContractStatus.ACTIVE
    }

    "DynamoDB - 존재하지 않는 계약 → NotFoundError" {
        val mockClient = createMockClient(null)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("not-exists", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "DynamoDB - requiredSlices 파싱 검증" {
        val dataJson = """{
            "requiredSlices": ["CORE", "PRICE", "INVENTORY"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.requiredSlices shouldBe listOf(SliceType.CORE, SliceType.PRICE, SliceType.INVENTORY)
    }

    "DynamoDB - optionalSlices 파싱 검증" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": ["MEDIA", "REVIEW"],
            "missingPolicy": "PARTIAL_ALLOWED",
            "partialPolicy": {"allowed": true, "optionalOnly": false, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": true}},
            "fallbackPolicy": "DEFAULT_VALUE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.optionalSlices shouldBe listOf(SliceType.MEDIA, SliceType.REVIEW)
    }

    "DynamoDB - missingPolicy PARTIAL_ALLOWED 파싱" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "PARTIAL_ALLOWED",
            "partialPolicy": {"allowed": true, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.missingPolicy shouldBe MissingPolicy.PARTIAL_ALLOWED
    }

    "DynamoDB - partialPolicy 전체 필드 파싱" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "PARTIAL_ALLOWED",
            "partialPolicy": {
                "allowed": true,
                "optionalOnly": false,
                "responseMeta": {
                    "includeMissingSlices": false,
                    "includeUsedContracts": true
                }
            },
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.partialPolicy.allowed shouldBe true
        contract.partialPolicy.optionalOnly shouldBe false
        contract.partialPolicy.responseMeta.includeMissingSlices shouldBe false
        contract.partialPolicy.responseMeta.includeUsedContracts shouldBe true
    }

    "DynamoDB - fallbackPolicy DEFAULT_VALUE 파싱" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "DEFAULT_VALUE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.fallbackPolicy shouldBe FallbackPolicy.DEFAULT_VALUE
    }

    "DynamoDB - ruleSetRef 파싱 검증" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.product.v2", "version": "2.1.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.ruleSetRef.id shouldBe "ruleset.product.v2"
        contract.ruleSetRef.version shouldBe SemVer.parse("2.1.0")
    }

    "DynamoDB - requiredSlices 누락 → ContractError" {
        val dataJson = """{
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - partialPolicy 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - ruleSetRef 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE"
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - data 누락 → ContractError" {
        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - malformed JSON → ContractError" {
        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr("{invalid json"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - 알 수 없는 SliceType → ContractError" {
        val dataJson = """{
            "requiredSlices": ["UNKNOWN_SLICE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - 알 수 없는 MissingPolicy → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "UNKNOWN_POLICY",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - 알 수 없는 FallbackPolicy → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "UNKNOWN_FALLBACK",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - DRAFT status → ContractError (ACTIVE만 허용)" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("DRAFT"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - 빈 requiredSlices → 정상 로드 (빈 리스트)" {
        val dataJson = """{
            "requiredSlices": [],
            "optionalSlices": ["CORE", "PRICE"],
            "missingPolicy": "PARTIAL_ALLOWED",
            "partialPolicy": {"allowed": true, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.requiredSlices shouldBe emptyList()
        contract.optionalSlices shouldBe listOf(SliceType.CORE, SliceType.PRICE)
    }

    "DynamoDB - responseMeta 필드 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    // ==================== 엣지/코너케이스 테스트 (수학적 완결성) ====================

    "DynamoDB - 모든 SliceType enum 값 파싱 검증" {
        val dataJson = """{
            "requiredSlices": ["CORE", "PRICE", "INVENTORY", "MEDIA"],
            "optionalSlices": ["CATEGORY", "PROMOTION", "REVIEW", "CUSTOM"],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.requiredSlices shouldBe listOf(SliceType.CORE, SliceType.PRICE, SliceType.INVENTORY, SliceType.MEDIA)
        contract.optionalSlices shouldBe listOf(SliceType.CATEGORY, SliceType.PROMOTION, SliceType.REVIEW, SliceType.CUSTOM)
    }

    "DynamoDB - DEPRECATED status → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("DEPRECATED"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - ARCHIVED status → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ARCHIVED"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - optionalSlices에 잘못된 SliceType → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": ["INVALID_TYPE"],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - partialPolicy.allowed 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - responseMeta.includeMissingSlices 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "DynamoDB - Boolean 조합 전수 테스트 (allowed=true, optionalOnly=false)" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": ["PRICE"],
            "missingPolicy": "PARTIAL_ALLOWED",
            "partialPolicy": {
                "allowed": true,
                "optionalOnly": false,
                "responseMeta": {"includeMissingSlices": false, "includeUsedContracts": true}
            },
            "fallbackPolicy": "DEFAULT_VALUE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("VIEW_DEFINITION"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Ok<*>>()
        val contract = (result as ContractRegistryPort.Result.Ok).value
        contract.partialPolicy.allowed shouldBe true
        contract.partialPolicy.optionalOnly shouldBe false
        contract.partialPolicy.responseMeta.includeMissingSlices shouldBe false
        contract.partialPolicy.responseMeta.includeUsedContracts shouldBe true
        contract.missingPolicy shouldBe MissingPolicy.PARTIAL_ALLOWED
        contract.fallbackPolicy shouldBe FallbackPolicy.DEFAULT_VALUE
    }

    "DynamoDB - kind 필드 누락 → ContractError" {
        val dataJson = """{
            "requiredSlices": ["CORE"],
            "optionalSlices": [],
            "missingPolicy": "FAIL_CLOSED",
            "partialPolicy": {"allowed": false, "optionalOnly": true, "responseMeta": {"includeMissingSlices": true, "includeUsedContracts": false}},
            "fallbackPolicy": "NONE",
            "ruleSetRef": {"id": "ruleset.v1", "version": "1.0.0"}
        }"""

        val responseItem = mapOf(
            "id" to attr("view.v1"),
            "version" to attr("1.0.0"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("view.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadViewDefinitionContract(ref)

        result.shouldBeInstanceOf<ContractRegistryPort.Result.Err>()
        (result as ContractRegistryPort.Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    // ==================== 도메인 모델 불변성 테스트 ====================

    "ViewDefinitionContract - data class 불변성 검증" {
        val meta = ContractMeta("VIEW_DEFINITION", "view.v1", SemVer.parse("1.0.0"), ContractStatus.ACTIVE)
        val contract = ViewDefinitionContract(
            meta = meta,
            requiredSlices = listOf(SliceType.CORE),
            optionalSlices = listOf(SliceType.PRICE),
            missingPolicy = MissingPolicy.FAIL_CLOSED,
            partialPolicy = PartialPolicy(
                allowed = false,
                optionalOnly = true,
                responseMeta = ResponseMeta(includeMissingSlices = true, includeUsedContracts = false),
            ),
            fallbackPolicy = FallbackPolicy.NONE,
            ruleSetRef = ContractRef("ruleset.v1", SemVer.parse("1.0.0")),
        )

        // data class copy로 새 인스턴스 생성 시 원본 불변
        val copied = contract.copy(missingPolicy = MissingPolicy.PARTIAL_ALLOWED)
        contract.missingPolicy shouldBe MissingPolicy.FAIL_CLOSED
        copied.missingPolicy shouldBe MissingPolicy.PARTIAL_ALLOWED
    }

    "PartialPolicy - 값 객체 동등성 검증" {
        val policy1 = PartialPolicy(
            allowed = true,
            optionalOnly = false,
            responseMeta = ResponseMeta(includeMissingSlices = true, includeUsedContracts = true),
        )
        val policy2 = PartialPolicy(
            allowed = true,
            optionalOnly = false,
            responseMeta = ResponseMeta(includeMissingSlices = true, includeUsedContracts = true),
        )

        // 값이 같으면 동등
        (policy1 == policy2) shouldBe true
        policy1.hashCode() shouldBe policy2.hashCode()
    }
})
