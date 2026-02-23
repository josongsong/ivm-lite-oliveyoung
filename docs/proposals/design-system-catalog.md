# Living Design System — Catalog

**Subtitle**: 개발자/디자이너 모두 열광하는 Self-Documenting Design System

**Version**: 1.0 (Execution Ready)

---

## 0. Executive Thesis (DX 관점 단일 문장)

> Design System Catalog는 컴포넌트 전시가 아니라
> **"개발이 시작되는 곳이자, 끝나는 곳"**이어야 한다.

---

## 1. 핵심 가치 (Core Values)

### 1-1. Zero Friction
- 복사 한번에 바로 사용
- 프로젝트 설정에 맞는 코드 자동 생성
- 붙여넣고 즉시 동작

### 1-2. Self-Documenting
- 코드가 곧 문서
- Props → 타입 → 문서 단방향 자동화
- 수동 문서 작성 금지

### 1-3. Interactive First
- 만져보며 이해
- Props Playground로 실시간 탐색
- URL 상태 공유로 커뮤니케이션 제거

### 1-4. Trustworthy
- A11y Score는 axe-core 실측 기반
- Component Contract Stability 표시
- Design Token SSOT (Single Source of Truth)

---

## 2. 예상 임팩트 (Expected Impact)

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 컴포넌트 사용법 질문 | 일일 10건+ | 일일 1건 이하 | **90% 감소** |
| 신규 개발자 온보딩 | 2주 | 1주 | **50% 단축** |
| 디자인-개발 핸드오프 마찰 | 높음 | 거의 없음 | **제거** |
| 코드리뷰 피로도 | 높음 | 거의 0 | **급감** |
| Design System 신뢰도 | 중간 | 최고 | **폭증** |

---

## 3. 사용자 시나리오 (User Scenarios)

### 3-1. 개발자 A: "Button에 loading 상태 어떻게 넣지?"

**Before (현재)**:
1. Slack에서 질문
2. 동료가 코드 예시 보내줌
3. 복사해서 사용

**After (목표)**:
1. `/design-system/button` 접속
2. Playground에서 `loading` 토글
3. 자동 생성된 코드 복사
4. 끝 (약 10초)

### 3-2. 디자이너 B: "우리 컬러 팔레트 뭐였지?"

**Before**:
1. Figma 파일 찾기
2. 디자인 토큰 확인
3. 개발자에게 CSS 변수 물어보기

**After**:
1. `/design-system/foundations/colors` 접속
2. 전체 팔레트 한눈에 확인
3. 색상 클릭 → CSS Variable 자동 복사
4. Figma에 동기화

### 3-3. 테크리드 C: "이 컴포넌트 Props 스펙 공유해줘"

**Before**:
1. 문서 작성 또는 스크린샷
2. Slack/PR에 공유
3. 질문 답변 반복

**After**:
1. 해당 컴포넌트 페이지 URL 공유
2. Props Table + 예제 코드 포함
3. 별도 문서 작성 불필요

### 3-4. 신입 개발자 D: "어떤 컴포넌트 써야 하지?"

**Before**:
1. 코드베이스 전체 탐색
2. 비슷한 패턴 찾기
3. 불확실한 선택

**After**:
1. `/design-system` 접속
2. "이 프로젝트에서 자주 쓰는 컴포넌트" 확인
3. "현재 페이지 컨텍스트 기반 추천" 확인
4. 남들 쓰는 방식 바로 학습

---

## 4. 정보 아키텍처 (Information Architecture)

```
Design System
├── 🎨 Foundations (기반)
│   ├── Colors        # CSS Variables 팔레트 + Contrast Checker
│   ├── Typography    # 폰트 스케일 + 사용 가이드
│   ├── Spacing       # 간격 시스템 시각화
│   ├── Shadows       # 그림자 레벨
│   └── Motion        # 애니메이션 프리셋
│
├── 🧱 Components (컴포넌트)
│   ├── Actions       # Button, IconButton
│   ├── Forms         # Input, TextArea, Select, Label
│   ├── Feedback      # Loading, Toast, StatusBadge
│   ├── Layout        # Modal, Accordion, Tabs
│   ├── Data Display  # Table, Chip, Pagination
│   └── Utilities     # ErrorBoundary, YamlViewer
│
├── 📐 Patterns (패턴)
│   ├── Form Layout   # 폼 구성 가이드
│   ├── Error States  # 에러 처리 패턴
│   ├── Loading States# 로딩 패턴
│   └── Empty States  # 빈 상태 패턴
│
└── 🛠 Resources (리소스)
    ├── Icon Gallery  # 전체 아이콘 검색
    ├── Changelog     # 컴포넌트 변경 이력 + Visual Diff
    └── Migration     # 버전 업그레이드 가이드 + Codemod
```

