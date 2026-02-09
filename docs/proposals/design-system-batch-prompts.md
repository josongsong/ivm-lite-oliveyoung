# Design System Catalog 배치별 작업 프롬프트

**목적**: 각 배치 작업자가 복사-붙여넣기하여 바로 사용할 수 있는 독립적인 프롬프트

**작성일**: 2026-02-01  
**버전**: 1.0

---

## Phase 0: 인터페이스 정의

### [Phase 0] 타입 정의 및 Mock 데이터

**의존성**: 없음  
**예상 기간**: 1일  
**브랜치**: `feature/design-system-phase-0-types`

---

```
Design System Catalog의 모든 타입과 인터페이스를 정의하세요.

## 목표
- 모든 작업자가 참조할 수 있는 타입 정의
- Mock 데이터 구조 정의
- API 인터페이스 정의

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/data/types.ts`
- Mock 데이터: `admin-ui/src/features/design-system/data/mockData.ts`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 타입 정의 파일 생성
위치: `admin-ui/src/features/design-system/data/types.ts`

다음 타입들을 모두 정의:

```typescript
// 컴포넌트 카테고리
export type ComponentCategory = 
  | 'actions' 
  | 'forms' 
  | 'feedback' 
  | 'layout' 
  | 'data-display' 
  | 'utilities'

// 안정성 레벨
export type ComponentStability = 'stable' | 'experimental' | 'deprecated'

// 컨트롤 타입
export type ControlType = 'select' | 'boolean' | 'text' | 'number'

// 컨트롤 정의
export interface ControlDefinition {
  type: ControlType
  options?: string[] // select 타입일 때
  default: any
  description?: string
}

// 사용 예제
export interface ComponentExample {
  title: string
  intent: string // 사용 이유
  props: Record<string, any>
  code?: string // 선택적 코드 스니펫
}

// Anti-pattern
export interface AntiPattern {
  title: string
  code: string
  reason: string
  correct: string
}

// 접근성 이슈
export interface A11yIssue {
  id: string
  message: string
  severity: 'error' | 'warning'
}

// 접근성 점수
export interface A11yScore {
  score: number
  issues: A11yIssue[]
}

// 관련 결정사항
export interface RelatedDecision {
  type: 'RFC' | 'ADR'
  id: string
  title: string
  url?: string
}

// 컴포넌트 메타데이터
export interface ComponentMeta {
  name: string
  category: ComponentCategory
  description: string
  component: React.ComponentType<any>
  stability: ComponentStability
  deprecationReason?: string
  controls: Record<string, ControlDefinition>
  examples: ComponentExample[]
  antiPatterns: AntiPattern[]
  related: string[] // 관련 컴포넌트 이름들
  a11y: Record<string, A11yScore> // 상태별 점수
  relatedDecisions?: RelatedDecision[]
}

// 추출된 Props 정보
export interface ExtractedProp {
  name: string
  type: string
  required: boolean
  defaultValue?: any
  description?: string
}

// 사용 위치 정보
export interface UsageInfo {
  path: string
  count: number
  preview: string
  link: string
}

// Design Smell
export interface DesignSmell {
  type: 'pattern-violation' | 'anti-pattern' | 'accessibility'
  message: string
  severity: 'error' | 'warning' | 'info'
  suggestion: string
}

// 프로젝트 설정
export interface ProjectSettings {
  framework: 'react' | 'next' | 'vite'
  style: 'tailwind' | 'css-module' | 'vanilla-css'
  import: 'alias' | 'relative'
}

// A11y 테스트 결과
export interface A11yResult {
  score: number
  issues: A11yIssue[]
}
```

### 2. Mock 데이터 구조 정의
위치: `admin-ui/src/features/design-system/data/mockData.ts`

```typescript
import { ComponentMeta } from './types'
import React from 'react'

// Mock 컴포넌트 레지스트리 (실제 컴포넌트 없이도 작업 가능)
export const mockComponentRegistry: ComponentMeta[] = [
  {
    name: 'Button',
    category: 'actions',
    description: 'Mock Button component',
    component: () => React.createElement('div', null, 'Mock Button'), // 임시 컴포넌트
    stability: 'stable',
    controls: {
      variant: { type: 'select', options: ['primary', 'secondary'], default: 'primary' },
      size: { type: 'select', options: ['sm', 'md', 'lg'], default: 'md' },
    },
    examples: [],
    antiPatterns: [],
    related: [],
    a11y: { default: { score: 95, issues: [] } },
  },
  // Input, Select도 동일하게 Mock 추가
]
```

