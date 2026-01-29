package com.oliveyoung.ivmlite.pkg.contracts.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.adapters.withSpanSuspend
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractIntegrityError
import com.oliveyoung.ivmlite.shared.ports.ContractCachePort
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import kotlinx.serialization.json.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * DynamoDB 기반 Contract Registry Adapter (v2 운영용)
 *
 * RFC-IMPL Phase B-5: DynamoDB에서 계약 스키마를 로드
 * RFC-IMPL-010 Phase C-1: 캐싱 지원 추가
 * RFC-IMPL-009: OpenTelemetry tracing 지원
 *
 * DynamoDB 테이블 스키마:
 * - TableName: contract_registry
 * - PK: id (String) - 계약 ID (예: "changeset.v1", "inverted-index.v1")
 * - SK: version (String) - SemVer 문자열 (예: "1.0.0")
 * - Attributes:
 *   - kind: String (CHANGESET, JOIN_SPEC, INVERTED_INDEX)
 *   - status: String (DRAFT, ACTIVE, DEPRECATED, ARCHIVED)
 *   - data: String (JSON 직렬화된 계약 데이터)
 *   - createdAt: String (ISO 8601)
 *   - updatedAt: String (ISO 8601)
 *
 * @param dynamoClient DynamoDB 비동기 클라이언트
 * @param tableName DynamoDB 테이블명
 * @param cache Contract 캐시 (null이면 캐싱 비활성화)
 * @param tracer OpenTelemetry Tracer (RFC-IMPL-009)
 */
