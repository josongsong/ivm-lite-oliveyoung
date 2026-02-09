package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinType
import com.oliveyoung.ivmlite.pkg.slices.domain.Projection
import com.oliveyoung.ivmlite.pkg.slices.domain.ProjectionMode
import com.oliveyoung.ivmlite.pkg.slices.domain.FieldMapping
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Projection 기능 통합 테스트
 *
 * 실제 사용 시나리오:
 * 1. Brand RawData 업데이트
 * 2. Product CORE 슬라이스에 brandName, brandLogoUrl만 projection
 * 3. Fanout으로 Product 재슬라이싱
 */
class JoinExecutorProjectionIntegrationTest : StringSpec({

    "통합: Brand 업데이트 → Product CORE에 brandName projection" {
        val tenantId = TenantId("oliveyoung")
        
        // 1. Brand RawData (업데이트됨)
        val brandData = RawDataRecord(
            tenantId = tenantId,
            entityKey = EntityKey("BRAND#oliveyoung#이니스프리"),
            version = 2L,
            schemaId = "entity.brand.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payload = """{"brandId":"이니스프리","brandName":"이니스프리 (NEW)","brandDesc":"업데이트된 설명","brandLogoUrl":"https://new-logo.png","country":"KR"}""",
            payloadHash = "hash-brand",
        )

        // 2. Product RawData (brandId 참조)
        val productData = RawDataRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#oliveyoung#P001"),
            version = 1L,
            schemaId = "entity.product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payload = """{"productId":"P001","brandId":"이니스프리","title":"상품명"}""",
            payloadHash = "hash-product",
        )

        val repo = MockRawDataRepoForProjection(
            mapOf(
                "BRAND#oliveyoung#이니스프리" to brandData,
            ),
        )
        val executor = JoinExecutor(repo)

        // 3. Projection이 있는 JOIN 스펙
        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandId",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
            projection = Projection(
                mode = ProjectionMode.COPY_FIELDS,
                fields = listOf(
                    FieldMapping(
                        fromTargetPath = "/brandName",
                        toOutputPath = "/brandName",
                    ),
                    FieldMapping(
                        fromTargetPath = "/brandLogoUrl",
                        toOutputPath = "/brandLogoUrl",
                    ),
                ),
            ),
        )

        // 4. JOIN 실행
        val result = runBlocking { executor.executeJoins(productData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val data = (result as Result.Ok).value
        
        // 5. 검증: brandName과 brandLogoUrl만 포함되어야 함
        val brandJson = data["brand"]!!
        val mapper = jacksonObjectMapper()
        val brandObj = mapper.readTree(brandJson)
        
        brandObj.get("brandName")?.asText() shouldBe "이니스프리 (NEW)"
        brandObj.get("brandLogoUrl")?.asText() shouldBe "https://new-logo.png"
        
        // brandDesc, country는 포함되지 않아야 함
        brandObj.has("brandDesc") shouldBe false
        brandObj.has("country") shouldBe false
    }

    "통합: Projection 없음 → 전체 payload 반환 (하위 호환성)" {
        val tenantId = TenantId("oliveyoung")
        
        val brandData = RawDataRecord(
            tenantId = tenantId,
            entityKey = EntityKey("BRAND#oliveyoung#이니스프리"),
            version = 1L,
            schemaId = "entity.brand.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payload = """{"brandId":"이니스프리","brandName":"이니스프리","brandDesc":"설명"}""",
            payloadHash = "hash",
        )

        val productData = RawDataRecord(
            tenantId = tenantId,
            entityKey = EntityKey("PRODUCT#oliveyoung#P001"),
            version = 1L,
            schemaId = "entity.product.v1",
            schemaVersion = SemVer.parse("1.0.0"),
            payload = """{"productId":"P001","brandId":"이니스프리"}""",
            payloadHash = "hash",
        )

        val repo = MockRawDataRepoForProjection(
            mapOf("BRAND#oliveyoung#이니스프리" to brandData),
        )
        val executor = JoinExecutor(repo)

        // Projection 없음
        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandId",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
            projection = null,  // projection 없음
        )

        val result = runBlocking { executor.executeJoins(productData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val data = (result as Result.Ok).value
        
        // 전체 payload 반환
        data["brand"] shouldBe """{"brandId":"이니스프리","brandName":"이니스프리","brandDesc":"설명"}"""
    }
})

// ==================== 헬퍼 함수 ====================

/**
 * Mock RawDataRepositoryPort (JoinExecutorTest와 동일한 구현)
 */
internal class MockRawDataRepoForProjection(
    private val data: Map<String, RawDataRecord>,
) : RawDataRepositoryPort {
    override suspend fun putIdempotent(record: RawDataRecord): Result<Unit> {
        throw NotImplementedError("Not used in test")
    }

    override suspend fun get(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): Result<RawDataRecord> {
        throw NotImplementedError("Not used in test")
    }

    override suspend fun getLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): Result<RawDataRecord> {
        val record = data[entityKey.value]
        return if (record != null) {
            Result.Ok(record)
        } else {
            Result.Err(
                DomainError.NotFoundError("RawData", entityKey.value),
            )
        }
    }

    override suspend fun batchGetLatest(
        tenantId: TenantId,
        entityKeys: List<EntityKey>,
    ): Result<Map<EntityKey, RawDataRecord>> {
        val resultMap = entityKeys.mapNotNull { key ->
            data[key.value]?.let { key to it }
        }.toMap()
        return Result.Ok(resultMap)
    }
}