---

## 5. 핵심 기능 상세 (Core Features)

### 5-1. Component Showcase (컴포넌트 전시)

```
┌─────────────────────────────────────────────────────────────┐
│ Button                                        [📋 Copy URL] │
│ 사용자 액션을 트리거하는 기본 인터랙티브 요소                    │
│ Stability: ✅ Stable | A11y: 95/100                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─ Live Preview ─────────────────────────────────────────┐ │
│  │                                                        │ │
│  │    [  Primary  ]  [  Secondary  ]  [  Ghost  ]        │ │
│  │                                                        │ │
│  │    [  Small  ]    [  Medium  ]     [  Large  ]        │ │
│  │                                                        │ │
│  │    [  Loading...  ]   [  Disabled  ]                  │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Interactive Playground ───────────────────────────────┐ │
│  │                                                        │ │
│  │  variant   [primary     ▼]    size      [md    ▼]     │ │
│  │  children  [Click me      ]    disabled  [  ]          │ │
│  │  loading   [  ]               fullWidth  [  ]          │ │
│  │                                                        │ │
│  │  Result:                                               │ │
│  │  ┌──────────────────────────────────────────────────┐ │ │
│  │  │              [  Click me  ]                      │ │ │
│  │  └──────────────────────────────────────────────────┘ │ │
│  │                                                        │ │
│  │  URL: /design-system/button?variant=primary&size=md  │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Code ──────────────────────────────────────── [Copy] ─┐ │
│  │ [Framework] React  [Style] Tailwind  [Import] alias    │ │
│  │                                                        │ │
│  │ import { Button } from '@/shared/ui'                   │ │
│  │                                                        │ │
│  │ <Button variant="primary" size="md">                   │ │
│  │   Click me                                             │ │
│  │ </Button>                                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Props Reference ──────────────────────────────────────┐ │
│  │ Prop      │ Type              │ Default   │ Required  │ │
│  │───────────┼───────────────────┼───────────┼───────────│ │
│  │ variant   │ 'primary'|'sec...'│ 'primary' │     -     │ │
│  │ size      │ 'sm'|'md'|'lg'    │ 'md'      │     -     │ │
│  │ onClick   │ () => void        │     -     │     ✓     │ │
│  │ disabled  │ boolean           │ false     │     -     │ │
│  │ loading   │ boolean           │ false     │     -     │ │
│  │ children  │ ReactNode         │     -     │     ✓     │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Usage Examples ───────────────────────────────────────┐ │
│  │ ▸ Form Submit Button                                   │ │
│  │   Why: Primary action은 항상 primary variant 사용      │ │
│  │   Code: <Button variant="primary">Submit</Button>      │ │
│  │                                                        │ │
│  │ ▸ Destructive Action                                   │ │
│  │   Why: 삭제/위험한 액션은 danger variant 사용          │ │
│  │   Code: <Button variant="danger">Delete</Button>       │ │
│  │                                                        │ │
│  │ ▸ With Icon                                            │ │
│  │   Why: 아이콘과 함께 사용 시 IconButton 고려           │ │
│  │   Code: <Button><Icon /> Save</Button>                 │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Anti-Patterns ────────────────────────────────────────┐ │
│  │ ❌ Button 안에 Spinner 직접 넣기                       │ │
│  │    ✅ loading prop 사용                                │ │
│  │                                                        │ │
│  │ ❌ onClick 없이 Button 렌더링                          │ │
│  │    ✅ disabled prop 사용 또는 LinkButton 고려          │ │
│  │                                                        │ │
│  │ ❌ Icon-only Button에 aria-label 없음                 │ │
│  │    ✅ IconButton 사용 또는 aria-label 필수            │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Used In ──────────────────────────────────────────────┐ │
│  │ 이 컴포넌트가 사용되는 곳:                             │ │
│  │ • CheckoutPage.tsx (12회)                              │ │
│  │ • SignupForm.tsx (5회)                                 │ │
│  │ • AdminUserEdit.tsx (3회)                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Accessibility ───────────────────────────────────────┐ │
│  │ Score: 95/100  ████████████████████░░  Excellent       │ │
│  │                                                       │ │
│  │ ✅ Keyboard navigable                                 │ │
│  │ ✅ ARIA labels present                                │ │
│  │ ✅ Color contrast AAA                                  │ │
│  │ ⚠️ Loading 상태에서 focus-visible 개선 필요            │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 5-2. Foundations - Colors

```
┌─────────────────────────────────────────────────────────────┐
│ Colors                                                      │
│ 디자인 시스템의 컬러 팔레트 (SSOT)                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Primary                                                    │
│  ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐      │
│  │ 50 │100 │200 │300 │400 │500 │600 │700 │800 │900 │      │
│  │████│████│████│████│████│████│████│████│████│████│      │
│  └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘      │
│  클릭 시: --color-primary-500 복사됨 ✓                       │
│                                                             │
│  Semantic Colors                                            │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐ │
│  │   Success   │   Warning   │    Error    │    Info     │ │
│  │    ████     │    ████     │    ████     │    ████     │ │
│  │   #22C55E   │   #F59E0B   │   #EF4444   │   #3B82F6   │ │
│  └─────────────┴─────────────┴─────────────┴─────────────┘ │
│                                                             │
│  ┌─ Contrast Checker ─────────────────────────────────────┐ │
│  │ Background: [--color-gray-900  ▼]                      │ │
│  │ Foreground: [--color-white     ▼]                      │ │
│  │                                                        │ │
│  │ Contrast Ratio: 15.8:1  ✅ AAA Pass                    │ │
│  │                                                        │ │
│  │ [Test All Combinations]                                │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─ Export Options ──────────────────────────────────────┐ │
│  │ [Copy CSS Variables] [Copy Tailwind Config]            │ │
│  │ [Export Figma Tokens] [Export JSON]                    │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 5-3. Icon Gallery