### 3. 유틸리티 인터페이스 정의
위치: `admin-ui/src/features/design-system/utils/types.ts`

```typescript
import { ExtractedProp, A11yResult, ProjectSettings } from '../data/types'

// Props 추출 함수 시그니처
export type PropsExtractor = (
  componentPath: string,
  componentName: string
) => Promise<ExtractedProp[]>

// 코드 생성 함수 시그니처
export type CodeGenerator = (
  componentName: string,
  props: Record<string, any>,
  options: ProjectSettings
) => string

// A11y 테스트 함수 시그니처
export type A11yTester = (element: HTMLElement) => Promise<A11yResult>
```

## 완료 조건
- [ ] 모든 타입 정의 완료
- [ ] TypeScript 컴파일 에러 없음
- [ ] Mock 데이터 구조 정의
- [ ] 다른 작업자들이 import하여 사용 가능
- [ ] 린트 통과
```

---

## Phase 1: 독립 유틸리티들

### [Phase 1-A] Props 추출 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-props-extractor`

---

```
TypeScript 타입에서 Props 정보를 자동으로 추출하는 유틸리티를 구축하세요.

## 목표
- ts-morph을 사용하여 컴포넌트 Props 타입 분석
- Props Table 자동 생성
- JSDoc 주석에서 설명 추출

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/propsExtractor.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (ExtractedProp)
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 의존성 설치
```bash
cd admin-ui
pnpm add ts-morph
pnpm add -D @types/node
```

### 2. Props 추출 유틸리티
위치: `admin-ui/src/features/design-system/utils/propsExtractor.ts`

```typescript
import { Project, SourceFile, InterfaceDeclaration, PropertySignature } from 'ts-morph'
import { ExtractedProp } from '../data/types'

export async function extractPropsFromComponent(
  componentPath: string,
  componentName: string
): Promise<ExtractedProp[]> {
  const project = new Project()
  const sourceFile = project.addSourceFileAtPath(componentPath)
  
  // Props 인터페이스/타입 찾기
  // 예: ButtonProps, InputProps 등
  const propsInterface = findPropsInterface(sourceFile, componentName)
  
  if (!propsInterface) {
    return []
  }
  
  // 각 prop 추출
  const props: ExtractedProp[] = []
  propsInterface.getProperties().forEach(prop => {
    const propSignature = prop.asKind(NodeKind.PropertySignature)
    if (!propSignature) return
    
    props.push({
      name: prop.getName(),
      type: propSignature.getTypeNode()?.getText() || 'unknown',
      required: !propSignature.hasQuestionToken(),
      defaultValue: extractDefaultValue(propSignature),
      description: extractJSDoc(propSignature)
    })
  })
  
  return props
}

function findPropsInterface(sourceFile: SourceFile, componentName: string): InterfaceDeclaration | null {
  // Button 컴포넌트의 경우 ButtonProps 인터페이스 찾기
  const interfaceName = `${componentName}Props`
  return sourceFile.getInterface(interfaceName) || null
}

function extractDefaultValue(prop: PropertySignature): any {
  // defaultProps 또는 기본 파라미터에서 추출
  // 구현 필요
}

function extractJSDoc(prop: PropertySignature): string | undefined {
  // JSDoc 주석에서 description 추출
  const jsDoc = prop.getJsDocs()[0]
  return jsDoc?.getComment()?.toString()
}
```

### 3. 타입 문자열 포맷팅
- Union 타입: `'primary' | 'secondary'` → `'primary'|'secondary'`
- Optional 타입: `variant?: string` → `variant?: string`
- Function 타입: `(e: Event) => void` → `(e: Event) => void`

### 4. 테스트
- Mock 컴포넌트로 테스트
- Button 컴포넌트로 실제 테스트
- Input, Select 컴포넌트로 테스트

## 완료 조건
- [ ] Props 추출 기능 동작
- [ ] JSDoc 주석에서 description 추출
- [ ] 타입 문자열 포맷팅 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-B] 코드 생성 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-code-generator`

