# Design System Catalog 구현 DAG 및 배치별 작업 프롬프트

**목적**: 여러 개발자가 동시에 작업할 수 있도록 의존성 기반 작업 분할

**작성일**: 2026-02-01  
**버전**: 1.0

---

## DAG 구조 개요 (최대 동시성)

```
[Phase 0] 인터페이스 정의 (모든 작업의 기반)
    │
    ├─> [Phase 1] 독립 유틸리티들 (완전 병렬, 8개 작업)
    │   ├─> [1-A] Props 추출 유틸
    │   ├─> [1-B] 코드 생성 유틸
    │   ├─> [1-C] 클립보드 훅
    │   ├─> [1-D] A11y 테스터 유틸
    │   ├─> [1-E] Usage Tracker 유틸
    │   ├─> [1-F] Design Smell Detector 유틸
    │   ├─> [1-G] Contrast Calculator 유틸
    │   └─> [1-H] Component Analyzer 유틸
    │
    ├─> [Phase 2] 기본 인프라 (병렬 가능)
    │   ├─> [2-A] 라우팅 설정
    │   ├─> [2-B] 레이아웃 컴포넌트 (Sidebar, ContentArea, Header)
    │   └─> [2-C] 레지스트리 구조 & Mock 데이터
    │
    ├─> [Phase 3] 컴포넌트 전시 (완전 병렬, 3개 작업)
    │   ├─> [3-A] Button 전시
    │   ├─> [3-B] Input 전시
    │   └─> [3-C] Select 전시
    │
    ├─> [Phase 4] Foundations (완전 병렬, 5개 작업)
    │   ├─> [4-A] Colors
    │   ├─> [4-B] Typography
    │   ├─> [4-C] Spacing
    │   ├─> [4-D] Shadows
    │   └─> [4-E] Motion
    │
    ├─> [Phase 5] 핵심 기능들 (병렬 가능)
    │   ├─> [5-A] Props Playground (Phase 3 완료 필요)
    │   ├─> [5-B] Props Table 컴포넌트 (Phase 1-A 완료 필요)
    │   ├─> [5-C] Code Block 컴포넌트 (Phase 1-B, 1-C 완료 필요)
    │   ├─> [5-D] 검색 기능 (Phase 2-C 완료 필요)
    │   └─> [5-E] URL 상태 공유 (Phase 5-A 완료 필요)
    │
    ├─> [Phase 6] 고급 기능들 (병렬 가능)
    │   ├─> [6-A] Usage Examples (Phase 3 완료 필요)
    │   ├─> [6-B] Anti-Patterns (Phase 3 완료 필요)
    │   ├─> [6-C] Component Decision Guide (Phase 3 완료 필요)
    │   ├─> [6-D] UsedIn 컴포넌트 (Phase 1-E 완료 필요)
    │   ├─> [6-E] Icon Gallery (Phase 2-B 완료 필요)
    │   ├─> [6-F] Responsive Preview (Phase 2-B 완료 필요)
    │   ├─> [6-G] A11y Score UI (Phase 1-D 완료 필요)
    │   └─> [6-H] Contrast Checker UI (Phase 1-G 완료 필요)
    │
    └─> [Phase 7] DX Enhancement (병렬 가능)
        ├─> [7-A] Project-aware Code Gen UI (Phase 1-B 완료 필요)
        ├─> [7-B] Design Smell UI (Phase 1-F 완료 필요)
        ├─> [7-C] Runtime Validation (Phase 3 완료 필요)
        ├─> [7-D] Keyboard Shortcuts (Phase 2-B 완료 필요)
        ├─> [7-E] Context-aware Copy (Phase 1-B, 1-C 완료 필요)
        ├─> [7-F] Component Recommendation UI (Phase 1-H 완료 필요)
        └─> [7-G] Visual Diff (Phase 2-C 완료 필요)
```

**최대 동시 작업 가능 인원**: 약 20-25명 (Phase 1, 3, 4에서)

---

## 배치별 작업 상세

### Phase 0: 인터페이스 정의 (Interface Definitions) - P0

**의존성**: 없음 (모든 작업의 기반)  
**예상 기간**: 1일  
**담당자**: 1명 (또는 Tech Lead)

**목적**: 모든 작업자가 참조할 수 있는 타입/인터페이스를 먼저 정의하여 동시 작업 가능하게 함

**프롬프트**:

```
Design System Catalog의 모든 타입과 인터페이스를 정의하세요.

## 목표
- 모든 작업자가 참조할 수 있는 타입 정의
- Mock 데이터 구조 정의
- API 인터페이스 정의

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

// Mock 컴포넌트 레지스트리 (실제 컴포넌트 없이도 작업 가능)
export const mockComponentRegistry: ComponentMeta[] = [
  {
    name: 'Button',
    category: 'actions',
    description: 'Mock Button component',
    component: () => <div>Mock Button</div>, // 임시 컴포넌트
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
```

---

### Phase 1: 독립 유틸리티들 (Independent Utilities) - 완전 병렬

**의존성**: Phase 0  
**예상 기간**: 각 2-3일  
**담당자**: 각 1명씩 (총 8명 동시 작업 가능)

