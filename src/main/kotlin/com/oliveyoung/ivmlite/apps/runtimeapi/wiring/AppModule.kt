package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.ConfigLoader
import org.koin.dsl.module

/**
 * App Module (RFC-IMPL-009)
 *
 * 모든 DI 모듈을 조합하는 최상위 모듈.
 * wiring 위치: apps/runtimeapi/wiring/ (RFC-IMPL-009 P0)
 */
val appModule = module {
    // Config (Hoplite)
    single<AppConfig> { ConfigLoader.load() }
}

/**
 * 모든 모듈 조합 (v1: InMemory 어댑터)
 */
val allModules = listOf(
    appModule,
    tracingModule,
    metricsModule,
    adapterModule,
    domainServiceModule,  // Domain Service Port 바인딩 (공통)
    viewModule,  // ViewComposer (Sink Dispatch는 Lambda 전용)
    sinkPluginRegistryModule,  // Sink Preflight (Ingest 시점 검증)
    workflowModule,
    sdkModule,
)

/**
 * Production 모듈 조합 (DynamoDB 중심)
 *
 * RawData/Slice/InvertedIndex/SinkEvent는 DynamoDB, ChangeSet/View는 PostgreSQL.
 * - DynamoDB: RawData, Slice, InvertedIndex, SinkEvent, Contract Registry
 * - PostgreSQL: ChangeSet, View
 */
val productionModules = listOf(
    appModule,
    tracingModule,
    metricsModule,
    infraModule,
    productionAdapterModule,
    domainServiceModule,  // Domain Service Port 바인딩 (공통)
    viewModule,  // ViewComposer (Sink Dispatch는 Lambda 전용)
    sinkPluginRegistryModule,  // Sink Preflight (Ingest 시점 검증)
    workflowModule,
    sdkModule,
)
