package com.oliveyoung.ivmlite.apps.admin.routes

import arrow.core.Either
import com.oliveyoung.ivmlite.apps.admin.application.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Playground Routes (Contract Playground API)
 *
 * YAML Contract를 실시간으로 편집/검증/시뮬레이션하는 API.
 *
 * POST /playground/validate  - YAML 문법 + 스키마 검증
 * POST /playground/simulate  - 샘플 데이터로 슬라이싱 시뮬레이션
 * POST /playground/diff      - 현재 계약과 비교
 * POST /playground/try       - 실제 데이터 드라이런
 * POST /playground/apply     - 변경사항 적용
 */
fun Route.playgroundRoutes() {
    val playgroundService by inject<PlaygroundService>()

    /**
     * POST /playground/validate
     * YAML 문법 + 스키마 검증
     */
    post("/playground/validate") {
        val request = call.receive<ValidateRequest>()

        when (val result = playgroundService.validateYaml(request.yaml)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Either.Left -> {
                throw result.value
            }
        }
    }

    /**
     * POST /playground/simulate
     * 샘플 데이터로 슬라이싱 시뮬레이션
     */
    post("/playground/simulate") {
        val request = call.receive<SimulateRequest>()

        when (val result = playgroundService.simulate(request.yaml, request.sampleData)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Either.Left -> {
                throw result.value
            }
        }
    }

    /**
     * POST /playground/diff
     * 현재 계약과 변경사항 비교
     */
    post("/playground/diff") {
        val request = call.receive<DiffRequest>()

        when (val result = playgroundService.diff(request.contractId, request.newYaml)) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Either.Left -> {
                throw result.value
            }
        }
    }

    /**
     * POST /playground/try
     * 실제 데이터로 드라이런 테스트
     */
    post("/playground/try") {
        val request = call.receive<TryRequest>()

        when (val result = playgroundService.tryOnRealData(
            yamlContent = request.yaml,
            entityKey = request.entityKey,
            tenantId = request.tenantId
        )) {
            is Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
            is Either.Left -> {
                throw result.value
            }
        }
    }

    /**
     * GET /playground/presets
     * 샘플 데이터 프리셋 목록
     */
    get("/playground/presets") {
        call.respond(HttpStatusCode.OK, getPresets())
    }
}

// ==================== Request DTOs ====================

@Serializable
data class ValidateRequest(
    val yaml: String,
)

@Serializable
data class SimulateRequest(
    val yaml: String,
    val sampleData: String,
)

@Serializable
data class DiffRequest(
    val contractId: String,
    val newYaml: String,
)

@Serializable
data class TryRequest(
    val yaml: String,
    val entityKey: String,
    val tenantId: String = "oliveyoung",
)

// ==================== Response DTOs ====================

@Serializable
data class ValidationResultResponse(
    val valid: Boolean,
    val errors: List<ValidationErrorResponse>,
    val warnings: List<String>,
)

@Serializable
data class ValidationErrorResponse(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String,
)

@Serializable
data class SimulationResultResponse(
    val success: Boolean,
    val slices: List<SimulatedSliceResponse>,
    val errors: List<String>,
    val rawDataPreview: RawDataPreviewResponse?,
)

@Serializable
data class SimulatedSliceResponse(
    val type: String,
    val data: String,
    val hash: String,
    val fields: List<String>,
)

@Serializable
data class RawDataPreviewResponse(
    val payload: String,
    val entityType: String,
    val sliceCount: Int,
)

@Serializable
data class DiffResultResponse(
    val added: List<DiffItemResponse>,
    val removed: List<DiffItemResponse>,
    val modified: List<DiffItemResponse>,
    val summary: String,
)

@Serializable
data class DiffItemResponse(
    val path: String,
    val oldValue: String?,
    val newValue: String?,
)

@Serializable
data class TryResultResponse(
    val success: Boolean,
    val entityKey: String,
    val version: Long,
    val slices: List<SimulatedSliceResponse>,
    val errors: List<String>,
)

@Serializable
data class PresetResponse(
    val presets: List<PresetItem>,
)

@Serializable
data class PresetItem(
    val id: String,
    val name: String,
    val description: String,
    val entityType: String,
    val sampleData: String,
    val sampleYaml: String,
)

