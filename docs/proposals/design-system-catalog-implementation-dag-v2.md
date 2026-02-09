# Design System Catalog 구현 DAG - 최대 동시성 버전

**목적**: 최대 20-25명이 동시에 작업할 수 있도록 의존성을 최소화하고 작업을 세분화

**작성일**: 2026-02-01  
**버전**: 2.0 (Maximum Concurrency)

---

## 핵심 전략

1. **인터페이스 우선**: Phase 0에서 모든 타입 정의 → 모든 작업자가 동시 시작 가능
2. **Mock 데이터 활용**: 실제 컴포넌트 없이도 UI 작업 가능
3. **완전 독립 작업**: 유틸리티, 컴포넌트 전시, Foundations는 완전 병렬
4. **작은 단위 분할**: 컴포넌트별, Foundation별로 개별 배치

---

## 레포지토리 구조

### 전체 디렉토리 구조

```
admin-ui/src/
├── features/
│   └── design-system/                    # Design System Catalog Feature
│       ├── index.ts                       # Public API
│       │
│       ├── ui/                            # 페이지 컴포넌트
│       │   ├── DesignSystemPage.tsx       # 메인 페이지
│       │   ├── ComponentDetail.tsx        # 컴포넌트 상세 페이지
│       │   ├── ComponentCategory.tsx      # 카테고리 페이지
│       │   ├── FoundationsSection.tsx     # Foundations 섹션
│       │   ├── PatternGuide.tsx           # 패턴 가이드
│       │   └── ResourcePage.tsx           # 리소스 페이지
│       │
│       ├── components/                     # 하위 컴포넌트
│       │   ├── layout/                     # 레이아웃 컴포넌트
│       │   │   ├── Sidebar.tsx
│       │   │   ├── ContentArea.tsx
│       │   │   ├── Header.tsx
│       │   │   └── index.ts
│       │   │
│       │   ├── showcase/                   # 컴포넌트 전시 관련
│       │   │   ├── ComponentShowcase.tsx
│       │   │   ├── LivePreview.tsx
│       │   │   ├── PropsPlayground.tsx
│       │   │   ├── ControlRenderer.tsx
│       │   │   ├── PropsTable.tsx
│       │   │   ├── CodeBlock.tsx
│       │   │   ├── UsageExamples.tsx
│       │   │   ├── AntiPatterns.tsx
│       │   │   ├── ComponentDecision.tsx
│       │   │   ├── UsedIn.tsx
│       │   │   └── index.ts
│       │   │
│       │   ├── foundations/                # Foundations 컴포넌트
│       │   │   ├── ColorPalette.tsx
│       │   │   ├── TypographyScale.tsx
│       │   │   ├── SpacingScale.tsx
│       │   │   ├── ShadowScale.tsx
│       │   │   ├── MotionPreview.tsx
│       │   │   └── index.ts
│       │   │
│       │   ├── resources/                  # 리소스 컴포넌트
│       │   │   ├── IconGallery.tsx
│       │   │   ├── Changelog.tsx
│       │   │   ├── Migration.tsx
│       │   │   └── index.ts
│       │   │
│       │   └── utilities/                  # 유틸리티 컴포넌트
│       │       ├── ContrastChecker.tsx
│       │       ├── ResponsiveFrame.tsx
│       │       ├── A11yScore.tsx
│       │       ├── HealthDashboard.tsx
│       │       └── index.ts
│       │
│       ├── hooks/                          # 커스텀 훅
│       │   ├── usePlayground.ts
│       │   ├── useClipboard.ts
│       │   ├── useSearch.ts
│       │   ├── useCodeGenerator.ts
│       │   ├── useComponentRecommendation.ts
│       │   ├── useKeyboardShortcuts.ts
│       │   ├── useProjectSettings.ts
│       │   └── index.ts
│       │
│       ├── data/                           # 데이터 레이어
│       │   ├── types.ts                    # 타입 정의 (Phase 0)
│       │   ├── mockData.ts                 # Mock 데이터
│       │   ├── componentRegistry.ts        # 컴포넌트 레지스트리
│       │   ├── colorTokens.ts              # 컬러 토큰 (SSOT)
│       │   ├── iconList.ts                 # 아이콘 목록
│       │   └── tokens/                     # Design Tokens (SSOT)
│       │       ├── colors.json
│       │       ├── typography.json
│       │       ├── spacing.json
│       │       ├── shadows.json
│       │       └── motion.json
│       │
│       └── utils/                           # 유틸리티 함수
│           ├── propsExtractor.ts           # Props 추출 (ts-morph)
│           ├── codeGenerator.ts            # 코드 생성
│           ├── contrastCalculator.ts        # 대비 계산
│           ├── a11yTester.ts              # A11y 테스트 (axe-core)
│           ├── usageTracker.ts             # 사용 위치 추적
│           ├── designSmellDetector.ts      # Design Smell 감지
│           ├── componentAnalyzer.ts        # 컴포넌트 분석
│           └── index.ts
│
├── shared/                                  # 공유 리소스
│   ├── ui/                                 # Shared UI 컴포넌트 (전시 대상)
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── Select.tsx
│   │   └── ... (기타 컴포넌트들)
│   │
│   └── utils/                              # 공유 유틸리티
│       └── clipboard.ts                    # 클립보드 유틸 (기존)
│
└── app/
    └── routes/
        └── AppRoutes.tsx                   # 라우팅 설정 (여기에 DS 라우트 추가)
```

