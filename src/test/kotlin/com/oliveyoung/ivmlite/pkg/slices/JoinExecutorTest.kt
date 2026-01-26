package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinType
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * RFC-IMPL-010 Phase D-4: JoinExecutor Light JOIN TDD
 *
 * 엣지/코너 케이스 전수:
 * 1. LOOKUP join 성공 → 타겟 데이터 반환
 * 2. required=true, 타겟 없음 → Err(JoinError)
 * 3. required=false, 타겟 없음 → 빈 맵 반환
 * 4. sourceFieldPath에서 값 추출 (dot notation)
 * 5. targetKeyPattern 보간 (BRAND#{tenantId}#{value})
 * 6. 타겟 RawData 조회 → payload 추출
 * 7. 여러 JoinSpec → 모든 결과 병합
 * 8. 순환 참조 방지 (join depth 제한 = 1) - JoinExecutor는 재귀 호출 없음
 */
class JoinExecutorTest : StringSpec({

    // ==================== 1. LOOKUP join 성공 → 타겟 데이터 반환 ====================

    "LOOKUP join 성공 → 타겟 payload 반환" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR001"}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike","country":"US"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#BR001" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("brand" to """{"brandName":"Nike","country":"US"}""")
    }

    // ==================== 2. required=true, 타겟 없음 → Err(JoinError) ====================

    "required=true, 타겟 없음 → Err(JoinError)" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR999"}""",
        )
        val repo = MockRawDataRepo(emptyMap())
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Err>()
        val error = (result as JoinExecutor.Result.Err).error
        error.shouldBeInstanceOf<DomainError.JoinError>()
        error.message shouldBe "target not found: BRAND#T001#BR999"
    }

    // ==================== 3. required=false, 타겟 없음 → 빈 맵 반환 ====================

    "required=false, 타겟 없음 → 빈 맵 반환" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR999"}""",
        )
        val repo = MockRawDataRepo(emptyMap())
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe emptyMap()
    }

    // ==================== 4. sourceFieldPath에서 값 추출 (dot notation) ====================

    "sourceFieldPath 중첩 필드 추출 (brand.code)" {
        val sourceData = createRawData(
            payload = """{"brand":{"code":"BR001"}}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#BR001" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brand.code",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("brand" to """{"brandName":"Nike"}""")
    }

    // ==================== 5. targetKeyPattern 보간 (BRAND#{tenantId}#{value}) ====================

    "targetKeyPattern 보간 → BRAND#{tenantId}#{value}" {
        val sourceData = createRawData(
            payload = """{"brandCode":"NIKE"}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("BRAND#T001#NIKE"),
            payload = """{"brandName":"Nike Inc"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#NIKE" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("brand" to """{"brandName":"Nike Inc"}""")
    }

    // ==================== 6. 타겟 RawData 조회 → payload 추출 ====================

    "타겟 RawData 조회 → payload만 추출 (메타데이터 제외)" {
        val sourceData = createRawData(
            payload = """{"categoryId":"CAT01"}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("CATEGORY#T001#CAT01"),
            payload = """{"categoryName":"Electronics","level":1}""",
            version = 42,
            payloadHash = "some-hash",
        )
        val repo = MockRawDataRepo(mapOf("CATEGORY#T001#CAT01" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "category",
            type = JoinType.LOOKUP,
            sourceFieldPath = "categoryId",
            targetEntityType = "CATEGORY",
            targetKeyPattern = "CATEGORY#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("category" to """{"categoryName":"Electronics","level":1}""")
    }

    // ==================== 7. 여러 JoinSpec → 모든 결과 병합 ====================

    "여러 JoinSpec → 모든 결과 병합" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR001","categoryId":"CAT01"}""",
        )
        val brandData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike"}""",
        )
        val categoryData = createRawData(
            entityKey = EntityKey("CATEGORY#T001#CAT01"),
            payload = """{"categoryName":"Sports"}""",
        )
        val repo = MockRawDataRepo(
            mapOf(
                "BRAND#T001#BR001" to brandData,
                "CATEGORY#T001#CAT01" to categoryData,
            ),
        )
        val executor = JoinExecutor(repo)

        val joinSpecs = listOf(
            JoinSpec(
                name = "brand",
                type = JoinType.LOOKUP,
                sourceFieldPath = "brandCode",
                targetEntityType = "BRAND",
                targetKeyPattern = "BRAND#{tenantId}#{value}",
                required = true,
            ),
            JoinSpec(
                name = "category",
                type = JoinType.LOOKUP,
                sourceFieldPath = "categoryId",
                targetEntityType = "CATEGORY",
                targetKeyPattern = "CATEGORY#{tenantId}#{value}",
                required = true,
            ),
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpecs[0], joinSpecs[1])) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf(
            "brand" to """{"brandName":"Nike"}""",
            "category" to """{"categoryName":"Sports"}""",
        )
    }

    // ==================== 8. 순환 참조 방지 (join depth 제한 = 1) ====================

    "JoinExecutor는 재귀 호출 없음 (depth=1 보장)" {
        // JoinExecutor는 설계상 1-depth JOIN만 수행
        // 타겟 RawData를 조회하되, 타겟에 대한 추가 JOIN은 실행하지 않음
        // 따라서 순환 참조가 발생할 수 없음
        val sourceData = createRawData(
            payload = """{"brandCode":"BR001"}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike","parentBrandCode":"BR000"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#BR001" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        // 타겟의 parentBrandCode는 JOIN되지 않음 (depth=1)
        data shouldBe mapOf("brand" to """{"brandName":"Nike","parentBrandCode":"BR000"}""")
    }

    // ==================== 9. sourceFieldPath 값 없음, required=true → Err ====================

    "sourceFieldPath 값 없음, required=true → Err(JoinError)" {
        val sourceData = createRawData(
            payload = """{"productName":"Product"}""",
        )
        val repo = MockRawDataRepo(emptyMap())
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Err>()
        val error = (result as JoinExecutor.Result.Err).error
        error.shouldBeInstanceOf<DomainError.JoinError>()
        error.message shouldBe "required source field missing: brandCode"
    }

    // ==================== 10. sourceFieldPath 값 없음, required=false → 빈 맵 ====================

    "sourceFieldPath 값 없음, required=false → 빈 맵 반환" {
        val sourceData = createRawData(
            payload = """{"productName":"Product"}""",
        )
        val repo = MockRawDataRepo(emptyMap())
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe emptyMap()
    }

    // ==================== 11. 여러 JOIN 중 하나 실패 (required=true) → 전체 실패 ====================

    "여러 JOIN 중 하나 실패 (required=true) → 전체 실패" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR001","categoryId":"CAT999"}""",
        )
        val brandData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#BR001" to brandData))
        val executor = JoinExecutor(repo)

        val joinSpecs = listOf(
            JoinSpec(
                name = "brand",
                type = JoinType.LOOKUP,
                sourceFieldPath = "brandCode",
                targetEntityType = "BRAND",
                targetKeyPattern = "BRAND#{tenantId}#{value}",
                required = true,
            ),
            JoinSpec(
                name = "category",
                type = JoinType.LOOKUP,
                sourceFieldPath = "categoryId",
                targetEntityType = "CATEGORY",
                targetKeyPattern = "CATEGORY#{tenantId}#{value}",
                required = true,
            ),
        )

        val result = runBlocking { executor.executeJoins(sourceData, joinSpecs) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Err>()
        val error = (result as JoinExecutor.Result.Err).error
        error.shouldBeInstanceOf<DomainError.JoinError>()
        error.message shouldBe "target not found: CATEGORY#T001#CAT999"
    }

    // ==================== 12. 여러 JOIN 중 하나 실패 (required=false) → 성공한 것만 반환 ====================

    "여러 JOIN 중 하나 실패 (required=false) → 성공한 것만 반환" {
        val sourceData = createRawData(
            payload = """{"brandCode":"BR001","categoryId":"CAT999"}""",
        )
        val brandData = createRawData(
            entityKey = EntityKey("BRAND#T001#BR001"),
            payload = """{"brandName":"Nike"}""",
        )
        val repo = MockRawDataRepo(mapOf("BRAND#T001#BR001" to brandData))
        val executor = JoinExecutor(repo)

        val joinSpecs = listOf(
            JoinSpec(
                name = "brand",
                type = JoinType.LOOKUP,
                sourceFieldPath = "brandCode",
                targetEntityType = "BRAND",
                targetKeyPattern = "BRAND#{tenantId}#{value}",
                required = true,
            ),
            JoinSpec(
                name = "category",
                type = JoinType.LOOKUP,
                sourceFieldPath = "categoryId",
                targetEntityType = "CATEGORY",
                targetKeyPattern = "CATEGORY#{tenantId}#{value}",
                required = false,
            ),
        )

        val result = runBlocking { executor.executeJoins(sourceData, joinSpecs) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("brand" to """{"brandName":"Nike"}""")
    }

    // ==================== 13. 배열 인덱스 접근 items[0].name ====================

    "sourceFieldPath 배열 인덱스 접근 (items[0].name)" {
        val sourceData = createRawData(
            payload = """{"items":[{"name":"item1","code":"IT001"},{"name":"item2","code":"IT002"}]}""",
        )
        val targetData = createRawData(
            entityKey = EntityKey("ITEM#T001#IT001"),
            payload = """{"itemName":"First Item"}""",
        )
        val repo = MockRawDataRepo(mapOf("ITEM#T001#IT001" to targetData))
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "firstItem",
            type = JoinType.LOOKUP,
            sourceFieldPath = "items[0].code",
            targetEntityType = "ITEM",
            targetKeyPattern = "ITEM#{tenantId}#{value}",
            required = true,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe mapOf("firstItem" to """{"itemName":"First Item"}""")
    }

    // ==================== 14. 빈 payload → sourceField 추출 실패 ====================

    "빈 payload → sourceField 추출 실패, required=false → 빈 맵" {
        val sourceData = createRawData(
            payload = "",
        )
        val repo = MockRawDataRepo(emptyMap())
        val executor = JoinExecutor(repo)

        val joinSpec = JoinSpec(
            name = "brand",
            type = JoinType.LOOKUP,
            sourceFieldPath = "brandCode",
            targetEntityType = "BRAND",
            targetKeyPattern = "BRAND#{tenantId}#{value}",
            required = false,
        )

        val result = runBlocking { executor.executeJoins(sourceData, listOf(joinSpec)) }

        result.shouldBeInstanceOf<JoinExecutor.Result.Ok<*>>()
        val data = (result as JoinExecutor.Result.Ok).value
        data shouldBe emptyMap()
    }
})

