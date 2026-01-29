package com.oliveyoung.ivmlite.shared.domain.types

import kotlinx.serialization.Serializable

/**
 * Outbox 토픽 정의 (RFC-IMPL-008)
 * 
 * Kafka와 PostgreSQL Polling 모두에서 동일한 토픽 패턴 지원.
 * 
 * 토픽명 패턴: {topicPrefix}.events.{aggregatetype}
 * 
 * 예시:
 * - ivm.events.raw_data → RAW_DATA
 * - ivm.events.slice → SLICE
 * - ivm.events.changeset → CHANGESET
 */
@Serializable
enum class Topic(
    val aggregateType: AggregateType,
    val suffix: String,
) {
    /**
     * RawData Ingest 이벤트 토픽
     * 
     * 이벤트 타입: RawDataIngested
     * 후속 처리: SlicingWorkflow 트리거
     */
    RAW_DATA(AggregateType.RAW_DATA, "raw_data"),

    /**
     * Slice 이벤트 토픽
     * 
     * 이벤트 타입: SliceCreated, ShipRequested
     * 후속 처리: ShipWorkflow 트리거
     */
    SLICE(AggregateType.SLICE, "slice"),

    /**
     * ChangeSet 이벤트 토픽
     * 
     * 이벤트 타입: ChangeSetCreated
     * 후속 처리: FanoutWorkflow 트리거
     */
    CHANGESET(AggregateType.CHANGESET, "changeset");

    /**
     * 전체 토픽명 생성
     * 
     * @param prefix 토픽 prefix (기본: "ivm")
     * @return 전체 토픽명 (예: "ivm.events.raw_data")
     */
    fun toTopicName(prefix: String = "ivm"): String = "$prefix.events.$suffix"

    companion object {
        /**
         * AggregateType에서 Topic 변환
         */
        fun fromAggregateType(type: AggregateType): Topic = entries.first { it.aggregateType == type }

        /**
         * 토픽명에서 Topic 파싱
         * 
         * @param topicName 토픽명 (예: "ivm.events.raw_data")
         * @return Topic 또는 null
         */
        fun fromTopicName(topicName: String): Topic? {
            val suffix = topicName.substringAfterLast(".")
            return entries.find { it.suffix == suffix }
        }

        /**
         * 토픽명에서 AggregateType 파싱
         * 
         * @param topicName 토픽명 (예: "ivm.events.raw_data")
         * @return AggregateType 또는 null
         */
        fun toAggregateType(topicName: String): AggregateType? = fromTopicName(topicName)?.aggregateType

        /**
         * 모든 토픽명 생성
         * 
         * @param prefix 토픽 prefix (기본: "ivm")
         * @return 모든 토픽명 목록
         */
        fun allTopicNames(prefix: String = "ivm"): List<String> = entries.map { it.toTopicName(prefix) }
    }
}

/**
 * 토픽 설정 (SDK/Worker 공용)
 * 
 * Kafka Consumer와 PostgreSQL Polling Worker 모두에서 사용.
 * 
 * @example
 * ```kotlin
 * // 특정 토픽만 구독
 * val config = TopicConfig(topics = listOf(Topic.RAW_DATA))
 * 
 * // 모든 토픽 구독
 * val config = TopicConfig.all()
 * 
 * // 토픽명으로 설정
 * val config = TopicConfig.fromTopicNames(listOf("ivm.events.raw_data"))
 * ```
 */
data class TopicConfig(
    /** 구독할 토픽 목록 (null이면 모든 토픽) */
    val topics: List<Topic>? = null,
    /** 토픽 prefix (기본: "ivm") */
    val topicPrefix: String = "ivm",
) {
    /**
     * 구독할 토픽명 목록
     */
    val topicNames: List<String>
        get() = topics?.map { it.toTopicName(topicPrefix) } ?: Topic.allTopicNames(topicPrefix)

    /**
     * 구독할 AggregateType 목록
     */
    val aggregateTypes: List<AggregateType>?
        get() = topics?.map { it.aggregateType }

    companion object {
        /**
         * 모든 토픽 구독
         */
        fun all(prefix: String = "ivm") = TopicConfig(topics = null, topicPrefix = prefix)

        /**
         * 특정 토픽만 구독
         */
        fun of(vararg topics: Topic, prefix: String = "ivm") = TopicConfig(topics = topics.toList(), topicPrefix = prefix)

        /**
         * 토픽명으로 설정
         */
        fun fromTopicNames(topicNames: List<String>, prefix: String = "ivm"): TopicConfig {
            val topics = topicNames.mapNotNull { Topic.fromTopicName(it) }
            return TopicConfig(topics = topics.ifEmpty { null }, topicPrefix = prefix)
        }

        /**
         * AggregateType으로 설정
         */
        fun fromAggregateTypes(types: List<AggregateType>, prefix: String = "ivm"): TopicConfig {
            val topics = types.map { Topic.fromAggregateType(it) }
            return TopicConfig(topics = topics, topicPrefix = prefix)
        }
    }
}
