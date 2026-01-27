package com.oliveyoung.ivmlite.apps.playground

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.changeset.adapters.InMemoryChangeSetRepository
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqOutboxRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchSinkAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexBuilder
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.DatabaseConfig
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

/**
 * RawData → Slice → OpenSearch Sink Playground
 *
 * 샘플 데이터로 파이프라인 테스트:
 * - 상품 3개
 * - 카테고리 2개
 * - 브랜드 2개
 */
fun main() {
    val logger = LoggerFactory.getLogger("Playground")

    // DB 연결 (rawdata DB)
    val dbUrl = System.getenv("RAWDATA_DB_URL")
        ?: "jdbc:postgresql://${System.getenv("RAWDATA_PGHOST") ?: "localhost"}:${System.getenv("RAWDATA_PGPORT") ?: "5432"}/${System.getenv("RAWDATA_PGDATABASE") ?: "rawdata"}"
    val dbUser = System.getenv("RAWDATA_PGUSER") ?: "postgres"
    val dbPassword = System.getenv("RAWDATA_PGPASSWORD") ?: ""

    logger.info("Connecting to database: {}", dbUrl.replace(dbPassword, "***"))

    val connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    val dslContext = DSL.using(connection)

    // ivmlite DB 연결 (raw_data 테이블용)
    val ivmliteDbUrl = System.getenv("DB_URL")
        ?: "jdbc:postgresql://${System.getenv("RAWDATA_PGHOST") ?: "localhost"}:${System.getenv("RAWDATA_PGPORT") ?: "5432"}/ivmlite"
    val ivmliteConnection = DriverManager.getConnection(ivmliteDbUrl, dbUser, dbPassword)
    val ivmliteDslContext = DSL.using(ivmliteConnection)

    runBlocking {
        try {
            // 1. rawdata DB에서 샘플 데이터 읽기
            logger.info("=== Step 1: Reading sample data from rawdata DB ===")
            val sampleData = readSampleDataFromRawdata(dslContext)

            // 2. ivmlite raw_data 테이블에 Ingest
            logger.info("=== Step 2: Ingesting to raw_data table ===")
            val ingestWorkflow = createIngestWorkflow(ivmliteDslContext)
            val rawDataKeys = ingestSampleData(ingestWorkflow, sampleData)

            // 3. Slice 생성
            logger.info("=== Step 3: Creating slices ===")
            val slicingWorkflow = createSlicingWorkflow(ivmliteDslContext)
            val sliceKeys = createSlices(slicingWorkflow, rawDataKeys)

            // 4. OpenSearch Sink
            logger.info("=== Step 4: Shipping to OpenSearch ===")
            val openSearchSink = createOpenSearchSink()
            val sliceRepo = JooqSliceRepository(ivmliteDslContext)
            shipToOpenSearch(openSearchSink, sliceRepo, sliceKeys)

            logger.info("=== ✅ Playground 완료! ===")
        } catch (e: Exception) {
            logger.error("Playground 실패", e)
            throw e
        } finally {
            connection.close()
            ivmliteConnection.close()
        }
    }
}

/**
 * rawdata DB에서 샘플 데이터 읽기
 */
fun readSampleDataFromRawdata(dsl: DSLContext): SampleData {
    val logger = LoggerFactory.getLogger("Playground")

    // 상품 3개
    val products = dsl.select()
        .from("raw_product_document")
        .limit(3)
        .fetch()
        .map { row ->
            val productId = row.get("product_id") as? String ?: ""
            val document = row.get("document") as? org.postgresql.util.PGobject
            SampleProduct(productId, document?.value ?: "{}")
        }

    logger.info("읽은 상품: {}개", products.size)
    products.forEach { logger.info("  - {}", it.productId) }

    // 카테고리 2개
    val categories = dsl.select()
        .from("raw_category_document")
        .limit(2)
        .fetch()
        .map { row ->
            val categoryId = row.get("category_id") as? String ?: ""
            val document = row.get("document") as? org.postgresql.util.PGobject
            SampleCategory(categoryId, document?.value ?: "{}")
        }

    logger.info("읽은 카테고리: {}개", categories.size)
    categories.forEach { logger.info("  - {}", it.categoryId) }

    // 브랜드 2개
    val brands = dsl.select()
        .from("raw_brand_document")
        .limit(2)
        .fetch()
        .map { row ->
            val brandId = row.get("brand_id") as? String ?: ""
            val document = row.get("document") as? org.postgresql.util.PGobject
            SampleBrand(brandId, document?.value ?: "{}")
        }

    logger.info("읽은 브랜드: {}개", brands.size)
    brands.forEach { logger.info("  - {}", it.brandId) }

    return SampleData(products, categories, brands)
}

