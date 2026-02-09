package com.oliveyoung.ivmlite.integration
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL

/**
 * JooqRawDataRepository 통합 테스트 (PostgreSQL Testcontainers)
 *
 * RFC-IMPL Phase B-4: jOOQ 어댑터 실제 DB 검증
 *
 * Docker 없으면 자동 스킵됨.
 * 실행: ./gradlew integrationTest
 */
@EnabledIf(DockerEnabledCondition::class)
class JooqRawDataRepositoryIntegrationTest : StringSpec({

    tags(IntegrationTag)

    val dsl = PostgresTestContainer.start()
    val repository: RawDataRepositoryPort = JooqRawDataRepository(dsl)

    beforeEach {
        dsl.execute("TRUNCATE TABLE raw_data CASCADE")
    }

    "putIdempotent - 새 레코드 저장 성공" {
        val record = createTestRecord("tenant-1", "entity-1", 1L)

        val result = runBlocking { repository.putIdempotent(record) }

        result.shouldBeInstanceOf<Result.Ok<Unit>>()

        val count = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .where(DSL.field("tenant_id").eq("tenant-1"))
            .fetchOne(0, Int::class.java)
        count shouldBe 1
    }

    "putIdempotent - 동일 데이터 2번 저장 (멱등성)" {
        val record = createTestRecord("tenant-1", "entity-2", 1L)

        runBlocking { repository.putIdempotent(record) }
        val result = runBlocking { repository.putIdempotent(record) }

        result.shouldBeInstanceOf<Result.Ok<Unit>>()

        val count = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq("entity-2"))
            .fetchOne(0, Int::class.java)
        count shouldBe 1
    }

    "putIdempotent - 같은 key/version, 다른 hash → 에러" {
        val record1 = createTestRecord("tenant-1", "entity-3", 1L, payload = """{"a": 1}""")
        val record2 = createTestRecord("tenant-1", "entity-3", 1L, payload = """{"b": 2}""")

        runBlocking { repository.putIdempotent(record1) }
        val result = runBlocking { repository.putIdempotent(record2) }

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
    }

    "get - 존재하는 레코드 조회" {
        val record = createTestRecord("tenant-1", "entity-4", 1L)
        runBlocking { repository.putIdempotent(record) }

        val result = runBlocking {
            repository.get(TenantId("tenant-1"), EntityKey("entity-4"), 1L)
        }

        result.shouldBeInstanceOf<Result.Ok<RawDataRecord>>()
        val fetched = (result as Result.Ok).value
        fetched.tenantId shouldBe record.tenantId
        fetched.entityKey shouldBe record.entityKey
        fetched.version shouldBe record.version
        fetched.payloadHash shouldBe record.payloadHash
    }

    "get - 존재하지 않는 레코드 → NotFoundError" {
        val result = runBlocking {
            repository.get(TenantId("tenant-x"), EntityKey("not-exists"), 999L)
        }

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    "putIdempotent - 다른 버전은 별도 레코드" {
        val record1 = createTestRecord("tenant-1", "entity-5", 1L)
        val record2 = createTestRecord("tenant-1", "entity-5", 2L)

        runBlocking { repository.putIdempotent(record1) }
        runBlocking { repository.putIdempotent(record2) }

        val count = dsl.selectCount()
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq("entity-5"))
            .fetchOne(0, Int::class.java)
        count shouldBe 2
    }
})

private fun createTestRecord(
    tenantId: String,
    entityKey: String,
    version: Long,
    schemaId: String = "product.v1",
    schemaVersion: SemVer = SemVer.parse("1.0.0"),
    payload: String = """{"name": "test"}""",
): RawDataRecord {
    return RawDataRecord(
        tenantId = TenantId(tenantId),
        entityKey = EntityKey(entityKey),
        version = version,
        schemaId = schemaId,
        schemaVersion = schemaVersion,
        payload = payload,
        payloadHash = sha256(payload),
    )
}

private fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
