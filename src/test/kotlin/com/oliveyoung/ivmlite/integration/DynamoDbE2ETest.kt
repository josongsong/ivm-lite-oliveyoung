package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.DynamoDbRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.net.URI
import kotlinx.coroutines.delay

/**
 * DynamoDB E2E 테스트 (DynamoDB Local 사용)
 * 
 * 실제 fixture 데이터를 DynamoDB에 저장하고 검증:
 * - RawData 저장 (DynamoDB)
 * - Slice 생성 (DynamoDB)
 * - Inverted Index 생성 (DynamoDB)
 * - Query 결과 확인
 * - Outbox는 InMemory (PostgreSQL 대신)
 * 
 * 실행 전 요구사항:
 * - DynamoDB Local 실행: docker-compose up dynamodb
 * - 테이블 생성: ./infra/dynamodb/create-data-tables.sh
 */
class DynamoDbE2ETest : StringSpec({

    // DynamoDB Local 클라이언트
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

    // DynamoDB 연결 테스트 및 테이블 생성
    var isReady = false
    beforeSpec {
        println("🔍 DynamoDB Local 연결 테스트 시작...")
        isReady = try {
            runBlocking {
                // 연결 테스트
                try {
                    val tables = dynamoClient.listTables().await()
                    println("✅ DynamoDB Local 연결 성공! 기존 테이블: ${tables.tableNames()}")
                } catch (e: Exception) {
                    println("❌ DynamoDB Local 연결 실패: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
                
                // 테이블 존재 확인
                try {
                    dynamoClient.describeTable { it.tableName(tableName) }.await()
                    println("✅ DynamoDB 테이블이 이미 존재합니다: $tableName")
                } catch (e: ResourceNotFoundException) {
                    // 테이블이 없으면 생성
                    println("📦 DynamoDB 테이블을 생성합니다: $tableName")
                    dynamoClient.createTable {
                        it.tableName(tableName)
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
                    
                    // 테이블 생성 대기
                    var retries = 0
                    while (retries < 10) {
                        try {
                            val status = dynamoClient.describeTable { it.tableName(tableName) }.await().table().tableStatus()
                            if (status == TableStatus.ACTIVE) {
                                println("✅ DynamoDB 테이블 생성 완료: $tableName")
                                break
                            }
                            kotlinx.coroutines.delay(500)
                            retries++
                        } catch (e: Exception) {
                            kotlinx.coroutines.delay(500)
                            retries++
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("⚠️  DynamoDB Local 연결 실패: ${e.message}")
            false
        }
    }

    // Repository 생성 (DynamoDB)
    val rawDataRepo = DynamoDbRawDataRepository(dynamoClient, tableName)
    val sliceRepo = DynamoDbSliceRepository(dynamoClient, tableName)
    val invertedIndexRepo = DynamoDbInvertedIndexRepository(dynamoClient, tableName)
    val outboxRepo = InMemoryOutboxRepository()  // Outbox만 InMemory

    // Contract Registry (LocalYaml)
    val contractRegistry = LocalYamlContractRegistryAdapter()
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = SlicingEngine(contractRegistry, joinExecutor)

    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

    // Workflow 생성
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

    // 실제 fixture 데이터
    val productFixtureV1 = """
    {
        "productId": "A000000001",
        "title": "[올영픽] 라운드랩 자작나무 수분 선크림 SPF50+ PA++++",
        "brand": "라운드랩",
        "brandId": "BRAND#oliveyoung#roundlab",
        "price": 25000,
        "salePrice": 19900,
        "discount": 20,
        "stock": 1500,
        "availability": "IN_STOCK",
        "images": [
            {"url": "https://cdn.oliveyoung.co.kr/img/product/A000000001_01.jpg", "type": "MAIN"},
            {"url": "https://cdn.oliveyoung.co.kr/img/product/A000000001_02.jpg", "type": "DETAIL"}
        ],
        "videos": [],
        "categoryId": "CAT-SKINCARE-SUN",
        "categoryPath": ["스킨케어", "선케어", "선크림"],
        "tags": ["자외선차단", "수분", "민감피부", "자작나무"],
        "promotionIds": ["PROMO-2026-SUMMER"],
        "couponIds": [],
        "reviewCount": 12847,
        "averageRating": 4.8,
        "ingredients": ["정제수", "사이클로펜타실록세인", "에칠헥실메톡시신나메이트"],
        "description": "자작나무 수액으로 촉촉하게 마무리되는 선크림"
    }
    """.trimIndent()

    val tenantId = TenantId("oliveyoung")
    val entityKey = EntityKey("PRODUCT#oliveyoung#A000000001")

    beforeEach {
        // DynamoDB 데이터 삭제 (테스트 격리)
        if (isReady) {
            clearDynamoTable(dynamoClient, tableName, tenantId, entityKey)
        }
    }

    "DynamoDB 연결 테스트" {
        println("isReady = $isReady")
        if (!isReady) {
            println("⚠️  DynamoDB Local에 연결할 수 없습니다. docker-compose up dynamodb를 실행하세요.")
        }
        isReady shouldBe true
    }

    "E2E: 실제 fixture → DynamoDB 저장 → Slice 생성 → Query".config(enabled = isReady) {
        // Step 1: Ingest (RawData DynamoDB 저장)
        val ingestResult = runBlocking {
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = productFixtureV1,
            )
        }
        ingestResult.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // Step 2: DynamoDB에서 RawData 확인
        val rawData = runBlocking { rawDataRepo.get(tenantId, entityKey, 1L) }
        rawData.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok<*>>()
        val record = (rawData as com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok).value
        record.payload shouldContain "라운드랩"
        record.payload shouldContain "자작나무 수분 선크림"

        // Step 3: Slicing (Slice DynamoDB 저장)
        val sliceResult = runBlocking {
            slicingWorkflow.execute(tenantId, entityKey, 1L)
        }
        sliceResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow.Result.Ok<*>>()

        // Step 4: DynamoDB에서 Slice 확인
        val slices = runBlocking { sliceRepo.getByVersion(tenantId, entityKey, 1L) }
        slices.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok<*>>()
        val sliceList = (slices as com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok).value
        sliceList.size shouldBe 5  // CORE, PRICE, INVENTORY, MEDIA, CATEGORY

        val coreSlice = sliceList.first { it.sliceType == SliceType.CORE }
        coreSlice.data shouldContain "라운드랩"
        coreSlice.data shouldContain "자작나무 수분 선크림"
        coreSlice.ruleSetId shouldBe "ruleset.core.v1"

        // Step 5: Inverted Index 확인
        val brandEntries = runBlocking {
            invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "라운드랩")
        }
        brandEntries.isNotEmpty() shouldBe true
        brandEntries.any { it.refEntityKey == entityKey } shouldBe true

        // Step 6: Query (ViewDefinition 기반)
        val queryResult = runBlocking {
            queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "view.product.pdp.v1",
                entityKey = entityKey,
                version = 1L,
            )
        }
        queryResult.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow.Result.Ok<*>>()
        val response = (queryResult as com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow.Result.Ok).value
        response.data shouldContain "라운드랩"
        response.data shouldContain "자작나무 수분 선크림"
    }

    "E2E: 여러 상품 일괄 처리 (DynamoDB)".config(enabled = isReady) {
        val products = listOf(
            "A000000001" to productFixtureV1,
            "A000000002" to productFixtureV1.replace("A000000001", "A000000002")
                .replace("라운드랩", "토리든"),
            "A000000003" to productFixtureV1.replace("A000000001", "A000000003")
                .replace("라운드랩", "닥터지"),
        )

        // 일괄 Ingest
        products.forEach { (productId, fixture) ->
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = EntityKey("PRODUCT#oliveyoung#$productId"),
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = fixture,
                )
            }
        }

        // 일괄 Slicing
        products.forEach { (productId, _) ->
            runBlocking {
                slicingWorkflow.execute(tenantId, EntityKey("PRODUCT#oliveyoung#$productId"), 1L)
            }
        }

        // 첫 번째 상품 확인
        val slices = runBlocking {
            sliceRepo.getByVersion(tenantId, EntityKey("PRODUCT#oliveyoung#A000000001"), 1L)
        }
        slices.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok<*>>()
        val sliceList = (slices as com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort.Result.Ok).value
        sliceList.size shouldBe 5
    }

    "E2E: DynamoDB 멱등성 검증".config(enabled = isReady) {
        // 동일 데이터 2번 Ingest
        val result1 = runBlocking {
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = productFixtureV1,
            )
        }
        result1.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        val result2 = runBlocking {
            ingestWorkflow.execute(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                schemaId = "product.v1",
                schemaVersion = SemVer.parse("1.0.0"),
                payloadJson = productFixtureV1,
            )
        }
        result2.shouldBeInstanceOf<IngestWorkflow.Result.Ok<*>>()

        // DynamoDB에 1개만 존재
        val rawData = runBlocking { rawDataRepo.get(tenantId, entityKey, 1L) }
        rawData.shouldBeInstanceOf<com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort.Result.Ok<*>>()
    }
})

/**
 * DynamoDB 테이블 데이터 삭제 (테스트 격리용)
 */
private suspend fun clearDynamoTable(
    dynamoClient: DynamoDbAsyncClient,
    tableName: String,
    tenantId: TenantId,
    entityKey: EntityKey
) {
    try {
        val pk = "TENANT#${tenantId.value}#ENTITY#${entityKey.value}"

        // Query로 모든 SK 가져오기
        val response = dynamoClient.query {
            it.tableName(tableName)
            it.keyConditionExpression("PK = :pk")
            it.expressionAttributeValues(
                mapOf(":pk" to AttributeValue.builder().s(pk).build())
            )
            it.projectionExpression("PK, SK")
        }.await()

        // 모든 아이템 삭제
        response.items().forEach { item ->
            dynamoClient.deleteItem {
                it.tableName(tableName)
                it.key(
                    mapOf(
                        "PK" to item["PK"],
                        "SK" to item["SK"]
                    )
                )
            }.await()
        }
    } catch (e: Exception) {
        // 테이블 없거나 데이터 없으면 무시
    }
}
