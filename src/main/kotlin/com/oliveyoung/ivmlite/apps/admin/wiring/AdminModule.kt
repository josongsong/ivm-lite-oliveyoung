package com.oliveyoung.ivmlite.apps.admin.wiring

import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.domainServiceModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.infraModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.productionAdapterModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.tracingModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.viewModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.workflowModule

// Alerts
import com.oliveyoung.ivmlite.pkg.alerts.adapters.DefaultAlertRuleLoader
import com.oliveyoung.ivmlite.pkg.alerts.adapters.ExposedAlertRepository
import com.oliveyoung.ivmlite.pkg.alerts.adapters.SlackNotifier
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngine
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngineConfig
import com.oliveyoung.ivmlite.pkg.alerts.application.MetricCollector
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRuleLoaderPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.NotifierPort

// Backfill
import com.oliveyoung.ivmlite.pkg.backfill.adapters.DefaultBackfillExecutor
import com.oliveyoung.ivmlite.pkg.backfill.adapters.ExposedBackfillJobRepository
import com.oliveyoung.ivmlite.pkg.backfill.application.BackfillService
import com.oliveyoung.ivmlite.pkg.backfill.application.BackfillServiceConfig
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillExecutorPort
import com.oliveyoung.ivmlite.pkg.backfill.ports.BackfillJobRepositoryPort

// Health
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.pkg.health.adapters.PostgresHealthCheck
import com.oliveyoung.ivmlite.pkg.health.application.HealthService
import com.oliveyoung.ivmlite.pkg.health.ports.HealthCheckPort

// Observability
import com.oliveyoung.ivmlite.pkg.observability.adapters.PipelineMetricsCollector
import com.oliveyoung.ivmlite.pkg.observability.application.ObservabilityService
import com.oliveyoung.ivmlite.pkg.observability.ports.MetricsCollectorPort

import com.oliveyoung.ivmlite.apps.admin.adapters.DynamoDbExplorerAdapter
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.ConfigLoader
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

// Workflow Canvas (RFC-IMPL-015)
import com.oliveyoung.ivmlite.pkg.workflow.canvas.adapters.WorkflowGraphBuilder
import com.oliveyoung.ivmlite.pkg.workflow.canvas.ports.WorkflowGraphBuilderPort
import com.oliveyoung.ivmlite.pkg.workflow.canvas.application.WorkflowCanvasService

// Admin Services (SOTA Refactoring)
import com.oliveyoung.ivmlite.apps.admin.application.AdminDashboardService
import com.oliveyoung.ivmlite.apps.admin.application.AdminPipelineService
import com.oliveyoung.ivmlite.apps.admin.application.AdminContractService
import com.oliveyoung.ivmlite.apps.admin.application.ContractGraphService
import com.oliveyoung.ivmlite.apps.admin.application.ContractValidationService
import com.oliveyoung.ivmlite.apps.admin.application.ContractCursorService
import com.oliveyoung.ivmlite.apps.admin.application.WhyEngineService
import com.oliveyoung.ivmlite.apps.admin.application.SemanticDiffService
import com.oliveyoung.ivmlite.apps.admin.application.PlaygroundService
import com.oliveyoung.ivmlite.apps.admin.application.GitOutputService
import com.oliveyoung.ivmlite.apps.admin.application.ExplorerService
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow

import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Admin App Module
 *
 * Admin 앱 전용 DI 모듈.
 * runtimeapi와 독립적으로 동작하도록 별도 모듈 구성.
 *
 * Admin 앱은 모니터링/관리 전용.
 */
