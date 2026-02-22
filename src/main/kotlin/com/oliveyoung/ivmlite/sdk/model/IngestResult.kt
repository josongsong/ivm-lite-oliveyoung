package com.oliveyoung.ivmlite.sdk.model

/**
 * Ingest 단계 결과
 */
data class IngestResult(
    val entityKey: String,
    val version: Long,
    val success: Boolean,
    val error: String? = null
)
