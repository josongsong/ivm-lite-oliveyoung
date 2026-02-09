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

/** Contract 종류별 정보 */
export type ContractKind = 'ENTITY_SCHEMA' | 'RULESET' | 'VIEW_DEFINITION' | 'SINKRULE'

export interface ContractKindInfo {
  label: string
  description: string
  color: string
  icon: string
}

const CONTRACT_KIND_INFO: Record<ContractKind, ContractKindInfo> = {
  ENTITY_SCHEMA: {
    label: 'Entity Schema',
    description: '엔티티 스키마 정의',
    color: '#00d4ff',
    icon: 'Database',
  },
  RULESET: {
    label: 'Ruleset',
    description: '변환 규칙 정의',
    color: '#8855ff',
    icon: 'Cog',
  },
  VIEW_DEFINITION: {
    label: 'View Definition',
    description: '뷰 정의',
    color: '#00ff88',
    icon: 'Eye',
  },
  SINKRULE: {
    label: 'Sink Rule',
    description: '외부 전송 규칙',
    color: '#ffaa00',
    icon: 'Send',
  },
}

export function getContractKindInfo(kind: string): ContractKindInfo {
  return CONTRACT_KIND_INFO[kind as ContractKind] ?? {
    label: kind,
    description: '',
    color: '#666',
    icon: 'File',
  }
}

/** Explorer 기본 설정 */
export const EXPLORER_DEFAULTS = {
  /** 기본 탭 */
  DEFAULT_TAB: 'rawdata' as const,
  /** 목록 기본 limit */
  DEFAULT_LIMIT: 50,
  /** 최근 검색 저장 개수 */
  SEARCH_HISTORY_LIMIT: 10,
  /** 페이지 사이즈 옵션 */
  PAGE_SIZE_OPTIONS: [10, 25, 50, 100] as const,
  /** 기본 테넌트 */
  DEFAULT_TENANT: 'default',
  /** 기본 페이지 사이즈 */
  DEFAULT_PAGE_SIZE: 25,
} as const
