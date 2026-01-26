package com.oliveyoung.ivmlite.sdk.dsl.sink

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.OpenSearchSinkSpec

@IvmDslMarker
class OpenSearchBuilder internal constructor() {
    private var index: String? = null
    private var alias: String? = null
    private var batchSize: Int = 1000

    fun index(value: String) {
        index = value
    }

    fun alias(value: String) {
        alias = value
    }

    fun batchSize(value: Int) {
        batchSize = value
    }

    internal fun build(): OpenSearchSinkSpec = OpenSearchSinkSpec(index, alias, batchSize)
}
