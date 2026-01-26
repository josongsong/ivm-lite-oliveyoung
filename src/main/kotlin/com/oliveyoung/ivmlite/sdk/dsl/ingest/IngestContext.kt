package com.oliveyoung.ivmlite.sdk.dsl.ingest

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.dsl.entity.GenericEntityInput
import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.schema.EntityBuilder
import com.oliveyoung.ivmlite.sdk.schema.EntityRef

/**
 * Ingest Context - 쓰기 진입점
 * 
 * @example 레거시 패턴
 * ```kotlin
 * Ivm.client().ingest().product { ... }.deploy()
 * ```
 * 
 * @example 코드젠 패턴 (추천)
 * ```kotlin
 * Ivm.client().ingest(Entities.Product) { ... }.deploy()
 * ```
 */
@IvmDslMarker
class IngestContext internal constructor(
    internal val config: IvmClientConfig,
    internal val executor: DeployExecutor? = null
) {
    /**
     * 코드젠 엔티티로 Ingest (추천)
     * 
     * @example
     * ```kotlin
     * Ivm.client().ingest(Entities.Product) {
     *     sku = "SKU-001"
     *     name = "비타민C"
     *     price = 15000
     * }.deploy()
     * ```
     */
    fun <T : EntityBuilder> entity(
        entityRef: EntityRef<T>,
        block: T.() -> Unit
    ): DeployableContext {
        val builder = entityRef.builderFactory()
        builder.block()
        val data = builder.build()
        
        // tenantId 추가
        val tenantId = config.tenantId ?: throw IllegalArgumentException("tenantId is required")
        val input = GenericEntityInput(
            tenantId = tenantId,
            entityType = entityRef.entityType,
            data = data
        )
        
        return DeployableContext(input, config, executor)
    }
}
