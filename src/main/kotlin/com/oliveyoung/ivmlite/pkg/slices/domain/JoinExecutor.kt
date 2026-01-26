package com.oliveyoung.ivmlite.pkg.slices.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey

/**
 * RFC-IMPL-010 Phase D-4: JoinExecutor - Light JOIN 실행
 *
 * LOOKUP 타입만 v1 지원 (1:1 조회)
 * - required=true → 타겟 없으면 Err(JoinError)
 * - required=false → 타겟 없으면 빈 맵 반환
 *
 * Join Boundary: Slice 생성 시에만 JOIN 허용 (View에서 재계산 금지)
 * 결정성: 동일 소스 + 동일 타겟 → 동일 결과
 */
class JoinExecutor(
    private val rawRepo: RawDataRepositoryPort,
) {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * 여러 JoinSpec 실행 → 모든 결과 병합
     *
     * @param rawData 소스 RawData
     * @param joinSpecs JOIN 스펙 리스트
     * @return 병합된 JOIN 결과 맵 (key: joinSpec.name, value: 타겟 payload)
     */
    suspend fun executeJoins(
        rawData: RawDataRecord,
        joinSpecs: List<JoinSpec>,
    ): Result<Map<String, String>> {
        val results = mutableMapOf<String, String>()

        for (spec in joinSpecs) {
            when (val result = executeJoin(rawData, spec)) {
                is Result.Ok -> results.putAll(result.value)
                is Result.Err -> if (spec.required) return result else continue
            }
        }

        return Result.Ok(results)
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
        // 1. 소스 필드에서 값 추출
        val sourceValue = extractField(rawData.payload, spec.sourceFieldPath)
            ?: return if (spec.required) {
                Result.Err(DomainError.JoinError("required source field missing: ${spec.sourceFieldPath}"))
            } else {
                Result.Ok(emptyMap())
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
            is RawDataRepositoryPort.Result.Ok ->
                Result.Ok(mapOf(spec.name to target.value.payload))
            is RawDataRepositoryPort.Result.Err ->
                if (spec.required) {
                    Result.Err(DomainError.JoinError("target not found: $targetKey"))
                } else {
                    Result.Ok(emptyMap())
                }
        }
    }

    /**
     * JSON payload에서 필드 추출 (dot notation)
     *
     * 지원 패턴:
     * - "name" → root.name
     * - "product.name" → root.product.name
     * - "items[0].name" → root.items[0].name (배열 인덱스)
     *
     * @return 추출된 값 (String) 또는 null
     */
    private fun extractField(payload: String, path: String): String? {
        if (payload.isBlank()) {
            return null
        }

        return try {
            val root = mapper.readTree(payload)
            val value = extractValue(root, path)
            when {
                value == null || value.isNull -> null
                value.isTextual -> value.asText()
                value.isNumber -> value.asText()
                value.isBoolean -> value.asText()
                else -> mapper.writeValueAsString(value) // 객체/배열은 JSON string으로
            }
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            // JSON 파싱 실패 시 null 반환 (fail-open for optional joins)
            null
        } catch (e: IllegalArgumentException) {
            // 잘못된 배열 인덱스 등 (toIntOrNull 후속 처리)
            null
        }
    }

    /**
     * JSON path에서 값 추출 (SlicingEngine과 동일 로직)
     *
     * 지원 패턴:
     * - "name" → root.name
     * - "product.name" → root.product.name
     * - "items[0].name" → root.items[0].name
     */
    private fun extractValue(root: JsonNode, path: String): JsonNode? {
        val parts = path.split(".")
        var current: JsonNode? = root

        for (part in parts) {
            // 배열 인덱스 패턴: items[0]
            if (part.contains("[") && part.endsWith("]")) {
                val fieldName = part.substringBefore("[")
                val index = part.substringAfter("[").substringBefore("]").toIntOrNull()
                    ?: return null

                current = current?.get(fieldName)
                if (current == null || !current.isArray) {
                    return null
                }
                current = current.get(index)
            } else {
                current = current?.get(part)
            }

            if (current == null) {
                return null
            }
        }

        return current
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

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val error: DomainError) : Result<Nothing>()
    }
}
