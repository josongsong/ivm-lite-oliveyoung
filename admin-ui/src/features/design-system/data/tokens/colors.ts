/**
 * Color Tokens - Design System SSOT
 * Phase 4-A: 컬러 팔레트 정의
 */

import type { ColorToken } from '../types'

// ============================================================================
// Primary Colors
// ============================================================================

export const primaryColors: ColorToken[] = [
  {
    name: 'primary-50',
    cssVar: '--color-primary-50',
    value: '#eff6ff',
    category: 'primary',
    description: '가장 밝은 프라이머리 색상 - 배경, 호버 상태에 사용',
  },
  {
    name: 'primary-100',
    cssVar: '--color-primary-100',
    value: '#dbeafe',
    category: 'primary',
    description: '밝은 프라이머리 색상 - 선택 상태 배경',
  },
  {
    name: 'primary-200',
    cssVar: '--color-primary-200',
    value: '#bfdbfe',
    category: 'primary',
    description: '밝은 프라이머리 색상 - 비활성 상태',
  },
  {
    name: 'primary-300',
    cssVar: '--color-primary-300',
    value: '#93c5fd',
    category: 'primary',
    description: '중간 밝기 프라이머리 - 보더, 아이콘',
  },
  {
    name: 'primary-400',
    cssVar: '--color-primary-400',
    value: '#60a5fa',
    category: 'primary',
    description: '중간 프라이머리 - 호버 상태',
  },
  {
    name: 'primary-500',
    cssVar: '--color-primary-500',
    value: '#3b82f6',
    category: 'primary',
    description: '기본 프라이머리 색상 - 버튼, 링크',
  },
  {
    name: 'primary-600',
    cssVar: '--color-primary-600',
    value: '#2563eb',
    category: 'primary',
    description: '진한 프라이머리 - 활성 상태',
  },
  {
    name: 'primary-700',
    cssVar: '--color-primary-700',
    value: '#1d4ed8',
    category: 'primary',
    description: '더 진한 프라이머리 - 포커스 상태',
  },
  {
    name: 'primary-800',
    cssVar: '--color-primary-800',
    value: '#1e40af',
    category: 'primary',
    description: '어두운 프라이머리 - 다크모드 배경',
  },
  {
    name: 'primary-900',
    cssVar: '--color-primary-900',
    value: '#1e3a8a',
    category: 'primary',
    description: '가장 어두운 프라이머리 - 텍스트',
  },
]

// ============================================================================
// Neutral Colors (Grays)
// ============================================================================

export const neutralColors: ColorToken[] = [
  {
    name: 'neutral-50',
    cssVar: '--color-neutral-50',
    value: '#fafafa',
    category: 'neutral',
    description: '가장 밝은 회색 - 페이지 배경',
  },
  {
    name: 'neutral-100',
    cssVar: '--color-neutral-100',
    value: '#f4f4f5',
    category: 'neutral',
    description: '밝은 회색 - 카드 배경',
  },
  {
    name: 'neutral-200',
    cssVar: '--color-neutral-200',
    value: '#e4e4e7',
    category: 'neutral',
    description: '밝은 회색 - 구분선',
  },
  {
    name: 'neutral-300',
    cssVar: '--color-neutral-300',
    value: '#d4d4d8',
    category: 'neutral',
    description: '중간 회색 - 비활성 보더',
  },
  {
    name: 'neutral-400',
    cssVar: '--color-neutral-400',
    value: '#a1a1aa',
    category: 'neutral',
    description: '중간 회색 - 플레이스홀더',
  },
  {
    name: 'neutral-500',
    cssVar: '--color-neutral-500',
    value: '#71717a',
    category: 'neutral',
    description: '기본 회색 - 보조 텍스트',
  },
  {
    name: 'neutral-600',
    cssVar: '--color-neutral-600',
    value: '#52525b',
    category: 'neutral',
    description: '진한 회색 - 아이콘',
  },
  {
    name: 'neutral-700',
    cssVar: '--color-neutral-700',
    value: '#3f3f46',
    category: 'neutral',
    description: '어두운 회색 - 부제목',
  },
  {
    name: 'neutral-800',
    cssVar: '--color-neutral-800',
    value: '#27272a',
    category: 'neutral',
    description: '더 어두운 회색 - 제목',
  },
  {
    name: 'neutral-900',
    cssVar: '--color-neutral-900',
    value: '#18181b',
    category: 'neutral',
    description: '가장 어두운 회색 - 본문 텍스트',
  },
]

// ============================================================================
// Semantic Colors
// ============================================================================

