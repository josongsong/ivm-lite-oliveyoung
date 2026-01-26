package com.oliveyoung.ivmlite.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.net.URI

/**
 * DynamoDB Local 간단한 연결 테스트
 * 
 * - DynamoDB 연결 확인
 * - 테이블 생성
 * - 데이터 쓰기/읽기
 * 
 * 실행 전: docker-compose up -d dynamodb
 */
class DynamoDbSimpleTest : StringSpec({

    val client = DynamoDbAsyncClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")
            )
        )
        .build()

    val testTable = "test-simple-${System.currentTimeMillis()}"

    "DynamoDB Local 연결 테스트" {
        val tables = runBlocking {
            try {
                client.listTables().await().tableNames()
            } catch (e: Exception) {
                println("❌ DynamoDB Local 연결 실패: ${e.message}")
                println("   docker-compose up -d dynamodb를 실행하세요")
                return@runBlocking null
            }
        }
        
        println("✅ DynamoDB Local 연결 성공! 기존 테이블: $tables")
        tables shouldNotBe null
    }

    "테이블 생성 및 데이터 CRUD" {
        // 1. 테이블 생성
        runBlocking {
            try {
                client.createTable {
                    it.tableName(testTable)
                    it.attributeDefinitions(
                        AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build()
                    )
                    it.keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()
                    )
                    it.billingMode(BillingMode.PAY_PER_REQUEST)
                }.await()
                
                println("✅ 테이블 생성: $testTable")
                
                // 테이블 생성 대기
                var retries = 0
                while (retries < 10) {
                    kotlinx.coroutines.delay(500)
                    val status = client.describeTable { it.tableName(testTable) }.await().table().tableStatus()
                    if (status == TableStatus.ACTIVE) {
                        println("✅ 테이블 ACTIVE")
                        break
                    }
                    retries++
                }
            } catch (e: ResourceInUseException) {
                println("ℹ️  테이블이 이미 존재합니다")
            }
        }

        // 2. 데이터 쓰기
        runBlocking {
            client.putItem {
                it.tableName(testTable)
                it.item(
                    mapOf(
                        "PK" to AttributeValue.builder().s("TEST#001").build(),
                        "SK" to AttributeValue.builder().s("DATA#001").build(),
                        "payload" to AttributeValue.builder().s("{\"name\": \"테스트 상품\"}").build()
                    )
                )
            }.await()
            println("✅ 데이터 쓰기 성공")
        }

        // 3. 데이터 읽기
        val item = runBlocking {
            client.getItem {
                it.tableName(testTable)
                it.key(
                    mapOf(
                        "PK" to AttributeValue.builder().s("TEST#001").build(),
                        "SK" to AttributeValue.builder().s("DATA#001").build()
                    )
                )
            }.await().item()
        }

        println("✅ 데이터 읽기 성공: ${item["payload"]?.s()}")
        item["payload"]?.s() shouldBe "{\"name\": \"테스트 상품\"}"

        // 4. 테이블 삭제
        runBlocking {
            try {
                client.deleteTable { it.tableName(testTable) }.await()
                println("✅ 테이블 삭제: $testTable")
            } catch (e: Exception) {
                println("⚠️  테이블 삭제 실패: ${e.message}")
            }
        }
    }
})