### 레이어별 역할

#### 1. `ui/` - 페이지 컴포넌트
- **역할**: 라우팅에 직접 연결되는 페이지 컴포넌트
- **규칙**: 각 페이지는 단일 책임, 라우팅 로직만 포함, 비즈니스 로직은 hooks나 utils로 분리

#### 2. `components/` - 하위 컴포넌트
- **구조**: `layout/`, `showcase/`, `foundations/`, `resources/`, `utilities/`
- **규칙**: 각 컴포넌트는 독립적으로 테스트 가능, Props는 타입 안전하게 정의

#### 3. `hooks/` - 커스텀 훅
- **역할**: 상태 관리 및 비즈니스 로직
- **규칙**: 각 훅은 단일 책임, 재사용 가능하도록 설계, 테스트 가능하도록 순수 함수로 구성

#### 4. `data/` - 데이터 레이어
- **역할**: 정적 데이터 및 타입 정의
- **구조**: `types.ts` (Phase 0에서 먼저 생성), `tokens/` (SSOT)
- **규칙**: `types.ts`는 Phase 0에서 먼저 생성, `tokens/`는 SSOT로 JSON 파일로 관리

#### 5. `utils/` - 유틸리티 함수
- **역할**: 순수 함수 유틸리티
- **규칙**: 순수 함수로 작성 (부작용 없음), 테스트 가능하도록 설계, 외부 의존성 최소화

### 파일 생성 순서 (Phase별)

#### Phase 0: 인터페이스 정의
```
features/design-system/
├── data/
│   └── types.ts                    # 모든 타입 정의
└── data/
    └── mockData.ts                 # Mock 데이터
```

#### Phase 1: 독립 유틸리티
```
features/design-system/
└── utils/
    ├── propsExtractor.ts
    ├── codeGenerator.ts
    ├── contrastCalculator.ts
    ├── a11yTester.ts
    ├── usageTracker.ts
    ├── designSmellDetector.ts
    └── componentAnalyzer.ts
```

#### Phase 2: 기본 인프라
```
features/design-system/
├── ui/
│   └── DesignSystemPage.tsx
├── components/
│   └── layout/
│       ├── Sidebar.tsx
│       ├── ContentArea.tsx
│       └── Header.tsx
└── data/
    └── componentRegistry.ts
```

#### Phase 3: 컴포넌트 전시
```
features/design-system/
├── ui/
│   └── ComponentDetail.tsx
└── components/
    └── showcase/
        ├── ComponentShowcase.tsx
        └── LivePreview.tsx
```

### Import 규칙

#### Feature 내부 Import
```typescript
// 같은 레이어 내
import { ComponentMeta } from '../data/types'
import { usePlayground } from '../hooks/usePlayground'

// 하위 레이어에서 상위 레이어
import { ComponentShowcase } from '../components/showcase/ComponentShowcase'
```

#### Shared 레이어 Import
```typescript
// Shared UI 컴포넌트 (전시 대상)
import { Button, Input, Select } from '@/shared/ui'

// Shared 유틸리티
import { formatDate } from '@/shared/utils'
```

#### 절대 경로 사용
```typescript
// tsconfig.json에 설정된 경로 별칭 사용
import { ComponentMeta } from '@/features/design-system/data/types'
import { Button } from '@/shared/ui'
```

### 파일 네이밍 규칙

- **컴포넌트 파일**: PascalCase (`ComponentShowcase.tsx`)
- **훅 파일**: camelCase with `use` prefix (`usePlayground.ts`)
- **유틸리티 파일**: camelCase (`propsExtractor.ts`)
- **타입 파일**: camelCase (`types.ts`)
- **데이터 파일**: camelCase (`componentRegistry.ts`)

### Public API (index.ts)

각 레이어는 `index.ts`를 통해 Public API를 노출합니다.

```typescript
// features/design-system/index.ts
export { DesignSystemPage } from './ui/DesignSystemPage'
export { ComponentDetail } from './ui/ComponentDetail'
export { usePlayground } from './hooks/usePlayground'
export { componentRegistry } from './data/componentRegistry'
export type { ComponentMeta } from './data/types'
```

**규칙**: 외부에서 사용할 것만 export, 내부 구현 세부사항은 export하지 않음

### Design Tokens SSOT 구조

```
features/design-system/data/tokens/
├── colors.json              # 컬러 팔레트
├── typography.json          # 타이포그래피 스케일
├── spacing.json             # 간격 시스템
├── shadows.json             # 그림자 레벨
└── motion.json              # 애니메이션 프리셋
```

