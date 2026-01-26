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
    adapterModule,
    workflowModule,
    workerModule,
    sdkModule,
)

/**
 * Production 모듈 조합 (v2: jOOQ 기반)
 *
 * PostgreSQL + jOOQ 어댑터 사용. infraModule에서 DSLContext 제공.
 * DynamoDB 어댑터는 RFC-IMPL-007에서 추가 예정.
 */
val productionModules = listOf(
    appModule,
    tracingModule,
    infraModule,
    jooqAdapterModule,
    workflowModule,
    workerModule,
    sdkModule,
)