// ==================== Domain → DTO 변환 ====================

private fun ValidationResult.toResponse() = ValidationResultResponse(
    valid = valid,
    errors = errors.map { it.toResponse() },
    warnings = warnings
)

private fun ValidationError.toResponse() = ValidationErrorResponse(
    line = line,
    column = column,
    message = message,
    severity = severity
)

private fun SimulationResult.toResponse() = SimulationResultResponse(
    success = success,
    slices = slices.map { it.toResponse() },
    errors = errors,
    rawDataPreview = rawDataPreview?.toResponse()
)

private fun SimulatedSlice.toResponse() = SimulatedSliceResponse(
    type = type,
    data = data,
    hash = hash,
    fields = fields
)

private fun RawDataPreview.toResponse() = RawDataPreviewResponse(
    payload = payload,
    entityType = entityType,
    sliceCount = sliceCount
)

private fun DiffResult.toResponse() = DiffResultResponse(
    added = added.map { it.toResponse() },
    removed = removed.map { it.toResponse() },
    modified = modified.map { it.toResponse() },
    summary = summary
)

private fun DiffItem.toResponse() = DiffItemResponse(
    path = path,
    oldValue = oldValue,
    newValue = newValue
)

private fun TryResult.toResponse() = TryResultResponse(
    success = success,
    entityKey = entityKey,
    version = version,
    slices = slices.map { it.toResponse() },
    errors = errors
)

// ==================== Presets ====================

private fun getPresets() = PresetResponse(
    presets = listOf(
        PresetItem(
            id = "product",
            name = "Product (상품)",
            description = "올리브영 상품 데이터 예시",
            entityType = "PRODUCT",
            sampleData = """{
  "id": "SKU-001",
  "name": "롬앤 쥬시 래스팅 틴트",
  "price": 13000,
  "brandId": "ROMAND",
  "categoryId": "LIP_TINT",
  "stock": 150,
  "isActive": true,
  "tags": ["신상", "베스트셀러"]
}""",
            sampleYaml = """kind: RULESET
id: product_ruleset
version: "1.0.0"
entityType: PRODUCT
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "price", "isActive"]
  - type: SUMMARY
    buildRules:
      mapFields:
        name: displayName
        price: displayPrice
        brandId: brand
"""
        ),
        PresetItem(
            id = "category",
            name = "Category (카테고리)",
            description = "상품 카테고리 데이터 예시",
            entityType = "CATEGORY",
            sampleData = """{
  "id": "LIP_TINT",
  "name": "립틴트",
  "parentId": "LIP",
  "depth": 2,
  "displayOrder": 1,
  "isActive": true
}""",
            sampleYaml = """kind: RULESET
id: category_ruleset
version: "1.0.0"
entityType: CATEGORY
slices:
  - type: CORE
    buildRules:
      passThrough: ["*"]
"""
        ),
        PresetItem(
            id = "brand",
            name = "Brand (브랜드)",
            description = "브랜드 데이터 예시",
            entityType = "BRAND",
            sampleData = """{
  "id": "ROMAND",
  "name": "롬앤",
  "logoUrl": "https://cdn.oliveyoung.co.kr/brands/romand.png",
  "isPartner": true,
  "ranking": 5
}""",
            sampleYaml = """kind: RULESET
id: brand_ruleset
version: "1.0.0"
entityType: BRAND
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "isPartner"]
  - type: ENRICHED
    buildRules:
      passThrough: ["*"]
"""
        ),
        PresetItem(
            id = "order",
            name = "Order (주문)",
            description = "주문 데이터 예시",
            entityType = "ORDER",
            sampleData = """{
  "id": "ORD-2024-001",
  "userId": "USER-123",
  "items": [
    {"productId": "SKU-001", "quantity": 2, "price": 13000},
    {"productId": "SKU-002", "quantity": 1, "price": 25000}
  ],
  "totalAmount": 51000,
  "status": "COMPLETED",
  "orderedAt": "2024-01-15T10:30:00Z"
}""",
            sampleYaml = """kind: RULESET
id: order_ruleset
version: "1.0.0"
entityType: ORDER
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "userId", "totalAmount", "status"]
  - type: DETAIL
    buildRules:
      passThrough: ["*"]
"""
        )
    )
)