/**
 * 샘플 데이터를 raw_data 테이블에 Ingest
 */
suspend fun ingestSampleData(
    ingestWorkflow: IngestWorkflow,
    sampleData: SampleData
): List<RawDataKey> {
    val logger = LoggerFactory.getLogger("Playground")
    val tenantId = TenantId("mecca")
    val rawDataKeys = mutableListOf<RawDataKey>()

    // 상품 Ingest
    sampleData.products.forEach { product ->
        // entity_key는 최대 256자이지만 안전하게 productId만 사용 (앞부분만)
        val shortProductId = product.productId.take(30)
        val entityKey = EntityKey("p-${shortProductId}")
        val version = 1L
        val schemaId = "entity.product.v1"
        val schemaVersion = SemVer.parse("1.0.0")

        // MECCA 크롤러 데이터를 entity.product.v1 스키마로 변환
        val transformedPayload = transformMeccaProductToEntitySchema(product.document)

        when (val result = ingestWorkflow.execute(
            tenantId, entityKey, version, schemaId, schemaVersion, transformedPayload
        )) {
            is IngestWorkflow.Result.Ok -> {
                logger.info("✅ Ingested product: {}", entityKey.value)
                rawDataKeys.add(RawDataKey(tenantId, entityKey, version, "PRODUCT"))
            }
            is IngestWorkflow.Result.Err -> {
                logger.error("❌ Failed to ingest product {}: {}", entityKey.value, result.error)
            }
        }
    }

    // 카테고리 Ingest
    sampleData.categories.forEach { category ->
        val shortCategoryId = category.categoryId.take(30)
        val entityKey = EntityKey("c-${shortCategoryId}")
        val version = 1L
        val schemaId = "entity.category.v1"
        val schemaVersion = SemVer.parse("1.0.0")

        val transformedPayload = transformMeccaCategoryToEntitySchema(category.document)

        when (val result = ingestWorkflow.execute(
            tenantId, entityKey, version, schemaId, schemaVersion, transformedPayload
        )) {
            is IngestWorkflow.Result.Ok -> {
                logger.info("✅ Ingested category: {}", entityKey.value)
                rawDataKeys.add(RawDataKey(tenantId, entityKey, version, "CATEGORY"))
            }
            is IngestWorkflow.Result.Err -> {
                logger.error("❌ Failed to ingest category {}: {}", entityKey.value, result.error)
            }
        }
    }

    // 브랜드 Ingest
    sampleData.brands.forEach { brand ->
        val shortBrandId = brand.brandId.take(30)
        val entityKey = EntityKey("b-${shortBrandId}")
        val version = 1L
        val schemaId = "entity.brand.v1"
        val schemaVersion = SemVer.parse("1.0.0")

        val transformedPayload = transformMeccaBrandToEntitySchema(brand.document)

        when (val result = ingestWorkflow.execute(
            tenantId, entityKey, version, schemaId, schemaVersion, transformedPayload
        )) {
            is IngestWorkflow.Result.Ok -> {
                logger.info("✅ Ingested brand: {}", entityKey.value)
                rawDataKeys.add(RawDataKey(tenantId, entityKey, version, "BRAND"))
            }
            is IngestWorkflow.Result.Err -> {
                logger.error("❌ Failed to ingest brand {}: {}", entityKey.value, result.error)
            }
        }
    }

    return rawDataKeys
}

/**
 * Slice 생성
 */
suspend fun createSlices(
    slicingWorkflow: SlicingWorkflow,
    rawDataKeys: List<RawDataKey>
): List<SliceRepositoryPort.SliceKey> {
    val logger = LoggerFactory.getLogger("Playground")
    val allSliceKeys = mutableListOf<SliceRepositoryPort.SliceKey>()

    rawDataKeys.forEach { key ->
        when (val result = slicingWorkflow.execute(key.tenantId, key.entityKey, key.version)) {
            is SlicingWorkflow.Result.Ok -> {
                logger.info("✅ Created slices for {}: {} slices", key.entityKey.value, result.value.size)
                allSliceKeys.addAll(result.value)
            }
            is SlicingWorkflow.Result.Err -> {
                logger.error("❌ Failed to create slices for {}: {}", key.entityKey.value, result.error)
            }
        }
    }

    return allSliceKeys
}

/**
 * OpenSearch로 Ship
 */
