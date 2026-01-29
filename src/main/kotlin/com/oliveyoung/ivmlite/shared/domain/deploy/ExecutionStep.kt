package com.oliveyoung.ivmlite.shared.domain.deploy

/**
 * 실행 스텝
 * RFC-IMPL-011 Wave 1-B
 * 
 * Note: 이 모델은 pkg, sdk 모두에서 사용되므로 shared에 위치
 */
data class ExecutionStep(
    val stepNumber: Int,
    val sliceRef: String,
    val dependencies: List<String>
)
