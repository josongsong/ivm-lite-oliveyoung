package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipEventHandler
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkRuleRegistry
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.shared.config.AppConfig
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module

/**
 * Worker Module (RFC-IMPL Phase B-2 + RFC-IMPL-013)
 *
 * OutboxPollingWorker DI 바인딩.
 * 
 * RFC-IMPL-013:
 * - ShipEventHandler 주입 → ShipRequested 처리
 * - SinkRuleRegistry 주입 → Slicing 후 자동 Ship 생성
 *
 * Lifecycle: Application scope (singleton)
 */
val workerModule = module {

    // SinkRuleRegistry (개발용: InMemory)
    // TODO: Production에서는 ContractRegistrySinkRuleAdapter 사용
    single<SinkRuleRegistryPort> {
        InMemorySinkRuleRegistry()
    }

    // ShipEventHandler (ShipRequested outbox 처리)
    single {
        ShipEventHandler(
            shipWorkflow = get<ShipWorkflow>(),
            slicingWorkflow = get<SlicingWorkflow>(),
            deployJobRepository = null  // TODO: 필요시 주입
        )
    }

    single {
        OutboxPollingWorker(
            outboxRepo = get<OutboxRepositoryPort>(),
            slicingWorkflow = get<SlicingWorkflow>(),
            config = get<AppConfig>().worker,
            eventHandler = get<ShipEventHandler>(),  // RFC-IMPL-013: Ship 처리
            tracer = get<Tracer>(),
            sinkRuleRegistry = get<SinkRuleRegistryPort>(),  // RFC-IMPL-013: 자동 Ship
        )
    }
}
