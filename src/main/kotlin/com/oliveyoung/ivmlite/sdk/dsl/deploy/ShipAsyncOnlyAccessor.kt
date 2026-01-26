package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.dsl.sink.SinkBuilder
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

@IvmDslMarker
class ShipAsyncOnlyAccessor internal constructor(
    private val onSet: (ShipSpec) -> Unit
) {
    fun async(block: SinkBuilder.() -> Unit) {
        val sinks = SinkBuilder().apply(block).build()
        onSet(ShipSpec(ShipMode.Async, sinks))
    }
    // sync 없음 - 타입 레벨에서 차단!
}
