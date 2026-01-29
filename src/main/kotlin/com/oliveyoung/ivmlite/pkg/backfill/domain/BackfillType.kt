package com.oliveyoung.ivmlite.pkg.backfill.domain

/**
 * Backfill 타입
 * 
 * 어떤 단계를 재처리할지 정의한다.
 */
enum class BackfillType {
    /** RawData → Slice 재처리 */
    RAW_TO_SLICE,
    
    /** Slice → View 재조합 (캐시 갱신) */
    SLICE_TO_VIEW,
    
    /** View → Sink 재전송 */
    VIEW_TO_SINK,
    
    /** 전체 파이프라인 재처리 (Raw → Slice → View → Sink) */
    FULL_REPROCESS,
    
    /** DLQ 메시지 일괄 재처리 */
    DLQ_REPLAY,
    
    /** 실패한 Outbox 엔트리 재처리 */
    FAILED_REPLAY;
    
    fun description(): String = when (this) {
        RAW_TO_SLICE -> "RawData를 다시 슬라이싱합니다"
        SLICE_TO_VIEW -> "Slice를 다시 View로 조합합니다"
        VIEW_TO_SINK -> "View를 외부 시스템으로 다시 전송합니다"
        FULL_REPROCESS -> "전체 파이프라인을 처음부터 다시 처리합니다"
        DLQ_REPLAY -> "Dead Letter Queue의 메시지를 다시 처리합니다"
        FAILED_REPLAY -> "실패한 Outbox 엔트리를 다시 처리합니다"
    }
}
