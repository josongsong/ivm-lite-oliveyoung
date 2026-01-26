package com.oliveyoung.ivmlite.pkg.rawdata.adapters

import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

/**
 * DynamoDB 기반 RawData Repository
 * 
 * Single Table Design:
 * - PK: TENANT#{tenantId}#ENTITY#{entityKey}
 * - SK: RAWDATA#v{version}
 * 
 * Attributes:
 * - payload_json (String)
 * - payload_hash (String)
 * - schema_id (String)
 * - schema_version (String)
 * - created_at (String, ISO 8601)
 */
class DynamoDbRawDataRepository(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String = "ivm-lite-data"
) : RawDataRepositoryPort, HealthCheckable {

    override val healthName: String = "dynamodb-rawdata"

    override suspend fun healthCheck(): Boolean {
        return try {
            dynamoClient.describeTable { it.tableName(tableName) }.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun putIdempotent(record: RawDataRecord): RawDataRepositoryPort.Result<Unit> {
        return try {
            val pk = buildPK(record.tenantId, record.entityKey)
            val sk = buildSK(record.version)

            // 멱등성 체크: 동일 PK/SK 존재 시 hash 일치 확인
            val existing = getItem(pk, sk)
            if (existing != null) {
                val existingHash = existing["payload_hash"]?.s()
                    ?: throw DomainError.StorageError("Missing required field 'payload_hash' in existing DynamoDB raw data item for $pk/$sk")
                val existingSchemaId = existing["schema_id"]?.s()
                    ?: throw DomainError.StorageError("Missing required field 'schema_id' in existing DynamoDB raw data item for $pk/$sk")
                val existingSchemaVersion = existing["schema_version"]?.s()
                    ?: throw DomainError.StorageError("Missing required field 'schema_version' in existing DynamoDB raw data item for $pk/$sk")

                return if (
                    existingHash == record.payloadHash &&
                    existingSchemaId == record.schemaId &&
                    existingSchemaVersion == record.schemaVersion.toString()
                ) {
                    RawDataRepositoryPort.Result.Ok(Unit)
                } else {
                    RawDataRepositoryPort.Result.Err(
                        DomainError.InvariantViolation("RawData invariant mismatch for $pk/$sk")
                    )
                }
            }

            // 새 레코드 저장
            dynamoClient.putItem {
                it.tableName(tableName)
                it.item(
                    mapOf(
                        "PK" to AttributeValue.builder().s(pk).build(),
                        "SK" to AttributeValue.builder().s(sk).build(),
                        "tenant_id" to AttributeValue.builder().s(record.tenantId.value).build(),
                        "entity_key" to AttributeValue.builder().s(record.entityKey.value).build(),
                        "version" to AttributeValue.builder().n(record.version.toString()).build(),
                        "payload_json" to AttributeValue.builder().s(record.payload).build(),
                        "payload_hash" to AttributeValue.builder().s(record.payloadHash).build(),
                        "schema_id" to AttributeValue.builder().s(record.schemaId).build(),
                        "schema_version" to AttributeValue.builder().s(record.schemaVersion.toString()).build(),
                        "created_at" to AttributeValue.builder().s(java.time.Instant.now().toString()).build()
                    )
                )
            }.await()

            RawDataRepositoryPort.Result.Ok(Unit)
        } catch (e: Exception) {
            RawDataRepositoryPort.Result.Err(
                DomainError.StorageError("DynamoDB putIdempotent failed: ${e.message}")
            )
        }
    }

    override suspend fun get(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long
    ): RawDataRepositoryPort.Result<RawDataRecord> {
        return try {
            val pk = buildPK(tenantId, entityKey)
            val sk = buildSK(version)

            val item = getItem(pk, sk)
                ?: return RawDataRepositoryPort.Result.Err(
                    DomainError.NotFoundError("RawData", "$pk/$sk")
                )

            val payloadJson = item["payload_json"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'payload_json' in DynamoDB raw data item")
            val payloadHash = item["payload_hash"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'payload_hash' in DynamoDB raw data item")
            val schemaId = item["schema_id"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'schema_id' in DynamoDB raw data item")
            val schemaVersionStr = item["schema_version"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'schema_version' in DynamoDB raw data item")

            val record = RawDataRecord(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                payload = payloadJson,
                payloadHash = payloadHash,
                schemaId = schemaId,
                schemaVersion = try {
                    com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse(schemaVersionStr)
                } catch (e: Exception) {
                    throw DomainError.StorageError("Invalid schema_version format in DynamoDB raw data item: $schemaVersionStr")
                }
            )

            RawDataRepositoryPort.Result.Ok(record)
        } catch (e: DomainError) {
            RawDataRepositoryPort.Result.Err(e)
        } catch (e: Exception) {
            RawDataRepositoryPort.Result.Err(
                DomainError.StorageError("DynamoDB get failed: ${e.message}")
            )
        }
    }

    override suspend fun getLatest(
        tenantId: TenantId,
        entityKey: EntityKey
    ): RawDataRepositoryPort.Result<RawDataRecord> {
        return try {
            val pk = buildPK(tenantId, entityKey)
            val skPrefix = "RAWDATA#v"

            // Query로 가장 최신 버전 조회 (SK 역순 정렬)
            val response = dynamoClient.query {
                it.tableName(tableName)
                it.keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                it.expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.builder().s(pk).build(),
                        ":sk" to AttributeValue.builder().s(skPrefix).build()
                    )
                )
                it.scanIndexForward(false)  // 역순 정렬
                it.limit(1)
            }.await()

            val item = response.items().firstOrNull()
                ?: return RawDataRepositoryPort.Result.Err(
                    DomainError.NotFoundError("RawData", "$pk (latest)")
                )

            val version = item["version"]?.n()?.toLongOrNull()
                ?: return RawDataRepositoryPort.Result.Err(
                    DomainError.StorageError("Invalid version in DynamoDB item")
                )

            val payloadJson = item["payload_json"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'payload_json' in DynamoDB raw data item")
            val payloadHash = item["payload_hash"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'payload_hash' in DynamoDB raw data item")
            val schemaId = item["schema_id"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'schema_id' in DynamoDB raw data item")
            val schemaVersionStr = item["schema_version"]?.s()
                ?: throw DomainError.StorageError("Missing required field 'schema_version' in DynamoDB raw data item")

            val record = RawDataRecord(
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                payload = payloadJson,
                payloadHash = payloadHash,
                schemaId = schemaId,
                schemaVersion = try {
                    com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse(schemaVersionStr)
                } catch (e: Exception) {
                    throw DomainError.StorageError("Invalid schema_version format in DynamoDB raw data item: $schemaVersionStr")
                }
            )

            RawDataRepositoryPort.Result.Ok(record)
        } catch (e: DomainError) {
            RawDataRepositoryPort.Result.Err(e)
        } catch (e: Exception) {
            RawDataRepositoryPort.Result.Err(
                DomainError.StorageError("DynamoDB getLatest failed: ${e.message}")
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

    private fun buildPK(tenantId: TenantId, entityKey: EntityKey): String =
        "TENANT#${tenantId.value}#ENTITY#${entityKey.value}"

    private fun buildSK(version: Long): String =
        "RAWDATA#v${version.toString().padStart(10, '0')}"
}
