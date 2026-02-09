package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.model.Select

/**
 * DynamoDB 기반 Slice Repository
 * 
 * Single Table Design:
 * - PK: TENANT#{tenantId}#ENTITY#{entityKey}
 * - SK: SLICE#v{version}#{sliceType}
 * 
 * Attributes:
 * - data (String)
 * - hash (String)
 * - rule_set_id (String)
 * - rule_set_version (String)
 * - tombstone_deleted (Boolean, optional)
 * - tombstone_reason (String, optional)
 */
class DynamoDbSliceRepository(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String = "ivm-lite-data"
) : SliceRepositoryPort, HealthCheckable {

    override val healthName: String = "dynamodb-slice"

    override suspend fun healthCheck(): Boolean {
        return try {
            dynamoClient.describeTable { it.tableName(tableName) }.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun putAllIdempotent(slices: List<SliceRecord>): Result<Unit> {
        return try {
            for (slice in slices) {
                val pk = buildPK(slice.tenantId, slice.entityKey)
                val sk = buildSK(slice.version, slice.sliceType)

                // 멱등성 체크
                val existing = getItem(pk, sk)
                if (existing != null) {
                    val existingHash = existing["hash"]?.s()
                        ?: throw DomainError.StorageError("Missing required field 'hash' in existing DynamoDB slice item for $pk/$sk")
                    if (existingHash != slice.hash) {
                        return Result.Err(
                            DomainError.InvariantViolation("Slice invariant mismatch for $pk/$sk")
                        )
                    }
                    // 동일 hash면 skip
                } else {
                    // 새 레코드 저장
                    val item = buildItem(pk, sk, slice)
                    dynamoClient.putItem {
                        it.tableName(tableName)
                        it.item(item)
                    }.await()
                }
            }

            Result.Ok(Unit)
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB putAllIdempotent failed: ${e.message}")
            )
        }
    }

    override suspend fun batchGet(
        tenantId: TenantId,
        keys: List<SliceRepositoryPort.SliceKey>,
        includeTombstones: Boolean
    ): Result<List<SliceRecord>> {
        if (keys.isEmpty()) {
            return Result.Ok(emptyList())
        }

        return try {
            // N+1 방지: BatchGetItem API 사용 (최대 100개 동시 처리)
            val batchKeys: List<Map<String, AttributeValue>> = keys.map { key ->
                mapOf(
                    "PK" to AttributeValue.builder().s(buildPK(key.tenantId, key.entityKey)).build(),
                    "SK" to AttributeValue.builder().s(buildSK(key.version, key.sliceType)).build()
                )
            }

            // DynamoDB BatchGetItem은 최대 100개 제한
            val allFetchedItems = mutableMapOf<String, Map<String, AttributeValue>>()
            for (chunk in batchKeys.chunked(100)) {
                val response = dynamoClient.batchGetItem { builder ->
                    builder.requestItems(
                        mapOf(tableName to KeysAndAttributes.builder().keys(chunk).build())
                    )
                }.await()

                val items = response.responses()[tableName] ?: continue
                for (item in items) {
                    val itemPk = item["PK"]?.s() ?: continue
                    val itemSk = item["SK"]?.s() ?: continue
                    allFetchedItems["$itemPk|$itemSk"] = item
                }
            }

            // 요청 순서대로 결과 매핑, 누락 시 에러
            val results = mutableListOf<SliceRecord>()
            for (key in keys) {
                val pk = buildPK(key.tenantId, key.entityKey)
                val sk = buildSK(key.version, key.sliceType)
                val item = allFetchedItems["$pk|$sk"]
                    ?: return Result.Err(
                        DomainError.NotFoundError(
                            "Slice",
                            "${key.tenantId.value}:${key.entityKey.value}:${key.sliceType.name}@${key.version}"
                        )
                    )

                val record = when (val r = parseSliceRecord(item)) {
                    is Result.Ok -> r.value
                    is Result.Err -> return r
                }
                if (includeTombstones || record.tombstone == null) {
                    results.add(record)
                } else {
                    return Result.Err(
                        DomainError.NotFoundError(
                            "Slice",
                            "${key.tenantId.value}:${key.entityKey.value}:${key.sliceType.name}@${key.version} (tombstone)"
                        )
                    )
                }
            }

            Result.Ok(results)
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB batchGet failed: ${e.message}")
            )
        }
    }

    override suspend fun getByVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        includeTombstones: Boolean
    ): Result<List<SliceRecord>> {
        return try {
            val pk = buildPK(tenantId, entityKey)
            val skPrefix = "SLICE#v${version.toString().padStart(10, '0')}#"

            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(pk).build(),
                        ":sk" to AttributeValue.builder().s(skPrefix).build()
                    )
                )
            }.await()

            val slices = mutableListOf<SliceRecord>()
            for (item in response.items()) {
                val record = when (val r = parseSliceRecord(item)) {
                    is Result.Ok -> r.value
                    is Result.Err -> return r
                }
                if (includeTombstones || record.tombstone == null) {
                    slices.add(record)
                }
            }
            Result.Ok(slices)
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB getByVersion failed: ${e.message}")
            )
        }
    }

    private suspend fun getItem(pk: String, sk: String): Map<String, AttributeValue>? {
        val response = dynamoClient.getItem {
            it.tableName(tableName)
            it.key(
                mapOf(
                    "PK" to AttributeValue.builder().s(pk).build(),
                    "SK" to AttributeValue.builder().s(sk).build()
                )
            )
        }.await()

        return if (response.hasItem()) response.item() else null
    }

    private fun buildItem(pk: String, sk: String, slice: SliceRecord): Map<String, AttributeValue> {
        val item = mutableMapOf(
            "PK" to AttributeValue.builder().s(pk).build(),
            "SK" to AttributeValue.builder().s(sk).build(),
            "tenant_id" to AttributeValue.builder().s(slice.tenantId.value).build(),
            "entity_key" to AttributeValue.builder().s(slice.entityKey.value).build(),
            "version" to AttributeValue.builder().n(slice.version.toString()).build(),
            "slice_type" to AttributeValue.builder().s(slice.sliceType.name).build(),
            "data" to AttributeValue.builder().s(slice.data).build(),
            "hash" to AttributeValue.builder().s(slice.hash).build(),
            "rule_set_id" to AttributeValue.builder().s(slice.ruleSetId).build(),
            "rule_set_version" to AttributeValue.builder().s(slice.ruleSetVersion.toString()).build()
        )

        // Tombstone (optional)
        slice.tombstone?.let { tombstone ->
            item["tombstone_deleted"] = AttributeValue.builder().bool(tombstone.isDeleted).build()
            item["tombstone_deleted_at_version"] = AttributeValue.builder().n(tombstone.deletedAtVersion.toString()).build()
            item["tombstone_delete_reason"] = AttributeValue.builder().s(tombstone.deleteReason.name).build()
        }

        return item
    }

    private fun parseSliceRecord(item: Map<String, AttributeValue>): Result<SliceRecord> {
        val tenantIdStr = item["tenant_id"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'tenant_id' in DynamoDB item"))
        val entityKeyStr = item["entity_key"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'entity_key' in DynamoDB item"))
        val versionStr = item["version"]?.n()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'version' in DynamoDB item"))
        val version = versionStr.toLongOrNull()
            ?: return Result.Err(DomainError.StorageError("Invalid version format in DynamoDB item: $versionStr"))
        val sliceTypeStr = item["slice_type"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'slice_type' in DynamoDB item"))
        val data = item["data"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'data' in DynamoDB item"))
        val hash = item["hash"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'hash' in DynamoDB item"))
        val ruleSetId = item["rule_set_id"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'rule_set_id' in DynamoDB item"))
        val ruleSetVersionStr = item["rule_set_version"]?.s()
            ?: return Result.Err(DomainError.StorageError("Missing required field 'rule_set_version' in DynamoDB item"))

        val tombstone = if (item["tombstone_deleted"]?.bool() == true) {
            val deletedAtVersionStr = item["tombstone_deleted_at_version"]?.n()
                ?: return Result.Err(DomainError.StorageError("Missing required field 'tombstone_deleted_at_version' in DynamoDB item when tombstone_deleted is true"))
            val deletedAtVersion = deletedAtVersionStr.toLongOrNull()
                ?: return Result.Err(DomainError.StorageError("Invalid tombstone_deleted_at_version format in DynamoDB item: $deletedAtVersionStr"))
            val deleteReasonStr = item["tombstone_delete_reason"]?.s()
                ?: return Result.Err(DomainError.StorageError("Missing required field 'tombstone_delete_reason' in DynamoDB item when tombstone_deleted is true"))
            val deleteReason = com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason.fromDbValue(deleteReasonStr)
            Tombstone(
                isDeleted = true,
                deletedAtVersion = deletedAtVersion,
                deleteReason = deleteReason
            )
        } else null

        return try {
            Result.Ok(SliceRecord(
                tenantId = TenantId(tenantIdStr),
                entityKey = EntityKey(entityKeyStr),
                version = version,
                sliceType = SliceType.valueOf(sliceTypeStr),
                data = data,
                hash = hash,
                ruleSetId = ruleSetId,
                ruleSetVersion = SemVer.parse(ruleSetVersionStr),
                tombstone = tombstone
            ))
        } catch (e: IllegalArgumentException) {
            Result.Err(DomainError.StorageError("Invalid SliceType or SemVer in DynamoDB item: ${e.message}"))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to parse SliceRecord from DynamoDB item: ${e.message}"))
        }
    }

    override suspend fun findByKeyPrefix(
        tenantId: TenantId,
        keyPrefix: String,
        sliceType: SliceType?,
        limit: Int,
        cursor: String?
    ): Result<SliceRepositoryPort.RangeQueryResult> {
        return try {
            val pkPrefix = "TENANT#${tenantId.value}#ENTITY#$keyPrefix"
            
            val requestBuilder = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("begins_with(PK, :pk)")
                .expressionAttributeValues(
                    mapOf(":pk" to AttributeValue.builder().s(pkPrefix).build())
                )
                .limit(limit + 1)
            
            cursor?.let { 
                // 간단한 커서 파싱 (실제로는 lastEvaluatedKey 사용)
                requestBuilder.exclusiveStartKey(mapOf(
                    "PK" to AttributeValue.builder().s(it.split("|")[0]).build(),
                    "SK" to AttributeValue.builder().s(it.split("|").getOrElse(1) { "" }).build()
                ))
            }
            
            val response = dynamoClient.query(requestBuilder.build()).await()
            
            val items = response.items().mapNotNull { item ->
                when (val r = parseSliceRecord(item)) {
                    is Result.Ok -> {
                        val record = r.value
                        if (sliceType == null || record.sliceType == sliceType) {
                            if (record.tombstone == null) record else null
                        } else null
                    }
                    is Result.Err -> null  // 파싱 실패 시 스킵
                }
            }
            
            val hasMore = items.size > limit
            val resultItems = if (hasMore) items.take(limit) else items
            val nextCursor = if (hasMore && resultItems.isNotEmpty()) {
                val last = response.lastEvaluatedKey()
                "${last["PK"]?.s()}|${last["SK"]?.s()}"
            } else null
            
            Result.Ok(SliceRepositoryPort.RangeQueryResult(
                items = resultItems,
                nextCursor = nextCursor,
                hasMore = hasMore
            ))
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("DynamoDB findByKeyPrefix failed: ${e.message}"))
        }
    }

    override suspend fun count(
        tenantId: TenantId,
        keyPrefix: String?,
        sliceType: SliceType?
    ): Result<Long> {
        return try {
            val pkPattern = if (keyPrefix != null) {
                "TENANT#${tenantId.value}#ENTITY#$keyPrefix"
            } else {
                "TENANT#${tenantId.value}#ENTITY#"
            }
            
            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("begins_with(PK, :pk)")
                it.expressionAttributeValues(
                    mapOf(":pk" to AttributeValue.builder().s(pkPattern).build())
                )
                it.select(Select.COUNT)
            }.await()
            
            Result.Ok(response.count().toLong())
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("DynamoDB count failed: ${e.message}"))
        }
    }

    override suspend fun getLatestVersion(
        tenantId: TenantId,
        entityKey: EntityKey,
        sliceType: SliceType?
    ): Result<List<SliceRecord>> {
        return try {
            val pk = buildPK(tenantId, entityKey)
            
            // SK 역순으로 조회하여 최신 버전 가져오기
            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(pk).build(),
                        ":sk" to AttributeValue.builder().s("SLICE#").build()
                    )
                )
                it.scanIndexForward(false)  // 역순
                it.limit(10)  // 최대 10개 SliceType
            }.await()
            
            if (response.items().isEmpty()) {
                return Result.Ok(emptyList())
            }
            
            val allSlices = response.items().mapNotNull { item ->
                when (val r = parseSliceRecord(item)) {
                    is Result.Ok -> {
                        val record = r.value
                        if (record.tombstone == null) record else null
                    }
                    is Result.Err -> null  // 파싱 실패 시 스킵
                }
            }
            
            if (allSlices.isEmpty()) {
                return Result.Ok(emptyList())
            }
            
            val latestVersion = allSlices.maxOf { it.version }
            val result = allSlices.filter { it.version == latestVersion }
                .filter { sliceType == null || it.sliceType == sliceType }
            
            Result.Ok(result)
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("DynamoDB getLatestVersion failed: ${e.message}"))
        }
    }

    private fun buildPK(tenantId: TenantId, entityKey: EntityKey): String =
        "TENANT#${tenantId.value}#ENTITY#${entityKey.value}"

    private fun buildSK(version: Long, sliceType: SliceType): String =
        "SLICE#v${version.toString().padStart(10, '0')}#${sliceType.name}"
}