**자동 파이프라인** (나중에 구현):
```
tokens/
  ├─> CSS Variables (자동 생성)
  ├─> Tailwind Config (자동 생성)
  ├─> Figma Tokens (자동 생성)
  └─> Design System UI (직접 사용)
```

### Git 브랜치 전략

#### 브랜치 네이밍
```
feature/design-system-phase-0-types
feature/design-system-phase-1-props-extractor
feature/design-system-phase-2-routing
feature/design-system-phase-3-button-showcase
```

#### 커밋 메시지
```
[Design System] Phase 0: Add type definitions
[Design System] Phase 1-A: Implement props extractor utility
[Design System] Phase 2-A: Add routing configuration
[Design System] Phase 3-A: Implement Button showcase
```

### 코드 리뷰 체크리스트

각 PR에서 확인할 사항:

- [ ] FSD 아키텍처 준수
- [ ] 타입 안전성 (TypeScript 에러 없음)
- [ ] Import 경로 정확 (절대 경로 사용)
- [ ] 파일 네이밍 규칙 준수
- [ ] CSS 충돌 없음 (CSS Module 사용)
- [ ] 테스트 코드 작성 (필요 시)
- [ ] Public API만 export
- [ ] 린트 통과

**상세한 레포지토리 구조 문서**: `docs/proposals/design-system-catalog-repo-structure.md` 참고

---

## DAG 구조 (최대 동시성)

```
[Phase 0] 인터페이스 정의 (1일, 1명)
    │
    ├─> [Phase 1] 독립 유틸리티 (완전 병렬, 8개 작업, 8명)
    │   ├─> [1-A] Props 추출 유틸
    │   ├─> [1-B] 코드 생성 유틸
    │   ├─> [1-C] 클립보드 훅
    │   ├─> [1-D] A11y 테스터 유틸
    │   ├─> [1-E] Usage Tracker 유틸
    │   ├─> [1-F] Design Smell Detector 유틸
    │   ├─> [1-G] Contrast Calculator 유틸
    │   └─> [1-H] Component Analyzer 유틸
    │
    ├─> [Phase 2] 기본 인프라 (병렬 가능, 3개 작업, 3명)
    │   ├─> [2-A] 라우팅 설정
    │   ├─> [2-B] 레이아웃 컴포넌트
    │   └─> [2-C] 레지스트리 구조 & Mock 데이터
    │
    ├─> [Phase 3] 컴포넌트 전시 (완전 병렬, 3개 작업, 3명)
    │   ├─> [3-A] Button 전시
    │   ├─> [3-B] Input 전시
    │   └─> [3-C] Select 전시
    │
    ├─> [Phase 4] Foundations (완전 병렬, 5개 작업, 5명)
    │   ├─> [4-A] Colors
    │   ├─> [4-B] Typography
    │   ├─> [4-C] Spacing
    │   ├─> [4-D] Shadows
    │   └─> [4-E] Motion
    │
    ├─> [Phase 5] 핵심 기능 (병렬 가능, 5개 작업, 3-5명)
    │   ├─> [5-A] Props Playground (3-A/B/C 완료 필요)
    │   ├─> [5-B] Props Table UI (1-A 완료 필요)
    │   ├─> [5-C] Code Block UI (1-B, 1-C 완료 필요)
    │   ├─> [5-D] 검색 기능 (2-C 완료 필요)
    │   └─> [5-E] URL 상태 공유 (5-A 완료 필요)
    │
    ├─> [Phase 6] 고급 기능 (병렬 가능, 8개 작업, 5-8명)
    │   ├─> [6-A] Usage Examples (3-A/B/C 완료 필요)
    │   ├─> [6-B] Anti-Patterns (3-A/B/C 완료 필요)
    │   ├─> [6-C] Component Decision Guide (3-A/B/C 완료 필요)
    │   ├─> [6-D] UsedIn 컴포넌트 (1-E 완료 필요)
    │   ├─> [6-E] Icon Gallery (2-B 완료 필요)
    │   ├─> [6-F] Responsive Preview (2-B 완료 필요)
    │   ├─> [6-G] A11y Score UI (1-D 완료 필요)
    │   └─> [6-H] Contrast Checker UI (1-G 완료 필요)
    │
    └─> [Phase 7] DX Enhancement (병렬 가능, 7개 작업, 4-7명)
        ├─> [7-A] Project-aware Code Gen UI (1-B 완료 필요)
        ├─> [7-B] Design Smell UI (1-F 완료 필요)
        ├─> [7-C] Runtime Validation (3-A/B/C 완료 필요)
        ├─> [7-D] Keyboard Shortcuts (2-B 완료 필요)
        ├─> [7-E] Context-aware Copy (1-B, 1-C 완료 필요)
        ├─> [7-F] Component Recommendation UI (1-H 완료 필요)
        └─> [7-G] Visual Diff (2-C 완료 필요)

[Phase 8] Organization Scale (모든 Phase 완료 후, 1-2명)
    ├─> [8-A] Health Dashboard
    ├─> [8-B] RFC/ADR 연결
    ├─> [8-C] PR Template 통합
    └─> [8-D] CI 통합
```

