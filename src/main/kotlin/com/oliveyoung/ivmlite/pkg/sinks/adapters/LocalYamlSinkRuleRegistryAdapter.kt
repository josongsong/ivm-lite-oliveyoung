package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.pkg.sinks.domain.*
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

/**
 * Contract YAML 기반 SinkRule Registry (Contract is Law)
 *
 * resources/contracts/v1/sinkrule-*.yaml 파일에서 SinkRule 로드.
 * InMemorySinkRuleRegistry 대체 → YAML이 SSOT.
 */
class LocalYamlSinkRuleRegistryAdapter(
    private val resourceRoot: String = "/contracts/v1"
) : SinkRuleRegistryPort {

    private val logger = LoggerFactory.getLogger(LocalYamlSinkRuleRegistryAdapter::class.java)
    private val yaml = Yaml()
    private val rules: List<SinkRule> by lazy { loadAllSinkRules() }

    override suspend fun findByEntityAndSliceType(
        entityType: String,
        sliceType: SliceType
    ): Result<List<SinkRule>> {
        val matched = rules.filter { rule ->
            rule.status == SinkRuleStatus.ACTIVE &&
                rule.input.entityTypes.any { it.equals(entityType, ignoreCase = true) } &&
                rule.input.sliceTypes.contains(sliceType)
        }
        return Result.Ok(matched)
    }

    override suspend fun findByEntityType(entityType: String): Result<List<SinkRule>> {
        val matched = rules.filter { rule ->
            rule.status == SinkRuleStatus.ACTIVE &&
                rule.input.entityTypes.any { it.equals(entityType, ignoreCase = true) }
        }
        return Result.Ok(matched)
    }

    override suspend fun findAllActive(): Result<List<SinkRule>> {
        return Result.Ok(rules.filter { it.status == SinkRuleStatus.ACTIVE })
    }

    override suspend fun findById(id: String): Result<SinkRule?> {
        return Result.Ok(rules.find { it.id == id })
    }

    private fun loadAllSinkRules(): List<SinkRule> {
        val sinkRuleFiles = discoverSinkRuleFiles()
        logger.info("Discovered {} SinkRule files in {}", sinkRuleFiles.size, resourceRoot)

        return sinkRuleFiles.mapNotNull { filename ->
            try {
                val path = resourceRoot.trimEnd('/') + "/" + filename
                val stream = javaClass.getResourceAsStream(path) ?: run {
                    logger.warn("SinkRule file not found: {}", path)
                    return@mapNotNull null
                }
                @Suppress("UNCHECKED_CAST")
                val map = yaml.load(stream) as? Map<String, Any?> ?: return@mapNotNull null
                parseSinkRule(map)
            } catch (e: Exception) {
                logger.error("Failed to parse SinkRule: {}", filename, e)
                null
            }
        }
    }

    /**
     * classpath에서 sinkrule-*.yaml 파일 동적 발견
     *
     * file:// (개발환경) + jar:// (프로덕션) 모두 지원.
     */
    private fun discoverSinkRuleFiles(): List<String> {
        return try {
            val resourcePath = resourceRoot.trimStart('/')
            val url = javaClass.classLoader.getResource(resourcePath) ?: return emptyList()

            when (url.protocol) {
                "file" -> {
                    java.io.File(url.toURI())
                        .listFiles { f -> f.name.startsWith("sinkrule-") && (f.name.endsWith(".yaml") || f.name.endsWith(".yml")) }
                        ?.map { it.name }
                        ?.sorted()
                        ?: emptyList()
                }
                "jar" -> {
                    val jarPath = url.path.substringBefore("!")
                    val jarFile = java.util.jar.JarFile(java.net.URI(jarPath).path)
                    jarFile.use { jar ->
                        jar.entries().asSequence()
                            .filter { entry ->
                                val name = entry.name
                                name.startsWith("$resourcePath/") &&
                                    name.substringAfterLast("/").startsWith("sinkrule-") &&
                                    (name.endsWith(".yaml") || name.endsWith(".yml"))
                            }
                            .map { it.name.substringAfterLast("/") }
                            .sorted()
                            .toList()
                    }
                }
                else -> {
                    logger.warn("Unknown resource protocol: {}, falling back to empty", url.protocol)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to discover SinkRule files", e)
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSinkRule(map: Map<String, Any?>): SinkRule? {
        val kind = map["kind"]?.toString() ?: return null
        if (ContractKind.fromWireValue(kind) != ContractKind.SINK_RULE) return null

        val id = map["id"]?.toString() ?: return null
        val version = map["version"]?.toString() ?: return null
        val status = try {
            SinkRuleStatus.valueOf(map["status"]?.toString()?.uppercase() ?: "INACTIVE")
        } catch (e: IllegalArgumentException) {
            SinkRuleStatus.INACTIVE
        }

        // input 파싱
        val inputMap = map["input"] as? Map<String, Any?> ?: return null
        val inputType = try {
            InputType.valueOf(inputMap["type"]?.toString()?.uppercase() ?: "SLICE")
        } catch (e: IllegalArgumentException) {
            InputType.SLICE
        }
        val sliceTypes = (inputMap["sliceTypes"] as? List<*>)?.mapNotNull { s ->
            try { SliceType.valueOf(s.toString().uppercase()) } catch (e: IllegalArgumentException) { null }
        } ?: listOf(SliceType.CORE)
        val entityTypes = (inputMap["entityTypes"] as? List<*>)?.map { it.toString() } ?: emptyList()

        // target 파싱
        val targetMap = map["target"] as? Map<String, Any?> ?: return null
        val targetType = try {
            SinkTargetType.valueOf(targetMap["type"]?.toString()?.uppercase() ?: return null)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val endpoint = resolveEnvVar(targetMap["endpoint"]?.toString() ?: "") ?: ""
        val indexPattern = targetMap["indexPattern"]?.toString()

        val authMap = targetMap["auth"] as? Map<String, Any?>
        val auth = if (authMap != null) {
            AuthSpec(
                type = try { AuthType.valueOf(authMap["type"]?.toString()?.uppercase() ?: "NONE") } catch (e: IllegalArgumentException) { AuthType.NONE },
                username = resolveEnvVar(authMap["username"]?.toString()),
                password = resolveEnvVar(authMap["password"]?.toString())
            )
        } else null

        // docId 파싱
        val docIdMap = map["docId"] as? Map<String, Any?>
        val docId = DocIdSpec(pattern = docIdMap?.get("pattern")?.toString() ?: "{tenantId}__{entityKey}")

        // commit 파싱
        val commitMap = map["commit"] as? Map<String, Any?>
        val commit = CommitSpec(
            batchSize = commitMap?.get("batchSize")?.toString()?.toIntOrNull() ?: 1000,
            timeoutMs = commitMap?.get("timeoutMs")?.toString()?.toLongOrNull() ?: 30000
        )

        return SinkRule(
            id = id,
            version = version,
            status = status,
            input = SinkRuleInput(type = inputType, sliceTypes = sliceTypes, entityTypes = entityTypes),
            target = SinkRuleTarget(type = targetType, endpoint = endpoint, indexPattern = indexPattern, auth = auth),
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
