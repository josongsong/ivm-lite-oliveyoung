/**
 * Design System Catalog - Component Registry
 * Phase 2-C: 컴포넌트 레지스트리 구조 및 조회 함수
 *
 * SSOT for all registered design system components.
 * Provides query functions for component lookup by name, category, etc.
 */

import type { ComponentCategory, ComponentMeta, ComponentStability } from './types'
import {
  buttonMockData,
  inputMockData,
  mockComponents,
  selectMockData,
} from './mockData'

// ============================================================================
// Component Registry
// ============================================================================

/**
 * Main component registry - 모든 등록된 컴포넌트 메타데이터
 * Mock 데이터로 초기화, 추후 실제 컴포넌트로 확장
 */
export const componentRegistry: ComponentMeta[] = [
  ...mockComponents,
]

// ============================================================================
// Query Functions - By Name
// ============================================================================

/**
 * 이름으로 컴포넌트 메타데이터 조회
 * @param name 컴포넌트 이름 (case-insensitive)
 * @returns ComponentMeta 또는 undefined
 */
export function getComponentByName(name: string): ComponentMeta | undefined {
  const normalizedName = name.toLowerCase()
  return componentRegistry.find(
    (c) => c.name.toLowerCase() === normalizedName
  )
}

/**
 * 이름 패턴으로 컴포넌트 목록 조회 (부분 일치)
 * @param pattern 검색 패턴
 * @returns 매칭된 ComponentMeta 배열
 */
export function searchComponentsByName(pattern: string): ComponentMeta[] {
  const normalizedPattern = pattern.toLowerCase()
  return componentRegistry.filter((c) =>
    c.name.toLowerCase().includes(normalizedPattern) ||
    c.description.toLowerCase().includes(normalizedPattern) ||
    (c.keywords?.some((k) => k.toLowerCase().includes(normalizedPattern)) ?? false)
  )
}

// ============================================================================
// Query Functions - By Category
// ============================================================================

/**
 * 카테고리별 컴포넌트 목록 조회
 * @param category 컴포넌트 카테고리
 * @returns 해당 카테고리의 ComponentMeta 배열
 */
export function getComponentsByCategory(category: ComponentCategory): ComponentMeta[] {
  return componentRegistry.filter((c) => c.category === category)
}

/**
 * 모든 카테고리 목록 조회 (중복 제거)
 * @returns 사용 중인 모든 카테고리 배열
 */
export function getAllCategories(): ComponentCategory[] {
  const categories = componentRegistry.map((c) => c.category)
  return [...new Set(categories)]
}

/**
 * 카테고리별 컴포넌트 수 조회
 * @returns 카테고리별 컴포넌트 수 맵
 */
export function getComponentCountByCategory(): Record<ComponentCategory, number> {
  const counts: Partial<Record<ComponentCategory, number>> = {}

  for (const component of componentRegistry) {
    counts[component.category] = (counts[component.category] ?? 0) + 1
  }

  return counts as Record<ComponentCategory, number>
}

// ============================================================================
// Query Functions - By Stability
// ============================================================================

/**
 * 안정성 레벨별 컴포넌트 목록 조회
 * @param stability 안정성 레벨
 * @returns 해당 안정성의 ComponentMeta 배열
 */
export function getComponentsByStability(stability: ComponentStability): ComponentMeta[] {
  return componentRegistry.filter((c) => c.stability === stability)
}

/**
 * 안정성 레벨별 컴포넌트 수 조회
 * @returns 안정성별 컴포넌트 수 맵
 */
export function getComponentCountByStability(): Record<ComponentStability, number> {
  const counts: Partial<Record<ComponentStability, number>> = {}

  for (const component of componentRegistry) {
    counts[component.stability] = (counts[component.stability] ?? 0) + 1
  }

  return counts as Record<ComponentStability, number>
}

// ============================================================================
// Query Functions - Relationships
// ============================================================================

/**
 * 관련 컴포넌트 목록 조회
 * @param componentName 기준 컴포넌트 이름
 * @returns 관련 컴포넌트 메타데이터 배열
 */
export function getRelatedComponents(componentName: string): ComponentMeta[] {
  const component = getComponentByName(componentName)
  if (!component?.relatedComponents) {
    return []
  }

  return component.relatedComponents
    .map((name) => getComponentByName(name))
    .filter((c): c is ComponentMeta => c !== undefined)
}

// ============================================================================
// Query Functions - Statistics
// ============================================================================

/**
 * 전체 컴포넌트 수 조회
 * @returns 전체 컴포넌트 수
 */
export function getTotalComponentCount(): number {
  return componentRegistry.length
}

/**
 * 안정(stable) 컴포넌트 수 조회
 * @returns stable 상태인 컴포넌트 수
 */
export function getStableComponentCount(): number {
  return componentRegistry.filter((c) => c.stability === 'stable').length
}

/**
 * 평균 접근성 점수 조회
 * @returns 평균 a11y 점수 (0-100)
 */
export function getAverageA11yScore(): number {
  const componentsWithScore = componentRegistry.filter((c) => c.a11yScore?.overall != null)
  if (componentsWithScore.length === 0) {
    return 0
  }

  const totalScore = componentsWithScore.reduce(
    (sum, c) => sum + (c.a11yScore?.overall ?? 0),
    0
  )

  return Math.round(totalScore / componentsWithScore.length)
}

/**
 * 레지스트리 통계 조회
 * @returns 전체 레지스트리 통계
 */
export function getRegistryStats() {
  return {
    total: getTotalComponentCount(),
    stable: getStableComponentCount(),
    avgA11yScore: getAverageA11yScore(),
    byCategory: getComponentCountByCategory(),
    byStability: getComponentCountByStability(),
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * 컴포넌트 존재 여부 확인
 * @param name 컴포넌트 이름
 * @returns 존재 여부
 */
export function hasComponent(name: string): boolean {
  return getComponentByName(name) !== undefined
}

/**
 * 특정 카테고리의 컴포넌트 이름 목록 조회
 * @param category 카테고리
 * @returns 컴포넌트 이름 배열
 */
export function getComponentNamesByCategory(category: ComponentCategory): string[] {
  return getComponentsByCategory(category).map((c) => c.name)
}

/**
 * 모든 컴포넌트 이름 목록 조회
 * @returns 모든 컴포넌트 이름 배열
 */
export function getAllComponentNames(): string[] {
  return componentRegistry.map((c) => c.name)
}

// ============================================================================
// Category Metadata
// ============================================================================

/** 카테고리별 메타데이터 */
export const categoryMetadata: Record<ComponentCategory, { label: string; description: string }> = {
  actions: {
    label: 'Actions',
    description: '사용자 인터랙션을 트리거하는 버튼, 링크 등',
  },
  inputs: {
    label: 'Inputs',
    description: '사용자 입력을 받는 폼 요소들',
  },
  navigation: {
    label: 'Navigation',
    description: '페이지/섹션 간 이동을 위한 컴포넌트',
  },
  feedback: {
    label: 'Feedback',
    description: '상태, 로딩, 알림을 표시하는 컴포넌트',
  },
  layout: {
    label: 'Layout',
    description: '레이아웃과 구조를 정의하는 컴포넌트',
  },
  'data-display': {
    label: 'Data Display',
    description: '데이터를 표시하는 테이블, 리스트 등',
  },
}

// ============================================================================
// Named Exports for Direct Access
// ============================================================================

export { buttonMockData, inputMockData, selectMockData }