**최대 동시 작업 가능 인원**: 
- Phase 1: 8명
- Phase 2: 3명
- Phase 3: 3명
- Phase 4: 5명
- **총합: 최대 19명 동시 작업 가능**

---

## Phase 0: 인터페이스 정의 (Foundation)

**의존성**: 없음  
**예상 기간**: 1일  
**담당자**: 1명 (Tech Lead 권장)

**프롬프트**:

```
Design System Catalog의 모든 타입과 인터페이스를 정의하세요.

## 목표
- 모든 작업자가 참조할 수 있는 타입 정의
- Mock 데이터 구조 정의
- API 인터페이스 정의

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/data/types.ts`
- Mock 데이터: `admin-ui/src/features/design-system/data/mockData.ts`
- 상세 구조: 레포지토리 구조 섹션 참고

## 작업 내용

### 1. 타입 정의 파일 생성
위치: `admin-ui/src/features/design-system/data/types.ts`

모든 타입 정의 (제안서 참고):
- ComponentCategory
- ComponentStability
- ControlType, ControlDefinition
- ComponentExample
- AntiPattern
- A11yIssue, A11yScore
- RelatedDecision
- ComponentMeta
- ExtractedProp
- UsageInfo
- DesignSmell
- ProjectSettings
- A11yResult

### 2. Mock 데이터 구조 정의
위치: `admin-ui/src/features/design-system/data/mockData.ts`

Button, Input, Select의 Mock 데이터 생성

### 3. 유틸리티 인터페이스 정의
위치: `admin-ui/src/features/design-system/utils/types.ts`

함수 시그니처 타입 정의

## 완료 조건
- [ ] 모든 타입 정의 완료
- [ ] TypeScript 컴파일 에러 없음
- [ ] Mock 데이터 구조 정의
- [ ] 다른 작업자들이 import하여 사용 가능
```

---

## Phase 1: 독립 유틸리티들 (Independent Utilities)

**의존성**: Phase 0  
**예상 기간**: 각 2-3일  
**담당자**: 각 1명씩 (총 8명 동시 작업 가능)

**전략**: 각 유틸리티는 완전히 독립적이며, Mock 데이터로 테스트 가능

---

### Batch 1-A: Props 추출 유틸리티

**프롬프트**:

```
TypeScript 타입에서 Props 정보를 자동으로 추출하는 유틸리티를 구축하세요.

## 목표
- ts-morph을 사용하여 컴포넌트 Props 타입 분석
- Props Table 자동 생성
- JSDoc 주석에서 설명 추출

## 레포지토리 구조
- 파일 위치: `admin-ui/src/features/design-system/utils/propsExtractor.ts`
- 타입 참조: `admin-ui/src/features/design-system/data/types.ts` (ExtractedProp)
- 상세 구조: 레포지토리 구조 섹션 참고

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
import { ExtractedProp } from '../data/types'

export async function extractPropsFromComponent(
  componentPath: string,
  componentName: string
): Promise<ExtractedProp[]> {
  // ts-morph을 사용하여:
  // 1. 컴포넌트 파일 로드
  // 2. Props 인터페이스/타입 찾기
  // 3. 각 prop의 타입, required 여부 추출
  // 4. JSDoc 주석에서 description 추출
  // 5. 기본값 추출 (defaultProps 또는 기본 파라미터)
}
```

### 3. 테스트
- Mock 컴포넌트로 테스트
- Button, Input, Select로 실제 테스트

## 완료 조건
- [ ] Props 추출 기능 동작
- [ ] JSDoc 주석에서 description 추출
- [ ] 타입 문자열 포맷팅 정확
- [ ] 테스트 코드 작성
```

---

### Batch 1-B: 코드 생성 유틸리티

**프롬프트**:

```
컴포넌트와 Props를 받아 코드 스니펫을 생성하는 유틸리티를 구축하세요.

## 목표
- 기본 코드 스니펫 생성
- Props 포맷팅
- Import 경로 생성

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
  // Props를 문자열로 포맷팅
  // 예: variant="primary" size="md"
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
```

---

### Batch 1-C: 클립보드 훅

**프롬프트**:

```
클립보드 복사 기능을 제공하는 React 훅을 구축하세요.

## 목표
- 클립보드 복사 기능
- 복사 성공 피드백

## 작업 내용

### 1. useClipboard 훅
위치: `admin-ui/src/features/design-system/hooks/useClipboard.ts`

```typescript
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
```

---

### Batch 1-D: A11y 테스터 유틸리티

**프롬프트**:

```
axe-core를 사용하여 접근성을 테스트하는 유틸리티를 구축하세요.

