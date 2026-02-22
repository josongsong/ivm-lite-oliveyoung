package com.oliveyoung.ivmlite.sdk.model

/**
 * Deploy 결과 (RFC-021: getOrThrow, onSuccess, onFailure)
 */
data class DeployResult(
    val success: Boolean,
    val entityKey: String,
    val version: String,
    val error: String? = null
) {
    /**
     * 성공 시 this 반환, 실패 시 DeployException 던짐
     */
    fun getOrThrow(): DeployResult {
        if (!success) throw DeployException(entityKey, version, error ?: "Unknown error")
        return this
    }

    /**
     * 성공 시에만 block 실행 (체이닝)
     */
    fun onSuccess(block: (DeployResult) -> Unit): DeployResult {
        if (success) block(this)
        return this
    }

    /**
     * 실패 시에만 block 실행 (체이닝)
     */
    fun onFailure(block: (DeployResult) -> Unit): DeployResult {
        if (!success) block(this)
        return this
    }

    companion object {
        fun success(entityKey: String, version: String) =
            DeployResult(success = true, entityKey = entityKey, version = version)

        fun failure(entityKey: String, version: String, error: String) =
            DeployResult(success = false, entityKey = entityKey, version = version, error = error)
    }
}

/** Deploy 실패 예외 */
class DeployException(
    val entityKey: String,
    val version: String,
    override val message: String
) : RuntimeException("Deploy failed for $entityKey: $message")
