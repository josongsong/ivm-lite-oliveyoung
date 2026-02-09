/**
 * Design System Catalog - Design Smell Detector Utility
 * Phase 1-F: 코드에서 Design Pattern 위반을 감지하는 유틸리티
 */

import type { DesignSmell, DesignSmellSeverity, DesignSmellType } from '../data/types'

// ============================================================================
// Types
// ============================================================================

/** 패턴 규칙 정의 */
interface PatternRule {
  /** 규칙 ID */
  id: string
  /** 규칙 이름 */
  name: string
  /** Smell 타입 */
  type: DesignSmellType
  /** 심각도 */
  severity: DesignSmellSeverity
  /** 설명 */
  description: string
  /** 검사 함수 */
  check: (code: string) => PatternViolation[]
}

/** 패턴 위반 */
interface PatternViolation {
  /** 메시지 */
  message: string
  /** 제안 */
  suggestion: string
  /** 라인 번호 (optional) */
  line?: number
  /** 컨텍스트 코드 */
  context?: string
}

/** 검사 옵션 */
export interface DetectionOptions {
  /** 검사할 규칙 ID (비어있으면 모두 검사) */
  ruleIds?: string[]
  /** 심각도 필터 */
  minSeverity?: DesignSmellSeverity
  /** 타입 필터 */
  types?: DesignSmellType[]
}

// ============================================================================
// Helper Functions
// ============================================================================

/** 심각도 순서 */
const SEVERITY_ORDER: Record<DesignSmellSeverity, number> = {
  error: 0,
  warning: 1,
  info: 2,
}

/** 코드에서 특정 패턴 찾기 (라인 정보 포함) */
function findPatternWithLine(code: string, pattern: RegExp): { match: RegExpMatchArray; line: number }[] {
  const results: { match: RegExpMatchArray; line: number }[] = []
  const lines = code.split('\n')

  lines.forEach((lineContent, index) => {
    const match = lineContent.match(pattern)
    if (match) {
      results.push({ match, line: index + 1 })
    }
  })

  return results
}

/** 위험한 액션 키워드 확인 */
function containsDangerKeyword(code: string): boolean {
  const dangerKeywords = /\b(delete|remove|destroy|clear|reset|erase|삭제|제거|초기화)\b/i
  return dangerKeywords.test(code)
}

// ============================================================================
// Pattern Rules
// ============================================================================

