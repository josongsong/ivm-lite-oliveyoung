package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.pkg.orchestration.application.DeployJobRepositoryPort
import com.oliveyoung.ivmlite.sdk.model.DeployJobStatus
import com.oliveyoung.ivmlite.sdk.model.DeployResult
import com.oliveyoung.ivmlite.sdk.model.DeployState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Deploy 상태 조회 API
 * RFC-IMPL-011 Wave 5-K, Wave 6
 */
class DeployStatusApi internal constructor(
    private val config: IvmClientConfig,
    private val repository: DeployJobRepositoryPort? = null
) {
    /**
     * 특정 Job의 상태 조회
     * @throws IllegalArgumentException jobId가 빈 문자열인 경우
     */
    suspend fun status(jobId: String): DeployJobStatus {
        require(jobId.isNotBlank()) { "jobId must not be blank" }

        // Repository가 있으면 실제 조회
        if (repository != null) {
            when (val result = repository.get(jobId)) {
                is DeployJobRepositoryPort.Result.Ok -> {
                    val record = result.value
                    if (record != null) {
                        return DeployJobStatus(
                            jobId = record.jobId,
                            state = parseState(record.state),
                            entityKey = record.entityKey,
                            version = record.version,
                            error = record.error,
                            createdAt = record.createdAt,
                            updatedAt = record.updatedAt
                        )
                    }
                }
                is DeployJobRepositoryPort.Result.Err -> {
                    // 에러 시 기본값 반환
                }
            }
        }
        
        // 기본값 (Repository 없거나 조회 실패)
        return DeployJobStatus(
            jobId = jobId,
            state = DeployState.QUEUED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    private fun parseState(state: String): DeployState {
        return try {
            DeployState.valueOf(state)
        } catch (e: Exception) {
            DeployState.QUEUED
        }
    }

    /**
     * Job 완료 대기 (폴링)
     * @param jobId Job ID
     * @param timeout 최대 대기 시간 (기본 5분)
     * @param pollInterval 폴링 간격 (기본 1초)
     * @return DeployResult (성공/실패)
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    suspend fun await(
        jobId: String,
        timeout: Duration = Duration.ofMinutes(5),
        pollInterval: Duration = Duration.ofSeconds(1)
    ): DeployResult {
        // 경계값 검증
        require(jobId.isNotBlank()) { "jobId must not be blank" }
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive" }
        require(!pollInterval.isNegative && !pollInterval.isZero) { "pollInterval must be positive" }
        require(pollInterval <= timeout) { "pollInterval must not exceed timeout" }

        val startTime = Instant.now()
        val deadline = startTime.plus(timeout)

        while (true) {
            // 타임아웃 체크 (루프 시작 시)
            if (Instant.now().isAfter(deadline)) {
                return DeployResult.failure(jobId, "timeout", "Timeout waiting for job completion")
            }

            val status = status(jobId)
            when (status.state) {
                DeployState.DONE -> return DeployResult.success(jobId, "completed")
                DeployState.FAILED -> return DeployResult.failure(
                    jobId,
                    "failed",
                    status.error ?: "Unknown error"
                )
                // 진행 중 상태들: 명시적 처리
                DeployState.QUEUED,
                DeployState.RUNNING,
                DeployState.READY,
                DeployState.SINKING -> {
                    // 타임아웃 체크 (delay 전)
                    if (Instant.now().plus(pollInterval).isAfter(deadline)) {
                        // delay하면 타임아웃되므로 바로 반환
                        return DeployResult.failure(jobId, "timeout", "Timeout waiting for job completion")
                    }
                    delay(pollInterval.toMillis())
                }
            }
        }
    }
}
