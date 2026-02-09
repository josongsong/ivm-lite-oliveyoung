package com.oliveyoung.ivmlite.pkg.slices.adapters

import com.oliveyoung.ivmlite.pkg.slices.domain.InvertedIndexEntry
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.model.Select

/**
 * DynamoDB 기반 Inverted Index Repository
 * 
 * Single Table Design:
 * - PK: TENANT#{tenantId}#INDEX#{indexType}#{indexValue}
 * - SK: ENTITY#{refEntityKey}#SLICE#{refSliceType}
 * 
 * GSI (선택사항):
 * - GSI1-PK: TENANT#{tenantId}#ENTITY#{refEntityKey}
 * - GSI1-SK: INDEX#{indexType}#{indexValue}
 * - 용도: 특정 엔티티가 어떤 인덱스에 포함되어 있는지 역조회
 */
class DynamoDbInvertedIndexRepository(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String = "ivm-lite-data"
) : InvertedIndexRepositoryPort, HealthCheckable {

    override val healthName: String = "dynamodb-inverted-index"

    override suspend fun healthCheck(): Boolean {
        return try {
            dynamoClient.describeTable { it.tableName(tableName) }.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun putAllIdempotent(entries: List<InvertedIndexEntry>): Result<Unit> {
        if (entries.isEmpty()) {
            return Result.Ok(Unit)
        }

        return try {
            // BatchWriteItem API 사용 (최대 25개씩 배치 처리)
            val now = java.time.Instant.now().toString()
            val writeRequests: List<WriteRequest> = entries.map { entry ->
                val pk = buildPK(entry.tenantId, entry.indexType, entry.indexValue)
                val sk = buildSK(entry.refEntityKey, entry.sliceType)

                WriteRequest.builder()
                    .putRequest(
                        PutRequest.builder()
                            .item(
                                mapOf(
                                    "PK" to AttributeValue.builder().s(pk).build(),
                                    "SK" to AttributeValue.builder().s(sk).build(),
                                    "tenant_id" to AttributeValue.builder().s(entry.tenantId.value).build(),
                                    "ref_entity_key" to AttributeValue.builder().s(entry.refEntityKey.value).build(),
                                    "ref_version" to AttributeValue.builder().n(entry.refVersion.value.toString()).build(),
                                    "target_entity_key" to AttributeValue.builder().s(entry.targetEntityKey.value).build(),
                                    "target_version" to AttributeValue.builder().n(entry.targetVersion.value.toString()).build(),
                                    "index_type" to AttributeValue.builder().s(entry.indexType).build(),
                                    "index_value" to AttributeValue.builder().s(entry.indexValue).build(),
                                    "slice_type" to AttributeValue.builder().s(entry.sliceType.name).build(),
                                    "slice_hash" to AttributeValue.builder().s(entry.sliceHash).build(),
                                    "tombstone" to AttributeValue.builder().bool(entry.tombstone).build(),
                                    "created_at" to AttributeValue.builder().s(now).build()
                                )
                            )
                            .build()
                    )
                    .build()
            }

            // DynamoDB BatchWriteItem은 최대 25개 제한
            for (chunk in writeRequests.chunked(25)) {
                dynamoClient.batchWriteItem { builder ->
                    builder.requestItems(mapOf(tableName to chunk))
                }.await()
            }

            Result.Ok(Unit)
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB putAllIdempotent failed: ${e.message}")
            )
        }
    }

    override suspend fun listTargets(
        tenantId: TenantId,
        refPk: String,
        limit: Int
    ): Result<List<InvertedIndexEntry>> {
        return try {
            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(refPk).build()
                    )
                )
                it.limit(limit)
            }.await()

            val entries = response.items().map { item ->
                try {
                    parseEntry(item)
                } catch (e: DomainError) {
                    // 파싱 실패 시 에러를 던져서 전체 조회를 실패로 처리
                    throw e
                }
            }
            Result.Ok(entries)
        } catch (e: DomainError) {
            Result.Err(e)
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB listTargets failed: ${e.message}")
            )
        }
    }

    /**
     * RFC-IMPL-012: Fanout을 위한 역참조 조회
     */
    override suspend fun queryByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
        limit: Int,
        cursor: String?,
    ): Result<com.oliveyoung.ivmlite.pkg.slices.ports.FanoutQueryResult> {
        return try {
            val pk = buildPK(tenantId, indexType, indexValue)
            
            val queryBuilder = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk")
                it.filterExpression("tombstone = :false OR attribute_not_exists(tombstone)")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(pk).build(),
                        ":false" to AttributeValue.builder().bool(false).build(),
                    )
                )
                it.limit(limit)
                
                // 커서 기반 페이지네이션
                if (cursor != null) {
                    it.exclusiveStartKey(
                        mapOf(
                            "PK" to AttributeValue.builder().s(pk).build(),
                            "SK" to AttributeValue.builder().s(cursor).build(),
                        )
                    )
                }
            }
            
            val response = queryBuilder.await()
            
            // RFC-IMPL-013: targetEntityKey(참조하는 엔티티)를 반환해야 함
            // refEntityKey는 참조되는 엔티티(예: BRAND), targetEntityKey는 참조하는 엔티티(예: PRODUCT)
            val entries = response.items().mapNotNull { item ->
                try {
                    val entry = parseEntry(item)
                    com.oliveyoung.ivmlite.pkg.slices.ports.FanoutTarget(
                        entityKey = entry.targetEntityKey,  // 재슬라이싱 대상
                        currentVersion = entry.targetVersion.value,
                    )
                } catch (e: Exception) {
                    null  // 파싱 실패 시 스킵
                }
            }.distinctBy { it.entityKey.value }
            
            val nextCursor = response.lastEvaluatedKey()?.get("SK")?.s()
            
            Result.Ok(
                com.oliveyoung.ivmlite.pkg.slices.ports.FanoutQueryResult(entries, nextCursor)
            )
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB queryByIndexType failed: ${e.message}")
            )
        }
    }

    /**
     * RFC-IMPL-012: Fanout 대상 수 조회
     */
    override suspend fun countByIndexType(
        tenantId: TenantId,
        indexType: String,
        indexValue: String,
    ): Result<Long> {
        return try {
            val pk = buildPK(tenantId, indexType, indexValue)
            
            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk")
                it.filterExpression("tombstone = :false OR attribute_not_exists(tombstone)")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(pk).build(),
                        ":false" to AttributeValue.builder().bool(false).build(),
                    )
                )
                it.select(Select.COUNT)
            }.await()
            
            Result.Ok(response.count().toLong())
        } catch (e: Exception) {
            Result.Err(
                DomainError.StorageError("DynamoDB countByIndexType failed: ${e.message}")
            )
        }
    }

    // 테스트 헬퍼 (InMemory 호환)
    suspend fun queryByIndexForTest(
        tenantId: TenantId,
        indexType: String,
        indexValue: String
    ): List<InvertedIndexEntry> {
        val pk = buildPK(tenantId, indexType, indexValue)
        return when (val result = listTargets(tenantId, pk, 100)) {
            is Result.Ok -> result.value
            is Result.Err -> throw result.error
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

    private fun parseEntry(item: Map<String, AttributeValue>): InvertedIndexEntry {
        val tenantIdStr = item["tenant_id"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'tenant_id' in DynamoDB inverted index item")
        val refEntityKeyStr = item["ref_entity_key"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'ref_entity_key' in DynamoDB inverted index item")
        val refVersionStr = item["ref_version"]?.n()
            ?: throw DomainError.StorageError("Missing required field 'ref_version' in DynamoDB inverted index item")
        val refVersion = refVersionStr.toLongOrNull()
            ?: throw DomainError.StorageError("Invalid ref_version format in DynamoDB inverted index item: $refVersionStr")
        val targetEntityKeyStr = item["target_entity_key"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'target_entity_key' in DynamoDB inverted index item")
        val targetVersionStr = item["target_version"]?.n()
            ?: throw DomainError.StorageError("Missing required field 'target_version' in DynamoDB inverted index item")
        val targetVersion = targetVersionStr.toLongOrNull()
            ?: throw DomainError.StorageError("Invalid target_version format in DynamoDB inverted index item: $targetVersionStr")
        val indexType = item["index_type"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'index_type' in DynamoDB inverted index item")
        val indexValue = item["index_value"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'index_value' in DynamoDB inverted index item")
        val sliceTypeStr = item["slice_type"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'slice_type' in DynamoDB inverted index item")
        val sliceHash = item["slice_hash"]?.s()
            ?: throw DomainError.StorageError("Missing required field 'slice_hash' in DynamoDB inverted index item")

        return try {
            InvertedIndexEntry(
                tenantId = TenantId(tenantIdStr),
                refEntityKey = EntityKey(refEntityKeyStr),
                refVersion = com.oliveyoung.ivmlite.shared.domain.types.VersionLong(refVersion),
                targetEntityKey = EntityKey(targetEntityKeyStr),
                targetVersion = com.oliveyoung.ivmlite.shared.domain.types.VersionLong(targetVersion),
                indexType = indexType,
                indexValue = indexValue,
                sliceType = SliceType.valueOf(sliceTypeStr),
                sliceHash = sliceHash,
                tombstone = item["tombstone"]?.bool() ?: false
            )
        } catch (e: IllegalArgumentException) {
            throw DomainError.StorageError("Invalid SliceType in DynamoDB inverted index item: ${e.message}")
        } catch (e: Exception) {
            throw DomainError.StorageError("Failed to parse InvertedIndexEntry from DynamoDB item: ${e.message}")
        }
    }

    private fun buildPK(tenantId: TenantId, indexType: String, indexValue: String): String =
        "TENANT#${tenantId.value}#INDEX#${indexType}#${indexValue}"

    private fun buildSK(refEntityKey: EntityKey, sliceType: SliceType): String =
        "ENTITY#${refEntityKey.value}#SLICE#${sliceType.name}"
}
