package com.oliveyoung.ivmlite.pkg.orchestration.application

import com.oliveyoung.ivmlite.shared.domain.types.Result
import java.time.Instant

/**
 * DeployJob Repository Port
 *
 * Deploy Job의 상태를 추적하기 위한 포트.
 * Admin DeployStatusApi에서 사용.
 */
interface DeployJobRepositoryPort {
    suspend fun save(job: DeployJobRecord): Result<DeployJobRecord>
    suspend fun get(jobId: String): Result<DeployJobRecord?>
    suspend fun updateState(jobId: String, state: String, error: String? = null): Result<Unit>
}

/**
 * DeployJob Record
 */
data class DeployJobRecord(
    val jobId: String,
    val entityKey: String = "",
    val version: String = "",
    val state: String = "QUEUED",
    val error: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
