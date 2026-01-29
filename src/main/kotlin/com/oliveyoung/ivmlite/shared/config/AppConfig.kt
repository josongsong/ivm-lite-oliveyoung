package com.oliveyoung.ivmlite.shared.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.sources.SystemPropertiesPropertySource

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
    val admin: AdminConfig? = null,
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
    // Remote-only: 기본 로컬 테이블명 제거 (ConfigValidator에서 non-blank 강제)
    val tableName: String = "",
    /** AWS Access Key ID (환경 변수 AWS_ACCESS_KEY_ID 우선) */
    val accessKeyId: String? = null,
    /** AWS Secret Access Key (환경 변수 AWS_SECRET_ACCESS_KEY 우선) */
    val secretAccessKey: String? = null,
)

data class KafkaConfig(
    val bootstrapServers: String = "localhost:9094",
    val consumerGroup: String = "ivm-lite",
    /**
     * Kafka 토픽 prefix (RFC-IMPL-008)
     * 
     * Outbox 이벤트가 발행되는 토픽명 패턴: {topicPrefix}.events.{aggregatetype}
     * 
     * 예시:
     * - topicPrefix = "ivm" → 토픽: ivm.events.raw_data, ivm.events.slice
     * - topicPrefix = "oliveyoung" → 토픽: oliveyoung.events.raw_data
     * 
     * 기본값: "ivm"
     */
    val topicPrefix: String = "ivm",
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
 * 
 * Kafka Consumer와 PostgreSQL Polling 모두에서 동일한 토픽 패턴 지원.
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
    /**
     * 처리할 AggregateType 목록 (RFC-IMPL-013)
     * 
     * null이면 모든 타입 처리 (기존 동작)
     * 특정 타입만 지정하면 해당 타입만 조회 → Worker 분리 가능
     * 
     * 예시:
     * - [RAW_DATA] → Slicing 전용 Worker
     * - [SLICE] → Ship 전용 Worker
     * 
     * 토픽으로 설정하려면 topics 사용 권장
     */
    val aggregateTypes: List<String>? = null,
    /**
     * 처리할 토픽 목록 (Kafka/Polling 통합)
     * 
     * null이면 모든 토픽 처리 (기존 동작)
     * 특정 토픽만 지정하면 해당 토픽만 조회
     * 
     * 토픽명 패턴: {topicPrefix}.events.{suffix}
     * 
     * 예시:
     * - ["ivm.events.raw_data"] → Slicing 전용 Worker
     * - ["ivm.events.slice"] → Ship 전용 Worker
     * - ["ivm.events.raw_data", "ivm.events.slice"] → 복합 Worker
     */
    val topics: List<String>? = null,
) {
    /**
     * 실제 처리할 AggregateType 목록
     * 
     * topics가 설정되면 topics에서 추론, 없으면 aggregateTypes 사용
     */
    fun resolvedAggregateTypes(): List<com.oliveyoung.ivmlite.shared.domain.types.AggregateType>? {
        // topics가 설정되면 topics에서 AggregateType 추론
        if (!topics.isNullOrEmpty()) {
            return topics.mapNotNull { topicName ->
                com.oliveyoung.ivmlite.shared.domain.types.Topic.toAggregateType(topicName)
            }.ifEmpty { null }
        }
        // 기존 aggregateTypes 사용
        return aggregateTypes?.mapNotNull { 
            try { com.oliveyoung.ivmlite.shared.domain.types.AggregateType.valueOf(it) } 
            catch (e: Exception) { null }
        }?.ifEmpty { null }
    }
}

/**
 * Admin Configuration (알림/모니터링)
 */
data class AdminConfig(
    /** Slack Webhook URL (알림 발송용) */
    val slackWebhookUrl: String? = null,
    
    /** 알림 평가 주기 (ms) */
    val alertIntervalMs: Long = 10_000,
    
    /** Backfill 최대 동시 실행 수 */
    val maxConcurrentBackfills: Int = 3,
    
    /** Health Check 타임아웃 (ms) */
    val healthCheckTimeoutMs: Long = 5000,
)

/**
 * Config Loader (Singleton)
 * SOTA급: 설정 로드 + 검증 통합
 *
 * .env 지원: DotenvLoader.load()를 먼저 호출해야 함 (Application.kt에서 처리)
 */
object ConfigLoader {

    /**
     * Load config from application.yaml + environment variables
     * RFC-IMPL-009: unknown key = fail-closed
     * SOTA: 자동 검증 포함
     *
     * 우선순위: System Property (.env) > 환경변수 > YAML
     * NOTE: DotenvLoader가 .env를 System Property로 설정하므로 .env 값이 우선됨
     */
    fun load(): AppConfig {
        val config: AppConfig = ConfigLoaderBuilder.default()
            // System Property를 환경변수보다 우선 (우선순위 높은 게 나중에 추가)
            .addPropertySource(SystemPropertiesPropertySource())
            .addResourceSource("/application.yaml")
            .strict()  // unknown key → fail
            .build()
            .loadConfigOrThrow()

        // SOTA급 설정 검증
        ConfigValidator.validate(config)

        return config
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
