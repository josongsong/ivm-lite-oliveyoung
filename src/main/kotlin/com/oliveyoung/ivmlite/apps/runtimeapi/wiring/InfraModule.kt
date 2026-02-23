package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.shared.adapters.DatabaseConfig
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI

/**
 * Infrastructure Module (RFC-IMPL-009)
 *
 * DB 커넥션, AWS 클라이언트 등 인프라 의존성.
 * wiring 위치: apps/runtimeapi/wiring/ (RFC-IMPL-009 P0)
 *
 * ## CredentialsProvider 주입 (외부 앱 embed 시)
 *
 * 외부 앱이 IVM-Lite를 라이브러리로 embed할 때, 자체 CredentialsProvider를 주입하려면
 * infraModule보다 먼저 다음 모듈을 로드하세요:
 *
 * ```kotlin
 * val myCredentialsModule = module {
 *     single<AwsCredentialsProvider> {
 *         // 자체 IAM/STS/WebIdentity 등
 *         MyAppCredentialsProvider.create()
 *     }
 * }
 * loadKoinModules(listOf(myCredentialsModule) + productionModules)
 * ```
 *
 * 우선순위: 주입된 AwsCredentialsProvider > awsProfile > accessKey/secretKey > DefaultCredentialsProvider
 */
val infraModule = module {

    // HikariCP DataSource
    single<HikariDataSource> {
        val config: AppConfig = get()
        DatabaseConfig.createDataSource(
            DatabaseConfig.DbProperties(
                url = config.database.url,
                user = config.database.user,
                password = config.database.password,
                maxPoolSize = config.database.maxPoolSize,
                minIdle = config.database.minIdle,
            )
        )
    }

    // Exposed Database
    single<Database> {
        val dataSource: HikariDataSource = get()
        DatabaseConfig.connectDatabase(dataSource)
    }

    // DynamoDB Async Client
    single<DynamoDbAsyncClient> {
        val config: AppConfig = get()
        val builder = DynamoDbAsyncClient.builder()
            .region(Region.of(config.dynamodb.region))

        // 자격 증명 설정 (우선순위: 주입된 Provider > awsProfile > accessKey/secretKey > 기본 체인)
        val credentialsProvider = getOrNull<AwsCredentialsProvider>()
            ?: resolveCredentialsFromConfig(config)
        builder.credentialsProvider(credentialsProvider)

        // endpoint override는 opt-in (기본은 AWS 엔드포인트 사용)
        config.dynamodb.endpoint
            ?.takeIf { it.isNotBlank() }
            ?.let { endpoint -> builder.endpointOverride(URI.create(endpoint)) }

        builder.build()
    }
}

/**
 * Config 기반 자격 증명 생성 (주입된 Provider가 없을 때 사용)
 */
private fun resolveCredentialsFromConfig(config: AppConfig): AwsCredentialsProvider = when {
    config.dynamodb.awsProfile?.isNotBlank() == true ->
        ProfileCredentialsProvider.create(config.dynamodb.awsProfile)
    config.dynamodb.accessKeyId?.isNotBlank() == true &&
        config.dynamodb.secretAccessKey?.isNotBlank() == true -> {
        val accessKeyId = config.dynamodb.accessKeyId
        val secretAccessKey = config.dynamodb.secretAccessKey
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        )
    }
    else -> DefaultCredentialsProvider.create()
}