class DynamoDBContractRegistryAdapter(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String = "contract_registry",
    private val cache: ContractCachePort? = null,
    private val tracer: Tracer = io.opentelemetry.api.OpenTelemetry.noop().getTracer("contracts"),
) : ContractRegistryPort, HealthCheckable {

    override val healthName: String = "contracts"

    override suspend fun healthCheck(): Boolean = suspendCoroutine { cont ->
        val request = DescribeTableRequest.builder().tableName(tableName).build()
        dynamoClient.describeTable(request).whenComplete { _, error ->
            cont.resume(error == null)
        }
    }

    private val log = LoggerFactory.getLogger(DynamoDBContractRegistryAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadChangeSetContract(ref: ContractRef): ContractRegistryPort.Result<ChangeSetContract> {
        val cacheKey = ContractCachePort.key("CHANGESET", ref.id, ref.version.toString())

        // 캐시 hit → 즉시 반환
        cache?.get(cacheKey, ChangeSetContract::class)?.let { cached ->
            log.debug("Cache hit for ChangeSetContract: {}", cacheKey)
            return ContractRegistryPort.Result.Ok(cached)
        }

        // 캐시 miss → DynamoDB 조회
        val item = getItem(ref) ?: return notFound(ref)
        val result = parseChangeSet(item, ref)

        // 성공 시에만 캐시 저장 (negative caching 금지)
        if (result is ContractRegistryPort.Result.Ok) {
            cache?.put(cacheKey, result.value)
            log.debug("Cache put for ChangeSetContract: {}", cacheKey)
        }

        return result
    }

    override suspend fun loadJoinSpecContract(ref: ContractRef): ContractRegistryPort.Result<JoinSpecContract> {
        val cacheKey = ContractCachePort.key("JOIN_SPEC", ref.id, ref.version.toString())

        // 캐시 hit → 즉시 반환
        cache?.get(cacheKey, JoinSpecContract::class)?.let { cached ->
            log.debug("Cache hit for JoinSpecContract: {}", cacheKey)
            return ContractRegistryPort.Result.Ok(cached)
        }

        // 캐시 miss → DynamoDB 조회
        val item = getItem(ref) ?: return notFound(ref)
        val result = parseJoinSpec(item, ref)

        // 성공 시에만 캐시 저장 (negative caching 금지)
        if (result is ContractRegistryPort.Result.Ok) {
            cache?.put(cacheKey, result.value)
            log.debug("Cache put for JoinSpecContract: {}", cacheKey)
        }

        return result
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     * RuleSet.indexes의 IndexSpec.references로 통합되었습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    override suspend fun loadInvertedIndexContract(ref: ContractRef): ContractRegistryPort.Result<InvertedIndexContract> {
        log.warn("loadInvertedIndexContract is deprecated. Use IndexSpec.references in RuleSet instead. ref={}", ref)
        val cacheKey = ContractCachePort.key("INVERTED_INDEX", ref.id, ref.version.toString())

        // 캐시 hit → 즉시 반환
        cache?.get(cacheKey, InvertedIndexContract::class)?.let { cached ->
            log.debug("Cache hit for InvertedIndexContract: {}", cacheKey)
            return ContractRegistryPort.Result.Ok(cached)
        }

        // 캐시 miss → DynamoDB 조회
        val item = getItem(ref) ?: return notFound(ref)
        val result = parseInvertedIndex(item, ref)

        // 성공 시에만 캐시 저장 (negative caching 금지)
        if (result is ContractRegistryPort.Result.Ok) {
            cache?.put(cacheKey, result.value)
            log.debug("Cache put for InvertedIndexContract: {}", cacheKey)
        }

        return result
    }

    override suspend fun loadRuleSetContract(ref: ContractRef): ContractRegistryPort.Result<RuleSetContract> {
        return tracer.withSpanSuspend(
            "DynamoDB.loadRuleSetContract",
            mapOf(
                "contract_id" to ref.id,
                "contract_version" to ref.version.toString(),
            ),
        ) { span ->
            val cacheKey = ContractCachePort.key("RULE_SET", ref.id, ref.version.toString())

            cache?.get(cacheKey, RuleSetContract::class)?.let { cached ->
                log.debug("Cache hit for RuleSetContract: {}", cacheKey)
                span.setAttribute("cache.hit", true)
                return@withSpanSuspend ContractRegistryPort.Result.Ok(cached)
            }

            val item = getItem(ref) ?: return@withSpanSuspend notFound(ref)
            span.setAttribute("cache.hit", false)
            span.setAttribute("dynamodb.calls", 1)
            val result = parseRuleSet(item, ref)

            if (result is ContractRegistryPort.Result.Ok) {
                cache?.put(cacheKey, result.value)
                log.debug("Cache put for RuleSetContract: {}", cacheKey)
            }

            result
        }
    }

    override suspend fun loadViewDefinitionContract(ref: ContractRef): ContractRegistryPort.Result<ViewDefinitionContract> {
        return tracer.withSpanSuspend(
            "DynamoDB.loadViewDefinitionContract",
            mapOf(
                "contract_id" to ref.id,
                "contract_version" to ref.version.toString(),
            ),
        ) { span ->
            val cacheKey = ContractCachePort.key("VIEW_DEFINITION", ref.id, ref.version.toString())

            cache?.get(cacheKey, ViewDefinitionContract::class)?.let { cached ->
                log.debug("Cache hit for ViewDefinitionContract: {}", cacheKey)
                span.setAttribute("cache.hit", true)
                return@withSpanSuspend ContractRegistryPort.Result.Ok(cached)
            }

            val item = getItem(ref) ?: return@withSpanSuspend notFound(ref)
            span.setAttribute("cache.hit", false)
            span.setAttribute("dynamodb.calls", 1)
            val result = parseViewDefinition(item, ref)

            if (result is ContractRegistryPort.Result.Ok) {
                cache?.put(cacheKey, result.value)
                log.debug("Cache put for ViewDefinitionContract: {}", cacheKey)
            }

            result
        }
    }

    private suspend fun getItem(ref: ContractRef): Map<String, AttributeValue>? {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "id" to AttributeValue.builder().s(ref.id).build(),
                    "version" to AttributeValue.builder().s(ref.version.toString()).build(),
                ),
            )
            .build()

        return suspendCoroutine { cont ->
            dynamoClient.getItem(request).whenComplete { response, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    val item = response.item()
                    if (item.isNullOrEmpty()) {
                        cont.resume(null)
                    } else {
                        cont.resume(item)
                    }
                }
            }
        }
    }

    private fun parseMeta(item: Map<String, AttributeValue>, ref: ContractRef): ContractRegistryPort.Result<ContractMeta> {
        val kind = item["kind"]?.s() ?: return err("missing kind for ${ref.id}")
        val statusStr = item["status"]?.s() ?: return err("missing status for ${ref.id}")
        val status = try {
            ContractStatus.valueOf(statusStr)
        } catch (e: IllegalArgumentException) {
            return err("invalid status '$statusStr' for ${ref.id}")
        }

        return ContractRegistryPort.Result.Ok(
            ContractMeta(
                kind = kind,
                id = ref.id,
                version = ref.version,
                status = status,
            ),
        )
    }

    private fun parseChangeSet(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<ChangeSetContract> {
        val checksumResult = verifyChecksum(item, ref)
        if (checksumResult is ContractRegistryPort.Result.Err) return checksumResult

        val metaResult = parseMeta(item, ref)
        if (metaResult is ContractRegistryPort.Result.Err) return metaResult

        val meta = (metaResult as ContractRegistryPort.Result.Ok).value
        val dataJson = item["data"]?.s() ?: return err("missing data for ${ref.id}")

        return try {
            val data = json.parseToJsonElement(dataJson).jsonObject
            val identity = data["identity"]?.jsonObject
            val entityKeyFormat = identity?.get("entityKeyFormat")?.jsonPrimitive?.content
                ?: "{ENTITY_TYPE}#{tenantId}#{entityId}"

            val payload = data["payload"]?.jsonObject
            val ext = payload?.get("externalizationPolicy")?.jsonObject
            val threshold = ext?.get("thresholdBytes")?.jsonPrimitive?.intOrNull ?: 100000

            val fanout = data["fanout"]?.jsonObject
            val enabled = fanout?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: false

            ContractRegistryPort.Result.Ok(
                ChangeSetContract(
                    meta = meta,
                    entityKeyFormat = entityKeyFormat,
                    externalizeThresholdBytes = threshold,
                    fanoutEnabled = enabled,
                ),
            )
        } catch (e: Exception) {
            err("failed to parse changeset data for ${ref.id}: ${e.message}")
        }
    }

    private fun parseJoinSpec(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<JoinSpecContract> {
        val checksumResult = verifyChecksum(item, ref)
        if (checksumResult is ContractRegistryPort.Result.Err) return checksumResult

        val metaResult = parseMeta(item, ref)
        if (metaResult is ContractRegistryPort.Result.Err) return metaResult

        val meta = (metaResult as ContractRegistryPort.Result.Ok).value
        val dataJson = item["data"]?.s() ?: return err("missing data for ${ref.id}")

        return try {
            val data = json.parseToJsonElement(dataJson).jsonObject
            val constraints = data["constraints"]?.jsonObject
            val maxJoinDepth = constraints?.get("maxJoinDepth")?.jsonPrimitive?.intOrNull ?: 1

            val fanout = data["fanout"]?.jsonObject
            val inverted = fanout?.get("invertedIndex")?.jsonObject
            val maxFanout = inverted?.get("maxFanout")?.jsonPrimitive?.intOrNull ?: 10000

            val contractRef = inverted?.get("contractRef")?.jsonObject
            val refId = contractRef?.get("id")?.jsonPrimitive?.content
                ?: return err("missing invertedIndex.contractRef.id for ${ref.id}")
            val refVer = contractRef["version"]?.jsonPrimitive?.content?.let(SemVer::parse)
                ?: return err("missing invertedIndex.contractRef.version for ${ref.id}")

            ContractRegistryPort.Result.Ok(
                JoinSpecContract(
                    meta = meta,
                    maxJoinDepth = maxJoinDepth,
                    maxFanout = maxFanout,
                    invertedIndexRef = ContractRef(refId, refVer),
                ),
            )
        } catch (e: Exception) {
            err("failed to parse join-spec data for ${ref.id}: ${e.message}")
        }
    }

    private fun parseInvertedIndex(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<InvertedIndexContract> {
        val checksumResult = verifyChecksum(item, ref)
        if (checksumResult is ContractRegistryPort.Result.Err) return checksumResult

        val metaResult = parseMeta(item, ref)
        if (metaResult is ContractRegistryPort.Result.Err) return metaResult

        val meta = (metaResult as ContractRegistryPort.Result.Ok).value
        val dataJson = item["data"]?.s() ?: return err("missing data for ${ref.id}")

        return try {
            val data = json.parseToJsonElement(dataJson).jsonObject
            val keySpec = data["keySpec"]?.jsonObject
                ?: return err("missing keySpec for ${ref.id}")

            val pkPattern = keySpec["pkPattern"]?.jsonPrimitive?.content
                ?: return err("missing keySpec.pkPattern for ${ref.id}")
            val skPattern = keySpec["skPattern"]?.jsonPrimitive?.content
                ?: return err("missing keySpec.skPattern for ${ref.id}")
            val padWidth = keySpec["padWidth"]?.jsonPrimitive?.intOrNull ?: 12
            val separator = keySpec["separator"]?.jsonPrimitive?.content ?: "#"

            val guards = data["guards"]?.jsonObject
            val maxTargetsPerRef = guards?.get("maxTargetsPerRef")?.jsonPrimitive?.intOrNull ?: 500000

            ContractRegistryPort.Result.Ok(
                InvertedIndexContract(
                    meta = meta,
                    pkPattern = pkPattern,
                    skPattern = skPattern,
                    padWidth = padWidth,
                    separator = separator,
                    maxTargetsPerRef = maxTargetsPerRef,
                ),
            )
        } catch (e: Exception) {
            err("failed to parse inverted-index data for ${ref.id}: ${e.message}")
        }
    }

    private fun parseRuleSet(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<RuleSetContract> {
        val checksumResult = verifyChecksum(item, ref)
        if (checksumResult is ContractRegistryPort.Result.Err) return checksumResult

        val metaResult = parseMeta(item, ref)
        if (metaResult is ContractRegistryPort.Result.Err) return metaResult

        val meta = (metaResult as ContractRegistryPort.Result.Ok).value

        // ACTIVE 상태만 허용 (fail-closed)
        if (meta.status != ContractStatus.ACTIVE) {
            return err("RuleSet contract must be ACTIVE, got ${meta.status} for ${ref.id}")
        }

        val dataJson = item["data"]?.s() ?: return err("missing data for ${ref.id}")

        return try {
            val data = json.parseToJsonElement(dataJson).jsonObject
            val entityType = data["entityType"]?.jsonPrimitive?.content
                ?: return err("missing entityType for ${ref.id}")

            // slices 필수 검증
            if (!data.containsKey("slices")) {
                return err("missing slices for ${ref.id}")
            }

            val impactMapJson = data["impactMap"]?.jsonObject ?: emptyMap<String, JsonElement>()
            val impactMap = mutableMapOf<SliceType, List<String>>()
            for ((key, value) in impactMapJson) {
                val sliceType = try {
                    SliceType.valueOf(key)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$key' in impactMap for ${ref.id}")
                }
                val jsonArray = value as? JsonArray
                    ?: return err("impactMap value for '$key' is not an array in ${ref.id}")
                val fields = jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                if (fields.isEmpty() && jsonArray.isNotEmpty()) {
                    // 배열이 있지만 모든 항목이 null인 경우 (파싱 실패)
                    return err("all paths are null in impactMap for '$key' in ${ref.id}")
                }
                impactMap[sliceType] = fields
            }

            val joinsJson = data["joins"]?.jsonArray ?: emptyList()
            val joins = mutableListOf<JoinSpec>()
            for (joinElement in joinsJson) {
                val joinObj = joinElement.jsonObject
                val sourceSliceStr = joinObj["sourceSlice"]?.jsonPrimitive?.content
                    ?: return err("missing sourceSlice in join for ${ref.id}")
                val sourceSlice = try {
                    SliceType.valueOf(sourceSliceStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$sourceSliceStr' in join for ${ref.id}")
                }
                val targetEntity = joinObj["targetEntity"]?.jsonPrimitive?.content
                    ?: return err("missing targetEntity in join for ${ref.id}")
                val joinPath = joinObj["joinPath"]?.jsonPrimitive?.content
                    ?: return err("missing joinPath in join for ${ref.id}")
                val cardinalityStr = joinObj["cardinality"]?.jsonPrimitive?.content ?: "ONE_TO_ONE"  // 기본값 허용 (RFC-003)
                val cardinality = try {
                    JoinCardinality.valueOf(cardinalityStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid cardinality '$cardinalityStr' in join for ${ref.id}")
                }
                joins.add(JoinSpec(sourceSlice, targetEntity, joinPath, cardinality))
            }

            val slicesJson = data["slices"]?.jsonArray ?: emptyList()
            val slices = mutableListOf<SliceDefinition>()
            for (sliceElement in slicesJson) {
                val sliceObj = sliceElement.jsonObject
                val typeStr = sliceObj["type"]?.jsonPrimitive?.content
                    ?: return err("missing type in slice for ${ref.id}")
                val type = try {
                    SliceType.valueOf(typeStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$typeStr' in slice for ${ref.id}")
                }

                // RFC-IMPL-016: sliceKind 파싱 (옵셔널, 기본값: STANDARD)
                val sliceKindStr = sliceObj["sliceKind"]?.jsonPrimitive?.contentOrNull
                val sliceKind = if (sliceKindStr != null) {
                    try {
                        SliceKind.valueOf(sliceKindStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return err("invalid SliceKind '$sliceKindStr' in slice for ${ref.id}")
                    }
                } else {
                    SliceKind.STANDARD
                }

                val buildRulesObj = sliceObj["buildRules"]?.jsonObject
                    ?: return err("missing buildRules in slice for ${ref.id}")
                val buildRulesType = buildRulesObj["type"]?.jsonPrimitive?.content?.lowercase()
                val buildRules = when (buildRulesType) {
                    "passthrough" -> {
                        val fields = buildRulesObj["fields"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: listOf("*")
                        SliceBuildRules.PassThrough(fields)
                    }
                    "mapfields" -> {
                        // RFC-IMPL-016: 배열 형태와 객체 형태 모두 지원
                        val mappingsElement = buildRulesObj["mappings"]
                        val mappings: Map<String, String> = when {
                            mappingsElement is JsonObject -> mappingsElement.mapNotNull { (k, v) ->
                                v.jsonPrimitive.contentOrNull?.let { k to it }
                            }.toMap()
                            mappingsElement is JsonArray -> mappingsElement.mapNotNull { item ->
                                val obj = item.jsonObject
                                val from = obj["from"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                val to = obj["to"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                from to to
                            }.toMap()
                            else -> emptyMap()
                        }
                        SliceBuildRules.MapFields(mappings)
                    }
                    else -> return err("unknown buildRules type '$buildRulesType' in slice for ${ref.id}")
                }

                // RFC-IMPL-010 GAP-B: slices[].joins 파싱 (JoinExecutor가 이해하는 형태)
                val sliceJoinsJson = sliceObj["joins"]?.jsonArray ?: emptyList()
                val sliceJoins = mutableListOf<com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec>()
                for (joinElement in sliceJoinsJson) {
                    val joinObj = joinElement.jsonObject
                    val joinName = joinObj["name"]?.jsonPrimitive?.content
                        ?: return err("missing name in slice join for ${ref.id}")
                    val joinTypeStr = joinObj["type"]?.jsonPrimitive?.content?.uppercase() ?: "LOOKUP"
                    val joinType = try {
                        com.oliveyoung.ivmlite.pkg.slices.domain.JoinType.valueOf(joinTypeStr)
                    } catch (e: IllegalArgumentException) {
                        return err("invalid JoinType '$joinTypeStr' in slice join for ${ref.id}")
                    }
                    val sourceFieldPath = joinObj["sourceFieldPath"]?.jsonPrimitive?.content
                        ?: return err("missing sourceFieldPath in slice join for ${ref.id}")
                    val targetEntityType = joinObj["targetEntityType"]?.jsonPrimitive?.content
                        ?: return err("missing targetEntityType in slice join for ${ref.id}")
                    val targetKeyPattern = joinObj["targetKeyPattern"]?.jsonPrimitive?.content
                        ?: return err("missing targetKeyPattern in slice join for ${ref.id}")
                    val required = joinObj["required"]?.jsonPrimitive?.booleanOrNull ?: true  // default: fail-closed

                    sliceJoins.add(
                        com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec(
                            name = joinName,
                            type = joinType,
                            sourceFieldPath = sourceFieldPath,
                            targetEntityType = targetEntityType,
                            targetKeyPattern = targetKeyPattern,
                            required = required,
                        )
                    )
                }

                slices.add(SliceDefinition(type, buildRules, sliceJoins, sliceKind))
            }

            // RFC-IMPL-010 Phase D-9: indexes 파싱 (통합 버전 - references/maxFanout 추가)
            val indexesJson = data["indexes"]?.jsonArray ?: emptyList()
            val indexes = mutableListOf<IndexSpec>()
            for (indexElement in indexesJson) {
                val indexObj = indexElement.jsonObject
                val type = indexObj["type"]?.jsonPrimitive?.content
                    ?: return err("missing type in index for ${ref.id}")
                val selector = indexObj["selector"]?.jsonPrimitive?.content
                    ?: return err("missing selector in index for ${ref.id}")

                // selector validation: $ prefix 필수
                if (!selector.startsWith("$")) {
                    return err("index selector must start with '$': $selector for ${ref.id}")
                }

                // 통합 버전: references 및 maxFanout 파싱 (옵션)
                val references = indexObj["references"]?.jsonPrimitive?.contentOrNull
                val maxFanout = indexObj["maxFanout"]?.jsonPrimitive?.intOrNull ?: 10000

                indexes.add(IndexSpec(
                    type = type,
                    selector = selector,
                    references = references,
                    maxFanout = maxFanout,
                ))
            }

            ContractRegistryPort.Result.Ok(
                RuleSetContract(
                    meta = meta,
                    entityType = entityType,
                    impactMap = impactMap,
                    joins = joins,
                    slices = slices,
                    indexes = indexes,
                ),
            )
        } catch (e: Exception) {
            err("failed to parse ruleset data for ${ref.id}: ${e.message}")
        }
    }

    private fun parseViewDefinition(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<ViewDefinitionContract> {
        val checksumResult = verifyChecksum(item, ref)
        if (checksumResult is ContractRegistryPort.Result.Err) return checksumResult

        val metaResult = parseMeta(item, ref)
        if (metaResult is ContractRegistryPort.Result.Err) return metaResult

        val meta = (metaResult as ContractRegistryPort.Result.Ok).value

        // ACTIVE 상태만 허용 (fail-closed)
        if (meta.status != ContractStatus.ACTIVE) {
            return err("ViewDefinition contract must be ACTIVE, got ${meta.status} for ${ref.id}")
        }

        val dataJson = item["data"]?.s() ?: return err("missing data for ${ref.id}")

        return try {
            val data = json.parseToJsonElement(dataJson).jsonObject

            // requiredSlices 파싱 (필수 필드)
            if (!data.containsKey("requiredSlices")) {
                return err("missing requiredSlices for ${ref.id}")
            }
            val requiredSlicesJson = data["requiredSlices"]?.jsonArray
                ?: return err("requiredSlices is null for ${ref.id}")
            val requiredSlices = mutableListOf<SliceType>()
            for (sliceElement in requiredSlicesJson) {
                val sliceStr = sliceElement.jsonPrimitive.content
                val sliceType = try {
                    SliceType.valueOf(sliceStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$sliceStr' in requiredSlices for ${ref.id}")
                }
                requiredSlices.add(sliceType)
            }

            // optionalSlices 파싱
            val optionalSlicesJson = data["optionalSlices"]?.jsonArray ?: emptyList()
            val optionalSlices = mutableListOf<SliceType>()
            for (sliceElement in optionalSlicesJson) {
                val sliceStr = sliceElement.jsonPrimitive.content
                val sliceType = try {
                    SliceType.valueOf(sliceStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$sliceStr' in optionalSlices for ${ref.id}")
                }
                optionalSlices.add(sliceType)
            }

            // missingPolicy 파싱
            val missingPolicyStr = data["missingPolicy"]?.jsonPrimitive?.content ?: "FAIL_CLOSED"
            val missingPolicy = try {
                MissingPolicy.valueOf(missingPolicyStr)
            } catch (e: IllegalArgumentException) {
                return err("invalid MissingPolicy '$missingPolicyStr' for ${ref.id}")
            }

            // partialPolicy 파싱 (필수 필드)
            val partialPolicyJson = data["partialPolicy"]?.jsonObject
                ?: return err("missing partialPolicy for ${ref.id}")
            val allowed = partialPolicyJson["allowed"]?.jsonPrimitive?.booleanOrNull
                ?: return err("missing partialPolicy.allowed for ${ref.id}")
            val optionalOnly = partialPolicyJson["optionalOnly"]?.jsonPrimitive?.booleanOrNull
                ?: return err("missing partialPolicy.optionalOnly for ${ref.id}")
            val responseMetaJson = partialPolicyJson["responseMeta"]?.jsonObject
                ?: return err("missing partialPolicy.responseMeta for ${ref.id}")
            val includeMissingSlices = responseMetaJson["includeMissingSlices"]?.jsonPrimitive?.booleanOrNull
                ?: return err("missing responseMeta.includeMissingSlices for ${ref.id}")
            val includeUsedContracts = responseMetaJson["includeUsedContracts"]?.jsonPrimitive?.booleanOrNull
                ?: return err("missing responseMeta.includeUsedContracts for ${ref.id}")

            val partialPolicy = PartialPolicy(
                allowed = allowed,
                optionalOnly = optionalOnly,
                responseMeta = ResponseMeta(
                    includeMissingSlices = includeMissingSlices,
                    includeUsedContracts = includeUsedContracts,
                ),
            )

            // fallbackPolicy 파싱
            val fallbackPolicyStr = data["fallbackPolicy"]?.jsonPrimitive?.content ?: "NONE"
            val fallbackPolicy = try {
                FallbackPolicy.valueOf(fallbackPolicyStr)
            } catch (e: IllegalArgumentException) {
                return err("invalid FallbackPolicy '$fallbackPolicyStr' for ${ref.id}")
            }

            // ruleSetRef 파싱 (필수 필드)
            val ruleSetRefJson = data["ruleSetRef"]?.jsonObject
                ?: return err("missing ruleSetRef for ${ref.id}")
            val ruleSetRefId = ruleSetRefJson["id"]?.jsonPrimitive?.content
                ?: return err("missing ruleSetRef.id for ${ref.id}")
            val ruleSetRefVersion = ruleSetRefJson["version"]?.jsonPrimitive?.content?.let(SemVer::parse)
                ?: return err("missing ruleSetRef.version for ${ref.id}")
            val ruleSetRef = ContractRef(ruleSetRefId, ruleSetRefVersion)

            ContractRegistryPort.Result.Ok(
                ViewDefinitionContract(
                    meta = meta,
                    requiredSlices = requiredSlices,
                    optionalSlices = optionalSlices,
                    missingPolicy = missingPolicy,
                    partialPolicy = partialPolicy,
                    fallbackPolicy = fallbackPolicy,
                    ruleSetRef = ruleSetRef,
                ),
            )
        } catch (e: Exception) {
            err("failed to parse view-definition data for ${ref.id}: ${e.message}")
        }
    }

    /**
     * checksum 무결성 검증 (RFC-IMPL-010 Phase C-2)
     *
     * 검증 매트릭스:
     * | checksum | data   | 결과                           |
     * |----------|--------|-------------------------------|
     * | null     | null   | Ok (이후 parse에서 에러)        |
     * | null     | 존재   | Ok + 경고 로그 (migration 호환) |
     * | 존재     | null   | Err (데이터 손상)              |
     * | 빈문자열  | 존재   | Err (잘못된 checksum 형식)     |
     * | 존재     | 존재   | 검증 수행                       |
     *
     * @return Ok(Unit) if valid, Err if mismatch or corruption
     */
    private fun verifyChecksum(
        item: Map<String, AttributeValue>,
        ref: ContractRef,
    ): ContractRegistryPort.Result<Unit> {
        val storedChecksum = item["checksum"]?.s()
        val dataJson = item["data"]?.s()

        // Case 1: checksum, data 둘 다 없음 → Ok (이후 parse에서 "missing data" 에러)
        if (storedChecksum == null && dataJson == null) {
            return ContractRegistryPort.Result.Ok(Unit)
        }

        // Case 2: checksum 없음, data 있음 → Ok + 경고 (migration 호환)
        if (storedChecksum == null && dataJson != null) {
            log.warn("Contract '${ref.id}@${ref.version}' missing checksum field (migration compatibility)")
            return ContractRegistryPort.Result.Ok(Unit)
        }

        // Case 3: checksum 있음, data 없음 → Err (데이터 손상)
        if (storedChecksum != null && dataJson == null) {
            return ContractRegistryPort.Result.Err(
                ContractIntegrityError(
                    contractId = "${ref.id}@${ref.version}",
                    expected = storedChecksum,
                    actual = "<data_missing>",
                ),
            )
        }

        // Case 4: checksum이 빈 문자열 → Err (잘못된 형식)
        if (storedChecksum!!.isBlank()) {
            return ContractRegistryPort.Result.Err(
                ContractIntegrityError(
                    contractId = "${ref.id}@${ref.version}",
                    expected = "<non-empty checksum>",
                    actual = "<blank>",
                ),
            )
        }

        // Case 5: 정상 검증
        val actualHash = Hashing.sha256Hex(dataJson!!)
        val actualTagged = Hashing.sha256Tagged(dataJson)

        val isValid = storedChecksum == actualHash || storedChecksum == actualTagged

        return if (isValid) {
            ContractRegistryPort.Result.Ok(Unit)
        } else {
            ContractRegistryPort.Result.Err(
                ContractIntegrityError(
                    contractId = "${ref.id}@${ref.version}",
                    expected = storedChecksum,
                    actual = actualTagged,
                ),
            )
        }
    }

    private fun notFound(ref: ContractRef): ContractRegistryPort.Result.Err =
        ContractRegistryPort.Result.Err(
            DomainError.NotFoundError("Contract", "${ref.id}@${ref.version}"),
        )

    private fun err(msg: String): ContractRegistryPort.Result.Err =
        ContractRegistryPort.Result.Err(ContractError(msg))

    // ===== List Operations (GSI 사용) =====

    /**
     * GSI로 Contract 목록 조회
     */
    override suspend fun listContractRefs(
        kind: String,
        status: ContractStatus?
    ): ContractRegistryPort.Result<List<ContractRef>> {
        return try {
            val refs = queryByKindStatus(kind, status)
            ContractRegistryPort.Result.Ok(refs)
        } catch (e: Exception) {
            log.error("Failed to list contracts: kind=$kind, status=$status", e)
            err("Failed to list contracts: ${e.message}")
        }
    }

    /**
     * 모든 ViewDefinition Contract 조회
     */
    override suspend fun listViewDefinitions(
        status: ContractStatus?
    ): ContractRegistryPort.Result<List<ViewDefinitionContract>> {
        return try {
            val refs = queryByKindStatus("VIEW_DEFINITION", status)
            val contracts = refs.map { ref ->
                when (val result = loadViewDefinitionContract(ref)) {
                    is ContractRegistryPort.Result.Ok -> result.value
                    is ContractRegistryPort.Result.Err -> {
                        throw DomainError.StorageError("Failed to load ViewDefinition: ${ref.id}@${ref.version}, error: ${result.error}")
                    }
                }
            }
            ContractRegistryPort.Result.Ok(contracts)
        } catch (e: Exception) {
            log.error("Failed to list ViewDefinitions", e)
            err("Failed to list ViewDefinitions: ${e.message}")
        }
    }

    private suspend fun queryByKindStatus(
        kind: String,
        status: ContractStatus?
    ): List<ContractRef> = suspendCoroutine { cont ->
        val builder = software.amazon.awssdk.services.dynamodb.model.QueryRequest.builder()
            .tableName(tableName)
            .indexName("kind-status-index")
            .keyConditionExpression(
                if (status != null) "kind = :kind AND #status = :status"
                else "kind = :kind"
            )
            .expressionAttributeValues(
                buildMap {
                    put(":kind", AttributeValue.builder().s(kind).build())
                    if (status != null) {
                        put(":status", AttributeValue.builder().s(status.name).build())
                    }
                }
            )
        
        if (status != null) {
            builder.expressionAttributeNames(mapOf("#status" to "status"))
        }

        dynamoClient.query(builder.build()).whenComplete { response, error ->
            if (error != null) {
                cont.resumeWithException(error)
            } else {
                val refs = response.items().map { item ->
                    val id = item["id"]?.s()
                        ?: throw DomainError.StorageError("Missing required field 'id' in contract registry item")
                    val versionStr = item["version"]?.s()
                        ?: throw DomainError.StorageError("Missing required field 'version' in contract registry item for id: $id")
                    val version = try {
                        SemVer.parse(versionStr)
                    } catch (e: Exception) {
                        throw DomainError.StorageError("Invalid version format in contract registry item for id: $id, version: $versionStr")
                    }
                    ContractRef(id, version)
                }
                cont.resume(refs)
            }
        }
    }

    // ===== Save Operations =====

    override suspend fun saveViewDefinitionContract(
        contract: ViewDefinitionContract
    ): ContractRegistryPort.Result<Unit> {
        return saveContract(
            kind = "VIEW_DEFINITION",
            id = contract.meta.id,
            version = contract.meta.version.toString(),
            status = contract.meta.status,
            data = buildViewDefinitionData(contract)
        )
    }

    override suspend fun saveRuleSetContract(
        contract: RuleSetContract
    ): ContractRegistryPort.Result<Unit> {
        return saveContract(
            kind = "RULE_SET",
            id = contract.meta.id,
            version = contract.meta.version.toString(),
            status = contract.meta.status,
            data = buildRuleSetData(contract)
        )
    }

    suspend fun saveChangeSetContract(
        contract: ChangeSetContract
    ): ContractRegistryPort.Result<Unit> {
        return saveContract(
            kind = "CHANGESET",
            id = contract.meta.id,
            version = contract.meta.version.toString(),
            status = contract.meta.status,
            data = buildChangeSetData(contract)
        )
    }

    suspend fun saveJoinSpecContract(
        contract: JoinSpecContract
    ): ContractRegistryPort.Result<Unit> {
        return saveContract(
            kind = "JOIN_SPEC",
            id = contract.meta.id,
            version = contract.meta.version.toString(),
            status = contract.meta.status,
            data = buildJoinSpecData(contract)
        )
    }

    private suspend fun saveContract(
        kind: String,
        id: String,
        version: String,
        status: ContractStatus,
        data: String
    ): ContractRegistryPort.Result<Unit> = suspendCoroutine { cont ->
        val checksum = Hashing.sha256Tagged(data)
        val now = java.time.Instant.now().toString()

        val request = software.amazon.awssdk.services.dynamodb.model.PutItemRequest.builder()
            .tableName(tableName)
            .item(
                mapOf(
                    "id" to AttributeValue.builder().s(id).build(),
                    "version" to AttributeValue.builder().s(version).build(),
                    "kind" to AttributeValue.builder().s(kind).build(),
                    "status" to AttributeValue.builder().s(status.name).build(),
                    "data" to AttributeValue.builder().s(data).build(),
                    "checksum" to AttributeValue.builder().s(checksum).build(),
                    "createdAt" to AttributeValue.builder().s(now).build(),
                    "updatedAt" to AttributeValue.builder().s(now).build(),
                )
            )
            .build()

        dynamoClient.putItem(request).whenComplete { _, error ->
            if (error != null) {
                log.error("Failed to save contract: $id@$version", error)
                cont.resume(err("Failed to save contract: ${error.message}"))
            } else {
                log.info("Contract saved: $kind $id@$version")
                cont.resume(ContractRegistryPort.Result.Ok(Unit))
            }
        }
    }

    private fun buildViewDefinitionData(contract: ViewDefinitionContract): String {
        return buildJsonObject {
            put("requiredSlices", buildJsonArray {
                contract.requiredSlices.forEach { add(it.name) }
            })
            put("optionalSlices", buildJsonArray {
                contract.optionalSlices.forEach { add(it.name) }
            })
            put("missingPolicy", contract.missingPolicy.name)
            put("partialPolicy", buildJsonObject {
                put("allowed", contract.partialPolicy.allowed)
                put("optionalOnly", contract.partialPolicy.optionalOnly)
                put("responseMeta", buildJsonObject {
                    put("includeMissingSlices", contract.partialPolicy.responseMeta.includeMissingSlices)
                    put("includeUsedContracts", contract.partialPolicy.responseMeta.includeUsedContracts)
                })
            })
            put("fallbackPolicy", contract.fallbackPolicy.name)
            put("ruleSetRef", buildJsonObject {
                put("id", contract.ruleSetRef.id)
                put("version", contract.ruleSetRef.version.toString())
            })
        }.toString()
    }

    private fun buildRuleSetData(contract: RuleSetContract): String {
        return buildJsonObject {
            put("entityType", contract.entityType)
            put("impactMap", buildJsonObject {
                contract.impactMap.forEach { (slice, fields) ->
                    put(slice.name, buildJsonArray { fields.forEach { add(it) } })
                }
            })
            put("joins", buildJsonArray {
                contract.joins.forEach { join ->
                    add(buildJsonObject {
                        put("sourceSlice", join.sourceSlice.name)
                        put("targetEntity", join.targetEntity)
                        put("joinPath", join.joinPath)
                        put("cardinality", join.cardinality.name)
                    })
                }
            })
            put("slices", buildJsonArray {
                contract.slices.forEach { slice ->
                    add(buildJsonObject {
                        put("type", slice.type.name)
                        put("buildRules", buildJsonObject {
                            when (val rules = slice.buildRules) {
                                is SliceBuildRules.PassThrough -> {
                                    put("type", "passthrough")
                                    put("fields", buildJsonArray { rules.fields.forEach { add(it) } })
                                }
                                is SliceBuildRules.MapFields -> {
                                    put("type", "mapfields")
                                    put("mappings", buildJsonObject {
                                        rules.mappings.forEach { (k, v) -> put(k, v) }
                                    })
                                }
                            }
                        })
                        if (slice.joins.isNotEmpty()) {
                            put("joins", buildJsonArray {
                                slice.joins.forEach { join ->
                                    add(buildJsonObject {
                                        put("name", join.name)
                                        put("type", join.type.name)
                                        put("sourceFieldPath", join.sourceFieldPath)
                                        put("targetEntityType", join.targetEntityType)
                                        put("targetKeyPattern", join.targetKeyPattern)
                                        put("required", join.required)
                                    })
                                }
                            })
                        }
                    })
                }
            })
            if (contract.indexes.isNotEmpty()) {
                put("indexes", buildJsonArray {
                    contract.indexes.forEach { idx ->
                        add(buildJsonObject {
                            put("type", idx.type)
                            put("selector", idx.selector)
                        })
                    }
                })
            }
        }.toString()
    }

    private fun buildChangeSetData(contract: ChangeSetContract): String {
        return buildJsonObject {
            put("identity", buildJsonObject {
                put("entityKeyFormat", contract.entityKeyFormat)
            })
            put("payload", buildJsonObject {
                put("externalizationPolicy", buildJsonObject {
                    put("thresholdBytes", contract.externalizeThresholdBytes)
                    put("prefer", "S3")
                })
            })
            put("fanout", buildJsonObject {
                put("enabled", contract.fanoutEnabled)
            })
        }.toString()
    }

    private fun buildJoinSpecData(contract: JoinSpecContract): String {
        return buildJsonObject {
            put("constraints", buildJsonObject {
                put("allowedJoinTypes", buildJsonArray { add("LOOKUP") })
                put("maxJoinDepth", contract.maxJoinDepth)
                put("forbidJoinChain", true)
                put("forbidCycles", true)
                put("forbidNMJoin", true)
                put("sourceCardinality", "SINGLE")
                put("maxJoinTargetsPerSource", 1)
                put("requireDeterministicResolution", true)
            })
            put("fanout", buildJsonObject {
                put("invertedIndex", buildJsonObject {
                    put("required", false)
                    put("maxFanout", contract.maxFanout)
                })
                put("joinDepReason", "JOIN_DEP")
            })
        }.toString()
    }
}
