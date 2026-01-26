package com.oliveyoung.ivmlite.sdk.model

/**
 * 단계별 실행 결과 타입
 * RFC-IMPL-011 Wave 5-L
 */

/**
 * Ingest 단계 결과
 */
data class IngestResult(
    val entityKey: String,
    val version: Long,
    val success: Boolean,
    val error: String? = null
)

/**
 * Compile 단계 결과
 */
data class CompileResult(
    val entityKey: String,
    val version: Long,
    val slices: List<String>,
    val success: Boolean,
    val error: String? = null
)

/**
 * Ship 단계 결과
 */
data class ShipResult(
    val entityKey: String,
    val version: Long,
    val sinks: List<String>,
    val success: Boolean,
    val error: String? = null
)
