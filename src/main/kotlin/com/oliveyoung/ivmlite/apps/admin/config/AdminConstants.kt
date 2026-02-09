package com.oliveyoung.ivmlite.apps.admin.config

/**
 * Admin 애플리케이션 상수 (SSOT - Single Source of Truth)
 *
 * 매직넘버와 하드코딩 방지를 위한 중앙 상수 정의.
 * detekt MagicNumber 규칙 준수.
 */
object AdminConstants {

    // ==================== 서버 설정 ====================

    /** Admin 서버 기본 포트 */
    const val DEFAULT_ADMIN_PORT = 8081

    /** Runtime API 기본 포트 */
    const val DEFAULT_RUNTIME_PORT = 8080

    // ==================== 페이지네이션 ====================

    /** 기본 페이지 크기 */
    const val DEFAULT_PAGE_SIZE = 50

    /** 최대 페이지 크기 */
    const val MAX_PAGE_SIZE = 200

    /** 최소 페이지 크기 */
    const val MIN_PAGE_SIZE = 1

    // ==================== 타임아웃 ====================

    /** 기본 타임아웃 (초) */
    const val DEFAULT_TIMEOUT_SECONDS = 60L

    /** 최소 타임아웃 (초) */
    const val MIN_TIMEOUT_SECONDS = 60L

    /** 최대 타임아웃 (초) - 1일 */
    const val MAX_TIMEOUT_SECONDS = 86_400L

    // ==================== 알림/모니터링 ====================

    /** 알림 평가 주기 (밀리초) */
    const val DEFAULT_ALERT_EVAL_INTERVAL_MS = 10_000L

    /** 기본 통계 조회 시간 범위 (시간) */
    const val DEFAULT_STATS_HOURS = 24

    /** 최대 통계 조회 시간 범위 (시간) */
    const val MAX_STATS_HOURS = 168  // 7일

    // ==================== 배치 처리 ====================

    /** 기본 배치 크기 */
    const val DEFAULT_BATCH_SIZE = 100

    /** 최대 배치 크기 */
    const val MAX_BATCH_SIZE = 1000

    // ==================== 검증 ====================

    /** 엔티티 키 최대 길이 */
    const val MAX_ENTITY_KEY_LENGTH = 255

    /** 테넌트 ID 최대 길이 */
    const val MAX_TENANT_ID_LENGTH = 64

    // ==================== 기본값 ====================

    /** 기본 테넌트 ID */
    const val DEFAULT_TENANT_ID = "oliveyoung"

    /** 기본 DynamoDB 테이블명 */
    const val DEFAULT_DYNAMODB_TABLE = "ivm-lite-data"

    // ==================== 캐시 ====================

    /** 기본 캐시 TTL (초) */
    const val DEFAULT_CACHE_TTL_SECONDS = 300L

    /** 최대 캐시 항목 수 */
    const val MAX_CACHE_ENTRIES = 10_000

    // ==================== 검색/자동완성 ====================

    /** 기본 자동완성 결과 수 */
    const val DEFAULT_AUTOCOMPLETE_LIMIT = 10

    /** 최대 자동완성 결과 수 */
    const val MAX_AUTOCOMPLETE_LIMIT = 50

    /** 기본 검색 결과 수 */
    const val DEFAULT_SEARCH_LIMIT = 20

    // ==================== DynamoDB 스캔 ====================

    /** 스캔 버퍼 배수 (limit * 3) */
    const val SCAN_BUFFER_MULTIPLIER = 3

    /** DynamoDB 스캔 버퍼 배수 (호환성) */
    const val DYNAMO_SCAN_BUFFER_MULTIPLIER = 3

    /** 자동완성 버퍼 배수 */
    const val AUTOCOMPLETE_BUFFER_MULTIPLIER = 2
}
