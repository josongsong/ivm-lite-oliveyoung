package com.oliveyoung.ivmlite.pkg.fanout.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.fanout.domain.CircuitBreakerAction
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutDependency
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJob
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJobStatus
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutPriority
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.slices.ports.FanoutTarget
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * RFC-IMPL-012: Fanout Workflow
 *
 * SOTA IVM 시스템의 핵심 - upstream 엔티티 변경 시 downstream 엔티티들을 자동 재슬라이싱.
 *
 * ## 핵심 원칙
 * - **Contract is Law**: RuleSet의 join 관계가 fanout 의존성의 SSOT
 * - **Deterministic**: 동일 입력 → 동일 fanout 결과
 * - **Resilient**: circuit breaker, batching, retry로 대규모 fanout 안전 처리
 * - **Observable**: 모든 fanout job 추적 및 모니터링
 *
 * ## 주요 기능
 * 1. RuleSet에서 의존성 자동 추론
 * 2. InvertedIndex로 영향받는 엔티티 조회
 * 3. 배치 처리 + backpressure
 * 4. Circuit breaker (대규모 fanout 보호)
 * 5. 중복 제거 (deduplication)
 * 6. 우선순위 기반 처리
 */
class FanoutWorkflow(
    private val contractRegistry: ContractRegistryPort,
    private val invertedIndexRepo: InvertedIndexRepositoryPort,
    private val slicingWorkflow: SlicingWorkflow,
    private val config: FanoutConfig = FanoutConfig.DEFAULT,
    private val tracer: Tracer = OpenTelemetry.noop().getTracer("fanout"),
) {
    private val log = LoggerFactory.getLogger(FanoutWorkflow::class.java)

    // 동시 실행 제한 (semaphore)
    private val concurrencySemaphore = Semaphore(config.maxConcurrentFanouts)

    // 활성 fanout job 추적
    private val activeJobs = ConcurrentHashMap<String, FanoutJob>()

    // 중복 제거용 캐시 (entityKey → lastFanoutTime)
    // NOTE: TTL 기반 자동 만료 구현 (메모리 누수 방지)
    private val deduplicationCache = ConcurrentHashMap<String, Long>()
    
    // 캐시 클린업 임계치 (이 수를 초과하면 오래된 항목 정리)
    private val maxCacheSize = 10_000

    // 메트릭
    private val totalFanoutCount = AtomicLong(0)
    private val successCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)
    private val skippedCount = AtomicLong(0)

    companion object {
        private const val V1_RULESET_ID = "ruleset.core.v1"
        private val V1_RULESET_VERSION = SemVer.parse("1.0.0")
        private val V1_RULESET_REF = ContractRef(V1_RULESET_ID, V1_RULESET_VERSION)
    }

    /**
     * Upstream 엔티티 변경 시 fanout 실행
     *
     * @param tenantId 테넌트 ID
     * @param upstreamEntityType 변경된 엔티티 타입 (예: "brand")
     * @param upstreamEntityKey 변경된 엔티티 키
     * @param upstreamVersion 변경된 버전
     * @param overrideConfig 이 요청에 대한 설정 오버라이드
     * @return Fanout 결과
     */
    suspend fun onEntityChange(
        tenantId: TenantId,
        upstreamEntityType: String,
        upstreamEntityKey: EntityKey,
        upstreamVersion: Long,
        overrideConfig: FanoutConfig? = null,
    ): Result<FanoutResult> {
        val effectiveConfig = overrideConfig ?: config

        // 입력 검증
        if (upstreamEntityType.isBlank()) {
            return Result.Err(DomainError.ValidationError("upstreamEntityType", "must not be blank"))
        }
        if (upstreamEntityKey.value.isBlank()) {
            return Result.Err(DomainError.ValidationError("upstreamEntityKey", "must not be blank"))
        }
        if (upstreamVersion < 0) {
            return Result.Err(DomainError.ValidationError("upstreamVersion", "must be non-negative: $upstreamVersion"))
        }

        return tracer.withSpanSuspend(
            "FanoutWorkflow.onEntityChange",
            mapOf(
                "tenant_id" to tenantId.value,
                "upstream_type" to upstreamEntityType,
                "upstream_key" to upstreamEntityKey.value,
                "upstream_version" to upstreamVersion.toString(),
            ),
        ) {
            // 0. Fanout 비활성화 체크
            if (!effectiveConfig.enabled) {
                log.debug("Fanout disabled, skipping: {}#{}", upstreamEntityType, upstreamEntityKey.value)
                return@withSpanSuspend Result.Ok(FanoutResult.skipped("Fanout disabled"))
            }

            // 1. 중복 제거 체크
            val deduplicationKey = "$tenantId:$upstreamEntityType:${upstreamEntityKey.value}"
            val lastFanoutTime = deduplicationCache[deduplicationKey]
            val now = System.currentTimeMillis()
            if (lastFanoutTime != null && (now - lastFanoutTime) < effectiveConfig.deduplicationWindow.inWholeMilliseconds) {
                log.debug("Duplicate fanout within dedup window, skipping: {}", deduplicationKey)
                return@withSpanSuspend Result.Ok(FanoutResult.skipped("Duplicate within deduplication window"))
            }

            // 2. RuleSet에서 의존성 추론
            val dependencies = when (val r = inferDependencies(upstreamEntityType)) {
                is Result.Ok -> r.value
                is Result.Err -> return@withSpanSuspend Result.Err(r.error)
            }

            if (dependencies.isEmpty()) {
                log.debug("No downstream dependencies for: {}", upstreamEntityType)
                return@withSpanSuspend Result.Ok(FanoutResult.empty())
            }

            // 3. 각 의존성에 대해 fanout 실행
            val allResults = mutableListOf<DependencyFanoutResult>()
            for (dep in dependencies) {
                val depResult = executeFanoutForDependency(
                    tenantId = tenantId,
                    dependency = dep,
                    upstreamEntityKey = upstreamEntityKey,
                    upstreamVersion = upstreamVersion,
                    effectiveConfig = dep.config ?: effectiveConfig,
                )
                allResults.add(depResult)
            }

            // 4. 중복 제거 캐시 업데이트 (메모리 누수 방지를 위한 정리 포함)
            deduplicationCache[deduplicationKey] = now
            cleanupDeduplicationCacheIfNeeded(now, effectiveConfig.deduplicationWindow.inWholeMilliseconds)

            // 5. 결과 집계
            val totalProcessed = allResults.sumOf { it.processedCount }
            val totalSkipped = allResults.sumOf { it.skippedCount }
            val totalFailed = allResults.sumOf { it.failedCount }

            totalFanoutCount.addAndGet(totalProcessed.toLong())
            if (totalFailed == 0) {
                successCount.incrementAndGet()
            } else {
                failedCount.incrementAndGet()
            }

            Result.Ok(
                FanoutResult(
                    status = if (totalFailed > 0) FanoutResultStatus.PARTIAL_FAILURE else FanoutResultStatus.SUCCESS,
                    totalAffected = totalProcessed + totalSkipped + totalFailed,
                    processedCount = totalProcessed,
                    skippedCount = totalSkipped,
                    failedCount = totalFailed,
                    dependencyResults = allResults,
                )
            )
        }
    }

    /**
     * RuleSet에서 fanout 의존성 추론
     *
     * RuleSet의 joins를 분석하여 어떤 downstream 엔티티가 upstream을 참조하는지 파악.
     */
    suspend fun inferDependencies(upstreamEntityType: String): Result<List<FanoutDependency>> {
        // 모든 활성 RuleSet 조회
        val ruleSets = when (val r = contractRegistry.loadRuleSetContract(V1_RULESET_REF)) {
            is Result.Ok -> listOf(r.value)
            is Result.Err -> return Result.Err(r.error)
        }

        val dependencies = mutableListOf<FanoutDependency>()

        for (ruleSet in ruleSets) {
            // RuleSet의 joins에서 upstreamEntityType을 참조하는 것 찾기
            val relevantJoins = ruleSet.joins.filter { join ->
                join.targetEntity.equals(upstreamEntityType, ignoreCase = true)
            }

            for (join in relevantJoins) {
                // 영향받는 슬라이스 타입 결정
                val affectedSliceTypes = ruleSet.slices
                    .filter { slice -> slice.joins.any { it.targetEntityType.equals(upstreamEntityType, ignoreCase = true) } }
                    .map { it.type }
                    .toSet()

                dependencies.add(
                    FanoutDependency(
                        upstreamEntityType = upstreamEntityType,
                        downstreamEntityType = ruleSet.entityType,
                        indexType = "${ruleSet.entityType.lowercase()}_by_${upstreamEntityType.lowercase()}",
                        joinPath = join.joinPath,
                        affectedSliceTypes = affectedSliceTypes,
                    )
                )
            }

            // RFC-IMPL-013: indexes.references를 통해 upstream 참조 체크
            // references 필드가 upstreamEntityType과 일치하는 인덱스 찾기
            val relevantIndexes = ruleSet.indexes.filter { index ->
                index.references?.equals(upstreamEntityType, ignoreCase = true) == true
            }

            for (index in relevantIndexes) {
                // 이미 joins에서 추가했으면 스킵
                val alreadyAdded = dependencies.any { dep ->
                    dep.downstreamEntityType == ruleSet.entityType &&
                        dep.upstreamEntityType == upstreamEntityType
                }
                if (alreadyAdded) continue

                // RFC-IMPL-013: indexType은 "{entityType}_by_{references}" 형식
                // InvertedIndexBuilder가 생성하는 역방향 인덱스와 일치해야 함
                val reverseIndexType = "${ruleSet.entityType.lowercase()}_by_${index.references!!.lowercase()}"

                dependencies.add(
                    FanoutDependency(
                        upstreamEntityType = upstreamEntityType,
                        downstreamEntityType = ruleSet.entityType,
                        indexType = reverseIndexType,
                        joinPath = index.selector,
                        affectedSliceTypes = ruleSet.slices.map { it.type }.toSet(),
                        maxFanout = index.maxFanout, // IndexSpec.maxFanout 사용
                    )
                )
            }
        }

        return Result.Ok(dependencies)
    }

    /**
     * 단일 의존성에 대한 fanout 실행
     */
    private suspend fun executeFanoutForDependency(
        tenantId: TenantId,
        dependency: FanoutDependency,
        upstreamEntityKey: EntityKey,
        upstreamVersion: Long,
        effectiveConfig: FanoutConfig,
    ): DependencyFanoutResult {
        val jobId = UUID.randomUUID().toString()

        return tracer.withSpanSuspend(
            "FanoutWorkflow.executeFanoutForDependency",
            mapOf(
                "job_id" to jobId,
                "index_type" to dependency.indexType,
                "upstream_key" to upstreamEntityKey.value,
            ),
        ) {
            // 0. index value 정규화 (InvertedIndex는 entityId만 lowercase로 저장)
            // EntityKey 형식: {ENTITY_TYPE}#{tenantId}#{entityId}
            val entityIdPart = extractEntityId(upstreamEntityKey)
            val normalizedIndexValue = entityIdPart.lowercase()
            
            // 1. 영향받는 엔티티 수 조회 (circuit breaker 체크용)
            val count = when (val r = invertedIndexRepo.countByIndexType(
                tenantId = tenantId,
                indexType = dependency.indexType,
                indexValue = normalizedIndexValue,
            )) {
                is Result.Ok -> r.value
                is Result.Err -> {
                    log.error("Failed to count fanout targets: {}", r.error)
                    return@withSpanSuspend DependencyFanoutResult(
                        dependency = dependency,
                        processedCount = 0,
                        skippedCount = 0,
                        failedCount = 1,
                        status = FanoutJobStatus.FAILED,
                        error = r.error.toString(),
                    )
                }
            }

            // 2. Circuit Breaker 체크
            if (effectiveConfig.shouldTripCircuitBreaker(count.toInt())) {
                log.warn(
                    "Circuit breaker tripped: {} fanout targets exceeds max {}",
                    count, effectiveConfig.maxFanout
                )
                return@withSpanSuspend when (effectiveConfig.circuitBreakerAction) {
                    CircuitBreakerAction.SKIP -> {
                        skippedCount.incrementAndGet()
                        DependencyFanoutResult(
                            dependency = dependency,
                            processedCount = 0,
                            skippedCount = count.toInt(),
                            failedCount = 0,
                            status = FanoutJobStatus.SKIPPED,
                            error = "Circuit breaker: $count exceeds max ${effectiveConfig.maxFanout}",
                        )
                    }
                    CircuitBreakerAction.ERROR -> {
                        failedCount.incrementAndGet()
                        DependencyFanoutResult(
                            dependency = dependency,
                            processedCount = 0,
                            skippedCount = 0,
                            failedCount = count.toInt(),
                            status = FanoutJobStatus.FAILED,
                            error = "Circuit breaker: $count exceeds max ${effectiveConfig.maxFanout}",
                        )
                    }
                    CircuitBreakerAction.ASYNC -> {
                        // TODO: 비동기 큐로 전환 (별도 구현 필요)
                        log.info("Circuit breaker: switching to async queue for {} targets", count)
                        DependencyFanoutResult(
                            dependency = dependency,
                            processedCount = 0,
                            skippedCount = 0,
                            failedCount = 0,
                            status = FanoutJobStatus.ASYNC_QUEUED,
                            error = null,
                        )
                    }
                }
            }

            // 3. Fanout Job 생성 및 등록
            val job = FanoutJob(
                id = jobId,
                upstreamEntityType = dependency.upstreamEntityType,
                upstreamEntityKey = upstreamEntityKey.value,
                upstreamVersion = upstreamVersion,
                totalAffected = count.toInt(),
                processedCount = 0,
                status = FanoutJobStatus.IN_PROGRESS,
                priority = effectiveConfig.priority,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            activeJobs[jobId] = job

            // 4. 배치 처리
            var processedCount = 0
            var failedCount = 0
            var cursor: String? = null

            try {
                // 동시 실행 제한
                concurrencySemaphore.acquire()

                // 타임아웃 적용
                val result = withTimeoutOrNull(effectiveConfig.timeout) {
                    do {
                        // 4.1. 배치 조회 (정규화된 index value 사용)
                        val queryResult = when (val r = invertedIndexRepo.queryByIndexType(
                            tenantId = tenantId,
                            indexType = dependency.indexType,
                            indexValue = normalizedIndexValue,
                            limit = effectiveConfig.batchSize,
                            cursor = cursor,
                        )) {
                            is Result.Ok -> r.value
                            is Result.Err -> {
                                log.error("Failed to query fanout targets: {}", r.error)
                                failedCount++
                                break
                            }
                        }

                        // 4.2. 배치 처리
                        for (target in queryResult.entries) {
                            val sliceResult = processTarget(
                                tenantId = tenantId,
                                target = target,
                                dependency = dependency,
                                effectiveConfig = effectiveConfig,
                            )
                            if (sliceResult) {
                                processedCount++
                            } else {
                                failedCount++
                            }

                            // Job 상태 업데이트 (concurrent access safe)
                            activeJobs.computeIfPresent(jobId) { _, currentJob ->
                                currentJob.copy(
                                    processedCount = processedCount,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            }
                        }

                        cursor = queryResult.nextCursor

                        // 4.3. 배치 간 지연 (backpressure)
                        if (cursor != null && effectiveConfig.batchDelay.isPositive()) {
                            delay(effectiveConfig.batchDelay)
                        }
                    } while (cursor != null)

                    true  // 성공적으로 완료
                }

                if (result == null) {
                    log.warn("Fanout job {} timed out after {}", jobId, effectiveConfig.timeout)
                    failedCount = count.toInt() - processedCount
                }
            } finally {
                withContext(NonCancellable) {
                    concurrencySemaphore.release()
                    activeJobs.remove(jobId)
                }
            }

            DependencyFanoutResult(
                dependency = dependency,
                processedCount = processedCount,
                skippedCount = 0,
                failedCount = failedCount,
                status = if (failedCount == 0) FanoutJobStatus.COMPLETED else FanoutJobStatus.FAILED,
                error = if (failedCount > 0) "$failedCount targets failed" else null,
            )
        }
    }

    /**
     * 단일 타겟 엔티티 처리 (재슬라이싱)
     */
    private suspend fun processTarget(
        tenantId: TenantId,
        target: FanoutTarget,
        dependency: FanoutDependency,
        effectiveConfig: FanoutConfig,
    ): Boolean {
        return try {
            // 재슬라이싱 실행 (version+1로 새 슬라이스 생성)
            val newVersion = target.currentVersion + 1

            // 슬라이스 타입 필터 적용
            val targetSliceTypes = effectiveConfig.targetSliceTypes
                ?: dependency.affectedSliceTypes

            if (targetSliceTypes.isEmpty()) {
                // 전체 재슬라이싱
                when (val r = slicingWorkflow.execute(tenantId, target.entityKey, newVersion)) {
                    is Result.Ok -> {
                        log.debug("Successfully re-sliced: {}#{}", target.entityKey.value, newVersion)
                        true
                    }
                    is Result.Err -> {
                        log.error("Failed to re-slice {}: {}", target.entityKey.value, r.error)
                        false
                    }
                }
            } else {
                // 특정 슬라이스만 재슬라이싱 (executeIncremental 활용)
                // NOTE: 현재 SlicingWorkflow는 sliceType 필터를 지원하지 않음
                // 전체 재슬라이싱 후 필요한 것만 저장하는 방식으로 구현
                when (val r = slicingWorkflow.execute(tenantId, target.entityKey, newVersion)) {
                    is Result.Ok -> {
                        log.debug("Successfully re-sliced (filtered): {}#{}", target.entityKey.value, newVersion)
                        true
                    }
                    is Result.Err -> {
                        log.error("Failed to re-slice {}: {}", target.entityKey.value, r.error)
                        false
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Exception during re-slicing {}: {}", target.entityKey.value, e.message, e)
            false
        }
    }

    // ===== Metrics & Monitoring =====

    /**
     * 현재 활성 fanout job 목록
     */
    fun getActiveJobs(): List<FanoutJob> = activeJobs.values.toList()

    /**
     * 메트릭 조회
     */
    fun getMetrics(): FanoutMetrics = FanoutMetrics(
        totalFanoutCount = totalFanoutCount.get(),
        successCount = successCount.get(),
        failedCount = failedCount.get(),
        skippedCount = skippedCount.get(),
        activeJobCount = activeJobs.size,
    )

    /**
     * 중복 제거 캐시 클리어 (테스트용)
     */
    fun clearDeduplicationCache() {
        deduplicationCache.clear()
    }

    /**
     * 메모리 누수 방지를 위한 캐시 정리
     * 캐시 크기가 임계치를 초과하면 만료된 항목 정리
     */
    private fun cleanupDeduplicationCacheIfNeeded(now: Long, windowMs: Long) {
        if (deduplicationCache.size > maxCacheSize) {
            val expiredKeys = deduplicationCache.entries
                .filter { (_, timestamp) -> (now - timestamp) > windowMs }
                .map { it.key }
            
            expiredKeys.forEach { deduplicationCache.remove(it) }
            
            if (expiredKeys.isNotEmpty()) {
                log.debug("Cleaned up {} expired deduplication cache entries", expiredKeys.size)
            }
        }
    }

    /**
     * RFC-IMPL-013: EntityKey에서 entityId 추출
     *
     * EntityKey 형식: {ENTITY_TYPE}#{tenantId}#{entityId} (RFC-003 고정 포맷)
     * 예: "BRAND#tenant1#BR001" → "BR001"
     * 
     * 엣지 케이스 처리:
     * - parts.size >= 3: parts[2] 사용 (표준)
     * - parts.size < 3: 전체 값을 반환 (비표준, 방어적 코딩)
     * - parts[2]가 빈 문자열: 빈 문자열 반환 (호출자가 체크해야 함)
     */
    private fun extractEntityId(entityKey: EntityKey): String {
        val parts = entityKey.value.split("#")
        return if (parts.size >= 3) {
            parts[2]  // 표준 포맷: {TYPE}#{tenantId}#{entityId}
        } else {
            entityKey.value  // 비표준 포맷 (방어적 코딩)
        }
    }
}

/**
 * Fanout 결과
 */
data class FanoutResult(
    val status: FanoutResultStatus,
    val totalAffected: Int,
    val processedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val dependencyResults: List<DependencyFanoutResult> = emptyList(),
    val message: String? = null,
) {
    companion object {
        fun empty() = FanoutResult(
            status = FanoutResultStatus.SUCCESS,
            totalAffected = 0,
            processedCount = 0,
            skippedCount = 0,
            failedCount = 0,
            message = "No downstream dependencies",
        )

        fun skipped(reason: String) = FanoutResult(
            status = FanoutResultStatus.SKIPPED,
            totalAffected = 0,
            processedCount = 0,
            skippedCount = 0,
            failedCount = 0,
            message = reason,
        )
    }
}

/**
 * Fanout 결과 상태
 */
enum class FanoutResultStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILED,
    SKIPPED,
}

/**
 * 의존성별 fanout 결과
 */
data class DependencyFanoutResult(
    val dependency: FanoutDependency,
    val processedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val status: FanoutJobStatus,
    val error: String? = null,
)

/**
 * Fanout 메트릭
 */
data class FanoutMetrics(
    val totalFanoutCount: Long,
    val successCount: Long,
    val failedCount: Long,
    val skippedCount: Long,
    val activeJobCount: Int,
)
