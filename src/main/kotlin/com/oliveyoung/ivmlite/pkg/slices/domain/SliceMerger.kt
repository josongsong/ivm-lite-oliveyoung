package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SliceMerger - Slice들을 하나의 JSON 문서로 병합
 *
 * RFC-003: fail-closed 원칙 적용
 * - JSON 파싱 실패 시 명시적 에러 반환 (예외 무시 금지)
 * - 결정성 보장: 동일 입력 → 동일 출력
 *
 * SOLID 원칙 적용:
 * - Single Responsibility: Slice 병합 로직만 담당
 * - 기존 ShipWorkflow.mergeSlices()에서 분리
 *
 * 사용처:
 * - ShipWorkflow: Sink로 전송 전 Slice 병합
 * - QueryViewWorkflow: View 조회 시 Slice 병합
 */
object SliceMerger {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Slice들을 하나의 JSON 문서로 병합 (fail-closed)
     *
     * @param slices 병합할 Slice 목록
     * @param excludeTombstones Tombstone 제외 여부 (기본값: true)
     * @return Result<String> - 파싱 실패 시 Err
     */
    fun merge(slices: List<SliceRecord>, excludeTombstones: Boolean = true): Result<String> {
        return try {
            val merged = buildJsonObject {
                slices.forEach { slice ->
                    // Tombstone이 아닌 Slice만 포함 (옵션)
                    if (!excludeTombstones || slice.tombstone == null) {
                        val sliceJson = json.parseToJsonElement(slice.data)
                        if (sliceJson is JsonObject) {
                            sliceJson.forEach { (key, value) ->
                                put(key, value)
                            }
                        }
                    }
                }
            }
            Result.Ok(json.encodeToString(JsonObject.serializer(), merged))
        } catch (e: Exception) {
            Result.Err(DomainError.InvariantViolation("SliceMerger: JSON parsing failed - ${e.message}"))
        }
    }

    /**
     * Slice들을 SliceType별로 그룹화하여 병합 (fail-closed)
     *
     * @param slices 병합할 Slice 목록
     * @return Result<Map<SliceTypeName, JSON String>>
     */
    fun mergeByType(slices: List<SliceRecord>): Result<Map<String, String>> {
        val result = mutableMapOf<String, String>()
        val grouped = slices
            .filter { it.tombstone == null }
            .groupBy { it.sliceType.name }

        for ((typeName, typeSlices) in grouped) {
            when (val mergeResult = merge(typeSlices)) {
                is Result.Ok -> result[typeName] = mergeResult.value
                is Result.Err -> return mergeResult
            }
        }

        return Result.Ok(result)
    }
}
