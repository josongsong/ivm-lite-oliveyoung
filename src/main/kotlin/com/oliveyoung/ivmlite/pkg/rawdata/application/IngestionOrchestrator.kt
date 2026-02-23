package com.oliveyoung.ivmlite.pkg.rawdata.application

import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkRuleStatus
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPreflightPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.TransactionPort
import com.oliveyoung.ivmlite.sinks.contract.SinkPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * IngestionOrchestrator - 데이터 처리 오케스트레이터 (Application Layer)
 *
 * 🎯 책임:
 * 1. 트랜잭션 관리 (TransactionPort)
 * 2. Workflow 실행
 * 3. SinkEvent 발행 (DynamoDB Streams → Lambda) 또는 inProcessSink 시 SinkPlugin 직접 호출
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
    private val sinkRuleRegistry: SinkRuleRegistryPort,
    private val sinkPreflight: SinkPreflightPort,
    private val pluginRegistry: SinkPluginRegistryPort? = null,
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
            // 1. Sink Preflight (skipSink면 스킵)
            val entityType = command.entityKey.value.substringBefore(":")
            val sinkTargets = if (command.skipSink) emptyList() else resolveSinkTargets(entityType)
            val sinkPending = sinkTargets.isNotEmpty()

            if (sinkPending) {
                when (val preflight = sinkPreflight.validate(sinkTargets)) {
                    is Result.Err -> {
                        logger.warn("Sink preflight failed: {}", preflight.error.message)
                        return@execute Result.Err(preflight.error)
                    }
                    is Result.Ok -> { /* 계속 */ }
                }
            }

            // 2. Workflow 실행 (RawData → Slicing → View)
            val workflowResult = when (val result = workflow.execute(command)) {
                is Result.Ok -> result.value
                is Result.Err -> return@execute Result.Err(result.error)
            }

            // 3. Sink 처리 (skipSink 또는 SinkRule 미매칭 시 스킵)
            if (sinkPending && !command.skipSink) {
                if (command.inProcessSink) {
                    if (pluginRegistry == null) {
                        return@execute Result.Err(
                            DomainError.ValidationError(
                                "inProcessSink",
                                "inProcessSink requires SinkPluginRegistry (set OPENSEARCH_ENDPOINT, S3_BUCKET, etc.)"
                            )
                        )
                    }
                    // 같은 세션에서 SinkPlugin 직접 호출 (Lambda/DynamoDB 미사용)
                    when (val result = executeInProcessSink(workflowResult.views, sinkTargets, command.jobId)) {
                        is Result.Err -> return@execute result
                        is Result.Ok -> logger.debug("In-process sink completed: ${workflowResult.views.size} views")
                    }
                } else {
                    // DynamoDB에 저장 → Streams → Lambda
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
                    when (val result = sinkEventRepo.putAll(sinkEvents)) {
                        is Result.Err -> return@execute Result.Err(result.error)
                        is Result.Ok -> logger.debug("SinkEvents published: ${sinkEvents.size}")
                    }
                }
            } else {
                logger.debug("No sink targets resolved, skipping SinkEvent creation")
            }

            // 4. 결과 반환 (skipSink면 sinkPending=false)
            val duration = java.time.Duration.between(startTime, Instant.now())
            Result.Ok(
                IngestionResult(
                    tenantId = command.tenantId.value,
                    entityKey = command.entityKey.value,
                    version = workflowResult.rawData.version,
                    sliceCount = workflowResult.slices.size,
                    viewCount = workflowResult.views.size,
                    sinkPending = sinkPending && !command.skipSink,
                    durationMs = duration.toMillis()
                )
            )
        }
    }

    private suspend fun resolveSinkTargets(entityType: String): List<String> =
        when (val result = sinkRuleRegistry.findByEntityType(entityType)) {
            is Result.Ok -> result.value
                .filter { rule -> rule.status == SinkRuleStatus.ACTIVE }
                .map { rule -> rule.target.type.toPluginId() }
                .distinct()
            is Result.Err -> {
                logger.warn("Failed to resolve sink targets: {}", result.error)
                emptyList()
            }
        }

    /**
     * 같은 세션에서 SinkPlugin 직접 호출 (Lambda/DynamoDB 미사용)
     */
    private suspend fun executeInProcessSink(
        views: List<com.oliveyoung.ivmlite.pkg.views.domain.ViewRecord>,
        sinkTargets: List<String>,
        jobId: String?,
    ): Result<Unit> {
        val json = Json { ignoreUnknownKeys = true }
        for (view in views) {
            val viewData = try {
                json.parseToJsonElement(view.data) as JsonObject
            } catch (e: kotlinx.serialization.SerializationException) {
                return Result.Err(DomainError.ValidationError("viewData", "Invalid view JSON: ${e.message}"))
            }
            val payloadDigest = SinkPayload.computePayloadDigest(viewData)
            val correlationId = "inprocess-${view.tenantId.value}-${view.entityKey.value}-${view.version}-${view.viewType}"
            val idempotencyKey = SinkPayload.generateIdempotencyKey(
                view.tenantId.value, view.entityKey.value, view.version, view.viewType, payloadDigest
            )
            val sinkPayload = SinkPayload.V1(
                correlationId = correlationId,
                timestamp = Instant.now().toString(),
                idempotencyKey = idempotencyKey,
                orderingKey = SinkPayload.generateOrderingKey(view.tenantId.value, view.entityKey.value),
                payloadDigest = payloadDigest,
                tenantId = view.tenantId.value,
                entityKey = view.entityKey.value,
                entityVersion = view.version,
                viewType = view.viewType,
                viewData = viewData,
                metadata = jobId?.let { mapOf("jobId" to it) } ?: emptyMap(),
            )
            for (target in sinkTargets) {
                val plugin = pluginRegistry!!.resolve(target)
                if (plugin == null) {
                    logger.warn("In-process sink: plugin not found for target: {}", target)
                    continue
                }
                plugin.execute(sinkPayload).fold(
                    { error ->
                        logger.error("In-process sink failed: target={}, error={}", target, error.message)
                        return Result.Err(DomainError.StorageError("Sink failed ($target): ${error.message}"))
                    },
                    { /* success */ }
                )
            }
        }
        return Result.Ok(Unit)
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
