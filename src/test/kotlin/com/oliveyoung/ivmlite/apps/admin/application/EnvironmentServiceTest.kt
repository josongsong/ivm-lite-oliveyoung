package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import com.oliveyoung.ivmlite.shared.config.*
import io.mockk.mockk
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * EnvironmentService 단위 테스트
 *
 * 환경 정보 조회 및 설정 관련 테스트
 */
class EnvironmentServiceTest {

    private lateinit var appConfig: AppConfig
    private lateinit var dsl: DSLContext
    private lateinit var service: EnvironmentService

    @BeforeEach
    fun setup() {
        appConfig = createTestAppConfig()
        dsl = mockk(relaxed = true)
        service = EnvironmentService(appConfig, dsl)
    }

    // ==================== getEnvironment 테스트 ====================

    @Test
    fun `getEnvironment - 기본 환경 정보 조회 성공`() {
        // Given
        val env = "development"

        // When
        val result = service.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        assertEquals(env, envData.environment)
        assertNotNull(envData.databases)
        assertNotNull(envData.config)
        assertNotNull(envData.git)
        assertNotNull(envData.timestamp)
    }

    @Test
    fun `getEnvironment - 데이터베이스 정보 포함 확인`() {
        // Given
        val env = "production"

        // When
        val result = service.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        assertTrue(envData.databases.isNotEmpty())

        // PostgreSQL 정보 확인
        val postgres = envData.databases.find { it.name == "PostgreSQL" }
        assertNotNull(postgres)
        assertEquals("PostgreSQL", postgres.type)

        // DynamoDB 정보 확인
        val dynamo = envData.databases.find { it.name == "DynamoDB" }
        assertNotNull(dynamo)
        assertEquals("DynamoDB", dynamo.type)

        // Kafka 정보 확인
        val kafka = envData.databases.find { it.name == "Kafka" }
        assertNotNull(kafka)
        assertEquals("Kafka", kafka.type)
    }

    @Test
    fun `getEnvironment - 설정 정보 포함 확인`() {
        // Given
        val env = "test"

        // When
        val result = service.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val config = envData.config

        // Database config
        assertEquals("jdbc:postgresql://localhost:5432/testdb", config.database.url)
        assertEquals("testuser", config.database.user)
        assertEquals(10, config.database.maxPoolSize)

        // DynamoDB config
        assertEquals("ap-northeast-2", config.dynamodb.region)
        assertEquals("test-table", config.dynamodb.tableName)

        // Kafka config
        assertEquals("localhost:9092", config.kafka.bootstrapServers)
        assertEquals("test-group", config.kafka.consumerGroup)

        // Observability config
        assertTrue(config.observability.metricsEnabled)
        assertTrue(config.observability.tracingEnabled)

        // Worker config
        assertTrue(config.worker.enabled)
        assertEquals(1000L, config.worker.pollIntervalMs)
    }

    @Test
    fun `getEnvironment - Git 정보 포함 확인`() {
        // Given
        val env = "development"

        // When
        val result = service.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val git = envData.git

        assertNotNull(git.branch)
        assertNotNull(git.commit)
        assertNotNull(git.commitDate)
    }

    // ==================== URL 파싱 테스트 ====================

    @Test
    fun `getEnvironment - PostgreSQL URL 파싱 성공`() {
        // Given
        val config = createTestAppConfig(
            dbUrl = "jdbc:postgresql://db.example.com:5432/mydb"
        )
        val localService = EnvironmentService(config, dsl)
        val env = "development"

        // When
        val result = localService.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val postgres = envData.databases.find { it.name == "PostgreSQL" }
        assertNotNull(postgres)
        assertEquals("db.example.com", postgres.host)
        assertEquals(5432, postgres.port)
        assertEquals("mydb", postgres.database)
    }

    @Test
    fun `getEnvironment - 기본 포트 없는 URL 파싱`() {
        // Given
        val config = createTestAppConfig(
            dbUrl = "jdbc:postgresql://localhost/testdb"
        )
        val localService = EnvironmentService(config, dsl)
        val env = "development"

        // When
        val result = localService.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val postgres = envData.databases.find { it.name == "PostgreSQL" }
        assertNotNull(postgres)
        assertEquals("localhost", postgres.host)
        assertEquals(5432, postgres.port) // 기본 포트
    }

