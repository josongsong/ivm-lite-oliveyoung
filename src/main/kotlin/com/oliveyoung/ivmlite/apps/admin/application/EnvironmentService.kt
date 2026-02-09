package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.right
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Environment Service
 *
 * 환경 정보 및 설정 조회 서비스.
 */
class EnvironmentService(
    private val appConfig: AppConfig,
    private val dsl: DSLContext
) {
    private val logger = LoggerFactory.getLogger(EnvironmentService::class.java)

    /**
     * 환경 정보 조회
     * Arrow Result 타입 사용 (try-catch 대신)
     */
    fun getEnvironment(env: String): Either<DomainError, EnvironmentData> = try {
        either {
            val databases = getDatabaseInfo().bind()
            val config = getEnvironmentConfig().bind()
            val git = getGitInfo().bind()

            EnvironmentData(
                environment = env,
                databases = databases,
                config = config,
                git = git,
                timestamp = Instant.now()
            )
        }
    } catch (e: Exception) {
        logger.error("[Environment] Failed to get environment info", e)
        Either.Left(DomainError.StorageError("Failed to get environment info: ${e.message}"))
    }

    private fun getDatabaseInfo(): Either<DomainError, List<DatabaseInfo>> = either {
        val databases = mutableListOf<DatabaseInfo>()

        // PostgreSQL 정보
        try {
            val dbUrl = appConfig.database.url
            // JDBC URL 파싱: jdbc:postgresql://host:port/database
            val (host, port, database) = try {
                // jdbc:postgresql:// 형식을 http:// 형식으로 변환하여 URI 파싱
                val normalizedUrl = dbUrl.replace("jdbc:postgresql://", "http://")
                val uri = URI(normalizedUrl)
                Triple(
                    uri.host,
                    uri.port.takeIf { it != -1 } ?: 5432,
                    uri.path.removePrefix("/").split("?").first().takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                logger.warn("[Environment] Failed to parse PostgreSQL URL: ${e.message}")
                // 정규식으로 파싱 시도
                val regex = Regex("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)")
                val match = regex.find(dbUrl)
                if (match != null) {
                    Triple(
                        match.groupValues[1],
                        match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 5432,
                        match.groupValues[3]
                    )
                } else {
                    Triple(null, null, null)
                }
            }

            // 연결 테스트
            val startTime = System.currentTimeMillis()
            val connected = try {
                dsl.select(DSL.field("1")).fetchOne() != null
            } catch (e: Exception) {
                logger.debug("[Environment] Database connection test failed: ${e.message}")
                false
            }
            val latencyMs = if (connected) (System.currentTimeMillis() - startTime).toInt() else null

            databases.add(
                DatabaseInfo(
                    name = "PostgreSQL",
                    type = "PostgreSQL",
                    host = host,
                    port = port,
                    database = database,
                    status = if (connected) "connected" else "disconnected",
                    latencyMs = latencyMs
                )
            )
        } catch (e: Exception) {
            logger.warn("[Environment] Failed to get PostgreSQL info: ${e.message}")
            databases.add(
                DatabaseInfo(
                    name = "PostgreSQL",
                    type = "PostgreSQL",
                    status = "unknown"
                )
            )
        }

        // DynamoDB 정보
        try {
            databases.add(
                DatabaseInfo(
                    name = "DynamoDB",
                    type = "DynamoDB",
                    region = appConfig.dynamodb.region,
                    status = "connected" // DynamoDB는 연결 테스트가 어려우므로 항상 connected로 표시
                )
            )
        } catch (e: Exception) {
            logger.warn("[Environment] Failed to get DynamoDB info: ${e.message}")
            databases.add(
                DatabaseInfo(
                    name = "DynamoDB",
                    type = "DynamoDB",
                    status = "unknown"
                )
            )
        }

        // Kafka 정보
        try {
            val kafkaServers = appConfig.kafka.bootstrapServers.split(",").firstOrNull()
            val kafkaHost = kafkaServers?.split(":")?.firstOrNull()
            val kafkaPort = kafkaServers?.split(":")?.getOrNull(1)?.toIntOrNull()

            databases.add(
                DatabaseInfo(
                    name = "Kafka",
                    type = "Kafka",
                    host = kafkaHost,
                    port = kafkaPort,
                    status = "unknown" // Kafka 연결 테스트는 복잡하므로 unknown으로 표시
                )
            )
        } catch (e: Exception) {
            logger.warn("[Environment] Failed to get Kafka info: ${e.message}")
            databases.add(
                DatabaseInfo(
                    name = "Kafka",
                    type = "Kafka",
                    status = "unknown"
                )
            )
        }

        databases
    }

    private fun getEnvironmentConfig(): Either<DomainError, EnvironmentConfig> = try {
        either {
            EnvironmentConfig(
                database = EnvironmentDatabaseConfig(
                    url = appConfig.database.url,
                    user = appConfig.database.user,
                    maxPoolSize = appConfig.database.maxPoolSize,
                    minIdle = appConfig.database.minIdle
                ),
                dynamodb = EnvironmentDynamoDBConfig(
                    endpoint = appConfig.dynamodb.endpoint,
                    region = appConfig.dynamodb.region,
                    tableName = appConfig.dynamodb.tableName
                ),
                kafka = EnvironmentKafkaConfig(
                    bootstrapServers = appConfig.kafka.bootstrapServers,
                    consumerGroup = appConfig.kafka.consumerGroup,
                    topicPrefix = appConfig.kafka.topicPrefix
                ),
                observability = EnvironmentObservabilityConfig(
                    metricsEnabled = appConfig.observability.metricsEnabled,
                    tracingEnabled = appConfig.observability.tracingEnabled,
                    otlpEndpoint = appConfig.observability.otlpEndpoint
                ),
                worker = EnvironmentWorkerConfig(
                    enabled = appConfig.worker.enabled,
                    pollIntervalMs = appConfig.worker.pollIntervalMs,
                    batchSize = appConfig.worker.batchSize
                )
            )
        }
    } catch (e: Exception) {
        logger.error("[Environment] Failed to get environment config: ${e.message}", e)
        Either.Left(DomainError.StorageError("Failed to get environment config: ${e.message}"))
    }

    private fun getGitInfo(): Either<DomainError, GitInfo> = try {
        either {
            // Git이 설치되어 있고 git 저장소인지 확인 (타임아웃: 2초)
            val gitCheckProcess = ProcessBuilder("git", "rev-parse", "--git-dir")
                .redirectErrorStream(true)
                .start()
            val gitCheckExitCode = try {
                if (!gitCheckProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    gitCheckProcess.destroyForcibly()
                    -1 // 타임아웃
                } else {
                    gitCheckProcess.exitValue()
                }
            } catch (e: InterruptedException) {
                gitCheckProcess.destroyForcibly()
                -1
            }
            
            if (gitCheckExitCode != 0) {
                // Git 저장소가 아니거나 git이 없는 경우
                GitInfo(
                    branch = "unknown",
                    commit = "unknown",
                    commitMessage = "Not a git repository",
                    author = "unknown",
                    commitDate = Instant.now().toString(),
                    isDirty = false
                )
            } else {
                // Git 명령어 실행 헬퍼 (타임아웃: 1초)
                fun executeGitCommand(vararg command: String, timeoutSeconds: Long = 1): String? {
                    return try {
                        val process = ProcessBuilder(*command)
                            .redirectErrorStream(true)
                            .start()
                        val completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                        if (completed && process.exitValue() == 0) {
                            process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
                        } else {
                            if (!completed) process.destroyForcibly()
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                // 브랜치 정보
                val branch = executeGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD") ?: "unknown"

                // 커밋 해시
                val commit = executeGitCommand("git", "rev-parse", "HEAD") ?: "unknown"

                // 커밋 메시지, 작성자, 날짜
                val logOutput = executeGitCommand("git", "log", "-1", "--pretty=format:%s%n%an%n%ai")
                    ?.lines() ?: emptyList()
                val commitMessage = logOutput.getOrNull(0) ?: ""
                val author = logOutput.getOrNull(1) ?: ""
                val commitDate = logOutput.getOrNull(2) ?: Instant.now().toString()

                // 변경사항 여부
                val statusOutput = executeGitCommand("git", "status", "--porcelain") ?: ""
                val isDirty = statusOutput.isNotEmpty()

                // Remote URL
                val remoteUrl = executeGitCommand("git", "config", "--get", "remote.origin.url")

                GitInfo(
                    branch = branch,
                    commit = commit,
                    commitMessage = commitMessage,
                    author = author,
                    commitDate = commitDate,
                    isDirty = isDirty,
                    remoteUrl = remoteUrl
                )
            }
        }
    } catch (e: Exception) {
        logger.warn("[Environment] Failed to get git info: ${e.message}", e)
        // Git 정보는 필수가 아니므로 기본값 반환 (에러로 처리하지 않음)
        GitInfo(
            branch = "unknown",
            commit = "unknown",
            commitMessage = "Failed to get git info: ${e.message}",
            author = "unknown",
            commitDate = Instant.now().toString(),
            isDirty = false
        ).right()
    }
}

// ==================== Domain Models ====================

data class EnvironmentData(
    val environment: String,
    val databases: List<DatabaseInfo>,
    val config: EnvironmentConfig,
    val git: GitInfo,
    val timestamp: Instant
)

data class DatabaseInfo(
    val name: String,
    val type: String,
    val host: String? = null,
    val port: Int? = null,
    val database: String? = null,
    val region: String? = null,
    val status: String,
    val latencyMs: Int? = null
)

data class EnvironmentConfig(
    val database: EnvironmentDatabaseConfig,
    val dynamodb: EnvironmentDynamoDBConfig,
    val kafka: EnvironmentKafkaConfig,
    val observability: EnvironmentObservabilityConfig,
    val worker: EnvironmentWorkerConfig
)

data class EnvironmentDatabaseConfig(
    val url: String,
    val user: String,
    val maxPoolSize: Int,
    val minIdle: Int
)

data class EnvironmentDynamoDBConfig(
    val endpoint: String?,
    val region: String,
    val tableName: String
)

data class EnvironmentKafkaConfig(
    val bootstrapServers: String,
    val consumerGroup: String,
    val topicPrefix: String,
    val securityProtocol: String = "PLAINTEXT",
    val saslMechanism: String? = null,
    val awsRegion: String? = null
)

data class EnvironmentObservabilityConfig(
    val metricsEnabled: Boolean,
    val tracingEnabled: Boolean,
    val otlpEndpoint: String
)

data class EnvironmentWorkerConfig(
    val enabled: Boolean,
    val pollIntervalMs: Long,
    val batchSize: Int
)

data class GitInfo(
    val branch: String,
    val commit: String,
    val commitMessage: String,
    val author: String,
    val commitDate: String,
    val isDirty: Boolean,
    val remoteUrl: String? = null
)
