package com.oliveyoung.ivmlite.sdk.model

/**
 * 실행 스텝
 * RFC-IMPL-011 Wave 1-B
 */
data class ExecutionStep(
    val stepNumber: Int,
    val sliceRef: String,
    val dependencies: List<String>
)