**전략**: 각 유틸리티는 완전히 독립적이며, Mock 데이터로 테스트 가능

---

### Batch 1-A: Props 추출 유틸리티 (Props Extraction Utility)

**의존성**: 없음 (시작점)  
**예상 기간**: 3-4일  
**담당자**: 1명

**작업 목록**:
1. Design System 페이지 라우팅 설정
2. 기본 레이아웃 컴포넌트 (Sidebar, ContentArea, Header)
3. 네비게이션 구조
4. 기본 스타일링 (CSS)

**프롬프트**:

```
Design System Catalog의 기본 인프라를 구축하세요.

## 목표
- `/design-system` 경로에 접근 가능한 페이지 생성
- 좌측 사이드바 + 우측 콘텐츠 영역 레이아웃
- 네비게이션 메뉴 (Foundations, Components, Patterns, Resources)

## 작업 내용

### 1. 라우팅 설정
- `admin-ui/src/app/routes/AppRoutes.tsx`에 Design System 라우트 추가
- 경로: `/design-system`
- 중첩 라우팅 구조:
  - `/design-system` → 기본 페이지
  - `/design-system/foundations/:section`
  - `/design-system/components/:category`
  - `/design-system/components/:category/:name`
  - `/design-system/patterns/:pattern`
  - `/design-system/resources/:resource`

### 2. 기본 레이아웃 컴포넌트 생성
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

### 3. 메인 페이지 컴포넌트
위치: `admin-ui/src/features/design-system/ui/DesignSystemPage.tsx`

- Sidebar + ContentArea 레이아웃
- 기본 스타일링 (CSS Module 또는 CSS 파일)

### 4. 스타일링
- `DesignSystemPage.css`: 메인 레이아웃 스타일
- `Sidebar.css`: 사이드바 스타일
- 반응형 디자인 고려 (모바일에서는 사이드바 토글)

## 참고
- 기존 프로젝트의 FSD 아키텍처 준수
- Shared UI 컴포넌트 사용 (Button, Tabs 등)
- TypeScript 타입 안전성 보장

## 완료 조건
- [ ] `/design-system` 접속 시 레이아웃 표시
- [ ] 사이드바 네비게이션 클릭 시 라우팅 동작
- [ ] 반응형 레이아웃 작동
- [ ] 린트 통과
```

---

### Batch 1-A: 컴포넌트 레지스트리 구조 (Component Registry Structure)

**의존성**: Batch 0  
**예상 기간**: 2-3일  
**담당자**: 1명

**프롬프트**:

```
Design System의 컴포넌트 레지스트리 데이터 구조를 구축하세요.

## 목표
- 컴포넌트 메타데이터를 관리하는 타입 시스템 구축
- 컴포넌트 레지스트리 초기 구조 생성

## 작업 내용

### 1. 타입 정의
위치: `admin-ui/src/features/design-system/data/types.ts`

다음 타입들을 정의하세요:

```typescript
export type ComponentCategory = 
  | 'actions' 
  | 'forms' 
  | 'feedback' 
  | 'layout' 
  | 'data-display' 
  | 'utilities'

export type ComponentStability = 'stable' | 'experimental' | 'deprecated'

export type ControlType = 'select' | 'boolean' | 'text' | 'number'

export interface ControlDefinition {
  type: ControlType
  options?: string[] // select 타입일 때
  default: any
  description?: string
}

export interface ComponentExample {
  title: string
  intent: string // 사용 이유
  props: Record<string, any>
}

export interface AntiPattern {
  title: string
  code: string
  reason: string
  correct: string
}

export interface A11yIssue {
  id: string
  message: string
  severity: 'error' | 'warning'
}

export interface A11yScore {
  score: number
  issues: A11yIssue[]
}

export interface RelatedDecision {
  type: 'RFC' | 'ADR'
  id: string
  title: string
}

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
```

### 2. 레지스트리 파일 생성
위치: `admin-ui/src/features/design-system/data/componentRegistry.ts`

```typescript
import { ComponentMeta } from './types'
// 컴포넌트 import는 나중에 추가

