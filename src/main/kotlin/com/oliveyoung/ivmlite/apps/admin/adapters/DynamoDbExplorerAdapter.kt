package com.oliveyoung.ivmlite.apps.admin.adapters

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.apps.admin.config.AdminConstants
import com.oliveyoung.ivmlite.apps.admin.ports.AutocompleteItem
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.apps.admin.ports.RawDataItem
import com.oliveyoung.ivmlite.apps.admin.ports.RawDataListResult
import com.oliveyoung.ivmlite.apps.admin.ports.SearchResults
import com.oliveyoung.ivmlite.apps.admin.ports.SliceItem
import com.oliveyoung.ivmlite.apps.admin.ports.SliceListResult
import com.oliveyoung.ivmlite.apps.admin.ports.SliceTypeInfo
import com.oliveyoung.ivmlite.apps.admin.ports.EntitySearchResult
import com.oliveyoung.ivmlite.apps.admin.ports.EntitySearchItem
import com.oliveyoung.ivmlite.apps.admin.ports.VersionHistoryItem
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

/**
 * DynamoDB Explorer Adapter
 *
 * P0: 헥사고날 아키텍처 준수
 * - Application 레이어에서 직접 DynamoDB 사용 금지
 * - 이 Adapter를 통해서만 접근
 */
