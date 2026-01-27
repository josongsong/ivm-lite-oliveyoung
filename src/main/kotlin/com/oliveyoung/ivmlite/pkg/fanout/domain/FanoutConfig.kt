package com.oliveyoung.ivmlite.pkg.fanout.domain

import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * RFC-IMPL-012: Fanout Configuration
 *
 * SOTA IVM 시스템의 fanout 동작을 제어하는 설정.
 * RuleSet의 join 관계에서 자동 추론된 의존성에 대해 운영 파라미터를 오버라이드.
 *
 * ## 핵심 원칙
 * - **Contract is Law**: RuleSet의 join이 의존성의 SSOT
 * - **Config is Policy**: FanoutConfig는 운영 정책 (how, not what)
 * - **Safe by Default**: 기본값은 보수적 (작은 batch, circuit breaker ON)
 */
data class FanoutConfig(
    /**
     * fanout 활성화 여부
     * false면 upstream 변경이 downstream에 전파되지 않음
     */
    val enabled: Boolean = true,

    /**
     * 한 번에 처리할 downstream 엔티티 수
     * 대규모 fanout 시 시스템 보호를 위해 제한
     */
    val batchSize: Int = DEFAULT_BATCH_SIZE,

    /**
     * 배치 간 지연 시간 (backpressure)
     * 대규모 fanout 시 시스템 부하 완화
     */
    val batchDelay: Duration = DEFAULT_BATCH_DELAY,

    /**
     * 단일 upstream 변경이 트리거할 수 있는 최대 downstream 수
     * 이 수를 초과하면 circuit breaker 발동
     */
    val maxFanout: Int = DEFAULT_MAX_FANOUT,

    /**
     * circuit breaker 발동 시 동작
     * - SKIP: fanout 건너뛰기 (경고 로그만)
     * - ERROR: 에러 발생 (트랜잭션 롤백)
     * - ASYNC: 비동기 큐로 전환 (별도 워커가 처리)
     */
    val circuitBreakerAction: CircuitBreakerAction = CircuitBreakerAction.SKIP,

    /**
     * fanout 우선순위 (높을수록 먼저 처리)
     * 여러 upstream이 동시에 변경될 때 처리 순서 결정
     */
    val priority: FanoutPriority = FanoutPriority.NORMAL,

    /**
     * 동시 fanout 처리 수 제한 (전역)
     * 시스템 전체에서 동시에 처리 중인 fanout job 수 제한
     */
    val maxConcurrentFanouts: Int = DEFAULT_MAX_CONCURRENT,

    /**
     * fanout 타임아웃
     * 이 시간 내에 완료되지 않으면 취소
     */
    val timeout: Duration = DEFAULT_TIMEOUT,

    /**
     * 재시도 설정
     */
    val retry: RetryConfig = RetryConfig(),

    /**
     * 특정 슬라이스 타입만 fanout (null이면 전체)
     * 선택적 fanout을 위한 필터
     */
    val targetSliceTypes: Set<SliceType>? = null,

    /**
     * deduplication 윈도우
     * 이 시간 내 동일 엔티티에 대한 중복 fanout 요청 무시
     */
    val deduplicationWindow: Duration = DEFAULT_DEDUP_WINDOW,
) {
    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        val DEFAULT_BATCH_DELAY: Duration = 100.milliseconds
        const val DEFAULT_MAX_FANOUT = 10_000
        const val DEFAULT_MAX_CONCURRENT = 10
        val DEFAULT_TIMEOUT: Duration = 5.minutes
        val DEFAULT_DEDUP_WINDOW: Duration = 1.seconds

        /**
         * 기본 설정 (안전한 기본값)
         */
        val DEFAULT = FanoutConfig()

        /**
         * 고성능 설정 (대량 처리용)
         */
        val HIGH_THROUGHPUT = FanoutConfig(
            batchSize = 500,
            batchDelay = 50.milliseconds,
            maxFanout = 100_000,
            maxConcurrentFanouts = 50,
        )

        /**
         * 보수적 설정 (안정성 우선)
         */
        val CONSERVATIVE = FanoutConfig(
            batchSize = 50,
            batchDelay = 200.milliseconds,
            maxFanout = 1_000,
            maxConcurrentFanouts = 5,
            circuitBreakerAction = CircuitBreakerAction.ERROR,
        )

        /**
         * 비활성화
         */
        val DISABLED = FanoutConfig(enabled = false)
    }

    init {
        require(batchSize > 0) { "batchSize must be positive: $batchSize" }
        require(maxFanout > 0) { "maxFanout must be positive: $maxFanout" }
        require(maxConcurrentFanouts > 0) { "maxConcurrentFanouts must be positive: $maxConcurrentFanouts" }
    }

    /**
     * batch 수 계산
     */
    fun calculateBatchCount(totalCount: Int): Int {
        return (totalCount + batchSize - 1) / batchSize
    }

    /**
     * circuit breaker 체크
     */
    fun shouldTripCircuitBreaker(fanoutCount: Int): Boolean {
        return fanoutCount > maxFanout
    }
}

