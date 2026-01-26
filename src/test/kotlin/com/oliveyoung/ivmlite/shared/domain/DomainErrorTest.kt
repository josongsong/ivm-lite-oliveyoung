package com.oliveyoung.ivmlite.shared.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainErrorTest {

    // ==================== sealed class 계층 테스트 ====================

    @Test
    fun `모든 DomainError는 RuntimeException 상속`() {
        val errors: List<DomainError> = listOf(
            DomainError.ContractError("test"),
            DomainError.ContractIntegrityError("contract-1", "abc", "def"),
            DomainError.ContractStatusError("contract-2", ContractStatus.DRAFT),
            DomainError.ValidationError("field", "msg"),
            DomainError.NotFoundError("Entity", "key-1"),
            DomainError.IdempotencyViolation("duplicate"),
            DomainError.StorageError("db error"),
            DomainError.InvariantViolation("invariant broken"),
            DomainError.ExternalServiceError("kafka", "connection refused"),
            DomainError.NotSupportedError("Feature not implemented"),
        )

        errors.forEach { error ->
            assertIs<RuntimeException>(error)
            assertTrue(error.message!!.isNotBlank())
        }
    }

    // ==================== ContractError 테스트 ====================

    @Test
    fun `ContractError - 스키마 검증 실패 표현`() {
        val error = DomainError.ContractError("Schema validation failed: missing required field 'id'")

        assertEquals("Schema validation failed: missing required field 'id'", error.msg)
        assertTrue(error.message!!.contains("Schema validation failed"))
    }

    // ==================== ValidationError 테스트 ====================

    @Test
    fun `ValidationError - 필드 + 메시지 포함`() {
        val error = DomainError.ValidationError(
            field = "email",
            msg = "invalid email format",
        )

        assertEquals("email", error.field)
        assertEquals("invalid email format", error.msg)
        assertTrue(error.message!!.contains("email"))
        assertTrue(error.message!!.contains("invalid email format"))
    }

    @Test
    fun `ValidationError - 중첩 필드 표현`() {
        val error = DomainError.ValidationError(
            field = "payload.items[0].price",
            msg = "must be positive",
        )

        assertEquals("payload.items[0].price", error.field)
    }

    // ==================== NotFoundError 테스트 ====================

    @Test
    fun `NotFoundError - 엔티티 타입과 키 포함`() {
        val error = DomainError.NotFoundError(
            entity = "RawDataRecord",
            key = "tenant-1:product-123",
        )

        assertEquals("RawDataRecord", error.entity)
        assertEquals("tenant-1:product-123", error.key)
        assertTrue(error.message!!.contains("RawDataRecord"))
        assertTrue(error.message!!.contains("tenant-1:product-123"))
    }

    @Test
    fun `NotFoundError - 다양한 엔티티 타입`() {
        listOf("SliceRecord", "Contract", "OutboxEntry").forEach { entityType ->
            val error = DomainError.NotFoundError(entity = entityType, key = "key")
            assertEquals(entityType, error.entity)
        }
    }

    // ==================== IdempotencyViolation 테스트 ====================

    @Test
    fun `IdempotencyViolation - 중복 저장 시도 표현`() {
        val error = DomainError.IdempotencyViolation(
            "Record with same key/version but different payload already exists",
        )

        assertTrue(error.message!!.contains("different payload"))
    }

    @Test
    fun `IdempotencyViolation - 해시 충돌 정보 포함 가능`() {
        val error = DomainError.IdempotencyViolation(
            "Hash mismatch: expected=abc123, actual=def456",
        )

        assertTrue(error.message!!.contains("abc123"))
        assertTrue(error.message!!.contains("def456"))
    }

    // ==================== StorageError 테스트 ====================

    @Test
    fun `StorageError - DB 오류 표현`() {
        val error = DomainError.StorageError("Connection timeout after 30s")

        assertTrue(error.message!!.contains("timeout"))
    }

    @Test
    fun `StorageError - 트랜잭션 실패`() {
        val error = DomainError.StorageError("Transaction rollback: deadlock detected")

        assertTrue(error.message!!.contains("rollback"))
    }

    // ==================== ExternalServiceError 테스트 ====================

    @Test
    fun `ExternalServiceError - 서비스명과 상세 메시지 포함`() {
        val error = DomainError.ExternalServiceError(
            service = "Kafka",
            msg = "Failed to publish message: broker not available",
        )

        assertEquals("Kafka", error.service)
        assertEquals("Failed to publish message: broker not available", error.msg)
        assertTrue(error.message!!.contains("Kafka"))
    }

    @Test
    fun `ExternalServiceError - 다양한 외부 서비스`() {
        listOf("DynamoDB", "Redis", "S3", "ElasticSearch").forEach { svc ->
            val error = DomainError.ExternalServiceError(service = svc, msg = "unavailable")
            assertEquals(svc, error.service)
        }
    }

    // ==================== when 분기 테스트 (sealed class 활용) ====================

    @Test
    fun `sealed class - when 분기 exhaustive`() {
        val errors = listOf(
            DomainError.ContractError("c"),
            DomainError.ContractIntegrityError("c-1", "abc", "def"),
            DomainError.ContractStatusError("c-2", ContractStatus.ARCHIVED),
            DomainError.ValidationError("f", "m"),
            DomainError.NotFoundError("e", "k"),
            DomainError.IdempotencyViolation("i"),
            DomainError.StorageError("s"),
            DomainError.InvariantViolation("inv"),
            DomainError.ExternalServiceError("svc", "msg"),
        )

        errors.forEach { error ->
            val code = when (error) {
                is DomainError.ContractError -> "CONTRACT"
                is DomainError.ContractIntegrityError -> "CONTRACT_INTEGRITY"
                is DomainError.ContractStatusError -> "CONTRACT_STATUS"
                is DomainError.ValidationError -> "VALIDATION"
                is DomainError.NotFoundError -> "NOT_FOUND"
                is DomainError.IdempotencyViolation -> "IDEMPOTENCY"
                is DomainError.StorageError -> "STORAGE"
                is DomainError.InvariantViolation -> "INVARIANT"
                is DomainError.ExternalServiceError -> "EXTERNAL"
                is DomainError.UnmappedChangePathError -> "UNMAPPED_PATH"
                is DomainError.JoinError -> "JOIN"
                is DomainError.MissingSliceError -> "MISSING_SLICE"
                is DomainError.NotSupportedError -> "NOT_SUPPORTED"
            }
            assertTrue(code.isNotBlank())
        }
    }

    // ==================== HTTP 상태 코드 매핑 테스트 ====================

    @Test
    fun `toHttpStatus - 에러 타입별 HTTP 상태 코드 매핑`() {
        val mappings = mapOf(
            DomainError.ContractError("c") to 400,
            DomainError.ContractIntegrityError("c-1", "abc", "def") to 500,
            DomainError.ContractStatusError("c-2", ContractStatus.DRAFT) to 400,
            DomainError.ValidationError("f", "m") to 400,
            DomainError.NotFoundError("e", "k") to 404,
            DomainError.IdempotencyViolation("i") to 409,
            DomainError.StorageError("s") to 500,
            DomainError.InvariantViolation("inv") to 500,
            DomainError.ExternalServiceError("svc", "msg") to 502,
        )

        mappings.forEach { (error, expectedStatus) ->
            assertEquals(expectedStatus, error.toHttpStatus(), "Failed for ${error::class.simpleName}")
        }
    }

    // ==================== 에러 코드 테스트 ====================

    @Test
    fun `errorCode - 일관된 에러 코드 반환`() {
        val expectations = mapOf(
            DomainError.ContractError("c") to "ERR_CONTRACT",
            DomainError.ContractIntegrityError("c-1", "abc", "def") to "ERR_CONTRACT_INTEGRITY",
            DomainError.ContractStatusError("c-2", ContractStatus.ARCHIVED) to "ERR_CONTRACT_STATUS",
            DomainError.ValidationError("f", "m") to "ERR_VALIDATION",
            DomainError.NotFoundError("e", "k") to "ERR_NOT_FOUND",
            DomainError.IdempotencyViolation("i") to "ERR_IDEMPOTENCY",
            DomainError.StorageError("s") to "ERR_STORAGE",
            DomainError.InvariantViolation("inv") to "ERR_INVARIANT",
            DomainError.ExternalServiceError("svc", "msg") to "ERR_EXTERNAL_SERVICE",
        )

        expectations.forEach { (error, expectedCode) ->
            assertEquals(expectedCode, error.errorCode, "Failed for ${error::class.simpleName}")
        }
    }

    // ==================== data class 동등성 테스트 ====================

    @Test
    fun `data class 동등성 - 같은 값이면 equal`() {
        val e1 = DomainError.NotFoundError("Entity", "key")
        val e2 = DomainError.NotFoundError("Entity", "key")

        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `data class 동등성 - 다른 값이면 not equal`() {
        val e1 = DomainError.NotFoundError("Entity", "key1")
        val e2 = DomainError.NotFoundError("Entity", "key2")

        assertTrue(e1 != e2)
    }
}
