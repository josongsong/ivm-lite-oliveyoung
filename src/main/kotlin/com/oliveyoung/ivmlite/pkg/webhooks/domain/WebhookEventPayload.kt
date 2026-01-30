package com.oliveyoung.ivmlite.pkg.webhooks.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Webhook Event Payload
 *
 * 웹훅으로 전송되는 이벤트 페이로드.
 */
@Serializable
data class WebhookEventPayload(
    val id: String,
    val event: String,
    val timestamp: String,
    val data: Map<String, String>,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json {
            prettyPrint = false
            encodeDefaults = true
        }

        fun create(
            event: WebhookEvent,
            data: Map<String, String>,
            metadata: Map<String, String> = emptyMap()
        ): WebhookEventPayload = WebhookEventPayload(
            id = UUID.randomUUID().toString(),
            event = event.name,
            timestamp = Instant.now().toString(),
            data = data,
            metadata = metadata
        )

        // Factory methods for common events
        fun sliceCreated(
            sliceId: String,
            entityType: String,
            entityId: String,
            version: Long
        ): WebhookEventPayload = create(
            event = WebhookEvent.SLICE_CREATED,
            data = mapOf(
                "sliceId" to sliceId,
                "entityType" to entityType,
                "entityId" to entityId,
                "version" to version.toString()
            )
        )

        fun sliceUpdated(
            sliceId: String,
            entityType: String,
            entityId: String,
            oldVersion: Long,
            newVersion: Long
        ): WebhookEventPayload = create(
            event = WebhookEvent.SLICE_UPDATED,
            data = mapOf(
                "sliceId" to sliceId,
                "entityType" to entityType,
                "entityId" to entityId,
                "oldVersion" to oldVersion.toString(),
                "newVersion" to newVersion.toString()
            )
        )

        fun viewChanged(
            viewName: String,
            entityId: String,
            changeType: String
        ): WebhookEventPayload = create(
            event = WebhookEvent.VIEW_CHANGED,
            data = mapOf(
                "viewName" to viewName,
                "entityId" to entityId,
                "changeType" to changeType
            )
        )

        fun sinkShipped(
            sinkType: String,
            destination: String,
            recordCount: Int
        ): WebhookEventPayload = create(
            event = WebhookEvent.SINK_SHIPPED,
            data = mapOf(
                "sinkType" to sinkType,
                "destination" to destination,
                "recordCount" to recordCount.toString()
            )
        )

        fun error(
            source: String,
            message: String,
            stackTrace: String? = null
        ): WebhookEventPayload = create(
            event = WebhookEvent.ERROR,
            data = mapOf(
                "source" to source,
                "message" to message,
                "stackTrace" to (stackTrace ?: "")
            )
        )
    }
}