export const componentRegistry: ComponentMeta[] = [
  // 초기에는 빈 배열, 나중에 컴포넌트 추가
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

### 3. 컴포넌트 카테고리 페이지
위치: `admin-ui/src/features/design-system/ui/ComponentCategory.tsx`

- 카테고리별 컴포넌트 목록 표시
- 각 컴포넌트 카드 클릭 시 상세 페이지로 이동

## 완료 조건
- [ ] 타입 정의 완료
- [ ] 레지스트리 구조 생성
- [ ] 카테고리별 컴포넌트 조회 함수 구현
- [ ] TypeScript 컴파일 에러 없음
```

---

### Batch 1-B: Props 추출 유틸리티 (Props Extraction Utility)

**의존성**: 없음 (병렬 가능)  
**예상 기간**: 3-4일  
**담당자**: 1명

**프롬프트**:

```
TypeScript 타입에서 Props 정보를 자동으로 추출하는 유틸리티를 구축하세요.

## 목표
- ts-morph을 사용하여 컴포넌트 Props 타입 분석
- Props Table 자동 생성
- JSDoc 주석에서 설명 추출

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
import { Project, SourceFile } from 'ts-morph'

export interface ExtractedProp {
  name: string
  type: string
  required: boolean
  defaultValue?: any
  description?: string
}

export function extractPropsFromComponent(
  componentPath: string,
  componentName: string
): ExtractedProp[] {
  // ts-morph을 사용하여:
  // 1. 컴포넌트 파일 로드
  // 2. Props 인터페이스/타입 찾기
  // 3. 각 prop의 타입, required 여부 추출
  // 4. JSDoc 주석에서 description 추출
  // 5. 기본값 추출 (defaultProps 또는 기본 파라미터)
}
```

### 3. 타입 문자열 포맷팅
- Union 타입: `'primary' | 'secondary'` → `'primary'|'secondary'`
- Optional 타입: `variant?: string` → `variant?: string`
- Function 타입: `(e: Event) => void` → `(e: Event) => void`

### 4. 테스트
- Button 컴포넌트로 테스트
- Input 컴포넌트로 테스트
- Select 컴포넌트로 테스트

## 참고
- ts-morph 문서: https://ts-morph.com/
- Shared UI 컴포넌트 경로: `admin-ui/src/shared/ui/`

## 완료 조건
- [ ] Button 컴포넌트 Props 추출 성공
- [ ] Input 컴포넌트 Props 추출 성공
- [ ] Select 컴포넌트 Props 추출 성공
- [ ] JSDoc 주석에서 description 추출
- [ ] 타입 문자열 포맷팅 정확
```

---

### Batch 2: 컴포넌트 전시 (Component Showcase) - Button, Input, Select

**의존성**: Batch 0, Batch 1-A, Batch 1-B  
**예상 기간**: 4-5일  
**담당자**: 1-2명 (컴포넌트별로 나눌 수 있음)

**프롬프트**:

```
Button, Input, Select 3개 컴포넌트의 전시 페이지를 구현하세요.

## 목표
- 컴포넌트 상세 페이지 UI 구현
- Live Preview 섹션
- Props Table 자동 생성 및 표시
- 기본 코드 복사 기능

## 작업 내용

### 1. 컴포넌트 상세 페이지
위치: `admin-ui/src/features/design-system/ui/ComponentDetail.tsx`

구조:
- 컴포넌트 이름 및 설명
- Stability 배지
- Live Preview 섹션
- Props Reference 테이블
- Code Block (복사 가능)

### 2. Live Preview 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/LivePreview.tsx`

- 컴포넌트를 실제로 렌더링
- 다양한 variant, size 등 미리 정의된 예시 표시
- 예: Button의 경우 Primary, Secondary, Ghost, Small, Medium, Large 등

### 3. Props Table 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/PropsTable.tsx`

- propsExtractor로 추출한 Props 정보를 테이블로 표시
- 컬럼: Prop, Type, Default, Required, Description
- Shared UI의 Table 컴포넌트 사용

### 4. Code Block 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/CodeBlock.tsx`

- 코드를 syntax highlighting하여 표시
- 복사 버튼 (useClipboard 훅 사용)
- 언어: TypeScript/TSX

### 5. 컴포넌트 레지스트리 등록
위치: `admin-ui/src/features/design-system/data/componentRegistry.ts`

Button, Input, Select 3개 컴포넌트를 레지스트리에 추가:

```typescript
import { Button } from '@/shared/ui'
import { Input } from '@/shared/ui'
import { Select } from '@/shared/ui'
import { extractPropsFromComponent } from '../utils/propsExtractor'

export const componentRegistry: ComponentMeta[] = [
  {
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
    examples: [
      { 
        title: 'Form Submit', 
        intent: 'Primary action은 항상 primary variant 사용',
        props: { variant: 'primary', children: 'Submit' } 
      },
    ],
    antiPatterns: [],
    related: ['IconButton'],
    a11y: { default: { score: 95, issues: [] } },
  },
  // Input, Select도 동일하게 추가
]
```

### 6. 기본 코드 생성
위치: `admin-ui/src/features/design-system/utils/codeGenerator.ts`

```typescript
export function generateCodeSnippet(
  componentName: string,
  props: Record<string, any>
): string {
  // 기본 코드 스니펫 생성
  // 예: <Button variant="primary">Click me</Button>
}
```

### 7. useClipboard 훅
위치: `admin-ui/src/features/design-system/hooks/useClipboard.ts`

```typescript
export function useClipboard() {
  const [copied, setCopied] = useState(false)
  
  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  
  return { copy, copied }
}
```

## 완료 조건
- [ ] Button 상세 페이지 완성
- [ ] Input 상세 페이지 완성
- [ ] Select 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] Props Table 자동 생성 및 표시
- [ ] 코드 복사 기능 동작
- [ ] `/design-system/components/actions/button` 접속 시 페이지 표시
```

---

### Batch 3: Props Playground 구현

**의존성**: Batch 2  
**예상 기간**: 3-4일  
**담당자**: 1명

**프롬프트**:

```
Props Playground를 구현하여 사용자가 Props를 실시간으로 조작할 수 있게 하세요.