## 목표
- 컴포넌트의 접근성 점수 자동 계산
- 이슈 목록 추출

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
```

---

### Batch 1-E: Usage Tracker 유틸리티

**프롬프트**:

```
코드베이스에서 컴포넌트 사용 위치를 찾는 유틸리티를 구축하세요.

## 목표
- 코드베이스 스캔하여 컴포넌트 사용 위치 찾기
- 사용 횟수 계산
- 코드 스니펫 추출

## 작업 내용

### 1. Usage Tracker 유틸리티
위치: `admin-ui/src/features/design-system/utils/usageTracker.ts`

```typescript
import { UsageInfo } from '../data/types'
import * as fs from 'fs'
import * as path from 'path'

export function trackComponentUsage(componentName: string): UsageInfo[] {
  // admin-ui/src 디렉토리 스캔
  // grep 또는 AST 분석으로 컴포넌트 사용 찾기
  // 각 파일의 사용 횟수 계산
  // 코드 스니펫 추출
}
```

### 2. 테스트
- Mock 파일로 테스트
- 실제 코드베이스로 테스트

## 완료 조건
- [ ] 코드베이스 스캔 기능 동작
- [ ] 사용 횟수 계산 정확
- [ ] 코드 스니펫 추출 정확
```

---

### Batch 1-F: Design Smell Detector 유틸리티

**프롬프트**:

```
코드에서 Design Pattern 위반을 감지하는 유틸리티를 구축하세요.

## 목표
- 패턴 위반 자동 감지
- 제안사항 제공

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
```

### 2. 테스트
- 다양한 코드 패턴으로 테스트

## 완료 조건
- [ ] Design Smell 감지 로직 구현
- [ ] 제안사항 제공
- [ ] 테스트 코드 작성
```

---

### Batch 1-G: Contrast Calculator 유틸리티

**프롬프트**:

```
색상 대비를 계산하는 유틸리티를 구축하세요.

## 목표
- WCAG 대비 비율 계산
- AAA/AA 통과 여부 판단

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
  // 색상을 RGB로 변환
  // 상대 휘도 계산
  // 대비 비율 계산
  // WCAG 기준 확인
}
```

### 2. 테스트
- 다양한 색상 조합으로 테스트

## 완료 조건
- [ ] 대비 비율 계산 정확
- [ ] WCAG 기준 확인 정확
- [ ] 테스트 코드 작성
```

---

### Batch 1-H: Component Analyzer 유틸리티

**프롬프트**:

```
코드베이스를 분석하여 컴포넌트 사용 통계를 생성하는 유틸리티를 구축하세요.

## 목표
- 컴포넌트 사용 횟수 계산
- 패턴 분석 (어떤 컴포넌트가 함께 쓰이는지)

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
}
```

### 2. 테스트
- Mock 코드베이스로 테스트

## 완료 조건
- [ ] 사용 횟수 계산 정확
- [ ] 패턴 분석 정확
- [ ] 테스트 코드 작성
```

---

## Phase 2: 기본 인프라 (Infrastructure)

**의존성**: Phase 0  
**예상 기간**: 각 2-3일  
**담당자**: 각 1명씩 (총 3명 동시 작업 가능)

---

### Batch 2-A: 라우팅 설정

**프롬프트**:

```
Design System 페이지의 라우팅을 설정하세요.

## 작업 내용

### 1. 라우팅 설정
위치: `admin-ui/src/app/routes/AppRoutes.tsx`

```typescript
<Route path="/design-system" element={<DesignSystemPage />}>
  <Route index element={<Navigate to="foundations/colors" />} />
  <Route path="foundations/:section" element={<FoundationsSection />} />
  <Route path="components/:category" element={<ComponentCategory />} />
  <Route path="components/:category/:name" element={<ComponentDetail />} />
  <Route path="patterns/:pattern" element={<PatternGuide />} />
  <Route path="resources/:resource" element={<ResourcePage />} />
</Route>
```

## 완료 조건
- [ ] 라우팅 설정 완료
- [ ] 각 경로 접근 가능
```

---

### Batch 2-B: 레이아웃 컴포넌트

**프롬프트**:

```
Design System 페이지의 기본 레이아웃 컴포넌트를 구축하세요.

## 작업 내용

### 1. 레이아웃 컴포넌트 생성
위치: `admin-ui/src/features/design-system/components/layout/`

- `Sidebar.tsx`: 좌측 네비게이션
- `ContentArea.tsx`: 메인 콘텐츠 영역
- `Header.tsx`: 상단 헤더 (검색 placeholder)

### 2. 메인 페이지 컴포넌트
위치: `admin-ui/src/features/design-system/ui/DesignSystemPage.tsx`

- Sidebar + ContentArea 레이아웃

## 완료 조건
- [ ] 레이아웃 컴포넌트 완성
- [ ] 반응형 디자인 적용
- [ ] 스타일링 완료
```

---

### Batch 2-C: 레지스트리 구조 & Mock 데이터

**프롬프트**:

