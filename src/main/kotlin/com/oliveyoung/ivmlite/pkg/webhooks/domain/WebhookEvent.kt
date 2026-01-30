package com.oliveyoung.ivmlite.pkg.webhooks.domain

/**
 * Webhook Event Types
 *
 * 파이프라인에서 발생하는 이벤트 타입 정의.
 * 외부 시스템에 전송할 수 있는 이벤트 목록.
 */
enum class WebhookEvent(val description: String, val category: String) {
    // RawData Events
    RAWDATA_INGESTED("Raw data ingested into the system", "rawdata"),

    // Slice Events
    SLICE_CREATED("New slice created", "slice"),
    SLICE_UPDATED("Existing slice updated", "slice"),
    SLICE_DELETED("Slice deleted", "slice"),

    // View Events
    VIEW_ASSEMBLED("View assembled from slices", "view"),
    VIEW_CHANGED("View content changed", "view"),

    // Sink Events
    SINK_SHIPPED("Data shipped to sink", "sink"),
    SINK_FAILED("Sink shipping failed", "sink"),

    // System Events
    ERROR("System error occurred", "system"),
    WORKER_STARTED("Worker started", "system"),
    WORKER_STOPPED("Worker stopped", "system"),
    BACKFILL_COMPLETED("Backfill job completed", "system");

    companion object {
        fun fromString(value: String): WebhookEvent? =
            entries.find { it.name.equals(value, ignoreCase = true) }

        fun byCategory(category: String): List<WebhookEvent> =
            entries.filter { it.category == category }
    }
}