## 목표
- Props를 인터랙티브하게 조작하는 UI
- 실시간으로 컴포넌트 미리보기 업데이트
- 코드 스니펫 자동 생성

## 작업 내용

### 1. PropsPlayground 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/PropsPlayground.tsx`

구조:
- 좌측: Props 컨트롤 (ControlRenderer)
- 중앙: 실시간 미리보기
- 우측: 생성된 코드

### 2. ControlRenderer 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/ControlRenderer.tsx`

ControlDefinition 타입에 따라 적절한 입력 컴포넌트 렌더링:
- `select`: Select 컴포넌트
- `boolean`: Checkbox 또는 Toggle
- `text`: Input 컴포넌트
- `number`: Number Input

### 3. usePlayground 훅
위치: `admin-ui/src/features/design-system/hooks/usePlayground.ts`

```typescript
export function usePlayground(controls: Record<string, ControlDefinition>) {
  const [props, setProps] = useState(() => 
    Object.fromEntries(
      Object.entries(controls).map(([key, control]) => [key, control.default])
    )
  )
  
  const updateProp = (key: string, value: any) => {
    setProps(prev => ({ ...prev, [key]: value }))
  }
  
  const reset = () => {
    setProps(Object.fromEntries(
      Object.entries(controls).map(([key, control]) => [key, control.default])
    ))
  }
  
  return { props, updateProp, reset }
}
```

### 4. 실시간 코드 생성
- props 변경 시마다 codeGenerator 호출
- CodeBlock에 자동 반영

### 5. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 PropsPlayground 섹션 추가
- Live Preview와 PropsPlayground를 탭 또는 섹션으로 구분

## 완료 조건
- [ ] Props 컨트롤 동작
- [ ] 실시간 미리보기 업데이트
- [ ] 코드 스니펫 자동 생성
- [ ] Reset 기능 동작
- [ ] Button, Input, Select 모두에서 동작
```

---

### Batch 4: Foundations - Colors, Typography, Spacing

**의존성**: Batch 0  
**예상 기간**: 4-5일  
**담당자**: 1-2명 (섹션별로 나눌 수 있음)

**프롬프트**:

```
Foundations 섹션을 구현하세요 (Colors, Typography, Spacing, Shadows, Motion).

## 목표
- Design Token SSOT 구조 구축
- 각 Foundation의 시각화
- CSS Variable 복사 기능

## 작업 내용

### 1. Design Token SSOT 구조
위치: `admin-ui/src/features/design-system/data/tokens/`

- `colors.json`: 컬러 팔레트 정의
- `typography.json`: 타이포그래피 스케일
- `spacing.json`: 간격 시스템
- `shadows.json`: 그림자 레벨
- `motion.json`: 애니메이션 프리셋

예시 (colors.json):
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

### 3. TypographyScale 컴포넌트
위치: `admin-ui/src/features/design-system/components/foundations/TypographyScale.tsx`

- 폰트 스케일 시각화
- 각 스타일의 예시 표시
- CSS Variable 복사

### 4. SpacingScale 컴포넌트
위치: `admin-ui/src/features/design-system/components/foundations/SpacingScale.tsx`

- 간격 시스템 시각화
- 각 간격의 시각적 표현
- CSS Variable 복사

### 5. ContrastChecker 컴포넌트
위치: `admin-ui/src/features/design-system/components/utilities/ContrastChecker.tsx`

- 배경색/전경색 선택
- 대비 비율 계산 및 표시
- WCAG AAA/AA 통과 여부 표시

### 6. FoundationsSection 페이지
위치: `admin-ui/src/features/design-system/ui/FoundationsSection.tsx`

- URL 파라미터로 섹션 구분 (`/foundations/colors`, `/foundations/typography` 등)
- 각 섹션에 맞는 컴포넌트 렌더링

## 완료 조건
- [ ] Design Token JSON 파일 생성
- [ ] ColorPalette 컴포넌트 완성
- [ ] TypographyScale 컴포넌트 완성
- [ ] SpacingScale 컴포넌트 완성
- [ ] ContrastChecker 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] `/design-system/foundations/colors` 접속 시 페이지 표시
```

---

### Batch 5: 검색 기능 & URL 상태 공유

**의존성**: Batch 2, Batch 3  
**예상 기간**: 3-4일  
**담당자**: 1명

**프롬프트**:

```
검색 기능과 Props Playground URL 상태 공유를 구현하세요.

## 목표
- 컴포넌트 검색 기능
- Props Playground 상태를 URL에 저장
- URL에서 상태 복원

## 작업 내용

### 1. 검색 기능
위치: `admin-ui/src/features/design-system/components/layout/Header.tsx`

- 검색 바 추가
- 컴포넌트 이름, 설명, 카테고리로 검색
- 실시간 필터링

### 2. useSearch 훅
위치: `admin-ui/src/features/design-system/hooks/useSearch.ts`

