package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.shared.adapters.DatabaseConfig
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI

/**
 * Infrastructure Module (RFC-IMPL-009)
 * 
 * DB 커넥션, AWS 클라이언트 등 인프라 의존성.
 * wiring 위치: apps/runtimeapi/wiring/ (RFC-IMPL-009 P0)
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
    
    // jOOQ DSLContext
    single<DSLContext> {
        val dataSource: HikariDataSource = get()
        DatabaseConfig.createDSLContext(dataSource)
    }
    
    // DynamoDB Async Client
    single<DynamoDbAsyncClient> {
        val config: AppConfig = get()
        val builder = DynamoDbAsyncClient.builder()
            .region(Region.of(config.dynamodb.region))
        
        // 자격 증명 설정 (환경 변수 우선, 없으면 설정 파일)
        val credentialsProvider = when {
            // 환경 변수 또는 설정 파일에 명시적 자격 증명이 있으면 사용
            config.dynamodb.accessKeyId?.isNotBlank() == true &&
            config.dynamodb.secretAccessKey?.isNotBlank() == true -> {
                val accessKeyId = config.dynamodb.accessKeyId!!
                val secretAccessKey = config.dynamodb.secretAccessKey!!
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            }
            // 그 외에는 기본 자격 증명 체인 사용 (환경 변수, ~/.aws/credentials, IAM 역할 등)
            else -> DefaultCredentialsProvider.create()
        }
        builder.credentialsProvider(credentialsProvider)
        
        // 로컬 개발용 endpoint override
        config.dynamodb.endpoint?.let { endpoint ->
            builder.endpointOverride(URI.create(endpoint))
        }
        
        builder.build()
    }
}
