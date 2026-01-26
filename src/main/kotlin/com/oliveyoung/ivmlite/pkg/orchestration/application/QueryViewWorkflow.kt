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
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.trace.Tracer

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
                is ContractRegistryPort.Result.Ok -> r.value
                is ContractRegistryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 2. 필요한 모든 SliceType 결정 (required + optional)
            val allSliceTypes = (viewDef.requiredSlices + viewDef.optionalSlices).distinct().sortedBy { it.name }

            // 3. Slice 조회 (getByVersion은 없는 슬라이스를 에러로 반환하지 않음)
            val allSlices = when (val r = sliceRepo.getByVersion(tenantId, entityKey, version)) {
                is SliceRepositoryPort.Result.Ok -> r.value
                is SliceRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
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
            is SliceRepositoryPort.Result.Ok -> r.value
            is SliceRepositoryPort.Result.Err -> return Result.Err(r.error)
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
     */
    private fun buildViewData(
        viewId: String,
        entityKey: EntityKey,
        version: Long,
        slices: List<SliceRecord>,
        allSliceTypes: List<SliceType>,
        gotTypes: Set<SliceType>,
    ): String {
        val sb = StringBuilder()
        sb.append("{\"viewId\":\"")
        sb.append(viewId)
        sb.append("\",\"entityKey\":\"")
        sb.append(entityKey.value)
        sb.append("\",\"version\":")
        sb.append(version)
        sb.append(",\"slices\":[")

        // Determinism: sliceTypes 순서대로, 존재하는 것만 출력
        val existingTypes = allSliceTypes.filter { it in gotTypes }
        existingTypes.forEachIndexed { i, st ->
            if (i > 0) sb.append(',')
            val s = slices.first { it.sliceType == st }
            sb.append(s.data)
        }
        sb.append("]}")

        return sb.toString()
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
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
}
