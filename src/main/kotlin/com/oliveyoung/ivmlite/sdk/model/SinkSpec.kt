package com.oliveyoung.ivmlite.sdk.model

sealed interface SinkSpec

data class OpenSearchSinkSpec(
    val index: String? = null,
    val alias: String? = null,
    val batchSize: Int = 1000
) : SinkSpec

data class PersonalizeSinkSpec(
    val datasetArn: String? = null,
    val roleArn: String? = null
) : SinkSpec