---

```
컴포넌트와 Props를 받아 코드 스니펫을 생성하는 유틸리티를 구축하세요.

## 목표
- 기본 코드 스니펫 생성
- Props 포맷팅
- Import 경로 생성

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/codeGenerator.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (ProjectSettings)
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 코드 생성 유틸리티
위치: `admin-ui/src/features/design-system/utils/codeGenerator.ts`

```typescript
import { ProjectSettings } from '../data/types'

export function generateCodeSnippet(
  componentName: string,
  props: Record<string, any>,
  options?: ProjectSettings
): string {
  const defaultOptions: ProjectSettings = {
    framework: 'react',
    style: 'tailwind',
    import: 'alias'
  }
  
  const settings = { ...defaultOptions, ...options }
  const importPath = settings.import === 'alias'
    ? `@/shared/ui/${componentName.toLowerCase()}`
    : `../shared/ui/${componentName}`
  
  const propsString = formatProps(props)
  
  return `import { ${componentName} } from '${importPath}'

<${componentName}${propsString ? ` ${propsString}` : ''}>
  ${props.children || ''}
</${componentName}>`
}

function formatProps(props: Record<string, any>): string {
  const entries = Object.entries(props)
    .filter(([key, value]) => {
      // children은 별도 처리
      if (key === 'children') return false
      // undefined, null 제외
      if (value === undefined || value === null) return false
      return true
    })
    .map(([key, value]) => {
      if (typeof value === 'string') {
        return `${key}="${value}"`
      }
      if (typeof value === 'boolean') {
        return value ? key : ''
      }
      return `${key}={${JSON.stringify(value)}}`
    })
    .filter(Boolean)
  
  return entries.join(' ')
}
```

### 2. 테스트
- 다양한 Props 조합으로 테스트
- 각 설정 옵션별로 테스트

## 완료 조건
- [ ] 기본 코드 생성 동작
- [ ] Props 포맷팅 정확
- [ ] Import 경로 생성 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-C] 클립보드 훅

**의존성**: Phase 0  
**예상 기간**: 1일  
**브랜치**: `feature/design-system-phase-1-clipboard-hook`

---

```
클립보드 복사 기능을 제공하는 React 훅을 구축하세요.

## 목표
- 클립보드 복사 기능
- 복사 성공 피드백

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/hooks/useClipboard.ts`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. useClipboard 훅
위치: `admin-ui/src/features/design-system/hooks/useClipboard.ts`

```typescript
import { useState } from 'react'

export function useClipboard() {
  const [copied, setCopied] = useState(false)
  
  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (error) {
      console.error('Failed to copy:', error)
    }
  }
  
  return { copy, copied }
}
```

### 2. 테스트
- 복사 기능 테스트
- 피드백 표시 테스트

## 완료 조건
- [ ] 클립보드 복사 동작
- [ ] 복사 성공 피드백 표시
- [ ] 에러 처리 구현
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-D] A11y 테스터 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-a11y-tester`

---

```
axe-core를 사용하여 접근성을 테스트하는 유틸리티를 구축하세요.

## 목표
- 컴포넌트의 접근성 점수 자동 계산
- 이슈 목록 추출

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/a11yTester.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (A11yResult)
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 의존성 설치
```bash
cd admin-ui
pnpm add axe-core
```

### 2. A11y 테스터 유틸리티
위치: `admin-ui/src/features/design-system/utils/a11yTester.ts`

```typescript
import * as axe from 'axe-core'
import { A11yResult, A11yIssue } from '../data/types'

export async function testA11y(element: HTMLElement): Promise<A11yResult> {
  const results = await axe.run(element, {
    rules: {
      'color-contrast': { enabled: true },
      'keyboard-navigation': { enabled: true },
      'aria-labels': { enabled: true },
      'focus-order': { enabled: true }
    }
  })
  
  const score = calculateScore(results.violations, results.incomplete)
  
  return {
    score,
    issues: results.violations.map(v => ({
      id: v.id,
      message: v.help,
      severity: v.impact === 'critical' || v.impact === 'serious' ? 'error' : 'warning'
    }))
  }
}

