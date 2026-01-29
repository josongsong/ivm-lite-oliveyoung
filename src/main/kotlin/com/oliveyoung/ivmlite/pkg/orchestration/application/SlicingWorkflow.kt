package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetBuilderPort
import com.oliveyoung.ivmlite.pkg.changeset.ports.ImpactCalculatorPort
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Cross-domain orchestration workflow: 여러 도메인(rawdata 읽기 + slices 저장)을 조율.
 *
 * RFC-V4-010: 외부 진입점은 *Workflow로 명명
 * RFC-IMPL-010 Phase D-3: SlicingEngine 기반 슬라이싱
 * RFC-IMPL-010 Phase D-8: INCREMENTAL 슬라이싱
 * RFC-IMPL-010 Phase D-9: InvertedIndex 동시 생성 및 저장
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 *
 * v4 slicing: RuleSet을 적용하여 Slice를 생성하고 저장한다.
 */
class SlicingWorkflow(
    private val rawRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val slicingEngine: SlicingEnginePort,
    private val invertedIndexRepo: InvertedIndexRepositoryPort,
    private val changeSetBuilder: ChangeSetBuilderPort,
    private val impactCalculator: ImpactCalculatorPort,
    private val contractRegistry: ContractRegistryPort,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("slicing"),
) {
    companion object {
        // RFC-IMPL-004 (v1): ruleSet은 외부 파라미터로 받지 않고 v1 고정값을 사용
        private const val V1_RULESET_ID = "ruleset.core.v1"
        private val V1_RULESET_VERSION = SemVer.parse("1.0.0")
        private val V1_RULESET_REF = ContractRef(V1_RULESET_ID, V1_RULESET_VERSION)
    }

    /**
     * FULL 슬라이싱: 전체 Slice 재생성
     *
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param version 데이터 버전
     * @param ruleSetRef RuleSet 참조 (기본값: V1_RULESET_REF)
     */
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        ruleSetRef: ContractRef = V1_RULESET_REF,
    ): Result<List<SliceRepositoryPort.SliceKey>> {
        return tracer.withSpanSuspend(
            "SlicingWorkflow.execute",
            mapOf(
                "tenant_id" to tenantId.value,
                "entity_key" to entityKey.value,
                "version" to version.toString(),
                "mode" to "FULL",
                "ruleset_ref" to ruleSetRef.id,
            ),
        ) {
            val raw = when (val r = rawRepo.get(tenantId, entityKey, version)) {
                is RawDataRepositoryPort.Result.Ok -> r.value
                is RawDataRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // RFC-IMPL-010 D-3: SlicingEngine을 사용하여 RuleSet 기반 슬라이싱
            val slicingResult: SlicingEnginePort.SlicingResult = when (val r = slicingEngine.slice(raw, ruleSetRef)) {
                is SlicingEnginePort.Result.Ok -> r.value
                is SlicingEnginePort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            val put = sliceRepo.putAllIdempotent(slicingResult.slices)
            when (put) {
                is SliceRepositoryPort.Result.Ok -> Unit
                is SliceRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(put.error)
            }

            // RFC-IMPL-010 Phase D-9: Inverted Indexes 저장
            val putIndexes = invertedIndexRepo.putAllIdempotent(slicingResult.indexes)
            when (putIndexes) {
                is InvertedIndexRepositoryPort.Result.Ok -> Unit
                is InvertedIndexRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(putIndexes.error)
            }

            val keys = slicingResult.slices.map { slice ->
                SliceRepositoryPort.SliceKey(tenantId, entityKey, version, slice.sliceType)
            }
            Result.Ok(keys)
        }
    }

    /**
     * RFC-IMPL-010 GAP-F: 자동 슬라이싱 모드 선택
     *
     * 이전 버전이 존재하면 INCREMENTAL, 없으면 FULL로 실행.
     * OutboxPollingWorker가 이 메서드를 호출하면 자동으로 최적의 경로 선택.
     *
     * ## L12 원칙
     * - **결정성**: 동일 입력 → 동일 결과 (FULL ≡ INCREMENTAL)
     * - **멱등성**: 재실행해도 동일 결과
     * - **fail-closed**: INCREMENTAL 중 매핑 안 된 변경 → 에러 (FULL로 폴백하지 않음)
     *
     * ## 버전 갭 처리
     * - version-1이 없으면 FULL로 폴백 (예: v1→v5 갱신 시)
     * - 연속 버전이 아닌 경우에도 안전하게 동작
     * - 성능: INCREMENTAL이 불가능한 경우 FULL로 자동 전환
     *
     * ## 사용 예시
     * ```kotlin
     * // OutboxPollingWorker에서 호출
     * slicingWorkflow.executeAuto(tenantId, entityKey, version)
     * // → 이전 버전 있으면 INCREMENTAL, 없으면 FULL
     * ```
     *
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param version 신규 버전
     * @param ruleSetRef RuleSet 참조 (기본값: V1_RULESET_REF)
     * @return 생성된 SliceKey 목록
     */
    suspend fun executeAuto(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        ruleSetRef: ContractRef = V1_RULESET_REF,
    ): Result<List<SliceRepositoryPort.SliceKey>> {
        return tracer.withSpanSuspend(
            "SlicingWorkflow.executeAuto",
            mapOf(
                "tenant_id" to tenantId.value,
                "entity_key" to entityKey.value,
                "version" to version.toString(),
                "mode" to "AUTO",
            ),
        ) {
            // 1. 첫 버전이면 무조건 FULL (이전 버전 없음)
            if (version <= 1L) {
                return@withSpanSuspend execute(tenantId, entityKey, version, ruleSetRef)
            }

            // 2. 이전 버전(version-1) 존재 여부 확인
            //    NOTE: 비연속 버전(v1→v5)의 경우 version-1(v4)이 없을 수 있음
            //    이 경우 안전하게 FULL로 폴백
            val fromVersion = version - 1
            val hasPreviousVersion = when (rawRepo.get(tenantId, entityKey, fromVersion)) {
                is RawDataRepositoryPort.Result.Ok -> true
                is RawDataRepositoryPort.Result.Err -> false
            }

            // 3. 이전 버전이 있으면 INCREMENTAL, 없으면 FULL
            //    INCREMENTAL은 ChangeSet 기반으로 영향받는 Slice만 재생성
            //    FULL은 전체 Slice 재생성 (더 비용이 높지만 항상 안전)
            if (hasPreviousVersion) {
                executeIncremental(tenantId, entityKey, fromVersion, version, ruleSetRef)
            } else {
                execute(tenantId, entityKey, version, ruleSetRef)
            }
        }
    }

    /**
     * RFC-IMPL-010 D-8: INCREMENTAL 슬라이싱
     *
     * ChangeSet → ImpactMap → 영향받는 Slice만 재생성
     * - FULL == INCREMENTAL 결과 동치 (불변식)
     * - 결정성: 동일 fromVersion, toVersion → 동일 결과
     * - fail-closed: 매핑 안 된 변경 → 에러
     *
     * @param tenantId 테넌트 ID
     * @param entityKey 엔티티 키
     * @param fromVersion 이전 버전
     * @param toVersion 신규 버전
     * @param ruleSetRef RuleSet 참조
     * @return 생성된 SliceKey 목록 (tombstone 제외)
     */
    suspend fun executeIncremental(
        tenantId: TenantId,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
        ruleSetRef: ContractRef,
    ): Result<List<SliceRepositoryPort.SliceKey>> {
        return tracer.withSpanSuspend(
            "SlicingWorkflow.executeIncremental",
            mapOf(
                "tenant_id" to tenantId.value,
                "entity_key" to entityKey.value,
                "from_version" to fromVersion.toString(),
                "to_version" to toVersion.toString(),
                "mode" to "INCREMENTAL",
            ),
        ) {
            // 1. RawData 로드
            val fromRaw = when (val r = rawRepo.get(tenantId, entityKey, fromVersion)) {
                is RawDataRepositoryPort.Result.Ok -> r.value
                is RawDataRepositoryPort.Result.Err -> null // 첫 버전인 경우
            }
            val toRaw = when (val r = rawRepo.get(tenantId, entityKey, toVersion)) {
                is RawDataRepositoryPort.Result.Ok -> r.value
                is RawDataRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 2. 첫 버전이면 FULL로 폴백
            if (fromRaw == null) {
                return@withSpanSuspend execute(tenantId, entityKey, toVersion)
            }

            // 3. RuleSet 로드
            val ruleSet = when (val r = contractRegistry.loadRuleSetContract(ruleSetRef)) {
                is ContractRegistryPort.Result.Ok -> r.value
                is ContractRegistryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 4. ChangeSet 생성
            val changeSet = changeSetBuilder.build(
                tenantId = tenantId,
                entityType = ruleSet.entityType,
                entityKey = entityKey,
                fromVersion = fromVersion,
                toVersion = toVersion,
                fromPayload = fromRaw.payload,
                toPayload = toRaw.payload,
                impactedSliceTypes = emptySet(),
                impactMap = emptyMap(),
            )

            // 5. ImpactMap 계산 (fail-closed)
            val impactMap = try {
                impactCalculator.calculate(changeSet, ruleSet)
            } catch (e: DomainError.UnmappedChangePathError) {
                return@withSpanSuspend Result.Err(e)
            }
            val impactedTypes = impactMap.keys.map { SliceType.valueOf(it) }.toSet()

            // 6. 영향받는 Slice만 재생성
            val slicingResult = when (val r = slicingEngine.slicePartial(toRaw, ruleSetRef, impactedTypes)) {
                is SlicingEnginePort.Result.Ok -> r.value
                is SlicingEnginePort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 7. 기존 Slice 중 영향 없는 것 버전 올려서 유지
            // NOTE: NotFoundError는 첫 버전일 때 발생 가능 - emptyList로 처리
            // 다른 에러(StorageError 등)는 전파해야 함
            val existingSlices = when (val r = sliceRepo.getByVersion(tenantId, entityKey, fromVersion)) {
                is SliceRepositoryPort.Result.Ok -> r.value
                is SliceRepositoryPort.Result.Err -> when (r.error) {
                    is DomainError.NotFoundError -> emptyList()
                    else -> return@withSpanSuspend Result.Err(r.error)
                }
            }
            val unchangedSlices = existingSlices
                .filter { it.sliceType !in impactedTypes }
                .map { it.copy(version = toVersion) }

            // 8. tombstone 처리: impactedTypes에 있지만 newSlices에 없는 경우
            // RFC-IMPL-010 D-1: tombstone의 hash는 결정적이어야 함 (version + type 기반)
            val newTypes = slicingResult.slices.map { it.sliceType }.toSet()
            val tombstoneTypes = impactedTypes - newTypes
            val tombstones = tombstoneTypes.map { type ->
                // tombstone hash: 결정적 생성 (version + sliceType + entityKey)
                val tombstoneHash = com.oliveyoung.ivmlite.shared.domain.determinism.Hashing.sha256Tagged(
                    "TOMBSTONE:${entityKey.value}:${type.name}:v$toVersion"
                )
                com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = toVersion,
                    sliceType = type,
                    data = "{\"__tombstone\":true}",  // 빈 데이터 대신 마커
                    hash = tombstoneHash,
                    ruleSetId = ruleSet.meta.id,
                    ruleSetVersion = ruleSet.meta.version,
                    tombstone = Tombstone.create(toVersion, DeleteReason.VALIDATION_FAIL),
                )
            }

            // 9. 저장 (Slices + Tombstones)
            val allSlices = slicingResult.slices + unchangedSlices + tombstones
            when (val r = sliceRepo.putAllIdempotent(allSlices)) {
                is SliceRepositoryPort.Result.Ok -> Unit
                is SliceRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 10. Inverted Indexes 저장
            // NOTE: tombstone slice의 index는 InvertedIndexBuilder에서 tombstone=true로 생성됨
            // (slice.tombstone?.isDeleted ?: false 참조)
            val allIndexes = slicingResult.indexes
            when (val r = invertedIndexRepo.putAllIdempotent(allIndexes)) {
                is InvertedIndexRepositoryPort.Result.Ok -> Unit
                is InvertedIndexRepositoryPort.Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            // 11. 반환: tombstone 제외한 SliceKey 목록
            val keys = slicingResult.slices.map { slice ->
                SliceRepositoryPort.SliceKey(tenantId, entityKey, toVersion, slice.sliceType)
            } + unchangedSlices.map { slice ->
                SliceRepositoryPort.SliceKey(tenantId, entityKey, toVersion, slice.sliceType)
            }
            Result.Ok(keys)
        }
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
