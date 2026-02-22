package com.oliveyoung.ivmlite.pkg.rawdata.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkRuleStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.TransactionPort
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * IngestionOrchestrator - 데이터 처리 오케스트레이터 (Application Layer)
 *
 * 🎯 책임:
 * 1. 트랜잭션 관리 (TransactionPort)
 * 2. Workflow 실행
 * 3. SinkEvent 발행 (DynamoDB Streams → Lambda)
 *
 * SOTA 원칙:
 * - Clean Architecture (Application Layer)
 * - Hexagonal Architecture (Port/Adapter)
 * - DynamoDB Streams 기반 이벤트 처리
 */
class IngestionOrchestrator(
    private val workflow: IngestionWorkflow,
    private val sinkEventRepo: SinkEventRepositoryPort,
    private val transactionPort: TransactionPort,
    private val sinkRuleRegistry: SinkRuleRegistryPort
) {
    private val logger = LoggerFactory.getLogger(IngestionOrchestrator::class.java)

    /**
     * 데이터 처리 실행 (트랜잭션 + 이벤트)
     *
     * 단일 트랜잭션:
     * - RawData 저장
     * - Slicing 실행
     * - View Composition
     * - SinkEvent 발행 (DynamoDB → Streams → Lambda)
     *
     * @param command IngestionCommand
     * @return IngestionResult
     */
    suspend fun ingest(command: IngestionCommand): Result<IngestionResult> {
        val startTime = Instant.now()

        return transactionPort.execute {
            // 1. Workflow 실행 (순수 비즈니스 로직)
            val workflowResult = when (val result = workflow.execute(command)) {
                is Result.Ok -> result.value
                is Result.Err -> return@execute Result.Err(result.error)
            }

            // 2. SinkEvent 발행 (SinkRule 매칭 시에만)
            val entityType = command.entityKey.value.substringBefore(":")
            val sinkTargets = resolveSinkTargets(entityType)
            val sinkPending = sinkTargets.isNotEmpty()

            if (sinkPending) {
                val sinkEvents = workflowResult.views.map { view ->
                    SinkEvent.create(
                        tenantId = view.tenantId.value,
                        entityKey = view.entityKey.value,
                        version = view.version,
                        viewType = view.viewType,
                        payload = view.data,
                        sinkTargets = sinkTargets,
                        jobId = command.jobId
                    )
                }

                // DynamoDB에 저장 → Streams 자동 트리거
                when (val result = sinkEventRepo.putAll(sinkEvents)) {
                    is Result.Err -> return@execute Result.Err(result.error)
                    is Result.Ok -> logger.debug("SinkEvents published: ${sinkEvents.size}")
                }
            } else {
                logger.debug("No sink targets resolved, skipping SinkEvent creation")
            }

            // 3. 결과 반환
            val duration = java.time.Duration.between(startTime, Instant.now())
            Result.Ok(
                IngestionResult(
                    tenantId = command.tenantId.value,
                    entityKey = command.entityKey.value,
                    version = workflowResult.rawData.version,
                    sliceCount = workflowResult.slices.size,
                    viewCount = workflowResult.views.size,
                    sinkPending = sinkPending,
                    durationMs = duration.toMillis()
                )
            )
        }
    }

    private suspend fun resolveSinkTargets(entityType: String): List<String> {
        return when (val result = sinkRuleRegistry.findByEntityType(entityType)) {
            is Result.Ok -> result.value
                .filter { rule -> rule.status == SinkRuleStatus.ACTIVE }
                .map { rule -> rule.target.type.name.lowercase() }
                .distinct()
            is Result.Err -> {
                logger.warn("Failed to resolve sink targets: {}", result.error)
                emptyList()
            }
        }
    }
}

/**
 * Ingestion 결과
 */
@kotlinx.serialization.Serializable
data class IngestionResult(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val sliceCount: Int,
    val viewCount: Int,
    val sinkPending: Boolean,
    val durationMs: Long
)
