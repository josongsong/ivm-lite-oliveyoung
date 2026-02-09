package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.sdk.IvmContext
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.shared.config.AppConfig
import org.koin.dsl.module

/**
 * SDK Module (RFC-IMPL-011 Wave 5-L)
 *
 * SDK Layer DI 바인딩
 * - DeployExecutor: Deploy 실행 엔진
 * - IvmContext: SDK 의존성 컨테이너 (단일 객체로 통합)
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

    // IvmContext 빌드 및 SDK 초기화 (신규 방식)
    single<Unit>(createdAtStart = true) {
        val executor = get<DeployExecutor>()
        val worker = get<com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker>()
        val outboxRepo = get<OutboxRepositoryPort>()
        val appConfig = get<AppConfig>()

        // IvmContext로 모든 의존성 통합
        val context = IvmContext.builder()
            .executor(executor)
            .worker(worker)
            .outboxRepository(outboxRepo)
            .kafkaConfig(appConfig.kafka)
            .workerConfig(appConfig.worker)
            .build()

        // SDK 초기화
        Ivm.initialize(context)
    }
}