```
컴포넌트 레지스트리 구조와 Mock 데이터를 구축하세요.

## 작업 내용

### 1. 레지스트리 파일 생성
위치: `admin-ui/src/features/design-system/data/componentRegistry.ts`

```typescript
import { ComponentMeta } from './types'

export const componentRegistry: ComponentMeta[] = [
  // Mock 데이터로 초기화
]

export function getComponentByName(name: string): ComponentMeta | undefined
export function getComponentsByCategory(category: ComponentCategory): ComponentMeta[]
export function getAllCategories(): ComponentCategory[]
```

### 2. Mock 데이터
- Button, Input, Select의 Mock 데이터 생성

## 완료 조건
- [ ] 레지스트리 구조 완성
- [ ] Mock 데이터 생성
- [ ] 조회 함수 구현
```

---

## Phase 3: 컴포넌트 전시 (Component Showcases)

**의존성**: Phase 0, Phase 2-C  
**예상 기간**: 각 3-4일  
**담당자**: 각 1명씩 (총 3명 동시 작업 가능)

**전략**: 각 컴포넌트 전시는 완전히 독립적이며, Mock 데이터로 작업 가능

---

### Batch 3-A: Button 전시

**프롬프트**:

```
Button 컴포넌트의 전시 페이지를 구현하세요.

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

## 완료 조건
- [ ] Button 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
```

---

### Batch 3-B: Input 전시

**프롬프트**:

```
Input 컴포넌트의 전시 페이지를 구현하세요.

## 작업 내용
- Batch 3-A와 동일한 구조
- Input 컴포넌트에 맞게 조정

## 완료 조건
- [ ] Input 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
```

---

### Batch 3-C: Select 전시

**프롬프트**:

```
Select 컴포넌트의 전시 페이지를 구현하세요.

## 작업 내용
- Batch 3-A와 동일한 구조
- Select 컴포넌트에 맞게 조정

## 완료 조건
- [ ] Select 상세 페이지 완성
- [ ] Live Preview 동작
- [ ] 레지스트리 등록 완료
```

---

## Phase 4: Foundations (완전 병렬)

**의존성**: Phase 0, Phase 2-B  
**예상 기간**: 각 2-3일  
**담당자**: 각 1명씩 (총 5명 동시 작업 가능)

---

### Batch 4-A: Colors

**프롬프트**:

```
Colors Foundation 페이지를 구현하세요.

## 작업 내용

### 1. Design Token SSOT
위치: `admin-ui/src/features/design-system/data/tokens/colors.json`

- 컬러 팔레트 정의

### 2. ColorPalette 컴포넌트
위치: `admin-ui/src/features/design-system/components/foundations/ColorPalette.tsx`

- 컬러 팔레트 그리드 표시
- CSS Variable 복사 기능

### 3. FoundationsSection 페이지
- Colors 섹션 렌더링

## 완료 조건
- [ ] ColorPalette 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
- [ ] `/design-system/foundations/colors` 접속 시 페이지 표시
```

---

### Batch 4-B: Typography

**프롬프트**:

```
Typography Foundation 페이지를 구현하세요.

## 작업 내용
- Batch 4-A와 동일한 구조
- Typography에 맞게 조정

## 완료 조건
- [ ] TypographyScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
```

---

### Batch 4-C: Spacing

**프롬프트**:

```
Spacing Foundation 페이지를 구현하세요.

## 작업 내용
- Batch 4-A와 동일한 구조
- Spacing에 맞게 조정

## 완료 조건
- [ ] SpacingScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
```

---

### Batch 4-D: Shadows

**프롬프트**:

```
Shadows Foundation 페이지를 구현하세요.

## 작업 내용
- Batch 4-A와 동일한 구조
- Shadows에 맞게 조정

## 완료 조건
- [ ] ShadowScale 컴포넌트 완성
- [ ] CSS Variable 복사 기능 동작
```

---

### Batch 4-E: Motion

**프롬프트**:

```
Motion Foundation 페이지를 구현하세요.

## 작업 내용
- Batch 4-A와 동일한 구조
- Motion에 맞게 조정

## 완료 조건
- [ ] MotionPreview 컴포넌트 완성
- [ ] 애니메이션 프리셋 표시
```

---

## Phase 5: 핵심 기능 (Core Features)

**의존성**: Phase 1, Phase 2, Phase 3  
**예상 기간**: 각 2-3일  
**담당자**: 3-5명

---

### Batch 5-A: Props Playground

**의존성**: Phase 3-A/B/C  
**프롬프트**:

```
Props Playground를 구현하여 사용자가 Props를 실시간으로 조작할 수 있게 하세요.

## 작업 내용

### 1. PropsPlayground 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/PropsPlayground.tsx`

- Props 컨트롤 (ControlRenderer)
- 실시간 미리보기
- 생성된 코드 표시

### 2. usePlayground 훅
위치: `admin-ui/src/features/design-system/hooks/usePlayground.ts`