function calculateScore(violations: any[], incomplete: any[]): number {
  // 100점 만점 기준 계산
  // violations에 따라 감점
  // critical: -10점, serious: -5점, moderate: -2점, minor: -1점
  let score = 100
  
  violations.forEach(v => {
    switch (v.impact) {
      case 'critical':
        score -= 10
        break
      case 'serious':
        score -= 5
        break
      case 'moderate':
        score -= 2
        break
      case 'minor':
        score -= 1
        break
    }
  })
  
  return Math.max(0, score)
}
```

### 3. 테스트
- Mock 요소로 테스트
- 실제 컴포넌트로 테스트

## 완료 조건
- [ ] axe-core 통합 완료
- [ ] 점수 계산 로직 정확
- [ ] 이슈 추출 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-E] Usage Tracker 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-usage-tracker`

---

```
코드베이스에서 컴포넌트 사용 위치를 찾는 유틸리티를 구축하세요.

## 목표
- 코드베이스 스캔하여 컴포넌트 사용 위치 찾기
- 사용 횟수 계산
- 코드 스니펫 추출

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/usageTracker.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (UsageInfo)
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. Usage Tracker 유틸리티
위치: `admin-ui/src/features/design-system/utils/usageTracker.ts`

```typescript
import { UsageInfo } from '../data/types'
import * as fs from 'fs'
import * as path from 'path'
import { glob } from 'glob'

export function trackComponentUsage(componentName: string): UsageInfo[] {
  const usageInfos: UsageInfo[] = []
  const srcDir = path.join(process.cwd(), 'admin-ui/src')
  
  // .tsx, .ts 파일 찾기
  const files = glob.sync('**/*.{ts,tsx}', {
    cwd: srcDir,
    ignore: ['**/node_modules/**', '**/*.test.ts', '**/*.test.tsx']
  })
  
  files.forEach(file => {
    const filePath = path.join(srcDir, file)
    const content = fs.readFileSync(filePath, 'utf-8')
    
    // 컴포넌트 사용 찾기 (정규식 또는 AST 분석)
    const regex = new RegExp(`<${componentName}[\\s>]`, 'g')
    const matches = content.match(regex)
    
    if (matches && matches.length > 0) {
      usageInfos.push({
        path: file,
        count: matches.length,
        preview: extractCodePreview(content, componentName),
        link: generateGitHubLink(file)
      })
    }
  })
  
  return usageInfos
}

function extractCodePreview(content: string, componentName: string): string {
  // 컴포넌트 사용 부분의 코드 스니펫 추출
  const lines = content.split('\n')
  // 구현 필요
  return content.substring(0, 200) // 임시
}

function generateGitHubLink(filePath: string): string {
  // GitHub 링크 생성
  return `https://github.com/.../${filePath}`
}
```

### 2. 테스트
- Mock 파일로 테스트
- 실제 코드베이스로 테스트

## 완료 조건
- [ ] 코드베이스 스캔 기능 동작
- [ ] 사용 횟수 계산 정확
- [ ] 코드 스니펫 추출 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-F] Design Smell Detector 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-design-smell-detector`

---

```
코드에서 Design Pattern 위반을 감지하는 유틸리티를 구축하세요.

## 목표
- 패턴 위반 자동 감지
- 제안사항 제공

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/designSmellDetector.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (DesignSmell)
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. Design Smell Detector 유틸리티
위치: `admin-ui/src/features/design-system/utils/designSmellDetector.ts`

```typescript
import { DesignSmell } from '../data/types'

export function detectDesignSmells(code: string): DesignSmell[] {
  const smells: DesignSmell[] = []
  
  // Pattern: danger 액션인데 secondary 사용
  if (hasDangerousAction(code) && usesVariant(code, 'secondary')) {
    smells.push({
      type: 'pattern-violation',
      message: 'danger 액션인데 secondary variant 사용 중',
      severity: 'warning',
      suggestion: 'variant="danger" 사용 권장'
    })
  }
  
  // Pattern: loading 상태인데 disabled 미설정
  if (hasLoadingState(code) && !hasDisabledProp(code)) {
    smells.push({
      type: 'pattern-violation',
      message: 'loading 상태인데 disabled prop 미설정',
      severity: 'info',
      suggestion: 'loading 시 disabled={true} 권장'
    })
  }
  
  return smells
}

