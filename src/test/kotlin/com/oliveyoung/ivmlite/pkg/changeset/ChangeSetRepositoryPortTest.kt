package com.oliveyoung.ivmlite.pkg.changeset
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.changeset.adapters.InMemoryChangeSetRepository
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSet
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangedPath
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactDetail
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ChangeSetRepositoryPort 계약 테스트
 */
class ChangeSetRepositoryPortTest {

    private lateinit var repo: ChangeSetRepositoryPort

    @BeforeEach
    fun setup() {
        repo = InMemoryChangeSetRepository()
    }

    // ==================== save 테스트 ====================

    @Test
    fun `save - 새 ChangeSet 저장 성공`() = runBlocking {
        val cs = createChangeSet()

        val result = repo.save(cs)

        assertIs<Result.Ok<ChangeSet>>(result)
        assertEquals(cs.changeSetId, result.value.changeSetId)
    }

    @Test
    fun `save - 동일 ID 멱등성 (같은 hash면 OK)`() = runBlocking {
        val cs = createChangeSet()
        repo.save(cs)

        val result = repo.save(cs)

        assertIs<Result.Ok<ChangeSet>>(result)
    }

    @Test
    fun `save - 동일 ID 다른 hash면 IdempotencyViolation`() = runBlocking {
        val cs1 = createChangeSet(payloadHash = "hash-1")
        val cs2 = cs1.copy(payloadHash = "hash-2")
        repo.save(cs1)

        val result = repo.save(cs2)

        assertIs<Result.Err>(result)
        assertIs<DomainError.IdempotencyViolation>(result.error)
    }

    // ==================== findById 테스트 ====================

    @Test
    fun `findById - 존재하는 ChangeSet 조회`() = runBlocking {
        val cs = createChangeSet()
        repo.save(cs)

        val result = repo.findById(cs.changeSetId)

        assertIs<Result.Ok<ChangeSet>>(result)
        assertEquals(cs.changeSetId, result.value.changeSetId)
    }

    @Test
    fun `findById - 없으면 NotFoundError`() = runBlocking {
        val result = repo.findById("non-existent-id")

        assertIs<Result.Err>(result)
        assertIs<DomainError.NotFoundError>(result.error)
    }

    // ==================== findByEntity 테스트 ====================

    @Test
    fun `findByEntity - 특정 엔티티의 ChangeSet 조회`() = runBlocking {
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product-123")

        val cs1 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 1)
        val cs2 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 2)
        val other = createChangeSet(tenantId = tenantId, entityKey = EntityKey("other"))

        repo.save(cs1)
        repo.save(cs2)
        repo.save(other)

        val result = repo.findByEntity(tenantId, entityKey)

        assertIs<Result.Ok<List<ChangeSet>>>(result)
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.entityKey == entityKey })
    }

    @Test
    fun `findByEntity - toVersion 내림차순 정렬`() = runBlocking {
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product-123")

        val cs1 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 1)
        val cs2 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 3)
        val cs3 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 2)

        repo.save(cs1)
        repo.save(cs2)
        repo.save(cs3)

        val result = repo.findByEntity(tenantId, entityKey)

        assertIs<Result.Ok<List<ChangeSet>>>(result)
        assertEquals(3, result.value[0].toVersion) // 최신 먼저
        assertEquals(2, result.value[1].toVersion)
        assertEquals(1, result.value[2].toVersion)
    }

    @Test
    fun `findByEntity - 없으면 빈 리스트`() = runBlocking {
        val result = repo.findByEntity(TenantId("t"), EntityKey("e"))

        assertIs<Result.Ok<List<ChangeSet>>>(result)
        assertTrue(result.value.isEmpty())
    }

    // ==================== findByVersionRange 테스트 ====================

    @Test
    fun `findByVersionRange - 버전 범위로 조회`() = runBlocking {
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product-123")

        val cs1 = createChangeSet(tenantId = tenantId, entityKey = entityKey, fromVersion = 0, toVersion = 1)
        val cs2 = createChangeSet(tenantId = tenantId, entityKey = entityKey, fromVersion = 1, toVersion = 2)
        val cs3 = createChangeSet(tenantId = tenantId, entityKey = entityKey, fromVersion = 2, toVersion = 3)

        repo.save(cs1)
        repo.save(cs2)
        repo.save(cs3)

        val result = repo.findByVersionRange(tenantId, entityKey, fromVersion = 1, toVersion = 3)

        assertIs<Result.Ok<List<ChangeSet>>>(result)
        assertEquals(2, result.value.size) // cs2, cs3
    }

    // ==================== findLatest 테스트 ====================

    @Test
    fun `findLatest - 가장 최신 ChangeSet 조회`() = runBlocking {
        val tenantId = TenantId("tenant-1")
        val entityKey = EntityKey("product-123")

        val cs1 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 1)
        val cs2 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 5)
        val cs3 = createChangeSet(tenantId = tenantId, entityKey = entityKey, toVersion = 3)

        repo.save(cs1)
        repo.save(cs2)
        repo.save(cs3)

        val result = repo.findLatest(tenantId, entityKey)

        assertIs<Result.Ok<ChangeSet>>(result)
        assertEquals(5, result.value.toVersion)
    }

    @Test
    fun `findLatest - 없으면 NotFoundError`() = runBlocking {
        val result = repo.findLatest(TenantId("t"), EntityKey("e"))

        assertIs<Result.Err>(result)
        assertIs<DomainError.NotFoundError>(result.error)
    }

    // ==================== ChangeType 필터 테스트 ====================

    @Test
    fun `findByChangeType - 특정 ChangeType만 조회`() = runBlocking {
        val tenantId = TenantId("tenant-1")

        val create = createChangeSet(tenantId = tenantId, changeType = ChangeType.CREATE)
        val update = createChangeSet(tenantId = tenantId, changeType = ChangeType.UPDATE)
        val delete = createChangeSet(tenantId = tenantId, changeType = ChangeType.DELETE)

        repo.save(create)
        repo.save(update)
        repo.save(delete)

        val result = repo.findByChangeType(tenantId, ChangeType.CREATE, limit = 10)

        assertIs<Result.Ok<List<ChangeSet>>>(result)
        assertEquals(1, result.value.size)
        assertEquals(ChangeType.CREATE, result.value[0].changeType)
    }

    // ==================== 헬퍼 ====================

    private fun createChangeSet(
        tenantId: TenantId = TenantId("tenant-1"),
        entityKey: EntityKey = EntityKey("entity-${UUID.randomUUID().toString().take(8)}"),
        fromVersion: Long = 0,
        toVersion: Long = 1,
        changeType: ChangeType = ChangeType.CREATE,
        payloadHash: String = "hash-${UUID.randomUUID()}",
    ): ChangeSet = ChangeSet(
        changeSetId = UUID.randomUUID().toString(),
        tenantId = tenantId,
        entityType = "Product",
        entityKey = entityKey,
        fromVersion = fromVersion,
        toVersion = toVersion,
        changeType = changeType,
        changedPaths = listOf(ChangedPath("/name", "abc123")),
        impactedSliceTypes = setOf("CORE"),
        impactMap = mapOf("CORE" to ImpactDetail("field changed", listOf("/name"))),
        payloadHash = payloadHash,
    )
}
