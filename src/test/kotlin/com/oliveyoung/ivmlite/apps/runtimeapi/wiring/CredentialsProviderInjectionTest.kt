package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.integration.IntegrationTag
import com.oliveyoung.ivmlite.integration.PostgresTestContainer
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.CacheConfig
import com.oliveyoung.ivmlite.shared.config.ContractsConfig
import com.oliveyoung.ivmlite.shared.config.DatabaseConfig
import com.oliveyoung.ivmlite.shared.config.DynamoDbConfig
import com.oliveyoung.ivmlite.shared.config.KafkaConfig
import com.oliveyoung.ivmlite.shared.config.ObservabilityConfig
import com.oliveyoung.ivmlite.shared.config.ServerConfig
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import org.koin.dsl.module
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

/**
 * CredentialsProvider 주입 통합 테스트
 *
 * infraModule + credentialsModule 조합 시 DynamoDbAsyncClient 정상 생성 검증.
 * (IntegrationTag - Docker 필요, integrationTest에서 실행)
 */
class CredentialsProviderInjectionTest : DescribeSpec({

    tags(IntegrationTag)

    afterEach {
        stopKoin()
    }

    describe("infraModule + credentialsModule") {
        it("주입된 Provider가 있으면 DynamoDbAsyncClient가 정상 생성된다") {
            if (!PostgresTestContainer.isDockerAvailable) return@it

            val injectedProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("injected-key", "injected-secret")
            )

            PostgresTestContainer.start()
            val testConfig = createTestConfig()

            startKoin {
                modules(
                    credentialsModule(injectedProvider),
                    module { single<AppConfig> { testConfig } },
                    infraModule
                )
            }

            val dynamoClient = org.koin.core.context.GlobalContext.get().get<DynamoDbAsyncClient>()
            dynamoClient shouldNotBe null
        }

        it("주입된 Provider가 없으면 Config 기반으로 DynamoDbAsyncClient가 생성된다") {
            if (!PostgresTestContainer.isDockerAvailable) return@it

            PostgresTestContainer.start()
            val baseConfig = createTestConfig()
            val testConfig = baseConfig.copy(
                dynamodb = baseConfig.dynamodb.copy(
                    endpoint = "http://localhost:8000",
                    accessKeyId = "dummy",
                    secretAccessKey = "dummy"
                )
            )

            startKoin {
                modules(
                    module { single<AppConfig> { testConfig } },
                    infraModule
                )
            }

            val dynamoClient = org.koin.core.context.GlobalContext.get().get<DynamoDbAsyncClient>()
            dynamoClient shouldNotBe null
        }
    }
})

private fun createTestConfig(): AppConfig = AppConfig(
        server = ServerConfig(),
        database = DatabaseConfig(
            url = PostgresTestContainer.jdbcUrl(),
            user = PostgresTestContainer.username(),
            password = PostgresTestContainer.password()
        ),
        dynamodb = DynamoDbConfig(
            endpoint = System.getenv("DYNAMODB_ENDPOINT") ?: "http://localhost:8000",
            region = System.getenv("AWS_REGION") ?: "ap-northeast-2",
            tableName = "ivm-lite-schema-registry-test",
            accessKeyId = "dummy",
            secretAccessKey = "dummy"
        ),
        kafka = KafkaConfig(),
        contracts = ContractsConfig(),
        observability = ObservabilityConfig(),
        worker = WorkerConfig(),
        cache = CacheConfig()
    )
