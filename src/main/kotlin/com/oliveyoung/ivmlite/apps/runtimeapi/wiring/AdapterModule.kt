package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.pkg.changeset.adapters.InMemoryChangeSetRepository
import com.oliveyoung.ivmlite.pkg.changeset.ports.ChangeSetRepositoryPort
import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.GatedContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.DefaultContractStatusGate
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.DynamoDbRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.InMemoryRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqOutboxRepository
import com.oliveyoung.ivmlite.pkg.rawdata.adapters.JooqRawDataRepository
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.DynamoDbSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemoryInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqSliceRepository
import com.oliveyoung.ivmlite.pkg.slices.adapters.JooqInvertedIndexRepository
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.pkg.slices.ports.InvertedIndexRepositoryPort
import com.oliveyoung.ivmlite.shared.adapters.InMemoryContractCache
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.ports.ContractCachePort
import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.opentelemetry.api.trace.Tracer
import org.jooq.DSLContext
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Adapter Module (RFC-IMPL-009)
 * 
 * Port → Adapter 바인딩.
 * v1: InMemory/LocalYaml 어댑터 (개발/테스트)
 * v2: jOOQ/DynamoDB 어댑터로 교체 가능 (DI만 변경)
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

    // RawData Repository (v1: InMemory, v2: jOOQ)
    single { InMemoryRawDataRepository() } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (v1: InMemory, v2: jOOQ)
    single { InMemorySliceRepository() } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (v1: InMemory, v2: jOOQ)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryInvertedIndexRepository() } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // Outbox Repository (v1: InMemory Polling, v2: jOOQ + Debezium)
    single { InMemoryOutboxRepository() } binds arrayOf(OutboxRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (v1.1: InMemory, v2: jOOQ)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)
}

/**
 * Production Adapter Module (jOOQ 기반)
 *
 * PostgreSQL 연결 시 사용. infraModule과 함께 로드해야 함.
 * DSLContext는 infraModule에서 제공.
 */
val jooqAdapterModule = module {

    // Contract Registry (v1: LocalYaml + StatusGate, v2: DynamoDB)
    single {
        val config: AppConfig = get()
        GatedContractRegistryAdapter(
            delegate = LocalYamlContractRegistryAdapter(config.contracts.resourcePath),
            statusGate = DefaultContractStatusGate,
        )
    } binds arrayOf(ContractRegistryPort::class, HealthCheckable::class)

    // RawData Repository (jOOQ)
    single { JooqRawDataRepository(get<DSLContext>()) } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (jOOQ)
    single { JooqSliceRepository(get<DSLContext>()) } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (jOOQ - RFC-IMPL-010 GAP-E)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { JooqInvertedIndexRepository(get<DSLContext>()) } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // Outbox Repository (jOOQ + Polling)
    single { JooqOutboxRepository(get<DSLContext>()) } binds arrayOf(OutboxRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (v1.1: InMemory, v2: jOOQ)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)
}

/**
 * DynamoDB Adapter Module (RFC-IMPL Phase B-5)
 *
 * 운영 환경에서 사용할 DynamoDB 기반 어댑터.
 * jooqAdapterModule + dynamodbContractModule 조합으로 사용.
 *
 * 사용법:
 * - infraModule (DynamoDbAsyncClient 제공)
 * - jooqAdapterModule (PostgreSQL 리포지토리)
 * - dynamodbContractModule (DynamoDB ContractRegistry)
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
 * Full Production Adapter Module (DynamoDB + PostgreSQL Outbox)
 *
 * 운영 환경용:
 * - DynamoDB: RawData, Slice, InvertedIndex
 * - PostgreSQL: Outbox (트랜잭션 보장)
 * - DynamoDB: Contract Registry
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
            tableName = "ivm-lite-data-${config.dynamodb.tableName.substringAfterLast("-")}"
        )
    } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (DynamoDB)
    single {
        val config: AppConfig = get()
        DynamoDbSliceRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = "ivm-lite-data-${config.dynamodb.tableName.substringAfterLast("-")}"
        )
    } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (DynamoDB)
    single {
        val config: AppConfig = get()
        DynamoDbInvertedIndexRepository(
            dynamoClient = get<DynamoDbAsyncClient>(),
            tableName = "ivm-lite-data-${config.dynamodb.tableName.substringAfterLast("-")}"
        )
    } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // Outbox Repository (PostgreSQL - 트랜잭션 보장)
    single { JooqOutboxRepository(get<DSLContext>()) } binds arrayOf(OutboxRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (InMemory for now)
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)

    // RawData Repository (jOOQ)
    single { JooqRawDataRepository(get<DSLContext>()) } binds arrayOf(RawDataRepositoryPort::class, HealthCheckable::class)

    // Slice Repository (jOOQ)
    single { JooqSliceRepository(get<DSLContext>()) } binds arrayOf(SliceRepositoryPort::class, HealthCheckable::class)

    // InvertedIndex Repository (jOOQ - RFC-IMPL-010 GAP-E)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { JooqInvertedIndexRepository(get<DSLContext>()) } binds arrayOf(InvertedIndexRepositoryPort::class, HealthCheckable::class)

    // Outbox Repository (jOOQ + Polling)
    single { JooqOutboxRepository(get<DSLContext>()) } binds arrayOf(OutboxRepositoryPort::class, HealthCheckable::class)

    // ChangeSet Repository (v1.1: InMemory, v2: jOOQ)
    // RFC-IMPL-010 GAP-G: HealthCheckable 바인딩 추가
    single { InMemoryChangeSetRepository() } binds arrayOf(ChangeSetRepositoryPort::class, HealthCheckable::class)
}
