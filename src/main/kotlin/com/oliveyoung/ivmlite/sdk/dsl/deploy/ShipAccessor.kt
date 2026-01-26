package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.dsl.sink.SinkBuilder
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

@IvmDslMarker
class ShipAccessor internal constructor(
    private val onSet: (ShipSpec) -> Unit
) {
    fun sync(block: SinkBuilder.() -> Unit) {
        val sinks = SinkBuilder().apply(block).build()
        onSet(ShipSpec(ShipMode.Sync, sinks))
    }

    fun async(block: SinkBuilder.() -> Unit) {
        val sinks = SinkBuilder().apply(block).build()
        onSet(ShipSpec(ShipMode.Async, sinks))
    }
}
