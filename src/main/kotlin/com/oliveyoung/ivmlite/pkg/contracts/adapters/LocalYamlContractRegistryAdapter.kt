package com.oliveyoung.ivmlite.pkg.contracts.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * v1 모드: contracts는 repo 내부 리소스(YAML)에서만 로드한다.
 * (id, version)의 immutability/hash enforcement는 "registry service"가 수행하고,
 * 런타임은 status/required fields만 fail-closed로 검증한다.
 */
class LocalYamlContractRegistryAdapter(
    private val resourceRoot: String = "/contracts/v1"
) : ContractRegistryPort, HealthCheckable {
    override val healthName: String = "contracts"
    override suspend fun healthCheck(): Boolean = true

    private val yaml = Yaml()

    override suspend fun loadChangeSetContract(ref: ContractRef): ContractRegistryPort.Result<ChangeSetContract> {
        val map = loadYaml("changeset.v1.yaml") ?: return err("changeset.v1.yaml not found")
        return parseChangeSet(map)
    }

    override suspend fun loadJoinSpecContract(ref: ContractRef): ContractRegistryPort.Result<JoinSpecContract> {
        val map = loadYaml("join-spec.v1.yaml") ?: return err("join-spec.v1.yaml not found")
        return parseJoinSpec(map)
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     * RuleSet.indexes의 IndexSpec.references로 통합되었습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    override suspend fun loadInvertedIndexContract(ref: ContractRef): ContractRegistryPort.Result<InvertedIndexContract> {
        val map = loadYaml("inverted-index.v1.yaml") ?: return err("inverted-index.v1.yaml not found (deprecated)")
        return parseInvertedIndex(map)
    }

    override suspend fun loadRuleSetContract(ref: ContractRef): ContractRegistryPort.Result<RuleSetContract> {
        val map = loadYaml("ruleset.v1.yaml") ?: return err("ruleset.v1.yaml not found")
        return parseRuleSet(map)
    }

    override suspend fun loadViewDefinitionContract(ref: ContractRef): ContractRegistryPort.Result<ViewDefinitionContract> {
        val map = loadYaml("view-definition.v1.yaml") ?: return err("view-definition.v1.yaml not found")
        return parseViewDefinition(map)
    }

    private fun loadYaml(filename: String): Map<String, Any?>? {
        val path = resourceRoot.trimEnd('/') + "/" + filename
        val stream: InputStream = javaClass.getResourceAsStream(path) ?: return null
        @Suppress("UNCHECKED_CAST")
        return yaml.load(stream) as? Map<String, Any?>
    }

    private fun parseMeta(map: Map<String, Any?>): ContractRegistryPort.Result<ContractMeta> {
        val kind = map["kind"]?.toString() ?: return err("missing kind")
        val id = map["id"]?.toString() ?: return err("missing id")
        val version = map["version"]?.toString()?.let(SemVer::parse) ?: return err("missing version")
        val status = map["status"]?.toString()?.let { ContractStatus.valueOf(it) } ?: return err("missing status")
        return ContractRegistryPort.Result.Ok(ContractMeta(kind, id, version, status))
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    private fun parseInvertedIndex(map: Map<String, Any?>): ContractRegistryPort.Result<InvertedIndexContract> {
        val meta = (parseMeta(map) as? ContractRegistryPort.Result.Ok)?.value ?: return parseMeta(map) as ContractRegistryPort.Result.Err

        val keySpec = map["keySpec"] as? Map<*, *> ?: return err("missing keySpec")
        val pkPattern = keySpec["pkPattern"]?.toString() ?: return err("missing keySpec.pkPattern")
        val skPattern = keySpec["skPattern"]?.toString() ?: return err("missing keySpec.skPattern")
        val padWidth = keySpec["padWidth"]?.toString()?.toIntOrNull() ?: 12
        val separator = keySpec["separator"]?.toString() ?: "#"

        val guards = map["guards"] as? Map<*, *>
        val maxTargetsPerRef = guards?.get("maxTargetsPerRef")?.toString()?.toIntOrNull() ?: 500000

        return ContractRegistryPort.Result.Ok(
            InvertedIndexContract(
                meta = meta,
                pkPattern = pkPattern,
                skPattern = skPattern,
                padWidth = padWidth,
                separator = separator,
                maxTargetsPerRef = maxTargetsPerRef,
            )
        )
    }

    private fun parseJoinSpec(map: Map<String, Any?>): ContractRegistryPort.Result<JoinSpecContract> {
        val meta = (parseMeta(map) as? ContractRegistryPort.Result.Ok)?.value ?: return parseMeta(map) as ContractRegistryPort.Result.Err

        val constraints = map["constraints"] as? Map<*, *> ?: return err("missing constraints")
        val maxJoinDepth = constraints["maxJoinDepth"]?.toString()?.toIntOrNull() ?: 1

        val fanout = map["fanout"] as? Map<*, *> ?: return err("missing fanout")
        val inverted = fanout["invertedIndex"] as? Map<*, *> ?: return err("missing fanout.invertedIndex")
        val maxFanout = inverted["maxFanout"]?.toString()?.toIntOrNull() ?: 10000
        val contractRef = inverted["contractRef"] as? Map<*, *> ?: return err("missing invertedIndex.contractRef")
        val refId = contractRef["id"]?.toString() ?: return err("missing invertedIndex.contractRef.id")
        val refVer = contractRef["version"]?.toString()?.let(SemVer::parse) ?: return err("missing invertedIndex.contractRef.version")

        return ContractRegistryPort.Result.Ok(
            JoinSpecContract(
                meta = meta,
                maxJoinDepth = maxJoinDepth,
                maxFanout = maxFanout,
                invertedIndexRef = ContractRef(refId, refVer),
            )
        )
    }

    private fun parseChangeSet(map: Map<String, Any?>): ContractRegistryPort.Result<ChangeSetContract> {
        val meta = (parseMeta(map) as? ContractRegistryPort.Result.Ok)?.value ?: return parseMeta(map) as ContractRegistryPort.Result.Err

        val identity = map["identity"] as? Map<*, *> ?: return err("missing identity")
        val entityKeyFormat = identity["entityKeyFormat"]?.toString() ?: "{ENTITY_TYPE}#{tenantId}#{entityId}"

        val payload = map["payload"] as? Map<*, *> ?: return err("missing payload")
        val ext = payload["externalizationPolicy"] as? Map<*, *>
        val threshold = ext?.get("thresholdBytes")?.toString()?.toIntOrNull() ?: 100000

        val fanout = map["fanout"] as? Map<*, *>
        val enabled = fanout?.get("enabled")?.toString()?.toBooleanStrictOrNull() ?: false

        return ContractRegistryPort.Result.Ok(
            ChangeSetContract(
                meta = meta,
                entityKeyFormat = entityKeyFormat,
                externalizeThresholdBytes = threshold,
                fanoutEnabled = enabled,
            )
        )
    }

    private fun parseRuleSet(map: Map<String, Any?>): ContractRegistryPort.Result<RuleSetContract> {
        val meta = (parseMeta(map) as? ContractRegistryPort.Result.Ok)?.value ?: return parseMeta(map) as ContractRegistryPort.Result.Err

        // ACTIVE 상태만 허용 (fail-closed)
        if (meta.status != ContractStatus.ACTIVE) {
            return err("RuleSet contract must be ACTIVE, got ${meta.status}")
        }

        val entityType = map["entityType"]?.toString() ?: return err("missing entityType")

        @Suppress("UNCHECKED_CAST")
        val impactMapRaw = map["impactMap"] as? Map<String, List<String>> ?: emptyMap()
        val impactMap = try {
            impactMapRaw.mapKeys { (k, _) -> SliceType.valueOf(k.uppercase()) }
        } catch (e: IllegalArgumentException) {
            return err("invalid SliceType in impactMap: ${e.message}")
        }

        @Suppress("UNCHECKED_CAST")
        val joinsRaw = map["joins"] as? List<Map<String, Any?>> ?: emptyList()
        val joins = try {
            joinsRaw.map { j ->
                val sourceSliceStr = j["sourceSlice"]?.toString()?.uppercase()
                    ?: return err("missing sourceSlice in join")
                val sourceSlice = try {
                    SliceType.valueOf(sourceSliceStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$sourceSliceStr' in join")
                }
                val targetEntity = j["targetEntity"]?.toString()
                    ?: return err("missing targetEntity in join")
                val joinPath = j["joinPath"]?.toString()
                    ?: return err("missing joinPath in join")
                val cardinalityStr = j["cardinality"]?.toString()?.uppercase()?.replace("-", "_") ?: "ONE_TO_ONE"
                val cardinality = try {
                    JoinCardinality.valueOf(cardinalityStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid cardinality '$cardinalityStr' in join")
                }
                JoinSpec(
                    sourceSlice = sourceSlice,
                    targetEntity = targetEntity,
                    joinPath = joinPath,
                    cardinality = cardinality,
                )
            }
        } catch (e: IllegalArgumentException) {
            return err("invalid JoinSpec: ${e.message}")
        }

        @Suppress("UNCHECKED_CAST")
        val slicesRaw = map["slices"] as? List<Map<String, Any?>> ?: emptyList()
        val slices = try {
            slicesRaw.map { s ->
                val sliceTypeStr = s["type"]?.toString()?.uppercase()
                    ?: return err("missing type in slice")
                val sliceType = try {
                    SliceType.valueOf(sliceTypeStr)
                } catch (e: IllegalArgumentException) {
                    return err("invalid SliceType '$sliceTypeStr' in slice")
                }
                @Suppress("UNCHECKED_CAST")
                val buildRulesRaw = s["buildRules"] as? Map<String, Any?>
                val buildRulesType = buildRulesRaw?.get("type")?.toString()?.lowercase()
                val buildRules = when (buildRulesType) {
                    "passthrough" -> {
                        @Suppress("UNCHECKED_CAST")
                        val fields = buildRulesRaw["fields"] as? List<String> ?: listOf("*")
                        SliceBuildRules.PassThrough(fields)
                    }
                    "mapfields" -> {
                        @Suppress("UNCHECKED_CAST")
                        val mappings = buildRulesRaw["mappings"] as? Map<String, String> ?: emptyMap()
                        SliceBuildRules.MapFields(mappings)
                    }
                    else -> return err("unknown buildRules type: $buildRulesType")
                }

                // RFC-IMPL-010 GAP-B: slices[].joins 파싱 (JoinExecutor가 이해하는 형태)
                @Suppress("UNCHECKED_CAST")
                val sliceJoinsRaw = s["joins"] as? List<Map<String, Any?>> ?: emptyList()
                val sliceJoins = sliceJoinsRaw.map { j ->
                    com.oliveyoung.ivmlite.pkg.slices.domain.JoinSpec(
                        name = j["name"]?.toString() ?: return err("missing name in slice join"),
                        type = try {
                            com.oliveyoung.ivmlite.pkg.slices.domain.JoinType.valueOf(
                                j["type"]?.toString()?.uppercase() ?: "LOOKUP"
                            )
                        } catch (e: IllegalArgumentException) {
                            return err("invalid JoinType in slice join: ${j["type"]}")
                        },
                        sourceFieldPath = j["sourceFieldPath"]?.toString() ?: return err("missing sourceFieldPath in slice join"),
                        targetEntityType = j["targetEntityType"]?.toString() ?: return err("missing targetEntityType in slice join"),
                        targetKeyPattern = j["targetKeyPattern"]?.toString() ?: return err("missing targetKeyPattern in slice join"),
                        required = j["required"]?.toString()?.toBooleanStrictOrNull() ?: true,  // default: fail-closed
                    )
                }

                SliceDefinition(sliceType, buildRules, sliceJoins)
            }
        } catch (e: IllegalArgumentException) {
            return err("invalid SliceDefinition: ${e.message}")
        }

        // RFC-IMPL-010 Phase D-9: indexes 파싱 (통합 버전 - references/maxFanout 추가)
        @Suppress("UNCHECKED_CAST")
        val indexesRaw = map["indexes"] as? List<Map<String, Any?>> ?: emptyList()
        val indexes = try {
            indexesRaw.map { idx ->
                val type = idx["type"]?.toString() ?: return err("missing type in index")
                val selector = idx["selector"]?.toString() ?: return err("missing selector in index")

                // selector validation: $ prefix 필수
                if (!selector.startsWith("$")) {
                    return err("index selector must start with '$': $selector")
                }

                // 통합 버전: references 및 maxFanout 파싱 (옵션)
                val references = idx["references"]?.toString()
                val maxFanout = idx["maxFanout"]?.toString()?.toIntOrNull() ?: 10000

                IndexSpec(
                    type = type,
                    selector = selector,
                    references = references,
                    maxFanout = maxFanout,
                )
            }
        } catch (e: IllegalArgumentException) {
            return err("invalid IndexSpec: ${e.message}")
        }

        return ContractRegistryPort.Result.Ok(
            RuleSetContract(
                meta = meta,
                entityType = entityType,
                impactMap = impactMap,
                joins = joins,
                slices = slices,
                indexes = indexes,
            )
        )
    }

    private fun parseViewDefinition(map: Map<String, Any?>): ContractRegistryPort.Result<ViewDefinitionContract> {
        val meta = (parseMeta(map) as? ContractRegistryPort.Result.Ok)?.value ?: return parseMeta(map) as ContractRegistryPort.Result.Err

        // ACTIVE 상태만 허용 (fail-closed)
        if (meta.status != ContractStatus.ACTIVE) {
            return err("ViewDefinition contract must be ACTIVE, got ${meta.status}")
        }

        // requiredSlices 파싱
        @Suppress("UNCHECKED_CAST")
        val requiredSlicesRaw = map["requiredSlices"] as? List<String> ?: return err("missing requiredSlices")
        val requiredSlices = try {
            requiredSlicesRaw.map { SliceType.valueOf(it.uppercase()) }
        } catch (e: IllegalArgumentException) {
            return err("invalid SliceType in requiredSlices: ${e.message}")
        }

        // optionalSlices 파싱
        @Suppress("UNCHECKED_CAST")
        val optionalSlicesRaw = map["optionalSlices"] as? List<String> ?: emptyList()
        val optionalSlices = try {
            optionalSlicesRaw.map { SliceType.valueOf(it.uppercase()) }
        } catch (e: IllegalArgumentException) {
            return err("invalid SliceType in optionalSlices: ${e.message}")
        }

        // missingPolicy 파싱
        val missingPolicy = try {
            MissingPolicy.valueOf(map["missingPolicy"]?.toString()?.uppercase() ?: "FAIL_CLOSED")
        } catch (e: IllegalArgumentException) {
            return err("invalid MissingPolicy: ${e.message}")
        }

        // partialPolicy 파싱
        @Suppress("UNCHECKED_CAST")
        val partialPolicyRaw = map["partialPolicy"] as? Map<String, Any?> ?: return err("missing partialPolicy")
        val allowed = partialPolicyRaw["allowed"]?.toString()?.toBooleanStrictOrNull() ?: return err("missing partialPolicy.allowed")
        val optionalOnly = partialPolicyRaw["optionalOnly"]?.toString()?.toBooleanStrictOrNull() ?: return err("missing partialPolicy.optionalOnly")

        @Suppress("UNCHECKED_CAST")
        val responseMetaRaw = partialPolicyRaw["responseMeta"] as? Map<String, Any?> ?: return err("missing partialPolicy.responseMeta")
        val includeMissingSlices = responseMetaRaw["includeMissingSlices"]?.toString()?.toBooleanStrictOrNull() ?: return err("missing responseMeta.includeMissingSlices")
        val includeUsedContracts = responseMetaRaw["includeUsedContracts"]?.toString()?.toBooleanStrictOrNull() ?: return err("missing responseMeta.includeUsedContracts")

        val partialPolicy = PartialPolicy(
            allowed = allowed,
            optionalOnly = optionalOnly,
            responseMeta = ResponseMeta(
                includeMissingSlices = includeMissingSlices,
                includeUsedContracts = includeUsedContracts,
            ),
        )

        // fallbackPolicy 파싱
        val fallbackPolicy = try {
            FallbackPolicy.valueOf(map["fallbackPolicy"]?.toString()?.uppercase() ?: "NONE")
        } catch (e: IllegalArgumentException) {
            return err("invalid FallbackPolicy: ${e.message}")
        }

        // ruleSetRef 파싱
        @Suppress("UNCHECKED_CAST")
        val ruleSetRefRaw = map["ruleSetRef"] as? Map<String, Any?> ?: return err("missing ruleSetRef")
        val ruleSetRefId = ruleSetRefRaw["id"]?.toString() ?: return err("missing ruleSetRef.id")
        val ruleSetRefVersion = ruleSetRefRaw["version"]?.toString()?.let(SemVer::parse) ?: return err("missing ruleSetRef.version")
        val ruleSetRef = ContractRef(ruleSetRefId, ruleSetRefVersion)

        return ContractRegistryPort.Result.Ok(
            ViewDefinitionContract(
                meta = meta,
                requiredSlices = requiredSlices,
                optionalSlices = optionalSlices,
                missingPolicy = missingPolicy,
                partialPolicy = partialPolicy,
                fallbackPolicy = fallbackPolicy,
                ruleSetRef = ruleSetRef,
            )
        )
    }

    private fun err(msg: String): ContractRegistryPort.Result.Err =
        ContractRegistryPort.Result.Err(ContractError(msg))
}