```typescript
export function useSearch(query: string) {
  return useMemo(() => {
    if (!query) return componentRegistry
    
    const lowerQuery = query.toLowerCase()
    return componentRegistry.filter(component => 
      component.name.toLowerCase().includes(lowerQuery) ||
      component.description.toLowerCase().includes(lowerQuery) ||
      component.category.toLowerCase().includes(lowerQuery)
    )
  }, [query])
}
```

### 3. URL 상태 공유 (Props Playground)
위치: `admin-ui/src/features/design-system/components/showcase/PropsPlayground.tsx`

- Props 변경 시 URL 쿼리 파라미터 업데이트
- 예: `/design-system/components/actions/button?variant=primary&size=lg&loading=true`
- URL에서 초기 상태 로드

```typescript
// URL에서 Props 파싱
function parsePropsFromURL(searchParams: URLSearchParams): Record<string, any> {
  const props: Record<string, any> = {}
  searchParams.forEach((value, key) => {
    // 타입에 맞게 변환
    if (value === 'true' || value === 'false') {
      props[key] = value === 'true'
    } else if (!isNaN(Number(value))) {
      props[key] = Number(value)
    } else {
      props[key] = value
    }
  })
  return props
}

// Props를 URL로 변환
function generateURLWithProps(componentName: string, props: Record<string, any>): string {
  const params = new URLSearchParams()
  Object.entries(props).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      params.set(key, String(value))
    }
  })
  const queryString = params.toString()
  return `/design-system/components/actions/${componentName.toLowerCase()}${queryString ? `?${queryString}` : ''}`
}
```

### 4. URL 복사 버튼
- ComponentDetail 페이지에 "Copy URL" 버튼 추가
- 현재 Props 상태가 포함된 URL 복사

### 5. 검색 결과 표시
- 검색 시 컴포넌트 목록 필터링
- 사이드바 또는 메인 영역에 검색 결과 표시

## 완료 조건
- [ ] 검색 기능 동작
- [ ] Props Playground 상태가 URL에 반영
- [ ] URL에서 상태 복원
- [ ] URL 복사 기능 동작
- [ ] 검색 결과 실시간 업데이트
```

---

### Batch 6-A: 코드 생성 유틸리티 강화 (Project-aware)

**의존성**: Batch 2  
**예상 기간**: 2-3일  
**담당자**: 1명

**프롬프트**:

```
Project-aware 코드 생성을 구현하세요.

## 목표
- 프로젝트 설정에 맞는 코드 자동 생성
- Framework, Style, Import 방식 선택

## 작업 내용

### 1. 프로젝트 설정 감지
위치: `admin-ui/src/features/design-system/hooks/useProjectSettings.ts`

```typescript
export interface ProjectSettings {
  framework: 'react' | 'next' | 'vite'
  style: 'tailwind' | 'css-module' | 'vanilla-css'
  import: 'alias' | 'relative'
}

export function useProjectSettings(): ProjectSettings {
  // 프로젝트 설정 자동 감지 또는 기본값 반환
  return {
    framework: 'react', // 기본값
    style: 'tailwind', // 기본값
    import: 'alias' // 기본값
  }
}
```

### 2. 코드 생성 옵션 UI
위치: `admin-ui/src/features/design-system/components/showcase/CodeBlock.tsx`

- 코드 블록 상단에 설정 선택 UI 추가:
  - [Framework] React / Next / Vite
  - [Style] Tailwind / CSS Module / Vanilla CSS
  - [Import] alias(@/shared/ui) / relative

### 3. 코드 생성 로직 강화
위치: `admin-ui/src/features/design-system/utils/codeGenerator.ts`

```typescript
export function generateCodeSnippet(
  componentName: string,
  props: Record<string, any>,
  options: ProjectSettings
): string {
  const importPath = options.import === 'alias'
    ? `@/shared/ui/${componentName.toLowerCase()}`
    : `../shared/ui/${componentName}`
  
  // props 포맷팅
  const propsString = formatProps(props, options.style)
  
  return `import { ${componentName} } from '${importPath}'

<${componentName}${propsString ? ` ${propsString}` : ''}>
  ${props.children || ''}
</${componentName}>`
}
```

### 4. Props 포맷팅
- Tailwind: className으로 스타일 적용 (필요시)
- CSS Module: className에 모듈 클래스 적용
- Vanilla CSS: 인라인 스타일 또는 클래스

## 완료 조건
- [ ] 프로젝트 설정 선택 UI 동작
- [ ] 설정 변경 시 코드 자동 업데이트
- [ ] 각 설정 조합에서 올바른 코드 생성
- [ ] 복사된 코드가 프로젝트에 바로 사용 가능
```

---

### Batch 6-B: A11y Score 통합 (axe-core)

**의존성**: Batch 2  
**예상 기간**: 3-4일  
**담당자**: 1명

**프롬프트**:

```
axe-core를 사용하여 A11y Score를 실측 기반으로 구현하세요.

## 목표
- 컴포넌트의 접근성 점수 자동 계산
- 상태별 점수 분리
- 이슈 목록 표시

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