function hasDangerousAction(code: string): boolean {
  // 삭제, 제거 등의 위험한 액션 키워드 확인
  return /delete|remove|destroy|clear/i.test(code)
}

function usesVariant(code: string, variant: string): boolean {
  return new RegExp(`variant=["']${variant}["']`).test(code)
}

function hasLoadingState(code: string): boolean {
  return /loading\s*=\s*{?true}?/.test(code)
}

function hasDisabledProp(code: string): boolean {
  return /disabled\s*=\s*{?true}?/.test(code)
}
```

### 2. 테스트
- 다양한 코드 패턴으로 테스트

## 완료 조건
- [ ] Design Smell 감지 로직 구현
- [ ] 제안사항 제공
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-G] Contrast Calculator 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2일  
**브랜치**: `feature/design-system-phase-1-contrast-calculator`

---

```
색상 대비를 계산하는 유틸리티를 구축하세요.

## 목표
- WCAG 대비 비율 계산
- AAA/AA 통과 여부 판단

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/contrastCalculator.ts`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. Contrast Calculator 유틸리티
위치: `admin-ui/src/features/design-system/utils/contrastCalculator.ts`

```typescript
export interface ContrastResult {
  ratio: number
  levelAA: boolean
  levelAAA: boolean
  levelAALarge: boolean
  levelAAALarge: boolean
}

export function calculateContrast(
  foreground: string,
  background: string
): ContrastResult {
  const fgRgb = hexToRgb(foreground)
  const bgRgb = hexToRgb(background)
  
  const fgLuminance = getRelativeLuminance(fgRgb)
  const bgLuminance = getRelativeLuminance(bgRgb)
  
  const ratio = (Math.max(fgLuminance, bgLuminance) + 0.05) / 
                (Math.min(fgLuminance, bgLuminance) + 0.05)
  
  return {
    ratio: Math.round(ratio * 100) / 100,
    levelAA: ratio >= 4.5,
    levelAAA: ratio >= 7,
    levelAALarge: ratio >= 3,
    levelAAALarge: ratio >= 4.5
  }
}

function hexToRgb(hex: string): [number, number, number] {
  // #RRGGBB를 RGB로 변환
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result
    ? [
        parseInt(result[1], 16),
        parseInt(result[2], 16),
        parseInt(result[3], 16)
      ]
    : [0, 0, 0]
}

function getRelativeLuminance([r, g, b]: [number, number, number]): number {
  const [rs, gs, bs] = [r, g, b].map(val => {
    val = val / 255
    return val <= 0.03928 ? val / 12.92 : Math.pow((val + 0.055) / 1.055, 2.4)
  })
  
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
}
```

### 2. 테스트
- 다양한 색상 조합으로 테스트

## 완료 조건
- [ ] 대비 비율 계산 정확
- [ ] WCAG 기준 확인 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

### [Phase 1-H] Component Analyzer 유틸리티

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-1-component-analyzer`

---

```
코드베이스를 분석하여 컴포넌트 사용 통계를 생성하는 유틸리티를 구축하세요.

## 목표
- 컴포넌트 사용 횟수 계산
- 패턴 분석 (어떤 컴포넌트가 함께 쓰이는지)

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/componentAnalyzer.ts`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. Component Analyzer 유틸리티
위치: `admin-ui/src/features/design-system/utils/componentAnalyzer.ts`

```typescript
export interface ComponentStats {
  name: string
  count: number
  files: string[]
  coOccurrences: Record<string, number> // 함께 쓰이는 컴포넌트
}

export function analyzeComponentUsage(): ComponentStats[] {
  // 코드베이스 스캔
  // 각 컴포넌트 사용 횟수 계산
  // 함께 쓰이는 컴포넌트 분석
  
  // 구현 필요
  return []
}
```

### 2. 테스트
- Mock 코드베이스로 테스트

## 완료 조건
- [ ] 사용 횟수 계산 정확
- [ ] 패턴 분석 정확
- [ ] 테스트 코드 작성
- [ ] 린트 통과
```

---

## Phase 2: 기본 인프라

### [Phase 2-A] 라우팅 설정

**의존성**: Phase 0  
**예상 기간**: 1일  
**브랜치**: `feature/design-system-phase-2-routing`

---

```
Design System 페이지의 라우팅을 설정하세요.

