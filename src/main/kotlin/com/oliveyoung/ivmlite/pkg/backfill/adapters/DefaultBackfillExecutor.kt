package com.oliveyoung.ivmlite.pkg.backfill.adapters

import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import com.oliveyoung.ivmlite.pkg.backfill.ports.*
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Default Backfill Executor
 * 
 * RAW_TO_SLICE, DLQ_REPLAY, FAILED_REPLAY 타입을 지원한다.
 */
class DefaultBackfillExecutor(
    private val dsl: DSLContext,
    private val rawDataRepo: RawDataRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val slicingWorkflow: SlicingWorkflow
) : BackfillExecutorPort {
    
    private val logger = LoggerFactory.getLogger(DefaultBackfillExecutor::class.java)
    
    override val supportedTypes = setOf(
        BackfillType.RAW_TO_SLICE,
        BackfillType.DLQ_REPLAY,
        BackfillType.FAILED_REPLAY,
        BackfillType.FULL_REPROCESS
    )
    
    override suspend fun dryRun(scope: BackfillScope): Result<DryRunResult> {
        return try {
            val resolution = resolveScope(scope)
            when (resolution) {
                is Result.Ok -> {
                    val res = resolution.value
                    
                    // 샘플 추출
                    val samples = res.entityKeys.take(10).toList()
                    
                    // 예상 시간 계산 (100 entities/sec 가정)
                    val estimatedSeconds = res.totalCount / 100
                    
                    Result.Ok(DryRunResult(
                        estimatedCount = res.totalCount,
                        countByType = res.countByType,
                        estimatedDuration = Duration.ofSeconds(estimatedSeconds),
                        sampleEntities = samples,
                        warnings = buildWarnings(res.totalCount)
                    ))
                }
                is Result.Err -> resolution
            }
        } catch (e: Exception) {
            logger.error("Dry run failed", e)
            Result.Err(
                com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InternalError(
                    "Dry run failed: ${e.message}"
                )
            )
        }
    }
    
    override suspend fun resolveScope(scope: BackfillScope): Result<ScopeResolution> {
        return try {
            // SQL 조건 빌드
            var query = dsl.select(DSL.field("entity_key"), DSL.field("schema_id"))
                .from(DSL.table("raw_data"))
            
            val conditions = mutableListOf<org.jooq.Condition>()
            
            scope.tenantIds?.let { tenants ->
                conditions.add(DSL.field("tenant_id").`in`(tenants))
            }
            
            scope.entityTypes?.let { types ->
                // entity_key에서 타입 추출 (예: "PRODUCT:sku123" → "PRODUCT")
                val typeConditions = types.map { type ->
                    DSL.field("entity_key").like("$type:%")
                }
                conditions.add(DSL.or(typeConditions))
            }
            
            scope.entityKeys?.let { keys ->
                conditions.add(DSL.field("entity_key").`in`(keys))
            }
            
            scope.entityKeyPattern?.let { pattern ->
                conditions.add(DSL.field("entity_key").like(pattern))
            }
            
            scope.fromTime?.let { from ->
                conditions.add(DSL.field("created_at").ge(java.sql.Timestamp.from(from)))
            }
            
            scope.toTime?.let { to ->
                conditions.add(DSL.field("created_at").le(java.sql.Timestamp.from(to)))
            }
            
            scope.schemaIds?.let { schemas ->
                conditions.add(DSL.field("schema_id").`in`(schemas))
            }
            
            val finalQuery = if (conditions.isNotEmpty()) {
                query.where(DSL.and(conditions))
            } else {
                query
            }
            
            // 카운트 조회
            val countQuery = dsl.selectCount()
                .from(DSL.table("raw_data"))
            if (conditions.isNotEmpty()) {
                countQuery.where(DSL.and(conditions))
            }
            val totalCount = countQuery.fetchOne(0, Long::class.java) ?: 0L
            
            // 엔티티 키 시퀀스 (Lazy)
            val entityKeys = sequence {
                val results = finalQuery.orderBy(DSL.field("entity_key")).fetch()
                for (record in results) {
                    yield(record.get("entity_key", String::class.java) ?: continue)
                }
            }
            
            // 타입별 카운트
            val byType = dsl.select(
                DSL.field("schema_id"),
                DSL.count()
            )
                .from(DSL.table("raw_data"))
                .apply { if (conditions.isNotEmpty()) where(DSL.and(conditions)) }
                .groupBy(DSL.field("schema_id"))
                .fetch()
                .associate { 
                    (it.get(0, String::class.java) ?: "unknown") to (it.get(1, Long::class.java) ?: 0L)
                }
            
            Result.Ok(ScopeResolution(
                totalCount = totalCount,
                entityKeys = entityKeys,
                countByType = byType
            ))
        } catch (e: Exception) {
            logger.error("Failed to resolve scope", e)
            Result.Err(
                com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InternalError(
                    "Failed to resolve scope: ${e.message}"
                )
            )
        }
    }
    
    override suspend fun processEntity(
        entityKey: String,
        type: BackfillType,
        config: BackfillConfig
    ): Result<EntityProcessResult> {
        val startTime = System.currentTimeMillis()
        
        return try {
            when (type) {
                BackfillType.RAW_TO_SLICE, BackfillType.FULL_REPROCESS -> {
                    reprocessRawToSlice(entityKey)
                }
                BackfillType.DLQ_REPLAY -> {
                    replayDlqEntry(entityKey)
                }
                BackfillType.FAILED_REPLAY -> {
                    replayFailedEntry(entityKey)
                }
                else -> {
                    Result.Err(
                        com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ValidationError(
                            field = "type",
                            msg = "Unsupported backfill type: $type"
                        )
                    )
                }
            }.let { result ->
                when (result) {
                    is Result.Ok -> {
                        Result.Ok(result.value.copy(
                            durationMs = System.currentTimeMillis() - startTime
                        ))
                    }
                    is Result.Err -> result
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process entity: {}", entityKey, e)
            Result.Ok(EntityProcessResult(
                entityKey = entityKey,
                success = false,
                message = e.message,
                durationMs = System.currentTimeMillis() - startTime
            ))
        }
    }
    
    override suspend fun processBatch(
        entityKeys: List<String>,
        type: BackfillType,
        config: BackfillConfig
    ): Result<BatchProcessResult> {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<EntityProcessResult>()
        var succeeded = 0
        var failed = 0
        var skipped = 0
        
        for (entityKey in entityKeys) {
            val result = processEntity(entityKey, type, config)
            when (result) {
                is Result.Ok -> {
                    results.add(result.value)
                    if (result.value.success) {
                        succeeded++
                    } else {
                        failed++
                        if (!config.continueOnError) break
                    }
                }
                is Result.Err -> {
                    failed++
                    if (!config.continueOnError) break
                }
            }
        }
        
        return Result.Ok(BatchProcessResult(
            total = entityKeys.size,
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
            results = results,
            durationMs = System.currentTimeMillis() - startTime
        ))
    }
    
    // ==================== Internal ====================
    
    private suspend fun reprocessRawToSlice(entityKey: String): Result<EntityProcessResult> {
        // 최신 버전 조회
        val latestVersion = dsl.select(DSL.max(DSL.field("version")))
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq(entityKey))
            .fetchOne(0, Long::class.java) ?: return Result.Ok(
                EntityProcessResult(entityKey, false, "No raw data found")
            )
        
        // tenant_id 조회
        val tenantId = dsl.select(DSL.field("tenant_id"))
            .from(DSL.table("raw_data"))
            .where(DSL.field("entity_key").eq(entityKey))
            .and(DSL.field("version").eq(latestVersion))
            .fetchOne(0, String::class.java) ?: "default"
        
        // SlicingWorkflow 호출
        val result = slicingWorkflow.executeAuto(
            tenantId = TenantId(tenantId),
            entityKey = EntityKey(entityKey),
            version = latestVersion
        )
        
        return when (result) {
            is Result.Ok -> {
                Result.Ok(EntityProcessResult(
                    entityKey = entityKey,
                    success = true,
                    slicesCreated = result.value.size
                ))
            }
            is Result.Err -> {
                Result.Ok(EntityProcessResult(
                    entityKey = entityKey,
                    success = false,
                    message = result.error.toString()
                ))
            }
        }
    }
    
    private suspend fun replayDlqEntry(entityKey: String): Result<EntityProcessResult> {
        // DLQ에서 해당 엔티티 찾기
        val dlqEntries = when (val r = outboxRepo.findDlq(100)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Ok(
                EntityProcessResult(entityKey, false, "Failed to find DLQ entries")
            )
        }
        
        val entry = dlqEntries.find { it.aggregateId.contains(entityKey) }
            ?: return Result.Ok(
                EntityProcessResult(entityKey, false, "No DLQ entry found")
            )
        
        // Replay
        return when (val r = outboxRepo.replayFromDlq(entry.id)) {
            is Result.Ok -> {
                Result.Ok(EntityProcessResult(
                    entityKey = entityKey,
                    success = r.value,
                    outboxEntriesCreated = if (r.value) 1 else 0
                ))
            }
            is Result.Err -> {
                Result.Ok(EntityProcessResult(
                    entityKey = entityKey,
                    success = false,
                    message = r.error.toString()
                ))
            }
        }
    }
    
    private suspend fun replayFailedEntry(entityKey: String): Result<EntityProcessResult> {
        // FAILED 상태 엔트리 찾기
        val failedEntries = dsl.select()
            .from(DSL.table("outbox"))
            .where(DSL.field("status").eq("FAILED"))
            .and(DSL.field("aggregateid").like("%$entityKey%"))
            .fetch()
        
        if (failedEntries.isEmpty()) {
            return Result.Ok(
                EntityProcessResult(entityKey, false, "No failed entry found")
            )
        }
        
        var replayed = 0
        for (record in failedEntries) {
            val id = record.get("id", java.util.UUID::class.java) ?: continue
            when (outboxRepo.resetToPending(id)) {
                is Result.Ok -> replayed++
                is Result.Err -> {}
            }
        }
        
        return Result.Ok(EntityProcessResult(
            entityKey = entityKey,
            success = replayed > 0,
            outboxEntriesCreated = replayed
        ))
    }
    
    private fun buildWarnings(totalCount: Long): List<String> {
        val warnings = mutableListOf<String>()
        
        if (totalCount > 10000) {
            warnings.add("대량 재처리 (${totalCount}건): 시스템 부하가 발생할 수 있습니다.")
        }
        if (totalCount > 100000) {
            warnings.add("⚠️ 매우 큰 규모: 피크 시간대를 피해 실행하세요.")
        }
        
        return warnings
    }
}
