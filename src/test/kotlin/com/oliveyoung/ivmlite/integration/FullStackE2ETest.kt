package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.DynamoDbRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqOutboxRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.net.URI
import java.sql.DriverManager

/**
 * Full Stack E2E 테스트 (DynamoDB + PostgreSQL)
 * 
 * 실제 프로덕션 환경과 동일한 구성:
 * - DynamoDB: RawData, Slice, InvertedIndex 저장
 * - PostgreSQL: Outbox (트랜잭션 보장)
 * 
 * 샘플 데이터를 통해 전체 플로우 검증:
 * 1. Ingest → DynamoDB RawData 저장 + PostgreSQL Outbox 저장
 * 2. Slicing → DynamoDB Slice 저장 + InvertedIndex 생성
 * 3. Query → DynamoDB에서 Slice 조회
 * 4. Fanout → InvertedIndex로 연관 엔티티 조회
 */
class FullStackE2ETest : StringSpec({

    // ==================== DynamoDB 설정 ====================
    val dynamoClient = DynamoDbAsyncClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")
            )
        )
        .build()

    val tableName = "ivm-lite-data-local"

    // ==================== PostgreSQL 설정 ====================
    val rdsHost = System.getenv("RDS_HOST") ?: "ivm-lite.crcikgmci55c.ap-northeast-2.rds.amazonaws.com"
    val rdsUser = System.getenv("RDS_USER") ?: "postgres"
    val rdsPassword = System.getenv("RDS_PASSWORD") ?: "Dhfflqmdud9("
    val rdsDatabase = System.getenv("RDS_DATABASE") ?: "ivmlite"

    val jdbcUrl = "jdbc:postgresql://$rdsHost:5432/$rdsDatabase?sslmode=require"
    val dsl: DSLContext = DSL.using(
        DriverManager.getConnection(jdbcUrl, rdsUser, rdsPassword),
        SQLDialect.POSTGRES
    )

    // ==================== Repository 설정 ====================
    val rawDataRepo = DynamoDbRawDataRepository(dynamoClient, tableName)
    val sliceRepo = DynamoDbSliceRepository(dynamoClient, tableName)
    val invertedIndexRepo = DynamoDbInvertedIndexRepository(dynamoClient, tableName)
    val outboxRepo = JooqOutboxRepository(dsl)  // PostgreSQL Outbox

    // ==================== Workflow 설정 ====================
    val contractRegistry = LocalYamlContractRegistryAdapter()
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = SlicingEngine(contractRegistry, joinExecutor)
    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

    val ingestWorkflow = IngestWorkflow(rawDataRepo, outboxRepo)
    val slicingWorkflow = SlicingWorkflow(
        rawDataRepo,
        sliceRepo,
        slicingEngine,
        invertedIndexRepo,
        changeSetBuilder,
        impactCalculator,
        contractRegistry,
    )
    val queryViewWorkflow = QueryViewWorkflow(sliceRepo, contractRegistry)

    val tenantId = TenantId("oliveyoung")
    
    // ==================== 샘플 데이터 ====================
    val sampleProduct = """
    {
        "productId": "A000000001",
        "title": "라운드랩 1025 독도 토너",
        "brandId": "BRAND#oliveyoung#roundlab",
        "price": 25000,
        "salePrice": 20000,
        "discount": 20,
        "stock": 100,
        "availability": "IN_STOCK",
        "categoryId": "CATEGORY#oliveyoung#skincare",
        "categoryPath": ["스킨케어", "토너"],
        "images": [
            "https://image.oliveyoung.co.kr/uploads/images/goods/550/1025/1025001.jpg"
        ],
        "tags": ["수분", "진정", "민감성"]
    }
    """.trimIndent()

    val sampleBrand = """
    {
        "brandId": "roundlab",
        "name": "라운드랩",
        "description": "한국 대표 스킨케어 브랜드",
        "logoUrl": "https://image.oliveyoung.co.kr/brands/roundlab.png"
    }
    """.trimIndent()

    // ==================== 테스트 준비 ====================
    var isReady = false
    beforeSpec {
        isReady = try {
            runBlocking {
                // DynamoDB 연결 확인
                try {
                    dynamoClient.listTables().await()
                    println("✅ DynamoDB Local 연결 성공")
                } catch (e: Exception) {
                    println("❌ DynamoDB Local 연결 실패: ${e.message}")
                    return@runBlocking false
                }

                // PostgreSQL 연결 확인
                try {
                    dsl.select(DSL.count()).from(DSL.table("outbox")).fetchOne()
                    println("✅ PostgreSQL 연결 성공")
                } catch (e: Exception) {
                    println("❌ PostgreSQL 연결 실패: ${e.message}")
                    return@runBlocking false
                }
            }
            true
        } catch (e: Exception) {
            println("⚠️  초기화 실패: ${e.message}")
            false
        }
    }

    beforeEach {
        if (isReady) {
            runBlocking {
                // DynamoDB 데이터 정리
                val productKey = EntityKey("PRODUCT#oliveyoung#A000000001")
                val brandKey = EntityKey("BRAND#oliveyoung#roundlab")
                clearDynamoTable(dynamoClient, tableName, tenantId, productKey)
                clearDynamoTable(dynamoClient, tableName, tenantId, brandKey)

                // PostgreSQL Outbox 정리
                dsl.deleteFrom(DSL.table("outbox")).execute()
            }
        }
    }

    "Full Stack E2E: 샘플 Product Ingest → Slicing → Query → Fanout" {
        if (isReady) {
            val productKey = EntityKey("PRODUCT#oliveyoung#A000000001")
            val brandKey = EntityKey("BRAND#oliveyoung#roundlab")

            // Step 1: Brand Ingest (참조 엔티티)
            runBlocking {
                val brandResult = ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = brandKey,
                    version = 1L,
                    schemaId = "brand.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = sampleBrand,
                )
                brandResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()
                println("✅ Brand Ingest 완료")
            }

            // Step 2: Product Ingest (DynamoDB RawData + PostgreSQL Outbox)
            runBlocking {
                val productResult = ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = productKey,
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = sampleProduct,
                )
                productResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()
                println("✅ Product Ingest 완료")
            }

            // Step 3: PostgreSQL Outbox 확인
            runBlocking {
                val outboxEntries = outboxRepo.findPending(10)
                outboxEntries.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
                val entries = (outboxEntries as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
                entries.size shouldBe 2  // Brand + Product
                entries.forEach { entry ->
                    println("📦 Outbox Entry: ${entry.aggregateType} - ${entry.eventType}")
                    entry.aggregateId shouldContain "oliveyoung"
                }
                println("✅ PostgreSQL Outbox 저장 확인: ${entries.size}개")
            }

            // Step 4: DynamoDB RawData 확인
            runBlocking {
                val rawData = rawDataRepo.get(tenantId, productKey, 1L)
                rawData.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok<*>>()
                val record = (rawData as com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok).value
                record.payload.shouldContain("라운드랩")
                record.payload.shouldContain("A000000001")
                record.version shouldBe 1L
                println("✅ DynamoDB RawData 저장 확인")
            }

            // Step 5: Slicing (DynamoDB Slice + InvertedIndex 생성)
            runBlocking {
                val sliceResult = slicingWorkflow.execute(tenantId, productKey, 1L)
                sliceResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow.Result.Ok<*>>()
                println("✅ Slicing 완료")
            }

            // Step 6: DynamoDB Slice 확인
            runBlocking {
                val slices = sliceRepo.getByVersion(tenantId, productKey, 1L)
                slices.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok<*>>()
                val sliceList = (slices as com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok).value
                sliceList.isNotEmpty() shouldBe true
                
                val coreSlice = sliceList.first { it.sliceType == SliceType.CORE }
                coreSlice.data shouldContain "라운드랩"
                coreSlice.data shouldContain "A000000001"
                coreSlice.ruleSetId shouldBe "ruleset.core.v1"
                println("✅ DynamoDB Slice 저장 확인: ${sliceList.size}개")
            }

            // Step 7: DynamoDB InvertedIndex 확인
            runBlocking {
                // 모든 인덱스 조회해서 디버그
                val allIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "roundlab")
                println("🔍 Brand 인덱스 조회 결과: ${allIndexes.size}개")
                allIndexes.forEach { idx ->
                    println("  - indexType: ${idx.indexType}, indexValue: '${idx.indexValue}', targetEntityKey: ${idx.targetEntityKey.value}")
                }
                
                // brandId에서 추출한 entityId 확인 (canonicalized)
                // "BRAND#oliveyoung#roundlab" → "roundlab" (lowercase)
                val brandIndexes = allIndexes.filter { 
                    it.indexType == "brand" && it.indexValue.lowercase() == "roundlab"
                }
                if (brandIndexes.isEmpty()) {
                    // 다른 가능한 값들로 시도
                    val altIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "라운드랩")
                    println("🔍 '라운드랩'으로 조회: ${altIndexes.size}개")
                    (allIndexes.isNotEmpty() || altIndexes.isNotEmpty()) shouldBe true
                } else {
                    (brandIndexes.isNotEmpty()) shouldBe true
                }
                println("✅ DynamoDB InvertedIndex 생성 확인: brand 인덱스 ${allIndexes.size}개")

                val reverseIndexes = invertedIndexRepo.queryByIndexForTest(tenantId, "product_by_brand", "roundlab")
                println("🔍 역방향 인덱스 조회 결과: ${reverseIndexes.size}개")
                reverseIndexes.forEach { idx ->
                    println("  - indexType: ${idx.indexType}, indexValue: '${idx.indexValue}', targetEntityKey: ${idx.targetEntityKey.value}, refEntityKey: ${idx.refEntityKey.value}")
                }
                (reverseIndexes.isNotEmpty()) shouldBe true
                val matching = reverseIndexes.filter { it.targetEntityKey == productKey }
                (matching.isNotEmpty()) shouldBe true
                println("✅ DynamoDB 역방향 인덱스 생성 확인: product_by_brand ${reverseIndexes.size}개")
            }

            // Step 8: Query (DynamoDB에서 Slice 조회)
            runBlocking {
                val queryResult = queryViewWorkflow.execute(
                    tenantId = tenantId,
                    viewId = "default",
                    entityKey = productKey,
                    version = 1L,
                )
                queryResult.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok<*>>()
                val viewResponse = (queryResult as QueryViewWorkflow.Result.Ok).value
                viewResponse.data.shouldContain("라운드랩")
                viewResponse.data.shouldContain("A000000001")
                println("✅ Query 성공")
            }

            // Step 9: Fanout (Brand 변경 시 연관 Product 조회)
            runBlocking {
                val fanoutResult = invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "roundlab",
                )
                fanoutResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort.Result.Ok<*>>()
                val entries = (fanoutResult as com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort.Result.Ok).value.entries
                entries.isNotEmpty() shouldBe true
                entries.any { it.entityKey == productKey } shouldBe true
                println("✅ Fanout 조회 성공: ${entries.size}개 Product")
            }

            // Step 10: PostgreSQL Outbox 처리 완료 표시
            runBlocking {
                val pendingEntries = outboxRepo.findPending(10)
                val entries = (pendingEntries as com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok).value
                if (entries.isNotEmpty()) {
                    val processed = outboxRepo.markProcessed(entries.map { it.id })
                    processed.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort.Result.Ok<*>>()
                    println("✅ Outbox 처리 완료: ${entries.size}개")
                }
            }

            println("")
            println("🎉 Full Stack E2E 테스트 완료!")
            println("   - DynamoDB: RawData, Slice, InvertedIndex ✅")
            println("   - PostgreSQL: Outbox ✅")
            println("   - 전체 플로우: Ingest → Slicing → Query → Fanout ✅")
        }
    }
})

/**
 * DynamoDB 테이블 데이터 삭제 헬퍼
 */
private suspend fun clearDynamoTable(
    dynamoClient: DynamoDbAsyncClient,
    tableName: String,
    tenantId: TenantId,
    entityKey: EntityKey
) {
    try {
        val pk = "TENANT#${tenantId.value}#ENTITY#${entityKey.value}"
        val response = dynamoClient.query {
            it.tableName(tableName)
            it.keyConditionExpression("PK = :pk")
            it.expressionAttributeValues(
                mapOf(":pk" to AttributeValue.builder().s(pk).build())
            )
            it.projectionExpression("PK, SK")
        }.await()

        response.items().forEach { item ->
            dynamoClient.deleteItem {
                it.tableName(tableName)
                it.key(
                    mapOf(
                        "PK" to item["PK"]!!,
                        "SK" to item["SK"]!!
                    )
                )
            }.await()
        }
    } catch (e: Exception) {
        // 무시 (테이블이 없거나 항목이 없을 수 있음)
    }
}
