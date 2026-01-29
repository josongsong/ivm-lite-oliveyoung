package com.oliveyoung.ivmlite.apps.admin.wiring

import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.adapterModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.infraModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.jooqAdapterModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.tracingModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.workflowModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.workerModule

// Alerts
import com.oliveyoung.ivmlite.pkg.alerts.adapters.DefaultAlertRuleLoader
import com.oliveyoung.ivmlite.pkg.alerts.adapters.InMemoryAlertRepository
import com.oliveyoung.ivmlite.pkg.alerts.adapters.SlackNotifier
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngine
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngineConfig
import com.oliveyoung.ivmlite.pkg.alerts.application.MetricCollector
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.NotifierPort

// Backfill
import com.oliveyoung.ivmlite.pkg.backfill.adapters.DefaultBackfillExecutor
import com.oliveyoung.ivmlite.pkg.backfill.adapters.InMemoryBackfillJobRepository
import com.oliveyoung.ivmlite.pkg.backfill.application.BackfillService
import com.oliveyoung.ivmlite.pkg.backfill.application.BackfillServiceConfig
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillExecutorPort
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillJobRepositoryPort

// Health
import com.oliveyoung.ivmlite.pkg.health.adapters.OutboxHealthCheck
import com.oliveyoung.ivmlite.pkg.health.adapters.PostgresHealthCheck
import com.oliveyoung.ivmlite.pkg.health.adapters.WorkerHealthCheck
import com.oliveyoung.ivmlite.pkg.health.application.HealthService
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort

// Observability
import com.oliveyoung.ivmlite.pkg.observability.adapters.PipelineMetricsCollector
import com.oliveyoung.ivmlite.pkg.observability.application.ObservabilityService
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.ConfigLoader

// Workflow Canvas (RFC-IMPL-015)
import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.ports.WorkflowGraphBuilderPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.WorkflowCanvasService

import org.jooq.DSLContext
import org.koin.dsl.module

/**
 * Admin App Module
 *
 * Admin 앱 전용 DI 모듈.
 * runtimeapi와 독립적으로 동작하도록 별도 모듈 구성.
 *
 * ⚠️ 주의: Admin 앱은 모니터링/관리 전용이므로 Worker를 시작하지 않습니다.
 *          Worker는 runtimeapi에서만 실행됩니다.
 */
val adminAppModule = module {
    // Config (Hoplite)
    single<AppConfig> { ConfigLoader.load() }
}

/**
 * Alerts 도메인 모듈
 */
val alertsModule = module {
    // Repository (In-Memory for now, can be replaced with JOOQ adapter)
    single<AlertRepositoryPort> { InMemoryAlertRepository() }

    // Rule Loader
    single<AlertRuleLoaderPort> { DefaultAlertRuleLoader() }

    // Notifiers
    single<List<NotifierPort>> {
        val config = get<AppConfig>()
        listOf(
            SlackNotifier(config.admin?.slackWebhookUrl)
            // Add more notifiers here: EmailNotifier, WebhookNotifier, etc.
        )
    }

    // Metric Collector
    single {
        MetricCollector(
            dsl = get<DSLContext>(),
            worker = getOrNull<OutboxPollingWorker>(),
            outboxRepo = getOrNull<OutboxRepositoryPort>()
        )
    }

    // Alert Engine
    single {
        AlertEngine(
            metricCollector = get(),
            ruleLoader = get(),
            alertRepository = get(),
            notifiers = get(),
            config = AlertEngineConfig(
                evaluationIntervalMs = 10_000  // 10초마다 평가
            )
        )
    }
}

/**
 * Backfill 도메인 모듈
 */
val backfillModule = module {
    // Repository (In-Memory for now)
    single<BackfillJobRepositoryPort> { InMemoryBackfillJobRepository() }

    // Executor
    single<BackfillExecutorPort> {
        DefaultBackfillExecutor(
            dsl = get<DSLContext>(),
            rawDataRepo = get<RawDataRepositoryPort>(),
            outboxRepo = get<OutboxRepositoryPort>(),
            slicingWorkflow = get<SlicingWorkflow>()
        )
    }

    // Service
    single {
        BackfillService(
            jobRepository = get(),
            executor = get(),
            config = BackfillServiceConfig(
                maxConcurrentJobs = 3
            )
        )
    }
}

/**
 * Health 도메인 모듈
 */
val healthModule = module {
    // Health Checks
    single<List<HealthCheckPort>> {
        listOf(
            PostgresHealthCheck(get<DSLContext>()),
            WorkerHealthCheck(getOrNull<OutboxPollingWorker>()),
            OutboxHealthCheck(get<DSLContext>())
        )
    }

    // Health Service
    single {
        HealthService(
            healthChecks = get()
        )
    }
}

/**
 * Observability 도메인 모듈
 */
val observabilityModule = module {
    // Metrics Collector
    single<MetricsCollectorPort> {
        PipelineMetricsCollector(
            dsl = get<DSLContext>(),
            worker = getOrNull<OutboxPollingWorker>()
        )
    }

    // Observability Service
    single {
        ObservabilityService(
            metricsCollector = get()
        )
    }
}

/**
 * Workflow Canvas 도메인 모듈 (RFC-IMPL-015)
 *
 * 데이터 파이프라인 시각화를 위한 모듈.
 * - GraphBuilder: Contract YAML을 분석하여 노드-엣지 그래프 빌드
 * - CanvasService: 실시간 통계 주입 및 그래프 조회
 */
val workflowCanvasModule = module {
    // Graph Builder (Port binding)
    single<WorkflowGraphBuilderPort> { WorkflowGraphBuilder() }

    // Canvas Service
    single {
        WorkflowCanvasService(
            graphBuilder = get(),
            metricsCollector = getOrNull<MetricsCollectorPort>()
        )
    }
}

/**
 * Admin 앱용 모듈 조합
 *
 * productionModules에서 sdkModule 제외
 * (SDK는 Admin에서 불필요 - Admin은 조회/모니터링 전용)
 *
 * 런타임 독립성:
 * - 별도 포트 (8081)
 * - 별도 프로세스
 * - runtimeapi 없이도 실행 가능
 */
val adminAllModules = listOf(
    adminAppModule,
    tracingModule,
    infraModule,
    jooqAdapterModule,
    adapterModule,  // SlicingEnginePort 등 Port 바인딩
    workflowModule,
    workerModule,  // Worker는 주입만 받고 시작 안 함
    // 새로운 Admin 전용 모듈들
    alertsModule,
    backfillModule,
    healthModule,
    observabilityModule,
    workflowCanvasModule,  // RFC-IMPL-015: Workflow Canvas
    // sdkModule 제외 - Admin에서 불필요
)