const patternRules: PatternRule[] = [
  // ========================================
  // Button 관련 규칙
  // ========================================
  {
    id: 'danger-action-wrong-variant',
    name: 'Danger 액션에 잘못된 variant 사용',
    type: 'pattern-violation',
    severity: 'warning',
    description: '위험한 액션(삭제 등)에는 danger variant를 사용해야 합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // Button 사용 패턴 찾기
      const buttonPatterns = findPatternWithLine(code, /<Button[^>]*>/g)

      buttonPatterns.forEach(({ match, line }) => {
        const buttonCode = match[0]
        // danger 키워드가 있는데 variant가 danger가 아닌 경우
        const hasDangerContext = containsDangerKeyword(buttonCode) ||
          /onClick\s*=\s*\{[^}]*(delete|remove|destroy|clear)/i.test(buttonCode)
        const hasNonDangerVariant = /variant\s*=\s*["'](secondary|primary|ghost)["']/.test(buttonCode)

        if (hasDangerContext && hasNonDangerVariant) {
          violations.push({
            message: '위험한 액션에 danger variant가 아닌 다른 variant를 사용 중',
            suggestion: 'variant="danger" 사용을 권장합니다',
            line,
            context: buttonCode,
          })
        }
      })

      return violations
    },
  },

  {
    id: 'loading-without-feedback',
    name: 'Loading 상태에서 텍스트 미변경',
    type: 'pattern-violation',
    severity: 'info',
    description: 'Loading 상태일 때 버튼 텍스트도 변경하면 UX가 향상됩니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // loading prop이 있는 Button 찾기
      const loadingButtons = findPatternWithLine(code, /<Button[^>]*loading[^>]*>/g)

      loadingButtons.forEach(({ match, line }) => {
        const buttonCode = match[0]
        // 동적 children이 없는 경우 (고정 텍스트)
        const hasStaticChildren = !/{[^}]*loading[^}]*\?/.test(buttonCode) &&
          !/{[^}]*isLoading[^}]*\?/.test(buttonCode)

        if (hasStaticChildren) {
          violations.push({
            message: 'Loading 상태에서 버튼 텍스트가 변경되지 않음',
            suggestion: 'loading 상태에 따라 텍스트 변경 고려 (예: "저장" → "저장 중...")',
            line,
            context: buttonCode,
          })
        }
      })

      return violations
    },
  },

  {
    id: 'icon-only-button-no-aria',
    name: '아이콘만 있는 버튼에 aria-label 누락',
    type: 'a11y-issue',
    severity: 'error',
    description: '아이콘만 있는 버튼에는 aria-label이 필수입니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // Button에 icon prop이 있지만 children이 없는 경우
      const iconButtons = findPatternWithLine(code, /<Button[^>]*icon\s*=\s*\{[^}]+\}[^>]*\/>/g)

      iconButtons.forEach(({ match, line }) => {
        const buttonCode = match[0]
        const hasAriaLabel = /aria-label\s*=/.test(buttonCode)

        if (!hasAriaLabel) {
          violations.push({
            message: '아이콘만 있는 버튼에 aria-label이 누락됨',
            suggestion: 'aria-label="버튼 목적 설명" 추가 필요',
            line,
            context: buttonCode,
          })
        }
      })

      // IconButton 사용 시에도 확인
      const iconOnlyButtons = findPatternWithLine(code, /<IconButton[^>]*\/>/g)

      iconOnlyButtons.forEach(({ match, line }) => {
        const buttonCode = match[0]
        const hasAriaLabel = /aria-label\s*=/.test(buttonCode)

        if (!hasAriaLabel) {
          violations.push({
            message: 'IconButton에 aria-label이 누락됨',
            suggestion: 'aria-label="버튼 목적 설명" 추가 필요',
            line,
            context: buttonCode,
          })
        }
      })

      return violations
    },
  },

  // ========================================
  // Input 관련 규칙
  // ========================================
  {
    id: 'input-without-label',
    name: 'Label 없는 Input',
    type: 'a11y-issue',
    severity: 'warning',
    description: 'Input에는 연결된 Label이 필요합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // Input 사용 패턴 찾기
      const inputPatterns = findPatternWithLine(code, /<Input[^>]*>/g)

      inputPatterns.forEach(({ match, line }) => {
        const inputCode = match[0]
        // id가 있는지, aria-label이 있는지 확인
        const hasId = /\bid\s*=/.test(inputCode)
        const hasAriaLabel = /aria-label\s*=/.test(inputCode)
        const hasAriaLabelledBy = /aria-labelledby\s*=/.test(inputCode)

        // id가 있으면 Label과 연결 가능, aria-label이 있으면 OK
        if (!hasId && !hasAriaLabel && !hasAriaLabelledBy) {
          violations.push({
            message: 'Input에 Label 연결을 위한 id 또는 aria-label이 없음',
            suggestion: 'id와 <Label htmlFor>를 사용하거나 aria-label 추가',
            line,
            context: inputCode,
          })
        }
      })

      return violations
    },
  },

  {
    id: 'error-without-error-prop',
    name: 'errorMessage만 있고 error prop 누락',
    type: 'pattern-violation',
    severity: 'warning',
    description: 'errorMessage는 error prop이 true일 때만 표시됩니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      const inputPatterns = findPatternWithLine(code, /<Input[^>]*errorMessage[^>]*>/g)

      inputPatterns.forEach(({ match, line }) => {
        const inputCode = match[0]
        const hasErrorProp = /\berror\s*=/.test(inputCode) || /\berror\b/.test(inputCode)

        if (!hasErrorProp) {
          violations.push({
            message: 'errorMessage가 있지만 error prop이 없음',
            suggestion: 'error prop 추가: error={true} 또는 error={hasError}',
            line,
            context: inputCode,
          })
        }
      })

      return violations
    },
  },

  // ========================================
  // Select 관련 규칙
  // ========================================
  {
    id: 'select-value-without-onchange',
    name: 'value만 있고 onChange 누락',
    type: 'pattern-violation',
    severity: 'error',
    description: '제어 컴포넌트로 사용하려면 onChange가 필수입니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      const selectPatterns = findPatternWithLine(code, /<Select[^>]*value\s*=[^>]*>/g)

      selectPatterns.forEach(({ match, line }) => {
        const selectCode = match[0]
        const hasOnChange = /onChange\s*=/.test(selectCode)

        if (!hasOnChange) {
          violations.push({
            message: 'Select에 value는 있지만 onChange가 없음',
            suggestion: 'onChange={setValue} 추가 필요',
            line,
            context: selectCode,
          })
        }
      })

      return violations
    },
  },

  {
    id: 'empty-options-select',
    name: '빈 options 배열',
    type: 'pattern-violation',
    severity: 'info',
    description: '빈 options 배열을 가진 Select는 사용성이 떨어집니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      const emptyOptions = findPatternWithLine(code, /options\s*=\s*\{\s*\[\s*\]\s*\}/g)

      emptyOptions.forEach(({ line }) => {
        violations.push({
          message: 'Select에 빈 options 배열 사용',
          suggestion: 'options가 비어있을 때는 EmptyState 표시 또는 조건부 렌더링 고려',
          line,
        })
      })

      return violations
    },
  },

  // ========================================
  // 일반 컴포넌트 사용 규칙
  // ========================================
  {
    id: 'native-button-usage',
    name: 'Native <button> 사용',
    type: 'inconsistent-usage',
    severity: 'warning',
    description: 'Design System의 Button 컴포넌트를 사용해야 합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // native <button> 태그 찾기
      const nativeButtons = findPatternWithLine(code, /<button[^>]*>/gi)

      nativeButtons.forEach(({ match, line }) => {
        violations.push({
          message: 'Native <button> 태그 사용 감지',
          suggestion: '@/shared/ui의 Button 컴포넌트 사용 권장',
          line,
          context: match[0],
        })
      })

      return violations
    },
  },

  {
    id: 'native-select-usage',
    name: 'Native <select> 사용',
    type: 'inconsistent-usage',
    severity: 'warning',
    description: 'Design System의 Select 컴포넌트를 사용해야 합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      const nativeSelects = findPatternWithLine(code, /<select[^>]*>/gi)

      nativeSelects.forEach(({ match, line }) => {
        violations.push({
          message: 'Native <select> 태그 사용 감지',
          suggestion: '@/shared/ui의 Select 컴포넌트 사용 권장',
          line,
          context: match[0],
        })
      })

      return violations
    },
  },

  {
    id: 'deprecated-component-usage',
    name: 'Deprecated 컴포넌트 사용',
    type: 'deprecated-usage',
    severity: 'warning',
    description: '사용 중단된 컴포넌트가 사용되고 있습니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // deprecated 컴포넌트 목록 (예시)
      const deprecatedComponents = [
        { name: 'OldButton', replacement: 'Button' },
        { name: 'LegacyInput', replacement: 'Input' },
      ]

      deprecatedComponents.forEach(({ name, replacement }) => {
        const pattern = new RegExp(`<${name}[^>]*>`, 'g')
        const usages = findPatternWithLine(code, pattern)

        usages.forEach(({ line, match }) => {
          violations.push({
            message: `Deprecated 컴포넌트 ${name} 사용`,
            suggestion: `${replacement}로 마이그레이션 필요`,
            line,
            context: match[0],
          })
        })
      })

      return violations
    },
  },

  // ========================================
  // 스타일 관련 규칙
  // ========================================
  {
    id: 'inline-style-usage',
    name: 'Inline style 과다 사용',
    type: 'pattern-violation',
    severity: 'info',
    description: 'Inline style 대신 CSS 클래스나 Design Token 사용을 권장합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // style={{ ... }} 패턴 찾기
      const inlineStyles = findPatternWithLine(code, /style\s*=\s*\{\{[^}]+\}\}/g)

      // 5개 이상 사용 시 경고
      if (inlineStyles.length >= 5) {
        violations.push({
          message: `Inline style이 ${inlineStyles.length}회 사용됨`,
          suggestion: 'CSS Module 또는 Design Token 사용 고려',
        })
      }

      return violations
    },
  },

  {
    id: 'hardcoded-color',
    name: 'Hardcoded 색상 값',
    type: 'pattern-violation',
    severity: 'info',
    description: 'Design Token의 CSS Variable을 사용해야 합니다',
    check: (code: string): PatternViolation[] => {
      const violations: PatternViolation[] = []

      // #xxxxxx 또는 rgb(...) 패턴
      const hexColors = findPatternWithLine(code, /#[0-9a-fA-F]{3,6}\b/g)
      const rgbColors = findPatternWithLine(code, /rgb\([^)]+\)/g)

      const colorUsages = [...hexColors, ...rgbColors]

      if (colorUsages.length > 0) {
        violations.push({
          message: `Hardcoded 색상 값이 ${colorUsages.length}회 감지됨`,
          suggestion: 'var(--color-xxx) 형태의 CSS Variable 사용 권장',
        })
      }

      return violations
    },
  },
]