```
┌─────────────────────────────────────────────────────────────┐
│ Icons                                    [🔍 Search icons ] │
├─────────────────────────────────────────────────────────────┤
│ Filter: [All ▼]  [Outlined ○]  [Filled ●]                  │
│                                                             │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐  │
│  │  ✕  │ │  +  │ │  ✓  │ │  ⚙  │ │  🔍 │ │  ⬇  │ │  ⬆  │  │
│  │Close│ │ Add │ │Check│ │Gear │ │Srch │ │Down │ │ Up  │  │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘  │
│                                                             │
│  클릭 시:                                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ import { X } from 'lucide-react'            [Copy] ✓  │ │
│  │                                                        │ │
│  │ <X className="w-4 h-4" />                              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 5-4. Responsive Preview

```
┌─────────────────────────────────────────────────────────────┐
│ Responsive Preview          [📱 375] [💻 768] [🖥 1280]    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐│
│  │ ┌─────────┐                                             ││
│  │ │         │  ← 현재 컴포넌트가 각 뷰포트에서             ││
│  │ │ [Btn]   │    어떻게 보이는지 실시간 확인               ││
│  │ │         │                                             ││
│  │ └─────────┘                                             ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 5-5. Component Decision Guide

```
┌─────────────────────────────────────────────────────────────┐
│ Button vs IconButton vs LinkButton                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  사용자 행동을 유발하는가?                                   │
│  ├─ Yes → Button                                            │
│  │   └─ 아이콘만 있는가?                                    │
│  │      ├─ Yes → IconButton                                 │
│  │      └─ No → Button                                      │
│  │                                                          │
│  └─ No → 페이지 이동인가?                                    │
│      ├─ Yes → LinkButton                                    │
│      └─ No → 다른 컴포넌트 고려                             │
│                                                             │
│  [Decision Tree 시각화]                                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. 기술 설계 (Technical Design)

### 6-1. 디렉토리 구조

```
features/design-system/
├── ui/
│   └── DesignSystemPage.tsx
│   └── DesignSystemPage.css
│
├── components/
│   ├── layout/
│   │   ├── Sidebar.tsx              # 네비게이션
│   │   ├── ContentArea.tsx          # 메인 콘텐츠
│   │   └── Header.tsx               # 검색 + 테마 토글
│   │
│   ├── showcase/
│   │   ├── ComponentShowcase.tsx    # 컴포넌트 전시 컨테이너
│   │   ├── LivePreview.tsx          # 라이브 렌더링
│   │   ├── PropsPlayground.tsx      # 인터랙티브 Props 조작
│   │   ├── CodeBlock.tsx            # 코드 표시 + 복사
│   │   ├── PropsTable.tsx           # Props 문서화 (자동 생성)
│   │   ├── UsageExamples.tsx        # 사용 예제
│   │   ├── AntiPatterns.tsx         # Anti-pattern 갤러리
│   │   ├── UsedIn.tsx               # 사용 위치 역추적
│   │   └── ComponentDecision.tsx    # 선택 가이드
│   │
│   ├── foundations/
│   │   ├── ColorPalette.tsx         # 컬러 시각화
│   │   ├── TypographyScale.tsx      # 타이포그래피
│   │   ├── SpacingScale.tsx         # 간격 시스템
│   │   ├── ShadowScale.tsx          # 그림자
│   │   └── MotionPreview.tsx        # 애니메이션
│   │
│   ├── resources/
│   │   ├── IconGallery.tsx          # 아이콘 검색
│   │   ├── Changelog.tsx            # 변경 이력 + Visual Diff
│   │   └── Migration.tsx            # 마이그레이션 가이드
│   │
│   └── utilities/
│       ├── ContrastChecker.tsx      # 대비 검사
│       ├── ResponsiveFrame.tsx      # 반응형 프리뷰
│       ├── A11yScore.tsx            # 접근성 점수 (axe-core)
│       ├── HealthDashboard.tsx     # DS Health 대시보드
│       └── DesignSmellDetector.tsx  # 패턴 위반 감지
│
├── data/
│   ├── componentRegistry.ts         # 컴포넌트 메타데이터
│   ├── colorTokens.ts               # 컬러 토큰 정의 (SSOT)
│   ├── iconList.ts                  # 아이콘 목록
│   └── usageTracker.ts              # 실제 사용 위치 추적
│
├── hooks/
│   ├── usePlayground.ts             # Playground 상태 관리
│   ├── useClipboard.ts              # 클립보드 복사
│   ├── useSearch.ts                 # 검색 로직 (의도 기반)
│   ├── useCodeGenerator.ts          # 프로젝트 설정 기반 코드 생성
│   └── useComponentRecommendation.ts # 컴포넌트 추천
│
└── utils/
    ├── propsExtractor.ts            # Props 타입 추출 (ts-morph)
    ├── codeGenerator.ts             # 코드 스니펫 생성
    ├── contrastCalculator.ts        # 대비 계산
    ├── a11yTester.ts                # axe-core 통합
    └── designSmellDetector.ts       # 패턴 위반 감지
