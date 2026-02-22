package com.oliveyoung.ivmlite.pkg.backfill.adapters

import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import com.oliveyoung.ivmlite.pkg.backfill.ports.*
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Default Backfill Executor
 *
 * RawData/Slice는 DynamoDB로 이전됨. ExplorerRepositoryPort + RawDataRepositoryPort 사용.
 * RAW_TO_SLICE, FULL_REPROCESS 타입 지원.
 */
class DefaultBackfillExecutor(
    private val explorerRepo: ExplorerRepositoryPort,
    private val rawDataRepo: RawDataRepositoryPort,
    private val slicingWorkflow: SlicingWorkflow
) : BackfillExecutorPort {

    private val logger = LoggerFactory.getLogger(DefaultBackfillExecutor::class.java)

    override val supportedTypes = setOf(
        BackfillType.RAW_TO_SLICE,
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
            withContext(Dispatchers.IO) {
                // DynamoDB 미지원 필터 검증
                if (scope.entityKeyPattern != null || scope.fromTime != null || scope.toTime != null) {
                    return@withContext Result.Err(
                        com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ValidationError(
                            field = "scope",
                            msg = "DynamoDB Backfill은 entityKeyPattern, fromTime, toTime 필터를 지원하지 않습니다."
                        )
                    )
                }

                val entityKeysList = mutableListOf<String>()
                val countByType = mutableMapOf<String, Long>()

                when {
                    // 1) entityKeys 직접 지정
                    !scope.entityKeys.isNullOrEmpty() -> {
                        entityKeysList.addAll(scope.entityKeys)
                        entityKeysList.forEach { key ->
                            val schemaId = parseSchemaFromEntityKey(key)
                            countByType[schemaId] = (countByType[schemaId] ?: 0L) + 1
                        }
                    }
                    // 2) tenantIds 기반 (ExplorerRepositoryPort.listRawData)
                    !scope.tenantIds.isNullOrEmpty() -> {
                        val tenantIds = scope.tenantIds
                        val entityTypes = scope.entityTypes

                        for (tenantId in tenantIds) {
                            var cursor: String? = null
                            do {
                                val result = Result.fromEither(
                                    explorerRepo.listRawData(
                                        tenantId = TenantId(tenantId),
                                        entityType = null,
                                        limit = 500,
                                        cursor = cursor
                                    )
                                )
                                when (result) {
                                    is Result.Ok -> {
                                        val items = result.value.items
                                        items.forEach { item ->
                                            val matchesEntityType = entityTypes == null ||
                                                entityTypes.any { item.entityKey.startsWith("$it#") }
                                            val matchesSchema = scope.schemaIds == null ||
                                                scope.schemaIds.any { item.schemaId.startsWith(it) }
                                            if (matchesEntityType && matchesSchema) {
                                                entityKeysList.add(item.entityKey)
                                                val schemaId = item.schemaId.ifBlank { "unknown" }
                                                countByType[schemaId] = (countByType[schemaId] ?: 0L) + 1
                                            }
                                        }
                                        cursor = result.value.nextCursor
                                    }
                                    is Result.Err -> return@withContext result
                                }
                            } while (cursor != null)
                        }
                    }
                    else -> {
                        return@withContext Result.Err(
                            com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ValidationError(
                                field = "scope",
                                msg = "entityKeys 또는 tenantIds 중 하나는 필수입니다."
                            )
                        )
                    }
                }

                Result.Ok(ScopeResolution(
                    totalCount = entityKeysList.size.toLong(),
                    entityKeys = entityKeysList.asSequence(),
                    countByType = countByType
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to resolve scope", e)
            Result.Err(
                com.oliveyoung.ivmlite.shared.domain.errors.DomainError.InternalError(
                    "Failed to resolve scope: ${e.message}"
                )
            )
        }
    }

    private fun parseSchemaFromEntityKey(entityKey: String): String {
        return try {
            val parts = entityKey.split('#')
            if (parts.size >= 1) parts[0] else "unknown"
        } catch (_: Exception) {
            "unknown"
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
        // EntityKey 포맷: {ENTITY_TYPE}#{tenantId}#{entityId}
        val tenantId = try {
            val parts = entityKey.split('#')
            require(parts.size >= 3) { "Invalid entityKey: $entityKey" }
            parts[1]
        } catch (_: Exception) {
            return Result.Ok(EntityProcessResult(entityKey, false, "Invalid entityKey format: $entityKey"))
        }

        // RawDataRepositoryPort(DynamoDB)에서 최신 버전 조회
        val latestResult = rawDataRepo.getLatest(TenantId(tenantId), EntityKey(entityKey))
        val record = when (latestResult) {
            is Result.Ok -> latestResult.value
            is Result.Err -> return Result.Ok(
                EntityProcessResult(entityKey, false, "No raw data found: ${latestResult.error}")
            )
        }

        val latestVersion = record.version

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

    private fun buildWarnings(totalCount: Long): List<String> {
        val warnings = mutableListOf<String>()

        if (totalCount > 10_000) {
            warnings.add("대량 재처리 (${totalCount}건): 시스템 부하가 발생할 수 있습니다.")
        }
        if (totalCount > 100_000) {
            warnings.add("⚠️ 매우 큰 규모: 피크 시간대를 피해 실행하세요.")
        }

        return warnings
    }
}
