package com.oliveyoung.ivmlite.pkg.slices.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.json.JsonPathExtractor
import com.oliveyoung.ivmlite.shared.domain.json.JsonPathExtractor.ExtractResult
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * RFC-IMPL-010 Phase D-4: JoinExecutor - Light JOIN 실행
 *
 * LOOKUP 타입만 v1 지원 (1:1 조회)
 * - required=true → 타겟 없으면 Err(JoinError)
 * - required=false → 타겟 없으면 빈 맵 반환
 *
 * Join Boundary: Slice 생성 시에만 JOIN 허용 (View에서 재계산 금지)
 * 결정성: 동일 소스 + 동일 타겟 → 동일 결과
 *
 * RFC-003: fail-closed 원칙 - required join에서 파싱 실패는 명시적 에러
 *
 * 성능 최적화:
 * - batchGetLatest: N+1 쿼리를 단일 IN 쿼리로 최적화
 * - 여러 JoinSpec의 타겟을 한 번에 조회 후 매핑
 */
class JoinExecutor(
    private val rawRepo: RawDataRepositoryPort,
) {
    companion object {
        // ObjectMapper 싱글톤 (thread-safe, 재사용으로 GC 압력 감소)
        private val mapper = ObjectMapper()
    }
    /**
     * 여러 JoinSpec 실행 → 모든 결과 병합 (배치 최적화)
     *
     * 최적화 전략:
     * 1. 모든 JoinSpec에서 타겟 키 추출 (실패 시 required 체크)
     * 2. batchGetLatest로 한 번에 조회
     * 3. 결과 매핑
     *
     * @param rawData 소스 RawData
     * @param joinSpecs JOIN 스펙 리스트
     * @return 병합된 JOIN 결과 맵 (key: joinSpec.name, value: 타겟 payload)
     */
    suspend fun executeJoins(
        rawData: RawDataRecord,
        joinSpecs: List<JoinSpec>,
    ): Result<Map<String, String>> {
        if (joinSpecs.isEmpty()) {
            return Result.Ok(emptyMap())
        }

        // 1. 모든 JoinSpec에서 타겟 키 추출
        val specToTargetKey = mutableMapOf<JoinSpec, EntityKey>()
        for (spec in joinSpecs) {
            when (val keyResult = extractTargetKey(rawData, spec)) {
                is Result.Ok -> {
                    if (keyResult.value != null) {
                        specToTargetKey[spec] = keyResult.value
                    }
                }
                is Result.Err -> {
                    if (spec.required) return keyResult as Result<Map<String, String>>
                    // optional이면 skip
                }
            }
        }

        if (specToTargetKey.isEmpty()) {
            return Result.Ok(emptyMap())
        }

        // 2. batchGetLatest로 한 번에 조회
        val entityKeys = specToTargetKey.values.toList()
        val batchResult = rawRepo.batchGetLatest(rawData.tenantId, entityKeys)

        val fetchedRecords = when (batchResult) {
            is Result.Ok -> batchResult.value
            is Result.Err -> return batchResult as Result<Map<String, String>>
        }

        // 3. 결과 매핑 (Projection 처리 포함)
        val results = mutableMapOf<String, String>()
        for ((spec, targetKey) in specToTargetKey) {
            val record = fetchedRecords[targetKey]
            if (record != null) {
                val payload = applyProjection(record.payload, spec.projection)
                results[spec.name] = payload
            } else if (spec.required) {
                return Result.Err(DomainError.JoinError("target not found: ${targetKey.value}"))
            }
            // optional이면 skip
        }

        return Result.Ok(results)
    }

    /**
     * Projection 적용
     *
     * @param payload 원본 JSON 문자열
     * @param projection Projection 정의 (null이면 원본 반환)
     * @return Projection이 적용된 JSON 문자열
     */
    private fun applyProjection(payload: String, projection: Projection?): String {
        if (projection == null) {
            return payload
        }

        return try {
            val sourceNode = mapper.readTree(payload)
            val resultNode = mapper.createObjectNode()

            when (projection.mode) {
                ProjectionMode.COPY_FIELDS -> {
                    for (field in projection.fields) {
                        val fromPath = field.fromTargetPath.trimStart('/')
                        val toPath = field.toOutputPath.trimStart('/')
                        val value = sourceNode.at("/${fromPath}")
                        if (!value.isMissingNode) {
                            resultNode.set<com.fasterxml.jackson.databind.JsonNode>(toPath, value)
                        }
                    }
                }
                ProjectionMode.EXCLUDE_FIELDS -> {
                    // 원본 복사 후 지정 필드 제외
                    if (sourceNode is ObjectNode) {
                        resultNode.setAll<ObjectNode>(sourceNode)
                        for (field in projection.fields) {
                            val path = field.fromTargetPath.trimStart('/')
                            resultNode.remove(path)
                        }
                    }
                }
            }

            mapper.writeValueAsString(resultNode)
        } catch (e: Exception) {
            // Projection 실패 시 원본 반환
            payload
        }
    }

    /**
     * 타겟 키 추출 (배치 조회용)
     */
    private fun extractTargetKey(
        rawData: RawDataRecord,
        spec: JoinSpec,
    ): Result<EntityKey?> {
        val sourceValue = when (val extractResult = JsonPathExtractor.extractSingle(rawData.payload, spec.sourceFieldPath)) {
            is ExtractResult.Found -> extractResult.value
            is ExtractResult.NotFound -> return if (spec.required) {
                Result.Err(DomainError.JoinError("required source field missing: ${spec.sourceFieldPath}"))
            } else {
                Result.Ok(null)
            }
            is ExtractResult.ParseError -> return if (spec.required) {
                Result.Err(DomainError.JoinError("required source field parse error at ${spec.sourceFieldPath}: ${extractResult.message}"))
            } else {
                Result.Ok(null)
            }
        }

        val targetKey = interpolateKey(
            spec.targetKeyPattern,
            mapOf(
                "tenantId" to rawData.tenantId.value,
                "value" to sourceValue,
            ),
        )

        return Result.Ok(EntityKey(targetKey))
    }

    /**
     * 단일 JoinSpec 실행
     *
     * 로직:
     * 1. sourceFieldPath에서 값 추출 (dot notation: brand.code)
     * 2. targetKeyPattern 보간 (BRAND#{tenantId}#{brandCode})
     * 3. 타겟 RawData 조회 (getLatest)
     * 4. 타겟 payload 반환
     */
    private suspend fun executeJoin(
        rawData: RawDataRecord,
        spec: JoinSpec,
    ): Result<Map<String, String>> {
        // 1. 소스 필드에서 값 추출 (fail-closed for required joins)
        val sourceValue = when (val extractResult = JsonPathExtractor.extractSingle(rawData.payload, spec.sourceFieldPath)) {
            is ExtractResult.Found -> extractResult.value
            is ExtractResult.NotFound -> return if (spec.required) {
                Result.Err(DomainError.JoinError("required source field missing: ${spec.sourceFieldPath}"))
            } else {
                Result.Ok(emptyMap())
            }
            is ExtractResult.ParseError -> return if (spec.required) {
                // RFC-003: required join에서 파싱 실패는 명시적 에러 (fail-closed)
                Result.Err(DomainError.JoinError("required source field parse error at ${spec.sourceFieldPath}: ${extractResult.message}"))
            } else {
                Result.Ok(emptyMap())
            }
        }

        // 2. 타겟 키 생성
        val targetKey = interpolateKey(
            spec.targetKeyPattern,
            mapOf(
                "tenantId" to rawData.tenantId.value,
                "value" to sourceValue,
            ),
        )

        // 3. 타겟 조회
        return when (val target = rawRepo.getLatest(rawData.tenantId, EntityKey(targetKey))) {
            is Result.Ok ->
                Result.Ok(mapOf(spec.name to target.value.payload))
            is Result.Err ->
                if (spec.required) {
                    Result.Err(DomainError.JoinError("target not found: $targetKey"))
                } else {
                    Result.Ok(emptyMap())
                }
        }
    }

    /**
     * targetKeyPattern 보간
     *
     * 패턴: BRAND#{tenantId}#{value}
     * 변수:
     * - {tenantId} → rawData.tenantId.value
     * - {value} → sourceValue
     *
     * 주의: `#{key}` 패턴에서 `#`은 separator로 유지되고, `{key}`만 치환됩니다.
     *
     * @return 보간된 키
     */
    private fun interpolateKey(pattern: String, vars: Map<String, String>): String {
        var result = pattern
        vars.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }
}
