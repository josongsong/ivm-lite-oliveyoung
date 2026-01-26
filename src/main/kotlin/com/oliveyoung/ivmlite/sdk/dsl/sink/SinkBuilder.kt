package com.oliveyoung.ivmlite.sdk.dsl.sink

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.SinkSpec

@IvmDslMarker
class SinkBuilder internal constructor() {
    private val sinks = mutableListOf<SinkSpec>()

    fun opensearch(block: OpenSearchBuilder.() -> Unit = {}) {
        sinks.add(OpenSearchBuilder().apply(block).build())
    }

    fun personalize(block: PersonalizeBuilder.() -> Unit = {}) {
        sinks.add(PersonalizeBuilder().apply(block).build())
    }

    internal fun build(): List<SinkSpec> = sinks.toList()
}
