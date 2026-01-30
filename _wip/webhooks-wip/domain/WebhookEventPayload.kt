package com.oliveyoung.ivmlite.pkg.webhooks.domain

import java.time.Instant
import java.util.UUID

/**
 * 웹훅 이벤트 페이로드
 *
 * 웹훅으로 전송되는 이벤트 데이터 구조.
 *
 * @property eventId 이벤트 고유 ID
 * @property eventType 이벤트 타입
 * @property timestamp 이벤트 발생 시각
 * @property source 이벤트 소스 (서비스명)
 * @property context 이벤트 컨텍스트 (tenantId, entityType 등)
 * @property data 이벤트 데이터
 */
data class WebhookEventPayload(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: WebhookEvent,
    val timestamp: Instant = Instant.now(),
    val source: String = "ivm-lite",
    val context: Map<String, String> = emptyMap(),
    val data: Map<String, Any?> = emptyMap()
) {
    /**
     * Map으로 변환 (JSON 직렬화용)
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "eventId" to eventId.toString(),
        "eventType" to eventType.name,
        "timestamp" to timestamp.toString(),
        "source" to source,
        "context" to context,
        "data" to data
    )

    /**
     * 컨텍스트에서 특정 값 추출
     */
    fun getContextValue(key: String): String? = context[key]

    /**
     * tenantId 추출
     */
    fun getTenantId(): String? = getContextValue("tenantId")

    /**
     * entityType 추출
     */
    fun getEntityType(): String? = getContextValue("entityType")

    /**
     * entityKey 추출
     */
    fun getEntityKey(): String? = getContextValue("entityKey")

    companion object {
        /**
         * RawData Ingested 이벤트 생성
         */
        fun rawDataIngested(
            tenantId: String,
            entityType: String,
            entityKey: String,
            version: Long,
            dataHash: String?
        ): WebhookEventPayload = WebhookEventPayload(
            eventType = WebhookEvent.RAWDATA_INGESTED,
            context = mapOf(
                "tenantId" to tenantId,
                "entityType" to entityType,
                "entityKey" to entityKey
            ),
            data = mapOf(
                "version" to version,
                "dataHash" to dataHash
            )
        )

        /**
         * Slice Created/Updated/Deleted 이벤트 생성
         */
        fun sliceEvent(
            eventType: WebhookEvent,
            tenantId: String,
            entityType: String,
            entityKey: String,
            sliceType: String,
            version: Long,
            sliceData: Map<String, Any?>? = null
        ): WebhookEventPayload {
            require(eventType in listOf(
                WebhookEvent.SLICE_CREATED,
                WebhookEvent.SLICE_UPDATED,
                WebhookEvent.SLICE_DELETED
            )) { "Invalid slice event type: $eventType" }

            return WebhookEventPayload(
                eventType = eventType,
                context = mapOf(
                    "tenantId" to tenantId,
                    "entityType" to entityType,
                    "entityKey" to entityKey,
                    "sliceType" to sliceType
                ),
                data = mapOf(
                    "version" to version,
                    "slice" to sliceData
                )
            )
        }

        /**
         * View Assembled/Changed 이벤트 생성
         */
        fun viewEvent(
            eventType: WebhookEvent,
            tenantId: String,
            entityType: String,
            entityKey: String,
            viewName: String,
            version: Long,
            changedFields: List<String>? = null
        ): WebhookEventPayload {
            require(eventType in listOf(
                WebhookEvent.VIEW_ASSEMBLED,
                WebhookEvent.VIEW_CHANGED
            )) { "Invalid view event type: $eventType" }

            return WebhookEventPayload(
                eventType = eventType,
                context = mapOf(
                    "tenantId" to tenantId,
                    "entityType" to entityType,
                    "entityKey" to entityKey,
                    "viewName" to viewName
                ),
                data = mapOf(
                    "version" to version,
                    "changedFields" to changedFields
                )
            )
        }

        /**
         * Sink Shipped/Failed 이벤트 생성
         */
        fun sinkEvent(
            eventType: WebhookEvent,
            tenantId: String,
            entityKey: String,
            sinkName: String,
            success: Boolean,
            errorMessage: String? = null
        ): WebhookEventPayload {
            require(eventType in listOf(
                WebhookEvent.SINK_SHIPPED,
                WebhookEvent.SINK_FAILED
            )) { "Invalid sink event type: $eventType" }

            return WebhookEventPayload(
                eventType = eventType,
                context = mapOf(
                    "tenantId" to tenantId,
                    "entityKey" to entityKey,
                    "sinkName" to sinkName
                ),
                data = mapOf(
                    "success" to success,
                    "error" to errorMessage
                )
            )
        }

        /**
         * 시스템 이벤트 생성
         */
        fun systemEvent(
            eventType: WebhookEvent,
            message: String,
            details: Map<String, Any?>? = null
        ): WebhookEventPayload = WebhookEventPayload(
            eventType = eventType,
            data = mapOf(
                "message" to message,
                "details" to details
            )
        )
    }
}
