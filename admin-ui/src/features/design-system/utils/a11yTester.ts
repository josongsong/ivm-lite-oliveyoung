/**
 * Design System Catalog - A11y Tester Utility
 * Phase 1-D: axe-core 기반 접근성 테스트 유틸리티
 */

import axe, { type AxeResults, type ImpactValue, type Result, type RunOptions } from 'axe-core'
import type { A11yIssue, A11yResult, A11yScore, A11ySeverity } from '../data/types'

// ============================================================================
// Axe-core Configuration
// ============================================================================

/** 기본 axe-core 설정 */
const DEFAULT_AXE_OPTIONS: RunOptions = {
  rules: {
    // 핵심 접근성 규칙
    'color-contrast': { enabled: true },
    'label': { enabled: true },
    'button-name': { enabled: true },
    'image-alt': { enabled: true },
    'link-name': { enabled: true },
    'aria-required-attr': { enabled: true },
    'aria-valid-attr': { enabled: true },
    'focus-order-semantics': { enabled: true },
    'tabindex': { enabled: true },
  },
  resultTypes: ['violations', 'incomplete'],
}

// ============================================================================
// Score Calculation
// ============================================================================

/** Impact별 감점 점수 */
const IMPACT_SCORES: Record<NonNullable<ImpactValue>, number> = {
  critical: 15,
  serious: 10,
  moderate: 5,
  minor: 2,
}

/** Impact를 Severity로 변환 */
function impactToSeverity(impact: ImpactValue | undefined): A11ySeverity {
  if (impact === 'critical' || impact === 'serious') {
    return 'error'
  }
  if (impact === 'moderate') {
    return 'warning'
  }
  return 'info'
}

/** Violation에서 감점 계산 */
function calculateDeduction(violations: Result[]): number {
  return violations.reduce((total, violation) => {
    const impact = violation.impact ?? 'minor'
    const deduction = IMPACT_SCORES[impact] ?? 2
    // 여러 노드에서 발생하면 추가 감점 (최대 2배)
    const nodeMultiplier = Math.min(2, 1 + (violation.nodes.length - 1) * 0.1)
    return total + deduction * nodeMultiplier
  }, 0)
}

/** 점수 계산 (100점 만점) */
function calculateScore(violations: Result[], incomplete: Result[]): number {
  const violationDeduction = calculateDeduction(violations)
  const incompleteDeduction = calculateDeduction(incomplete) * 0.5 // incomplete은 절반 감점
  const totalDeduction = violationDeduction + incompleteDeduction
  return Math.max(0, Math.round(100 - totalDeduction))
}

// ============================================================================
// Issue Extraction
// ============================================================================

/** Axe Result를 A11yIssue로 변환 */
function extractIssues(results: Result[]): A11yIssue[] {
  return results.map((violation) => ({
    id: violation.id,
    message: violation.help,
    severity: impactToSeverity(violation.impact),
    suggestion: violation.helpUrl ? `자세한 정보: ${violation.helpUrl}` : undefined,
  }))
}

// ============================================================================
// Category Scoring
// ============================================================================

/** 규칙을 카테고리별로 분류 */
const RULE_CATEGORIES: Record<string, keyof A11yScore['categories']> = {
  'color-contrast': 'colorContrast',
  'focus-order-semantics': 'focusManagement',
  'tabindex': 'keyboardNavigation',
  'aria-required-attr': 'ariaLabels',
  'aria-valid-attr': 'ariaLabels',
  'button-name': 'ariaLabels',
  'label': 'ariaLabels',
  'image-alt': 'ariaLabels',
  'link-name': 'ariaLabels',
}

/** 카테고리별 점수 계산 */
function calculateCategoryScores(violations: Result[]): A11yScore['categories'] {
  const categoryDeductions: A11yScore['categories'] = {
    colorContrast: 0,
    keyboardNavigation: 0,
    ariaLabels: 0,
    focusManagement: 0,
  }

  violations.forEach((violation) => {
    const category = RULE_CATEGORIES[violation.id]
    if (category) {
      const impact = violation.impact ?? 'minor'
      const deduction = IMPACT_SCORES[impact] ?? 2
      categoryDeductions[category] += deduction
    }
  })

  return {
    colorContrast: Math.max(0, 100 - categoryDeductions.colorContrast),
    keyboardNavigation: Math.max(0, 100 - categoryDeductions.keyboardNavigation),
    ariaLabels: Math.max(0, 100 - categoryDeductions.ariaLabels),
    focusManagement: Math.max(0, 100 - categoryDeductions.focusManagement),
  }
}

