package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.TargetRef

@IvmDslMarker
class CompileTargetsBuilder internal constructor() {
    private val targets = mutableListOf<TargetRef>()

    fun targets(block: TargetsBuilder.() -> Unit) {
        TargetsBuilder(targets).apply(block)
    }

    internal fun build(): List<TargetRef> = targets.toList()
}