// ============================================================================
// Public API
// ============================================================================

/**
 * 코드에서 Design Smell 감지
 * @param code 분석할 코드
 * @param options 검사 옵션
 * @returns 감지된 Design Smell 목록
 */
export function detectDesignSmells(code: string, options?: DetectionOptions): DesignSmell[] {
  const smells: DesignSmell[] = []

  // 필터링할 규칙 결정
  let rulesToCheck = patternRules
  if (options?.ruleIds?.length) {
    rulesToCheck = patternRules.filter((r) => options.ruleIds!.includes(r.id))
  }
  if (options?.types?.length) {
    rulesToCheck = rulesToCheck.filter((r) => options.types!.includes(r.type))
  }

  // 각 규칙 실행
  rulesToCheck.forEach((rule) => {
    const violations = rule.check(code)

    violations.forEach((violation) => {
      smells.push({
        type: rule.type,
        message: violation.message,
        severity: rule.severity,
        suggestion: violation.suggestion,
        line: violation.line,
      })
    })
  })

  // 심각도 필터링
  if (options?.minSeverity) {
    const minOrder = SEVERITY_ORDER[options.minSeverity]
    return smells.filter((s) => SEVERITY_ORDER[s.severity] <= minOrder)
  }

  // 심각도 순으로 정렬
  return smells.sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity])
}

