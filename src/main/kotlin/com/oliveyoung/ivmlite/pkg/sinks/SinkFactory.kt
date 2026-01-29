package com.oliveyoung.ivmlite.pkg.sinks

import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkAdapter
import com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchConfig
import com.oliveyoung.ivmlite.pkg.sinks.adapters.OpenSearchSinkAdapter
import com.oliveyoung.ivmlite.pkg.sinks.adapters.PersonalizeConfig
import com.oliveyoung.ivmlite.pkg.sinks.adapters.PersonalizeSinkAdapter
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort
import org.slf4j.LoggerFactory

/**
 * Sink Factory
 * 
 * 설정 기반으로 적절한 SinkPort 구현체를 생성합니다.
 * 이를 통해 애플리케이션 코드가 특정 Sink 구현에 의존하지 않게 됩니다.
 * 
 * 사용법:
 * ```kotlin
 * val sink = SinkFactory.create(SinkType.OPENSEARCH, config)
 * sink.ship(...)
 * ```
 */
object SinkFactory {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /**
     * 환경변수 기반 Sink 생성
     */
    fun createFromEnv(type: SinkType): SinkPort {
        return when (type) {
            SinkType.OPENSEARCH -> createOpenSearchFromEnv()
            SinkType.PERSONALIZE -> createPersonalizeFromEnv()
            SinkType.IN_MEMORY -> InMemorySinkAdapter()
        }
    }
    
    /**
     * 설정 객체 기반 Sink 생성
     */
    fun create(config: SinkConfig): SinkPort {
        return when (config) {
            is SinkConfig.OpenSearch -> OpenSearchSinkAdapter(
                OpenSearchConfig(
                    endpoint = config.endpoint,
                    indexPrefix = config.indexPrefix,
                    username = config.username,
                    password = config.password,
                    timeoutMs = config.timeoutMs
                )
            )
            is SinkConfig.Personalize -> PersonalizeSinkAdapter(
                PersonalizeConfig(
                    datasetArn = config.datasetArn,
                    region = config.region
                )
            )
            is SinkConfig.InMemory -> InMemorySinkAdapter()
        }
    }
    
    /**
     * 모든 Sink Map 생성 (ShipWorkflow용)
     */
    fun createAll(configs: List<SinkConfig>): Map<String, SinkPort> {
        return configs.associate { config ->
            val sinkType = when (config) {
                is SinkConfig.OpenSearch -> "opensearch"
                is SinkConfig.Personalize -> "personalize"
                is SinkConfig.InMemory -> "in-memory"
            }
            sinkType to create(config)
        }
    }
    
    private fun createOpenSearchFromEnv(): SinkPort {
        val endpoint = System.getenv("OPENSEARCH_ENDPOINT") ?: "http://localhost:9200"
        val indexPrefix = System.getenv("OPENSEARCH_INDEX_PREFIX") ?: "ivm"
        val username = System.getenv("OPENSEARCH_USERNAME")
        val password = System.getenv("OPENSEARCH_PASSWORD")
        val timeoutMs = System.getenv("OPENSEARCH_TIMEOUT_MS")?.toLongOrNull() ?: 30_000L
        
        logger.info("Creating OpenSearch sink: endpoint={}, indexPrefix={}", endpoint, indexPrefix)
        
        return create(SinkConfig.OpenSearch(
            endpoint = endpoint,
            indexPrefix = indexPrefix,
            username = username,
            password = password,
            timeoutMs = timeoutMs
        ))
    }
    
    private fun createPersonalizeFromEnv(): SinkPort {
        val datasetArn = System.getenv("PERSONALIZE_DATASET_ARN") ?: ""
        val region = System.getenv("PERSONALIZE_REGION") ?: "ap-northeast-2"
        
        logger.info("Creating Personalize sink: datasetArn={}", datasetArn)
        
        return create(SinkConfig.Personalize(
            datasetArn = datasetArn,
            region = region
        ))
    }
}

/**
 * Sink 타입 열거형
 */
enum class SinkType {
    OPENSEARCH,
    PERSONALIZE,
    IN_MEMORY
}

/**
 * Sink 설정 sealed class
 * 
 * 각 Sink 타입별 설정을 타입 안전하게 표현합니다.
 */
sealed interface SinkConfig {
    
    data class OpenSearch(
        val endpoint: String,
        val indexPrefix: String = "ivm",
        val username: String? = null,
        val password: String? = null,
        val timeoutMs: Long = 30_000
    ) : SinkConfig
    
    data class Personalize(
        val datasetArn: String,
        val region: String = "ap-northeast-2"
    ) : SinkConfig
    
    data object InMemory : SinkConfig
}
