package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.model.DeployResult

/**
 * Shipped Entity - 전송 완료 상태
 */
class ShippedEntity<T : EntityInput>(
    private val input: T,
    private val shipResult: ShipResult,
    private val config: IvmClientConfig
) {
    val entityKey: String get() = shipResult.entityKey
    val version: Long get() = shipResult.version
    val sinks: List<String> get() = shipResult.sinks
    val success: Boolean get() = shipResult.success
    val error: String? get() = shipResult.error

    fun toDeployResult(): DeployResult {
        return if (success) {
            DeployResult.success(entityKey, "v$version")
        } else {
            DeployResult.failure(entityKey, "v$version", error ?: "Unknown error")
        }
    }
}