// ============================================================================
// Public API
// ============================================================================

/**
 * 단일 HTML 요소의 접근성 테스트
 * @param element 테스트할 HTML 요소
 * @param options 추가 axe-core 옵션
 * @returns A11yResult (점수 + 이슈 목록)
 */
export async function testA11y(
  element: HTMLElement,
  options?: Partial<RunOptions>
): Promise<A11yResult> {
  const axeOptions: RunOptions = {
    ...DEFAULT_AXE_OPTIONS,
    ...options,
  }

  const results: AxeResults = await axe.run(element, axeOptions)
  const score = calculateScore(results.violations, results.incomplete)
  const issues = extractIssues(results.violations)

  return { score, issues }
}

/**
 * 상세 접근성 점수 테스트 (카테고리별 점수 포함)
 * @param element 테스트할 HTML 요소
 * @param options 추가 axe-core 옵션
 * @returns A11yScore (전체 점수 + 카테고리별 점수 + 이슈 목록)
 */
export async function testA11yDetailed(
  element: HTMLElement,
  options?: Partial<RunOptions>
): Promise<A11yScore> {
  const axeOptions: RunOptions = {
    ...DEFAULT_AXE_OPTIONS,
    ...options,
  }

  const results: AxeResults = await axe.run(element, axeOptions)
  const overall = calculateScore(results.violations, results.incomplete)
  const categories = calculateCategoryScores(results.violations)
  const issues = extractIssues(results.violations)

  return {
    overall,
    categories,
    issues,
  }
}

/**
 * 특정 규칙만 테스트
 * @param element 테스트할 HTML 요소
 * @param ruleIds 테스트할 규칙 ID 목록
 * @returns A11yResult
 */
export async function testA11yWithRules(
  element: HTMLElement,
  ruleIds: string[]
): Promise<A11yResult> {
  const rules: Record<string, { enabled: boolean }> = {}
  ruleIds.forEach((id) => {
    rules[id] = { enabled: true }
  })

  const options: RunOptions = {
    rules,
    resultTypes: ['violations', 'incomplete'],
  }

  const results: AxeResults = await axe.run(element, options)
  const score = calculateScore(results.violations, results.incomplete)
  const issues = extractIssues(results.violations)

  return { score, issues }
}

/**
 * 색상 대비 전용 테스트
 * @param element 테스트할 HTML 요소
 * @returns A11yResult
 */
export async function testColorContrast(element: HTMLElement): Promise<A11yResult> {
  return testA11yWithRules(element, ['color-contrast'])
}

/**
 * 키보드 네비게이션 전용 테스트
 * @param element 테스트할 HTML 요소
 * @returns A11yResult
 */
export async function testKeyboardNavigation(element: HTMLElement): Promise<A11yResult> {
  return testA11yWithRules(element, [
    'focus-order-semantics',
    'tabindex',
    'keyboard',
  ])
}

/**
 * ARIA 라벨 전용 테스트
 * @param element 테스트할 HTML 요소
 * @returns A11yResult
 */
export async function testAriaLabels(element: HTMLElement): Promise<A11yResult> {
  return testA11yWithRules(element, [
    'aria-required-attr',
    'aria-valid-attr',
    'button-name',
    'label',
    'image-alt',
    'link-name',
  ])
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * A11y 점수를 등급으로 변환
 * @param score 점수 (0-100)
 * @returns 등급 문자열
 */
export function getA11yGrade(score: number): 'A' | 'B' | 'C' | 'D' | 'F' {
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

/**
 * 이슈 심각도별 개수 반환
 * @param issues 이슈 목록
 * @returns 심각도별 개수
 */
export function countIssuesBySeverity(issues: A11yIssue[]): Record<A11ySeverity, number> {
  return issues.reduce(
    (acc, issue) => {
      acc[issue.severity]++
      return acc
    },
    { error: 0, warning: 0, info: 0 } as Record<A11ySeverity, number>
  )
}

/**
 * 가장 심각한 이슈 반환
 * @param issues 이슈 목록
 * @returns 가장 심각한 이슈 또는 undefined
 */
export function getMostCriticalIssue(issues: A11yIssue[]): A11yIssue | undefined {
  const severityOrder: A11ySeverity[] = ['error', 'warning', 'info']
  for (const severity of severityOrder) {
    const issue = issues.find((i) => i.severity === severity)
    if (issue) return issue
  }
  return undefined
}
