package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.MissingPolicy
import com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.trace.Tracer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cross-domain orchestration workflow for runtime view query flow.
 *
 * RFC-V4-010: 외부 진입점은 *Workflow로 명명
 * RFC-IMPL-005: QueryView Workflow v1
 * RFC-IMPL-010 GAP-D: ViewDefinitionContract 기반 실행
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 *
 * Contract is Law: ViewDefinition이 조회 정책의 SSOT
 * - MissingPolicy: 필수 슬라이스 누락 시 정책
 * - PartialPolicy: 부분 응답 허용 시 세부 정책
 * - FallbackPolicy: 폴백 정책
 */
class QueryViewWorkflow(
    private val sliceRepo: SliceRepositoryPort,
    private val contractRegistry: ContractRegistryPort? = null,  // optional for backward compatibility
    private val defaultViewVersion: SemVer = SemVer.parse("1.0.0"),
    private val tracer: Tracer = io.opentelemetry.api.OpenTelemetry.noop().getTracer("query"),
) {
    /**
     * v2: ViewDefinitionContract 기반 실행
     *
     * @param tenantId 테넌트 ID
     * @param viewId ViewDefinition contract ID
     * @param entityKey 엔티티 키
     * @param version 데이터 버전
     * @return ViewResponse JSON 또는 에러
     */
    suspend fun execute(
        tenantId: TenantId,
        viewId: String,
        entityKey: EntityKey,
        version: Long,
    ): Result<ViewResponse> {
        return tracer.withSpanSuspend(
            "QueryViewWorkflow.execute",
            mapOf(
                "tenant_id" to tenantId.value,
                "view_id" to viewId,
                "entity_key" to entityKey.value,
                "version" to version.toString(),
            ),
        ) {
            // contractRegistry가 없으면 에러 (v1 호환 모드 제거)
            val registry = contractRegistry
                ?: return@withSpanSuspend Result.Err(ContractError("ContractRegistryPort not configured"))

            // 1. ViewDefinitionContract 로드
            val viewRef = ContractRef(viewId, defaultViewVersion)
            val viewDef = when (val r = registry.loadViewDefinitionContract(viewRef)) {
                is Result.Ok -> r.value
                is Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 2. 필요한 모든 SliceType 결정 (required + optional)
            val allSliceTypes = (viewDef.requiredSlices + viewDef.optionalSlices).distinct().sortedBy { it.name }

            // 3. Slice 조회 (getByVersion은 없는 슬라이스를 에러로 반환하지 않음)
            val allSlices = when (val r = sliceRepo.getByVersion(tenantId, entityKey, version)) {
                is Result.Ok -> r.value
                is Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }
            // 필요한 SliceType만 필터링
            val slices = allSlices.filter { it.sliceType in allSliceTypes }

            // 4. MissingPolicy 적용
            val gotTypes = slices.map { it.sliceType }.toSet()
            val missingRequired = viewDef.requiredSlices.filter { it !in gotTypes }
            val missingOptional = viewDef.optionalSlices.filter { it !in gotTypes }

            when (viewDef.missingPolicy) {
                MissingPolicy.FAIL_CLOSED -> {
                    // 필수 슬라이스 하나라도 누락 시 즉시 실패
                    if (missingRequired.isNotEmpty()) {
                        return@withSpanSuspend Result.Err(
                            DomainError.MissingSliceError(
                                missingRequired.map { it.name },
                                "required slices missing (policy: FAIL_CLOSED)"
                            )
                        )
                    }
                }
                MissingPolicy.PARTIAL_ALLOWED -> {
                    // PartialPolicy에 따라 처리
                    if (!viewDef.partialPolicy.allowed && missingRequired.isNotEmpty()) {
                        return@withSpanSuspend Result.Err(
                            DomainError.MissingSliceError(
                                missingRequired.map { it.name },
                                "required slices missing (partialPolicy.allowed=false)"
                            )
                        )
                    }
                    if (viewDef.partialPolicy.optionalOnly && missingRequired.isNotEmpty()) {
                        return@withSpanSuspend Result.Err(
                            DomainError.MissingSliceError(
                                missingRequired.map { it.name },
                                "required slices missing (partialPolicy.optionalOnly=true)"
                            )
                        )
                    }
                }
            }

            // 5. 응답 생성
            val viewData = buildViewData(viewId, entityKey, version, slices, allSliceTypes, gotTypes)

            // 6. ResponseMeta 생성 (정책에 따라)
            val meta = if (viewDef.partialPolicy.responseMeta.includeMissingSlices ||
                viewDef.partialPolicy.responseMeta.includeUsedContracts
            ) {
                ViewMeta(
                    missingSlices = if (viewDef.partialPolicy.responseMeta.includeMissingSlices) {
                        (missingRequired + missingOptional).map { it.name }
                    } else null,
                    usedContracts = if (viewDef.partialPolicy.responseMeta.includeUsedContracts) {
                        listOf("${viewDef.meta.id}@${viewDef.meta.version}")
                    } else null,
                )
            } else null

            Result.Ok(ViewResponse(data = viewData, meta = meta))
        }
    }

    /**
     * v1 호환: sliceTypes를 직접 전달하는 방식 (deprecated, backward compatibility)
     */
    @Deprecated("Use execute(tenantId, viewId, entityKey, version) with ViewDefinitionContract")
    suspend fun execute(
        tenantId: TenantId,
        viewId: String,
        entityKey: EntityKey,
        version: Long,
        requiredSliceTypes: List<SliceType>,
    ): Result<ViewResponse> {
        // 결정성: sliceTypes는 내부에서 정렬
        val sliceTypes = requiredSliceTypes.distinct().sortedBy { it.name }
        val keys = sliceTypes.map { st -> SliceRepositoryPort.SliceKey(tenantId, entityKey, version, st) }
        val slices = when (val r = sliceRepo.batchGet(tenantId, keys)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }

        // Fail-closed: required sliceTypes가 누락되면 즉시 오류
        val gotTypes = slices.map { it.sliceType }.toSet()
        val missing = sliceTypes.filter { it !in gotTypes }
        if (missing.isNotEmpty()) {
            return Result.Err(ContractError("missing slices: " + missing.joinToString(",") { it.name }))
        }

        val viewData = buildViewData(viewId, entityKey, version, slices, sliceTypes, gotTypes)
        return Result.Ok(ViewResponse(data = viewData, meta = null))
    }

    /**
     * View 데이터 JSON 생성 (결정성 보장: sliceTypes 순서대로)
     *
     * kotlinx.serialization 사용으로 안전한 JSON 생성
     * - 자동 escape 처리 (XSS 방지)
     * - 타입 안전성 보장
     */
    private fun buildViewData(
        viewId: String,
        entityKey: EntityKey,
        version: Long,
        slices: List<SliceRecord>,
        allSliceTypes: List<SliceType>,
        gotTypes: Set<SliceType>,
    ): String {
        // Determinism: sliceTypes 순서대로, 존재하는 것만 출력
        val existingTypes = allSliceTypes.filter { it in gotTypes }
        val sliceJsonElements = existingTypes.map { st ->
            val sliceData = slices.first { it.sliceType == st }.data
            Json.parseToJsonElement(sliceData)
        }

        val result = buildJsonObject {
            put("viewId", viewId)
            put("entityKey", entityKey.value)
            put("version", version)
            put("slices", JsonArray(sliceJsonElements))
        }

        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), result)
    }

    /**
     * View 응답 구조
     */
    data class ViewResponse(
        val data: String,
        val meta: ViewMeta?,
    )

    /**
     * View 메타데이터 (PartialPolicy에 따라 포함)
     */
    data class ViewMeta(
        val missingSlices: List<String>?,
        val usedContracts: List<String>?,
    )

    // ===== RFC-IMPL-011 Wave 6: Range Query & Count =====

    /**
     * Range Query: 키 프리픽스로 여러 엔티티 조회
     * 
     * @param tenantId 테넌트 ID
     * @param keyPrefix 엔티티 키 프리픽스
     * @param sliceType 특정 Slice 타입 (null이면 전체)
     * @param limit 최대 결과 수
     * @param cursor 페이지네이션 커서
     * @return 결과 페이지
     */
    suspend fun executeRange(
        tenantId: TenantId,
        keyPrefix: String,
        sliceType: SliceType? = null,
        limit: Int = 100,
        cursor: String? = null,
    ): Result<RangeResult> {
        return tracer.withSpanSuspend(
            "QueryViewWorkflow.executeRange",
            mapOf(
                "tenant_id" to tenantId.value,
                "key_prefix" to keyPrefix,
                "slice_type" to (sliceType?.name ?: "ALL"),
                "limit" to limit.toString(),
            ),
        ) {
            val queryResult = when (val r = sliceRepo.findByKeyPrefix(tenantId, keyPrefix, sliceType, limit, cursor)) {
                is Result.Ok -> r.value
                is Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }
            
            val items = queryResult.items.map { slice ->
                RangeItem(
                    entityKey = slice.entityKey.value,
                    version = slice.version,
                    sliceType = slice.sliceType.name,
                    data = slice.data,
                    hash = slice.hash
                )
            }
            
            Result.Ok(RangeResult(
                items = items,
                totalCount = items.size.toLong(),
                hasMore = queryResult.hasMore,
                nextCursor = queryResult.nextCursor
            ))
        }
    }

    /**
     * Count: 조건에 맞는 Slice 개수 조회
     */
    suspend fun executeCount(
        tenantId: TenantId,
        keyPrefix: String? = null,
        sliceType: SliceType? = null,
    ): Result<Long> {
        return tracer.withSpanSuspend(
            "QueryViewWorkflow.executeCount",
            mapOf(
                "tenant_id" to tenantId.value,
                "key_prefix" to (keyPrefix ?: "ALL"),
                "slice_type" to (sliceType?.name ?: "ALL"),
            ),
        ) {
            when (val r = sliceRepo.count(tenantId, keyPrefix, sliceType)) {
                is Result.Ok -> Result.Ok(r.value)
                is Result.Err -> Result.Err(r.error)
            }
        }
    }

    /**
     * 최신 버전 조회
     */
    suspend fun executeLatest(
        tenantId: TenantId,
        entityKey: EntityKey,
        sliceType: SliceType? = null,
    ): Result<ViewResponse> {
        return tracer.withSpanSuspend(
            "QueryViewWorkflow.executeLatest",
            mapOf(
                "tenant_id" to tenantId.value,
                "entity_key" to entityKey.value,
                "slice_type" to (sliceType?.name ?: "ALL"),
            ),
        ) {
            val slices = when (val r = sliceRepo.getLatestVersion(tenantId, entityKey, sliceType)) {
                is Result.Ok -> r.value
                is Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }
            
            if (slices.isEmpty()) {
                return@withSpanSuspend Result.Err(DomainError.NotFoundError("Slice", entityKey.value))
            }
            
            val version = slices.first().version
            val sliceTypes = slices.map { it.sliceType }.sortedBy { it.name }
            val gotTypes = sliceTypes.toSet()
            val viewData = buildViewData("latest", entityKey, version, slices, sliceTypes, gotTypes)
            
            Result.Ok(ViewResponse(data = viewData, meta = null))
        }
    }

    /**
     * Range 결과
     */
    data class RangeResult(
        val items: List<RangeItem>,
        val totalCount: Long,
        val hasMore: Boolean,
        val nextCursor: String?
    )

    /**
     * Range 결과 아이템
     */
    data class RangeItem(
        val entityKey: String,
        val version: Long,
        val sliceType: String,
        val data: String,
        val hash: String
    )
}
