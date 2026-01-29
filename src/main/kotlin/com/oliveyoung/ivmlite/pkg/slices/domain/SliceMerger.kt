package com.oliveyoung.ivmlite.pkg.slices.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SliceMerger - Slice들을 하나의 JSON 문서로 병합
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
     * Slice들을 하나의 JSON 문서로 병합
     * 
     * @param slices 병합할 Slice 목록
     * @param excludeTombstones Tombstone 제외 여부 (기본값: true)
     * @return 병합된 JSON 문자열
     */
    fun merge(slices: List<SliceRecord>, excludeTombstones: Boolean = true): String {
        val merged = buildJsonObject {
            slices.forEach { slice ->
                // Tombstone이 아닌 Slice만 포함 (옵션)
                if (!excludeTombstones || slice.tombstone == null) {
                    try {
                        val sliceJson = json.parseToJsonElement(slice.data)
                        if (sliceJson is JsonObject) {
                            sliceJson.forEach { (key, value) ->
                                put(key, value)
                            }
                        }
                    } catch (e: Exception) {
                        // JSON 파싱 실패 시 raw data 포함
                        put(slice.sliceType.name.lowercase(), slice.data)
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), merged)
    }
    
    /**
     * Slice들을 SliceType별로 그룹화하여 병합
     * 
     * @param slices 병합할 Slice 목록
     * @return Map<SliceTypeName, JSON String>
     */
    fun mergeByType(slices: List<SliceRecord>): Map<String, String> {
        return slices
            .filter { it.tombstone == null }
            .groupBy { it.sliceType.name }
            .mapValues { (_, typeSlices) ->
                merge(typeSlices)
            }
    }
}
