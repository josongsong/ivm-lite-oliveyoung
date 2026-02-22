package com.oliveyoung.ivmlite.sdk.model

data class DeployJob(
    val jobId: String,
    val entityKey: String,
    val version: String,
    val state: DeployState
)