export interface A11yResult {
  score: number
  issues: Array<{
    id: string
    message: string
    severity: 'error' | 'warning'
  }>
}

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
  // 점수 계산 로직 (100점 만점)
  // violations에 따라 감점
  // incomplete는 경고로 처리
}
```

### 3. A11yScore 컴포넌트
위치: `admin-ui/src/features/design-system/components/utilities/A11yScore.tsx`

- 점수 표시 (0-100)
- 진행 바 (Progress Bar)
- 이슈 목록 표시
- 점수에 따른 배지 (Excellent, Good, Needs Improvement)

### 4. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 A11y Score 섹션 추가
- Live Preview의 각 상태별로 점수 측정
- 예: Button의 default, disabled, loading 상태별 점수

### 5. 상태별 점수 저장
- componentRegistry의 a11y 필드에 상태별 점수 저장
- 컴포넌트 로드 시 자동 측정 또는 캐시된 값 사용

## 완료 조건
- [ ] axe-core 통합 완료
- [ ] Button 컴포넌트 A11y Score 측정 성공
- [ ] 상태별 점수 분리 표시
- [ ] 이슈 목록 표시
- [ ] 점수 계산 로직 정확
```

---

### Batch 7: Advanced Features

**의존성**: Batch 2, Batch 4, Batch 6-B  
**예상 기간**: 5-6일  
**담당자**: 2-3명

**프롬프트 (7-A: Usage Examples & Anti-Patterns)**:

```
Usage Examples와 Anti-Pattern 갤러리를 구현하세요.

## 목표
- "Why"가 보이는 Usage Examples
- Anti-Pattern 갤러리
- Component Decision Guide

## 작업 내용

### 1. UsageExamples 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/UsageExamples.tsx`

- 예제 목록 표시
- 각 예제에:
  - Title
  - Intent (사용 이유)
  - Code
  - Live Preview

### 2. AntiPatterns 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/AntiPatterns.tsx`

- Anti-pattern 목록 표시
- 각 항목에:
  - Title
  - 잘못된 코드 (❌)
  - 이유 (Reason)
  - 올바른 코드 (✅)

### 3. ComponentDecision 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/ComponentDecision.tsx`

- 컴포넌트 선택 가이드
- 예: Button vs IconButton vs LinkButton
- Decision Tree 시각화

### 4. 컴포넌트 레지스트리 업데이트
- Button, Input, Select에 examples와 antiPatterns 데이터 추가

## 완료 조건
- [ ] Usage Examples 섹션 완성
- [ ] Anti-Pattern 갤러리 완성
- [ ] Component Decision Guide 완성
- [ ] Button, Input, Select에 예제 데이터 추가
```

**프롬프트 (7-B: Component Usage Tracker)**:

```
컴포넌트가 실제로 어디서 사용되는지 추적하는 기능을 구현하세요.

## 목표
- 코드베이스 스캔하여 컴포넌트 사용 위치 찾기
- 사용 위치 목록 표시
- 코드 미리보기

## 작업 내용

### 1. Usage Tracker 유틸리티
위치: `admin-ui/src/features/design-system/utils/usageTracker.ts`

```typescript
export interface UsageInfo {
  path: string
  count: number
  preview: string
  link: string // GitHub 링크 또는 파일 경로
}

export function trackComponentUsage(componentName: string): UsageInfo[] {
  // 코드베이스 스캔 (grep 또는 AST 분석)
  // admin-ui/src 디렉토리에서 컴포넌트 사용 찾기
  // 각 파일의 사용 횟수 계산
  // 코드 스니펫 추출
}
```

### 2. UsedIn 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/UsedIn.tsx`

- 사용 위치 목록 표시
- 각 항목에:
  - 파일 경로
  - 사용 횟수
  - 코드 미리보기
  - 링크 (클릭 시 파일 열기)

### 3. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 "Used In" 섹션 추가
- 컴포넌트 로드 시 자동으로 사용 위치 조회

## 완료 조건
- [ ] 코드베이스 스캔 기능 동작
- [ ] 사용 위치 목록 표시
- [ ] 코드 미리보기 표시
- [ ] 파일 링크 동작
```

**프롬프트 (7-C: Icon Gallery & Responsive Preview)**:

```
Icon Gallery와 Responsive Preview를 구현하세요.

## 목표
- 전체 아이콘 검색 및 표시
- 아이콘 코드 복사
- 반응형 프리뷰

## 작업 내용

### 1. Icon Gallery
위치: `admin-ui/src/features/design-system/components/resources/IconGallery.tsx`

- lucide-react 아이콘 목록 표시
- 검색 기능
- 필터 (Outlined, Filled 등)
- 아이콘 클릭 시 코드 복사

### 2. 아이콘 목록 데이터
위치: `admin-ui/src/features/design-system/data/iconList.ts`

- lucide-react에서 사용 가능한 아이콘 목록 생성
- 아이콘 이름, 카테고리 등 메타데이터

### 3. ResponsiveFrame 컴포넌트
위치: `admin-ui/src/features/design-system/components/utilities/ResponsiveFrame.tsx`

- 뷰포트 크기 선택 (375px, 768px, 1280px)
- 선택한 크기로 컴포넌트 렌더링
- iframe 또는 div로 구현

### 4. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 Responsive Preview 옵션 추가