/**
 * Circuit Breaker 발동 시 동작
 */
enum class CircuitBreakerAction {
    /**
     * fanout 건너뛰기 (경고 로그만)
     * 시스템 안정성 우선, 데이터 일관성은 나중에 수동 보정
     */
    SKIP,

    /**
     * 에러 발생 (트랜잭션 롤백)
     * 데이터 일관성 우선, 시스템 가용성 희생
     */
    ERROR,

    /**
     * 비동기 큐로 전환
     * 안정성과 일관성 모두 유지, 지연 허용
     */
    ASYNC,
}

/**
 * Fanout 우선순위
 */
enum class FanoutPriority(val weight: Int) {
    /**
     * 최우선 처리 (가격, 재고 등 실시간성 중요)
     */
    CRITICAL(100),

    /**
     * 높은 우선순위 (주요 속성 변경)
     */
    HIGH(75),

    /**
     * 일반 우선순위 (기본값)
     */
    NORMAL(50),

    /**
     * 낮은 우선순위 (메타데이터 등)
     */
    LOW(25),

    /**
     * 배경 처리 (분석용 데이터 등)
     */
    BACKGROUND(10);

    companion object {
        fun fromWeight(weight: Int): FanoutPriority {
            return entries.sortedByDescending { it.weight }
                .firstOrNull { weight >= it.weight }
                ?: BACKGROUND
        }
    }
}

/**
 * 재시도 설정
 */
data class RetryConfig(
    /**
     * 최대 재시도 횟수
     */
    val maxAttempts: Int = 3,

    /**
     * 재시도 간 기본 지연
     */
    val baseDelay: Duration = 1.seconds,

    /**
     * 지수 백오프 배수
     */
    val backoffMultiplier: Double = 2.0,

    /**
     * 최대 지연 시간
     */
    val maxDelay: Duration = 30.seconds,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be non-negative: $maxAttempts" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0: $backoffMultiplier" }
    }

    /**
     * n번째 재시도의 지연 시간 계산
     */
    fun calculateDelay(attempt: Int): Duration {
        if (attempt <= 0) return Duration.ZERO
        val multiplier = pow(backoffMultiplier, attempt - 1)
        val delayMs = (baseDelay.inWholeMilliseconds * multiplier).toLong()
        return minOf(delayMs.milliseconds, maxDelay)
    }

    private fun pow(base: Double, n: Int): Double {
        var result = 1.0
        repeat(n) { result *= base }
        return result
    }
}

/**
 * Fanout 의존성 정보
 *
 * RuleSet에서 추론된 의존성 관계를 표현
 */
data class FanoutDependency(
    /**
     * upstream 엔티티 타입 (변경 발생)
     * 예: "brand", "category"
     */
    val upstreamEntityType: String,

    /**
     * downstream 엔티티 타입 (영향 받음)
     * 예: "product"
     */
    val downstreamEntityType: String,

    /**
     * 인덱스 타입 (역참조 조회용)
     * 예: "product_by_brand"
     */
    val indexType: String,

    /**
     * join 필드 경로 (JSON Path)
     * 예: "/brandCode"
     */
    val joinPath: String,

    /**
     * 영향받는 슬라이스 타입들
     */
    val affectedSliceTypes: Set<SliceType>,

    /**
     * RFC-IMPL-013: 최대 fanout 수 (circuit breaker 임계값)
     * IndexSpec.maxFanout에서 가져옴. 0 또는 음수이면 전역 설정 사용.
     */
    val maxFanout: Int = 0,

    /**
     * 이 의존성에 대한 개별 설정 (전역 설정 오버라이드)
     */
    val config: FanoutConfig? = null,
)

/**
 * Fanout Job 상태
 */
data class FanoutJob(
    val id: String,
    val upstreamEntityType: String,
    val upstreamEntityKey: String,
    val upstreamVersion: Long,
    val totalAffected: Int,
    val processedCount: Int,
    val status: FanoutJobStatus,
    val priority: FanoutPriority,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null,
) {
    val progress: Double
        get() = if (totalAffected == 0) 1.0 else processedCount.toDouble() / totalAffected

    val isComplete: Boolean
        get() = status in setOf(FanoutJobStatus.COMPLETED, FanoutJobStatus.FAILED, FanoutJobStatus.SKIPPED)
}

/**
 * Fanout Job 상태
 */
enum class FanoutJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED,  // circuit breaker로 건너뜀
    ASYNC_QUEUED,  // 비동기 큐로 전환됨
}
