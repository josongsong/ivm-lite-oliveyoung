package com.oliveyoung.ivmlite.pkg.contracts.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError.ContractError
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

/**
 * v1 모드: contracts는 repo 내부 리소스(YAML)에서만 로드한다.
 * (id, version)의 immutability/hash enforcement는 "registry service"가 수행하고,
 * 런타임은 status/required fields만 fail-closed로 검증한다.
 *
 * Hot Reload (product-schema-dx-proposal RFC 5.3): fileBaseDir 설정 시 파일 시스템에서 직접 로드.
 * CONTRACTS_FILE_PATH=src/main/resources/contracts/v1 환경변수로 개발 모드에서 재시작 없이 반영.
 */
class LocalYamlContractRegistryAdapter(
    private val resourceRoot: String = "/contracts/v1",
    private val fileBaseDir: File? = null,
) : ContractRegistryPort, HealthCheckable {
    override val healthName: String = "contracts"
    override suspend fun healthCheck(): Boolean = true

    private val yaml = Yaml()

    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        val map = loadYaml("changeset.v1.yaml") ?: return err("changeset.v1.yaml not found")
        return parseChangeSet(map)
    }

    override suspend fun loadJoinSpecContract(ref: ContractRef): Result<JoinSpecContract> {
        val map = loadYaml("join-spec.v1.yaml") ?: return err("join-spec.v1.yaml not found")
        return parseJoinSpec(map)
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     * RuleSet.indexes의 IndexSpec.references로 통합되었습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    override suspend fun loadInvertedIndexContract(ref: ContractRef): Result<InvertedIndexContract> {
        val map = loadYaml("inverted-index.v1.yaml") ?: return err("inverted-index.v1.yaml not found (deprecated)")
        return parseInvertedIndex(map)
    }

    override suspend fun loadRuleSetContract(ref: ContractRef): Result<RuleSetContract> {
        // ID → filename: ContractFileRegistry SSOT 우선, 없으면 일반 규칙 적용
        val filename = ContractFileRegistry.RULESET_ID_TO_FILE[ref.id] ?: run {
            val withoutVersion = ref.id.removeSuffix(".v1")
            val idPart = withoutVersion.replace(".", "-")
            "$idPart.v1.yaml"
        }
        val map = loadYaml(filename) ?: return err("ruleset contract not found: $filename")
        val parsed = parseRuleSet(map)
        // ID 검증 (fail-closed)
        if (parsed is Result.Ok && parsed.value.meta.id != ref.id) {
            return err("RuleSet ID mismatch: expected ${ref.id}, got ${parsed.value.meta.id}")
        }
        return parsed
    }

    override suspend fun loadViewDefinitionContract(ref: ContractRef): Result<ViewDefinitionContract> {
        // ID → filename: ContractFileRegistry SSOT 우선, 없으면 일반 규칙 적용
        val filename = ContractFileRegistry.VIEWDEF_ID_TO_FILE[ref.id] ?: run {
            val idPart = ref.id.replace(".v1", "").replace(".", "-")
            "$idPart.v1.yaml"
        }
        val map = loadYaml(filename) ?: loadYaml("view-definition.v1.yaml") ?: return err("view definition contract not found: $filename or view-definition.v1.yaml")
        val parsed = parseViewDefinition(map)
        // ID 검증 (fail-closed)
        if (parsed is Result.Ok && parsed.value.meta.id != ref.id) {
            return err("ViewDefinition ID mismatch: expected ${ref.id}, got ${parsed.value.meta.id}")
        }
        return parsed
    }

    override suspend fun listContractRefs(kind: ContractKind, status: ContractStatus?): Result<List<ContractRef>> {
        val allFiles = when (kind) {
            ContractKind.VIEW_DEFINITION -> ContractFileRegistry.VIEW_DEFINITION_FILES
            ContractKind.RULESET -> ContractFileRegistry.RULESET_FILES
            else -> return Result.Ok(emptyList())
        }

        val refs = allFiles.mapNotNull { filename ->
            loadYaml(filename)?.let { map ->
                val meta = (parseMeta(map) as? Result.Ok)?.value
                if (status == null || meta?.status == status) {
                    meta?.let { ContractRef(it.id, it.version) }
                } else null
            }
        }
        return Result.Ok(refs)
    }

    override suspend fun listViewDefinitions(status: ContractStatus?): Result<List<ViewDefinitionContract>> {
        val refsResult = listContractRefs(ContractKind.VIEW_DEFINITION, status)
        if (refsResult is Result.Err) {
            return Result.Err(refsResult.error)
        }

        val refs = (refsResult as Result.Ok).value
        val contracts = refs.mapNotNull { ref ->
            when (val result = loadViewDefinitionContract(ref)) {
                is Result.Ok -> result.value
                is Result.Err -> null
            }
        }
        return Result.Ok(contracts)
    }

    private fun loadYaml(filename: String): Map<String, Any?>? {
        if (fileBaseDir != null) {
            val file = File(fileBaseDir, filename)
            if (!file.exists()) return null
            @Suppress("UNCHECKED_CAST")
            return yaml.load(file.readText()) as? Map<String, Any?>
        }
        val path = resourceRoot.trimEnd('/') + "/" + filename
        val stream: InputStream = javaClass.getResourceAsStream(path) ?: return null
        @Suppress("UNCHECKED_CAST")
        return yaml.load(stream) as? Map<String, Any?>
    }

    private fun parseMeta(map: Map<String, Any?>): Result<ContractMeta> {
        val kindStr = map["kind"]?.toString() ?: return err("missing kind")
        val kind = ContractKind.fromWireValue(kindStr)
            ?: return err("unknown contract kind: $kindStr")
        val id = map["id"]?.toString() ?: return err("missing id")
        val version = map["version"]?.toString()?.let(SemVer::parse) ?: return err("missing version")
        val status = map["status"]?.toString()?.let { ContractStatus.valueOf(it) } ?: return err("missing status")
        return Result.Ok(ContractMeta(kind, id, version, status))
    }

    /**
     * @deprecated InvertedIndexContract는 더 이상 사용되지 않습니다.
     */
    @Deprecated("Use IndexSpec.references in RuleSet instead")
    @Suppress("DEPRECATION")
    private fun parseInvertedIndex(map: Map<String, Any?>): Result<InvertedIndexContract> {
        val meta = (parseMeta(map) as? Result.Ok)?.value ?: return parseMeta(map) as Result.Err

        val keySpec = map["keySpec"] as? Map<*, *> ?: return err("missing keySpec")
        val pkPattern = keySpec["pkPattern"]?.toString() ?: return err("missing keySpec.pkPattern")
        val skPattern = keySpec["skPattern"]?.toString() ?: return err("missing keySpec.skPattern")
        val padWidth = keySpec["padWidth"]?.toString()?.toIntOrNull() ?: 12
        val separator = keySpec["separator"]?.toString() ?: "#"

        val guards = map["guards"] as? Map<*, *>
        val maxTargetsPerRef = guards?.get("maxTargetsPerRef")?.toString()?.toIntOrNull() ?: 500_000

        return Result.Ok(
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

    private fun parseJoinSpec(map: Map<String, Any?>): Result<JoinSpecContract> {
        val meta = (parseMeta(map) as? Result.Ok)?.value ?: return parseMeta(map) as Result.Err

        val constraints = map["constraints"] as? Map<*, *> ?: return err("missing constraints")
        val maxJoinDepth = constraints["maxJoinDepth"]?.toString()?.toIntOrNull() ?: 1

        val fanout = map["fanout"] as? Map<*, *> ?: return err("missing fanout")
        val inverted = fanout["invertedIndex"] as? Map<*, *> ?: return err("missing fanout.invertedIndex")
        val maxFanout = inverted["maxFanout"]?.toString()?.toIntOrNull() ?: 10_000

        return Result.Ok(
            JoinSpecContract(
                meta = meta,
                maxJoinDepth = maxJoinDepth,
                maxFanout = maxFanout,
            )
        )
    }

    private fun parseChangeSet(map: Map<String, Any?>): Result<ChangeSetContract> {
        val meta = (parseMeta(map) as? Result.Ok)?.value ?: return parseMeta(map) as Result.Err

        val identity = map["identity"] as? Map<*, *> ?: return err("missing identity")
        val entityKeyFormat = identity["entityKeyFormat"]?.toString() ?: "{ENTITY_TYPE}#{tenantId}#{entityId}"

        val payload = map["payload"] as? Map<*, *> ?: return err("missing payload")
        val ext = payload["externalizationPolicy"] as? Map<*, *>
        val threshold = ext?.get("thresholdBytes")?.toString()?.toIntOrNull() ?: 100_000

        val fanout = map["fanout"] as? Map<*, *>
        val enabled = fanout?.get("enabled")?.toString()?.toBooleanStrictOrNull() ?: false

        return Result.Ok(
            ChangeSetContract(
                meta = meta,
                entityKeyFormat = entityKeyFormat,
                externalizeThresholdBytes = threshold,
                fanoutEnabled = enabled,
            )
        )
    }

    private fun parseRuleSet(map: Map<String, Any?>): Result<RuleSetContract> {
        val meta = (parseMeta(map) as? Result.Ok)?.value ?: return parseMeta(map) as Result.Err

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

                // RFC-IMPL-016: sliceKind 파싱 (옵셔널, 기본값: STANDARD)
                val sliceKindStr = s["sliceKind"]?.toString()?.uppercase()
                val sliceKind = if (sliceKindStr != null) {
                    try {
                        SliceKind.valueOf(sliceKindStr)
                    } catch (e: IllegalArgumentException) {
                        return err("invalid SliceKind '$sliceKindStr' in slice")
                    }
                } else {
                    SliceKind.STANDARD
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
                        val mappingsRaw = buildRulesRaw["mappings"]
                        val mappings: Map<String, String> = when (mappingsRaw) {
                            // 기존 방식: { from: to } 형태
                            is Map<*, *> -> mappingsRaw.mapNotNull { (k, v) ->
                                if (k != null && v != null) k.toString() to v.toString() else null
                            }.toMap()
                            // RFC-IMPL-016 신규 방식: [{ from: x, to: y }] 배열 형태
                            is List<*> -> mappingsRaw.mapNotNull { item ->
                                @Suppress("UNCHECKED_CAST")
                                val m = item as? Map<String, Any?> ?: return@mapNotNull null
                                val from = m["from"]?.toString() ?: return@mapNotNull null
                                val to = m["to"]?.toString() ?: return@mapNotNull null
                                from to to
                            }.toMap()
                            else -> emptyMap()
                        }
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
                        projection = parseProjection(j["projection"]),
                        targetSliceType = j["targetSliceType"]?.toString(),
                        missingPolicy = try {
                            j["missingPolicy"]?.toString()?.let { policy ->
                                com.oliveyoung.ivmlite.pkg.slices.domain.MissingPolicy.valueOf(policy)
                            } ?: com.oliveyoung.ivmlite.pkg.slices.domain.MissingPolicy.FAIL_CLOSED
                        } catch (e: IllegalArgumentException) {
                            return err("invalid MissingPolicy in slice join: ${j["missingPolicy"]}")
                        }
                    )
                }

                SliceDefinition(sliceType, buildRules, sliceJoins, sliceKind)
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

        return Result.Ok(
            RuleSetContract(
                meta = meta,
                entityType = entityType,
                impactMap = impactMap,
                slices = slices,
                indexes = indexes,
            )
        )
    }

    private fun parseViewDefinition(map: Map<String, Any?>): Result<ViewDefinitionContract> {
        val meta = (parseMeta(map) as? Result.Ok)?.value ?: return parseMeta(map) as Result.Err

        // ACTIVE 상태만 허용 (fail-closed)
        if (meta.status != ContractStatus.ACTIVE) {
            return err("ViewDefinition contract must be ACTIVE, got ${meta.status}")
        }

        // viewName 파싱 (선택)
        val viewName = map["viewName"]?.toString()

        // entityType 파싱 (선택)
        val entityType = map["entityType"]?.toString()

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

        return Result.Ok(
            ViewDefinitionContract(
                meta = meta,
                viewName = viewName,
                entityType = entityType,
                requiredSlices = requiredSlices,
                optionalSlices = optionalSlices,
                missingPolicy = missingPolicy,
                partialPolicy = partialPolicy,
                fallbackPolicy = fallbackPolicy,
                ruleSetRef = ruleSetRef,
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProjection(raw: Any?): com.oliveyoung.ivmlite.pkg.slices.domain.Projection? {
        val projMap = raw as? Map<String, Any?> ?: return null
        val mode = try {
            com.oliveyoung.ivmlite.pkg.slices.domain.ProjectionMode.valueOf(
                projMap["mode"]?.toString()?.uppercase() ?: "COPY_FIELDS"
            )
        } catch (_: IllegalArgumentException) {
            com.oliveyoung.ivmlite.pkg.slices.domain.ProjectionMode.COPY_FIELDS
        }
        val fields = (projMap["fields"] as? List<Map<String, Any?>>)?.mapNotNull { fm ->
            val from = fm["from"]?.toString() ?: fm["fromTargetPath"]?.toString() ?: return@mapNotNull null
            val to = fm["to"]?.toString() ?: fm["toOutputPath"]?.toString() ?: return@mapNotNull null
            com.oliveyoung.ivmlite.pkg.slices.domain.FieldMapping(fromTargetPath = from, toOutputPath = to)
        } ?: emptyList()
        return com.oliveyoung.ivmlite.pkg.slices.domain.Projection(mode = mode, fields = fields)
    }

    private fun err(msg: String): Result.Err =
        Result.Err(ContractError(msg))
}