val adminAppModule = module {
    // Config (Hoplite)
    single<AppConfig> { ConfigLoader.load() }

    // Admin Services (SOTA Refactoring)
    single {
        AdminDashboardService(
            sinkEventRepo = get<SinkEventRepositoryPort>(),
            explorerRepo = get<ExplorerRepositoryPort>()
        )
    }

    single {
        AdminPipelineService(
            contractRegistry = getOrNull<ContractRegistryPort>(),
            explorerRepo = get<ExplorerRepositoryPort>(),
            sinkEventRepo = getOrNull<SinkEventRepositoryPort>()
        )
    }

    // ExplorerRepositoryPort (DynamoDB - productionAdapterModule과 함께 사용)
    single<ExplorerRepositoryPort> {
        val config: AppConfig = get()
        DynamoDbExplorerAdapter(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.dataTableName
        )
    }

    single {
        AdminContractService(
            contractRegistry = get(),
            sinkRuleRegistry = get()
        )
    }

    // Contract Graph / Playground (RFC-022: ContractRegistryPort 기반)
    single {
        ContractGraphService(
            contractRegistry = get(),
            sinkRuleRegistry = get()
        )
    }
    single { ContractValidationService(get()) }
    single { ContractCursorService(get()) }
    single { WhyEngineService(get()) }
    single { SemanticDiffService(get()) }
    single { GitOutputService(get()) }
    single {
        PlaygroundService(
            contractRegistry = get(),
            contractService = get(),
            rawDataRepo = get()
        )
    }

    // ExplorerService - Data Explorer 기능 (DynamoDB via ExplorerRepositoryPort)
    single {
        ExplorerService(
            rawDataRepo = get<RawDataRepositoryPort>(),
            sliceRepo = get<SliceRepositoryPort>(),
            explorerRepo = get<ExplorerRepositoryPort>(),
            queryViewWorkflow = getOrNull<QueryViewWorkflow>(),
            contractRegistry = getOrNull<ContractRegistryPort>(),
            slicingWorkflow = getOrNull<SlicingWorkflow>()
        )
    }
}

/**
 * Alerts 도메인 모듈
 */
val alertsModule = module {
    // Repository (Exposed - PostgreSQL)
    single<AlertRepositoryPort> { ExposedAlertRepository(get<Database>()) }

    // Rule Loader
    single<AlertRuleLoaderPort> { DefaultAlertRuleLoader() }

    // Notifiers (named으로 구분 - List<HealthCheckPort>와 타입 소거 충돌 방지)
    single<List<NotifierPort>>(named("alertNotifiers")) {
        val config = get<AppConfig>()
        listOf(
            SlackNotifier(config.admin?.slackWebhookUrl)
        )
    }

    // Metric Collector
    single {
        MetricCollector(
            database = get<Database>(),
            sinkEventRepo = getOrNull<SinkEventRepositoryPort>()
        )
    }

    // Alert Engine
    single {
        AlertEngine(
            metricCollector = get(),
            ruleLoader = get(),
            alertRepository = get(),
            notifiers = get(named("alertNotifiers")),
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
    // Repository (Exposed - PostgreSQL)
    single<BackfillJobRepositoryPort> { ExposedBackfillJobRepository(get<Database>()) }

    // Executor
    single<BackfillExecutorPort> {
        DefaultBackfillExecutor(
            explorerRepo = get<ExplorerRepositoryPort>(),
            rawDataRepo = get<RawDataRepositoryPort>(),
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
    // Health Checks (named으로 구분 - List 타입 소거 충돌 방지)
    single<List<HealthCheckPort>>(named("healthChecks")) {
        listOf(
            PostgresHealthCheck(get<Database>())
        )
    }

    // Health Service
    single {
        HealthService(
            healthChecks = get(named("healthChecks"))
        )
    }
}

/**
 * Observability 도메인 모듈
 */
val observabilityModule = module {
    // Metrics Collector (SinkEvent 기반)
    single<MetricsCollectorPort> {
        PipelineMetricsCollector(
            sinkEventRepo = getOrNull<SinkEventRepositoryPort>()
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
    // Graph Builder (RFC-022: ContractRegistryPort + SinkRuleRegistryPort 기반)
    single<WorkflowGraphBuilderPort> {
        WorkflowGraphBuilder(
            contractRegistry = get(),
            sinkRuleRegistry = get()
        )
    }

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
    productionAdapterModule,  // DynamoDB: RawData, Slice, InvertedIndex, Contract, SinkEvent / PostgreSQL: ChangeSet, View
    domainServiceModule,  // SlicingEnginePort, ChangeSetBuilderPort, ImpactCalculatorPort
    viewModule,  // ViewComposer (IngestionWorkflow 의존성)
    workflowModule,
    // 새로운 Admin 전용 모듈들
    alertsModule,
    backfillModule,
    healthModule,
    observabilityModule,
    workflowCanvasModule,  // RFC-IMPL-015: Workflow Canvas
    // sdkModule 제외 - Admin에서 불필요
)
