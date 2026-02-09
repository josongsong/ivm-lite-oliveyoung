/**
 * Design System Catalog - Usage Tracker Utility
 * Phase 1-E: 코드베이스에서 컴포넌트 사용 위치를 추적하는 유틸리티
 *
 * NOTE: 이 유틸리티는 빌드 타임 또는 CLI에서 실행됩니다.
 * 브라우저 환경에서는 pre-computed 데이터를 사용해야 합니다.
 */

import type { ComponentStats, UsageInfo } from '../data/types'

// ============================================================================
// Types
// ============================================================================

/** 검색 옵션 */
export interface UsageSearchOptions {
  /** 검색할 루트 디렉토리 */
  rootDir?: string
  /** 제외할 패턴 */
  excludePatterns?: string[]
  /** 포함할 확장자 */
  extensions?: string[]
  /** 최대 결과 수 */
  maxResults?: number
}

/** 코드 스니펫 추출 옵션 */
export interface SnippetOptions {
  /** 컨텍스트 라인 수 (앞뒤) */
  contextLines?: number
  /** 최대 스니펫 길이 */
  maxLength?: number
}

// ============================================================================
// Constants
// ============================================================================

/** Future use: Node.js file system analysis - file extensions to scan */
export const FILE_EXTENSIONS = ['.tsx', '.ts', '.jsx', '.js']

/** Future use: Node.js file system analysis - patterns to exclude */
export const EXCLUDE_PATTERNS = [
  '**/node_modules/**',
  '**/*.test.ts',
  '**/*.test.tsx',
  '**/*.spec.ts',
  '**/*.spec.tsx',
  '**/__tests__/**',
  '**/dist/**',
  '**/build/**',
  '**/coverage/**',
]

const DEFAULT_SNIPPET_OPTIONS: Required<SnippetOptions> = {
  contextLines: 2,
  maxLength: 200,
}

// ============================================================================
// Browser-compatible Usage Analysis (Static Analysis at Build Time)
// ============================================================================

/**
 * 미리 계산된 사용 데이터 캐시
 * 빌드 시점에 생성된 데이터를 저장
 */
let precomputedUsageData: Map<string, UsageInfo[]> = new Map()

/**
 * 미리 계산된 사용 데이터 설정
 * 빌드 시점에 호출
 */
export function setPrecomputedUsageData(data: Record<string, UsageInfo[]>): void {
  precomputedUsageData = new Map(Object.entries(data))
}

/**
 * 미리 계산된 데이터에서 컴포넌트 사용 정보 조회
 * 브라우저 환경에서 사용
 */
export function getComponentUsage(componentName: string): UsageInfo[] {
  return precomputedUsageData.get(componentName) ?? []
}

/**
 * 모든 컴포넌트의 사용 통계 조회
 */
export function getAllComponentStats(): ComponentStats[] {
  const stats: ComponentStats[] = []

  precomputedUsageData.forEach((usages, name) => {
    const files = [...new Set(usages.map((u) => u.filePath))]
    const totalCount = usages.reduce((sum, u) => sum + u.count, 0)

    stats.push({
      name,
      count: totalCount,
      files,
      coOccurrences: {}, // co-occurrence는 별도 분석 필요
    })
  })

  return stats.sort((a, b) => b.count - a.count)
}

// ============================================================================
// Code Analysis Functions (For Build-time / CLI use)
// ============================================================================

/**
 * 코드 문자열에서 컴포넌트 사용 찾기
 * JSX 패턴 매칭
 */
export function findComponentUsagesInCode(
  code: string,
  componentName: string
): { lineNumber: number; snippet: string; count: number }[] {
  const results: { lineNumber: number; snippet: string; count: number }[] = []
  const lines = code.split('\n')

  // JSX 패턴: <ComponentName ... 또는 <ComponentName>
  const jsxPattern = new RegExp(`<${componentName}[\\s/>]`, 'g')

  lines.forEach((line, index) => {
    const matches = line.match(jsxPattern)
    if (matches && matches.length > 0) {
      results.push({
        lineNumber: index + 1,
        snippet: line.trim().slice(0, DEFAULT_SNIPPET_OPTIONS.maxLength),
        count: matches.length,
      })
    }
  })

  return results
}

