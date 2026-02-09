/**
 * Design System Catalog - Type Definitions
 * Phase 0: 모든 작업자가 참조할 수 있는 타입 정의
 */

// ============================================================================
// Component Categories & Stability
// ============================================================================

/** 컴포넌트 카테고리 */
export type ComponentCategory =
  | 'actions'      // Button, IconButton
  | 'inputs'       // Input, TextArea, Select, Checkbox
  | 'navigation'   // Tabs, Pagination, Breadcrumb
  | 'feedback'     // Loading, Modal, Toast
  | 'layout'       // Card, Section, Accordion
  | 'data-display' // Table, StatusBadge, Chip, Label

/** 컴포넌트 안정성 레벨 */
export type ComponentStability =
  | 'stable'       // 프로덕션 사용 가능
  | 'beta'         // API 변경 가능성 있음
  | 'experimental' // 실험적, 사용 주의
  | 'deprecated'   // 사용 중단 예정

// ============================================================================
// Props Playground Controls
// ============================================================================

/** Props Playground에서 사용할 컨트롤 타입 */
export type ControlType =
  | 'text'
  | 'number'
  | 'boolean'
  | 'select'
  | 'radio'
  | 'color'
  | 'range'
  | 'json'

/** 개별 컨트롤 정의 */
export interface ControlDefinition {
  /** 컨트롤 타입 */
  type: ControlType
  /** 컨트롤 라벨 */
  label: string
  /** 기본값 */
  defaultValue: unknown
  /** select/radio용 옵션 */
  options?: Array<{ label: string; value: string | number | boolean }>
  /** range용 최소값 */
  min?: number
  /** range용 최대값 */
  max?: number
  /** range용 스텝 */
  step?: number
  /** 설명 */
  description?: string
}

// ============================================================================
// Component Examples & Anti-Patterns
// ============================================================================

/** 사용 예시 */
export interface ComponentExample {
  /** 예시 제목 */
  title: string
  /** 예시 설명 */
  description: string
  /** 코드 스니펫 */
  code: string
  /** 사용 상황/컨텍스트 */
  context?: string
  /** 강조할 Props */
  highlightProps?: string[]
}

/** 안티 패턴 */
export interface AntiPattern {
  /** 안티패턴 제목 */
  title: string
  /** 왜 나쁜지 설명 */
  reason: string
  /** 잘못된 코드 */
  badCode: string
  /** 올바른 코드 */
  goodCode: string
  /** 관련 Props */
  relatedProps?: string[]
}

// ============================================================================
// Accessibility (A11y)
// ============================================================================

/** 접근성 이슈 심각도 */
export type A11ySeverity = 'error' | 'warning' | 'info'

/** 접근성 이슈 */
export interface A11yIssue {
  /** 이슈 ID (axe-core) */
  id: string
  /** 이슈 메시지 */
  message: string
  /** 심각도 */
  severity: A11ySeverity
  /** 해결 방법 */
  suggestion?: string
}

/** 접근성 점수 */
export interface A11yScore {
  /** 전체 점수 (0-100) */
  overall: number
  /** 카테고리별 점수 */
  categories: {
    colorContrast: number
    keyboardNavigation: number
    ariaLabels: number
    focusManagement: number
  }
  /** 발견된 이슈 목록 */
  issues: A11yIssue[]
}

/** A11y 테스트 결과 */
export interface A11yResult {
  /** 점수 (0-100) */
  score: number
  /** 발견된 이슈 목록 */
  issues: A11yIssue[]
}

// ============================================================================
// Related Decisions (RFC/ADR)
// ============================================================================

/** 관련 결정사항 */
export interface RelatedDecision {
  /** 결정 유형 */
  type: 'rfc' | 'adr'
  /** 제목 */
  title: string
  /** 링크 */
  url: string
  /** 상태 */
  status: 'draft' | 'accepted' | 'deprecated'
}

// ============================================================================
// Component Metadata (Core)
// ============================================================================

