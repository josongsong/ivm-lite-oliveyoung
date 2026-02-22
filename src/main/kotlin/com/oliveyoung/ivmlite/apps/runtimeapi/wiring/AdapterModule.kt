package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultChangeSetBuilderAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.DefaultImpactCalculatorAdapter
import com.oliveyoung.ivmlite.pkg.changeset.adapters.InMemoryChangeSetRepository
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ImpactCalculator
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetBuilderPort
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetRepositoryPort
import com.oliveyoung.ivmlite.pkg.changeset.ports.ImpactCalculatorPort
import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.DynamoDbRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDbSinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.DynamoDBSinkRuleRegistryAdapter
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.adapters.LocalYamlSinkRuleRegistryAdapter
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkRuleRegistryPort
import com.oliveyoung.ivmlite.pkg.slices.adapters.DefaultSlicingEngineAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.JoinExecutor
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.SlicingEnginePort
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.InMemoryContractCache
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.ports.ContractCachePort
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module


/**
 * Domain Service Module (RFC-IMPL-010)
 *
 * 도메인 서비스 Port/Adapter 바인딩.
 * 모든 환경(InMemory, Exposed, DynamoDB)에서 공통 사용.
 * SOLID DIP 준수: Domain → Port ← Adapter
 */
val domainServiceModule = module {

    // JoinExecutor (SlicingEngine 의존성)
    single { JoinExecutor(rawRepo = get()) }

    // SlicingEngine → SlicingEnginePort
    single {
        SlicingEngine(
            contractRegistry = get(),
            joinExecutor = get(),
        )
    }
    single<SlicingEnginePort> {
        DefaultSlicingEngineAdapter(delegate = get<SlicingEngine>())
    }

    // ChangeSetBuilder → ChangeSetBuilderPort
    single { ChangeSetBuilder() }
    single<ChangeSetBuilderPort> {
        DefaultChangeSetBuilderAdapter(delegate = get<ChangeSetBuilder>())
    }

    // ImpactCalculator → ImpactCalculatorPort
    single { ImpactCalculator() }
    single<ImpactCalculatorPort> {
        DefaultImpactCalculatorAdapter(delegate = get<ImpactCalculator>())
    }
}

/**
 * Adapter Module (RFC-IMPL-009)
 *
 * Port → Adapter 바인딩.
 * v1: InMemory/LocalYaml 어댑터 (개발/테스트)
 * v2: Exposed/DynamoDB 어댑터로 교체 가능 (DI만 변경)
 *
 * NOTE: domainServiceModule을 함께 로드해야 함
 */
val adapterModule = module {

    // Contract Registry (v1: LocalYaml + StatusGate)
    single {
        val config: AppConfig = get()
        GatedContractRegistryAdapter(
            delegate = LocalYamlContractRegistryAdapter(config.contracts.resourcePath),
            statusGate = DefaultContractStatusGate,
        )
    } binds arrayOf(ContractRegistryPort::class, HealthCheckable::class)

    // RawData Repository (v1: InMemory, v2: Exposed)
    single { InMemoryRawDataRepository() } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (v1: InMemory, v2: Exposed)
    single { InMemorySliceRepository() } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (v1: InMemory, v2: Exposed)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryInvertedIndexRepository() } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (v1.1: InMemory, v2: Exposed)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)

    // SinkEvent Repository (v1: InMemory, v2: DynamoDB Streams)
    single { InMemorySinkEventRepository() } bind SinkEventRepositoryPort::class

    // SinkRule Registry (v1: LocalYaml - RFC-022 Phase 2)
    single<SinkRuleRegistryPort> {
        val config: AppConfig = get()
        LocalYamlSinkRuleRegistryAdapter(config.contracts.resourcePath)
    }
}

/**
 * DynamoDB Contract Module (RFC-IMPL Phase B-5)
 *
 * productionAdapterModule과 함께 사용 (Contract Registry만 DynamoDB).
 */
val dynamodbContractModule = module {
    // Contract Cache (RFC-IMPL-010 Phase C-1)
    single<ContractCachePort> {
        val config: AppConfig = get()
        InMemoryContractCache(config.cache)
    }

    single {
        val config: AppConfig = get()
        GatedContractRegistryAdapter(
            delegate = DynamoDBContractRegistryAdapter(
                dynamoClient = get<DynamoDbAsyncClient>(),
                tableName = config.dynamodb.tableName,
                cache = get<ContractCachePort>(),
                tracer = get<Tracer>(),
            ),
            statusGate = DefaultContractStatusGate,
        )
    } binds arrayOf(ContractRegistryPort::class, HealthCheckable::class)
}

/**
 * Full Production Adapter Module (DynamoDB 기반)
 *
 * 운영 환경용 (DynamoDB 중심):
 * - DynamoDB: RawData, Slice, InvertedIndex, Contract Registry, SinkEvent
 * - PostgreSQL: alerts, backfill_jobs, TransactionPort (ChangeSet/View 저장 없음)
 *
 * NOTE: SinkEvent는 DynamoDB Streams → Lambda로 처리 (PostgreSQL Outbox 제거됨)
 */
val productionAdapterModule = module {

    // Contract Cache (RFC-IMPL-010 Phase C-1)
    single<ContractCachePort> {
        val config: AppConfig = get()
        InMemoryContractCache(config.cache)
    }

    // Contract Registry (DynamoDB + StatusGate + Cache)
    single {
        val config: AppConfig = get()
        GatedContractRegistryAdapter(
            delegate = DynamoDBContractRegistryAdapter(
                dynamoClient = get<DynamoDbAsyncClient>(),
                tableName = config.dynamodb.tableName,
                cache = get<ContractCachePort>(),
                tracer = get<Tracer>(),
            ),
            statusGate = DefaultContractStatusGate,
        )
    } binds arrayOf(ContractRegistryPort::class, HealthCheckable::class)

    // RawData Repository (DynamoDB)
    single {
        val config: AppConfig = get()
        DynamoDbRawDataRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.dataTableName
        )
    } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (DynamoDB)
    single {
        val config: AppConfig = get()
        DynamoDbSliceRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.dataTableName
        )
    } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (DynamoDB)
    single {
        val config: AppConfig = get()
        DynamoDbInvertedIndexRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.dataTableName
        )
    } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (InMemory - 저장 미사용, HealthCheck만)
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)

    // SinkEvent Repository (DynamoDB Streams 기반)
    single<SinkEventRepositoryPort> {
        val config: AppConfig = get()
        DynamoDbSinkEventRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.sinkEventsTableName
        )
    }

    // SinkRule Registry (RFC-022 Phase 2: DynamoDB)
    single<SinkRuleRegistryPort> {
        val config: AppConfig = get()
        DynamoDBSinkRuleRegistryAdapter(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = config.dynamodb.tableName
        )
    }
}