## 완료 조건
- [ ] Icon Gallery 완성
- [ ] 아이콘 검색 기능 동작
- [ ] 아이콘 코드 복사 기능 동작
- [ ] Responsive Preview 완성
- [ ] 뷰포트 크기 변경 시 컴포넌트 크기 조정
```

---

### Batch 8: DX Enhancement

**의존성**: Batch 3, Batch 6-A, Batch 7  
**예상 기간**: 5-6일  
**담당자**: 2-3명

**프롬프트 (8-A: Design Smell Detector & Runtime Validation)**:

```
Design Smell Detector와 Runtime Validation을 구현하세요.

## 목표
- 패턴 위반 자동 감지
- 개발 모드에서 경고 표시

## 작업 내용

### 1. Design Smell Detector
위치: `admin-ui/src/features/design-system/utils/designSmellDetector.ts`

```typescript
export interface DesignSmell {
  type: 'pattern-violation' | 'anti-pattern' | 'accessibility'
  message: string
  severity: 'error' | 'warning' | 'info'
  suggestion: string
}

export function detectDesignSmells(code: string): DesignSmell[] {
  // 코드 분석하여 패턴 위반 감지
  // 예: danger 액션인데 secondary 사용
  // 예: loading 상태인데 disabled 미설정
}
```

### 2. Runtime Validation (개발 모드)
- 컴포넌트 사용 시 props 검증
- 위반 시 console.warn 또는 UI overlay 표시
- NODE_ENV === 'development'에서만 동작

### 3. Design Smell 표시 UI
- 컴포넌트 상세 페이지에 Design Smell 섹션 추가
- 감지된 이슈 목록 표시

## 완료 조건
- [ ] Design Smell 감지 로직 구현
- [ ] Runtime Validation 동작
- [ ] 개발 모드에서만 동작 확인
- [ ] UI에 이슈 표시
```

**프롬프트 (8-B: Keyboard-First UX & Context-aware Copy)**:

```
Keyboard-First UX와 Context-aware Copy를 구현하세요.

## 목표
- 전역 키보드 단축키
- 컨텍스트별 코드 복사 옵션

## 작업 내용

### 1. Keyboard 단축키
- `/` → 컴포넌트 검색 포커스
- `Enter` → 첫 번째 검색 결과로 이동
- `P` → Props Playground 포커스
- `C` → 코드 복사
- `Esc` → 검색 닫기

### 2. useKeyboardShortcuts 훅
위치: `admin-ui/src/features/design-system/hooks/useKeyboardShortcuts.ts`

```typescript
export function useKeyboardShortcuts() {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === '/' && !isInputFocused()) {
        e.preventDefault()
        focusSearch()
      }
      // ... 다른 단축키
    }
    
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])
}
```

### 3. Context-aware Copy
위치: `admin-ui/src/features/design-system/components/showcase/CodeBlock.tsx`

- [Copy Component] - 컴포넌트만
- [Copy with Form Example] - 폼 예제 포함
- [Copy with Validation] - 검증 로직 포함

### 4. 코드 템플릿
- 각 컨텍스트별 코드 템플릿 정의
- 예제 코드 생성

## 완료 조건
- [ ] 키보드 단축키 동작
- [ ] Context-aware Copy 옵션 표시
- [ ] 각 옵션에서 올바른 코드 생성
- [ ] 단축키 충돌 없음
```

**프롬프트 (8-C: Component Recommendation)**:

```
실제 코드베이스 분석 기반 컴포넌트 추천을 구현하세요.

## 목표
- 프로젝트에서 자주 쓰는 컴포넌트 표시
- 컨텍스트 기반 추천

## 작업 내용

### 1. 코드베이스 분석
위치: `admin-ui/src/features/design-system/utils/componentAnalyzer.ts`

- admin-ui/src 디렉토리 스캔
- 각 컴포넌트 사용 횟수 계산
- 패턴 분석 (어떤 컴포넌트가 함께 쓰이는지)

### 2. Component Recommendation 훅
위치: `admin-ui/src/features/design-system/hooks/useComponentRecommendation.ts`

```typescript
export function useComponentRecommendation() {
  const popularComponents = useMemo(() => {
    // 사용 횟수 기준 정렬
    return analyzeComponentUsage()
  }, [])
  
  const contextualRecommendations = useMemo(() => {
    // 현재 페이지 컨텍스트 기반 추천
    // 예: Form 패턴에서 자주 쓰이는 컴포넌트
  }, [currentContext])
  
  return { popularComponents, contextualRecommendations }
}
```

### 3. 추천 UI
- 메인 페이지 또는 사이드바에 추천 섹션 추가
- "이 프로젝트에서 자주 쓰는 컴포넌트" 표시
- "현재 컨텍스트에서 추천" 표시

## 완료 조건
- [ ] 코드베이스 분석 기능 동작
- [ ] 사용 횟수 계산 정확
- [ ] 추천 UI 표시
- [ ] 컨텍스트 기반 추천 동작
```

---

### Batch 9: Organization Scale

**의존성**: Batch 7, Batch 8  
**예상 기간**: 3-4일  
**담당자**: 1-2명

**프롬프트**:

```
조직 규모 확장을 위한 기능을 구현하세요.