## 목표
- `/design-system` 경로에 접근 가능한 페이지 생성
- 중첩 라우팅 구조 설정

## 레포지토리 구조
- 파일 위치: `admin-ui/src/app/routes/AppRoutes.tsx`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 라우팅 설정
위치: `admin-ui/src/app/routes/AppRoutes.tsx`

```typescript
import { Route, Navigate } from 'react-router-dom'
import { DesignSystemPage } from '@/features/design-system'

// 기존 라우트에 추가
<Route path="/design-system" element={<DesignSystemPage />}>
  <Route index element={<Navigate to="foundations/colors" />} />
  <Route path="foundations/:section" element={<FoundationsSection />} />
  <Route path="components/:category" element={<ComponentCategory />} />
  <Route path="components/:category/:name" element={<ComponentDetail />} />
  <Route path="patterns/:pattern" element={<PatternGuide />} />
  <Route path="resources/:resource" element={<ResourcePage />} />
</Route>
```

### 2. 임시 페이지 컴포넌트 생성
- 각 라우트에 연결할 임시 컴포넌트 생성 (나중에 구현)

## 완료 조건
- [ ] 라우팅 설정 완료
- [ ] 각 경로 접근 가능
- [ ] 린트 통과
```

---

### [Phase 2-B] 레이아웃 컴포넌트

**의존성**: Phase 0  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-2-layout`

---

```
Design System 페이지의 기본 레이아웃 컴포넌트를 구축하세요.

## 목표
- 좌측 사이드바 + 우측 콘텐츠 영역 레이아웃
- 네비게이션 메뉴

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/components/layout/`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 레이아웃 컴포넌트 생성
위치: `admin-ui/src/features/design-system/components/layout/`

- `Sidebar.tsx`: 좌측 네비게이션
  - Foundations 섹션 (Colors, Typography, Spacing, Shadows, Motion)
  - Components 섹션 (Actions, Forms, Feedback, Layout, Data Display, Utilities)
  - Patterns 섹션
  - Resources 섹션
  - 현재 선택된 항목 하이라이트

- `ContentArea.tsx`: 메인 콘텐츠 영역
  - Outlet을 사용하여 중첩 라우팅 렌더링

- `Header.tsx`: 상단 헤더
  - 검색 바 (나중에 구현, 일단 placeholder)
  - 테마 토글 (Dark/Light, 나중에 구현)

### 2. 메인 페이지 컴포넌트
위치: `admin-ui/src/features/design-system/ui/DesignSystemPage.tsx`

```typescript
import { Outlet } from 'react-router-dom'
import { Sidebar } from '../components/layout/Sidebar'
import { ContentArea } from '../components/layout/ContentArea'
import './DesignSystemPage.css'

export function DesignSystemPage() {
  return (
    <div className="design-system-page">
      <Sidebar />
      <ContentArea>
        <Outlet />
      </ContentArea>
    </div>
  )
}
```

### 3. 스타일링
- `DesignSystemPage.css`: 메인 레이아웃 스타일
- `Sidebar.css`: 사이드바 스타일
- 반응형 디자인 고려 (모바일에서는 사이드바 토글)

## 완료 조건
- [ ] 레이아웃 컴포넌트 완성
- [ ] 반응형 디자인 적용
- [ ] 스타일링 완료
- [ ] `/design-system` 접속 시 레이아웃 표시
- [ ] 린트 통과
```

---

### [Phase 2-C] 레지스트리 구조 & Mock 데이터

**의존성**: Phase 0  
**예상 기간**: 1-2일  
**브랜치**: `feature/design-system-phase-2-registry`

---

```
컴포넌트 레지스트리 구조와 Mock 데이터를 구축하세요.

## 목표
- 컴포넌트 레지스트리 구조 생성
- Mock 데이터로 초기화

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/data/componentRegistry.ts`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 레지스트리 파일 생성
위치: `admin-ui/src/features/design-system/data/componentRegistry.ts`

```typescript
import { ComponentMeta, ComponentCategory } from './types'
import { mockComponentRegistry } from './mockData'

export const componentRegistry: ComponentMeta[] = [
  // Mock 데이터로 초기화
  ...mockComponentRegistry
]

