package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor

class ProductActions(
    input: ProductInput,
    config: IvmClientConfig,
    executor: DeployExecutor?
) : EntityActions<ProductInput>(input, config, executor) {
    override fun buildEntityKey(): String = "${input.entityType}:${input.sku}"
}
