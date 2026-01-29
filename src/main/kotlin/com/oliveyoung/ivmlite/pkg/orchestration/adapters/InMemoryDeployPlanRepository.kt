package com.oliveyoung.ivmlite.pkg.orchestration.adapters

import com.oliveyoung.ivmlite.shared.domain.deploy.DeployPlan
import com.oliveyoung.ivmlite.shared.domain.deploy.DependencyGraph
import com.oliveyoung.ivmlite.shared.domain.deploy.ExecutionStep
import com.oliveyoung.ivmlite.shared.domain.deploy.GraphNode
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * DeployPlan 저장소 인터페이스
 */
interface DeployPlanRepositoryPort {
    suspend fun save(plan: DeployPlanRecord): Result<DeployPlanRecord>
    suspend fun get(deployId: String): Result<DeployPlanRecord?>
    suspend fun getByEntityKey(entityKey: String): Result<List<DeployPlanRecord>>
    
    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val error: String) : Result<Nothing>
    }
}

/**
 * DeployPlan 레코드
 */
data class DeployPlanRecord(
    val deployId: String,
    val entityKey: String,
    val version: String,
    val graph: Map<String, List<String>>,  // 의존성 그래프
    val activatedRules: List<String>,
    val executionSteps: List<StepRecord>,
    val createdAt: Instant = Instant.now()
) {
    fun toDeployPlan(): DeployPlan = DeployPlan(
        deployId = deployId,
        graph = DependencyGraph(graph.map { (k, v) -> 
            k to GraphNode(id = k, dependencies = v, provides = emptyList())
        }.toMap()),
        activatedRules = activatedRules,
        executionSteps = executionSteps.mapIndexed { index, it -> it.toExecutionStep(index) }
    )
}

data class StepRecord(
    val sliceRef: String,
    val dependencies: List<String> = emptyList()
) {
    fun toExecutionStep(stepNumber: Int): ExecutionStep = ExecutionStep(
        stepNumber = stepNumber,
        sliceRef = sliceRef,
        dependencies = dependencies
    )
}

/**
 * InMemory DeployPlan Repository (테스트/개발용)
 */
class InMemoryDeployPlanRepository : DeployPlanRepositoryPort, HealthCheckable {
    
    override val healthName: String = "deploy-plan-repo"
    
    private val storage = ConcurrentHashMap<String, DeployPlanRecord>()
    
    override suspend fun save(plan: DeployPlanRecord): DeployPlanRepositoryPort.Result<DeployPlanRecord> {
        storage[plan.deployId] = plan
        return DeployPlanRepositoryPort.Result.Ok(plan)
    }
    
    override suspend fun get(deployId: String): DeployPlanRepositoryPort.Result<DeployPlanRecord?> {
        return DeployPlanRepositoryPort.Result.Ok(storage[deployId])
    }
    
    override suspend fun getByEntityKey(entityKey: String): DeployPlanRepositoryPort.Result<List<DeployPlanRecord>> {
        val plans = storage.values.filter { it.entityKey == entityKey }
            .sortedByDescending { it.createdAt }
        return DeployPlanRepositoryPort.Result.Ok(plans)
    }
    
    override suspend fun healthCheck(): Boolean = true
    
    // ===== 테스트 헬퍼 =====
    
    fun getAll(): List<DeployPlanRecord> = storage.values.toList()
    
    fun clear() = storage.clear()
}
