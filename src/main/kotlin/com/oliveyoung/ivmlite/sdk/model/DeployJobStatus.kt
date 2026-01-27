package com.oliveyoung.ivmlite.sdk.model

import java.time.Instant

/**
 * Deploy Job 상태 정보
 * RFC-IMPL-011 Wave 1-B, Wave 6
 */
data class DeployJobStatus(
    val jobId: String,
    val state: DeployState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val entityKey: String? = null,
    val version: String? = null,
    val error: String? = null
)
