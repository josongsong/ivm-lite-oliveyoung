package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetBuilderPort
import com.oliveyoung.ivmlite.pkg.changeset.ports.ImpactCalculatorPort
import com.oliveyoung.ivmlite.pkg.fanout.adapters.SlicingWorkflowFanoutAdapter
import com.oliveyoung.ivmlite.pkg.fanout.application.FanoutWorkflow
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.ports.FanoutSlicingPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.application.IngestionOrchestrator
import com.oliveyoung.ivmlite.pkg.rawdata.domain.IngestionWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPreflightPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.shared.adapters.ExposedTransactionAdapter
import com.oliveyoung.ivmlite.shared.ports.TransactionPort
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

/**
 * Workflow Module (SOTA - DynamoDB Streams)
 *
 * Runtime API + Admin 공유 Workflow 바인딩.
 * 제거된 레거시: RawDataIngestionService, TransactionManager,
 * DeployJobRepository, DeployPlanRepository, EventHandler
 * Ship은 DynamoDB Streams → Lambda (SinkStreamHandler)가 자동 처리
 */
val workflowModule = module {

    // ===== Infrastructure =====

    // TransactionPort (IngestionOrchestrator에서 사용)
    single<TransactionPort> {
        ExposedTransactionAdapter(database = get<Database>())
    }

    // ===== Domain Layer =====

    // IngestionWorkflow (순수 비즈니스 로직: RawData → Slice → View)
    // View는 SinkEvent payload로 전달, 별도 저장 없음
    single {
        IngestionWorkflow(
            rawDataRepo = get(),
            sliceRepo = get(),
            slicingEngine = get<SlicingEnginePort>(),
            viewComposer = get()
        )
    }

    // ===== Application Layer =====

    // IngestionOrchestrator (트랜잭션 + SinkEvent 발행 또는 inProcessSink 시 SinkPlugin 직접 호출)
    // SOTA: RawData → Slicing → View → SinkEvent (단일 트랜잭션)
    single {
        IngestionOrchestrator(
            workflow = get(),
            sinkEventRepo = get(),
            transactionPort = get(),
            sinkRuleRegistry = get<SinkRuleRegistryPort>(),
            sinkPreflight = get<SinkPreflightPort>(),
            pluginRegistry = getOrNull<SinkPluginRegistryPort>(),
        )
    }

    // SlicingWorkflow (RFC-IMPL-004, /api/v1/slice 엔드포인트)
    // Contract is Law: EntityContractResolver로 entityType별 RuleSet 동적 해석
    single {
        SlicingWorkflow(
            rawRepo = get(),
            sliceRepo = get(),
            slicingEngine = get<SlicingEnginePort>(),
            invertedIndexRepo = get(),
            changeSetBuilder = get<ChangeSetBuilderPort>(),
            impactCalculator = get<ImpactCalculatorPort>(),
            contractRegistry = get(),
            contractResolver = get(),
            tracer = get<Tracer>(),
        )
    }

    // QueryViewWorkflow (RFC-IMPL-005, /api/v1/query 엔드포인트)
    single {
        QueryViewWorkflow(
            sliceRepo = get(),
            contractRegistry = get(),
            tracer = get<Tracer>(),
        )
    }

    // FanoutSlicingPort (RFC-V4-010: orchestration 직접 호출 방지)
    single<FanoutSlicingPort> {
        SlicingWorkflowFanoutAdapter(slicingWorkflow = get())
    }

    // FanoutWorkflow (RFC-IMPL-012)
    single {
        FanoutWorkflow(
            contractRegistry = get(),
            invertedIndexRepo = get(),
            fanoutSlicing = get(),
            config = FanoutConfig.DEFAULT,
            tracer = get<Tracer>(),
            contractResolver = get(),
        )
    }
}
