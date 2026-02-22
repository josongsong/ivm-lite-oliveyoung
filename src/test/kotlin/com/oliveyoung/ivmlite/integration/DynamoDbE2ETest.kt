package com.oliveyoung.ivmlite.integration

import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.DynamoDbRawDataRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
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
 * - SinkEvent는 DynamoDB (Outbox 제거됨)
 *
 * 실행 전 요구사항:
 * - DynamoDB Local 실행: docker-compose up dynamodb
 * - 테이블 생성: ./infra/dynamodb/create-data-tables.sh
 */
class DynamoDbE2ETest : StringSpec(init@{
    tags(IntegrationTag)

    // Remote-only 정책: endpoint override가 명시된 경우에만 실행 (AWS 기본 엔드포인트로는 절대 실행 금지)
    val endpoint = System.getenv("DYNAMODB_ENDPOINT") ?: ""
    if (endpoint.isBlank()) return@init

    // DynamoDB Local 클라이언트
    val dynamoClient = DynamoDbAsyncClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(System.getenv("AWS_REGION") ?: "ap-northeast-2"))
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
    // Contract Registry (LocalYaml)
    val contractRegistry = LocalYamlContractRegistryAdapter()
    val joinExecutor = JoinExecutor(rawDataRepo)
    val slicingEngine = DefaultSlicingEngineAdapter(SlicingEngine(contractRegistry, joinExecutor))

    val changeSetBuilder = DefaultChangeSetBuilderAdapter(ChangeSetBuilder())
    val impactCalculator = DefaultImpactCalculatorAdapter(ImpactCalculator())

    // Workflow 생성
    val ingestWorkflow = IngestWorkflow(rawDataRepo)
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
            // 기본 entityKey 삭제
            clearDynamoTable(dynamoClient, tableName, tenantId, entityKey)
            // Fanout 테스트에서 사용하는 Product들도 삭제
            listOf("P001", "P002", "P003").forEach { productId ->
                val key = EntityKey("PRODUCT#oliveyoung#$productId")
                clearDynamoTable(dynamoClient, tableName, tenantId, key)
            }
        }
    }

    "DynamoDB 연결 테스트" {
        println("isReady = $isReady")
        if (!isReady) {
            println("⚠️  DynamoDB Local에 연결할 수 없습니다. docker-compose up dynamodb를 실행하세요.")
        }
        isReady shouldBe true
    }

    "E2E: 실제 fixture → DynamoDB 저장 → Slice 생성 → Query" {
        if (isReady) {
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
        ingestResult.shouldBeInstanceOf<Result.Ok<*>>()

        // Step 2: DynamoDB에서 RawData 확인
        val rawData = runBlocking { rawDataRepo.get(tenantId, entityKey, 1L) }
        rawData.shouldBeInstanceOf<Result.Ok<*>>()
        val record = (rawData as Result.Ok).value
        record.payload shouldContain "라운드랩"
        record.payload shouldContain "자작나무 수분 선크림"

        // Step 3: Slicing (Slice DynamoDB 저장)
        val sliceResult = runBlocking {
            slicingWorkflow.execute(tenantId, entityKey, 1L)
        }
        sliceResult.shouldBeInstanceOf<Result.Ok<*>>()

        // Step 4: DynamoDB에서 Slice 확인
        val slices = runBlocking { sliceRepo.getByVersion(tenantId, entityKey, 1L) }
        slices.shouldBeInstanceOf<Result.Ok<*>>()
        val sliceList = (slices as Result.Ok).value
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

        // 정확한 EntityKey 검증 (any 대신 정확한 매칭)
        val matchingEntries = brandEntries.filter { it.refEntityKey == entityKey }
        matchingEntries.isNotEmpty() shouldBe true
        matchingEntries.first().refEntityKey shouldBe entityKey

        // 모든 엔트리가 올바른 tenantId를 가지고 있는지 검증
        brandEntries.forEach { entry ->
            entry.refEntityKey.value shouldContain tenantId.value
        }

        // Step 6: Query (ViewDefinition 기반)
        val queryResult = runBlocking {
            queryViewWorkflow.execute(
                tenantId = tenantId,
                viewId = "view.product.pdp.v1",
                entityKey = entityKey,
                version = 1L,
            )
        }
        queryResult.shouldBeInstanceOf<Result.Ok<*>>()
        val response = (queryResult as Result.Ok<QueryViewWorkflow.ViewResponse>).value
            response.data shouldContain "라운드랩"
            response.data shouldContain "자작나무 수분 선크림"
        }
    }

    "E2E: 여러 상품 일괄 처리 (DynamoDB)" {
        if (isReady) {
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
        slices.shouldBeInstanceOf<Result.Ok<*>>()
            val sliceList = (slices as Result.Ok).value
            sliceList.size shouldBe 5
        }
    }

    // ==================== RFC-IMPL-013: 역방향 인덱스 + Fanout 테스트 ====================

    "E2E: DynamoDB 역방향 인덱스 (product_by_brand) 생성 확인" {
        if (isReady) {
            // Step 1: Ingest + Slicing
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = productFixtureV1,
                )
                slicingWorkflow.execute(tenantId, entityKey, 1L)
            }

            // Step 2: 정방향 인덱스 확인 (brand: BRAND#oliveyoung#roundlab)
            // RFC-IMPL-013: selector가 $.brandId이므로 indexValue는 전체 EntityKey
            val forwardEntries = runBlocking {
                invertedIndexRepo.queryByIndexForTest(tenantId, "brand", "brand#oliveyoung#roundlab")
            }
            println("📊 정방향 인덱스 (brand): ${forwardEntries.size}개")
            forwardEntries.isNotEmpty() shouldBe true

            // Step 3: 역방향 인덱스 확인 (product_by_brand)
            // RuleSet.indexes에서 references: BRAND가 설정되어 있으면 자동 생성됨
            // RFC-IMPL-013: indexValue는 entityId만 저장됨 (EntityKey에서 추출)
            // brandId: "BRAND#oliveyoung#roundlab" → entityId: "roundlab"
            val reverseResult = runBlocking {
                invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "roundlab",  // entityId (lowercase)
                )
            }

            // 역방향 인덱스 검증 (반드시 1개 이상 생성되어야 함)
            reverseResult.shouldBeInstanceOf<Result.Ok<*>>()
            val reverseOk = reverseResult as Result.Ok
            val entries = reverseOk.value.entries
            println("📊 역방향 인덱스 (product_by_brand): ${entries.size}개")
            entries.forEach { entry ->
                println("  - entityKey: ${entry.entityKey.value}, version: ${entry.currentVersion}")
            }

            // RFC-IMPL-013: 역방향 인덱스 필수 검증
            entries.isNotEmpty() shouldBe true  // 반드시 1개 이상 생성되어야 함

            // 정확한 EntityKey 검증 (contains가 아닌 정확한 매칭)
            val productEntries = entries.filter { it.entityKey == entityKey }
            productEntries.isNotEmpty() shouldBe true
            productEntries.first().entityKey shouldBe entityKey
            productEntries.first().currentVersion shouldBe 1L

            // 모든 엔트리가 PRODUCT 엔티티인지 검증
            entries.forEach { entry ->
                entry.entityKey.value shouldBe entityKey.value  // 정확한 매칭
                entry.currentVersion shouldBe 1L
            }
        }
    }

    "E2E: DynamoDB Fanout - Brand 변경 시 연관 Product 조회" {
        if (isReady) {
            // Step 1: 여러 Product가 같은 Brand 참조
            // 각 Product의 brandId는 동일하게 유지 (모두 "BRAND#oliveyoung#roundlab")
            val products = listOf(
                "P001" to productFixtureV1.replace("A000000001", "P001"),
                "P002" to productFixtureV1.replace("A000000001", "P002"),
                "P003" to productFixtureV1.replace("A000000001", "P003"),
            )

            // 일괄 Ingest + Slicing (각 Product마다 역방향 인덱스 생성)
            products.forEach { (productId, fixture) ->
                runBlocking {
                    val key = EntityKey("PRODUCT#oliveyoung#$productId")
                    val ingestResult = ingestWorkflow.execute(
                        tenantId = tenantId,
                        entityKey = key,
                        version = 1L,
                        schemaId = "product.v1",
                        schemaVersion = SemVer.parse("1.0.0"),
                        payloadJson = fixture,
                    )
                    ingestResult.shouldBeInstanceOf<Result.Ok<*>>()

                    val sliceResult = slicingWorkflow.execute(tenantId, key, 1L)
                    sliceResult.shouldBeInstanceOf<Result.Ok<*>>()

                    // 각 Product의 Slice가 생성되었는지 확인
                    val slices = sliceRepo.getByVersion(tenantId, key, 1L)
                    slices.shouldBeInstanceOf<Result.Ok<*>>()
                    val sliceList = (slices as Result.Ok).value
                    sliceList.isNotEmpty() shouldBe true
                    println("✅ Product $productId: ${sliceList.size} slices created")
                }
            }

            // Step 2: 각 Product의 역방향 인덱스가 생성되었는지 확인
            val allDebugIndexes = runBlocking {
                invertedIndexRepo.queryByIndexForTest(tenantId, "product_by_brand", "roundlab")
            }
            println("🔍 전체 역방향 인덱스 개수: ${allDebugIndexes.size}")
            allDebugIndexes.forEach { idx ->
                println("  - targetEntityKey: ${idx.targetEntityKey.value}, refEntityKey: ${idx.refEntityKey.value}, indexValue: '${idx.indexValue}', sliceType: ${idx.sliceType}")
            }

            // 각 Product가 역방향 인덱스를 가지고 있는지 확인
            products.forEach { (productId, _) ->
                val key = EntityKey("PRODUCT#oliveyoung#$productId")
                val matchingIndexes = allDebugIndexes.filter { it.targetEntityKey == key }
                println("🔍 Product $productId reverse index count: ${matchingIndexes.size}")
                matchingIndexes.forEach { idx ->
                    println("  - indexValue: '${idx.indexValue}', refEntityKey: ${idx.refEntityKey.value}, sliceType: ${idx.sliceType}")
                }
                // 각 Product마다 최소 1개 이상의 역방향 인덱스가 있어야 함
                matchingIndexes.isNotEmpty() shouldBe true
            }

            // 전체 역방향 인덱스가 3개 이상이어야 함 (각 Product마다 최소 1개, 여러 SliceType일 수 있음)
            (allDebugIndexes.size >= 3) shouldBe true

            // 모든 역방향 인덱스가 올바른 indexValue를 가지고 있는지 확인
            allDebugIndexes.forEach { idx ->
                idx.indexValue shouldBe "roundlab"  // entityId (lowercase)
                idx.indexType shouldBe "product_by_brand"
            }

            // Step 3: 역방향 인덱스로 연관 Product 조회 (Fanout 시나리오)
            // brandId: "BRAND#oliveyoung#roundlab" → entityId: "roundlab" (lowercase)
            // InvertedIndexBuilder는 EntityKey에서 parts[2]를 추출하여 lowercase로 저장

            // 각 Product가 역방향 인덱스를 가지고 있는지 확인 (이미 Step 2에서 확인함)
            val targetEntityKeysFromDebug = allDebugIndexes.map { it.targetEntityKey.value }.toSet()
            products.forEach { (productId, _) ->
                val expectedKey = "PRODUCT#oliveyoung#$productId"
                (expectedKey in targetEntityKeysFromDebug) shouldBe true
            }

            val fanoutResult = runBlocking {
                invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "roundlab",  // entityId (lowercase)
                )
            }

            // 검증: 역방향 인덱스로 3개 Product 모두 조회되어야 함
            fanoutResult.shouldBeInstanceOf<Result.Ok<*>>()
            val okResult = fanoutResult as Result.Ok
            val entries = okResult.value.entries

            println("📊 Brand 'roundlab' 변경 시 영향받는 Product (distinctBy 후): ${entries.size}개")
            entries.forEach { entry ->
                println("  - ${entry.entityKey.value}, version: ${entry.currentVersion}")
            }

            // 정확한 검증: 3개 Product 모두 조회되어야 함
            // distinctBy로 중복 제거되므로 각 Product당 1개씩 총 3개
            entries.size shouldBe 3
            val productIds = entries.map { it.entityKey.value.split("#").last() }.sorted()
            productIds shouldBe listOf("P001", "P002", "P003")

            // 모든 엔트리가 올바른 버전을 가지고 있는지 검증
            entries.forEach { entry ->
                entry.currentVersion shouldBe 1L
            }

            // Step 3: countByIndexType으로 수 확인
            val countResult = runBlocking {
                invertedIndexRepo.countByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "roundlab",  // entityId (lowercase)
                )
            }

            // countByIndexType 결과 검증
            countResult.shouldBeInstanceOf<Result.Ok<*>>()
            val countOk = countResult as Result.Ok
            val count = countOk.value
            count shouldBe 3  // 정확한 수 확인

            println("📊 Brand '라운드랩' 연관 Product 수: $count")
        }
    }

    "E2E: DynamoDB 멱등성 검증 - 동일 데이터 2번 Ingest" {
        if (isReady) {
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
            result1.shouldBeInstanceOf<Result.Ok<*>>()

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
            result2.shouldBeInstanceOf<Result.Ok<*>>()

            // DynamoDB에 1개만 존재하는지 검증
            val rawData = runBlocking { rawDataRepo.get(tenantId, entityKey, 1L) }
            rawData.shouldBeInstanceOf<Result.Ok<*>>()
            val record = (rawData as Result.Ok).value
            record.payload shouldContain "라운드랩"
            record.version shouldBe 1L
        }
    }

    "E2E: DynamoDB 에러 케이스 - 존재하지 않는 엔티티 조회" {
        if (isReady) {
            val nonExistentKey = EntityKey("PRODUCT#oliveyoung#NONEXISTENT")

            // 존재하지 않는 RawData 조회 → NotFoundError
            val rawDataResult = runBlocking {
                rawDataRepo.get(tenantId, nonExistentKey, 1L)
            }
            rawDataResult.shouldBeInstanceOf<Result.Err>()
            val rawDataErr = rawDataResult as Result.Err
            rawDataErr.error.shouldBeInstanceOf<com.oliveyoung.ivmlite.shared.domain.errors.DomainError.NotFoundError>()

            // 존재하지 않는 Slice 조회 → 빈 리스트 또는 Err (구현에 따라 다름)
            val sliceResult = runBlocking {
                sliceRepo.getByVersion(tenantId, nonExistentKey, 1L)
            }
            // DynamoDbSliceRepository는 빈 리스트를 반환할 수 있음
            if (sliceResult is Result.Ok) {
                sliceResult.value.isEmpty() shouldBe true  // 빈 리스트면 OK
            } else {
                sliceResult.shouldBeInstanceOf<Result.Err>()
            }
        }
    }

    "E2E: DynamoDB 엣지 케이스 - 빈 인덱스 결과 조회" {
        if (isReady) {
            // 존재하지 않는 Brand로 인덱스 조회
            val emptyResult = runBlocking {
                invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "NONEXISTENT_BRAND",
                )
            }

            // 빈 결과는 Ok이지만 entries가 비어있어야 함
            emptyResult.shouldBeInstanceOf<Result.Ok<*>>()
            val okResult = emptyResult as Result.Ok
            okResult.value.entries.isEmpty() shouldBe true

            // countByIndexType도 0 반환
            val countResult = runBlocking {
                invertedIndexRepo.countByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "NONEXISTENT_BRAND",
                )
            }
            countResult.shouldBeInstanceOf<Result.Ok<*>>()
            val countOk = countResult as Result.Ok
            countOk.value shouldBe 0
        }
    }

    "E2E: DynamoDB 엣지 케이스 - 여러 버전의 Slice 생성 및 조회" {
        if (isReady) {
            // Step 1: v1 생성
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = productFixtureV1,
                )
                slicingWorkflow.execute(tenantId, entityKey, 1L)
            }

            // Step 2: v2 생성 (데이터 변경)
            val updatedFixture = productFixtureV1.replace("라운드랩", "라운드랩 업데이트")
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = 2L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = updatedFixture,
                )
                slicingWorkflow.execute(tenantId, entityKey, 2L)
            }

            // Step 3: 각 버전별 Slice 확인
            val v1Slices = runBlocking {
                sliceRepo.getByVersion(tenantId, entityKey, 1L)
            }
            v1Slices.shouldBeInstanceOf<Result.Ok<*>>()
            val v1List = (v1Slices as Result.Ok).value
            v1List.size shouldBe 5
            v1List.forEach { it.version shouldBe 1L }

            val v2Slices = runBlocking {
                sliceRepo.getByVersion(tenantId, entityKey, 2L)
            }
            v2Slices.shouldBeInstanceOf<Result.Ok<*>>()
            val v2List = (v2Slices as Result.Ok).value
            v2List.size shouldBe 5
            v2List.forEach { it.version shouldBe 2L }

            // v2의 CORE slice에 업데이트된 데이터가 포함되어 있는지 확인
            val v2CoreSlice = v2List.first { it.sliceType == SliceType.CORE }
            v2CoreSlice.data shouldContain "라운드랩 업데이트"
        }
    }

    "E2E: DynamoDB 엣지 케이스 - 동일 Brand를 참조하는 여러 Product의 역방향 인덱스 격리" {
        if (isReady) {
            // Step 1: 서로 다른 Brand를 참조하는 Product들 생성
            val product1Key = EntityKey("PRODUCT#oliveyoung#BRAND1_PRODUCT")
            val product1Fixture = productFixtureV1.replace("A000000001", "BRAND1_PRODUCT").replace("BRAND#oliveyoung#roundlab", "BRAND#oliveyoung#brand1")
            val product2Key = EntityKey("PRODUCT#oliveyoung#BRAND2_PRODUCT")
            val product2Fixture = productFixtureV1.replace("A000000001", "BRAND2_PRODUCT").replace("BRAND#oliveyoung#roundlab", "BRAND#oliveyoung#brand2")

            // Ingest + Slicing
            runBlocking {
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = product1Key,
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = product1Fixture,
                )
                ingestWorkflow.execute(
                    tenantId = tenantId,
                    entityKey = product2Key,
                    version = 1L,
                    schemaId = "product.v1",
                    schemaVersion = SemVer.parse("1.0.0"),
                    payloadJson = product2Fixture,
                )
                slicingWorkflow.execute(tenantId, product1Key, 1L)
                slicingWorkflow.execute(tenantId, product2Key, 1L)
            }

            // Step 2: brand1의 역방향 인덱스 조회 → product1만 조회되어야 함
            val brand1Result = runBlocking {
                invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "brand1",
                )
            }
            brand1Result.shouldBeInstanceOf<Result.Ok<*>>()
            val brand1Entries = (brand1Result as Result.Ok).value.entries
            brand1Entries.size shouldBe 1
            brand1Entries[0].entityKey shouldBe product1Key

            // Step 3: brand2의 역방향 인덱스 조회 → product2만 조회되어야 함
            val brand2Result = runBlocking {
                invertedIndexRepo.queryByIndexType(
                    tenantId = tenantId,
                    indexType = "product_by_brand",
                    indexValue = "brand2",
                )
            }
            brand2Result.shouldBeInstanceOf<Result.Ok<*>>()
            val brand2Entries = (brand2Result as Result.Ok).value.entries
            brand2Entries.size shouldBe 1
            brand2Entries[0].entityKey shouldBe product2Key

            // Step 4: 격리 확인 - brand1 조회 시 product2가 포함되지 않음
            brand1Entries.none { it.entityKey == product2Key } shouldBe true
            brand2Entries.none { it.entityKey == product1Key } shouldBe true
        }
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