/** 컴포넌트 메타데이터 - 레지스트리에서 사용 */
export interface ComponentMeta {
  /** 컴포넌트 이름 */
  name: string
  /** 카테고리 */
  category: ComponentCategory
  /** 간단한 설명 */
  description: string
  /** 상세 설명 */
  longDescription?: string
  /** 안정성 레벨 */
  stability: ComponentStability
  /** 컴포넌트 파일 경로 */
  path: string
  /** Props 컨트롤 정의 */
  controls: Record<string, ControlDefinition>
  /** 사용 예시 */
  examples: ComponentExample[]
  /** 안티 패턴 */
  antiPatterns: AntiPattern[]
  /** 접근성 점수 */
  a11yScore?: A11yScore
  /** 관련 결정사항 */
  relatedDecisions?: RelatedDecision[]
  /** 관련 컴포넌트 */
  relatedComponents?: string[]
  /** 언제 사용해야 하는지 */
  whenToUse?: string[]
  /** 언제 사용하지 말아야 하는지 */
  whenNotToUse?: string[]
  /** 키워드 (검색용) */
  keywords?: string[]
  /** 버전 */
  version?: string
  /** 마지막 업데이트 */
  lastUpdated?: string
}

// ============================================================================
// Props Extraction (ts-morph)
// ============================================================================

/** 추출된 Props 정보 */
export interface ExtractedProp {
  /** Prop 이름 */
  name: string
  /** 타입 문자열 */
  type: string
  /** 필수 여부 */
  required: boolean
  /** 기본값 */
  defaultValue?: string
  /** 설명 (JSDoc) */
  description?: string
  /** Deprecated 여부 */
  deprecated?: boolean
  /** Deprecated 메시지 */
  deprecatedMessage?: string
}

// ============================================================================
// Usage Tracking
// ============================================================================

/** 컴포넌트 사용 정보 */
export interface UsageInfo {
  /** 파일 경로 */
  filePath: string
  /** 라인 번호 */
  lineNumber: number
  /** 사용 횟수 */
  count: number
  /** 코드 스니펫 */
  snippet: string
  /** 피처 이름 (features/xxx) */
  feature?: string
}

// ============================================================================
// Design Smell Detection
// ============================================================================

/** Design Smell 타입 */
export type DesignSmellType =
  | 'pattern-violation'   // 패턴 위반
  | 'inconsistent-usage'  // 일관성 없는 사용
  | 'a11y-issue'          // 접근성 이슈
  | 'deprecated-usage'    // 사용 중단된 API 사용

/** Design Smell 심각도 */
export type DesignSmellSeverity = 'error' | 'warning' | 'info'

/** Design Smell */
export interface DesignSmell {
  /** Smell 타입 */
  type: DesignSmellType
  /** 메시지 */
  message: string
  /** 심각도 */
  severity: DesignSmellSeverity
  /** 제안사항 */
  suggestion: string
  /** 관련 파일 */
  file?: string
  /** 관련 라인 */
  line?: number
}

// ============================================================================
// Project Settings (Code Generation)
// ============================================================================

/** 프레임워크 타입 */
export type FrameworkType = 'react' | 'vue' | 'svelte'

/** 스타일링 방식 */
export type StyleType = 'tailwind' | 'css-modules' | 'styled-components' | 'vanilla'

/** Import 방식 */
export type ImportType = 'alias' | 'relative'

/** 프로젝트 설정 */
export interface ProjectSettings {
  /** 프레임워크 */
  framework: FrameworkType
  /** 스타일링 방식 */
  style: StyleType
  /** Import 방식 */
  import: ImportType
  /** TypeScript 사용 여부 */
  typescript?: boolean
  /** 커스텀 Import 경로 */
  customImportPath?: string
}

// ============================================================================
// Component Statistics
// ============================================================================

/** 컴포넌트 사용 통계 */
export interface ComponentStats {
  /** 컴포넌트 이름 */
  name: string
  /** 총 사용 횟수 */
  count: number
  /** 사용된 파일 목록 */
  files: string[]
  /** 함께 쓰이는 컴포넌트 */
  coOccurrences: Record<string, number>
}

// ============================================================================
// Contrast Calculation
// ============================================================================

/** 대비 계산 결과 */
export interface ContrastResult {
  /** 대비 비율 */
  ratio: number
  /** WCAG AA 통과 (일반 텍스트: 4.5:1) */
  levelAA: boolean
  /** WCAG AAA 통과 (일반 텍스트: 7:1) */
  levelAAA: boolean
  /** WCAG AA 통과 (큰 텍스트: 3:1) */
  levelAALarge: boolean
  /** WCAG AAA 통과 (큰 텍스트: 4.5:1) */
  levelAAALarge: boolean
}