suspend fun shipToOpenSearch(
    sink: OpenSearchSinkAdapter,
    sliceRepo: com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort,
    sliceKeys: List<SliceRepositoryPort.SliceKey>
) {
    val logger = LoggerFactory.getLogger("Playground")

    // CORE slice만 OpenSearch에 ship
    val coreSlices = sliceKeys.filter { it.sliceType.name == "CORE" }

    logger.info("Shipping {} CORE slices to OpenSearch", coreSlices.size)

    coreSlices.forEach { sliceKey ->
        // Slice 읽기 (getByVersion으로 해당 버전의 모든 slice 조회 후 필터링)
        when (val sliceResult = sliceRepo.getByVersion(sliceKey.tenantId, sliceKey.entityKey, sliceKey.version)) {
            is SliceRepositoryPort.Result.Ok -> {
                val slice = sliceResult.value.find { it.sliceType == sliceKey.sliceType }
                if (slice != null) {
                    // OpenSearch로 ship
                    when (val shipResult = sink.ship(sliceKey.tenantId, sliceKey.entityKey, sliceKey.version, slice.data)) {
                        is com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort.Result.Ok -> {
                            logger.info("✅ Shipped to OpenSearch: {}:{}:{}:{} -> {}",
                                sliceKey.tenantId.value,
                                sliceKey.entityKey.value,
                                sliceKey.version,
                                sliceKey.sliceType.name,
                                shipResult.value.sinkId)
                        }
                        is com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort.Result.Err -> {
                            logger.error("❌ Failed to ship {}: {}", sliceKey.entityKey.value, shipResult.error)
                        }
                    }
                } else {
                    logger.warn("⚠️ Slice not found: {}:{}:{}:{}",
                        sliceKey.tenantId.value, sliceKey.entityKey.value, sliceKey.version, sliceKey.sliceType.name)
                }
            }
            is SliceRepositoryPort.Result.Err -> {
                logger.error("❌ Failed to read slice {}: {}", sliceKey.entityKey.value, sliceResult.error)
            }
        }
    }

    logger.info("✅ OpenSearch sink 완료")
}

// ===== Helper Functions =====

fun createIngestWorkflow(dsl: DSLContext): IngestWorkflow {
    val rawRepo = JooqRawDataRepository(dsl)
    val outboxRepo = JooqOutboxRepository(dsl)
    return IngestWorkflow(rawRepo, outboxRepo)
}

fun createSlicingWorkflow(dsl: DSLContext): SlicingWorkflow {
    val rawRepo = JooqRawDataRepository(dsl)
    val sliceRepo = JooqSliceRepository(dsl)
    val invertedIndexRepo = JooqInvertedIndexRepository(dsl)
    val contractRegistry = GatedContractRegistryAdapter(
        LocalYamlContractRegistryAdapter("/contracts/v1"),
        DefaultContractStatusGate
    )
    val joinExecutor = JoinExecutor(rawRepo)
    val slicingEngine = SlicingEngine(contractRegistry, joinExecutor)
    val changeSetBuilder = ChangeSetBuilder()
    val impactCalculator = ImpactCalculator()

    return SlicingWorkflow(
        rawRepo, sliceRepo, slicingEngine, invertedIndexRepo,
        changeSetBuilder, impactCalculator, contractRegistry
    )
}

fun createOpenSearchSink(): OpenSearchSinkAdapter {
    val endpoint = System.getenv("OPENSEARCH_ENDPOINT") ?: "http://localhost:9200"
    // OpenSearchConfig는 OpenSearchSinkAdapter와 같은 파일에 정의되어 있음
    val config = com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchConfig(
        endpoint = endpoint,
        indexPrefix = "ivm-products",
        username = System.getenv("OPENSEARCH_USERNAME"),
        password = System.getenv("OPENSEARCH_PASSWORD")
    )
    return OpenSearchSinkAdapter(config)
}

/**
 * MECCA 크롤러 데이터를 entity.product.v1 스키마로 변환
 */
