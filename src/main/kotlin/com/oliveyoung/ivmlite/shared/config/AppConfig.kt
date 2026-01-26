package com.oliveyoung.ivmlite.shared.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource

/**
 * Application Configuration (RFC-IMPL-009)
 * 
 * Hoplite 기반 타입 안전한 설정.
 * 우선순위: Env > YAML > defaults
 */
data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val dynamodb: DynamoDbConfig,
    val kafka: KafkaConfig,
    val contracts: ContractsConfig,
    val observability: ObservabilityConfig,
    val worker: WorkerConfig = WorkerConfig(),
    val cache: CacheConfig = CacheConfig(),
)

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
)

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2,
)

data class DynamoDbConfig(
    val endpoint: String? = null,
    val region: String = "ap-northeast-2",
    val tableName: String = "ivm-lite-schema-registry-local",
)

data class KafkaConfig(
    val bootstrapServers: String = "localhost:9094",
    val consumerGroup: String = "ivm-lite",
)

data class ContractsConfig(
    val resourcePath: String = "/contracts/v1",
)

data class ObservabilityConfig(
    val metricsEnabled: Boolean = true,
    val tracingEnabled: Boolean = true,
    val otlpEndpoint: String = "http://localhost:4317",
)

/**
 * Cache Configuration (RFC-IMPL-010 Phase C-1)
 *
 * Contract Registry 캐싱 설정.
 * - TTL 기반 자동 만료
 * - LRU eviction
 */
data class CacheConfig(
    /** 캐시 활성화 여부 */
    val enabled: Boolean = true,
    /** TTL (ms) - 기본 5분 */
    val ttlMs: Long = 300_000,
    /** 최대 캐시 항목 수 */
    val maxSize: Int = 1000,
)

/**
 * Worker Configuration (RFC-IMPL Phase B-2)
 *
 * Outbox Polling Worker 설정.
 * Exponential backoff with jitter 지원.
 */
data class WorkerConfig(
    /** Worker 활성화 여부 */
    val enabled: Boolean = true,
    /** Polling 간격 (ms) - 데이터 있을 때 */
    val pollIntervalMs: Long = 100,
    /** Polling 간격 (ms) - 데이터 없을 때 (idle) */
    val idlePollIntervalMs: Long = 1000,
    /** 한 번에 처리할 최대 엔트리 수 */
    val batchSize: Int = 100,
    /** 최대 backoff 시간 (ms) */
    val maxBackoffMs: Long = 30_000,
    /** Backoff multiplier (지수 증가) */
    val backoffMultiplier: Double = 2.0,
    /** Jitter factor (0.0 ~ 1.0) */
    val jitterFactor: Double = 0.1,
    /** Graceful shutdown 대기 시간 (ms) */
    val shutdownTimeoutMs: Long = 10_000,
)

/**
 * Config Loader (Singleton)
 */
object ConfigLoader {
    
    /**
     * Load config from application.yaml + environment variables
     * RFC-IMPL-009: unknown key = fail-closed
     */
    fun load(): AppConfig {
        return ConfigLoaderBuilder.default()
            .addResourceSource("/application.yaml")
            .strict()  // unknown key → fail
            .build()
            .loadConfigOrThrow()
    }
    
    /**
     * Load config for testing (with overrides)
     */
    fun loadForTest(overrides: Map<String, Any> = emptyMap()): AppConfig {
        return ConfigLoaderBuilder.default()
            .addResourceSource("/application.yaml")
            .build()
            .loadConfigOrThrow()
    }
}
