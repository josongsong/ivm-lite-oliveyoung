package com.oliveyoung.ivmlite.sdk.domain

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor

class CategoryActions(
    input: CategoryInput,
    config: IvmClientConfig,
    executor: DeployExecutor?
) : EntityActions<CategoryInput>(input, config, executor) {
    override fun buildEntityKey(): String = "${input.entityType}:${input.categoryId}"
}