class DynamoDbExplorerAdapter(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String
) : ExplorerRepositoryPort {

    private val logger = LoggerFactory.getLogger(DynamoDbExplorerAdapter::class.java)

    // DynamoDB 속성 키 상수 (P1: 하드코딩 방지)
    companion object {
        private const val ATTR_PK = "PK"
        private const val ATTR_SK = "SK"
        private const val ATTR_ENTITY_KEY = "entity_key"
        private const val ATTR_VERSION = "version"
        private const val ATTR_SLICE_VERSION = "slice_version"
        private const val ATTR_SCHEMA_ID = "schema_id"
        private const val ATTR_SCHEMA_VERSION = "schema_version"
        private const val ATTR_SLICE_TYPE = "slice_type"
        private const val ATTR_CREATED_AT = "created_at"

        private const val PREFIX_TENANT = "TENANT#"
        private const val PREFIX_RAWDATA = "RAWDATA#"
        private const val PREFIX_SLICE = "SLICE#"
        private const val SUFFIX_DELIMITER = "#"

        private const val TYPE_RAWDATA = "rawdata"
        private const val TYPE_SLICE = "slice"
        private const val TYPE_UNKNOWN = "unknown"

        private const val EXPR_VAR_PK_PREFIX = ":pkPrefix"
        private const val EXPR_VAR_SK_PREFIX = ":skPrefix"

        private const val FILTER_PK_BEGINS_WITH =
            "begins_with($ATTR_PK, $EXPR_VAR_PK_PREFIX)"
        private const val FILTER_PK_SK_BEGINS_WITH =
            "begins_with($ATTR_PK, $EXPR_VAR_PK_PREFIX) " +
                "AND begins_with($ATTR_SK, $EXPR_VAR_SK_PREFIX)"
    }

    override suspend fun listRawData(
        tenantId: TenantId,
        entityType: String?,
        limit: Int,
        cursor: String?
    ): Either<DomainError, RawDataListResult> = either {
        val scanLimit = limit * AdminConstants.DYNAMO_SCAN_BUFFER_MULTIPLIER

        val scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .limit(scanLimit)
            .filterExpression(FILTER_PK_BEGINS_WITH)
            .expressionAttributeValues(
                mapOf(EXPR_VAR_PK_PREFIX to attr(tenantPkPrefix(tenantId)))
            )
            .build()

        val response = runCatching { dynamoClient.scan(scanRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to scan RawData: ${e.message}", e)
                raise(DomainError.StorageError("Failed to list RawData: ${e.message}"))
            }

        val entityMap = mutableMapOf<String, RawDataItem>()

        for (item in response.items()) {
            val sk = item[ATTR_SK]?.s() ?: continue
            if (!sk.startsWith("${PREFIX_RAWDATA}v")) continue

            val entityKey = item[ATTR_ENTITY_KEY]?.s() ?: continue
            val version = item[ATTR_VERSION]?.n()?.toLongOrNull() ?: continue
            val schemaId = item[ATTR_SCHEMA_ID]?.s()?.takeIf { it.isNotBlank() }
            val schemaVersion = item[ATTR_SCHEMA_VERSION]?.s()?.takeIf { it.isNotBlank() }
            val createdAt = item[ATTR_CREATED_AT]?.s()

            // schemaId@schemaVersion 형식, 빈 값일 경우 적절히 처리
            val schemaRef = when {
                schemaId != null && schemaVersion != null -> "$schemaId@$schemaVersion"
                schemaId != null -> schemaId
                else -> ""
            }

            val existing = entityMap[entityKey]
            if (existing == null || version > existing.version) {
                entityMap[entityKey] = RawDataItem(
                    entityKey = entityKey,
                    schemaId = schemaRef,
                    version = version,
                    updatedAt = createdAt
                )
            }
        }

        // cursor-based pagination: cursor 다음 아이템부터 시작 (cursor는 이전 페이지 마지막 항목)
        val sortedItems = entityMap.values.sortedBy { it.entityKey }
        val filteredItems = if (cursor != null) {
            sortedItems.filter { it.entityKey > cursor }
        } else {
            sortedItems
        }

        val finalItems = filteredItems.take(limit)
        val hasMore = filteredItems.size > limit

        RawDataListResult(
            items = finalItems,
            nextCursor = if (hasMore) finalItems.lastOrNull()?.entityKey else null,
            totalCount = entityMap.size
        )
    }

    override suspend fun listSlicesByType(
        tenantId: TenantId,
        sliceType: SliceType,
        limit: Int,
        cursor: String?
    ): Either<DomainError, SliceListResult> = either {
        val scanLimit = limit * AdminConstants.DYNAMO_SCAN_BUFFER_MULTIPLIER

        val scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .limit(scanLimit)
            .filterExpression(FILTER_PK_SK_BEGINS_WITH)
            .expressionAttributeValues(
                mapOf(
                    EXPR_VAR_PK_PREFIX to attr(tenantPkPrefix(tenantId)),
                    EXPR_VAR_SK_PREFIX to attr(PREFIX_SLICE)
                )
            )
            .build()

        val response = runCatching { dynamoClient.scan(scanRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to scan Slices: ${e.message}", e)
                raise(DomainError.StorageError("Failed to list slices: ${e.message}"))
            }

        val sliceTypeValue = sliceType.toDbValue()
        val entityMap = mutableMapOf<String, SliceItem>()

        for (item in response.items()) {
            val itemSliceType = item[ATTR_SLICE_TYPE]?.s() ?: continue
            if (itemSliceType != sliceTypeValue) continue

            val entityKey = item[ATTR_ENTITY_KEY]?.s() ?: continue
            val version = item[ATTR_SLICE_VERSION]?.n()?.toLongOrNull() ?: continue
            val createdAt = item[ATTR_CREATED_AT]?.s()

            val existing = entityMap[entityKey]
            if (existing == null || version > existing.version) {
                entityMap[entityKey] = SliceItem(
                    sliceId = "$entityKey#$sliceTypeValue",
                    sliceType = sliceTypeValue,
                    sourceKey = entityKey,
                    version = version,
                    updatedAt = createdAt
                )
            }
        }

        // cursor-based pagination: cursor 이후의 아이템만 필터링
        val sortedItems = entityMap.values.sortedBy { it.sourceKey }
        val filteredItems = if (cursor != null) {
            sortedItems.dropWhile { it.sourceKey <= cursor }
        } else {
            sortedItems
        }

        val finalItems = filteredItems.take(limit)
        val hasMore = filteredItems.size > limit

        SliceListResult(
            items = finalItems,
            nextCursor = if (hasMore) finalItems.lastOrNull()?.sourceKey else null,
            totalCount = entityMap.size
        )
    }

    override suspend fun getSliceTypes(tenantId: TenantId): Either<DomainError, List<SliceTypeInfo>> = either {
        val scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression(FILTER_PK_SK_BEGINS_WITH)
            .expressionAttributeValues(
                mapOf(
                    EXPR_VAR_PK_PREFIX to attr(tenantPkPrefix(tenantId)),
                    EXPR_VAR_SK_PREFIX to attr(PREFIX_SLICE)
                )
            )
            .build()

        val response = runCatching { dynamoClient.scan(scanRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to scan slice types: ${e.message}", e)
                raise(DomainError.StorageError("Failed to get slice types: ${e.message}"))
            }

        val typeEntityMap = mutableMapOf<String, MutableSet<String>>()

        for (item in response.items()) {
            val sliceType = item[ATTR_SLICE_TYPE]?.s() ?: continue
            val entityKey = item[ATTR_ENTITY_KEY]?.s() ?: continue

            typeEntityMap.getOrPut(sliceType) { mutableSetOf() }.add(entityKey)
        }

        typeEntityMap.map { (type, entities) ->
            SliceTypeInfo(sliceType = type, count = entities.size)
        }.sortedBy { it.sliceType }
    }

    override suspend fun search(
        tenantId: TenantId,
        query: String,
        limit: Int
    ): Either<DomainError, SearchResults> = either {
        val scanLimit = limit * AdminConstants.DYNAMO_SCAN_BUFFER_MULTIPLIER

        val scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .limit(scanLimit)
            .filterExpression(FILTER_PK_BEGINS_WITH)
            .expressionAttributeValues(
                mapOf(EXPR_VAR_PK_PREFIX to attr(tenantPkPrefix(tenantId)))
            )
            .build()

        val response = runCatching { dynamoClient.scan(scanRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to search: ${e.message}", e)
                raise(DomainError.StorageError("Failed to search: ${e.message}"))
            }

        val rawDataItems = mutableListOf<RawDataItem>()
        val sliceItems = mutableListOf<SliceItem>()

        for (item in response.items()) {
            val entityKey = item[ATTR_ENTITY_KEY]?.s() ?: continue

            if (!entityKey.contains(query, ignoreCase = true)) continue

            val sk = item[ATTR_SK]?.s() ?: continue

            when {
                sk.startsWith(PREFIX_RAWDATA) -> {
                    val version = item[ATTR_VERSION]?.n()?.toLongOrNull() ?: 0L
                    rawDataItems.add(
                        RawDataItem(
                            entityKey = entityKey,
                            schemaId = item[ATTR_SCHEMA_ID]?.s() ?: "",
                            version = version,
                            updatedAt = item[ATTR_CREATED_AT]?.s()
                        )
                    )
                }
                sk.startsWith(PREFIX_SLICE) -> {
                    val sliceType = item[ATTR_SLICE_TYPE]?.s() ?: ""
                    sliceItems.add(
                        SliceItem(
                            sliceId = "$entityKey#$sliceType",
                            sliceType = sliceType,
                            sourceKey = entityKey,
                            version = item[ATTR_SLICE_VERSION]?.n()?.toLongOrNull() ?: 0L,
                            updatedAt = item[ATTR_CREATED_AT]?.s()
                        )
                    )
                }
            }
        }

        SearchResults(
            rawData = rawDataItems.distinctBy { it.entityKey }.take(limit),
            slices = sliceItems.distinctBy { it.sliceId }.take(limit),
            views = emptyList()
        )
    }

    override suspend fun autocomplete(
        tenantId: TenantId,
        prefix: String,
        limit: Int
    ): Either<DomainError, List<AutocompleteItem>> = either {
        val scanLimit = limit * AdminConstants.AUTOCOMPLETE_BUFFER_MULTIPLIER

        val scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .limit(scanLimit)
            .filterExpression(FILTER_PK_BEGINS_WITH)
            .expressionAttributeValues(
                mapOf(EXPR_VAR_PK_PREFIX to attr(tenantPkPrefix(tenantId)))
            )
            .build()

        val response = runCatching { dynamoClient.scan(scanRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to autocomplete: ${e.message}", e)
                raise(DomainError.StorageError("Failed to autocomplete: ${e.message}"))
            }

        val results = mutableSetOf<AutocompleteItem>()

        for (item in response.items()) {
            val entityKey = item[ATTR_ENTITY_KEY]?.s() ?: continue

            if (entityKey.startsWith(prefix, ignoreCase = true)) {
                val sk = item[ATTR_SK]?.s() ?: ""
                val type = when {
                    sk.startsWith(PREFIX_RAWDATA) -> TYPE_RAWDATA
                    sk.startsWith(PREFIX_SLICE) -> TYPE_SLICE
                    else -> TYPE_UNKNOWN
                }

                results.add(
                    AutocompleteItem(
                        value = entityKey,
                        type = type,
                        label = entityKey
                    )
                )
            }

            if (results.size >= limit) break
        }

        results.toList()
    }

    override suspend fun searchEntities(
        tenantId: TenantId,
        query: String,
        limit: Int
    ): Either<DomainError, EntitySearchResult> = either {
        val searchResult = search(tenantId, query, limit).bind()

        EntitySearchResult(
            items = searchResult.rawData.map { item ->
                EntitySearchItem(
                    entityKey = item.entityKey,
                    type = TYPE_RAWDATA
                )
            },
            nextCursor = null,
            totalCount = searchResult.rawData.size
        )
    }

    override suspend fun getVersionHistory(
        tenantId: TenantId,
        entityKey: EntityKey,
        limit: Int
    ): Either<DomainError, List<VersionHistoryItem>> = either {
        val pk = "$PREFIX_TENANT${tenantId.value}$SUFFIX_DELIMITER${entityKey.value}"
        val skPrefix = PREFIX_RAWDATA

        val queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("$ATTR_PK = :pk AND begins_with($ATTR_SK, :skPrefix)")
            .expressionAttributeValues(
                mapOf(
                    ":pk" to attr(pk),
                    ":skPrefix" to attr(skPrefix)
                )
            )
            .scanIndexForward(false) // 최신 버전부터 정렬
            .limit(limit)
            .build()

        val response = runCatching { dynamoClient.query(queryRequest).await() }
            .getOrElse { e ->
                logger.error("Failed to query version history: ${e.message}", e)
                raise(DomainError.StorageError("Failed to get version history: ${e.message}"))
            }

        // DynamoDB Query에서 이미 scanIndexForward(false)로 내림차순 정렬됨
        response.items().mapNotNull { item ->
            val version = item[ATTR_VERSION]?.n()?.toLongOrNull() ?: return@mapNotNull null
            val createdAt = item[ATTR_CREATED_AT]?.s()
            val payloadHash = item["payload_hash"]?.s() ?: ""

            VersionHistoryItem(
                version = version,
                createdAt = createdAt,
                payloadHash = payloadHash
            )
        }
    }

    private fun attr(value: String): AttributeValue =
        AttributeValue.builder().s(value).build()

    private fun tenantPkPrefix(tenantId: TenantId): String =
        "$PREFIX_TENANT${tenantId.value}$SUFFIX_DELIMITER"
}
