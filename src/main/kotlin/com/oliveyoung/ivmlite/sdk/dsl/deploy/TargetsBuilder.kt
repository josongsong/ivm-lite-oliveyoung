package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.TargetRef

@IvmDslMarker
class TargetsBuilder internal constructor(
    private val targets: MutableList<TargetRef>
) {
    fun searchDoc(version: String = "v1") {
        targets.add(TargetRef("search-doc", version))
    }

    fun recoFeed(version: String = "v1") {
        targets.add(TargetRef("reco-feed", version))
    }

    fun custom(id: String, version: String = "v1") {
        targets.add(TargetRef(id, version))
    }
}