// ==================== 헬퍼 함수 ====================

private fun createRawData(
    tenantId: TenantId = TenantId("T001"),
    entityKey: EntityKey = EntityKey("PRODUCT#T001#P001"),
    version: Long = 1,
    payload: String = "{}",
    payloadHash: String = "hash",
): RawDataRecord {
    return RawDataRecord(
        tenantId = tenantId,
        entityKey = entityKey,
        version = version,
        schemaId = "schema.product",
        schemaVersion = SemVer.parse("1.0.0"),
        payload = payload,
        payloadHash = payloadHash,
    )
}

/**
 * Mock RawDataRepositoryPort
 * entityKey → RawDataRecord 매핑
 */
private class MockRawDataRepo(
    private val data: Map<String, RawDataRecord>,
) : RawDataRepositoryPort {
    override suspend fun putIdempotent(record: RawDataRecord): RawDataRepositoryPort.Result<Unit> {
        throw NotImplementedError("Not used in test")
    }

    override suspend fun get(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
    ): RawDataRepositoryPort.Result<RawDataRecord> {
        throw NotImplementedError("Not used in test")
    }

    override suspend fun getLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
    ): RawDataRepositoryPort.Result<RawDataRecord> {
        val record = data[entityKey.value]
        return if (record != null) {
            RawDataRepositoryPort.Result.Ok(record)
        } else {
            RawDataRepositoryPort.Result.Err(
                DomainError.NotFoundError("RawData", entityKey.value),
            )
        }
    }
}