// ============================================================================
// Design Tokens
// ============================================================================

/** 색상 토큰 */
export interface ColorToken {
  /** 토큰 이름 */
  name: string
  /** CSS Variable 이름 */
  cssVar: string
  /** 색상 값 */
  value: string
  /** 설명 */
  description?: string
  /** 카테고리 (primary, neutral, semantic 등) */
  category: string
}

/** 타이포그래피 토큰 */
export interface TypographyToken {
  /** 토큰 이름 */
  name: string
  /** CSS Variable 이름 */
  cssVar: string
  /** 폰트 크기 */
  fontSize: string
  /** 줄 높이 */
  lineHeight: string
  /** 폰트 웨이트 */
  fontWeight: string | number
  /** 자간 */
  letterSpacing?: string
  /** 설명 */
  description?: string
}

/** 간격 토큰 */
export interface SpacingToken {
  /** 토큰 이름 */
  name: string
  /** CSS Variable 이름 */
  cssVar: string
  /** 값 (px 또는 rem) */
  value: string
  /** 픽셀 값 */
  pxValue: number
  /** 설명 */
  description?: string
}

/** 그림자 토큰 */
export interface ShadowToken {
  /** 토큰 이름 */
  name: string
  /** CSS Variable 이름 */
  cssVar: string
  /** box-shadow 값 */
  value: string
  /** 설명 */
  description?: string
}

/** 모션/애니메이션 토큰 */
export interface MotionToken {
  /** 토큰 이름 */
  name: string
  /** CSS Variable 이름 */
  cssVar: string
  /** duration */
  duration: string
  /** easing */
  easing: string
  /** 설명 */
  description?: string
}

// ============================================================================
// Search
// ============================================================================

/** 검색 결과 항목 */
export interface SearchResultItem {
  /** 컴포넌트 메타 */
  component: ComponentMeta
  /** 매칭된 필드 */
  matchedFields: string[]
  /** 검색 스코어 */
  score: number
}

/** 검색 필터 */
export interface SearchFilters {
  /** 카테고리 필터 */
  category?: ComponentCategory
  /** 안정성 필터 */
  stability?: ComponentStability
  /** 키워드 */
  keywords?: string[]
}

// ============================================================================
// Playground State
// ============================================================================

/** Playground 상태 */
export interface PlaygroundState {
  /** 현재 Props 값 */
  props: Record<string, unknown>
  /** 선택된 컴포넌트 */
  componentName: string
  /** 코드 스니펫 */
  code: string
  /** 에러 메시지 */
  error?: string
}

// ============================================================================
// Icon
// ============================================================================

/** 아이콘 정보 */
export interface IconInfo {
  /** 아이콘 이름 */
  name: string
  /** 카테고리 */
  category: string
  /** 태그 (검색용) */
  tags: string[]
  /** React 컴포넌트 이름 */
  componentName: string
}

// ============================================================================
// Health Dashboard
// ============================================================================

/** Design System 건강 지표 */
export interface HealthMetrics {
  /** 총 컴포넌트 수 */
  totalComponents: number
  /** 안정 컴포넌트 수 */
  stableComponents: number
  /** 평균 A11y 점수 */
  avgA11yScore: number
  /** 문서화 커버리지 (%) */
  documentationCoverage: number
  /** 사용되지 않는 컴포넌트 수 */
  unusedComponents: number
  /** 발견된 Design Smell 수 */
  designSmellCount: number
  /** 마지막 업데이트 */
  lastUpdated: string
}

// ============================================================================
// Changelog
// ============================================================================

/** 변경 타입 */
export type ChangeType = 'added' | 'changed' | 'deprecated' | 'removed' | 'fixed' | 'security'

/** 변경 로그 항목 */
export interface ChangelogEntry {
  /** 버전 */
  version: string
  /** 날짜 */
  date: string
  /** 변경 사항 */
  changes: Array<{
    type: ChangeType
    component?: string
    description: string
  }>
}
