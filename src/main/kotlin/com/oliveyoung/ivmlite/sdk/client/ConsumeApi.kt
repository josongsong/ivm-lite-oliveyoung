package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.Topic
import com.oliveyoung.ivmlite.shared.domain.types.TopicConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Consume API - 토픽 기반 이벤트 소비 (RFC-IMPL-008)
 * 
 * Kafka Consumer와 PostgreSQL Polling 모두에서 동일한 API로 사용.
 * 
 * @example
 * ```kotlin
 * // 특정 토픽만 구독
 * Ivm.consume(Topic.RAW_DATA) { entry ->
 *     println("Received: ${entry.eventType}")
 * }
 * 
 * // 여러 토픽 구독
 * Ivm.consume(Topic.RAW_DATA, Topic.SLICE) { entry ->
 *     println("Received: ${entry.eventType}")
 * }
 * 
 * // 토픽명으로 구독
 * Ivm.consume("ivm.events.raw_data") { entry ->
 *     println("Received: ${entry.eventType}")
 * }
 * 
 * // Flow로 처리
 * Ivm.consumeFlow(Topic.RAW_DATA)
 *     .collect { entry -> println(entry.eventType) }
 * ```
 */
class ConsumeApi(
    private val outboxRepo: OutboxRepositoryPort,
    private val workerConfig: WorkerConfig,
    private val topicPrefix: String = "ivm",
) {
    
    /**
     * 특정 토픽의 이벤트 조회 (Polling 방식)
     * 
     * @param topics 구독할 토픽 목록
     * @param limit 최대 조회 개수
     * @return 이벤트 목록
     */
    suspend fun poll(vararg topics: Topic, limit: Int = 100): List<OutboxEntry> {
        val aggregateTypes = topics.map { it.aggregateType }
        return pollByAggregateTypes(aggregateTypes, limit)
    }

    /**
     * 토픽명으로 이벤트 조회
     * 
     * @param topicNames 구독할 토픽명 목록
     * @param limit 최대 조회 개수
     * @return 이벤트 목록
     */
    suspend fun pollByTopicNames(vararg topicNames: String, limit: Int = 100): List<OutboxEntry> {
        val topics = topicNames.mapNotNull { Topic.fromTopicName(it) }
        val aggregateTypes = topics.map { it.aggregateType }
        return pollByAggregateTypes(aggregateTypes, limit)
    }

    /**
     * AggregateType으로 이벤트 조회
     */
    private suspend fun pollByAggregateTypes(
        aggregateTypes: List<AggregateType>,
        limit: Int
    ): List<OutboxEntry> {
        val aggregateType = aggregateTypes.firstOrNull()
        
        val result = if (aggregateType != null) {
            outboxRepo.findPendingByType(aggregateType, limit)
        } else {
            outboxRepo.findPending(limit)
        }
        
        return when (result) {
            is OutboxRepositoryPort.Result.Ok -> result.value
            is OutboxRepositoryPort.Result.Err -> emptyList()
        }
    }

    /**
     * 이벤트 처리 완료 표시
     * 
     * @param entries 처리 완료된 엔트리 목록
     */
    suspend fun ack(entries: List<OutboxEntry>) {
        val ids = entries.map { it.id }
        outboxRepo.markProcessed(ids)
    }

    /**
     * 이벤트 처리 실패 표시
     * 
     * @param entry 실패한 엔트리
     * @param reason 실패 사유
     */
    suspend fun nack(entry: OutboxEntry, reason: String) {
        outboxRepo.markFailed(entry.id, reason)
    }

    /**
     * Flow로 이벤트 소비 (연속 Polling)
     * 
     * @param topics 구독할 토픽 목록
     * @param batchSize 한 번에 조회할 개수
     * @param pollIntervalMs Polling 간격 (ms)
     * @return 이벤트 Flow
     */
    fun consumeFlow(
        vararg topics: Topic,
        batchSize: Int = workerConfig.batchSize,
        pollIntervalMs: Long = workerConfig.pollIntervalMs,
    ): Flow<OutboxEntry> = flow {
        val aggregateTypes = topics.map { it.aggregateType }
        
        while (true) {
            val entries = pollByAggregateTypes(aggregateTypes, batchSize)
            
            for (entry in entries) {
                emit(entry)
            }
            
            // 데이터가 없으면 idle 간격, 있으면 일반 간격
            val delayMs = if (entries.isEmpty()) {
                workerConfig.idlePollIntervalMs
            } else {
                pollIntervalMs
            }
            kotlinx.coroutines.delay(delayMs)
        }
    }

    /**
     * 토픽 설정 빌더
     */
    fun topics(vararg topics: Topic): TopicBuilder = TopicBuilder(this, topics.toList())

    /**
     * 토픽명으로 설정
     */
    fun topics(vararg topicNames: String): TopicBuilder {
        val topics = topicNames.mapNotNull { Topic.fromTopicName(it) }
        return TopicBuilder(this, topics)
    }

    /**
     * 모든 토픽 구독
     */
    fun allTopics(): TopicBuilder = TopicBuilder(this, Topic.entries.toList())

    /**
     * 토픽별 전체 토픽명 조회
     */
    fun getTopicNames(): List<String> = Topic.allTopicNames(topicPrefix)

    /**
     * 특정 토픽의 토픽명 조회
     */
    fun getTopicName(topic: Topic): String = topic.toTopicName(topicPrefix)
}

/**
 * Topic Builder - Fluent API
 */
class TopicBuilder(
    private val consumeApi: ConsumeApi,
    private val topics: List<Topic>,
) {
    private var batchSize: Int = 100
    private var pollIntervalMs: Long = 100
    
    fun batchSize(size: Int): TopicBuilder {
        this.batchSize = size
        return this
    }
    
    fun pollInterval(ms: Long): TopicBuilder {
        this.pollIntervalMs = ms
        return this
    }
    
    /**
     * 이벤트 조회 (한 번)
     */
    suspend fun poll(limit: Int = batchSize): List<OutboxEntry> {
        return consumeApi.poll(*topics.toTypedArray(), limit = limit)
    }
    
    /**
     * Flow로 연속 소비
     */
    fun flow(): Flow<OutboxEntry> {
        return consumeApi.consumeFlow(
            *topics.toTypedArray(),
            batchSize = batchSize,
            pollIntervalMs = pollIntervalMs,
        )
    }
    
    /**
     * 콜백으로 처리
     */
    suspend fun forEach(handler: suspend (OutboxEntry) -> Unit) {
        flow().collect { entry ->
            handler(entry)
        }
    }
}
