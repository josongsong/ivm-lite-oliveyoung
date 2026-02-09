package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.util.concurrent.CompletableFuture

/**
 * DynamoDBContractRegistryAdapter 단위 테스트 (RFC-IMPL Phase B-5)
 *
 * MockK를 사용한 DynamoDB 클라이언트 모킹
 */
class DynamoDBContractRegistryAdapterTest : StringSpec({

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

    "loadChangeSetContract - 성공" {
        val dataJson = """{
            "identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"},
            "payload": {"externalizationPolicy": {"thresholdBytes": 50000}},
            "fanout": {"enabled": true}
        }"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "changeset.v1"
        contract.meta.status shouldBe ContractStatus.ACTIVE
        contract.entityKeyFormat shouldBe "{ENTITY_TYPE}#{tenantId}#{entityId}"
        contract.externalizeThresholdBytes shouldBe 50000
        contract.fanoutEnabled shouldBe true
    }

    "loadJoinSpecContract - 성공" {
        val dataJson = """{
            "constraints": {"maxJoinDepth": 3},
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 5000,
                    "contractRef": {"id": "inverted-index.v1", "version": "1.0.0"}
                }
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadJoinSpecContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "join-spec.v1"
        contract.maxJoinDepth shouldBe 3
        contract.maxFanout shouldBe 5000
        // invertedIndexRef는 deprecated이므로 nullable 체크
        contract.invertedIndexRef?.id shouldBe "inverted-index.v1"
    }

    "loadInvertedIndexContract - 성공" {
        val dataJson = """{
            "keySpec": {
                "pkPattern": "INV#{ref_type}#{ref_value}",
                "skPattern": "TARGET#{target_type}#{target_id}",
                "padWidth": 16,
                "separator": "#"
            },
            "guards": {"maxTargetsPerRef": 100000}
        }"""

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.id shouldBe "inverted-index.v1"
        contract.pkPattern shouldBe "INV#{ref_type}#{ref_value}"
        contract.skPattern shouldBe "TARGET#{target_type}#{target_id}"
        contract.padWidth shouldBe 16
        contract.separator shouldBe "#"
        contract.maxTargetsPerRef shouldBe 100000
    }

    "loadChangeSetContract - 존재하지 않는 계약 → NotFoundError" {
        val mockClient = createMockClient(null)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("not-exists", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "loadChangeSetContract - kind 누락 → ContractError" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            // kind 누락
            "status" to attr("ACTIVE"),
            "data" to attr("{}"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadChangeSetContract - 잘못된 status → ContractError" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("INVALID_STATUS"),
            "data" to attr("{}"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadInvertedIndexContract - keySpec 누락 → ContractError" {
        val dataJson = """{"guards": {"maxTargetsPerRef": 100000}}"""

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadChangeSetContract - 기본값 적용" {
        val dataJson = """{}"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.entityKeyFormat shouldBe "{ENTITY_TYPE}#{tenantId}#{entityId}"
        contract.externalizeThresholdBytes shouldBe 100000
        contract.fanoutEnabled shouldBe false
    }

    // ==================== 엣지케이스 테스트 ====================

    "loadChangeSetContract - malformed JSON → ContractError" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr("{invalid json syntax"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadChangeSetContract - data 누락 → ContractError" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            // data 누락
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadJoinSpecContract - fanout.invertedIndex 누락 → ContractError" {
        val dataJson = """{"constraints": {"maxJoinDepth": 3}}"""

        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadJoinSpecContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadJoinSpecContract - contractRef.id 누락 → ContractError" {
        val dataJson = """{
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 1000,
                    "contractRef": {"version": "1.0.0"}
                }
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadJoinSpecContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadInvertedIndexContract - pkPattern 누락 → ContractError" {
        val dataJson = """{
            "keySpec": {
                "skPattern": "TARGET#{target_type}#{target_id}"
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadInvertedIndexContract - skPattern 누락 → ContractError" {
        val dataJson = """{
            "keySpec": {
                "pkPattern": "INV#{ref_type}#{ref_value}"
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadChangeSetContract - DEPRECATED 상태 contract → 정상 로드" {
        val dataJson = """{}"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("DEPRECATED"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        contract.meta.status shouldBe ContractStatus.DEPRECATED
    }

    "loadJoinSpecContract - constraints 누락 시 기본값 적용" {
        val dataJson = """{
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 1000,
                    "contractRef": {"id": "inv.v1", "version": "1.0.0"}
                }
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadJoinSpecContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        // 기본값 검증 (코드에서 기본값은 1)
        contract.maxJoinDepth shouldBe 1
    }

    "loadInvertedIndexContract - guards 누락 시 기본값 적용" {
        val dataJson = """{
            "keySpec": {
                "pkPattern": "INV#{ref_type}#{ref_value}",
                "skPattern": "TARGET#{target_type}#{target_id}"
            }
        }"""

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value
        // 기본값 검증
        contract.padWidth shouldBe 12
        contract.separator shouldBe "#"
        contract.maxTargetsPerRef shouldBe 500000
    }

    // ==================== Phase C-2: checksum 무결성 검증 ====================

    "checksum 일치 → Ok 반환" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""
        val checksum = Hashing.sha256Tagged(dataJson)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "checksum 불일치 → Err(ContractIntegrityError)" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""
        val wrongChecksum = "sha256:0000000000000000000000000000000000000000000000000000000000000000"

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(wrongChecksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }

    "checksum 필드 누락 → 경고 로그 + Ok (migration 호환)" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            // checksum 필드 누락
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "checksum 형식 - sha256: prefix 없이 순수 hex만 있을 때도 검증" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""
        val checksumHex = Hashing.sha256Hex(dataJson)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksumHex),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "빈 data → 빈 문자열 hash와 비교" {
        val dataJson = ""
        val checksum = Hashing.sha256Tagged(dataJson)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        // 빈 data는 파싱 실패하므로 ContractError (checksum은 통과)
        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractError>()
    }

    "loadJoinSpecContract - checksum 일치 시 정상 로드" {
        val dataJson = """{
            "constraints": {"maxJoinDepth": 3},
            "fanout": {
                "invertedIndex": {
                    "maxFanout": 5000,
                    "contractRef": {"id": "inverted-index.v1", "version": "1.0.0"}
                }
            }
        }"""
        val checksum = Hashing.sha256Tagged(dataJson)

        val responseItem = mapOf(
            "id" to attr("join-spec.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("JOIN_SPEC"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("join-spec.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadJoinSpecContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "loadInvertedIndexContract - checksum 불일치 시 ContractIntegrityError" {
        val dataJson = """{
            "keySpec": {
                "pkPattern": "INV#{ref_type}#{ref_value}",
                "skPattern": "TARGET#{target_type}#{target_id}"
            }
        }"""
        val wrongChecksum = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        val responseItem = mapOf(
            "id" to attr("inverted-index.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("INVERTED_INDEX"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(wrongChecksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("inverted-index.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadInvertedIndexContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }

    // ==================== Phase C-2: 엣지/코너 케이스 (수학적 완결성) ====================

    "checksum 존재 + data null → ContractIntegrityError (데이터 손상)" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            // data 필드 없음
            "checksum" to attr("sha256:abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        val error = (result as Result.Err).error
        error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
        (error as DomainError.ContractIntegrityError).actual shouldBe "<data_missing>"
    }

    "checksum 빈 문자열 → ContractIntegrityError (잘못된 형식)" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(""),  // 빈 문자열
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        val error = (result as Result.Err).error
        error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }

    "checksum 공백만 → ContractIntegrityError (잘못된 형식)" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr("   "),  // 공백만
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }

    "Unicode/특수문자 포함 data → 정상 checksum 검증" {
        val dataJson = """{"identity": {"entityKeyFormat": "한글테스트_émoji_🎉"}}"""
        val checksum = Hashing.sha256Tagged(dataJson)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "대용량 data (100KB) → checksum 검증 정상 동작" {
        val largePayload = "x".repeat(100_000)
        val dataJson = """{"identity": {"entityKeyFormat": "$largePayload"}}"""
        val checksum = Hashing.sha256Tagged(dataJson)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson),
            "checksum" to attr(checksum),
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
    }

    "결정성: 동일 data → 동일 checksum (반복 검증)" {
        val dataJson = """{"identity": {"entityKeyFormat": "{ENTITY_TYPE}#{tenantId}#{entityId}"}}"""

        // 100번 반복해도 동일한 결과
        repeat(100) {
            val hash1 = Hashing.sha256Hex(dataJson)
            val hash2 = Hashing.sha256Hex(dataJson)
            hash1 shouldBe hash2
        }
    }

    "checksum null + data null → Ok (이후 parse에서 에러)" {
        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            // checksum, data 둘 다 없음
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        // checksum 검증은 통과하지만, parse에서 "missing data" 에러
        result.shouldBeInstanceOf<Result.Err>()
        val error = (result as Result.Err).error
        error.shouldBeInstanceOf<DomainError.ContractError>()
        error.message shouldContain "missing data"
    }

    "1비트 변경 → checksum 불일치 (해시 충돌 저항성)" {
        val dataJson1 = """{"identity": {"entityKeyFormat": "test1"}}"""
        val dataJson2 = """{"identity": {"entityKeyFormat": "test2"}}"""  // 1글자 변경
        val checksum1 = Hashing.sha256Tagged(dataJson1)

        val responseItem = mapOf(
            "id" to attr("changeset.v1"),
            "version" to attr("1.0.0"),
            "kind" to attr("CHANGESET"),
            "status" to attr("ACTIVE"),
            "data" to attr(dataJson2),  // 변경된 data
            "checksum" to attr(checksum1),  // 원본 checksum
        )

        val mockClient = createMockClient(responseItem)
        val adapter = DynamoDBContractRegistryAdapter(mockClient, tableName)
        val ref = ContractRef("changeset.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadChangeSetContract(ref)

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.ContractIntegrityError>()
    }
})