/**
 * 특정 파일의 Design Smell 감지 (빌드 타임용)
 */
export function detectDesignSmellsInFile(
  filePath: string,
  code: string,
  options?: DetectionOptions
): DesignSmell[] {
  const smells = detectDesignSmells(code, options)

  // 파일 경로 추가
  return smells.map((smell) => ({
    ...smell,
    file: filePath,
  }))
}

/**
 * 모든 규칙 목록 조회
 */
export function getAllRules(): Pick<PatternRule, 'id' | 'name' | 'type' | 'severity' | 'description'>[] {
  return patternRules.map(({ id, name, type, severity, description }) => ({
    id,
    name,
    type,
    severity,
    description,
  }))
}

/**
 * 특정 규칙으로만 검사
 */
export function detectWithRule(code: string, ruleId: string): DesignSmell[] {
  return detectDesignSmells(code, { ruleIds: [ruleId] })
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Smell 심각도별 개수 반환
 */
export function countSmellsBySeverity(smells: DesignSmell[]): Record<DesignSmellSeverity, number> {
  return smells.reduce(
    (acc, smell) => {
      acc[smell.severity]++
      return acc
    },
    { error: 0, warning: 0, info: 0 } as Record<DesignSmellSeverity, number>
  )
}

/**
 * Smell 타입별 개수 반환
 */
export function countSmellsByType(smells: DesignSmell[]): Record<DesignSmellType, number> {
  return smells.reduce(
    (acc, smell) => {
      acc[smell.type]++
      return acc
    },
    {
      'pattern-violation': 0,
      'inconsistent-usage': 0,
      'a11y-issue': 0,
      'deprecated-usage': 0,
    } as Record<DesignSmellType, number>
  )
}

/**
 * 가장 심각한 Smell 반환
 */
export function getMostSevereSmell(smells: DesignSmell[]): DesignSmell | undefined {
  if (smells.length === 0) return undefined

  return smells.reduce((most, current) => {
    return SEVERITY_ORDER[current.severity] < SEVERITY_ORDER[most.severity] ? current : most
  })
}

/**
 * 코드 품질 점수 계산 (100점 만점)
 */
export function calculateCodeQualityScore(smells: DesignSmell[]): number {
  const deductions: Record<DesignSmellSeverity, number> = {
    error: 10,
    warning: 5,
    info: 1,
  }

  const totalDeduction = smells.reduce((sum, smell) => sum + deductions[smell.severity], 0)
  return Math.max(0, 100 - totalDeduction)
}