    @Test
    fun `getEnvironment - 쿼리 파라미터 포함 URL 파싱`() {
        // Given
        val config = createTestAppConfig(
            dbUrl = "jdbc:postgresql://localhost:5432/testdb?ssl=true&sslmode=require"
        )
        val localService = EnvironmentService(config, dsl)
        val env = "development"

        // When
        val result = localService.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val postgres = envData.databases.find { it.name == "PostgreSQL" }
        assertNotNull(postgres)
        assertEquals("testdb", postgres.database) // 쿼리 파라미터 제외
    }

    // ==================== Kafka 파싱 테스트 ====================

    @Test
    fun `getEnvironment - Kafka bootstrap servers 파싱`() {
        // Given
        val config = createTestAppConfig(
            kafkaServers = "kafka1:9092,kafka2:9093"
        )
        val localService = EnvironmentService(config, dsl)
        val env = "development"

        // When
        val result = localService.getEnvironment(env)

        // Then
        assertTrue(result is Either.Right)
        val envData = (result as Either.Right).value
        val kafka = envData.databases.find { it.name == "Kafka" }
        assertNotNull(kafka)
        assertEquals("kafka1", kafka.host)
        assertEquals(9092, kafka.port)
    }

    // ==================== DTO 테스트 ====================

    @Test
    fun `EnvironmentData - 생성 확인`() {
        val data = EnvironmentData(
            environment = "test",
            databases = listOf(
                DatabaseInfo(name = "Test", type = "PostgreSQL", status = "connected")
            ),
            config = EnvironmentConfig(
                database = EnvironmentDatabaseConfig("url", "user", 10, 2),
                dynamodb = EnvironmentDynamoDBConfig(null, "ap-northeast-2", "table"),
                kafka = EnvironmentKafkaConfig("localhost:9092", "group", "prefix"),
                observability = EnvironmentObservabilityConfig(true, true, "http://localhost:4317"),
                worker = EnvironmentWorkerConfig(true, 1000, 100)
            ),
            git = GitInfo("main", "abc123", "commit", "author", "2024-01-01", false),
            timestamp = java.time.Instant.now()
        )

        assertEquals("test", data.environment)
        assertEquals(1, data.databases.size)
    }

    @Test
    fun `DatabaseInfo - 모든 속성 확인`() {
        val info = DatabaseInfo(
            name = "PostgreSQL",
            type = "PostgreSQL",
            host = "localhost",
            port = 5432,
            database = "testdb",
            region = null,
            status = "connected",
            latencyMs = 10
        )

        assertEquals("PostgreSQL", info.name)
        assertEquals("localhost", info.host)
        assertEquals(5432, info.port)
        assertEquals("testdb", info.database)
        assertEquals("connected", info.status)
        assertEquals(10, info.latencyMs)
    }

    @Test
    fun `GitInfo - 생성 확인`() {
        val info = GitInfo(
            branch = "main",
            commit = "abc123def456",
            commitMessage = "Initial commit",
            author = "Test Author",
            commitDate = "2024-01-01T00:00:00Z",
            isDirty = true,
            remoteUrl = "https://github.com/test/repo.git"
        )

        assertEquals("main", info.branch)
        assertEquals("abc123def456", info.commit)
        assertTrue(info.isDirty)
        assertEquals("https://github.com/test/repo.git", info.remoteUrl)
    }

    // ==================== Helper Functions ====================

    private fun createTestAppConfig(
        dbUrl: String = "jdbc:postgresql://localhost:5432/testdb",
        kafkaServers: String = "localhost:9092"
    ): AppConfig {
        return AppConfig(
            server = ServerConfig(host = "0.0.0.0", port = 8081),
            database = DatabaseConfig(
                url = dbUrl,
                user = "testuser",
                password = "testpass",
                maxPoolSize = 10,
                minIdle = 2
            ),
            dynamodb = DynamoDbConfig(
                endpoint = null,
                region = "ap-northeast-2",
                tableName = "test-table"
            ),
            kafka = KafkaConfig(
                bootstrapServers = kafkaServers,
                consumerGroup = "test-group",
                topicPrefix = "test-prefix"
            ),
            contracts = ContractsConfig(resourcePath = "/contracts/v1"),
            observability = ObservabilityConfig(
                metricsEnabled = true,
                tracingEnabled = true,
                otlpEndpoint = "http://localhost:4317"
            ),
            worker = WorkerConfig(
                enabled = true,
                pollIntervalMs = 1000,
                batchSize = 100
            ),
            cache = CacheConfig(
                enabled = true,
                ttlMs = 300_000
            )
        )
    }
}
