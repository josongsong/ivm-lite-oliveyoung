package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import org.koin.dsl.module

/**
 * SDK Module (RFC-IMPL-011 Wave 5-L)
 *
 * SDK Layer DI 바인딩
 * - DeployExecutor: Deploy 실행 엔진
 */
val sdkModule = module {
    // DeployExecutor (RFC-IMPL-011 Wave 5-L)
    single {
        DeployExecutor(
            ingestWorkflow = get(),
            slicingWorkflow = get(),
            shipWorkflow = get(),
            outboxRepository = get()
        )
    }
}
