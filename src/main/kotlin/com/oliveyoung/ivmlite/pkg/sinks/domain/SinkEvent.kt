package com.oliveyoung.ivmlite.pkg.sinks.domain

import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import java.time.Instant
import java.util.UUID

/**
 * SinkEvent - DynamoDB Streams 기반 Sink 처리 이벤트
 *
 * Outbox 패턴 대체:
 * - DynamoDB에 저장
 * - DynamoDB Streams → Lambda 자동 트리거
 * - PostgreSQL Outbox 불필요
 *
 * @property id 고유 식별자 (UUID)
 * @property jobId 외부 서비스 jobId (end-to-end 추적용)
 * @property idempotencyKey 멱등성 키 (중복 방지)
 * @property tenantId 테넌트 ID
 * @property entityKey 엔티티 키
 * @property version 버전
 * @property viewType View 타입
 * @property payload View JSON 페이로드
 * @property sinkTargets Sink 플러그인 ID 목록 (SinkTargetType.toPluginId(), 예: ["opensearch-sink", "s3-sink"])
 * @property status 처리 상태 (PENDING, PROCESSING, COMPLETED, FAILED)
 * @property createdAt 생성 시각
 * @property processedAt 처리 완료 시각
 * @property ttl TTL (7일 후 자동 삭제, Unix timestamp)
 */
data class SinkEvent(
    val id: UUID,
    val jobId: String?,
    val idempotencyKey: String,
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val viewType: String,
    val payload: String,
    val sinkTargets: List<String>,
    val status: SinkEventStatus,
    val createdAt: Instant,
    val processedAt: Instant? = null,
    val ttl: Long,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        require(viewType.isNotBlank()) { "viewType must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
        require(sinkTargets.isNotEmpty()) { "sinkTargets must not be empty" }
    }

    companion object {
        private const val TTL_DAYS = 7L

        /**
         * 새 SinkEvent 생성 (PENDING 상태)
         */
        fun create(
            tenantId: String,
            entityKey: String,
            version: Long,
            viewType: String,
            payload: String,
            sinkTargets: List<String>,
            jobId: String? = null,
            timestamp: Instant = Instant.now(),
        ): SinkEvent {
            val idempotencyKey = generateIdempotencyKey(tenantId, entityKey, version, viewType)
            val ttl = timestamp.plusSeconds(TTL_DAYS * 24 * 60 * 60).epochSecond

            return SinkEvent(
                id = UUID.randomUUID(),
                jobId = jobId,
                idempotencyKey = idempotencyKey,
                tenantId = tenantId,
                entityKey = entityKey,
                version = version,
                viewType = viewType,
                payload = payload,
                sinkTargets = sinkTargets,
                status = SinkEventStatus.PENDING,
                createdAt = timestamp,
                ttl = ttl,
            )
        }

        /**
         * 결정적 idempotencyKey 생성
         */
        fun generateIdempotencyKey(
            tenantId: String,
            entityKey: String,
            version: Long,
            viewType: String
        ): String {
            val input = "$tenantId|$entityKey|$version|$viewType"
            return "sink_${Hashing.sha256Hex(input).take(32)}"
        }
    }

    /**
     * 처리 완료로 마킹
     */
    fun markCompleted(at: Instant = Instant.now()): SinkEvent = copy(
        status = SinkEventStatus.COMPLETED,
        processedAt = at,
    )

    /**
     * 실패로 마킹
     */
    fun markFailed(at: Instant = Instant.now()): SinkEvent = copy(
        status = SinkEventStatus.FAILED,
        processedAt = at,
    )
}

/**
 * SinkEvent 처리 상태
 */
enum class SinkEventStatus {
    PENDING,     // 대기 중
    PROCESSING,  // 처리 중 (Lambda에서 claim)
    COMPLETED,   // 완료
    FAILED,      // 실패
}
