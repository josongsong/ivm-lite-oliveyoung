package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.CutoverMode

@IvmDslMarker
class CutoverAccessor internal constructor(
    private val onSet: (CutoverMode) -> Unit
) {
    fun ready() { onSet(CutoverMode.Ready) }
    fun done() { onSet(CutoverMode.Done) }
}
