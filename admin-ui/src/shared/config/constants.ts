/**
 * 공통 상수 정의 (OCP 준수)
 * 새로운 타입 추가 시 이 파일만 수정
 */

/** Contract 종류별 정보 */
export const CONTRACT_KIND_INFO: Record<string, {
  label: string
  color: string
  description: string
}> = {
  'ENTITY_SCHEMA': {
    label: 'Entity Schema',
    color: 'cyan',
    description: '엔티티의 데이터 구조를 정의합니다. 필드 타입, 제약조건, 슬라이스 구성 등을 포함합니다.'
  },
  'RULESET': {
    label: 'RuleSet',
    color: 'purple',
    description: '슬라이싱 규칙을 정의합니다. 어떤 필드를 어떤 슬라이스로 분리할지 결정합니다.'
  },
  'VIEW_DEFINITION': {
    label: 'View Definition',
    color: 'green',
    description: '여러 슬라이스를 조합하여 뷰를 정의합니다. 조인 조건, 필터링 규칙을 포함합니다.'
  },
  'SINKRULE': {
    label: 'Sink Rule',
    color: 'orange',
    description: '외부 시스템으로 데이터를 전송하는 규칙을 정의합니다. Kafka, OpenSearch 등의 대상을 설정합니다.'
  },
  'JoinSpecContract': {
    label: 'Join Spec',
    color: 'magenta',
    description: '엔티티 간 조인 규칙을 정의합니다. 관계형 데이터 조합에 사용됩니다.'
  },
  'ChangeSetContract': {
    label: 'ChangeSet',
    color: 'yellow',
    description: '변경 감지 및 델타 계산 규칙을 정의합니다. 증분 업데이트에 사용됩니다.'
  },
} as const

/** Contract 기본값 */
export const CONTRACT_DEFAULTS = {
  UNKNOWN: { label: 'Unknown', color: 'gray', description: '' }
} as const

/** 탐색기 기본값 */
export const EXPLORER_DEFAULTS = {
  DEFAULT_TENANT: 'oliveyoung',
  DEFAULT_PAGE_SIZE: 20,
  MAX_PAGE_SIZE: 100,
} as const

/** Outbox 탭 타입 */
export const OUTBOX_TABS = ['recent', 'failed', 'dlq', 'stale'] as const
export type OutboxTabType = typeof OUTBOX_TABS[number]

/** 네비게이션 아이템 */
export const NAV_ITEMS = [
  { path: '/', label: 'Dashboard', icon: 'LayoutDashboard' },
  { path: '/contracts', label: 'Contracts', icon: 'FileCode2' },
  { path: '/workflow', label: 'Workflow', icon: 'GitBranch' },
  { path: '/explorer', label: 'Explorer', icon: 'Database' },
  { path: '/pipeline', label: 'Pipeline', icon: 'Workflow' },
  { path: '/outbox', label: 'Outbox', icon: 'Inbox' },
  { path: '/webhooks', label: 'Webhooks', icon: 'Webhook' },
  { path: '/playground', label: 'Playground', icon: 'FlaskConical' },
] as const

/** 헬퍼 함수: Contract 종류 정보 가져오기 */
export function getContractKindInfo(kind: string) {
  return CONTRACT_KIND_INFO[kind] ?? CONTRACT_DEFAULTS.UNKNOWN
}
