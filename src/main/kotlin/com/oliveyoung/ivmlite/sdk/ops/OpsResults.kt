package com.oliveyoung.ivmlite.sdk.ops

/**
 * ProcessResult - Outbox 처리 결과
 */
data class ProcessResult(
    val success: Boolean,
    val processed: Int,
    val failed: Int,
    val skipped: Int,
    val details: List<ProcessDetail>,
    val error: String? = null
) {
    companion object {
        fun success(processed: Int, failed: Int, details: List<ProcessDetail>): ProcessResult {
            return ProcessResult(
                success = true,
                processed = processed,
                failed = failed,
                skipped = 0,
                details = details
            )
        }

        fun failure(error: String): ProcessResult {
            return ProcessResult(
                success = false,
                processed = 0,
                failed = 0,
                skipped = 0,
                details = emptyList(),
                error = error
            )
        }

        fun empty(): ProcessResult {
            return ProcessResult(
                success = true,
                processed = 0,
                failed = 0,
                skipped = 0,
                details = emptyList()
            )
        }
    }
}

/**
 * ProcessDetail - 개별 처리 결과
 */
data class ProcessDetail(
    val entryId: String,
    val eventType: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * ReprocessResult - 재처리 결과
 */
data class ReprocessResult(
    val entityKey: String,
    val success: Boolean,
    val stages: List<StageResult>,
    val error: String? = null
) {
    companion object {
        fun success(entityKey: String, stages: List<StageResult>): ReprocessResult {
            return ReprocessResult(
                entityKey = entityKey,
                success = true,
                stages = stages
            )
        }

        fun failure(entityKey: String, error: String, stages: List<StageResult> = emptyList()): ReprocessResult {
            return ReprocessResult(
                entityKey = entityKey,
                success = false,
                stages = stages,
                error = error
            )
        }
    }
}

/**
 * StageResult - 단계별 결과
 */
data class StageResult(
    val stage: String,  // "Slice", "Ship", etc.
    val success: Boolean,
    val slicesCreated: Int = 0,
    val sinksShipped: Int = 0,
    val error: String? = null
)

/**
 * ProcessFilter - 처리 필터 옵션
 */
@IvmDslMarker
class ProcessFilter {
    internal var slicingOnly = false
    internal var shippingOnly = false
    internal var limit = 100

    fun onlySlicing() {
        slicingOnly = true
        shippingOnly = false
    }

    fun onlyShipping() {
        shippingOnly = true
        slicingOnly = false
    }

    fun limit(n: Int) {
        limit = n.coerceIn(1, 1000)
    }
}

/**
 * IvmDslMarker - DSL 타입 안전성 보장
 */
@DslMarker
annotation class IvmDslMarker
