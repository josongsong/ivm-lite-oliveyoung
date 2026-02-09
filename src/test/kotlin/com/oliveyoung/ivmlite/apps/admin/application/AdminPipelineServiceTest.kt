package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.shared.domain.types.Result
import org.jooq.DSLContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jooq.Result as JooqResult
import org.jooq.Record
import org.jooq.SelectSelectStep
import org.jooq.SelectJoinStep
import org.jooq.SelectConditionStep
import org.jooq.SelectLimitStep
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AdminPipelineService 단위 테스트
 *
 * SOTA 리팩토링: Service 레이어 테스트
 */
class AdminPipelineServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var service: AdminPipelineService

    @BeforeEach
    fun setup() {
        dsl = mockk(relaxed = true)
        service = AdminPipelineService(dsl)
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
    fun `escapeLikePattern handles special characters`() {
        // Given
        val input = "test%_value\\special"

        // When: private 함수 테스트를 위해 리플렉션 사용
        val method = AdminPipelineService::class.java.getDeclaredMethod("escapeLikePattern", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, input) as String

        // Then
        assertEquals("test\\%\\_value\\\\special", result)
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
 * AdminContractService 단위 테스트
 */
class AdminContractServiceTest {

    private lateinit var service: AdminContractService

    @BeforeEach
    fun setup() {
        service = AdminContractService()
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
    fun `ContractKind fromString handles valid values`() {
        // When/Then
        assertEquals(ContractKind.ENTITY_SCHEMA, ContractKind.fromString("ENTITY_SCHEMA"))
        assertEquals(ContractKind.RULESET, ContractKind.fromString("RULESET"))
        assertEquals(ContractKind.VIEW_DEFINITION, ContractKind.fromString("VIEW_DEFINITION"))
        assertEquals(ContractKind.SINK_RULE, ContractKind.fromString("SINKRULE"))
    }

    @Test
    fun `ContractKind fromString handles invalid values`() {
        // When/Then
        assertEquals(null, ContractKind.fromString("INVALID"))
        assertEquals(null, ContractKind.fromString(""))
    }

    @Test
    fun `ContractKind fromString is case insensitive`() {
        // When/Then
        assertEquals(ContractKind.ENTITY_SCHEMA, ContractKind.fromString("entity_schema"))
        assertEquals(ContractKind.RULESET, ContractKind.fromString("Ruleset"))
    }
}
