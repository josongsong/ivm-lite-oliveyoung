package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor

class BrandActions(
    input: BrandInput,
    config: IvmClientConfig,
    executor: DeployExecutor?
) : EntityActions<BrandInput>(input, config, executor) {
    override fun buildEntityKey(): String = "${input.entityType}:${input.brandId}"
}
