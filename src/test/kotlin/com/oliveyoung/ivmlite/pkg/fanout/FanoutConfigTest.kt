package com.oliveyoung.ivmlite.pkg.fanout

import com.oliveyoung.ivmlite.pkg.fanout.domain.CircuitBreakerAction
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutDependency
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJob
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutJobStatus
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutPriority
import com.oliveyoung.ivmlite.pkg.fanout.domain.RetryConfig
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * RFC-IMPL-012: FanoutConfig 도메인 모델 테스트
 *
 * 모든 설정값과 계산 로직에 대한 단위 테스트
 */
class FanoutConfigTest : StringSpec({

    // ==================== 1. FanoutConfig 생성 및 검증 ====================

    "기본 FanoutConfig 생성" {
        val config = FanoutConfig()

        config.enabled shouldBe true
        config.batchSize shouldBe 100
        config.maxFanout shouldBe 10_000
        config.circuitBreakerAction shouldBe CircuitBreakerAction.SKIP
        config.priority shouldBe FanoutPriority.NORMAL
        config.maxConcurrentFanouts shouldBe 10
    }

    "batchSize가 0 이하면 예외 발생" {
        shouldThrow<IllegalArgumentException> {
            FanoutConfig(batchSize = 0)
        }

        shouldThrow<IllegalArgumentException> {
            FanoutConfig(batchSize = -1)
        }
    }

    "maxFanout이 0 이하면 예외 발생" {
        shouldThrow<IllegalArgumentException> {
            FanoutConfig(maxFanout = 0)
        }
    }

    "maxConcurrentFanouts가 0 이하면 예외 발생" {
        shouldThrow<IllegalArgumentException> {
            FanoutConfig(maxConcurrentFanouts = 0)
        }
    }

    // ==================== 2. calculateBatchCount 테스트 ====================

    "calculateBatchCount - 경계값 테스트" {
        val config = FanoutConfig(batchSize = 100)

        // 0개
        config.calculateBatchCount(0) shouldBe 0

        // 정확히 1배치
        config.calculateBatchCount(100) shouldBe 1

        // 1배치 + 1
        config.calculateBatchCount(101) shouldBe 2

        // 정확히 2배치
        config.calculateBatchCount(200) shouldBe 2

        // 큰 수
        config.calculateBatchCount(1000) shouldBe 10
        config.calculateBatchCount(1001) shouldBe 11
    }

    "calculateBatchCount - 다양한 배치 크기" {
        FanoutConfig(batchSize = 50).calculateBatchCount(125) shouldBe 3
        FanoutConfig(batchSize = 1).calculateBatchCount(5) shouldBe 5
        FanoutConfig(batchSize = 1000).calculateBatchCount(5) shouldBe 1
    }

    // ==================== 3. shouldTripCircuitBreaker 테스트 ====================

    "shouldTripCircuitBreaker - 경계값 테스트" {
        val config = FanoutConfig(maxFanout = 10000)

        // 정확히 maxFanout
        config.shouldTripCircuitBreaker(10000) shouldBe false

        // maxFanout + 1
        config.shouldTripCircuitBreaker(10001) shouldBe true

        // maxFanout - 1
        config.shouldTripCircuitBreaker(9999) shouldBe false

        // 0
        config.shouldTripCircuitBreaker(0) shouldBe false
    }

    // ==================== 4. 프리셋 설정 테스트 ====================

    "DEFAULT 프리셋" {
        val config = FanoutConfig.DEFAULT

        config.enabled shouldBe true
        config.batchSize shouldBe 100
        config.maxFanout shouldBe 10_000
    }

    "HIGH_THROUGHPUT 프리셋" {
        val config = FanoutConfig.HIGH_THROUGHPUT

        config.batchSize shouldBe 500
        config.maxFanout shouldBe 100_000
        config.maxConcurrentFanouts shouldBe 50
    }

    "CONSERVATIVE 프리셋" {
        val config = FanoutConfig.CONSERVATIVE

        config.batchSize shouldBe 50
        config.maxFanout shouldBe 1_000
        config.circuitBreakerAction shouldBe CircuitBreakerAction.ERROR
    }

    "DISABLED 프리셋" {
        val config = FanoutConfig.DISABLED

        config.enabled shouldBe false
    }

    // ==================== 5. RetryConfig 테스트 ====================

    "RetryConfig 기본값" {
        val config = RetryConfig()

        config.maxAttempts shouldBe 3
        config.backoffMultiplier shouldBe 2.0
    }

    "RetryConfig calculateDelay - 지수 백오프" {
        val config = RetryConfig(
            baseDelay = 1.seconds,
            backoffMultiplier = 2.0,
            maxDelay = 30.seconds,
        )

        // attempt 0 → 0
        config.calculateDelay(0).inWholeMilliseconds shouldBe 0

        // attempt 1 → 1s
        config.calculateDelay(1).inWholeMilliseconds shouldBe 1000

        // attempt 2 → 2s
        config.calculateDelay(2).inWholeMilliseconds shouldBe 2000

        // attempt 3 → 4s
        config.calculateDelay(3).inWholeMilliseconds shouldBe 4000

        // attempt 4 → 8s
        config.calculateDelay(4).inWholeMilliseconds shouldBe 8000

        // attempt 5 → 16s
        config.calculateDelay(5).inWholeMilliseconds shouldBe 16000

        // attempt 6 → 30s (maxDelay 제한)
        config.calculateDelay(6).inWholeMilliseconds shouldBe 30000
    }

    "RetryConfig maxAttempts 음수 불가" {
        shouldThrow<IllegalArgumentException> {
            RetryConfig(maxAttempts = -1)
        }
    }

    "RetryConfig backoffMultiplier 1.0 미만 불가" {
        shouldThrow<IllegalArgumentException> {
            RetryConfig(backoffMultiplier = 0.5)
        }
    }

    // ==================== 6. FanoutPriority 테스트 ====================

    "FanoutPriority weight 값" {
        FanoutPriority.CRITICAL.weight shouldBe 100
        FanoutPriority.HIGH.weight shouldBe 75
        FanoutPriority.NORMAL.weight shouldBe 50
        FanoutPriority.LOW.weight shouldBe 25
        FanoutPriority.BACKGROUND.weight shouldBe 10
    }

    "FanoutPriority fromWeight" {
        FanoutPriority.fromWeight(150) shouldBe FanoutPriority.CRITICAL
        FanoutPriority.fromWeight(100) shouldBe FanoutPriority.CRITICAL
        FanoutPriority.fromWeight(99) shouldBe FanoutPriority.HIGH
        FanoutPriority.fromWeight(75) shouldBe FanoutPriority.HIGH
        FanoutPriority.fromWeight(74) shouldBe FanoutPriority.NORMAL
        FanoutPriority.fromWeight(50) shouldBe FanoutPriority.NORMAL
        FanoutPriority.fromWeight(49) shouldBe FanoutPriority.LOW
        FanoutPriority.fromWeight(25) shouldBe FanoutPriority.LOW
        FanoutPriority.fromWeight(24) shouldBe FanoutPriority.BACKGROUND
        FanoutPriority.fromWeight(10) shouldBe FanoutPriority.BACKGROUND
        FanoutPriority.fromWeight(0) shouldBe FanoutPriority.BACKGROUND
    }

    // ==================== 7. CircuitBreakerAction 테스트 ====================

    "CircuitBreakerAction 열거형" {
        CircuitBreakerAction.values().size shouldBe 3

        CircuitBreakerAction.SKIP.name shouldBe "SKIP"
        CircuitBreakerAction.ERROR.name shouldBe "ERROR"
        CircuitBreakerAction.ASYNC.name shouldBe "ASYNC"
    }

    // ==================== 8. FanoutDependency 테스트 ====================

    "FanoutDependency 생성" {
        val dependency = FanoutDependency(
            upstreamEntityType = "brand",
            downstreamEntityType = "product",
            indexType = "product_by_brand",
            joinPath = "/brandCode",
            affectedSliceTypes = setOf(SliceType.CORE, SliceType.DERIVED),
        )

        dependency.upstreamEntityType shouldBe "brand"
        dependency.downstreamEntityType shouldBe "product"
        dependency.indexType shouldBe "product_by_brand"
        dependency.affectedSliceTypes.size shouldBe 2
    }

    "FanoutDependency with config override" {
        val customConfig = FanoutConfig(batchSize = 25, maxFanout = 500)
        val dependency = FanoutDependency(
            upstreamEntityType = "brand",
            downstreamEntityType = "product",
            indexType = "product_by_brand",
            joinPath = "/brandCode",
            affectedSliceTypes = emptySet(),
            config = customConfig,
        )

        dependency.config shouldBe customConfig
        dependency.config!!.batchSize shouldBe 25
    }

    // ==================== 9. FanoutJob 테스트 ====================

    "FanoutJob progress 계산" {
        val job = FanoutJob(
            id = "job-1",
            upstreamEntityType = "brand",
            upstreamEntityKey = "BR001",
            upstreamVersion = 1L,
            totalAffected = 100,
            processedCount = 50,
            status = FanoutJobStatus.IN_PROGRESS,
            priority = FanoutPriority.NORMAL,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        job.progress shouldBe 0.5
    }

    "FanoutJob progress - totalAffected가 0일 때" {
        val job = FanoutJob(
            id = "job-1",
            upstreamEntityType = "brand",
            upstreamEntityKey = "BR001",
            upstreamVersion = 1L,
            totalAffected = 0,
            processedCount = 0,
            status = FanoutJobStatus.COMPLETED,
            priority = FanoutPriority.NORMAL,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        job.progress shouldBe 1.0  // Division by zero 방지
    }

    "FanoutJob isComplete 상태" {
        val baseJob = FanoutJob(
            id = "job-1",
            upstreamEntityType = "brand",
            upstreamEntityKey = "BR001",
            upstreamVersion = 1L,
            totalAffected = 10,
            processedCount = 5,
            status = FanoutJobStatus.PENDING,
            priority = FanoutPriority.NORMAL,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        baseJob.copy(status = FanoutJobStatus.PENDING).isComplete shouldBe false
        baseJob.copy(status = FanoutJobStatus.IN_PROGRESS).isComplete shouldBe false
        baseJob.copy(status = FanoutJobStatus.COMPLETED).isComplete shouldBe true
        baseJob.copy(status = FanoutJobStatus.FAILED).isComplete shouldBe true
        baseJob.copy(status = FanoutJobStatus.SKIPPED).isComplete shouldBe true
        baseJob.copy(status = FanoutJobStatus.ASYNC_QUEUED).isComplete shouldBe false
    }

    // ==================== 10. FanoutJobStatus 테스트 ====================

    "FanoutJobStatus 열거형" {
        FanoutJobStatus.values().size shouldBe 6

        FanoutJobStatus.PENDING.name shouldBe "PENDING"
        FanoutJobStatus.IN_PROGRESS.name shouldBe "IN_PROGRESS"
        FanoutJobStatus.COMPLETED.name shouldBe "COMPLETED"
        FanoutJobStatus.FAILED.name shouldBe "FAILED"
        FanoutJobStatus.SKIPPED.name shouldBe "SKIPPED"
        FanoutJobStatus.ASYNC_QUEUED.name shouldBe "ASYNC_QUEUED"
    }

    // ==================== 11. Duration 설정 테스트 ====================

    "timeout 기본값" {
        val config = FanoutConfig.DEFAULT
        config.timeout shouldBe 5.minutes
    }

    "deduplicationWindow 기본값" {
        val config = FanoutConfig.DEFAULT
        config.deduplicationWindow shouldBe 1.seconds
    }

    "batchDelay 기본값" {
        val config = FanoutConfig.DEFAULT
        config.batchDelay shouldBe 100.milliseconds
    }
})
