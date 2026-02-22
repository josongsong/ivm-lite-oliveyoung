package com.oliveyoung.ivmlite.pkg.backfill.adapters

import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillJobRepositoryPort
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillStats
import com.oliveyoung.ivmlite.shared.adapters.exposed.BackfillJobsTable
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Exposed 기반 BackfillJob Repository (PostgreSQL)
 *
 * upsert: ID 기반 insert or update
 */
class ExposedBackfillJobRepository(
    private val database: Database
) : BackfillJobRepositoryPort {

    private val logger = LoggerFactory.getLogger(ExposedBackfillJobRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(job: BackfillJob): Result<BackfillJob> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val exists = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.id eq job.id }
                        .count() > 0

                    if (exists) {
                        BackfillJobsTable.update({ BackfillJobsTable.id eq job.id }) {
                            it[name] = job.name
                            it[description] = job.description
                            it[type] = job.type.name
                            it[scope] = serializeScope(job.scope)
                            it[status] = job.status.name
                            it[priority] = job.priority
                            it[config] = serializeConfig(job.config)
                            it[progress] = serializeProgress(job.progress)
                            it[createdBy] = job.createdBy
                            it[createdAt] = job.createdAt.atOffset(ZoneOffset.UTC)
                            it[startedAt] = job.startedAt?.atOffset(ZoneOffset.UTC)
                            it[completedAt] = job.completedAt?.atOffset(ZoneOffset.UTC)
                            it[failureReason] = job.failureReason
                            it[dryRunResult] = job.dryRunResult?.let { dr -> serializeDryRunResult(dr) }
                        }
                    } else {
                        BackfillJobsTable.insert {
                            it[id] = job.id
                            it[name] = job.name
                            it[description] = job.description
                            it[type] = job.type.name
                            it[scope] = serializeScope(job.scope)
                            it[status] = job.status.name
                            it[priority] = job.priority
                            it[config] = serializeConfig(job.config)
                            it[progress] = serializeProgress(job.progress)
                            it[createdBy] = job.createdBy
                            it[createdAt] = job.createdAt.atOffset(ZoneOffset.UTC)
                            it[startedAt] = job.startedAt?.atOffset(ZoneOffset.UTC)
                            it[completedAt] = job.completedAt?.atOffset(ZoneOffset.UTC)
                            it[failureReason] = job.failureReason
                            it[dryRunResult] = job.dryRunResult?.let { dr -> serializeDryRunResult(dr) }
                        }
                    }
                }
                Result.Ok(job)
            } catch (e: Exception) {
                logger.error("Failed to save backfill job: ${job.id}", e)
                Result.Err(DomainError.StorageError("Failed to save backfill job: ${e.message}"))
            }
        }

    override suspend fun findById(id: UUID): Result<BackfillJob?> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val row = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.id eq id }
                        .firstOrNull()
                    Result.Ok(row?.let { mapToBackfillJob(it) })
                }
            } catch (e: Exception) {
                logger.error("Failed to find backfill job: $id", e)
                Result.Err(DomainError.StorageError("Failed to find backfill job: ${e.message}"))
            }
        }

    override suspend fun findByStatus(status: BackfillStatus, limit: Int): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.status eq status.name }
                        .orderBy(BackfillJobsTable.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map { mapToBackfillJob(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find backfill jobs by status: $status", e)
                Result.Err(DomainError.StorageError("Failed to find backfill jobs: ${e.message}"))
            }
        }

    override suspend fun findActive(): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val activeStatuses = listOf(
                        BackfillStatus.RUNNING.name,
                        BackfillStatus.PAUSED.name,
                        BackfillStatus.DRY_RUN.name
                    )
                    val records = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.status inList activeStatuses }
                        .orderBy(BackfillJobsTable.priority, SortOrder.ASC)
                        .map { mapToBackfillJob(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find active backfill jobs", e)
                Result.Err(DomainError.StorageError("Failed to find active backfill jobs: ${e.message}"))
            }
        }

    override suspend fun findPending(limit: Int): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val now = OffsetDateTime.now(ZoneOffset.UTC)
                    val records = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.status eq BackfillStatus.PENDING.name }
                        .orderBy(BackfillJobsTable.priority, SortOrder.ASC)
                        .limit(limit)
                        .map { mapToBackfillJob(it) }
                        .filter { it.config.scheduledAt == null || it.config.scheduledAt.isBefore(Instant.now()) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find pending backfill jobs", e)
                Result.Err(DomainError.StorageError("Failed to find pending backfill jobs: ${e.message}"))
            }
        }

    override suspend fun findScheduledBefore(time: Instant): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.status eq BackfillStatus.PENDING.name }
                        .orderBy(BackfillJobsTable.createdAt, SortOrder.ASC)
                        .map { mapToBackfillJob(it) }
                        .filter { it.config.scheduledAt != null && it.config.scheduledAt.isBefore(time) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find scheduled backfill jobs", e)
                Result.Err(DomainError.StorageError("Failed to find scheduled backfill jobs: ${e.message}"))
            }
        }

    override suspend fun findRecent(limit: Int): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = BackfillJobsTable.selectAll()
                        .orderBy(BackfillJobsTable.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map { mapToBackfillJob(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find recent backfill jobs", e)
                Result.Err(DomainError.StorageError("Failed to find recent backfill jobs: ${e.message}"))
            }
        }

    override suspend fun findByType(type: BackfillType, limit: Int): Result<List<BackfillJob>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = BackfillJobsTable.selectAll()
                        .where { BackfillJobsTable.type eq type.name }
                        .orderBy(BackfillJobsTable.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map { mapToBackfillJob(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find backfill jobs by type: $type", e)
                Result.Err(DomainError.StorageError("Failed to find backfill jobs: ${e.message}"))
            }
        }

    override suspend fun getStats(): Result<BackfillStats> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val allJobs = BackfillJobsTable.selectAll().map { mapToBackfillJob(it) }
                    val today = Instant.now().truncatedTo(ChronoUnit.DAYS)

                    val stats = BackfillStats(
                        totalJobs = allJobs.size,
                        activeJobs = allJobs.count { it.status.isActive() },
                        pendingJobs = allJobs.count { it.status == BackfillStatus.PENDING },
                        completedToday = allJobs.count {
                            it.status == BackfillStatus.COMPLETED &&
                                it.completedAt?.isAfter(today) == true
                        },
                        failedToday = allJobs.count {
                            it.status == BackfillStatus.FAILED &&
                                it.completedAt?.isAfter(today) == true
                        },
                        byType = allJobs.groupBy { it.type }.mapValues { it.value.size },
                        byStatus = allJobs.groupBy { it.status }.mapValues { it.value.size }
                    )
                    Result.Ok(stats)
                }
            } catch (e: Exception) {
                logger.error("Failed to get backfill stats", e)
                Result.Err(DomainError.StorageError("Failed to get backfill stats: ${e.message}"))
            }
        }

    override suspend fun deleteCompletedBefore(before: Instant): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val terminalStatuses = listOf(
                        BackfillStatus.COMPLETED.name,
                        BackfillStatus.FAILED.name,
                        BackfillStatus.CANCELLED.name
                    )
                    val count = BackfillJobsTable.deleteWhere {
                        BackfillJobsTable.status inList terminalStatuses and
                            (BackfillJobsTable.completedAt less before.atOffset(ZoneOffset.UTC))
                    }
                    Result.Ok(count)
                }
            } catch (e: Exception) {
                logger.error("Failed to delete completed backfill jobs", e)
                Result.Err(DomainError.StorageError("Failed to delete completed backfill jobs: ${e.message}"))
            }
        }

    // ===== Mapping =====

    private fun mapToBackfillJob(row: ResultRow): BackfillJob {
        return BackfillJob(
            id = row[BackfillJobsTable.id],
            name = row[BackfillJobsTable.name],
            description = row[BackfillJobsTable.description],
            type = BackfillType.valueOf(row[BackfillJobsTable.type]),
            scope = deserializeScope(row[BackfillJobsTable.scope]),
            status = BackfillStatus.valueOf(row[BackfillJobsTable.status]),
            priority = row[BackfillJobsTable.priority],
            config = deserializeConfig(row[BackfillJobsTable.config]),
            progress = deserializeProgress(row[BackfillJobsTable.progress]),
            createdBy = row[BackfillJobsTable.createdBy],
            createdAt = row[BackfillJobsTable.createdAt].toInstant(),
            startedAt = row[BackfillJobsTable.startedAt]?.toInstant(),
            completedAt = row[BackfillJobsTable.completedAt]?.toInstant(),
            failureReason = row[BackfillJobsTable.failureReason],
            dryRunResult = row[BackfillJobsTable.dryRunResult]?.let { deserializeDryRunResult(it) },
        )
    }

    // ===== JSON Serialization =====

    private fun serializeScope(scope: BackfillScope): String =
        buildJsonObject {
            scope.tenantIds?.let { put("tenantIds", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.entityTypes?.let { put("entityTypes", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.entityKeys?.let { put("entityKeys", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.entityKeyPattern?.let { put("entityKeyPattern", it) }
            scope.fromTime?.let { put("fromTime", it.toString()) }
            scope.toTime?.let { put("toTime", it.toString()) }
            scope.sliceTypes?.let { put("sliceTypes", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.viewIds?.let { put("viewIds", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.schemaIds?.let { put("schemaIds", buildJsonArray { it.forEach { v -> add(v) } }) }
            scope.minVersion?.let { put("minVersion", it) }
            scope.maxVersion?.let { put("maxVersion", it) }
        }.toString()

    private fun deserializeScope(jsonStr: String): BackfillScope {
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        return BackfillScope(
            tenantIds = obj["tenantIds"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            entityTypes = obj["entityTypes"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            entityKeys = obj["entityKeys"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            entityKeyPattern = obj["entityKeyPattern"]?.jsonPrimitive?.contentOrNull,
            fromTime = obj["fromTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) },
            toTime = obj["toTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) },
            sliceTypes = obj["sliceTypes"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            viewIds = obj["viewIds"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            schemaIds = obj["schemaIds"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
            minVersion = obj["minVersion"]?.jsonPrimitive?.longOrNull,
            maxVersion = obj["maxVersion"]?.jsonPrimitive?.longOrNull,
        )
    }

    private fun serializeConfig(config: BackfillConfig): String =
        buildJsonObject {
            put("batchSize", config.batchSize)
            put("concurrency", config.concurrency)
            put("continueOnError", config.continueOnError)
            put("maxRetries", config.maxRetries)
            put("retryDelayMs", config.retryDelayMs)
            put("batchDelayMs", config.batchDelayMs)
            put("dryRun", config.dryRun)
            config.scheduledAt?.let { put("scheduledAt", it.toString()) }
        }.toString()

    private fun deserializeConfig(jsonStr: String): BackfillConfig {
        if (jsonStr.isBlank() || jsonStr == "{}") return BackfillConfig()
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        return BackfillConfig(
            batchSize = obj["batchSize"]?.jsonPrimitive?.intOrNull ?: 100,
            concurrency = obj["concurrency"]?.jsonPrimitive?.intOrNull ?: 4,
            continueOnError = obj["continueOnError"]?.jsonPrimitive?.booleanOrNull ?: true,
            maxRetries = obj["maxRetries"]?.jsonPrimitive?.intOrNull ?: 3,
            retryDelayMs = obj["retryDelayMs"]?.jsonPrimitive?.longOrNull ?: 1000,
            batchDelayMs = obj["batchDelayMs"]?.jsonPrimitive?.longOrNull ?: 0,
            dryRun = obj["dryRun"]?.jsonPrimitive?.booleanOrNull ?: false,
            scheduledAt = obj["scheduledAt"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) },
        )
    }

    private fun serializeProgress(progress: BackfillProgress): String =
        buildJsonObject {
            put("total", progress.total)
            put("processed", progress.processed)
            put("succeeded", progress.succeeded)
            put("failed", progress.failed)
            put("skipped", progress.skipped)
            progress.currentEntity?.let { put("currentEntity", it) }
            progress.startedAt?.let { put("startedAt", it.toString()) }
            put("lastUpdatedAt", progress.lastUpdatedAt.toString())
            put("throughput", progress.throughput)
            progress.estimatedRemaining?.let { put("estimatedRemainingMs", it.toMillis()) }
            if (progress.recentErrors.isNotEmpty()) {
                put("recentErrors", buildJsonArray { progress.recentErrors.forEach { add(it) } })
            }
        }.toString()

    private fun deserializeProgress(jsonStr: String): BackfillProgress {
        if (jsonStr.isBlank() || jsonStr == "{}") return BackfillProgress.empty()
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        return BackfillProgress(
            total = obj["total"]?.jsonPrimitive?.longOrNull ?: 0,
            processed = obj["processed"]?.jsonPrimitive?.longOrNull ?: 0,
            succeeded = obj["succeeded"]?.jsonPrimitive?.longOrNull ?: 0,
            failed = obj["failed"]?.jsonPrimitive?.longOrNull ?: 0,
            skipped = obj["skipped"]?.jsonPrimitive?.longOrNull ?: 0,
            currentEntity = obj["currentEntity"]?.jsonPrimitive?.contentOrNull,
            startedAt = obj["startedAt"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) },
            lastUpdatedAt = obj["lastUpdatedAt"]?.jsonPrimitive?.contentOrNull
                ?.let { Instant.parse(it) } ?: Instant.now(),
            throughput = obj["throughput"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            estimatedRemaining = obj["estimatedRemainingMs"]?.jsonPrimitive?.longOrNull
                ?.let { Duration.ofMillis(it) },
            recentErrors = obj["recentErrors"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }

    private fun serializeDryRunResult(result: DryRunResult): String =
        buildJsonObject {
            put("estimatedCount", result.estimatedCount)
            put("countByType", buildJsonObject {
                result.countByType.forEach { (k, v) -> put(k, v) }
            })
            result.estimatedDuration?.let { put("estimatedDurationMs", it.toMillis()) }
            put("sampleEntities", buildJsonArray { result.sampleEntities.forEach { add(it) } })
            if (result.warnings.isNotEmpty()) {
                put("warnings", buildJsonArray { result.warnings.forEach { add(it) } })
            }
        }.toString()

    private fun deserializeDryRunResult(jsonStr: String): DryRunResult? {
        if (jsonStr.isBlank() || jsonStr == "{}") return null
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        return DryRunResult(
            estimatedCount = obj["estimatedCount"]?.jsonPrimitive?.longOrNull ?: 0,
            countByType = obj["countByType"]?.jsonObject?.entries?.associate { (k, v) ->
                k to (v.jsonPrimitive.longOrNull ?: 0)
            } ?: emptyMap(),
            estimatedDuration = obj["estimatedDurationMs"]?.jsonPrimitive?.longOrNull
                ?.let { Duration.ofMillis(it) },
            sampleEntities = obj["sampleEntities"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList(),
            warnings = obj["warnings"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }
}
