package com.oliveyoung.ivmlite.pkg.rawdata.domain

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.views.ports.ViewComposerPort
import com.oliveyoung.ivmlite.pkg.views.domain.ViewRecord
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * IngestionWorkflow - 데이터 처리 워크플로우 (Domain Layer)
 *
 * 🎯 단일 책임: RawData → Slicing → View 비즈니스 로직만
 * - 트랜잭션 무관
 * - 이벤트 발행 무관
 * - 순수 도메인 로직
 *
 * SOTA 원칙:
 * - Domain Driven Design
 * - Single Responsibility Principle
 * - Dependency Inversion (Port에만 의존)
 */
class IngestionWorkflow(
    private val rawDataRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val slicingEngine: SlicingEnginePort,
    private val viewComposer: ViewComposerPort
) {
    private val logger = LoggerFactory.getLogger(IngestionWorkflow::class.java)

    /**
     * 데이터 처리 실행
     *
     * @param command IngestionCommand
     * @return WorkflowResult (RawData, Slices, Views)
     */
    suspend fun execute(command: IngestionCommand): Result<WorkflowResult> {
        logger.info("Starting workflow: tenant=${command.tenantId}, entity=${command.entityKey}")

        // 1. RawData 생성 및 저장
        val rawData = RawDataRecord.create(
            tenantId = command.tenantId,
            entityKey = command.entityKey,
            data = command.data,
            version = command.version
        )
        when (val result = rawDataRepo.putIdempotent(rawData)) {
            is Result.Err -> return Result.Err(result.error)
            is Result.Ok -> Unit
        }
        logger.debug("RawData saved: version=${rawData.version}")

        // 2. Slicing
        val slicingResult = when (val result = slicingEngine.slice(rawData, command.ruleSetRef)) {
            is Result.Ok -> result.value
            is Result.Err -> return Result.Err(result.error)
        }
        val slices = slicingResult.slices

        if (slices.isEmpty()) {
            return Result.Err(DomainError.ValidationError("slices", "No slices generated"))
        }

        when (val result = sliceRepo.putAllIdempotent(slices)) {
            is Result.Err -> return Result.Err(result.error)
            is Result.Ok -> Unit
        }
        logger.debug("Slicing completed: ${slices.size} slices")

        // 3. View Composition (SinkEvent payload로 전달, 별도 저장 없음)
        val views = when (val result = viewComposer.compose(slices, command.viewDefId, command.viewDefVersion)) {
            is Result.Ok -> result.value
            is Result.Err -> return Result.Err(result.error)
        }
        logger.debug("View composition completed: ${views.size} views")

        return Result.Ok(
            WorkflowResult(
                rawData = rawData,
                slices = slices,
                views = views
            )
        )
    }
}

/**
 * IngestionCommand - 데이터 처리 명령
 */
data class IngestionCommand(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val data: JsonObject,
    val ruleSetRef: ContractRef,
    val viewDefId: String,
    val viewDefVersion: String = "1.0.0",
    val version: Long = 1L,
    val jobId: String? = null
)

/**
 * WorkflowResult - 워크플로우 실행 결과
 */
data class WorkflowResult(
    val rawData: RawDataRecord,
    val slices: List<SliceRecord>,
    val views: List<ViewRecord>
)
