/**
 * Admin UI 설정 상수
 * 하드코딩된 값들을 중앙 관리
 */

/** API 쿼리 기본 설정 */
export const QUERY_CONFIG = {
  /** 실시간 모니터링용 (5초) */
  REALTIME_INTERVAL: 5_000,
  /** 일반 대시보드용 (10초) */
  DASHBOARD_INTERVAL: 10_000,
  /** 관측성 지표용 (15초) */
  OBSERVABILITY_INTERVAL: 15_000,
  /** 워크플로우 그래프용 (30초) */
  WORKFLOW_INTERVAL: 30_000,
  /** 차트 데이터용 (1분) */
  CHART_INTERVAL: 60_000,
} as const

/** Outbox 관련 설정 */
export const OUTBOX_CONFIG = {
  /** 목록 조회 기본 limit */
  DEFAULT_LIMIT: 50,
  /** 일괄 재시도 limit */
  BATCH_RETRY_LIMIT: 100,
  /** Stale 판정 타임아웃 (초) */
  STALE_TIMEOUT_SECONDS: 300,
} as const

/** 차트 관련 설정 */
export const CHART_CONFIG = {
  /** 시간별 통계 조회 기간 (시간) */
  HOURLY_STATS_HOURS: 24,
} as const

/** 앱 정보 */
export const APP_INFO = {
  VERSION: '1.0.0',
  NAME: 'IVM Lite Admin',
} as const