export const semanticColors: ColorToken[] = [
  // Success
  {
    name: 'success',
    cssVar: '--color-success',
    value: '#22c55e',
    category: 'semantic',
    description: '성공 상태 - 완료, 승인, 저장됨',
  },
  {
    name: 'success-light',
    cssVar: '--color-success-light',
    value: '#dcfce7',
    category: 'semantic',
    description: '성공 배경색',
  },
  {
    name: 'success-dark',
    cssVar: '--color-success-dark',
    value: '#16a34a',
    category: 'semantic',
    description: '성공 진한 색 - 호버',
  },
  // Warning
  {
    name: 'warning',
    cssVar: '--color-warning',
    value: '#f59e0b',
    category: 'semantic',
    description: '경고 상태 - 주의, 대기 중',
  },
  {
    name: 'warning-light',
    cssVar: '--color-warning-light',
    value: '#fef3c7',
    category: 'semantic',
    description: '경고 배경색',
  },
  {
    name: 'warning-dark',
    cssVar: '--color-warning-dark',
    value: '#d97706',
    category: 'semantic',
    description: '경고 진한 색 - 호버',
  },
  // Error
  {
    name: 'error',
    cssVar: '--color-error',
    value: '#ef4444',
    category: 'semantic',
    description: '에러 상태 - 실패, 오류, 삭제',
  },
  {
    name: 'error-light',
    cssVar: '--color-error-light',
    value: '#fee2e2',
    category: 'semantic',
    description: '에러 배경색',
  },
  {
    name: 'error-dark',
    cssVar: '--color-error-dark',
    value: '#dc2626',
    category: 'semantic',
    description: '에러 진한 색 - 호버',
  },
  // Info
  {
    name: 'info',
    cssVar: '--color-info',
    value: '#3b82f6',
    category: 'semantic',
    description: '정보 상태 - 안내, 도움말',
  },
  {
    name: 'info-light',
    cssVar: '--color-info-light',
    value: '#dbeafe',
    category: 'semantic',
    description: '정보 배경색',
  },
  {
    name: 'info-dark',
    cssVar: '--color-info-dark',
    value: '#2563eb',
    category: 'semantic',
    description: '정보 진한 색 - 호버',
  },
]

// ============================================================================
// Accent Colors (Purple Theme)
// ============================================================================

export const accentColors: ColorToken[] = [
  {
    name: 'accent-purple',
    cssVar: '--accent-purple',
    value: '#8b5cf6',
    category: 'accent',
    description: '주요 액센트 색상 - 브랜딩, 강조',
  },
  {
    name: 'accent-purple-light',
    cssVar: '--accent-purple-light',
    value: '#a78bfa',
    category: 'accent',
    description: '밝은 액센트 - 호버 상태',
  },
  {
    name: 'accent-purple-dark',
    cssVar: '--accent-purple-dark',
    value: '#7c3aed',
    category: 'accent',
    description: '진한 액센트 - 활성 상태',
  },
  {
    name: 'accent-pink',
    cssVar: '--accent-pink',
    value: '#ec4899',
    category: 'accent',
    description: '보조 액센트 - 하이라이트',
  },
  {
    name: 'accent-cyan',
    cssVar: '--accent-cyan',
    value: '#06b6d4',
    category: 'accent',
    description: '보조 액센트 - 링크, 참조',
  },
]

// ============================================================================
// Background Colors
// ============================================================================

export const backgroundColors: ColorToken[] = [
  {
    name: 'bg-primary',
    cssVar: '--bg-primary',
    value: '#0f0f0f',
    category: 'background',
    description: '기본 페이지 배경 (다크 모드)',
  },
  {
    name: 'bg-secondary',
    cssVar: '--bg-secondary',
    value: '#1a1a1a',
    category: 'background',
    description: '카드, 패널 배경',
  },
  {
    name: 'bg-tertiary',
    cssVar: '--bg-tertiary',
    value: '#252525',
    category: 'background',
    description: '호버, 선택 상태 배경',
  },
  {
    name: 'bg-elevated',
    cssVar: '--bg-elevated',
    value: '#2a2a2a',
    category: 'background',
    description: '드롭다운, 모달 배경',
  },
]

// ============================================================================
// Text Colors
// ============================================================================

export const textColors: ColorToken[] = [
  {
    name: 'text-primary',
    cssVar: '--text-primary',
    value: '#ffffff',
    category: 'text',
    description: '기본 텍스트 - 제목, 본문',
  },
  {
    name: 'text-secondary',
    cssVar: '--text-secondary',
    value: '#a1a1aa',
    category: 'text',
    description: '보조 텍스트 - 설명, 라벨',
  },
  {
    name: 'text-muted',
    cssVar: '--text-muted',
    value: '#71717a',
    category: 'text',
    description: '희미한 텍스트 - 플레이스홀더',
  },
  {
    name: 'text-disabled',
    cssVar: '--text-disabled',
    value: '#52525b',
    category: 'text',
    description: '비활성 텍스트',
  },
]

// ============================================================================
// Border Colors
// ============================================================================

export const borderColors: ColorToken[] = [
  {
    name: 'border-color',
    cssVar: '--border-color',
    value: '#2a2a2a',
    category: 'border',
    description: '기본 보더 색상',
  },
  {
    name: 'border-hover',
    cssVar: '--border-hover',
    value: '#3a3a3a',
    category: 'border',
    description: '호버 보더 색상',
  },
  {
    name: 'border-focus',
    cssVar: '--border-focus',
    value: '#8b5cf6',
    category: 'border',
    description: '포커스 보더 색상',
  },
]

// ============================================================================
// All Colors Export
// ============================================================================

export const allColorTokens: ColorToken[] = [
  ...primaryColors,
  ...neutralColors,
  ...semanticColors,
  ...accentColors,
  ...backgroundColors,
  ...textColors,
  ...borderColors,
]

// ============================================================================
// Color Categories
// ============================================================================

export const colorCategories = [
  { id: 'primary', name: 'Primary', tokens: primaryColors },
  { id: 'neutral', name: 'Neutral', tokens: neutralColors },
  { id: 'semantic', name: 'Semantic', tokens: semanticColors },
  { id: 'accent', name: 'Accent', tokens: accentColors },
  { id: 'background', name: 'Background', tokens: backgroundColors },
  { id: 'text', name: 'Text', tokens: textColors },
  { id: 'border', name: 'Border', tokens: borderColors },
] as const
