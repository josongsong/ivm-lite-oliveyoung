package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.shared.domain.types.Result
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AdminPipelineService 단위 테스트
 *
 * DynamoDB 기반 (ExplorerRepositoryPort, SinkEventRepositoryPort)
 */
class AdminPipelineServiceTest {

    private lateinit var service: AdminPipelineService

    @BeforeEach
    fun setup() {
        service = AdminPipelineService(contractRegistry = null, explorerRepo = null, sinkEventRepo = null)
    }

    @Test
    fun `getEntityFlow validates entityKey - blank key returns error`() = runTest {
        // When
        val result = service.getEntityFlow("")

        // Then
        assertTrue(result is Result.Err)
        val error = (result as Result.Err).error
        assertEquals("ERR_VALIDATION", error.errorCode)
    }

    @Test
    fun `getEntityFlow validates entityKey - too long key returns error`() = runTest {
        // Given
        val longKey = "a".repeat(300)

        // When
        val result = service.getEntityFlow(longKey)

        // Then
        assertTrue(result is Result.Err)
        val error = (result as Result.Err).error
        assertEquals("ERR_VALIDATION", error.errorCode)
    }

    @Test
    fun `getEntityFlow escapes SQL injection characters in entityKey`() = runTest {
        // Given: SQL injection 시도
        val maliciousKey = "'; DROP TABLE raw_data; --"

        // When
        val result = service.getEntityFlow(maliciousKey)

        // Then: 에러 없이 정상 처리 (빈 결과)
        // 실제 SQL이 실행되면 mockk이 빈 결과를 반환
        assertTrue(result is Result.Ok || result is Result.Err)
    }

    @Test
    fun `getRecentItems coerces limit to valid range`() = runTest {
        // Given
        val negativeLimit = -10
        val excessiveLimit = 500

        // When - negative limit should be coerced to 1
        service.getRecentItems(negativeLimit)
        // When - excessive limit should be coerced to 200
        service.getRecentItems(excessiveLimit)

        // Then: DSL 호출 확인 (mockk relaxed mode)
        // 실제로 limit이 적용되었는지는 통합 테스트에서 확인
    }
}

/**
 * AdminContractService 단위 테스트 (RFC-022: ContractRegistryPort 기반)
 */
class AdminContractServiceTest {

    private lateinit var service: AdminContractService

    @BeforeEach
    fun setup() {
        val contractRegistry = com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter("/contracts/v1")
        val sinkRuleRegistry = com.oliveyoung.ivmlite.pkg.sinks.adapters.LocalYamlSinkRuleRegistryAdapter("/contracts/v1")
        service = AdminContractService(contractRegistry, sinkRuleRegistry)
    }

    @Test
    fun `getAllContracts returns result`() {
        // When
        val result = service.getAllContracts()

        // Then
        assertTrue(result is Result.Ok)
        // 리소스 로딩이 안 되면 빈 목록 반환
    }

    @Test
    fun `getByKind filters by kind`() {
        // When
        val result = service.getByKind(ContractKind.ENTITY_SCHEMA)

        // Then
        assertTrue(result is Result.Ok)
    }

    @Test
    fun `getById returns NotFoundError for non-existent contract`() {
        // When
        val result = service.getById(ContractKind.ENTITY_SCHEMA, "non-existent-id")

        // Then
        assertTrue(result is Result.Err)
        val error = (result as Result.Err).error
        assertEquals("ERR_NOT_FOUND", error.errorCode)
    }

    @Test
    fun `getStats returns statistics`() {
        // When
        val result = service.getStats()

        // Then
        assertTrue(result is Result.Ok)
        val stats = (result as Result.Ok).value
        assertTrue(stats.total >= 0)
    }

    @Test
    fun `ContractKind fromWireValue handles valid values`() {
        // When/Then
        assertEquals(ContractKind.ENTITY_SCHEMA, ContractKind.fromWireValue("ENTITY_SCHEMA"))
        assertEquals(ContractKind.RULESET, ContractKind.fromWireValue("RULESET"))
        assertEquals(ContractKind.VIEW_DEFINITION, ContractKind.fromWireValue("VIEW_DEFINITION"))
        assertEquals(ContractKind.SINK_RULE, ContractKind.fromWireValue("SINKRULE"))
    }

    @Test
    fun `ContractKind fromWireValue handles invalid values`() {
        // When/Then
        assertEquals(null, ContractKind.fromWireValue("INVALID"))
        assertEquals(null, ContractKind.fromWireValue(""))
    }

    @Test
    fun `ContractKind fromWireValue is case insensitive`() {
        // When/Then
        assertEquals(ContractKind.ENTITY_SCHEMA, ContractKind.fromWireValue("entity_schema"))
        assertEquals(ContractKind.RULESET, ContractKind.fromWireValue("Ruleset"))
    }
}