/**
 * Import 문에서 컴포넌트 import 확인
 */
export function isComponentImported(code: string, componentName: string): boolean {
  // Named import: import { ComponentName } from '...'
  const namedImportPattern = new RegExp(
    `import\\s*{[^}]*\\b${componentName}\\b[^}]*}\\s*from`,
    'm'
  )

  // Default import: import ComponentName from '...'
  const defaultImportPattern = new RegExp(
    `import\\s+${componentName}\\s+from`,
    'm'
  )

  return namedImportPattern.test(code) || defaultImportPattern.test(code)
}

/**
 * 코드에서 사용된 모든 shared/ui 컴포넌트 추출
 */
export function extractSharedUIComponents(code: string): string[] {
  const components: Set<string> = new Set()

  // @/shared/ui에서 import된 컴포넌트 찾기
  const sharedUIImportPattern = /import\s*{([^}]+)}\s*from\s*['"]@\/shared\/ui['"]/g
  let match: RegExpExecArray | null

  while ((match = sharedUIImportPattern.exec(code)) !== null) {
    const imports = match[1]
    // 각 import된 이름 추출
    imports.split(',').forEach((imp) => {
      const name = imp.split(' as ')[0].trim()
      if (name && /^[A-Z]/.test(name)) {
        components.add(name)
      }
    })
  }

  return [...components]
}

/**
 * 코드에서 컴포넌트 사용 컨텍스트 추출 (스니펫)
 */
export function extractUsageSnippet(
  code: string,
  lineNumber: number,
  options: SnippetOptions = {}
): string {
  const opts = { ...DEFAULT_SNIPPET_OPTIONS, ...options }
  const lines = code.split('\n')

  const startLine = Math.max(0, lineNumber - 1 - opts.contextLines)
  const endLine = Math.min(lines.length, lineNumber + opts.contextLines)

  const snippetLines = lines.slice(startLine, endLine)
  const snippet = snippetLines.join('\n')

  if (snippet.length > opts.maxLength) {
    return snippet.slice(0, opts.maxLength - 3) + '...'
  }

  return snippet
}

/**
 * 피처 이름 추출 (파일 경로에서)
 */
export function extractFeatureName(filePath: string): string | undefined {
  // features/xxx 패턴 매칭
  const match = filePath.match(/features\/([^/]+)/)
  return match ? match[1] : undefined
}

/**
 * 컴포넌트 co-occurrence 분석
 * 어떤 컴포넌트들이 함께 사용되는지 분석
 */
export function analyzeCoOccurrences(
  componentsPerFile: Map<string, string[]>
): Record<string, Record<string, number>> {
  const coOccurrences: Record<string, Record<string, number>> = {}

  componentsPerFile.forEach((components) => {
    // 같은 파일에 있는 컴포넌트들은 서로 co-occur
    components.forEach((comp) => {
      if (!coOccurrences[comp]) {
        coOccurrences[comp] = {}
      }

      components.forEach((otherComp) => {
        if (comp !== otherComp) {
          coOccurrences[comp][otherComp] = (coOccurrences[comp][otherComp] || 0) + 1
        }
      })
    })
  })

  return coOccurrences
}

// ============================================================================
// Usage Statistics
// ============================================================================

/**
 * 컴포넌트별 사용 통계 생성
 */