```

### 6-2. 컴포넌트 레지스트리

```typescript
// data/componentRegistry.ts
export interface ComponentMeta {
  name: string
  category: 'actions' | 'forms' | 'feedback' | 'layout' | 'data-display' | 'utilities'
  description: string
  component: React.ComponentType<any>
  
  // 안정성 표시
  stability: 'stable' | 'experimental' | 'deprecated'
  deprecationReason?: string
  
  // Playground 컨트롤 정의
  controls: Record<string, ControlDefinition>
  
  // 미리 정의된 예제들 (Why 포함)
  examples: Array<{
    title: string
    intent: string  // 사용 이유
    props: Record<string, any>
  }>
  
  // Anti-patterns
  antiPatterns: Array<{
    title: string
    code: string
    reason: string
    correct: string
  }>
  
  // 관련 컴포넌트
  related: string[]
  
  // 접근성 점수 (상태별)
  a11y: Record<string, {
    score: number
    issues: Array<{ id: string; message: string; severity: 'error' | 'warning' }>
  }>
  
  // RFC/ADR 연결
  relatedDecisions?: Array<{
    type: 'RFC' | 'ADR'
    id: string
    title: string
  }>
}

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
      fullWidth: { type: 'boolean', default: false },
      children: { type: 'text', default: 'Click me' },
    },
    
    examples: [
      { 
        title: 'Form Submit', 
        intent: 'Primary action은 항상 primary variant 사용',
        props: { variant: 'primary', children: 'Submit' } 
      },
      { 
        title: 'Destructive Action', 
        intent: '삭제/위험한 액션은 danger variant 사용',
        props: { variant: 'danger', children: 'Delete' } 
      },
    ],
    
    antiPatterns: [
      {
        title: 'Button 안에 Spinner 직접 넣기',
        code: '<Button><Spinner /> Loading</Button>',
        reason: 'loading prop이 이미 존재하며 접근성 처리됨',
        correct: '<Button loading>Loading</Button>'
      },
      {
        title: 'onClick 없이 Button 렌더링',
        code: '<Button>Click me</Button>',
        reason: '클릭 불가능한 버튼은 disabled 또는 LinkButton 사용',
        correct: '<Button disabled>Click me</Button>'
      }
    ],
    
    related: ['IconButton', 'LinkButton'],
    
    a11y: {
      default: { score: 95, issues: [] },
      disabled: { score: 100, issues: [] },
      loading: { 
        score: 92, 
        issues: [{ 
          id: 'focus-visible', 
          message: 'Loading 상태에서 focus-visible 개선 필요',
          severity: 'warning' 
        }] 
      }
    },
    
    relatedDecisions: [
      { type: 'ADR', id: '012', title: 'Button variant 정책' },
      { type: 'RFC', id: '045', title: 'Form Error Pattern' }
    ]
  },
  // ... 더 많은 컴포넌트
]
```

### 6-3. Props Playground 구현

```typescript
// components/showcase/PropsPlayground.tsx
interface PropsPlaygroundProps {
  component: React.ComponentType<any>
  controls: Record<string, ControlDefinition>
  onChange?: (props: Record<string, any>) => void
}

