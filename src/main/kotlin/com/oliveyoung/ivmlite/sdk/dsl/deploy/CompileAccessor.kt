package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.CompileMode

@IvmDslMarker
class CompileAccessor internal constructor(
    private val onSet: (CompileMode) -> Unit
) {
    fun sync() { onSet(CompileMode.Sync) }
    fun async() { onSet(CompileMode.Async) }

    // RFC-009: targets 지원
    operator fun invoke(block: CompileTargetsBuilder.() -> Unit) {
        val targets = CompileTargetsBuilder().apply(block).build()
        onSet(CompileMode.SyncWithTargets(targets))
    }
}
