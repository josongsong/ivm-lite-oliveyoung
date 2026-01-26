package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL
import java.security.MessageDigest

/**
 * JooqSliceRepository 통합 테스트 (PostgreSQL Testcontainers)
 *
 * RFC-IMPL Phase B-4: Slice 어댑터 실제 DB 검증
 *
 * Docker 없으면 자동 스킵됨.
 * 실행: ./gradlew integrationTest
 */
@EnabledIf(DockerEnabledCondition::class)
class JooqSliceRepositoryIntegrationTest : StringSpec({

    tags(IntegrationTag)

    val dsl = PostgresTestContainer.start()
    val repository: SliceRepositoryPort = JooqSliceRepository(dsl)

    beforeEach {
        dsl.execute("TRUNCATE TABLE slices CASCADE")
    }

    "putAllIdempotent - 새 슬라이스 저장 성공" {
        val slice = createTestSlice("tenant-1", "entity-1", 1L, SliceType.CORE)

        val result = runBlocking { repository.putAllIdempotent(listOf(slice)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()

        // DB 직접 확인
        val count = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("tenant_id").eq("tenant-1"))
            .fetchOne(0, Int::class.java)
        count shouldBe 1
    }

    "putAllIdempotent - 동일 데이터 2번 저장 (멱등성)" {
        val slice = createTestSlice("tenant-1", "entity-2", 1L, SliceType.CORE)

        runBlocking { repository.putAllIdempotent(listOf(slice)) }
        val result = runBlocking { repository.putAllIdempotent(listOf(slice)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()

        // 레코드 1개만 존재
        val count = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("entity_key").eq("entity-2"))
            .fetchOne(0, Int::class.java)
        count shouldBe 1
    }

    "putAllIdempotent - 같은 key/version/type, 다른 hash → 에러" {
        val slice1 = createTestSlice("tenant-1", "entity-3", 1L, SliceType.CORE, data = """{"a": 1}""")
        val slice2 = createTestSlice("tenant-1", "entity-3", 1L, SliceType.CORE, data = """{"b": 2}""")

        runBlocking { repository.putAllIdempotent(listOf(slice1)) }
        val result = runBlocking { repository.putAllIdempotent(listOf(slice2)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
        (result as SliceRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
    }

    "putAllIdempotent - 여러 슬라이스 배치 저장" {
        val slices = listOf(
            createTestSlice("tenant-1", "entity-batch", 1L, SliceType.CORE),
            createTestSlice("tenant-1", "entity-batch", 1L, SliceType.JOINED),
            createTestSlice("tenant-1", "entity-batch", 1L, SliceType.DERIVED),
        )

        val result = runBlocking { repository.putAllIdempotent(slices) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()

        val count = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("entity_key").eq("entity-batch"))
            .fetchOne(0, Int::class.java)
        count shouldBe 3
    }

    "batchGet - 존재하는 슬라이스 조회" {
        val slice = createTestSlice("tenant-1", "entity-4", 1L, SliceType.CORE)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-4"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        val fetched = (result as SliceRepositoryPort.Result.Ok).value
        fetched shouldHaveSize 1
        fetched[0].tenantId shouldBe slice.tenantId
        fetched[0].entityKey shouldBe slice.entityKey
        fetched[0].sliceType shouldBe slice.sliceType
        fetched[0].hash shouldBe slice.hash
    }

    "batchGet - 존재하지 않는 슬라이스 → NotFoundError" {
        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-x"),
            entityKey = EntityKey("not-exists"),
            version = 999L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-x"), listOf(key)) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
        (result as SliceRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "batchGet - 여러 키 조회" {
        val slices = listOf(
            createTestSlice("tenant-1", "entity-multi", 1L, SliceType.CORE),
            createTestSlice("tenant-1", "entity-multi", 1L, SliceType.JOINED),
        )
        runBlocking { repository.putAllIdempotent(slices) }

        val keys = listOf(
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-multi"), 1L, SliceType.CORE),
            SliceRepositoryPort.SliceKey(TenantId("tenant-1"), EntityKey("entity-multi"), 1L, SliceType.JOINED),
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), keys) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        (result as SliceRepositoryPort.Result.Ok).value shouldHaveSize 2
    }

    "putAllIdempotent - 다른 버전은 별도 레코드" {
        val slice1 = createTestSlice("tenant-1", "entity-5", 1L, SliceType.CORE)
        val slice2 = createTestSlice("tenant-1", "entity-5", 2L, SliceType.CORE)

        runBlocking { repository.putAllIdempotent(listOf(slice1)) }
        runBlocking { repository.putAllIdempotent(listOf(slice2)) }

        val count = dsl.selectCount()
            .from(DSL.table("slices"))
            .where(DSL.field("entity_key").eq("entity-5"))
            .and(DSL.field("slice_type").eq("CORE"))
            .fetchOne(0, Int::class.java)
        count shouldBe 2
    }

    "putAllIdempotent - 빈 리스트 → OK" {
        val result = runBlocking { repository.putAllIdempotent(emptyList()) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Unit>>()
    }

    "batchGet - 빈 키 리스트 → 빈 결과" {
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), emptyList()) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        (result as SliceRepositoryPort.Result.Ok).value shouldHaveSize 0
    }

    // ==================== RFC-IMPL-010 D-1: Tombstone 테스트 ====================

    "tombstone slice 저장 → DB 컬럼 확인" {
        val tombstone = Tombstone.create(version = 5L, reason = DeleteReason.USER_DELETE)
        val slice = createTestSlice("tenant-1", "entity-tomb-1", 1L, SliceType.CORE, tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val row = dsl.selectFrom(DSL.table("slices"))
            .where(DSL.field("entity_key").eq("entity-tomb-1"))
            .fetchOne()

        row!!.get(DSL.field("is_deleted", Boolean::class.java)) shouldBe true
        row.get(DSL.field("deleted_at_version", Long::class.java)) shouldBe 5L
        row.get(DSL.field("delete_reason", String::class.java)) shouldBe "user_delete"
    }

    "tombstone slice - includeTombstones=false → NotFoundError" {
        val tombstone = Tombstone.create(version = 1L, reason = DeleteReason.POLICY_HIDE)
        val slice = createTestSlice("tenant-1", "entity-tomb-2", 1L, SliceType.CORE, tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-tomb-2"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = false) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Err>()
        (result as SliceRepositoryPort.Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "tombstone slice - includeTombstones=true → 조회 성공" {
        val tombstone = Tombstone.create(version = 3L, reason = DeleteReason.ARCHIVED)
        val slice = createTestSlice("tenant-1", "entity-tomb-3", 1L, SliceType.CORE, tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-tomb-3"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }

        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        val fetched = (result as SliceRepositoryPort.Result.Ok).value
        fetched shouldHaveSize 1
        fetched[0].isDeleted shouldBe true
        fetched[0].tombstone!!.deletedAtVersion shouldBe 3L
        fetched[0].tombstone!!.deleteReason shouldBe DeleteReason.ARCHIVED
    }

    "tombstone 왕복 테스트 - 저장 후 조회 일치" {
        val tombstone = Tombstone.create(version = 42L, reason = DeleteReason.VALIDATION_FAIL)
        val slice = createTestSlice("tenant-1", "entity-tomb-4", 1L, SliceType.CORE, tombstone = tombstone)
        runBlocking { repository.putAllIdempotent(listOf(slice)) }

        val key = SliceRepositoryPort.SliceKey(
            tenantId = TenantId("tenant-1"),
            entityKey = EntityKey("entity-tomb-4"),
            version = 1L,
            sliceType = SliceType.CORE,
        )
        val result = runBlocking { repository.batchGet(TenantId("tenant-1"), listOf(key), includeTombstones = true) }

        val fetched = (result as SliceRepositoryPort.Result.Ok).value[0]
        fetched.tombstone shouldBe tombstone
    }
})

private fun createTestSlice(
    tenantId: String,
    entityKey: String,
    version: Long,
    sliceType: SliceType,
    data: String = """{"name": "test"}""",
    ruleSetId: String = "ruleset.core.v1",
    ruleSetVersion: SemVer = SemVer.parse("1.0.0"),
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
        ruleSetId = ruleSetId,
        ruleSetVersion = ruleSetVersion,
        tombstone = tombstone,
    )
}

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