export function PropsPlayground({ component: Component, controls }: PropsPlaygroundProps) {
  const [props, setProps] = useState(getDefaultProps(controls))
  const searchParams = useSearchParams()
  
  // URL에서 초기 상태 로드
  useEffect(() => {
    const urlProps = parsePropsFromURL(searchParams)
    if (urlProps) {
      setProps(urlProps)
    }
  }, [searchParams])
  
  // Props 변경 시 URL 업데이트
  useEffect(() => {
    const url = generateURLWithProps(Component.displayName, props)
    window.history.replaceState({}, '', url)
  }, [props])
  
  return (
    <div className="props-playground">
      <div className="props-playground__controls">
        {Object.entries(controls).map(([key, control]) => (
          <ControlRenderer
            key={key}
            name={key}
            control={control}
            value={props[key]}
            onChange={(v) => setProps(prev => ({ ...prev, [key]: v }))}
          />
        ))}
      </div>
      
      <div className="props-playground__preview">
        <Component {...props} />
      </div>
      
      <CodeBlock 
        code={generateCode(Component.displayName, props, {
          framework: 'react',
          style: 'tailwind',
          import: 'alias'
        })} 
      />
    </div>
  )
}
```

### 6-4. Project-aware Code Generation

```typescript
// hooks/useCodeGenerator.ts
interface CodeGenOptions {
  framework: 'react' | 'next' | 'vite'
  style: 'tailwind' | 'css-module' | 'vanilla-css'
  import: 'alias' | 'relative'
}

export function useCodeGenerator(componentName: string, props: Record<string, any>) {
  const options = useProjectSettings() // 프로젝트 설정 자동 감지
  
  return useMemo(() => {
    const importPath = options.import === 'alias' 
      ? `@/shared/ui/${componentName.toLowerCase()}`
      : `../shared/ui/${componentName}`
    
    const styleClass = options.style === 'tailwind'
      ? generateTailwindClasses(props)
      : generateCSSModuleClasses(props)
    
    return generateCodeSnippet({
      component: componentName,
      props,
      importPath,
      styleClass
    })
  }, [componentName, props, options])
}
```

### 6-5. Design Token SSOT

```typescript
// tokens/colors.json (SSOT)
{
  "primary": {
    "50": "#eff6ff",
    "100": "#dbeafe",
    // ...
    "900": "#1e3a8a"
  },
  "semantic": {
    "success": "#22c55e",
    "warning": "#f59e0b",
    "error": "#ef4444",
    "info": "#3b82f6"
  }
}

