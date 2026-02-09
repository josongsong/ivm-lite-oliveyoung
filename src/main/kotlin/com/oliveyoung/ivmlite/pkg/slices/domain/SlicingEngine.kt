package com.oliveyoung.ivmlite.pkg.slices.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceBuildRules
import com.oliveyoung.ivmlite.pkg.contracts.domain.SliceDefinition
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.shared.domain.determinism.CanonicalJson
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.json.JsonPathExtractor
import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * RFC-IMPL-010 Phase D-3: SlicingEngine - RuleSet 기반 슬라이싱 엔진
 * RFC-IMPL-010 Phase D-4: JoinExecutor 연동
 * RFC-IMPL-010 Phase D-9: Inverted Index 동시 생성
 *
 * Contract is Law: RuleSet이 슬라이싱 규칙의 유일한 정의 소스.
 * - 결정성: 동일 RawData + RuleSet → 동일 Slices
 * - 멱등성: 재실행해도 동일 결과
 *
 * RFC-003: JsonPathExtractor 사용으로 중복 로직 제거
 */
class SlicingEngine(
    private val contractRegistry: ContractRegistryPort,
    private val joinExecutor: JoinExecutor? = null,
) {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val indexBuilder: InvertedIndexBuilder = InvertedIndexBuilder()

    /**
     * RawDataRecord를 RuleSet 기반으로 슬라이싱 + Inverted Index 생성
     *
     * @param rawData 원본 데이터
     * @param ruleSetRef RuleSet 계약 참조
     * @return SlicingResult (Slices + Inverted Indexes)
     */
    suspend fun slice(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
    ): Result<SlicingResult> {
        val ruleSet = when (val r = contractRegistry.loadRuleSetContract(ruleSetRef)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }

        return try {
            // 1. 슬라이싱 (JOIN 포함)
            val slices = mutableListOf<SliceRecord>()
            for (def in ruleSet.slices) {
                val sliceResult = buildSlice(rawData, def, ruleSet)
                when (sliceResult) {
                    is Result.Ok -> slices.add(sliceResult.value)
                    is Result.Err -> return Result.Err(sliceResult.error)
                }
            }

            // 2. Inverted Index 생성
            val indexes: List<InvertedIndexEntry> = slices.flatMap { slice ->
                indexBuilder.build(slice, ruleSet.indexes)
            }

            Result.Ok(SlicingResult(slices, indexes))
        } catch (e: Exception) {
            Result.Err(
                DomainError.InvariantViolation(
                    "SlicingEngine.slice failed: ${e.message}",
                ),
            )
        }
    }

    /**
     * RFC-IMPL-010 D-8: 특정 SliceType만 부분 슬라이싱 (INCREMENTAL용)
     *
     * @param rawData 원본 데이터
     * @param ruleSetRef RuleSet 계약 참조
     * @param impactedTypes 영향받은 SliceType 집합
     * @return SlicingResult (영향받은 Slices + Inverted Indexes만 생성)
     */
    suspend fun slicePartial(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
        impactedTypes: Set<com.oliveyoung.ivmlite.shared.domain.types.SliceType>,
    ): Result<SlicingResult> {
        val ruleSet = when (val r = contractRegistry.loadRuleSetContract(ruleSetRef)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }

        return try {
            // 1. 영향받은 SliceType만 슬라이싱
            val slices = mutableListOf<SliceRecord>()
            for (def in ruleSet.slices.filter { it.type in impactedTypes }) {
                val sliceResult = buildSlice(rawData, def, ruleSet)
                when (sliceResult) {
                    is Result.Ok -> slices.add(sliceResult.value)
                    is Result.Err -> return Result.Err(sliceResult.error)
                }
            }

            // 2. Inverted Index 생성
            val indexes: List<InvertedIndexEntry> = slices.flatMap { slice ->
                indexBuilder.build(slice, ruleSet.indexes)
            }

            Result.Ok(SlicingResult(slices, indexes))
        } catch (e: Exception) {
            Result.Err(
                DomainError.InvariantViolation(
                    "SlicingEngine.slicePartial failed: ${e.message}",
                ),
            )
        }
    }

    /**
     * SliceDefinition 기반으로 단일 SliceRecord 생성
     * RFC-IMPL-010 Phase D-4: Light JOIN 실행
     */
    private suspend fun buildSlice(
        rawData: RawDataRecord,
        def: SliceDefinition,
        ruleSet: RuleSetContract,
    ): Result<SliceRecord> {
        // 1. Light JOIN 실행 (joins가 있으면)
        val mergedPayload: String = if (def.joins.isNotEmpty()) {
            if (joinExecutor == null) {
                // JOIN이 정의되어 있는데 executor가 없으면 에러 (fail-closed)
                return Result.Err(
                    DomainError.InvariantViolation(
                        "JoinExecutor not configured but joins defined for slice ${def.type.name}"
                    )
                )
            }
            val joinResult = joinExecutor.executeJoins(rawData, def.joins)
            when (joinResult) {
                is Result.Ok -> {
                    // JOIN 결과를 원본 payload에 병합
                    when (val mergeResult = mergePayload(rawData.payload, joinResult.value)) {
                        is Result.Ok -> mergeResult.value
                        is Result.Err -> return Result.Err(mergeResult.error)
                    }
                }
                is Result.Err -> {
                    return Result.Err(joinResult.error)
                }
            }
        } else {
            rawData.payload
        }

        // 2. 슬라이싱 규칙 적용
        val data: String = when (val rules = def.buildRules) {
            is SliceBuildRules.PassThrough -> {
                when (val r = applyPassThrough(mergedPayload, rules.fields)) {
                    is Result.Ok -> r.value
                    is Result.Err -> return r
                }
            }
            is SliceBuildRules.MapFields -> {
                when (val r = applyFieldMappings(mergedPayload, rules.mappings)) {
                    is Result.Ok -> r.value
                    is Result.Err -> return r
                }
            }
        }

        // 3. 정규화 및 해싱
        val canonical = CanonicalJson.canonicalize(data)
        val hash = Hashing.sha256Tagged(canonical)

        return Result.Ok(
            SliceRecord(
                tenantId = rawData.tenantId,
                entityKey = rawData.entityKey,
                version = rawData.version,
                sliceType = def.type,
                data = canonical,
                hash = hash,
                ruleSetId = ruleSet.meta.id,
                ruleSetVersion = ruleSet.meta.version,
            ),
        )
    }

    /**
     * JOIN 결과를 원본 payload에 병합
     *
     * @param originalPayload 원본 payload JSON string
     * @param joinResults JOIN 결과 맵 (key: joinSpec.name, value: 타겟 payload)
     * @return 병합된 payload JSON string (Result 타입)
     */
    private fun mergePayload(originalPayload: String, joinResults: Map<String, String>): Result<String> {
        return try {
            val original = mapper.readTree(originalPayload)
            if (original !is ObjectNode) {
                return Result.Err(
                    DomainError.ValidationError("payload", "Original payload is not a JSON object")
                )
            }
            joinResults.forEach { (name, payloadJson) ->
                val joinedData = mapper.readTree(payloadJson)
                original.set<JsonNode>(name, joinedData)
            }
            Result.Ok(mapper.writeValueAsString(original))
        } catch (e: Exception) {
            Result.Err(
                DomainError.ValidationError("payload", "Failed to parse/merge JSON: ${e.message}")
            )
        }
    }

    /**
     * PassThrough 규칙 적용: 지정된 필드만 통과
     * fields = ["*"] → 전체 payload 통과
     */
    private fun applyPassThrough(payload: String, fields: List<String>): Result<String> {
        if (payload.isBlank()) {
            return Result.Ok("{}")
        }

        if (fields.contains("*")) {
            return Result.Ok(payload)
        }

        return try {
            val source = mapper.readTree(payload)
            val target = mapper.createObjectNode()

            fields.forEach { field ->
                val value = source.get(field)
                if (value != null) {
                    target.set<JsonNode>(field, value)
                }
            }

            Result.Ok(mapper.writeValueAsString(target))
        } catch (e: Exception) {
            Result.Err(
                DomainError.ValidationError("payload", "Failed to parse JSON in PassThrough: ${e.message}")
            )
        }
    }

    /**
     * MapFields 규칙 적용: 소스 필드를 타겟 필드로 매핑
     *
     * 지원 패턴:
     * - 단순 필드: "name" → "name"
     * - 중첩 필드: "product.name" → "name"
     * - 배열 내부: "items[*].name" → "names"
     *
     * JsonPathExtractor 사용으로 중복 로직 제거
     */
    private fun applyFieldMappings(payload: String, mappings: Map<String, String>): Result<String> {
        if (payload.isBlank()) {
            return Result.Ok("{}")
        }

        return try {
            val source = mapper.readTree(payload)
            val target = mapper.createObjectNode()

            mappings.forEach { (sourcePath, targetField) ->
                val value = JsonPathExtractor.extractNode(source, sourcePath)
                if (value != null && !value.isNull) {
                    target.set<JsonNode>(targetField, value)
                }
            }

            Result.Ok(mapper.writeValueAsString(target))
        } catch (e: Exception) {
            Result.Err(
                DomainError.ValidationError("payload", "Failed to parse JSON in MapFields: ${e.message}")
            )
        }
    }

    /**
     * RFC-IMPL-010 Phase D-9: 슬라이싱 결과 (Slices + Inverted Indexes)
     */
    data class SlicingResult(
        val slices: List<SliceRecord>,
        val indexes: List<InvertedIndexEntry>,
    )
}