export function getComponentByName(name: string): ComponentMeta | undefined {
  return componentRegistry.find(c => c.name === name)
}

export function getComponentsByCategory(category: ComponentCategory): ComponentMeta[] {
  return componentRegistry.filter(c => c.category === category)
}

export function getAllCategories(): ComponentCategory[] {
  return Array.from(new Set(componentRegistry.map(c => c.category)))
}
```

### 2. Mock 데이터 업데이트
- Button, Input, Select의 Mock 데이터 생성

## 완료 조건
- [ ] 레지스트리 구조 완성
- [ ] Mock 데이터 생성
- [ ] 조회 함수 구현
- [ ] 린트 통과
```

---

## Phase 3: 컴포넌트 전시

### [Phase 3-A] Button 전시

**의존성**: Phase 0, Phase 2-C  
**예상 기간**: 3-4일  
**브랜치**: `feature/design-system-phase-3-button-showcase`

---

```
Button 컴포넌트의 전시 페이지를 구현하세요.

## 목표
- Button 컴포넌트 상세 페이지
- Live Preview 섹션
- 다양한 variant, size 표시

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/ui/ComponentDetail.tsx`
- 컴포넌트: `admin-ui/src/features/design-system/components/showcase/`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. 컴포넌트 상세 페이지
위치: `admin-ui/src/features/design-system/ui/ComponentDetail.tsx`

- 컴포넌트 이름 및 설명
- Stability 배지
- Live Preview 섹션
- Props Reference 테이블 (나중에 5-B에서 연결)
- Code Block (나중에 5-C에서 연결)

### 2. Live Preview 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/LivePreview.tsx`

- Button의 다양한 variant, size 표시
- Primary, Secondary, Ghost, Danger
- Small, Medium, Large
- Loading, Disabled 상태

### 3. 레지스트리 등록
- Button을 componentRegistry에 추가
- 실제 Button 컴포넌트 import

```typescript
import { Button } from '@/shared/ui'
import { ComponentMeta } from './types'

export const buttonMeta: ComponentMeta = {
  name: 'Button',
  category: 'actions',
  description: '사용자 액션을 트리거하는 기본 인터랙티브 요소',
  component: Button,
  stability: 'stable',
  controls: {
    variant: { 
      type: 'select', 
      options: ['primary', 'secondary', 'ghost', 'danger'],
      default: 'primary'
    },
    size: { 
      type: 'select', 
      options: ['sm', 'md', 'lg'],
      default: 'md'
    },
    disabled: { type: 'boolean', default: false },
    loading: { type: 'boolean', default: false },
    children: { type: 'text', default: 'Click me' },
  },
  examples: [],
  antiPatterns: [],
  related: ['IconButton'],
  a11y: { default: { score: 95, issues: [] } },
}
```

## 완료 조건
- [ ] Button 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
- [ ] `/design-system/components/actions/button` 접속 시 페이지 표시
- [ ] 린트 통과
```

---

### [Phase 3-B] Input 전시

**의존성**: Phase 0, Phase 2-C  
**예상 기간**: 3-4일  
**브랜치**: `feature/design-system-phase-3-input-showcase`

---

```
Input 컴포넌트의 전시 페이지를 구현하세요.

## 목표
- Input 컴포넌트 상세 페이지
- Live Preview 섹션
- 다양한 상태 표시

## 레포지토리 구조
- Phase 3-A와 동일한 구조
- Input 컴포넌트에 맞게 조정

## 작업 내용
- Phase 3-A와 동일한 구조
- Input 컴포넌트에 맞게 조정

## 완료 조건
- [ ] Input 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
- [ ] 린트 통과
```

---

### [Phase 3-C] Select 전시

**의존성**: Phase 0, Phase 2-C  
**예상 기간**: 3-4일  
**브랜치**: `feature/design-system-phase-3-select-showcase`

---

```
Select 컴포넌트의 전시 페이지를 구현하세요.

## 목표
- Select 컴포넌트 상세 페이지
- Live Preview 섹션
- 다양한 상태 표시

## 레포지토리 구조
- Phase 3-A와 동일한 구조
- Select 컴포넌트에 맞게 조정

## 작업 내용
- Phase 3-A와 동일한 구조
- Select 컴포넌트에 맞게 조정

