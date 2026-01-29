package com.oliveyoung.ivmlite.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI

/**
 * DynamoDB endpoint override 빠른 연결 테스트
 *
 * Remote-only 정책: 기본 테스트에서는 실행되지 않도록 IntegrationTag + endpoint opt-in.
 */
class DynamoDbQuickTest : StringSpec(init@{
    tags(IntegrationTag)

    val endpoint = System.getenv("DYNAMODB_ENDPOINT") ?: ""
    if (endpoint.isBlank()) return@init

    "DynamoDB Local 연결만 확인 (5초 타임아웃)".config(timeout = kotlin.time.Duration.parse("5s")) {
        val client = DynamoDbAsyncClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(System.getenv("AWS_REGION") ?: "ap-northeast-2"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")
                )
            )
            .build()

        val tables = runBlocking {
            try {
                val result = client.listTables().await()
                result.tableNames()
            } catch (e: Exception) {
                println("❌ 연결 실패: ${e.javaClass.simpleName} - ${e.message}")
                null
            }
        }

        println("✅ DynamoDB 연결 성공! 테이블: $tables")
        tables shouldNotBe null
    }
})