fun transformMeccaProductToEntitySchema(meccaJson: String): String {
    val json = Json { ignoreUnknownKeys = true }
    val meccaDoc = json.parseToJsonElement(meccaJson).jsonObject

    val masterInfo = meccaDoc["masterInfo"]?.jsonObject ?: return """{"sku":"","name":"","price":0}"""
    val onlineInfo = meccaDoc["onlineInfo"]?.jsonObject

    val sku = masterInfo["gdsCd"]?.jsonPrimitive?.content ?: ""
    val name = masterInfo["gdsNm"]?.jsonPrimitive?.content ?: ""
    val brand = masterInfo["brand"]?.jsonObject?.get("krName")?.jsonPrimitive?.content ?: ""
    val brandId = masterInfo["brand"]?.jsonObject?.get("code")?.jsonPrimitive?.content ?: ""
    val category = masterInfo["standardCategory"]?.jsonObject?.get("medium")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""

    // 카테고리 경로 구성
    val categoryPathParts = mutableListOf<String>()
    masterInfo["standardCategory"]?.jsonObject?.let { cat ->
        cat["large"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.let { categoryPathParts.add(it) }
        cat["medium"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.let { categoryPathParts.add(it) }
        cat["small"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.let { categoryPathParts.add(it) }
    }
    val categoryPath = categoryPathParts.joinToString(">")

    // 이미지 URL 목록
    val images = meccaDoc["thumbnailImages"]?.jsonArray?.mapNotNull {
        it.jsonObject["fullUrl"]?.jsonPrimitive?.content
    } ?: emptyList()

    val description = meccaDoc["descriptionInfo"]?.jsonObject?.get("sellingPoint")?.jsonPrimitive?.content ?: ""
    val isAvailable = onlineInfo?.get("displayYn")?.jsonPrimitive?.content == "Y"

    val productMap = mutableMapOf<String, JsonElement>()
    productMap["sku"] = JsonPrimitive(sku)
    productMap["name"] = JsonPrimitive(name)
    productMap["price"] = JsonPrimitive(0L) // MECCA 데이터에 가격 정보 없음
    if (brand.isNotEmpty()) productMap["brand"] = JsonPrimitive(brand)
    if (brandId.isNotEmpty()) productMap["brandId"] = JsonPrimitive(brandId)
    if (category.isNotEmpty()) productMap["category"] = JsonPrimitive(category)
    if (categoryPath.isNotEmpty()) productMap["categoryPath"] = JsonPrimitive(categoryPath)
    if (images.isNotEmpty()) productMap["images"] = JsonArray(images.map { JsonPrimitive(it) })
    if (description.isNotEmpty()) productMap["description"] = JsonPrimitive(description)
    productMap["isAvailable"] = JsonPrimitive(isAvailable)

    val product = JsonObject(productMap)
    return json.encodeToString(JsonObject.serializer(), product)
}

/**
 * MECCA 카테고리를 entity.category.v1 스키마로 변환
 */
fun transformMeccaCategoryToEntitySchema(meccaJson: String): String {
    val json = Json { ignoreUnknownKeys = true }
    val meccaDoc = json.parseToJsonElement(meccaJson).jsonObject

    val categoryId = meccaDoc["categoryId"]?.jsonPrimitive?.content ?: ""
    val name = meccaDoc["categoryName"]?.jsonPrimitive?.content ?: ""
    val path = meccaDoc["path"]?.jsonObject?.get("fullPath")?.jsonPrimitive?.content ?: ""
    val depth = meccaDoc["depth"]?.jsonPrimitive?.intOrNull ?: 0
    val isActive = meccaDoc["displayYn"]?.jsonPrimitive?.booleanOrNull ?: true

    val categoryMap = mutableMapOf<String, JsonElement>()
    categoryMap["categoryId"] = JsonPrimitive(categoryId)
    categoryMap["name"] = JsonPrimitive(name)
    if (path.isNotEmpty()) categoryMap["path"] = JsonPrimitive(path)
    categoryMap["depth"] = JsonPrimitive(depth)
    categoryMap["isActive"] = JsonPrimitive(isActive)

    val category = JsonObject(categoryMap)
    return json.encodeToString(JsonObject.serializer(), category)
}

/**
 * MECCA 브랜드를 entity.brand.v1 스키마로 변환
 */
fun transformMeccaBrandToEntitySchema(meccaJson: String): String {
    val json = Json { ignoreUnknownKeys = true }
    val meccaDoc = json.parseToJsonElement(meccaJson).jsonObject

    val brandId = meccaDoc["brandId"]?.jsonPrimitive?.content ?: ""
    val name = meccaDoc["brandName"]?.jsonPrimitive?.content ?: ""
    val logoUrl = meccaDoc["logoUrl"]?.jsonPrimitive?.content
    val description = meccaDoc["description"]?.jsonPrimitive?.content
    val country = meccaDoc["countryCode"]?.jsonPrimitive?.content
    val website = meccaDoc["websiteUrl"]?.jsonPrimitive?.content

    val brandMap = mutableMapOf<String, JsonElement>()
    brandMap["brandId"] = JsonPrimitive(brandId)
    brandMap["name"] = JsonPrimitive(name)
    logoUrl?.let { brandMap["logoUrl"] = JsonPrimitive(it) }
    description?.let { brandMap["description"] = JsonPrimitive(it) }
    country?.let { brandMap["country"] = JsonPrimitive(it) }
    website?.let { brandMap["website"] = JsonPrimitive(it) }

    val brand = JsonObject(brandMap)
    return json.encodeToString(JsonObject.serializer(), brand)
}

// ===== Data Classes =====

data class SampleData(
    val products: List<SampleProduct>,
    val categories: List<SampleCategory>,
    val brands: List<SampleBrand>
)

data class SampleProduct(val productId: String, val document: String)
data class SampleCategory(val categoryId: String, val document: String)
data class SampleBrand(val brandId: String, val document: String)

data class RawDataKey(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val entityType: String
)
