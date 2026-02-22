package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * DynamoDB 기반 SinkRule Registry Adapter (RFC-022 Phase 2)
 *
 * contract_registry 테이블의 kind-status-index GSI를 사용하여
 * kind=SINK_RULE, status=ACTIVE인 SinkRule을 조회합니다.
 *
 * @param dynamoClient DynamoDB 비동기 클라이언트
 * @param tableName contract_registry 테이블명
 */
class DynamoDBSinkRuleRegistryAdapter(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String = "contract_registry",
) : SinkRuleRegistryPort {

    private val log = LoggerFactory.getLogger(DynamoDBSinkRuleRegistryAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findByEntityAndSliceType(
        entityType: String,
        sliceType: SliceType
    ): Result<List<SinkRule>> {
        return findAllActive().map { rules ->
            rules.filter { rule ->
                rule.input.entityTypes.any { it.equals(entityType, ignoreCase = true) } &&
                    rule.input.sliceTypes.contains(sliceType)
            }
        }
    }

    override suspend fun findByEntityType(entityType: String): Result<List<SinkRule>> {
        return findAllActive().map { rules ->
            rules.filter { rule ->
                rule.input.entityTypes.any { it.equals(entityType, ignoreCase = true) }
            }
        }
    }

    override suspend fun findAllActive(): Result<List<SinkRule>> {
        return try {
            val items = queryByKindStatus(ContractKind.SINK_RULE.wireValue, "ACTIVE")
            val rules = items.mapNotNull { item -> parseSinkRuleItem(item) }
            Result.Ok(rules)
        } catch (e: Exception) {
            log.error("Failed to list SinkRules from DynamoDB", e)
            Result.Err(DomainError.StorageError("Failed to list SinkRules: ${e.message}"))
        }
    }

    override suspend fun findById(id: String): Result<SinkRule?> {
        return try {
            val items = queryById(id)
            val activeRules = items
                .filter { it["status"]?.s() == "ACTIVE" }
                .mapNotNull { parseSinkRuleItem(it) }
            Result.Ok(activeRules.maxByOrNull { it.version })
        } catch (e: Exception) {
            log.error("Failed to find SinkRule by id: $id", e)
            Result.Err(DomainError.StorageError("Failed to find SinkRule: ${e.message}"))
        }
    }

    /**
     * GSI kind-status-index로 kind, status 조회
     */
    private suspend fun queryByKindStatus(kind: String, status: String): List<Map<String, AttributeValue>> =
        suspendCoroutine { cont ->
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("kind-status-index")
                .keyConditionExpression("kind = :kind AND #status = :status")
                .expressionAttributeNames(mapOf("#status" to "status"))
                .expressionAttributeValues(
                    mapOf(
                        ":kind" to AttributeValue.builder().s(kind).build(),
                        ":status" to AttributeValue.builder().s(status).build(),
                    )
                )
                .build()

            dynamoClient.query(request).whenComplete { response, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    cont.resume(response.items())
                }
            }
        }

    /**
     * PK(id)로 조회 (동일 id의 모든 버전)
     */
    private suspend fun queryById(id: String): List<Map<String, AttributeValue>> =
        suspendCoroutine { cont ->
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("id = :id")
                .expressionAttributeValues(
                    mapOf(":id" to AttributeValue.builder().s(id).build())
                )
                .build()

            dynamoClient.query(request).whenComplete { response, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    cont.resume(response.items())
                }
            }
        }

    private fun parseSinkRuleItem(item: Map<String, AttributeValue>): SinkRule? {
        val id = item["id"]?.s() ?: return null
        val version = item["version"]?.s() ?: return null
        val kindStr = item["kind"]?.s()
        if (ContractKind.fromWireValue(kindStr ?: "") != ContractKind.SINK_RULE) return null

        val status = try {
            SinkRuleStatus.valueOf(item["status"]?.s()?.uppercase() ?: "INACTIVE")
        } catch (e: IllegalArgumentException) {
            SinkRuleStatus.INACTIVE
        }

        val dataJson = item["data"]?.s() ?: return null
        val data = try {
            json.parseToJsonElement(dataJson).jsonObject
        } catch (e: Exception) {
            log.warn("Failed to parse SinkRule data for $id: ${e.message}")
            return null
        }

        // input 파싱
        val inputObj = data["input"]?.jsonObject ?: return null
        val inputType = try {
            InputType.valueOf(inputObj["type"]?.jsonPrimitive?.content?.uppercase() ?: "SLICE")
        } catch (e: IllegalArgumentException) {
            InputType.SLICE
        }
        val sliceTypes = inputObj["sliceTypes"]?.jsonArray?.mapNotNull { el ->
            try {
                SliceType.valueOf(el.jsonPrimitive.content.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: listOf(SliceType.CORE)
        val entityTypes = inputObj["entityTypes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        // target 파싱
        val targetObj = data["target"]?.jsonObject ?: return null
        val targetType = try {
            SinkTargetType.valueOf(targetObj["type"]?.jsonPrimitive?.content?.uppercase() ?: return null)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val endpoint = resolveEnvVar(targetObj["endpoint"]?.jsonPrimitive?.content ?: "") ?: ""
        val indexPattern = targetObj["indexPattern"]?.jsonPrimitive?.contentOrNull
        val datasetArn = targetObj["datasetArn"]?.jsonPrimitive?.contentOrNull

        val authObj = targetObj["auth"]?.jsonObject
        val auth = if (authObj != null) {
            AuthSpec(
                type = try {
                    AuthType.valueOf(authObj["type"]?.jsonPrimitive?.content?.uppercase() ?: "NONE")
                } catch (e: IllegalArgumentException) {
                    AuthType.NONE
                },
                username = resolveEnvVar(authObj["username"]?.jsonPrimitive?.contentOrNull),
                password = resolveEnvVar(authObj["password"]?.jsonPrimitive?.contentOrNull)
            )
        } else null

        // docId 파싱
        val docIdObj = data["docId"]?.jsonObject
        val docId = DocIdSpec(
            pattern = docIdObj?.get("pattern")?.jsonPrimitive?.content ?: "{tenantId}__{entityKey}"
        )

        // commit 파싱
        val commitObj = data["commit"]?.jsonObject
        val commit = CommitSpec(
            batchSize = commitObj?.get("batchSize")?.jsonPrimitive?.intOrNull ?: 1000,
            timeoutMs = commitObj?.get("timeoutMs")?.jsonPrimitive?.longOrNull ?: 30000
        )

        return SinkRule(
            id = id,
            version = version,
            status = status,
            input = SinkRuleInput(type = inputType, sliceTypes = sliceTypes, entityTypes = entityTypes),
            target = SinkRuleTarget(
                type = targetType,
                endpoint = endpoint,
                indexPattern = indexPattern,
                datasetArn = datasetArn,
                auth = auth
            ),
            docId = docId,
            commit = commit
        )
    }

    /**
     * ${ENV_VAR:-default} 패턴 해석
     */
    private fun resolveEnvVar(value: String?): String? {
        if (value == null) return null
        val regex = Regex("""\$\{(\w+):-([^}]*)}""")
        return regex.replace(value) { match ->
            val envName = match.groupValues[1]
            val defaultVal = match.groupValues[2]
            System.getenv(envName) ?: defaultVal
        }
    }
}
