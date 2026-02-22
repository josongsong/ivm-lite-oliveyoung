package com.oliveyoung.ivmlite.sdk.execution

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionCommand
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.DeployJob
import com.oliveyoung.ivmlite.sdk.model.DeployResult
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.IngestResult
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deploy Executor (SOTA - DynamoDB Streams + Contract is Law)
 *
 * IngestionOrchestrator 기반:
 * - RawData → Slicing → View → SinkEvent (단일 트랜잭션)
 * - DynamoDB Streams → Lambda → S3/OpenSearch/Personalize
 *
 * EntityContractResolver로 EntityType별 RuleSet/ViewDef 동적 해석:
 * - PRODUCT → ruleset.core.v1, view.product.core.v1
 * - BRAND   → ruleset.brand.v1, view.brand.detail.v1
 * - CATEGORY → ruleset.category.v1, (ViewDef 미정의 시 에러)
 */
class DeployExecutor(
    private val orchestrator: IngestionOrchestrator,
    private val contractResolver: EntityContractResolver
) {
    /**
     * 동기 Deploy 실행 (올인원)
     *
     * RawData → Slicing → View → SinkEvent (DynamoDB)
     * DynamoDB Streams → Lambda → S3/OpenSearch/Personalize
     */
    suspend fun <T : EntityInput> executeSync(input: T): DeployResult {
        // 1. EntityInput → IngestionCommand 변환
        val command = when (val result = convertToIngestionCommand(input)) {
            is Either.Left -> return DeployResult.failure("unknown", "0", result.value.message ?: "Conversion failed")
            is Either.Right -> result.value
        }

        // 2. IngestionOrchestrator 실행 (RawData → Slicing → View → SinkEvent)
        val ingestionResult = orchestrator.ingest(command)

        return when (ingestionResult) {
            is Result.Err -> {
                DeployResult.failure(
                    command.entityKey.value,
                    command.version.toString(),
                    ingestionResult.error.toString()
                )
            }
            is Result.Ok<*> -> {
                DeployResult.success(
                    command.entityKey.value,
                    command.version.toString()
                )
            }
        }
    }

    /**
     * Deploy 실행 (Either 반환 - 함수형 에러 핸들링)
     *
     * SOTA: Ingestion(RawData→Slice→View→SinkEvent)은 동기 처리.
     * Sink 전송은 DynamoDB Streams → Lambda가 비동기 처리.
     *
     * executeSync()와 동일한 파이프라인이지만 Either<DomainError, DeployJob>을 반환하여
     * 함수형 에러 핸들링을 지원합니다.
     */
    suspend fun <T : EntityInput> executeAsync(input: T): Either<DomainError, DeployJob> {
        val result = executeSync(input)

        return if (result.success) {
            DeployJob(
                jobId = "deploy-${result.version}",
                entityKey = result.entityKey,
                version = result.version,
                state = DeployState.DONE
            ).right()
        } else {
            DomainError.StorageError(result.error ?: "Deploy failed").left()
        }
    }

    /**
     * EntityInput을 IngestionCommand로 변환
     *
     * EntityContractResolver를 통해 entityType별 RuleSet/ViewDef를 동적 해석.
     */
    private fun <T : EntityInput> convertToIngestionCommand(input: T): Either<DomainError, IngestionCommand> {
        val tenantId = TenantId(input.tenantId)
        val version = VersionGenerator.generate()

        // Contract is Law: EntityType에서 RuleSet/ViewDef 동적 해석
        val ruleSetRef = when (val r = contractResolver.resolveRuleSetRef(input.entityType)) {
            is Either.Left -> return r
            is Either.Right -> r.value
        }
        val viewDefId = when (val r = contractResolver.resolveViewDefId(input.entityType)) {
            is Either.Left -> return r
            is Either.Right -> r.value
        }
        val viewDefVersion = when (val r = contractResolver.resolveViewDefVersion(input.entityType)) {
            is Either.Left -> return r
            is Either.Right -> r.value
        }

        // EntityType별 처리
        val (entityKey, data) = when (input) {
            is ProductInput -> {
                val key = EntityKey("${input.entityType}:${input.sku}")
                val jsonData = buildJsonObject {
                    put("sku", input.sku)
                    put("name", input.name)
                    put("price", input.price)
                    put("currency", input.currency)
                    input.category?.let { put("category", it) }
                    input.brand?.let { put("brand", it) }
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }
                Pair(key, jsonData)
            }
            is BrandInput -> {
                val key = EntityKey("${input.entityType}:${input.brandId}")
                val jsonData = buildJsonObject {
                    put("brandId", input.brandId)
                    put("name", input.name)
                    input.logoUrl?.let { put("logoUrl", it) }
                    input.description?.let { put("description", it) }
                    input.country?.let { put("country", it) }
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }
                Pair(key, jsonData)
            }
            is CategoryInput -> {
                val key = EntityKey("${input.entityType}:${input.categoryId}")
                val jsonData = buildJsonObject {
                    put("categoryId", input.categoryId)
                    put("name", input.name)
                    input.parentId?.let { put("parentId", it) }
                    put("depth", input.depth)
                    put("displayOrder", input.displayOrder)
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }
                Pair(key, jsonData)
            }
            is GenericEntityInput -> {
                convertGenericEntityInput(input)
                    ?: return DomainError.NotSupportedError(
                        "GenericEntityInput requires entityKey, sku, brandId, or categoryId in data"
                    ).left()
            }
            else -> return DomainError.NotSupportedError("Unsupported EntityInput type: ${input::class}").left()
        }

        return IngestionCommand(
            tenantId = tenantId,
            entityKey = entityKey,
            data = data,
            ruleSetRef = ruleSetRef,
            viewDefId = viewDefId,
            viewDefVersion = viewDefVersion,
            version = version,
            jobId = null
        ).right()
    }

    private fun convertGenericEntityInput(input: GenericEntityInput): Pair<EntityKey, JsonObject>? {
        val entityKeyValue = input.data["entityKey"]?.toString()
            ?: input.data["sku"]?.toString()
            ?: input.data["brandId"]?.toString()
            ?: input.data["categoryId"]?.toString()
            ?: return null
        val key = EntityKey("${input.entityType}:$entityKeyValue")
        val jsonData = buildJsonObject {
            input.data.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v.toDouble())
                    is Boolean -> put(k, v)
                    null -> { /* skip nulls */ }
                    else -> put(k, v.toString())
                }
            }
        }
        return Pair(key, jsonData)
    }

    /**
     * Contract 기반 DeployPlan 생성 (explain용)
     *
     * EntityContractResolver에서 실제 RuleSet/ViewDef/Slice 정보를 해석.
     */
    fun explain(entityType: String, entityKey: String): com.oliveyoung.ivmlite.sdk.domain.DeployPlan {
        val sliceTypes = contractResolver.resolveSliceTypes(entityType)
        val viewDefIds = contractResolver.resolveViewDefIds(entityType)
        val ruleSetId = when (val r = contractResolver.resolveRuleSetRef(entityType)) {
            is Either.Right -> r.value.id
            is Either.Left -> "unknown"
        }

        return com.oliveyoung.ivmlite.sdk.domain.DeployPlan(
            entityKey = entityKey,
            entityType = entityType,
            slices = sliceTypes,
            views = viewDefIds,
            rules = listOf(ruleSetId)
        )
    }

    /**
     * Ingest만 실행 (올인원 처리)
     */
    internal suspend fun <T : EntityInput> ingestOnly(input: T): IngestResult {
        val command = when (val result = convertToIngestionCommand(input)) {
            is Either.Left -> return IngestResult(
                entityKey = "unknown",
                version = 0L,
                success = false,
                error = result.value.message ?: "Conversion failed"
            )
            is Either.Right -> result.value
        }

        val ingestionResult = orchestrator.ingest(command)

        return when (ingestionResult) {
            is Result.Err -> IngestResult(
                entityKey = command.entityKey.value,
                version = command.version,
                success = false,
                error = ingestionResult.error.toString()
            )
            is Result.Ok<*> -> IngestResult(
                entityKey = command.entityKey.value,
                version = command.version,
                success = true
            )
        }
    }
}
