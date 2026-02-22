package com.oliveyoung.ivmlite.pkg.views.application

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.views.domain.ViewRecord
import com.oliveyoung.ivmlite.pkg.views.ports.ViewComposerPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * ViewComposer - Slice 조합 구현체
 *
 * RFC-003: ViewDefinition 기반 Slice → View 변환
 * - 여러 Slice를 조합하여 단일 View 생성
 * - 멱등성: 동일 Slice → 동일 View hash
 *
 * v1: 단순 merge (모든 Slice를 하나의 JSON으로 병합)
 * v2: ViewDefinition의 transform 규칙 적용 (향후 확장)
 */
class ViewComposer : ViewComposerPort {
    private val logger = LoggerFactory.getLogger(ViewComposer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Slice 조합 → View 생성
     *
     * 현재 구현: 단순 merge (모든 SliceType을 키로 사용)
     * {
     *   "CORE": { ... },
     *   "PRICING": { ... },
     *   "INVENTORY": { ... }
     * }
     */
    override suspend fun compose(
        slices: List<SliceRecord>,
        viewDefId: String,
        viewDefVersion: String
    ): Result<List<ViewRecord>> {
        if (slices.isEmpty()) {
            return Result.Err(DomainError.ValidationError("slices", "Empty slices for viewDef: $viewDefId"))
        }

        // Slice는 동일 tenant/entity/version이어야 함
        val tenantId = slices.first().tenantId
        val entityKey = slices.first().entityKey
        val version = slices.first().version

        slices.forEach { slice ->
            if (slice.tenantId != tenantId || slice.entityKey != entityKey || slice.version != version) {
                return Result.Err(DomainError.ValidationError(
                    "slices",
                    "All slices must have same tenant/entity/version"
                ))
            }
        }

        // tombstone 제외
        val validSlices = slices.filter { it.tombstone == null }
        if (validSlices.isEmpty()) {
            return Result.Err(DomainError.ValidationError("slices", "All slices are tombstones"))
        }

        // ViewType 생성 (단순화: viewDefId에서 추출)
        val viewType = extractViewType(viewDefId)

        // Slice 조합
        val combinedData = combineSlices(validSlices)
        val usedSlices = validSlices.map { it.sliceType.name }

        // ViewRecord 생성
        val view = ViewRecord.create(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            viewType = viewType,
            data = combinedData,
            viewDefId = viewDefId,
            viewDefVersion = viewDefVersion,
            usedSlices = usedSlices
        )

        logger.debug("View composed: type=$viewType, slices=${usedSlices.size}")
        return Result.Ok(listOf(view))
    }

    override suspend fun composeOne(
        slices: List<SliceRecord>,
        viewDefId: String,
        viewType: String,
        viewDefVersion: String
    ): Result<ViewRecord> {
        return when (val result = compose(slices, viewDefId, viewDefVersion)) {
            is Result.Ok -> {
                val view = result.value.find { it.viewType == viewType }
                if (view != null) {
                    Result.Ok(view)
                } else {
                    Result.Err(DomainError.NotFoundError("View", "viewType=$viewType"))
                }
            }
            is Result.Err -> Result.Err(result.error)
        }
    }

    /**
     * Slice 조합 로직
     *
     * v1: 단순 merge (SliceType을 키로 사용)
     * {
     *   "CORE": { "name": "iPhone", ... },
     *   "PRICING": { "price": 1200000, ... }
     * }
     */
    private fun combineSlices(slices: List<SliceRecord>): String {
        val combined = buildJsonObject {
            slices.forEach { slice ->
                // tombstone 체크 (이미 위에서 필터링했지만 안전장치)
                if (slice.tombstone != null) return@forEach

                runCatching {
                    val sliceData = json.parseToJsonElement(slice.data).jsonObject
                    put(slice.sliceType.name, sliceData)
                }.onFailure { e ->
                    logger.warn("Failed to parse slice data: sliceType=${slice.sliceType}, error=${e.message}")
                }
            }
        }

        return combined.toString()
    }

    /**
     * ViewDefinition ID에서 ViewType 추출
     *
     * 예: "view.product.pdp.v1" → "PRODUCT_PDP"
     */
    private fun extractViewType(viewDefId: String): String {
        val parts = viewDefId.split(".")
        return if (parts.size >= 3 && parts[0] == "view") {
            "${parts[1].uppercase()}_${parts[2].uppercase()}"
        } else {
            viewDefId.uppercase().replace(".", "_")
        }
    }
}
