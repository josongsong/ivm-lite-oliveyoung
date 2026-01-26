package com.oliveyoung.ivmlite.sdk.dsl.sink

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.PersonalizeSinkSpec

@IvmDslMarker
class PersonalizeBuilder internal constructor() {
    private var datasetArn: String? = null
    private var roleArn: String? = null

    fun datasetArn(value: String) {
        datasetArn = value
    }

    fun dataset(value: String) {
        datasetArn = value
    }

    fun roleArn(value: String) {
        roleArn = value
    }

    internal fun build(): PersonalizeSinkSpec = PersonalizeSinkSpec(datasetArn, roleArn)
}