- Props 상태 관리
- Reset 기능

## 완료 조건
- [ ] Props 컨트롤 동작
- [ ] 실시간 미리보기 업데이트
- [ ] 코드 스니펫 자동 생성
```

---

### Batch 5-B: Props Table UI

**의존성**: Phase 1-A  
**프롬프트**:

```
Props Table 컴포넌트를 구현하세요.

## 작업 내용

### 1. PropsTable 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/PropsTable.tsx`

- propsExtractor로 추출한 Props 정보를 테이블로 표시
- 컬럼: Prop, Type, Default, Required, Description

### 2. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 PropsTable 섹션 추가

## 완료 조건
- [ ] Props Table 표시
- [ ] 컴포넌트 상세 페이지에 통합
```

---

### Batch 5-C: Code Block UI

**의존성**: Phase 1-B, Phase 1-C  
**프롬프트**:

```
Code Block 컴포넌트를 구현하세요.

## 작업 내용

### 1. CodeBlock 컴포넌트
위치: `admin-ui/src/features/design-system/components/showcase/CodeBlock.tsx`

- 코드를 syntax highlighting하여 표시
- 복사 버튼 (useClipboard 훅 사용)

### 2. 컴포넌트 상세 페이지에 통합
- ComponentDetail 페이지에 CodeBlock 섹션 추가

## 완료 조건
- [ ] Code Block 표시
- [ ] 복사 기능 동작
- [ ] 컴포넌트 상세 페이지에 통합
```

---

### Batch 5-D: 검색 기능

**의존성**: Phase 2-C  
**프롬프트**:

```
컴포넌트 검색 기능을 구현하세요.

## 작업 내용

### 1. 검색 기능
위치: `admin-ui/src/features/design-system/components/layout/Header.tsx`

- 검색 바 추가
- 컴포넌트 이름, 설명, 카테고리로 검색

### 2. useSearch 훅
위치: `admin-ui/src/features/design-system/hooks/useSearch.ts`

- 검색 로직 구현

## 완료 조건
- [ ] 검색 기능 동작
- [ ] 검색 결과 실시간 업데이트
```

---

### Batch 5-E: URL 상태 공유

**의존성**: Phase 5-A  
**프롬프트**:

```
Props Playground 상태를 URL에 저장하고 복원하는 기능을 구현하세요.

## 작업 내용

### 1. URL 상태 관리
- Props 변경 시 URL 쿼리 파라미터 업데이트
- URL에서 초기 상태 로드

### 2. URL 복사 버튼
- ComponentDetail 페이지에 "Copy URL" 버튼 추가