## 완료 조건
- [ ] Select 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
- [ ] 린트 통과
```

---

## Phase 4: Foundations

### [Phase 4-A] Colors

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 2-3일  
**브랜치**: `feature/design-system-phase-4-colors`

---

```
Colors Foundation 페이지를 구현하세요.

## 목표
- 컬러 팔레트 시각화
- CSS Variable 복사 기능

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/components/foundations/ColorPalette.tsx`
- 토큰: `admin-ui/src/features/design-system/data/tokens/colors.json`
- 상세 구조: `docs/proposals/design-system-catalog-repo-structure.md` 참고

## 작업 내용

### 1. Design Token SSOT
위치: `admin-ui/src/features/design-system/data/tokens/colors.json`

```json
{
  "primary": {
    "50": "#eff6ff",
    "100": "#dbeafe",
    "200": "#bfdbfe",
    "300": "#93c5fd",
    "400": "#60a5fa",
    "500": "#3b82f6",
    "600": "#2563eb",
    "700": "#1d4ed8",
    "800": "#1e40af",
    "900": "#1e3a8a"
  },
  "semantic": {
    "success": "#22c55e",
    "warning": "#f59e0b",
    "error": "#ef4444",
    "info": "#3b82f6"
  }
}
```

### 2. ColorPalette 컴포넌트
위치: `admin-ui/src/features/design-system/components/foundations/ColorPalette.tsx`

- 컬러 팔레트를 그리드로 표시
- 각 색상 클릭 시 CSS Variable 복사
- 예: `--color-primary-500` 복사

### 3. FoundationsSection 페이지
- Colors 섹션 렌더링

## 완료 조건
- [ ] ColorPalette 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] `/design-system/foundations/colors` 접속 시 페이지 표시
- [ ] 린트 통과
```

---

### [Phase 4-B] Typography

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 2일  
**브랜치**: `feature/design-system-phase-4-typography`

---

```
Typography Foundation 페이지를 구현하세요.

## 목표
- 폰트 스케일 시각화
- CSS Variable 복사 기능

## 레포지토리 구조
- Phase 4-A와 동일한 구조
- Typography에 맞게 조정

## 작업 내용
- Phase 4-A와 동일한 구조
- Typography에 맞게 조정

## 완료 조건
- [ ] TypographyScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] 린트 통과
```

---

### [Phase 4-C] Spacing

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 2일  
**브랜치**: `feature/design-system-phase-4-spacing`

---

```
Spacing Foundation 페이지를 구현하세요.

## 목표
- 간격 시스템 시각화
- CSS Variable 복사 기능

## 레포지토리 구조
- Phase 4-A와 동일한 구조
- Spacing에 맞게 조정

## 작업 내용
- Phase 4-A와 동일한 구조
- Spacing에 맞게 조정

## 완료 조건
- [ ] SpacingScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] 린트 통과
```

---

### [Phase 4-D] Shadows

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 2일  
**브랜치**: `feature/design-system-phase-4-shadows`

---

```
Shadows Foundation 페이지를 구현하세요.

## 목표
- 그림자 레벨 시각화
- CSS Variable 복사 기능

## 레포지토리 구조
- Phase 4-A와 동일한 구조
- Shadows에 맞게 조정

## 작업 내용
- Phase 4-A와 동일한 구조
- Shadows에 맞게 조정

## 완료 조건
- [ ] ShadowScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] 린트 통과
```

---

### [Phase 4-E] Motion

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 2일  
**브랜치**: `feature/design-system-phase-4-motion`

---

```
Motion Foundation 페이지를 구현하세요.

## 목표
- 애니메이션 프리셋 시각화
- CSS Variable 복사 기능

## 레포지토리 구조
- Phase 4-A와 동일한 구조
- Motion에 맞게 조정

## 작업 내용
- Phase 4-A와 동일한 구조
- Motion에 맞게 조정

## 완료 조건
- [ ] MotionPreview 컴포넌트 완성
- [ ] 애니메이션 프리셋 표시
- [ ] 린트 통과
```

---

**참고**: Phase 5-8의 프롬프트는 DAG 문서에서 확인하세요.

**작성일**: 2026-02-01  
**버전**: 1.0  
**상태**: Ready for Copy-Paste
