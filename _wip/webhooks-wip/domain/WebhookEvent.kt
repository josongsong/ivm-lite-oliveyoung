package com.oliveyoung.ivmlite.pkg.webhooks.domain

/**
 * 웹훅 이벤트 타입
 *
 * 파이프라인의 각 단계에서 발생하는 이벤트를 정의한다.
 * 웹훅 구독 시 이 이벤트 타입을 기준으로 필터링한다.
 */
enum class WebhookEvent(val description: String, val category: EventCategory) {
    // RawData 이벤트
    RAWDATA_INGESTED("원본 데이터가 수집됨", EventCategory.RAWDATA),

    // Slice 이벤트
    SLICE_CREATED("슬라이스가 생성됨", EventCategory.SLICE),
    SLICE_UPDATED("슬라이스가 업데이트됨", EventCategory.SLICE),
    SLICE_DELETED("슬라이스가 삭제됨", EventCategory.SLICE),

    // View 이벤트
    VIEW_ASSEMBLED("뷰가 조립됨", EventCategory.VIEW),
    VIEW_CHANGED("뷰 내용이 변경됨", EventCategory.VIEW),

    // Sink 이벤트
    SINK_SHIPPED("싱크 전송 완료", EventCategory.SINK),
    SINK_FAILED("싱크 전송 실패", EventCategory.SINK),

    // 시스템 이벤트
    ERROR("시스템 에러 발생", EventCategory.SYSTEM),
    WORKER_STARTED("워커 시작됨", EventCategory.SYSTEM),
    WORKER_STOPPED("워커 중지됨", EventCategory.SYSTEM),
    BACKFILL_STARTED("백필 작업 시작", EventCategory.SYSTEM),
    BACKFILL_COMPLETED("백필 작업 완료", EventCategory.SYSTEM),
    BACKFILL_FAILED("백필 작업 실패", EventCategory.SYSTEM);

    companion object {
        fun fromString(value: String): WebhookEvent? =
            entries.find { it.name.equals(value, ignoreCase = true) }

        fun byCategory(category: EventCategory): List<WebhookEvent> =
            entries.filter { it.category == category }
    }
}

/**
 * 이벤트 카테고리
 */
enum class EventCategory {
    RAWDATA,
    SLICE,
    VIEW,
    SINK,
    SYSTEM
}
