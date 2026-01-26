package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * RFC-IMPL-010 Phase D-1: SliceRecord tombstone 필드 TDD 테스트
 *
 * 엣지/코너 케이스 전수:
 * 1. tombstone=null → 일반 slice (삭제 안됨)
 * 2. tombstone.isDeleted=true → 조회에서 제외
 * 3. tombstone.deletedAtVersion 기록
 * 4. tombstone.deleteReason: USER_DELETE, POLICY_HIDE, VALIDATION_FAIL, ARCHIVED
 * 5. 저장 → 조회 왕복 일치
 * 6. batchGet에서 tombstone 제외 옵션
 * 7. 결정성/불변성/멱등성 검증
 */
class SliceTombstoneTest : StringSpec({

    val repository = InMemorySliceRepository()

    beforeEach {
        repository.clear()
    }

    // ==================== 1. tombstone=null → 일반 slice ====================

    "tombstone=null → isDeleted=false" {
        val slice = createSlice(tombstone = null)
        slice.isDeleted shouldBe false
        slice.tombstone shouldBe null
    }

    // ==================== 2. tombstone.isDeleted=true → 조회에서 제외 ====================

    "tombstone slice는 기본 batchGet에서 NotFoundError" {
        val slice = createSlice(
            tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE),
        )
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
        (result as SliceRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "tombstone slice는 includeTombstones=true면 조회 가능" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.POLICY_HIDE)
        val slice = createSlice(tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        val fetched = (result as SliceRepositoryPort.Result.Ok).value
        fetched.size shouldBe 1
        fetched[0].isDeleted shouldBe true
        fetched[0].tombstone shouldBe tombstone
    }

    // ==================== 3. tombstone.deletedAtVersion 기록 ====================

    "deletedAtVersion 저장/조회 왕복" {
        val tombstone = Tombstone.create(version = 42L, reason = DeleteReason.VALIDATION_FAIL)
        val slice = createSlice(tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }

        val fetched = (result as SliceRepositoryPort.Result.Ok).value[0]
        fetched.tombstone!!.deletedAtVersion shouldBe 42L
    }

    // ==================== 4. DeleteReason enum 전체 검증 ====================

    "DeleteReason.USER_DELETE" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE)
        tombstone.deleteReason shouldBe DeleteReason.USER_DELETE
        tombstone.deleteReason.toDbValue() shouldBe "user_delete"
        DeleteReason.fromDbValue("user_delete") shouldBe DeleteReason.USER_DELETE
    }

    "DeleteReason.POLICY_HIDE" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.POLICY_HIDE)
        tombstone.deleteReason shouldBe DeleteReason.POLICY_HIDE
        tombstone.deleteReason.toDbValue() shouldBe "policy_hide"
        DeleteReason.fromDbValue("POLICY_HIDE") shouldBe DeleteReason.POLICY_HIDE
    }

    "DeleteReason.VALIDATION_FAIL" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.VALIDATION_FAIL)
        tombstone.deleteReason shouldBe DeleteReason.VALIDATION_FAIL
        tombstone.deleteReason.toDbValue() shouldBe "validation_fail"
        DeleteReason.fromDbValue("Validation_Fail") shouldBe DeleteReason.VALIDATION_FAIL
    }

    "DeleteReason.ARCHIVED" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.ARCHIVED)
        tombstone.deleteReason shouldBe DeleteReason.ARCHIVED
        tombstone.deleteReason.toDbValue() shouldBe "archived"
        DeleteReason.fromDbValue("archived") shouldBe DeleteReason.ARCHIVED
    }

    "DeleteReason.fromDbValue 잘못된 값 → ValidationError" {
        shouldThrow<DomainError.ValidationError> {
            DeleteReason.fromDbValue("invalid_reason")
        }
    }

    "DeleteReason.fromDbValueOrNull 잘못된 값 → null" {
        DeleteReason.fromDbValueOrNull("invalid_reason") shouldBe null
    }

    // ==================== 5. 저장 → 조회 왕복 일치 ====================

    "일반 slice 저장 → 조회 왕복 일치" {
        val slice = createSlice(tombstone = null)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key)) }

        val fetched = (result as SliceRepositoryPort.Result.Ok).value[0]
        fetched.tenantId shouldBe slice.tenantId
        fetched.entityKey shouldBe slice.entityKey
        fetched.version shouldBe slice.version
        fetched.sliceType shouldBe slice.sliceType
        fetched.data shouldBe slice.data
        fetched.hash shouldBe slice.hash
        fetched.tombstone shouldBe null
    }

    "tombstone slice 저장 → 조회 왕복 일치 (includeTombstones=true)" {
        val tombstone = Tombstone.create(version = 5L, reason = DeleteReason.ARCHIVED)
        val slice = createSlice(tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }

        val fetched = (result as SliceRepositoryPort.Result.Ok).value[0]
        fetched.tombstone shouldBe tombstone
        fetched.isDeleted shouldBe true
    }

    // ==================== 6. batchGet tombstone 필터링 ====================

    "batchGet 혼합 조회 - 일반/tombstone 섞임" {
        val normalSlice = createSlice(entityKey = "entity-normal", tombstone = null)
        val tombstoneSlice = createSlice(
            entityKey = "entity-deleted",
            tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE),
        )
        runBlocking { repository.putAllIdempotent(listOf(normalSlice, tombstoneSlice)) }

        // tombstone 제외 조회
        val normalKey = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-normal"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val normalResult = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(normalKey)) }
        normalResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        (normalResult as SliceRepositoryPort.Result.Ok).value.size shouldBe 1

        // tombstone 포함 조회
        val deletedKey = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-deleted"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val excludeResult = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(deletedKey)) }
        excludeResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()

        val includeResult = runBlocking {
            repository.batchGet(TenantId("tenant-1"), listOf(deletedKey), includeTombstones = true)
        }
        includeResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
    }

    // ==================== 7. 결정성/불변성/멱등성 ====================

    "결정성: 동일 version → 동일 tombstone 상태" {
        val tombstone1 = Tombstone.create(version = 10L, reason = DeleteReason.USER_DELETE)
        val tombstone2 = Tombstone.create(version = 10L, reason = DeleteReason.USER_DELETE)
        tombstone1 shouldBe tombstone2
    }

    "불변성: Tombstone.isDeleted는 항상 true" {
        shouldThrow<IllegalArgumentException> {
            Tombstone(isDeleted = false, deletedAtVersion = 1L, deleteReason = DeleteReason.USER_DELETE)
        }
    }

    "불변성: deletedAtVersion은 양수" {
        shouldThrow<IllegalArgumentException> {
            Tombstone.create(version = 0L, reason = DeleteReason.USER_DELETE)
        }
        shouldThrow<IllegalArgumentException> {
            Tombstone.create(version = -1L, reason = DeleteReason.USER_DELETE)
        }
    }

    "멱등성: 동일 slice 2번 저장 → OK" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.ARCHIVED)
        val slice = createSlice(tombstone = tombstone)

        val result1 = runBlocking { repository.putAllIdempotent(listOf(slice)) }
        val result2 = runBlocking { repository.putAllIdempotent(listOf(slice)) }

        result1.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()
        result2.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()
        repository.size() shouldBe 1
    }

    // ==================== 8. 경계값 테스트 ====================

    "경계값: deletedAtVersion = Long.MAX_VALUE" {
        val tombstone = Tombstone.create(version = Long.MAX_VALUE, reason = DeleteReason.ARCHIVED)
        tombstone.deletedAtVersion shouldBe Long.MAX_VALUE

        val slice = createSlice(tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-1"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }
        val fetched = (result as SliceRepositoryPort.Result.Ok).value[0]
        fetched.tombstone!!.deletedAtVersion shouldBe Long.MAX_VALUE
    }

    "경계값: deletedAtVersion = 1 (최소 유효값)" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE)
        tombstone.deletedAtVersion shouldBe 1L
    }

    // ==================== 9. 혼합 배치 조회 - 부분 실패 ====================

    "혼합 배치 조회: 여러 키 중 일부만 tombstone → 전체 실패" {
        val normalSlice = createSlice(entityKey = "entity-a", tombstone = null)
        val tombstoneSlice = createSlice(
            entityKey = "entity-b",
            tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE),
        )
        runBlocking { repository.putAllIdempotent(listOf(normalSlice, tombstoneSlice)) }

        val keys = listOf(
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-a"), 1L, SliceType.CORE),
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-b"), 1L, SliceType.CORE),
        )
        // includeTombstones=false면 entity-b가 NotFound → 전체 실패
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), keys, includeTombstones = false) }
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
    }

    "혼합 배치 조회: includeTombstones=true면 전체 성공" {
        val normalSlice = createSlice(entityKey = "entity-c", tombstone = null)
        val tombstoneSlice = createSlice(
            entityKey = "entity-d",
            tombstone = Tombstone.create(version = 2L, reason = DeleteReason.POLICY_HIDE),
        )
        runBlocking { repository.putAllIdempotent(listOf(normalSlice, tombstoneSlice)) }

        val keys = listOf(
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-c"), 1L, SliceType.CORE),
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-d"), 1L, SliceType.CORE),
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), keys, includeTombstones = true) }
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        val fetched = (result as SliceRepositoryPort.Result.Ok).value
        fetched.size shouldBe 2
        fetched.count { it.isDeleted } shouldBe 1
        fetched.count { !it.isDeleted } shouldBe 1
    }

    // ==================== 10. equals/hashCode/copy 검증 ====================

    "Tombstone equals: 동일 값 → equal" {
        val t1 = Tombstone.create(version = 100L, reason = DeleteReason.VALIDATION_FAIL)
        val t2 = Tombstone.create(version = 100L, reason = DeleteReason.VALIDATION_FAIL)
        (t1 == t2) shouldBe true
        t1.hashCode() shouldBe t2.hashCode()
    }

    "Tombstone equals: 다른 version → not equal" {
        val t1 = Tombstone.create(version = 100L, reason = DeleteReason.USER_DELETE)
        val t2 = Tombstone.create(version = 200L, reason = DeleteReason.USER_DELETE)
        (t1 == t2) shouldBe false
    }

    "Tombstone equals: 다른 reason → not equal" {
        val t1 = Tombstone.create(version = 100L, reason = DeleteReason.USER_DELETE)
        val t2 = Tombstone.create(version = 100L, reason = DeleteReason.ARCHIVED)
        (t1 == t2) shouldBe false
    }

    "Tombstone copy: 불변성 검증" {
        val original = Tombstone.create(version = 50L, reason = DeleteReason.POLICY_HIDE)
        val copied = original.copy(deletedAtVersion = 60L)
        original.deletedAtVersion shouldBe 50L
        copied.deletedAtVersion shouldBe 60L
        (original == copied) shouldBe false
    }

    // ==================== 11. DeleteReason enum 전체 순회 ====================

    "DeleteReason: 모든 enum 값 toDbValue/fromDbValue 왕복" {
        DeleteReason.entries.forEach { reason ->
            val dbValue = reason.toDbValue()
            val restored = DeleteReason.fromDbValue(dbValue)
            restored shouldBe reason
        }
    }

    "DeleteReason: 대소문자 무관 파싱" {
        DeleteReason.entries.forEach { reason ->
            val upper = reason.name.uppercase()
            val lower = reason.name.lowercase()
            val mixed = reason.name.lowercase().replaceFirstChar { it.uppercase() }

            DeleteReason.fromDbValue(upper) shouldBe reason
            DeleteReason.fromDbValue(lower) shouldBe reason
            DeleteReason.fromDbValue(mixed) shouldBe reason
        }
    }

    // ==================== 12. SliceRecord isDeleted 프로퍼티 ====================

    "SliceRecord.isDeleted: tombstone=null → false" {
        val slice = createSlice(tombstone = null)
        slice.isDeleted shouldBe false
    }

    "SliceRecord.isDeleted: tombstone 존재 → true" {
        val slice = createSlice(tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE))
        slice.isDeleted shouldBe true
    }

    // ==================== 13. 멱등성 hash 불일치 검증 ====================

    "멱등성: 같은 key, 다른 hash의 tombstone slice → 에러" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.USER_DELETE)
        val slice1 = createSlice(data = """{"a":1}""", tombstone = tombstone)
        val slice2 = createSlice(data = """{"b":2}""", tombstone = tombstone)

        runBlocking { repository.putAllIdempotent(listOf(slice1)) }
        val result = runBlocking { repository.putAllIdempotent(listOf(slice2)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
        (result as SliceRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
    }

    // ==================== 14. Serialization 테스트 ====================

    "Tombstone JSON 직렬화/역직렬화 왕복" {
        val original = Tombstone.create(version = 123L, reason = DeleteReason.POLICY_HIDE)
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<Tombstone>(json)

        restored shouldBe original
        restored.isDeleted shouldBe true
        restored.deletedAtVersion shouldBe 123L
        restored.deleteReason shouldBe DeleteReason.POLICY_HIDE
    }

    "DeleteReason JSON 직렬화/역직렬화 왕복" {
        DeleteReason.entries.forEach { reason ->
            val json = Json.encodeToString(reason)
            val restored = Json.decodeFromString<DeleteReason>(json)
            restored shouldBe reason
        }
    }

    "Tombstone JSON 포맷 검증" {
        val tombstone = Tombstone.create(version = 42L, reason = DeleteReason.USER_DELETE)
        val json = Json.encodeToString(tombstone)

        // isDeleted는 기본값(true)이므로 encodeDefaults=false(기본) 시 생략됨
        json shouldContain "deletedAtVersion"
        json shouldContain "42"
        json shouldContain "deleteReason"
        json shouldContain "USER_DELETE"
    }
})

private fun createSlice(
    tenantId: String = "tenant-1",
    entityKey: String = "entity-1",
    version: Long = 1L,
    sliceType: SliceType = SliceType.CORE,
    data: String = """{"name": "test"}""",
    tombstone: Tombstone? = null,
): SliceRecord {
    val hash = sha256(data)
    return SliceRecord(
        tenantId = TenantId(tenantId),
        entityKey = EntityKey(entityKey),
        version = version,
        sliceType = sliceType,
        data = data,
        hash = hash,
        ruleSetId = "ruleset.core.v1",
        ruleSetVersion = SemVer.parse("1.0.0"),
        tombstone = tombstone,
    )
}

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
