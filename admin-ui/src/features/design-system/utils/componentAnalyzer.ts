/**
 * Component Analyzer Utility
 * 코드베이스에서 컴포넌트 사용 통계를 분석
 *
 * Phase 1-H: Component Analyzer
 *
 * 브라우저 환경에서 동작하도록 설계 (API 호출 기반)
 */

import type { ComponentStats, UsageInfo } from '../data/types'

// ============================================================================
// Types
// ============================================================================

/**
 * 컴포넌트 사용 분석 결과
 */
export interface ComponentAnalysisResult {
  /** 전체 통계 */
  stats: ComponentStats[]
  /** 가장 많이 사용된 컴포넌트 */
  topUsed: ComponentStats[]
  /** 사용되지 않는 컴포넌트 */
  unused: string[]
  /** 함께 자주 쓰이는 컴포넌트 쌍 */
  commonPairs: Array<{ pair: [string, string]; count: number }>
  /** 분석 시간 */
  analyzedAt: string
}

/**
 * 파일별 컴포넌트 사용 정보
 */
export interface FileComponentUsage {
  /** 파일 경로 */
  filePath: string
  /** 사용된 컴포넌트 목록 */
  components: string[]
  /** 컴포넌트별 사용 횟수 */
  counts: Record<string, number>
  /** 코드 스니펫 */
  snippets: Record<string, string[]>
}

/**
 * 분석 옵션
 */
export interface AnalyzeOptions {
  /** 분석할 컴포넌트 목록 (없으면 모든 shared/ui 컴포넌트) */
  components?: string[]
  /** 제외할 경로 패턴 */
  excludePatterns?: string[]
  /** 테스트 파일 포함 여부 */
  includeTests?: boolean
}

// ============================================================================
// Mock Data (브라우저 환경에서는 API로 대체)
// ============================================================================

/**
 * 기본 shared/ui 컴포넌트 목록
 */
export const SHARED_UI_COMPONENTS = [
  'Button',
  'IconButton',
  'Input',
  'TextArea',
  'Select',
  'Tabs',
  'Accordion',
  'Chip',
  'Modal',
  'Table',
  'Pagination',
  'Loading',
  'Label',
  'StatusBadge',
  'Card',
  'Section',
  'Switch',
  'Tooltip',
  'EmptyState',
  'Alert',
  'InfoRow',
  'Skeleton',
] as const

export type SharedUIComponent = (typeof SHARED_UI_COMPONENTS)[number]

// ============================================================================
// Analysis Functions
// ============================================================================

/**
 * 소스 코드에서 컴포넌트 사용 횟수 추출
 *
 * @param sourceCode - 분석할 소스 코드
 * @param componentName - 찾을 컴포넌트 이름
 * @returns 사용 횟수 및 위치 정보
 */
export function countComponentUsage(
  sourceCode: string,
  componentName: string
): { count: number; positions: number[] } {
  // JSX 태그 패턴: <Component 또는 <Component>
  const pattern = new RegExp(`<${componentName}(?:\\s|>|\\/)`, 'g')
  const positions: number[] = []
  let match: RegExpExecArray | null

  while ((match = pattern.exec(sourceCode)) !== null) {
    positions.push(match.index)
  }

  return {
    count: positions.length,
    positions,
  }
}

/**
 * 소스 코드에서 컴포넌트 import 확인
 *
 * @param sourceCode - 분석할 소스 코드
 * @param componentName - 찾을 컴포넌트 이름
 * @returns import 여부 및 import 경로
 */
export function findComponentImport(
  sourceCode: string,
  componentName: string
): { imported: boolean; importPath: string | null } {
  // import { Button } from '@/shared/ui' 패턴
  const namedImportPattern = new RegExp(
    `import\\s*{[^}]*\\b${componentName}\\b[^}]*}\\s*from\\s*['"]([^'"]+)['"]`
  )

  // import Button from '...' 패턴 (default import)
  const defaultImportPattern = new RegExp(
    `import\\s+${componentName}\\s+from\\s*['"]([^'"]+)['"]`
  )

  const namedMatch = sourceCode.match(namedImportPattern)
  if (namedMatch) {
    return { imported: true, importPath: namedMatch[1] }
  }

  const defaultMatch = sourceCode.match(defaultImportPattern)
  if (defaultMatch) {
    return { imported: true, importPath: defaultMatch[1] }
  }

  return { imported: false, importPath: null }
}

/**
 * 코드 스니펫 추출 (컴포넌트 사용 부분)
 *
 * @param sourceCode - 소스 코드
 * @param position - 시작 위치
 * @param contextLines - 전후 포함할 라인 수
 * @returns 코드 스니펫
 */
