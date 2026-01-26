package com.oliveyoung.ivmlite.sdk.model

data class DeployResult(
    val success: Boolean,
    val entityKey: String,
    val version: String,
    val error: String? = null
) {
    companion object {
        fun success(entityKey: String, version: String) =
            DeployResult(success = true, entityKey = entityKey, version = version)

        fun failure(entityKey: String, version: String, error: String) =
            DeployResult(success = false, entityKey = entityKey, version = version, error = error)
    }
}
