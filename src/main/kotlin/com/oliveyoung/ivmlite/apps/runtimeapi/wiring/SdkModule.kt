package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.sdk.IvmContext
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.sdk.execution.EntityContractResolver
import org.koin.dsl.module

/**
 * SDK Module (SOTA - IngestionOrchestrator 기반 + Contract is Law)
 *
 * SDK Layer DI 바인딩 (DynamoDB Streams 아키텍처)
 * - EntityContractResolver: EntityType별 RuleSet/ViewDef 동적 해석
 * - DeployExecutor: IngestionOrchestrator + EntityContractResolver 기반 Deploy 실행
 * - IvmContext: Executor + QueryWorkflow 주입
 */
val sdkModule = module {
    // EntityContractResolver (Contract is Law - ContractRegistryPort 기반 동적 해석)
    single {
        EntityContractResolver(contractRegistry = get())
    }

    // DeployExecutor (SOTA - IngestionOrchestrator + EntityContractResolver 기반)
    single {
        DeployExecutor(
            orchestrator = get(),
            contractResolver = get()
        )
    }

    // IvmContext 빌드 및 SDK 초기화
    single<Unit>(createdAtStart = true) {
        val executor = get<DeployExecutor>()
        val queryWorkflow = get<QueryViewWorkflow>()

        val context = IvmContext.builder()
            .executor(executor)
            .queryWorkflow(queryWorkflow)
            .build()

        // SDK 초기화
        Ivm.initialize(context)
    }
}
