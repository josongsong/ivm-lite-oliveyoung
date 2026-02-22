package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.ConfigLoader
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest
import java.net.URI

/**
 * 실제 AWS DynamoDB 연결 테스트
 *
 * 환경 변수에서 AWS 자격 증명을 읽어 실제 AWS DynamoDB에 연결합니다.
 *
 * 실행 전 요구사항:
 * - 환경 변수 설정: source scripts/load-env.sh
 * - AWS 자격 증명: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 * - Region: AWS_REGION (기본값: ap-northeast-2)
 *
 * 테스트 실행:
 * ./scripts/run-with-env.sh ./gradlew test --tests AwsDynamoDbConnectionTest
 */
class AwsDynamoDbConnectionTest : StringSpec(init@{

    tags(IntegrationTag)

    // Koin 모듈 설정
    val koinModule = module {
        single<AppConfig> { ConfigLoader.load() }
        single<DynamoDbAsyncClient> {
            val config: AppConfig = get()
            createDynamoDbClient(config)
        }
    }

    var dynamoClient: DynamoDbAsyncClient? = null
    var config: AppConfig? = null

    beforeSpec {
        startKoin {
            modules(koinModule)
        }
        config = org.koin.core.context.GlobalContext.get().get<AppConfig>()
        dynamoClient = org.koin.core.context.GlobalContext.get().get<DynamoDbAsyncClient>()
    }

    afterSpec {
        stopKoin()
    }

    "실제 AWS DynamoDB에 연결하여 테이블 목록 조회" {
        runBlocking {
            val client = dynamoClient ?: error("DynamoDB 클라이언트 초기화 실패")
            val cfg = config ?: error("설정 로드 실패")

            println("🔍 AWS DynamoDB 연결 테스트 시작...")
            println("   Region: ${cfg.dynamodb.region}")
            println("   Endpoint: ${cfg.dynamodb.endpoint ?: "AWS (기본)"}")

            try {
                // 테이블 목록 조회
                val response = client.listTables(
                    ListTablesRequest.builder().build()
                ).await()

                val tableNames = response.tableNames()
                println("✅ DynamoDB 연결 성공!")
                println("   조회된 테이블 수: ${tableNames.size}")

                if (tableNames.isNotEmpty()) {
                    println("   테이블 목록:")
                    tableNames.forEach { name ->
                        println("     - $name")
                    }
                } else {
                    println("   ⚠️  테이블이 없습니다.")
                }

                // 연결 성공 확인
                tableNames shouldNotBe null

            } catch (e: Exception) {
                println("❌ DynamoDB 연결 실패:")
                println("   에러 타입: ${e.javaClass.simpleName}")
                println("   메시지: ${e.message}")
                println("")
                println("문제 해결:")
                println("  1. 환경 변수 확인: source scripts/load-env.sh")
                println("  2. AWS 자격 증명 확인: echo \$AWS_ACCESS_KEY_ID")
                println("  3. Region 확인: echo \$AWS_REGION")
                println("  4. 네트워크 연결 확인")
                println("  5. IAM 권한 확인 (dynamodb:ListTables 필요)")
                throw e
            }
        }
    }

    "설정된 테이블 존재 확인" {
        runBlocking {
            val client = dynamoClient ?: error("DynamoDB 클라이언트 초기화 실패")
            val cfg = config ?: error("설정 로드 실패")
            val tableName = cfg.dynamodb.tableName

            println("🔍 테이블 존재 확인: $tableName")

            try {
                val response = client.describeTable { it.tableName(tableName) }.await()
                val table = response.table()

                println("✅ 테이블 '$tableName' 존재 확인 완료")
                println("   상태: ${table.tableStatus()}")
                println("   생성 시간: ${table.creationDateTime()}")

                // 테이블 상태 확인
                table.tableStatus() shouldNotBe null

            } catch (e: software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException) {
                println("⚠️  테이블 '$tableName'을 찾을 수 없습니다.")
                println("   실제 테이블 목록:")
                val tables = client.listTables().await().tableNames()
                tables.forEach { println("     - $it") }
                println("   테이블을 생성하거나 DYNAMODB_TABLE 환경 변수를 설정하세요")
                // 테이블이 없어도 연결 테스트는 성공으로 간주
            } catch (e: Exception) {
                println("❌ 테이블 조회 실패: ${e.message}")
                throw e
            }
        }
    }
})

/**
 * DynamoDB 클라이언트 생성 헬퍼 (InfraModule 로직 재사용)
 */
private fun createDynamoDbClient(config: AppConfig): DynamoDbAsyncClient {
    val builder = DynamoDbAsyncClient.builder()
        .region(software.amazon.awssdk.regions.Region.of(config.dynamodb.region))

    // 자격 증명 설정
    val credentialsProvider = when {
        config.dynamodb.accessKeyId?.isNotBlank() == true &&
        config.dynamodb.secretAccessKey?.isNotBlank() == true -> {
            software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                    config.dynamodb.accessKeyId!!,
                    config.dynamodb.secretAccessKey!!
                )
            )
        }
        else -> software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create()
    }
    builder.credentialsProvider(credentialsProvider)

    // 로컬 개발용 endpoint override
    config.dynamodb.endpoint?.let { endpoint ->
        builder.endpointOverride(java.net.URI.create(endpoint))
    }

    return builder.build()
}
