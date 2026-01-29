package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.pkg.backfill.application.BackfillService
import com.oliveyoung.ivmlite.pkg.backfill.application.CreateBackfillRequest
import com.oliveyoung.ivmlite.pkg.backfill.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID

/**
 * Backfill Routes (재처리 작업 관리 API)
 *
 * GET /backfill: Job 목록
 * POST /backfill: Job 생성
 * GET /backfill/{id}: Job 상세
 * POST /backfill/{id}/dry-run: Dry Run
 * POST /backfill/{id}/start: 시작
 * POST /backfill/{id}/pause: 일시정지
 * POST /backfill/{id}/resume: 재개
 * POST /backfill/{id}/cancel: 취소
 * POST /backfill/{id}/retry: 재시도
 * GET /backfill/stats: 통계
 */
fun Route.backfillRoutes() {
    val backfillService by inject<BackfillService>()
    
    /**
     * GET /backfill
     * Job 목록 조회
     */
    get("/backfill") {
        try {
            val status = call.request.queryParameters["status"]
                ?.let { BackfillStatus.valueOf(it.uppercase()) }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            
            val jobs = if (status != null) {
                // TODO: status 필터 지원
                backfillService.getRecentJobs(limit)
            } else {
                backfillService.getRecentJobs(limit)
            }
            
            call.respond(HttpStatusCode.OK, BackfillJobListResponse(
                jobs = jobs.map { it.toDto() },
                total = jobs.size
            ))
        } catch (e: Exception) {
            call.application.log.error("Failed to get backfill jobs", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "BACKFILL_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill
     * Job 생성
     */
    post("/backfill") {
        try {
            val request = call.receive<CreateBackfillRequestDto>()
            
            // Scope 파싱
            val scope = BackfillScope(
                tenantIds = request.scope.tenantIds?.toSet(),
                entityTypes = request.scope.entityTypes?.toSet(),
                entityKeys = request.scope.entityKeys?.toSet(),
                entityKeyPattern = request.scope.entityKeyPattern,
                fromTime = request.scope.fromTime?.let { Instant.parse(it) },
                toTime = request.scope.toTime?.let { Instant.parse(it) },
                sliceTypes = request.scope.sliceTypes?.toSet(),
                schemaIds = request.scope.schemaIds?.toSet()
            )
            
            val config = BackfillConfig(
                batchSize = request.config?.batchSize ?: 100,
                concurrency = request.config?.concurrency ?: 4,
                continueOnError = request.config?.continueOnError ?: true,
                batchDelayMs = request.config?.batchDelayMs ?: 0,
                dryRun = request.config?.dryRun ?: false
            )
            
            val createRequest = CreateBackfillRequest(
                name = request.name,
                type = BackfillType.valueOf(request.type.uppercase()),
                scope = scope,
                createdBy = request.createdBy ?: "admin",
                config = config,
                description = request.description ?: "",
                priority = request.priority ?: 5
            )
            
            when (val result = backfillService.createJob(createRequest)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.Created, result.value.toDto())
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "CREATE_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to create backfill job", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "CREATE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /backfill/active
     * 활성 Job 목록
     */
    get("/backfill/active") {
        try {
            val activeJobs = backfillService.getActiveJobs()
            call.respond(HttpStatusCode.OK, BackfillJobListResponse(
                jobs = activeJobs.map { it.toDto() },
                total = activeJobs.size
            ))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "BACKFILL_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /backfill/{id}
     * Job 상세 조회
     */
    get("/backfill/{id}") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@get
            }
            
            val job = backfillService.getJob(id)
            if (job != null) {
                call.respond(HttpStatusCode.OK, job.toDto())
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError(code = "NOT_FOUND", message = "Job not found"))
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "INVALID_ID", message = "Invalid job ID format")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/dry-run
     * Dry Run 실행
     */
    post("/backfill/{id}/dry-run") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.dryRun(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "DRY_RUN_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to execute dry run", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "DRY_RUN_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/start
     * Job 시작
     */
    post("/backfill/{id}/start") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.startJob(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "job" to result.value.toDto()
                    ))
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "START_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Failed to start job", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "START_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/pause
     * Job 일시정지
     */
    post("/backfill/{id}/pause") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.pauseJob(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "status" to "PAUSED"))
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "PAUSE_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "PAUSE_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/resume
     * Job 재개
     */
    post("/backfill/{id}/resume") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.resumeJob(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "status" to "RUNNING"))
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "RESUME_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RESUME_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/cancel
     * Job 취소
     */
    post("/backfill/{id}/cancel") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.cancelJob(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "status" to "CANCELLED"))
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "CANCEL_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "CANCEL_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * POST /backfill/{id}/retry
     * 실패한 Job 재시도
     */
    post("/backfill/{id}/retry") {
        try {
            val id = call.parameters["id"]?.let { UUID.fromString(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, ApiError(code = "INVALID_ID", message = "Invalid job ID"))
                return@post
            }
            
            when (val result = backfillService.retryJob(id)) {
                is BackfillService.Result.Ok -> {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "status" to "PENDING"))
                }
                is BackfillService.Result.Err -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "RETRY_ERROR", message = result.error.toString())
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "RETRY_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * GET /backfill/stats
     * 통계 조회
     */
    get("/backfill/stats") {
        try {
            val stats = backfillService.getStats()
            if (stats != null) {
                call.respond(HttpStatusCode.OK, stats)
            } else {
                call.respond(HttpStatusCode.OK, mapOf("message" to "No stats available"))
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "STATS_ERROR", message = e.message ?: "Unknown error")
            )
        }
    }
}

// ==================== DTOs ====================

@Serializable
data class BackfillJobListResponse(
    val jobs: List<BackfillJobDto>,
    val total: Int
)

@Serializable
data class BackfillJobDto(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val status: String,
    val priority: Int,
    val progress: BackfillProgressDto,
    val createdBy: String,
    val createdAt: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val failureReason: String? = null,
    val dryRunResult: DryRunResultDto? = null
)

@Serializable
data class BackfillProgressDto(
    val total: Long,
    val processed: Long,
    val succeeded: Long,
    val failed: Long,
    val skipped: Long,
    val progressPercent: Double,
    val throughput: Double,
    val estimatedRemaining: String? = null,
    val currentEntity: String? = null
)

@Serializable
data class CreateBackfillRequestDto(
    val name: String,
    val type: String,
    val scope: BackfillScopeDto,
    val config: BackfillConfigDto? = null,
    val description: String? = null,
    val createdBy: String? = null,
    val priority: Int? = null
)

@Serializable
data class BackfillScopeDto(
    val tenantIds: List<String>? = null,
    val entityTypes: List<String>? = null,
    val entityKeys: List<String>? = null,
    val entityKeyPattern: String? = null,
    val fromTime: String? = null,
    val toTime: String? = null,
    val sliceTypes: List<String>? = null,
    val schemaIds: List<String>? = null
)

@Serializable
data class BackfillConfigDto(
    val batchSize: Int? = null,
    val concurrency: Int? = null,
    val continueOnError: Boolean? = null,
    val batchDelayMs: Long? = null,
    val dryRun: Boolean? = null
)

@Serializable
data class DryRunResultDto(
    val estimatedCount: Long,
    val countByType: Map<String, Long>,
    val estimatedDurationSeconds: Long?,
    val sampleEntities: List<String>,
    val warnings: List<String>
)

private fun BackfillJob.toDto() = BackfillJobDto(
    id = id.toString(),
    name = name,
    description = description,
    type = type.name,
    status = status.name,
    priority = priority,
    progress = progress.toDto(),
    createdBy = createdBy,
    createdAt = createdAt.toString(),
    startedAt = startedAt?.toString(),
    completedAt = completedAt?.toString(),
    failureReason = failureReason,
    dryRunResult = dryRunResult?.toDto()
)

private fun BackfillProgress.toDto() = BackfillProgressDto(
    total = total,
    processed = processed,
    succeeded = succeeded,
    failed = failed,
    skipped = skipped,
    progressPercent = progressPercent,
    throughput = throughput,
    estimatedRemaining = estimatedRemaining?.toString(),
    currentEntity = currentEntity
)

private fun DryRunResult.toDto() = DryRunResultDto(
    estimatedCount = estimatedCount,
    countByType = countByType,
    estimatedDurationSeconds = estimatedDuration?.seconds,
    sampleEntities = sampleEntities,
    warnings = warnings
)
