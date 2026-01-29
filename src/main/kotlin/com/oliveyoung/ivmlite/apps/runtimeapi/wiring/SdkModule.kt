package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import com.oliveyoung.ivmlite.shared.config.AppConfig
import org.koin.dsl.module

/**
 * SDK Module (RFC-IMPL-011 Wave 5-L)
 *
 * SDK Layer DI 바인딩
 * - DeployExecutor: Deploy 실행 엔진
 * - OutboxPollingWorker: SDK에서 Worker 제어용
 * - OutboxRepository: SDK에서 Consume API 사용
 * - Config: Kafka/Worker 설정
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
    
    // SDK 초기화 (Worker, OutboxRepo, Config 주입)
    // Note: Unit 반환하여 OutboxPollingWorker와 순환 참조 방지
    single<Unit>(createdAtStart = true) {
        val worker = get<com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker>()
        val outboxRepo = get<OutboxRepositoryPort>()
        val appConfig = get<AppConfig>()

        // SDK에 주입
        com.oliveyoung.ivmlite.sdk.Ivm.setWorker(worker)
        com.oliveyoung.ivmlite.sdk.Ivm.setOutboxRepository(outboxRepo)
        com.oliveyoung.ivmlite.sdk.Ivm.setConfigs(appConfig.kafka, appConfig.worker)
    }
}
