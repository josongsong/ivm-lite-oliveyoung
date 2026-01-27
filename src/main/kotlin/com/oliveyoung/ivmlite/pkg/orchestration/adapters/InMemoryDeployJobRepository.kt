package com.oliveyoung.ivmlite.pkg.orchestration.adapters

import com.oliveyoung.ivmlite.pkg.orchestration.application.DeployJobRecord
import com.oliveyoung.ivmlite.pkg.orchestration.application.DeployJobRepositoryPort
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory DeployJob Repository (테스트/개발용)
 */
class InMemoryDeployJobRepository : DeployJobRepositoryPort, HealthCheckable {
    
    override val healthName: String = "deploy-job-repo"
    
    private val storage = ConcurrentHashMap<String, DeployJobRecord>()
    
    override suspend fun save(job: DeployJobRecord): DeployJobRepositoryPort.Result<DeployJobRecord> {
        storage[job.jobId] = job
        return DeployJobRepositoryPort.Result.Ok(job)
    }
    
    override suspend fun get(jobId: String): DeployJobRepositoryPort.Result<DeployJobRecord?> {
        return DeployJobRepositoryPort.Result.Ok(storage[jobId])
    }
    
    override suspend fun updateState(
        jobId: String, 
        state: String, 
        error: String?
    ): DeployJobRepositoryPort.Result<Unit> {
        val existing = storage[jobId]
        if (existing != null) {
            storage[jobId] = existing.copy(
                state = state,
                error = error,
                updatedAt = Instant.now()
            )
        }
        return DeployJobRepositoryPort.Result.Ok(Unit)
    }
    
    override suspend fun healthCheck(): Boolean = true
    
    // ===== 테스트 헬퍼 =====
    
    fun getAll(): List<DeployJobRecord> = storage.values.toList()
    
    fun findByState(state: String): List<DeployJobRecord> = 
        storage.values.filter { it.state == state }
    
    fun clear() = storage.clear()
}
