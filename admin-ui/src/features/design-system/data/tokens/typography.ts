/**
 * Typography Tokens - Design System SSOT
 * Phase 4-B: 타이포그래피 스케일 정의
 */

import type { TypographyToken } from '../types'

// ============================================================================
// Display Typography
// ============================================================================

export const displayTypography: TypographyToken[] = [
  {
    name: 'display-2xl',
    cssVar: '--font-display-2xl',
    fontSize: '4.5rem',
    lineHeight: '1',
    fontWeight: 700,
    letterSpacing: '-0.02em',
    description: '가장 큰 디스플레이 - 히어로 섹션',
  },
  {
    name: 'display-xl',
    cssVar: '--font-display-xl',
    fontSize: '3.75rem',
    lineHeight: '1',
    fontWeight: 700,
    letterSpacing: '-0.02em',
    description: '큰 디스플레이 - 랜딩 페이지',
  },
  {
    name: 'display-lg',
    cssVar: '--font-display-lg',
    fontSize: '3rem',
    lineHeight: '1.1',
    fontWeight: 700,
    letterSpacing: '-0.02em',
    description: '디스플레이 - 페이지 헤더',
  },
  {
    name: 'display-md',
    cssVar: '--font-display-md',
    fontSize: '2.25rem',
    lineHeight: '1.2',
    fontWeight: 600,
    letterSpacing: '-0.01em',
    description: '중간 디스플레이 - 섹션 제목',
  },
  {
    name: 'display-sm',
    cssVar: '--font-display-sm',
    fontSize: '1.875rem',
    lineHeight: '1.25',
    fontWeight: 600,
    letterSpacing: '-0.01em',
    description: '작은 디스플레이 - 카드 제목',
  },
]

// ============================================================================
// Heading Typography
// ============================================================================

export const headingTypography: TypographyToken[] = [
  {
    name: 'heading-xl',
    cssVar: '--font-heading-xl',
    fontSize: '1.5rem',
    lineHeight: '1.3',
    fontWeight: 600,
    description: 'H1 - 페이지 제목',
  },
  {
    name: 'heading-lg',
    cssVar: '--font-heading-lg',
    fontSize: '1.25rem',
    lineHeight: '1.4',
    fontWeight: 600,
    description: 'H2 - 섹션 제목',
  },
  {
    name: 'heading-md',
    cssVar: '--font-heading-md',
    fontSize: '1.125rem',
    lineHeight: '1.4',
    fontWeight: 600,
    description: 'H3 - 서브섹션 제목',
  },
  {
    name: 'heading-sm',
    cssVar: '--font-heading-sm',
    fontSize: '1rem',
    lineHeight: '1.5',
    fontWeight: 600,
    description: 'H4 - 작은 제목',
  },
  {
    name: 'heading-xs',
    cssVar: '--font-heading-xs',
    fontSize: '0.875rem',
    lineHeight: '1.5',
    fontWeight: 600,
    description: 'H5 - 레이블 제목',
  },
]

// ============================================================================
// Body Typography
// ============================================================================

export const bodyTypography: TypographyToken[] = [
  {
    name: 'body-xl',
    cssVar: '--font-body-xl',
    fontSize: '1.25rem',
    lineHeight: '1.6',
    fontWeight: 400,
    description: '큰 본문 - 리드 문단',
  },
  {
    name: 'body-lg',
    cssVar: '--font-body-lg',
    fontSize: '1.125rem',
    lineHeight: '1.6',
    fontWeight: 400,
    description: '큰 본문 - 강조 문단',
  },
  {
    name: 'body-md',
    cssVar: '--font-body-md',
    fontSize: '1rem',
    lineHeight: '1.6',
    fontWeight: 400,
    description: '기본 본문 - 일반 텍스트',
  },
  {
    name: 'body-sm',
    cssVar: '--font-body-sm',
    fontSize: '0.875rem',
    lineHeight: '1.5',
    fontWeight: 400,
    description: '작은 본문 - 부가 정보',
  },
  {
    name: 'body-xs',
    cssVar: '--font-body-xs',
    fontSize: '0.75rem',
    lineHeight: '1.5',
    fontWeight: 400,
    description: '가장 작은 본문 - 캡션',
  },
]

