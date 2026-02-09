package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SelectWhereStep

/**
 * JooqInvertedIndexRepository 단위 테스트 (RFC-IMPL-010 GAP-E)
 * 
 * 실제 DB 연동은 통합 테스트에서 수행.
 * 여기서는 기본 동작과 에러 처리를 검증.
 */
class JooqInvertedIndexRepositoryTest : StringSpec({

    "healthCheck - DSL 연결 성공 시 true" {
        val mockDsl = mockk<DSLContext>()
        every { mockDsl.selectOne() } returns mockk {
            every { fetch() } returns mockk()
        }

        val repo = JooqInvertedIndexRepository(mockDsl)
        val result = repo.healthCheck()
        
        result shouldBe true
    }

    "healthCheck - DSL 연결 실패 시 false" {
        val mockDsl = mockk<DSLContext>()
        every { mockDsl.selectOne() } throws RuntimeException("Connection failed")

        val repo = JooqInvertedIndexRepository(mockDsl)
        val result = repo.healthCheck()
        
        result shouldBe false
    }

    "healthName은 inverted-index" {
        val mockDsl = mockk<DSLContext>()
        val repo = JooqInvertedIndexRepository(mockDsl)
        
        repo.healthName shouldBe "inverted-index"
    }

    "putAllIdempotent - 빈 리스트 시 Ok 반환" {
        val mockDsl = mockk<DSLContext>()
        val repo = JooqInvertedIndexRepository(mockDsl)

        val result = repo.putAllIdempotent(emptyList())
        
        result.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.types.Result.Ok<*>>()
    }
})

// ==================== Helper ====================

private fun createTestEntry(
    tenantId: String = "tenant-1",
    refEntityKey: String = "PRODUCT#tenant-1#1234",
    refVersion: Long = 1L,
    targetEntityKey: String = "PRODUCT#tenant-1#1234",
    targetVersion: Long = 1L,
    indexType: String = "brand",
    indexValue: String = "oliveyoung",
    sliceType: SliceType = SliceType.CORE,
    sliceHash: String = "abc123",
    tombstone: Boolean = false,
): InvertedIndexEntry = InvertedIndexEntry(
    tenantId = TenantId(tenantId),
    refEntityKey = EntityKey(refEntityKey),
    refVersion = VersionLong(refVersion),
    targetEntityKey = EntityKey(targetEntityKey),
    targetVersion = VersionLong(targetVersion),
    indexType = indexType,
    indexValue = indexValue,
    sliceType = sliceType,
    sliceHash = sliceHash,
    tombstone = tombstone,
)