export function generateUsageStats(usages: UsageInfo[]): {
  totalUsages: number
  uniqueFiles: number
  featureBreakdown: Record<string, number>
  avgUsagesPerFile: number
} {
  const totalUsages = usages.reduce((sum, u) => sum + u.count, 0)
  const uniqueFiles = new Set(usages.map((u) => u.filePath)).size

  const featureBreakdown: Record<string, number> = {}
  usages.forEach((u) => {
    const feature = u.feature ?? 'other'
    featureBreakdown[feature] = (featureBreakdown[feature] || 0) + u.count
  })

  return {
    totalUsages,
    uniqueFiles,
    featureBreakdown,
    avgUsagesPerFile: uniqueFiles > 0 ? totalUsages / uniqueFiles : 0,
  }
}

/**
 * 가장 많이 사용되는 컴포넌트 N개 반환
 */
export function getTopUsedComponents(stats: ComponentStats[], limit: number = 10): ComponentStats[] {
  return [...stats].sort((a, b) => b.count - a.count).slice(0, limit)
}

/**
 * 사용되지 않는 컴포넌트 찾기
 */
export function findUnusedComponents(
  registeredComponents: string[],
  usageStats: ComponentStats[]
): string[] {
  const usedComponents = new Set(usageStats.map((s) => s.name))
  return registeredComponents.filter((comp) => !usedComponents.has(comp))
}

// ============================================================================
// Link Generation
// ============================================================================

/**
 * GitHub 링크 생성
 */
export function generateGitHubLink(
  filePath: string,
  lineNumber?: number,
  options?: {
    repo?: string
    branch?: string
  }
): string {
  const repo = options?.repo ?? 'oyg-dev/ivm-lite-oliveyoung-full'
  const branch = options?.branch ?? 'main'
  const relativePath = filePath.replace(/^.*?admin-ui\//, 'admin-ui/')
  const lineAnchor = lineNumber ? `#L${lineNumber}` : ''

  return `https://github.com/${repo}/blob/${branch}/${relativePath}${lineAnchor}`
}

/**
 * VSCode 링크 생성 (로컬 개발용)
 */
export function generateVSCodeLink(filePath: string, lineNumber?: number): string {
  const line = lineNumber ?? 1
  return `vscode://file/${filePath}:${line}`
}

// ============================================================================
// Mock Data for Development (브라우저에서 테스트용)
// ============================================================================

export const mockUsageData: Record<string, UsageInfo[]> = {
  Button: [
    {
      filePath: 'src/features/contracts/ui/Contracts.tsx',
      lineNumber: 45,
      count: 3,
      snippet: '<Button variant="primary" onClick={handleSave}>',
      feature: 'contracts',
    },
    {
      filePath: 'src/features/explorer/ui/DataExplorer.tsx',
      lineNumber: 112,
      count: 2,
      snippet: '<Button variant="secondary" onClick={handleRefresh}>',
      feature: 'explorer',
    },
    {
      filePath: 'src/features/outbox/ui/Outbox.tsx',
      lineNumber: 78,
      count: 1,
      snippet: '<Button variant="danger" onClick={handleRetry}>',
      feature: 'outbox',
    },
  ],
  Input: [
    {
      filePath: 'src/features/explorer/components/SearchBar.tsx',
      lineNumber: 23,
      count: 1,
      snippet: '<Input placeholder="Search..." onChange={handleSearch} />',
      feature: 'explorer',
    },
    {
      filePath: 'src/features/playground/components/SampleInput.tsx',
      lineNumber: 56,
      count: 2,
      snippet: '<Input value={value} onChange={setValue} />',
      feature: 'playground',
    },
  ],
  Select: [
    {
      filePath: 'src/shared/ui/EnvironmentSelector.tsx',
      lineNumber: 34,
      count: 1,
      snippet: '<Select value={env} onChange={setEnv} options={envOptions} />',
      feature: 'shared',
    },
    {
      filePath: 'src/features/traces/components/TraceFilters.tsx',
      lineNumber: 67,
      count: 3,
      snippet: '<Select value={filter} onChange={setFilter} />',
      feature: 'traces',
    },
  ],
}

// 개발 환경에서 Mock 데이터 초기화
if (import.meta.env?.DEV) {
  setPrecomputedUsageData(mockUsageData)
}
