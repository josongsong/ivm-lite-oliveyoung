package com.oliveyoung.ivmlite.shared.domain.events

import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * DomainEvent - 도메인 이벤트 기반 클래스
 *
 * Event Sourcing의 기초
 * SinkEvent(DynamoDB) / Kafka 이벤트에서 사용
 */
sealed interface DomainEvent {
    val eventId: String
    val occurredAt: Instant
    val aggregateType: AggregateType
    val aggregateId: String
}

/**
 * ViewsComposedEvent - View 조합 완료 이벤트
 *
 * RawData → Slicing → View Composition까지 완료되었을 때 발행
 * DynamoDB Streams → Lambda가 Sink 처리
 */
data class ViewsComposedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateType: AggregateType = AggregateType.VIEW,
    override val aggregateId: String,
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val viewKeys: List<ViewKey>,
    val sliceKeys: List<SliceKey>
) : DomainEvent

/**
 * ViewKey - View 식별자
 */
@Serializable
data class ViewKey(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val viewType: String
)

/**
 * SliceKey - Slice 식별자
 */
@Serializable
data class SliceKey(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val sliceType: String
)
