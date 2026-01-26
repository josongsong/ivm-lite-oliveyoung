package com.oliveyoung.ivmlite.sdk.model

import java.time.Instant

/**
 * Deploy Job 상태 정보
 * RFC-IMPL-011 Wave 1-B
 */
data class DeployJobStatus(
    val jobId: String,
    val state: DeployState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val error: String? = null
)
