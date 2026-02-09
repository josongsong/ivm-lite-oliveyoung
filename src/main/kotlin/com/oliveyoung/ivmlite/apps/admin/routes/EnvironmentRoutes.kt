package com.oliveyoung.ivmlite.apps.admin.routes

import com.oliveyoung.ivmlite.apps.admin.application.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Environment Routes
 *
 * GET /api/environment - 환경 정보 조회
 */
fun Route.environmentRoutes() {
    val environmentService by inject<EnvironmentService>()

    /**
     * GET /environment
     * 환경 정보 조회
     *
     * Query Parameters:
     * - env: 환경 이름 (Dev, Staging, Prod 등)
     */
    get("/environment") {
        val env = call.request.queryParameters["env"] ?: "Dev"

        when (val result = environmentService.getEnvironment(env)) {
            is arrow.core.Either.Left -> {
                throw result.value
            }
            is arrow.core.Either.Right -> {
                call.respond(HttpStatusCode.OK, result.value.toResponse())
            }
        }
    }
}

// ==================== Response DTOs ====================

@Serializable
data class EnvironmentDataResponse(
    val environment: String,
    val databases: List<DatabaseInfoResponse>,
    val config: EnvironmentConfigResponse,
    val git: GitInfoResponse,
    val timestamp: String
)

@Serializable
data class DatabaseInfoResponse(
    val name: String,
    val type: String,
    val host: String? = null,
    val port: Int? = null,
    val database: String? = null,
    val region: String? = null,
    val status: String,
    val latencyMs: Int? = null
)

@Serializable
data class EnvironmentConfigResponse(
    val database: DatabaseConfigResponse,
    val dynamodb: DynamoDBConfigResponse,
    val kafka: KafkaConfigResponse,
    val observability: ObservabilityConfigResponse,
    val worker: WorkerConfigResponse
)

@Serializable
data class DatabaseConfigResponse(
    val url: String,
    val user: String,
    val maxPoolSize: Int,
    val minIdle: Int
)

@Serializable
data class DynamoDBConfigResponse(
    val endpoint: String?,
    val region: String,
    val tableName: String
)

@Serializable
data class KafkaConfigResponse(
    val bootstrapServers: String,
    val consumerGroup: String,
    val topicPrefix: String,
    val securityProtocol: String = "PLAINTEXT",
    val saslMechanism: String? = null,
    val awsRegion: String? = null
)

@Serializable
data class ObservabilityConfigResponse(
    val metricsEnabled: Boolean,
    val tracingEnabled: Boolean,
    val otlpEndpoint: String
)

@Serializable
data class WorkerConfigResponse(
    val enabled: Boolean,
    val pollIntervalMs: Long,
    val batchSize: Int
)

@Serializable
data class GitInfoResponse(
    val branch: String,
    val commit: String,
    val commitMessage: String,
    val author: String,
    val commitDate: String,
    val isDirty: Boolean,
    val remoteUrl: String? = null
)

// ==================== Domain → DTO 변환 ====================

private fun EnvironmentData.toResponse() = EnvironmentDataResponse(
    environment = environment,
    databases = databases.map { it.toResponse() },
    config = config.toResponse(),
    git = git.toResponse(),
    timestamp = timestamp.toString()
)

private fun DatabaseInfo.toResponse() = DatabaseInfoResponse(
    name = name,
    type = type,
    host = host,
    port = port,
    database = database,
    region = region,
    status = status,
    latencyMs = latencyMs
)

private fun EnvironmentConfig.toResponse() = EnvironmentConfigResponse(
    database = database.toResponse(),
    dynamodb = dynamodb.toResponse(),
    kafka = kafka.toResponse(),
    observability = observability.toResponse(),
    worker = worker.toResponse()
)

private fun EnvironmentDatabaseConfig.toResponse() = DatabaseConfigResponse(
    url = url,
    user = user,
    maxPoolSize = maxPoolSize,
    minIdle = minIdle
)

private fun EnvironmentDynamoDBConfig.toResponse() = DynamoDBConfigResponse(
    endpoint = endpoint,
    region = region,
    tableName = tableName
)

private fun EnvironmentKafkaConfig.toResponse() = KafkaConfigResponse(
    bootstrapServers = bootstrapServers,
    consumerGroup = consumerGroup,
    topicPrefix = topicPrefix,
    securityProtocol = securityProtocol,
    saslMechanism = saslMechanism,
    awsRegion = awsRegion
)

private fun EnvironmentObservabilityConfig.toResponse() = ObservabilityConfigResponse(
    metricsEnabled = metricsEnabled,
    tracingEnabled = tracingEnabled,
    otlpEndpoint = otlpEndpoint
)

private fun EnvironmentWorkerConfig.toResponse() = WorkerConfigResponse(
    enabled = enabled,
    pollIntervalMs = pollIntervalMs,
    batchSize = batchSize
)

private fun GitInfo.toResponse() = GitInfoResponse(
    branch = branch,
    commit = commit,
    commitMessage = commitMessage,
    author = author,
    commitDate = commitDate,
    isDirty = isDirty,
    remoteUrl = remoteUrl
)