## 목표
- Design System Health Dashboard
- RFC/ADR 자동 연결
- PR Template 통합
- CI 통합

## 작업 내용

### 1. Health Dashboard
위치: `admin-ui/src/features/design-system/components/utilities/HealthDashboard.tsx`

- Deprecated 컴포넌트 사용 현황
- Anti-pattern 위반 건수
- A11y 평균 점수
- 컴포넌트 사용 통계

### 2. RFC/ADR 연결
- ComponentDetail 페이지에 "Related Decisions" 섹션 추가
- docs/rfc/, docs/adr/ 디렉토리에서 관련 문서 찾기
- 링크 표시

### 3. PR Template 통합
위치: `.github/pull_request_template.md` 또는 프로젝트 루트

```markdown
## Design System
- [ ] Design System 가이드 준수 확인
- [ ] 신규 컴포넌트 DS 등록 여부 확인
- [ ] A11y 점수 확인 (90점 이상)
```

### 4. CI 통합 (선택사항)
- GitHub Actions에서 DS Rule 검사
- 위반 시 Warning 표시

## 완료 조건
- [ ] Health Dashboard 완성
- [ ] RFC/ADR 연결 동작
- [ ] PR Template 업데이트
- [ ] (선택) CI 통합 완료
```

---

## 작업 배치 요약표

| 배치 | 작업 내용 | 의존성 | 예상 기간 | 담당자 수 |
|------|----------|--------|----------|----------|
| Batch 0 | 기본 인프라 | 없음 | 3-4일 | 1명 |
| Batch 1-A | 레지스트리 구조 | Batch 0 | 2-3일 | 1명 |
| Batch 1-B | Props 추출 유틸 | 없음 | 3-4일 | 1명 |
| Batch 2 | 컴포넌트 전시 (3개) | Batch 0, 1-A, 1-B | 4-5일 | 1-2명 |
| Batch 3 | Props Playground | Batch 2 | 3-4일 | 1명 |
| Batch 4 | Foundations | Batch 0 | 4-5일 | 1-2명 |
| Batch 5 | 검색 & URL 공유 | Batch 2, 3 | 3-4일 | 1명 |
| Batch 6-A | 코드 생성 강화 | Batch 2 | 2-3일 | 1명 |
| Batch 6-B | A11y Score | Batch 2 | 3-4일 | 1명 |
| Batch 7-A | Usage Examples | Batch 2, 4, 6-B | 2-3일 | 1명 |
| Batch 7-B | Usage Tracker | Batch 2 | 2-3일 | 1명 |
| Batch 7-C | Icon Gallery | Batch 0 | 2-3일 | 1명 |
| Batch 8-A | Design Smell | Batch 3, 6-A, 7 | 2-3일 | 1명 |
| Batch 8-B | Keyboard UX | Batch 3, 6-A, 7 | 2-3일 | 1명 |
| Batch 8-C | 추천 시스템 | Batch 7 | 2-3일 | 1명 |
| Batch 9 | Organization Scale | Batch 7, 8 | 3-4일 | 1-2명 |

**총 예상 기간**: 병렬 작업 시 약 6-8주

---

## 병렬 작업 가이드

### 동시에 시작 가능한 배치
- **Batch 0** (기본 인프라) - 필수 선행 작업
- **Batch 1-B** (Props 추출 유틸) - 독립적

### Batch 0 완료 후 시작 가능
- **Batch 1-A** (레지스트리 구조)
- **Batch 4** (Foundations)

### Batch 2 완료 후 시작 가능
- **Batch 3** (Props Playground)
- **Batch 5** (검색 & URL)
- **Batch 6-A** (코드 생성)
- **Batch 6-B** (A11y Score)
- **Batch 7-A** (Usage Examples)
- **Batch 7-B** (Usage Tracker)
- **Batch 7-C** (Icon Gallery)

### Batch 7 완료 후 시작 가능
- **Batch 8-A** (Design Smell)
- **Batch 8-B** (Keyboard UX)
- **Batch 8-C** (추천 시스템)

### Batch 8 완료 후 시작 가능
- **Batch 9** (Organization Scale)

---

## 작업 시작 전 체크리스트

각 배치 작업 시작 전:

1. [ ] 의존성 배치 완료 확인
2. [ ] 관련 문서 읽기 (제안서, 기존 코드)
3. [ ] 브랜치 생성 (`feature/design-system-batch-X`)
4. [ ] 작업 범위 명확화
5. [ ] 예상 완료일 설정

---

## PR 가이드라인

각 배치 완료 시:

1. **PR 제목**: `[Design System] Batch X: 작업 내용`
2. **PR 설명**:
   - 작업 내용 요약
   - 완료 조건 체크리스트
   - 스크린샷 (UI 변경 시)
   - 테스트 방법
3. **리뷰 요청**: Design System 팀 또는 Tech Lead
4. **머지 후**: 다음 배치 작업자에게 알림

---

**작성일**: 2026-02-01  
**버전**: 1.0  
**상태**: Ready for Execution