export function extractSnippet(
  sourceCode: string,
  position: number,
  contextLines: number = 2
): string {
  const lines = sourceCode.split('\n')
  let currentPos = 0
  let targetLineIndex = 0

  // position이 있는 라인 찾기
  for (let i = 0; i < lines.length; i++) {
    if (currentPos + lines[i].length >= position) {
      targetLineIndex = i
      break
    }
    currentPos += lines[i].length + 1 // +1 for newline
  }

  const startLine = Math.max(0, targetLineIndex - contextLines)
  const endLine = Math.min(lines.length - 1, targetLineIndex + contextLines)

  return lines.slice(startLine, endLine + 1).join('\n')
}

/**
 * 파일에서 모든 컴포넌트 사용 분석
 *
 * @param sourceCode - 소스 코드
 * @param components - 분석할 컴포넌트 목록
 * @returns 파일별 사용 정보
 */
export function analyzeFileUsage(
  sourceCode: string,
  components: readonly string[] = SHARED_UI_COMPONENTS
): FileComponentUsage {
  const result: FileComponentUsage = {
    filePath: '',
    components: [],
    counts: {},
    snippets: {},
  }

  for (const component of components) {
    const { imported } = findComponentImport(sourceCode, component)
    if (!imported) continue

    const usage = countComponentUsage(sourceCode, component)
    if (usage.count > 0) {
      result.components.push(component)
      result.counts[component] = usage.count
      result.snippets[component] = usage.positions
        .slice(0, 3) // 최대 3개 스니펫
        .map((pos) => extractSnippet(sourceCode, pos))
    }
  }

  return result
}

// ============================================================================
// Co-occurrence Analysis
// ============================================================================

/**
 * 파일별 사용 정보에서 함께 사용된 컴포넌트 쌍 추출
 *
 * @param fileUsages - 파일별 사용 정보 배열
 * @returns 컴포넌트 쌍별 동시 출현 횟수
 */
export function calculateCoOccurrences(
  fileUsages: FileComponentUsage[]
): Record<string, Record<string, number>> {
  const coOccurrences: Record<string, Record<string, number>> = {}

  for (const file of fileUsages) {
    const components = file.components

    // 각 컴포넌트 쌍에 대해 카운트
    for (let i = 0; i < components.length; i++) {
      for (let j = i + 1; j < components.length; j++) {
        const comp1 = components[i]
        const comp2 = components[j]

        // 양방향으로 기록
        if (!coOccurrences[comp1]) coOccurrences[comp1] = {}
        if (!coOccurrences[comp2]) coOccurrences[comp2] = {}

        coOccurrences[comp1][comp2] = (coOccurrences[comp1][comp2] || 0) + 1
        coOccurrences[comp2][comp1] = (coOccurrences[comp2][comp1] || 0) + 1
      }
    }
  }

  return coOccurrences
}

/**
 * 가장 자주 함께 사용되는 컴포넌트 쌍 추출
 *
 * @param coOccurrences - 동시 출현 맵
 * @param topN - 상위 N개
 * @returns 정렬된 컴포넌트 쌍 배열
 */
export function getTopPairs(
  coOccurrences: Record<string, Record<string, number>>,
  topN: number = 10
): Array<{ pair: [string, string]; count: number }> {
  const pairs: Array<{ pair: [string, string]; count: number }> = []
  const seen = new Set<string>()

  for (const [comp1, relations] of Object.entries(coOccurrences)) {
    for (const [comp2, count] of Object.entries(relations)) {
      const key = [comp1, comp2].sort().join('::')
      if (!seen.has(key)) {
        seen.add(key)
        pairs.push({ pair: [comp1, comp2], count })
      }
    }
  }

  return pairs.sort((a, b) => b.count - a.count).slice(0, topN)
}

// ============================================================================
// Statistics Aggregation
// ============================================================================

/**
 * 파일별 사용 정보를 컴포넌트 통계로 집계
 *
 * @param fileUsages - 파일별 사용 정보 배열
 * @returns 컴포넌트별 통계
 */
export function aggregateStats(fileUsages: FileComponentUsage[]): ComponentStats[] {
  const statsMap: Record<string, ComponentStats> = {}
  const coOccurrences = calculateCoOccurrences(fileUsages)

  for (const file of fileUsages) {
    for (const component of file.components) {
      if (!statsMap[component]) {
        statsMap[component] = {
          name: component,
          count: 0,
          files: [],
          coOccurrences: {},
        }
      }

      statsMap[component].count += file.counts[component]
      if (file.filePath && !statsMap[component].files.includes(file.filePath)) {
        statsMap[component].files.push(file.filePath)
      }
    }
  }

  // coOccurrences 할당
  for (const [name, stats] of Object.entries(statsMap)) {
    stats.coOccurrences = coOccurrences[name] || {}
  }

  return Object.values(statsMap).sort((a, b) => b.count - a.count)
}

