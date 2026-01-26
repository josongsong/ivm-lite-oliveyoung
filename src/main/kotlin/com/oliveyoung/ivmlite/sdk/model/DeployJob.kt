package com.oliveyoung.ivmlite.sdk.model

// Note: DeployState는 Wave 1-B에서 정의 예정
data class DeployJob(
    val jobId: String,
    val entityKey: String,
    val version: String,
    val state: DeployState
)
