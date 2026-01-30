package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PlaygroundService 단위 테스트
 *
 * YAML 검증, 시뮬레이션, 차이 비교 기능 테스트
 */
class PlaygroundServiceTest {

    private lateinit var contractRegistry: ContractRegistryPort
    private lateinit var contractService: AdminContractService
    private lateinit var rawDataRepo: RawDataRepositoryPort
    private lateinit var service: PlaygroundService

    @BeforeEach
    fun setup() {
        contractRegistry = mockk(relaxed = true)
        contractService = mockk(relaxed = true)
        rawDataRepo = mockk(relaxed = true)
        service = PlaygroundService(contractRegistry, contractService, rawDataRepo)
    }

    // ==================== validateYaml 테스트 ====================

    @Test
    fun `validateYaml - 올바른 RULESET YAML 검증 성공`() {
        // Given
        val validYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            entityType: PRODUCT
            slices:
              - type: CORE
                buildRules:
                  passThrough: ["*"]
        """.trimIndent()

        // When
        val result = service.validateYaml(validYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertTrue(validation.valid)
        assertTrue(validation.errors.isEmpty())
    }

    @Test
    fun `validateYaml - kind 필드 누락 시 에러`() {
        // Given
        val invalidYaml = """
            id: test-ruleset
            version: 1.0.0
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("kind") })
    }

    @Test
    fun `validateYaml - id 필드 누락 시 에러`() {
        // Given
        val invalidYaml = """
            kind: RULESET
            version: 1.0.0
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("id") })
    }

    @Test
    fun `validateYaml - RULESET에 entityType 누락 시 에러`() {
        // Given
        val invalidYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            slices:
              - type: CORE
                buildRules:
                  passThrough: ["*"]
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("entityType") })
    }

    @Test
    fun `validateYaml - RULESET에 slices 누락 시 에러`() {
        // Given
        val invalidYaml = """
            kind: RULESET
            id: test-ruleset
            entityType: PRODUCT
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("slices") })
    }

    @Test
    fun `validateYaml - 잘못된 YAML 문법 시 에러`() {
        // Given
        val brokenYaml = """
            kind: RULESET
            id: test-ruleset
              invalid: indentation
        """.trimIndent()

        // When
        val result = service.validateYaml(brokenYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.isNotEmpty())
    }

    @Test
    fun `validateYaml - 알 수 없는 kind 시 경고`() {
        // Given
        val unknownKindYaml = """
            kind: UNKNOWN_KIND
            id: test-unknown
        """.trimIndent()

        // When
        val result = service.validateYaml(unknownKindYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertTrue(validation.warnings.any { it.contains("알 수 없는 kind") })
    }

    @Test
    fun `validateYaml - slice에 type 누락 시 에러`() {
        // Given
        val invalidSliceYaml = """
            kind: RULESET
            id: test-ruleset
            entityType: PRODUCT
            slices:
              - buildRules:
                  passThrough: ["*"]
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidSliceYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("type") })
    }

    @Test
    fun `validateYaml - slice에 buildRules 누락 시 에러`() {
        // Given
        val invalidSliceYaml = """
            kind: RULESET
            id: test-ruleset
            entityType: PRODUCT
            slices:
              - type: CORE
        """.trimIndent()

        // When
        val result = service.validateYaml(invalidSliceYaml)

        // Then
        assertTrue(result is Either.Right)
        val validation = (result as Either.Right).value
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.message.contains("buildRules") })
    }

    // ==================== simulate 테스트 ====================

    @Test
    fun `simulate - 유효한 YAML과 샘플 데이터로 시뮬레이션 성공`() = runTest {
        // Given
        val validYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            entityType: PRODUCT
            slices:
              - type: CORE
                buildRules:
                  passThrough: ["*"]
        """.trimIndent()
        val sampleData = """{"id": "P001", "name": "Test Product"}"""

        // When
        val result = service.simulate(validYaml, sampleData)

        // Then
        assertTrue(result is Either.Right)
        val simulation = (result as Either.Right).value
        assertTrue(simulation.success)
        assertTrue(simulation.slices.isNotEmpty())
    }

    @Test
    fun `simulate - 유효하지 않은 YAML 시 에러 반환`() = runTest {
        // Given
        val invalidYaml = "kind: INVALID"
        val sampleData = """{"id": "P001"}"""

        // When
        val result = service.simulate(invalidYaml, sampleData)

        // Then
        assertTrue(result is Either.Right)
        val simulation = (result as Either.Right).value
        assertFalse(simulation.success)
        assertTrue(simulation.errors.isNotEmpty())
    }

    @Test
    fun `simulate - 유효하지 않은 JSON 데이터 시 예외 발생`() = runTest {
        // Given
        val validYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            entityType: PRODUCT
            slices:
              - type: CORE
                buildRules:
                  passThrough: ["*"]
        """.trimIndent()
        val invalidJson = "not-a-json"

        // When/Then: simulate에서 throw DomainError.ValidationError 발생
        var exceptionThrown = false
        try {
            service.simulate(validYaml, invalidJson)
        } catch (e: Exception) {
            exceptionThrown = true
            assertTrue(e.message?.contains("JSON") == true || e.message?.contains("파싱") == true)
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun `simulate - mapFields buildRules 적용`() = runTest {
        // Given
        val yamlWithMapFields = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            entityType: PRODUCT
            slices:
              - type: CORE
                buildRules:
                  mapFields:
                    id: productId
                    name: productName
        """.trimIndent()
        val sampleData = """{"id": "P001", "name": "Test Product", "extra": "ignored"}"""

        // When
        val result = service.simulate(yamlWithMapFields, sampleData)

        // Then
        assertTrue(result is Either.Right)
        val simulation = (result as Either.Right).value
        assertTrue(simulation.success)
        assertTrue(simulation.slices.isNotEmpty())
    }

    // ==================== diff 테스트 ====================

    @Test
    fun `diff - 기존 계약이 없을 때 새 계약으로 처리`() {
        // Given
        every { contractService.getById(any(), any()) } returns AdminContractService.Result.Err(
            DomainError.NotFoundError("Contract", "test-id")
        )
        val newYaml = """
            kind: RULESET
            id: new-ruleset
        """.trimIndent()

        // When
        val result = service.diff("test-id", newYaml)

        // Then
        assertTrue(result is Either.Right)
        val diff = (result as Either.Right).value
        assertEquals("기존 계약을 찾을 수 없습니다. 새 계약으로 처리됩니다.", diff.summary)
    }

    @Test
    fun `diff - 변경사항 감지`() {
        // Given
        val oldYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
        """.trimIndent()
        every { contractService.getById(any(), any()) } returns AdminContractService.Result.Ok(
            ContractInfo(
                kind = "RULESET",
                id = "test-ruleset",
                version = "1.0.0",
                status = "ACTIVE",
                fileName = "test.yaml",
                content = oldYaml,
                parsed = mapOf("kind" to "RULESET", "id" to "test-ruleset", "version" to "1.0.0")
            )
        )
        val newYaml = """
            kind: RULESET
            id: test-ruleset
            version: 2.0.0
        """.trimIndent()

        // When
        val result = service.diff("test-ruleset", newYaml)

        // Then
        assertTrue(result is Either.Right)
        val diff = (result as Either.Right).value
        assertTrue(diff.modified.isNotEmpty())
        assertTrue(diff.modified.any { it.path == "version" })
    }

    @Test
    fun `diff - 필드 추가 감지`() {
        // Given
        val oldYaml = """
            kind: RULESET
            id: test-ruleset
        """.trimIndent()
        every { contractService.getById(any(), any()) } returns AdminContractService.Result.Ok(
            ContractInfo(
                kind = "RULESET",
                id = "test-ruleset",
                version = "1.0.0",
                status = "ACTIVE",
                fileName = "test.yaml",
                content = oldYaml,
                parsed = mapOf("kind" to "RULESET", "id" to "test-ruleset")
            )
        )
        val newYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
        """.trimIndent()

        // When
        val result = service.diff("test-ruleset", newYaml)

        // Then
        assertTrue(result is Either.Right)
        val diff = (result as Either.Right).value
        assertTrue(diff.added.any { it.path == "version" })
    }

    @Test
    fun `diff - 필드 삭제 감지`() {
        // Given
        val oldYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
        """.trimIndent()
        every { contractService.getById(any(), any()) } returns AdminContractService.Result.Ok(
            ContractInfo(
                kind = "RULESET",
                id = "test-ruleset",
                version = "1.0.0",
                status = "ACTIVE",
                fileName = "test.yaml",
                content = oldYaml,
                parsed = mapOf("kind" to "RULESET", "id" to "test-ruleset", "version" to "1.0.0")
            )
        )
        val newYaml = """
            kind: RULESET
            id: test-ruleset
        """.trimIndent()

        // When
        val result = service.diff("test-ruleset", newYaml)

        // Then
        assertTrue(result is Either.Right)
        val diff = (result as Either.Right).value
        assertTrue(diff.removed.any { it.path == "version" })
    }

    // ==================== tryOnRealData 테스트 ====================

    @Test
    fun `tryOnRealData - RawDataRepo 미설정 시 에러`() = runTest {
        // Given
        val serviceWithoutRepo = PlaygroundService(contractRegistry, contractService, null)
        val yaml = "kind: RULESET"

        // When
        val result = serviceWithoutRepo.tryOnRealData(yaml, "entity-key")

        // Then
        assertTrue(result is Either.Left)
        val error = (result as Either.Left).value
        assertEquals("ERR_NOT_FOUND", error.errorCode)
    }

    @Test
    fun `tryOnRealData - 존재하지 않는 엔티티 시 에러`() = runTest {
        // Given
        coEvery { rawDataRepo.getLatest(any(), any()) } returns RawDataRepositoryPort.Result.Err(
            DomainError.NotFoundError("RawData", "entity-key")
        )
        val yaml = "kind: RULESET"

        // When
        val result = service.tryOnRealData(yaml, "entity-key")

        // Then
        assertTrue(result is Either.Left)
    }

    @Test
    fun `tryOnRealData - 실제 데이터로 시뮬레이션 성공`() = runTest {
        // Given
        val validYaml = """
            kind: RULESET
            id: test-ruleset
            version: 1.0.0
            entityType: PRODUCT
            slices:
              - type: CORE
                buildRules:
                  passThrough: ["*"]
        """.trimIndent()
        val rawDataRecord = RawDataRecord(
            tenantId = TenantId("oliveyoung"),
            entityKey = EntityKey("product:P001"),
            version = 1L,
            schemaId = "product-schema",
            schemaVersion = SemVer.parse("1.0.0"),
            payload = """{"id": "P001", "name": "Test"}""",
            payloadHash = "abc123"
        )
        coEvery { rawDataRepo.getLatest(any(), any()) } returns RawDataRepositoryPort.Result.Ok(rawDataRecord)

        // When
        val result = service.tryOnRealData(validYaml, "product:P001")

        // Then
        assertTrue(result is Either.Right)
        val tryResult = (result as Either.Right).value
        assertTrue(tryResult.success)
    }

    // ==================== DTO 테스트 ====================

    @Test
    fun `ValidationResult - 생성 확인`() {
        val result = ValidationResult(
            valid = true,
            errors = emptyList(),
            warnings = listOf("warning1")
        )
        assertTrue(result.valid)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `ValidationError - 속성 확인`() {
        val error = ValidationError(
            line = 10,
            column = 5,
            message = "Test error",
            severity = "error"
        )
        assertEquals(10, error.line)
        assertEquals(5, error.column)
        assertEquals("error", error.severity)
    }

    @Test
    fun `SimulatedSlice - 생성 확인`() {
        val slice = SimulatedSlice(
            type = "CORE",
            data = """{"id": "1"}""",
            hash = "abc123",
            fields = listOf("id")
        )
        assertEquals("CORE", slice.type)
        assertEquals(1, slice.fields.size)
    }

    @Test
    fun `DiffItem - 추가 삭제 수정 케이스`() {
        val added = DiffItem("path.new", null, "newValue")
        val removed = DiffItem("path.old", "oldValue", null)
        val modified = DiffItem("path.changed", "old", "new")

        assertTrue(added.oldValue == null && added.newValue != null)
        assertTrue(removed.oldValue != null && removed.newValue == null)
        assertTrue(modified.oldValue != null && modified.newValue != null)
    }
}