/**
 * 사용되지 않는 컴포넌트 찾기
 *
 * @param stats - 컴포넌트 통계
 * @param allComponents - 전체 컴포넌트 목록
 * @returns 사용되지 않는 컴포넌트 이름 배열
 */
export function findUnusedComponents(
  stats: ComponentStats[],
  allComponents: readonly string[] = SHARED_UI_COMPONENTS
): string[] {
  const usedComponents = new Set(stats.map((s) => s.name))
  return allComponents.filter((comp) => !usedComponents.has(comp))
}

// ============================================================================
// Full Analysis
// ============================================================================

/**
 * 전체 분석 수행 (Mock 구현)
 *
 * 실제 환경에서는 백엔드 API를 호출하여 파일 시스템을 스캔해야 함
 * 이 함수는 프론트엔드에서 분석 결과를 받아 처리하는 용도
 *
 * @param fileUsages - 파일별 사용 정보 (백엔드에서 수집)
 * @param options - 분석 옵션
 * @returns 전체 분석 결과
 */
export function analyzeComponents(
  fileUsages: FileComponentUsage[],
  options: AnalyzeOptions = {}
): ComponentAnalysisResult {
  const components = options.components || SHARED_UI_COMPONENTS

  // 통계 집계
  const stats = aggregateStats(fileUsages)

  // 상위 사용 컴포넌트
  const topUsed = stats.slice(0, 10)

  // 미사용 컴포넌트
  const unused = findUnusedComponents(stats, components)

  // 자주 함께 쓰이는 쌍
  const coOccurrences = calculateCoOccurrences(fileUsages)
  const commonPairs = getTopPairs(coOccurrences, 10)

  return {
    stats,
    topUsed,
    unused,
    commonPairs,
    analyzedAt: new Date().toISOString(),
  }
}

// ============================================================================
// Usage Info Conversion
// ============================================================================

/**
 * FileComponentUsage를 UsageInfo 배열로 변환
 *
 * @param fileUsage - 파일별 사용 정보
 * @param componentName - 컴포넌트 이름
 * @returns UsageInfo 배열
 */
export function toUsageInfoList(
  fileUsage: FileComponentUsage,
  componentName: string
): UsageInfo[] {
  const snippets = fileUsage.snippets[componentName] || []
  const count = fileUsage.counts[componentName] || 0

  // 피처 이름 추출 (features/xxx)
  const featureMatch = fileUsage.filePath.match(/features\/([^/]+)/)
  const feature = featureMatch ? featureMatch[1] : undefined

  return snippets.map((snippet, index) => ({
    filePath: fileUsage.filePath,
    lineNumber: index + 1, // 실제 라인 번호는 백엔드에서 계산
    count,
    snippet,
    feature,
  }))
}

// ============================================================================
// Formatting Utilities
// ============================================================================

/**
 * 사용 횟수를 가독성 있는 문자열로 포맷팅
 */
export function formatUsageCount(count: number): string {
  if (count === 0) return 'Not used'
  if (count === 1) return '1 usage'
  return `${count} usages`
}

/**
 * 파일 수를 가독성 있는 문자열로 포맷팅
 */
export function formatFileCount(count: number): string {
  if (count === 0) return 'No files'
  if (count === 1) return '1 file'
  return `${count} files`
}

/**
 * 컴포넌트 사용률 계산 (전체 파일 대비)
 */
export function calculateUsageRate(usedFileCount: number, totalFileCount: number): number {
  if (totalFileCount === 0) return 0
  return Math.round((usedFileCount / totalFileCount) * 100)
}

/**
 * 컴포넌트 통계를 표시용 객체로 변환
 */
export function formatComponentStats(stats: ComponentStats): {
  name: string
  usageText: string
  fileText: string
  topCoOccurrences: Array<{ name: string; count: number }>
} {
  const coOccurrenceEntries = Object.entries(stats.coOccurrences)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 5)
    .map(([name, count]) => ({ name, count }))

  return {
    name: stats.name,
    usageText: formatUsageCount(stats.count),
    fileText: formatFileCount(stats.files.length),
    topCoOccurrences: coOccurrenceEntries,
  }
}