// 자동 파이프라인
// tokens → CSS Variables
// tokens → Tailwind config
// tokens → Figma Tokens
// tokens → Design System UI
```

### 6-6. A11y Score (axe-core 기반)

```typescript
// utils/a11yTester.ts
import * as axe from 'axe-core'

export async function testA11y(element: HTMLElement): Promise<A11yResult> {
  const results = await axe.run(element, {
    rules: {
      'color-contrast': { enabled: true },
      'keyboard-navigation': { enabled: true },
      'aria-labels': { enabled: true }
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
```

### 6-7. Component Usage Tracker

```typescript
// utils/usageTracker.ts
export function trackComponentUsage(componentName: string): UsageInfo[] {
  // 코드베이스 스캔 (grep 또는 AST 분석)
  const files = findFilesUsingComponent(componentName)
  
  return files.map(file => ({
    path: file.path,
    count: file.occurrences,
    preview: extractCodePreview(file, componentName),
    link: generateGitHubLink(file.path)
  }))
}
```

### 6-8. Design Smell Detector

```typescript
// utils/designSmellDetector.ts
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

### 6-9. 라우팅

```typescript
// app/routes/AppRoutes.tsx에 추가
<Route path="/design-system" element={<DesignSystemPage />}>
  <Route index element={<Navigate to="foundations/colors" />} />
  <Route path="foundations/:section" element={<FoundationsSection />} />
  <Route path="components/:category" element={<ComponentCategory />} />
  <Route path="components/:category/:name" element={<ComponentDetail />} />
  <Route path="patterns/:pattern" element={<PatternGuide />} />
  <Route path="resources/:resource" element={<ResourcePage />} />
</Route>
```

---

## 7. DX 보완 포인트 (Top 10)

### 7-1. Zero Friction 강화: Project-aware Code Generation

**문제**: 코드 복사는 되지만 "내 프로젝트에 바로 맞는지"는 불확실

**해결**:
- [Framework] React / Next / Vite 선택
- [Style] Tailwind / CSS Module / Vanilla CSS 선택
- [Import] alias(@/shared/ui) / relative 선택

→ 선택 즉시 코드가 바뀜

**DX 임팩트**: "이거 우리 프로젝트에서는 어떻게 써?" 질문 제거

### 7-2. Props Playground URL 상태 공유 (P0급)

**기능**: `/design-system/button?variant=primary&size=lg&loading=true`

**효과**:
- 디자이너 ↔ 개발자 ↔ 리뷰어 간 말 설명 제거
- Slack / PR / Jira에 URL 하나로 커뮤니케이션 종료

### 7-3. Props → 타입 → 문서 단방향 자동화

**강제 규칙**: PropsTable, Playground, Docs는 TS 타입에서만 생성 (수동 작성 금지)

**구현**: ts-morph 기반 propsExtractor 강화, JSDoc → Props 설명 자동 반영

**DX 임팩트**: 문서 최신성 100% 보장, "문서 틀렸어요" 이슈 제거

### 7-4. "Why"가 보이는 Usage Examples

**보완**: 각 예제에 `intent` 필드 추가 (사용 이유)

**UI**: What (코드) + Why (사용 이유) + Anti-pattern (이렇게 쓰지 마세요)

### 7-5. Design Token Single Source of Truth (SSOT)

**구조**: `tokens/colors.json` → CSS Variables / Tailwind config / Figma Tokens / Design System UI 자동 파이프라인

**DX 임팩트**: 디자이너/개발자 불일치 제거, "이 색 지금 뭐가 기준이죠?" 질문 종결

### 7-6. A11y Score를 "신뢰 가능한 지표"로

**보완**: axe-core 실측 기반, 상태별 점수 분리

**DX 임팩트**: 테크리드/리뷰어 신뢰 확보, 접근성 논쟁 제거

### 7-7. Component Contract Stability 표시

**메타**: `stability: 'stable' | 'experimental' | 'deprecated'`

**UI 배지**: Stable (✅ Production Ready), Experimental (⚠️ API 변경 가능), Deprecated (❌ 신규 사용 금지)

**DX 임팩트**: 대규모 조직에서 사고 방지, "이거 써도 되나요?" 질문 제거

### 7-8. Changelog → Migration 자동 연결

**보완**: 버전 변경 시 Visual Diff + Auto-migrate Codemod 제공

**DX 임팩트**: 업그레이드 공포 제거, 레거시 정리 속도 상승

### 7-9. Search를 "컴포넌트 탐색기"로 진화

**기능**: 의도 기반 검색 (`/로딩 버튼`, `/danger action`, `/테이블 페이징`)

**결과**: 컴포넌트 + 패턴 + 예제 동시 노출

**DX 임팩트**: 신입 온보딩 속도 급상승

### 7-10. Design System = 개발 진입 포인트

**목표 상태**:
- 새 기능 개발 시: "일단 디자인 시스템 열어"
- PR 리뷰 시: "이거 Button 가이드 어겼음"
- 온보딩 시: "문서 말고 여기부터 봐"

---

## 8. 추가 DX 집착 레벨 보완 (12개)

### 8-1. "내가 쓰는 컴포넌트만" 자동 추천

**기능**: 실제 코드베이스 분석 기반 추천
- 이 프로젝트에서 자주 쓰는 컴포넌트 (사용 횟수 표시)
- 현재 페이지 컨텍스트 기반 추천 (Form 패턴에서 자주 함께 쓰이는 컴포넌트)

**DX 임팩트**: 탐색 시간 → 거의 0, 신입도 "남들 쓰는 방식" 바로 학습

### 8-2. "이 컴포넌트 어디서 쓰이고 있지?" 역추적

**기능**: Component Detail 페이지에 "Used In" 섹션
- 실제 코드 링크
- 스냅샷 미리보기

**DX 임팩트**: 리팩토링 공포 제거, 삭제/변경 의사결정 빨라짐

### 8-3. Design Smell Detector (DS 전용 린터)

**기능**: 패턴 위반 자동 감지
- danger 액션인데 secondary 사용 중
- loading 상태인데 disabled 미설정

**DX 임팩트**: 코드리뷰에서 말 안 해도 됨, "왜 안 돼요?" 논쟁 제거

### 8-4. Anti-Pattern 갤러리

**기능**: 모든 컴포넌트에 "❌ Don't do this" 섹션

**DX 임팩트**: 주니어 실수 80% 감소, 리뷰 피로 급감

### 8-5. Component Decision Guide (선택 가이드)

**기능**: Button vs IconButton vs LinkButton 같은 선택 가이드

**DX 임팩트**: "이거 뭐 써요?" 질문 제거, 일관성 폭증

### 8-6. Runtime Validation (개발 모드 전용)

**기능**: Dev 환경에서만 동작하는 경고
- `<Button loading onClick={undefined} />` → 경고 표시

**DX 임팩트**: 실수 즉시 인지, QA 이전에 대부분 차단

### 8-7. Visual Diff (버전 간 UI 차이)

**기능**: Changelog에 Before/After 이미지

**DX 임팩트**: 디자이너/기획자 커뮤니케이션 비용 0, "이거 왜 달라졌죠?" 제거

### 8-8. Copy = Context-aware (한 단계 더)

**기능**: 주변 코드 포함 옵션
- [Copy Component]
- [Copy with Form Example]
- [Copy with Validation]

**DX 임팩트**: 붙여넣고 바로 동작, 샘플 코드 찾을 필요 없음

### 8-9. Keyboard-First UX

**전역 단축키**:
- `/` → 컴포넌트 검색
- `Enter` → 첫 번째 컴포넌트 이동
- `P` → Props Playground 포커스
- `C` → 코드 복사

**DX 임팩트**: "마우스 거의 안 씀", 파워유저 만족도 급상승

### 8-10. Design System Health Dashboard

**기능**: Tech Lead 전용 뷰
- Deprecated 컴포넌트 사용: 17곳
- Anti-pattern 위반: 42건
- A11y 평균 점수: 93

**DX 임팩트**: DS가 "관리 대상"이 됨, 방치 안 됨

### 8-11. RFC / ADR 자동 연결

**기능**: Component Detail 하단에 "Related Decisions" 섹션

**DX 임팩트**: "왜 이렇게 설계됐는지" 즉시 이해, 재논쟁 방지

### 8-12. Design System을 "법"으로 만들기

**최종 단계**:
- PR Template에 자동 삽입 체크리스트
- CI에서 DS Rule 위반 시 Warning

**DX 임팩트**: 문화가 됨, 개인 의존성 제거

---

## 9. 구현 로드맵 (Implementation Roadmap)

### Phase 1: Foundation (MVP) - 2주
- [ ] 기본 레이아웃 (Sidebar + Content)
- [ ] 컴포넌트 레지스트리 구조
- [ ] Button, Input, Select 3개 컴포넌트 전시
- [ ] 기본 코드 복사 기능
- [ ] Props Table 자동 생성 (ts-morph 기반)

### Phase 2: Interactivity - 2주
- [ ] Props Playground 구현
- [ ] 실시간 코드 생성
- [ ] URL 상태 공유 (P0)
- [ ] 검색 기능 (기본)

### Phase 3: Foundations - 2주
- [ ] Color Palette 시각화
- [ ] Typography Scale
- [ ] Spacing System
- [ ] Contrast Checker
- [ ] Design Token SSOT 구조

### Phase 4: Polish - 2주
- [ ] Icon Gallery
- [ ] Responsive Preview
- [ ] A11y Score (axe-core 통합)
- [ ] Dark/Light 테마 토글
- [ ] Component Stability 표시

### Phase 5: Advanced - 3주
- [ ] Usage Examples (Why 포함)
- [ ] Anti-Pattern 갤러리
- [ ] Patterns 섹션
- [ ] Component Usage Tracker
- [ ] Visual Diff (Changelog)
- [ ] Migration 가이드 + Codemod

### Phase 6: DX Enhancement - 3주
- [ ] Project-aware Code Generation
- [ ] Component Recommendation (코드베이스 분석)
- [ ] Design Smell Detector
- [ ] Component Decision Guide
- [ ] Runtime Validation
- [ ] Keyboard-First UX
- [ ] Context-aware Copy

### Phase 7: Organization Scale - 2주
- [ ] Design System Health Dashboard
- [ ] RFC/ADR 자동 연결
- [ ] PR Template 통합
- [ ] CI 통합 (DS Rule 검사)

**총 예상 기간**: 16주 (약 4개월)

---

## 10. DX 성숙도 레벨 평가

| 영역 | 현재 | 보완 후 | 목표 달성 |
|------|------|---------|----------|
| 사용성 | L8 | L10 | ✅ |
| 자동화 | L7 | L11 | ✅ |
| 신뢰성 | L7 | L11 | ✅ |
| 조직 확장성 | L6 | L12 | ✅ |
| 온보딩 효율 | L8 | L12 | ✅ |
| 리뷰 피로도 | 높음 | 거의 0 | ✅ |
| 탐색 효율 | 중간 | 최고 | ✅ |

---

## 11. 최종 결론

이 설계는 이미 **"좋은 Design System"**이다.

위 보완을 적용하면 **"조직의 개발 문화를 바꾸는 시스템"**이 된다.

### 핵심 성공 요인

1. **Zero Friction**: 복사 한번에 바로 사용
2. **Self-Documenting**: 코드가 곧 문서 (타입 기반 자동화)
3. **Interactive First**: 만져보며 이해 (Playground)
4. **Trustworthy**: 신뢰 가능한 지표 (axe-core, SSOT)
5. **Organization Scale**: 문화가 됨 (PR Template, CI 통합)

### 다음 단계

1. ✅ 이 제안서 검토 및 승인
2. ✅ Phase 1 시작 (MVP)
3. ✅ 사용자 피드백 수집 및 반영
4. ✅ 점진적 확장 (Phase 2-7)

---

## 12. 참고 자료

### 관련 문서
- [Shared UI Refactoring Proposal](./shared-ui-refactoring.md)
- [Contract Editor UI Enhancement](./contract-editor-ui-enhancement.md)

### 기술 스택
- **Frontend**: React 19, TypeScript 5.7, Vite 7
- **UI Library**: Lucide React (Icons)
- **A11y Testing**: axe-core
- **Type Analysis**: ts-morph
- **Code Generation**: Custom utilities

### 외부 참고
- [Storybook](https://storybook.js.org/) - 컴포넌트 문서화 도구
- [Radix UI](https://www.radix-ui.com/) - 접근성 우선 컴포넌트
- [Chakra UI](https://chakra-ui.com/) - Design System 구조 참고

---

**작성일**: 2026-02-01  
**버전**: 1.0  
**상태**: Execution Ready