// ============================================================================
// Label Typography
// ============================================================================

export const labelTypography: TypographyToken[] = [
  {
    name: 'label-lg',
    cssVar: '--font-label-lg',
    fontSize: '0.9375rem',
    lineHeight: '1.4',
    fontWeight: 500,
    description: '큰 레이블 - 폼 레이블',
  },
  {
    name: 'label-md',
    cssVar: '--font-label-md',
    fontSize: '0.875rem',
    lineHeight: '1.4',
    fontWeight: 500,
    description: '기본 레이블 - 버튼, 탭',
  },
  {
    name: 'label-sm',
    cssVar: '--font-label-sm',
    fontSize: '0.8125rem',
    lineHeight: '1.4',
    fontWeight: 500,
    description: '작은 레이블 - 뱃지, 칩',
  },
  {
    name: 'label-xs',
    cssVar: '--font-label-xs',
    fontSize: '0.75rem',
    lineHeight: '1.4',
    fontWeight: 500,
    letterSpacing: '0.02em',
    description: '가장 작은 레이블 - 상태 표시',
  },
]

// ============================================================================
// Code Typography
// ============================================================================

export const codeTypography: TypographyToken[] = [
  {
    name: 'code-lg',
    cssVar: '--font-code-lg',
    fontSize: '1rem',
    lineHeight: '1.6',
    fontWeight: 400,
    description: '큰 코드 - 코드 블록',
  },
  {
    name: 'code-md',
    cssVar: '--font-code-md',
    fontSize: '0.875rem',
    lineHeight: '1.5',
    fontWeight: 400,
    description: '기본 코드 - 인라인 코드',
  },
  {
    name: 'code-sm',
    cssVar: '--font-code-sm',
    fontSize: '0.8125rem',
    lineHeight: '1.5',
    fontWeight: 400,
    description: '작은 코드 - 터미널',
  },
]

// ============================================================================
// Font Families
// ============================================================================

export interface FontFamily {
  name: string
  cssVar: string
  value: string
  description: string
}

export const fontFamilies: FontFamily[] = [
  {
    name: 'font-sans',
    cssVar: '--font-sans',
    value: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    description: '기본 산세리프 폰트',
  },
  {
    name: 'font-mono',
    cssVar: '--font-mono',
    value: "'SF Mono', 'Fira Code', 'Consolas', monospace",
    description: '모노스페이스 폰트 - 코드',
  },
  {
    name: 'font-display',
    cssVar: '--font-display',
    value: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
    description: '디스플레이 폰트 - 제목',
  },
]

// ============================================================================
// Font Weights
// ============================================================================

export interface FontWeight {
  name: string
  cssVar: string
  value: number
  description: string
}

export const fontWeights: FontWeight[] = [
  {
    name: 'font-thin',
    cssVar: '--font-weight-thin',
    value: 100,
    description: 'Thin - 장식용',
  },
  {
    name: 'font-light',
    cssVar: '--font-weight-light',
    value: 300,
    description: 'Light - 큰 텍스트',
  },
  {
    name: 'font-regular',
    cssVar: '--font-weight-regular',
    value: 400,
    description: 'Regular - 본문',
  },
  {
    name: 'font-medium',
    cssVar: '--font-weight-medium',
    value: 500,
    description: 'Medium - 강조',
  },
  {
    name: 'font-semibold',
    cssVar: '--font-weight-semibold',
    value: 600,
    description: 'Semibold - 제목',
  },
  {
    name: 'font-bold',
    cssVar: '--font-weight-bold',
    value: 700,
    description: 'Bold - 큰 제목',
  },
]

// ============================================================================
// All Typography Export
// ============================================================================

export const allTypographyTokens: TypographyToken[] = [
  ...displayTypography,
  ...headingTypography,
  ...bodyTypography,
  ...labelTypography,
  ...codeTypography,
]

// ============================================================================
// Typography Categories
// ============================================================================

export const typographyCategories = [
  { id: 'display', name: 'Display', tokens: displayTypography },
  { id: 'heading', name: 'Heading', tokens: headingTypography },
  { id: 'body', name: 'Body', tokens: bodyTypography },
  { id: 'label', name: 'Label', tokens: labelTypography },
  { id: 'code', name: 'Code', tokens: codeTypography },
] as const
