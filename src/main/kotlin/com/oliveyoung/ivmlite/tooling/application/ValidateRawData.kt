package com.oliveyoung.ivmlite.tooling.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * RawData Pre-Ingest 검증 (product-schema-dx-proposal RFC 5.1)
 *
 * - JSON 파싱
 * - 필수 경로 존재 (uaCode, _meta 등)
 * - rule/view/slice 존재성
 *
 * CI 통합: ./gradlew validateRawData -Dsample=.tmp/product/UA11279226.json
 */
object ValidateRawData {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    )

    /** Product 엔티티 필수 경로 (RFC 1.2 기준) */
    private val productRequiredPaths = listOf(
        "uaCode",
        "_meta",
        "_meta/schemaVersion",
    )

    /**
     * JSON 파일 검증
     *
     * @param sampleFile JSON 샘플 파일 (또는 디렉토리 내 .json 파일들)
     * @param ruleSetId RuleSet ID (기본: ruleset.product.oliveyoung.v1)
     * @return ValidationResult
     */
    fun validate(sampleFile: File, ruleSetId: String = "ruleset.product.oliveyoung.v1"): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. JSON 파싱
        val root = try {
            mapper.readTree(sampleFile.readText())
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            errors.add("JSON 파싱 실패: ${e.message}")
            return ValidationResult(valid = false, errors = errors)
        }

        if (!root.isObject) {
            errors.add("루트가 객체여야 합니다")
            return ValidationResult(valid = false, errors = errors)
        }

        // 2. 필수 경로 검증
        for (path in productRequiredPaths) {
            if (!pathExists(root, path)) {
                errors.add("필수 경로 누락: $path")
            }
        }

        // 3. RuleSet/View/Slice 존재성
        runBlocking {
            val contractRegistry = GatedContractRegistryAdapter(
                delegate = LocalYamlContractRegistryAdapter("/contracts/v1"),
                statusGate = DefaultContractStatusGate,
            )
            val ruleSetRef = ContractRef(ruleSetId, SemVer.parse("1.0.0"))
            val result = contractRegistry.loadRuleSetContract(ruleSetRef)
            when (result) {
                is Result.Ok -> {
                    result.value.slices.isEmpty() && warnings.add("RuleSet에 slice 정의가 없습니다")
                }
                is Result.Err -> {
                    errors.add("RuleSet 로드 실패: ${result.error}")
                }
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /** 디렉토리 내 모든 JSON 파일 검증 */
    fun validateDir(dir: File, ruleSetId: String = "ruleset.product.oliveyoung.v1"): List<Pair<File, ValidationResult>> {
        if (!dir.exists() || !dir.isDirectory) {
            return listOf()
        }
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .map { it to validate(it, ruleSetId) }
            .toList()
    }

    private fun pathExists(root: JsonNode, path: String): Boolean {
        val parts = path.split("/")
        var current: JsonNode? = root
        for (part in parts) {
            if (current == null) return false
            current = current.get(part)
        }
        return current != null && !current.isNull
    }
}