## 완료 조건
- [ ] Props 상태가 URL에 반영
- [ ] URL에서 상태 복원
- [ ] URL 복사 기능 동작
```

---

## Phase 6: 고급 기능 (Advanced Features)

**의존성**: Phase 1, Phase 2, Phase 3  
**예상 기간**: 각 2-3일  
**담당자**: 5-8명

각 배치는 독립적으로 작업 가능합니다. 상세 프롬프트는 기존 문서를 참고하세요.

---

### Batch 6-A: Usage Examples

**의존성**: Phase 3-A/B/C  
**작업**: UsageExamples 컴포넌트 구현

---

### Batch 6-B: Anti-Patterns

**의존성**: Phase 3-A/B/C  
**작업**: AntiPatterns 컴포넌트 구현

---

### Batch 6-C: Component Decision Guide

**의존성**: Phase 3-A/B/C  
**작업**: ComponentDecision 컴포넌트 구현

---

### Batch 6-D: UsedIn 컴포넌트

**의존성**: Phase 1-E  
**작업**: UsedIn 컴포넌트 구현

---

### Batch 6-E: Icon Gallery

**의존성**: Phase 2-B  
**작업**: IconGallery 컴포넌트 구현

---

### Batch 6-F: Responsive Preview

**의존성**: Phase 2-B  
**작업**: ResponsiveFrame 컴포넌트 구현

---

### Batch 6-G: A11y Score UI

**의존성**: Phase 1-D  
**작업**: A11yScore 컴포넌트 구현

---

### Batch 6-H: Contrast Checker UI

**의존성**: Phase 1-G  
**작업**: ContrastChecker 컴포넌트 구현

---

## Phase 7: DX Enhancement

**의존성**: Phase 1, Phase 2, Phase 3  
**예상 기간**: 각 2-3일  
**담당자**: 4-7명

각 배치는 독립적으로 작업 가능합니다.

---

### Batch 7-A: Project-aware Code Gen UI

**의존성**: Phase 1-B  
**작업**: 프로젝트 설정 선택 UI 구현

---

### Batch 7-B: Design Smell UI

**의존성**: Phase 1-F  
**작업**: Design Smell 표시 UI 구현

---

### Batch 7-C: Runtime Validation

**의존성**: Phase 3-A/B/C  
**작업**: 개발 모드에서 props 검증

---

### Batch 7-D: Keyboard Shortcuts

**의존성**: Phase 2-B  
**작업**: 전역 키보드 단축키 구현

---

### Batch 7-E: Context-aware Copy

**의존성**: Phase 1-B, Phase 1-C  
**작업**: 컨텍스트별 코드 복사 옵션 구현

---

### Batch 7-F: Component Recommendation UI

**의존성**: Phase 1-H  
**작업**: 컴포넌트 추천 UI 구현

---

### Batch 7-G: Visual Diff

**의존성**: Phase 2-C  
**작업**: 버전 간 UI 차이 표시

---

## Phase 8: Organization Scale

**의존성**: 모든 Phase 완료  
**예상 기간**: 3-4일  
**담당자**: 1-2명

---

### Batch 8-A: Health Dashboard

**작업**: Design System Health 대시보드 구현

---

### Batch 8-B: RFC/ADR 연결

**작업**: 관련 결정사항 자동 연결

---

### Batch 8-C: PR Template 통합

**작업**: PR Template에 DS 체크리스트 추가

---

### Batch 8-D: CI 통합

**작업**: GitHub Actions에서 DS Rule 검사

---

## 작업 배치 요약표

| Phase | 배치 수 | 최대 동시 작업자 | 예상 기간 |
|-------|---------|-----------------|-----------|
| Phase 0 | 1 | 1명 | 1일 |
| Phase 1 | 8 | 8명 | 2-3일 |
| Phase 2 | 3 | 3명 | 2-3일 |
| Phase 3 | 3 | 3명 | 3-4일 |
| Phase 4 | 5 | 5명 | 2-3일 |
| Phase 5 | 5 | 3-5명 | 2-3일 |
| Phase 6 | 8 | 5-8명 | 2-3일 |
| Phase 7 | 7 | 4-7명 | 2-3일 |
| Phase 8 | 4 | 1-2명 | 3-4일 |
| **총합** | **44개** | **최대 19명** | **약 6-8주** |

---

## 병렬 작업 가이드

### Week 1
- **Day 1**: Phase 0 완료 (1명)
- **Day 2-4**: Phase 1 시작 (8명 동시 작업)
- **Day 2-4**: Phase 2 시작 (3명 동시 작업)

### Week 2
- **Day 1-4**: Phase 1 완료
- **Day 1-4**: Phase 2 완료
- **Day 1-4**: Phase 3 시작 (3명 동시 작업)
- **Day 1-4**: Phase 4 시작 (5명 동시 작업)

### Week 3-4
- Phase 3, 4 완료
- Phase 5 시작 (3-5명)
- Phase 6 시작 (5-8명)

### Week 5-6
- Phase 5, 6 완료
- Phase 7 시작 (4-7명)

### Week 7-8
- Phase 7 완료
- Phase 8 완료

---

## 작업 시작 전 체크리스트

각 배치 작업 시작 전:

1. [ ] Phase 0 완료 확인
2. [ ] 의존성 배치 완료 확인
3. [ ] 관련 타입 import 가능 확인
4. [ ] 레포지토리 구조 확인 (파일 생성 위치 확인)
5. [ ] 브랜치 생성 (`feature/design-system-phase-X-batch-Y`)
6. [ ] 작업 범위 명확화
7. [ ] Mock 데이터로 테스트 가능한지 확인
8. [ ] 파일 네이밍 규칙 준수 (컴포넌트: PascalCase, 훅: use prefix, 유틸: camelCase)
9. [ ] Import 경로 확인 (절대 경로 사용: `@/features/design-system/...`)

---

## PR 가이드라인

각 배치 완료 시:

1. **PR 제목**: `[Design System] Phase X Batch Y: 작업 내용`
2. **PR 설명**:
   - 작업 내용 요약
   - 완료 조건 체크리스트
   - 스크린샷 (UI 변경 시)
   - 테스트 방법
   - 의존성 배치와의 통합 방법
3. **리뷰 요청**: Design System 팀 또는 Tech Lead
4. **머지 후**: 다음 배치 작업자에게 알림

---

## 동시성 최대화 팁

1. **인터페이스 우선**: Phase 0에서 모든 타입 정의
2. **Mock 데이터 활용**: 실제 컴포넌트 없이도 UI 작업 가능
3. **작은 단위 분할**: 컴포넌트별, Foundation별로 개별 배치
4. **독립 작업**: 유틸리티는 완전히 독립적으로 작업 가능
5. **통합은 나중에**: 각 배치는 독립적으로 완성 후 통합

---

## 관련 문서

- **레포지토리 구조 상세**: [design-system-catalog-repo-structure.md](./design-system-catalog-repo-structure.md)
- **제안서**: [design-system-catalog.md](./design-system-catalog.md)
- **기존 DAG 문서**: [design-system-catalog-implementation-dag.md](./design-system-catalog-implementation-dag.md)

---

**작성일**: 2026-02-01  
**버전**: 2.0 (Maximum Concurrency)  
**상태**: Ready for Execution
