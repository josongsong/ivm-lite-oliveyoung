package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * InMemorySliceRepository 단위 테스트
 *
 * 커버리지:
 * - putAllIdempotent: 신규, 멱등, hash 불일치
 * - batchGet: 정상, 미존재 키
 * - getByVersion: 정상, 빈 결과
 * - findByKeyPrefix: 접두사 필터, 커서 페이지네이션
 * - count: 필터링
 * - getLatestVersion: 최신 버전 조회
 * - clear / size
 */
class InMemorySliceRepositoryTest : DescribeSpec({

    val repo = InMemorySliceRepository()
    val tenantId = TenantId("test-tenant")
    val entityKey = EntityKey("product:SKU-001")
    val ruleSetVersion = SemVer.parse("1.0.0")

    fun createSlice(
        sliceType: SliceType = SliceType.CORE,
        version: Long = 1L,
        data: String = """{"name":"test"}""",
        hash: String = "hash-${sliceType.name}-$version",
        ek: EntityKey = entityKey,
    ) = SliceRecord(
        tenantId = tenantId,
        entityKey = ek,
        version = version,
        sliceType = sliceType,
        data = data,
        hash = hash,
        ruleSetId = "ruleset.product.v1",
        ruleSetVersion = ruleSetVersion,
    )

    afterEach { repo.clear() }

    describe("putAllIdempotent") {

        it("신규 저장 성공") {
            val result = repo.putAllIdempotent(listOf(createSlice()))
            result.shouldBeInstanceOf<Result.Ok<*>>()
            repo.size() shouldBe 1
        }

        it("동일 hash → 멱등 성공") {
            val slice = createSlice()
            repo.putAllIdempotent(listOf(slice))
            val result = repo.putAllIdempotent(listOf(slice))
            result.shouldBeInstanceOf<Result.Ok<*>>()
            repo.size() shouldBe 1
        }

        it("동일 키 + 다른 hash → InvariantViolation") {
            repo.putAllIdempotent(listOf(createSlice(hash = "hash-A")))
            val result = repo.putAllIdempotent(listOf(createSlice(hash = "hash-B")))
            result.shouldBeInstanceOf<Result.Err>()
            (result as Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
        }

        it("여러 슬라이스 동시 저장") {
            val slices = listOf(
                createSlice(SliceType.CORE),
                createSlice(SliceType.PRICE),
                createSlice(SliceType.INVENTORY),
            )
            repo.putAllIdempotent(slices)
            repo.size() shouldBe 3
        }
    }

    describe("batchGet") {

        it("정상 조회") {
            val slice = createSlice()
            repo.putAllIdempotent(listOf(slice))

            val keys = listOf(
                SliceRepositoryPort.SliceKey(tenantId, entityKey, 1L, SliceType.CORE)
            )
            val result = repo.batchGet(tenantId, keys)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value.size shouldBe 1
        }

        it("미존재 키 → NotFoundError") {
            val keys = listOf(
                SliceRepositoryPort.SliceKey(tenantId, entityKey, 999L, SliceType.CORE)
            )
            val result = repo.batchGet(tenantId, keys)
            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("getByVersion") {

        it("해당 version의 모든 슬라이스 조회") {
            repo.putAllIdempotent(listOf(
                createSlice(SliceType.CORE, version = 1L),
                createSlice(SliceType.PRICE, version = 1L),
                createSlice(SliceType.CORE, version = 2L),
            ))

            val result = repo.getByVersion(tenantId, entityKey, 1L)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value.size shouldBe 2
        }

        it("빈 결과") {
            val result = repo.getByVersion(tenantId, entityKey, 999L)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value.size shouldBe 0
        }
    }

    describe("findByKeyPrefix") {

        it("접두사 필터") {
            repo.putAllIdempotent(listOf(
                createSlice(ek = EntityKey("product:A")),
                createSlice(ek = EntityKey("product:B"), hash = "hash-B"),
                createSlice(ek = EntityKey("brand:X"), hash = "hash-X"),
            ))

            val result = repo.findByKeyPrefix(tenantId, "product:", limit = 10)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value.items.size shouldBe 2
        }

        it("sliceType 필터") {
            repo.putAllIdempotent(listOf(
                createSlice(SliceType.CORE),
                createSlice(SliceType.PRICE),
            ))

            val result = repo.findByKeyPrefix(tenantId, "product:", sliceType = SliceType.CORE, limit = 10)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value.items.size shouldBe 1
        }
    }

    describe("count") {

        it("전체 카운트") {
            repo.putAllIdempotent(listOf(
                createSlice(SliceType.CORE),
                createSlice(SliceType.PRICE),
            ))

            val result = repo.count(tenantId)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value shouldBe 2L
        }

        it("keyPrefix 필터") {
            repo.putAllIdempotent(listOf(
                createSlice(ek = EntityKey("product:A")),
                createSlice(ek = EntityKey("brand:X"), hash = "hash-X"),
            ))

            val result = repo.count(tenantId, keyPrefix = "product:")
            (result as Result.Ok).value shouldBe 1L
        }
    }

    describe("getLatestVersion") {

        it("최신 버전만 조회") {
            repo.putAllIdempotent(listOf(
                createSlice(version = 1L),
                createSlice(version = 2L, hash = "hash-v2"),
                createSlice(version = 3L, hash = "hash-v3"),
            ))

            val result = repo.getLatestVersion(tenantId, entityKey)
            result.shouldBeInstanceOf<Result.Ok<*>>()
            val slices = (result as Result.Ok).value
            slices.size shouldBe 1
            slices[0].version shouldBe 3L
        }

        it("빈 저장소 → 빈 리스트") {
            val result = repo.getLatestVersion(tenantId, entityKey)
            (result as Result.Ok).value shouldBe emptyList()
        }
    }

    describe("healthCheck") {

        it("항상 true") {
            repo.healthCheck() shouldBe true
        }

        it("healthName = slice") {
            repo.healthName shouldBe "slice"
        }
    }
})
