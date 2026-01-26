package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.config.AppConfig
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module

/**
 * Worker Module (RFC-IMPL Phase B-2)
 *
 * OutboxPollingWorker DI 바인딩.
 * Lifecycle: Application scope (singleton)
 */
val workerModule = module {

    single {
        OutboxPollingWorker(
            outboxRepo = get<OutboxRepositoryPort>(),
            slicingWorkflow = get<SlicingWorkflow>(),
            config = get<AppConfig>().worker,
            tracer = get<Tracer>(),
        )
    }
}
