# Design System Catalog 레포지토리 구성

**목적**: FSD 아키텍처를 준수하면서 확장 가능한 레포지토리 구조 정의

**작성일**: 2026-02-01  
**버전**: 1.0

---

## 전체 디렉토리 구조

```
admin-ui/src/
├── features/
│   └── design-system/                    # Design System Catalog Feature
│       ├── index.ts                       # Public API
│       │
│       ├── ui/                            # 페이지 컴포넌트
│       │   ├── DesignSystemPage.tsx       # 메인 페이지
│       │   ├── DesignSystemPage.css
│       │   ├── ComponentDetail.tsx        # 컴포넌트 상세 페이지
│       │   ├── ComponentCategory.tsx      # 카테고리 페이지
│       │   ├── FoundationsSection.tsx     # Foundations 섹션
│       │   ├── PatternGuide.tsx           # 패턴 가이드
│       │   └── ResourcePage.tsx           # 리소스 페이지
│       │
│       ├── components/                     # 하위 컴포넌트
│       │   ├── layout/                     # 레이아웃 컴포넌트
│       │   │   ├── Sidebar.tsx
│       │   │   ├── Sidebar.css
│       │   │   ├── ContentArea.tsx
│       │   │   ├── ContentArea.css
│       │   │   ├── Header.tsx
│       │   │   ├── Header.css
│       │   │   └── index.ts
│       │   │
│       │   ├── showcase/                   # 컴포넌트 전시 관련
│       │   │   ├── ComponentShowcase.tsx  # 전시 컨테이너
│       │   │   ├── LivePreview.tsx        # 라이브 미리보기
│       │   │   ├── PropsPlayground.tsx    # Props Playground
│       │   │   ├── PropsPlayground.css
│       │   │   ├── ControlRenderer.tsx    # Props 컨트롤 렌더러
│       │   │   ├── PropsTable.tsx         # Props 테이블
│       │   │   ├── CodeBlock.tsx          # 코드 블록
│       │   │   ├── CodeBlock.css
│       │   │   ├── UsageExamples.tsx      # 사용 예제
│       │   │   ├── AntiPatterns.tsx       # Anti-pattern 갤러리
│       │   │   ├── ComponentDecision.tsx  # 선택 가이드
│       │   │   ├── UsedIn.tsx             # 사용 위치
│       │   │   └── index.ts
│       │   │
│       │   ├── foundations/                # Foundations 컴포넌트
│       │   │   ├── ColorPalette.tsx       # 컬러 팔레트
│       │   │   ├── ColorPalette.css
│       │   │   ├── TypographyScale.tsx    # 타이포그래피
│       │   │   ├── TypographyScale.css
│       │   │   ├── SpacingScale.tsx       # 간격 시스템
│       │   │   ├── SpacingScale.css
│       │   │   ├── ShadowScale.tsx        # 그림자
│       │   │   ├── ShadowScale.css
│       │   │   ├── MotionPreview.tsx       # 애니메이션
│       │   │   ├── MotionPreview.css
│       │   │   └── index.ts
│       │   │
│       │   ├── resources/                  # 리소스 컴포넌트
│       │   │   ├── IconGallery.tsx         # 아이콘 갤러리
│       │   │   ├── IconGallery.css
│       │   │   ├── Changelog.tsx          # 변경 이력
│       │   │   ├── Changelog.css
│       │   │   ├── Migration.tsx          # 마이그레이션 가이드
│       │   │   └── index.ts
│       │   │
│       │   └── utilities/                  # 유틸리티 컴포넌트
│       │       ├── ContrastChecker.tsx     # 대비 검사기
│       │       ├── ContrastChecker.css
│       │       ├── ResponsiveFrame.tsx    # 반응형 프리뷰
│       │       ├── ResponsiveFrame.css
│       │       ├── A11yScore.tsx          # 접근성 점수
│       │       ├── A11yScore.css
│       │       ├── HealthDashboard.tsx     # Health 대시보드
│       │       ├── HealthDashboard.css
│       │       └── index.ts
│       │
│       ├── hooks/                           # 커스텀 훅
│       │   ├── usePlayground.ts           # Playground 상태 관리
│       │   ├── useClipboard.ts             # 클립보드 복사
│       │   ├── useSearch.ts                # 검색 로직
│       │   ├── useCodeGenerator.ts        # 코드 생성
│       │   ├── useComponentRecommendation.ts # 컴포넌트 추천
│       │   ├── useKeyboardShortcuts.ts    # 키보드 단축키
│       │   ├── useProjectSettings.ts      # 프로젝트 설정
│       │   └── index.ts
│       │
│       ├── data/                            # 데이터 레이어
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
│           ├── contrastCalculator.ts       # 대비 계산
│           ├── a11yTester.ts              # A11y 테스트 (axe-core)
│           ├── usageTracker.ts             # 사용 위치 추적
│           ├── designSmellDetector.ts     # Design Smell 감지
│           ├── componentAnalyzer.ts       # 컴포넌트 분석
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

---

## 세부 구조 설명

### 1. `features/design-system/` - 메인 Feature 모듈

FSD 아키텍처의 `features` 레이어에 속합니다. 완전히 독립적인 모듈로 구성됩니다.

#### 1-1. `ui/` - 페이지 컴포넌트

**역할**: 라우팅에 직접 연결되는 페이지 컴포넌트

**파일 구조**:
```typescript
// ui/DesignSystemPage.tsx
export function DesignSystemPage() {
  return (
    <div className="design-system-page">
      <Sidebar />
      <ContentArea>
        <Outlet /> {/* 중첩 라우팅 */}
      </ContentArea>
    </div>
  )
}

// ui/ComponentDetail.tsx
export function ComponentDetail() {
  const { name } = useParams()
  const component = getComponentByName(name)
  
  return (
    <ComponentShowcase component={component} />
  )
}
```

**규칙**:
- 각 페이지는 단일 책임
- 라우팅 로직만 포함
- 비즈니스 로직은 hooks나 utils로 분리

#### 1-2. `components/` - 하위 컴포넌트

**역할**: 페이지를 구성하는 재사용 가능한 컴포넌트

**구조**:
- `layout/`: 레이아웃 관련 컴포넌트
- `showcase/`: 컴포넌트 전시 관련 컴포넌트
- `foundations/`: Foundations 시각화 컴포넌트
- `resources/`: 리소스 관련 컴포넌트
- `utilities/`: 유틸리티 컴포넌트

**규칙**:
- 각 컴포넌트는 독립적으로 테스트 가능
- Props는 타입 안전하게 정의
- CSS는 CSS Module 또는 별도 CSS 파일

#### 1-3. `hooks/` - 커스텀 훅

**역할**: 상태 관리 및 비즈니스 로직

**주요 훅**:
- `usePlayground`: Props Playground 상태 관리
- `useClipboard`: 클립보드 복사
- `useSearch`: 검색 로직
- `useCodeGenerator`: 코드 생성
- `useComponentRecommendation`: 컴포넌트 추천

**규칙**:
- 각 훅은 단일 책임
- 재사용 가능하도록 설계
- 테스트 가능하도록 순수 함수로 구성

#### 1-4. `data/` - 데이터 레이어

**역할**: 정적 데이터 및 타입 정의

**구조**:
- `types.ts`: 모든 타입 정의 (Phase 0에서 생성)
- `mockData.ts`: Mock 데이터
- `componentRegistry.ts`: 컴포넌트 레지스트리
- `tokens/`: Design Tokens (SSOT)

**규칙**:
- `types.ts`는 Phase 0에서 먼저 생성 (모든 작업의 기반)
- `tokens/`는 SSOT (Single Source of Truth)
- JSON 파일로 관리하여 다른 도구와 공유 가능

#### 1-5. `utils/` - 유틸리티 함수

**역할**: 순수 함수 유틸리티

**주요 유틸리티**:
- `propsExtractor.ts`: ts-morph 기반 Props 추출
- `codeGenerator.ts`: 코드 스니펫 생성
- `a11yTester.ts`: axe-core 통합
- `usageTracker.ts`: 코드베이스 스캔

**규칙**:
- 순수 함수로 작성 (부작용 없음)
- 테스트 가능하도록 설계
- 외부 의존성 최소화

---

## 파일 생성 순서 (Phase별)

### Phase 0: 인터페이스 정의
```
features/design-system/
├── data/
│   └── types.ts                    # 모든 타입 정의
└── data/
    └── mockData.ts                 # Mock 데이터
```

### Phase 1: 독립 유틸리티
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

### Phase 2: 기본 인프라
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

### Phase 3: 컴포넌트 전시
```
features/design-system/
├── ui/
│   └── ComponentDetail.tsx
└── components/
    └── showcase/
        ├── ComponentShowcase.tsx
        └── LivePreview.tsx
```

---

## Import 규칙

### 1. Feature 내부 Import

```typescript
// 같은 레이어 내
import { ComponentMeta } from '../data/types'
import { usePlayground } from '../hooks/usePlayground'

// 하위 레이어에서 상위 레이어
import { ComponentShowcase } from '../components/showcase/ComponentShowcase'
```

### 2. Shared 레이어 Import

```typescript
// Shared UI 컴포넌트 (전시 대상)
import { Button, Input, Select } from '@/shared/ui'

// Shared 유틸리티
import { formatDate } from '@/shared/utils'
```

### 3. 절대 경로 사용

```typescript
// tsconfig.json에 설정된 경로 별칭 사용
import { ComponentMeta } from '@/features/design-system/data/types'
import { Button } from '@/shared/ui'
```

---

## 파일 네이밍 규칙

### 1. 컴포넌트 파일
- PascalCase: `ComponentShowcase.tsx`
- CSS는 같은 이름: `ComponentShowcase.css`

### 2. 훅 파일
- camelCase with `use` prefix: `usePlayground.ts`

### 3. 유틸리티 파일
- camelCase: `propsExtractor.ts`

### 4. 타입 파일
- camelCase: `types.ts`

### 5. 데이터 파일
- camelCase: `componentRegistry.ts`

---

## CSS 관리 전략

### 옵션 1: CSS Module (권장)

```typescript
// ComponentShowcase.tsx
import styles from './ComponentShowcase.css'

export function ComponentShowcase() {
  return <div className={styles.container}>...</div>
}
```

### 옵션 2: 별도 CSS 파일

```typescript
// ComponentShowcase.tsx
import './ComponentShowcase.css'

export function ComponentShowcase() {
  return <div className="component-showcase">...</div>
}
```

**권장**: CSS Module 사용 (스타일 충돌 방지)

---

## 테스트 파일 구조

```
features/design-system/
├── utils/
│   ├── propsExtractor.ts
│   └── propsExtractor.test.ts        # 같은 디렉토리에 테스트 파일
├── hooks/
│   ├── usePlayground.ts
│   └── usePlayground.test.ts
└── components/
    └── showcase/
        ├── ComponentShowcase.tsx
        └── ComponentShowcase.test.tsx
```

**규칙**:
- 테스트 파일은 소스 파일과 같은 디렉토리
- 파일명: `*.test.ts` 또는 `*.test.tsx`

---

## Public API (index.ts)

각 레이어는 `index.ts`를 통해 Public API를 노출합니다.

```typescript
// features/design-system/index.ts
export { DesignSystemPage } from './ui/DesignSystemPage'
export { ComponentDetail } from './ui/ComponentDetail'
export { usePlayground } from './hooks/usePlayground'
export { componentRegistry } from './data/componentRegistry'
export type { ComponentMeta } from './data/types'
```

**규칙**:
- 외부에서 사용할 것만 export
- 내부 구현 세부사항은 export하지 않음

---

## 라우팅 설정

```typescript
// app/routes/AppRoutes.tsx
import { DesignSystemPage } from '@/features/design-system'
import { ComponentDetail } from '@/features/design-system'
import { FoundationsSection } from '@/features/design-system'

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

## Design Tokens SSOT 구조

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

---

## Git 브랜치 전략

### 브랜치 네이밍
```
feature/design-system-phase-0-types
feature/design-system-phase-1-props-extractor
feature/design-system-phase-2-routing
feature/design-system-phase-3-button-showcase
```

### 커밋 메시지
```
[Design System] Phase 0: Add type definitions
[Design System] Phase 1-A: Implement props extractor utility
[Design System] Phase 2-A: Add routing configuration
[Design System] Phase 3-A: Implement Button showcase
```

---

## 의존성 관리

### 외부 의존성
```json
{
  "dependencies": {
    "axe-core": "^4.8.0",           // A11y 테스트
    "ts-morph": "^21.0.0"            // TypeScript 분석
  },
  "devDependencies": {
    "@types/node": "^20.0.0"         // ts-morph 타입
  }
}
```

### 내부 의존성
- `@/shared/ui`: Shared UI 컴포넌트 (전시 대상)
- `@/shared/utils`: 공유 유틸리티

---

## 확장성 고려사항

### 1. 새로운 컴포넌트 추가
1. `shared/ui/`에 컴포넌트 추가
2. `data/componentRegistry.ts`에 메타데이터 추가
3. 자동으로 전시 페이지 생성됨

### 2. 새로운 Foundation 추가
1. `data/tokens/`에 JSON 파일 추가
2. `components/foundations/`에 시각화 컴포넌트 추가
3. `ui/FoundationsSection.tsx`에 라우팅 추가

### 3. 새로운 유틸리티 추가
1. `utils/`에 유틸리티 함수 추가
2. `utils/index.ts`에 export 추가
3. 필요한 곳에서 import하여 사용

---

## 파일 크기 가이드라인

- **컴포넌트 파일**: 최대 300줄
- **훅 파일**: 최대 200줄
- **유틸리티 파일**: 최대 500줄
- **타입 파일**: 제한 없음 (하지만 논리적으로 분리)

**초과 시**:
- 컴포넌트는 하위 컴포넌트로 분리
- 훅은 여러 훅으로 분리
- 유틸리티는 기능별로 파일 분리

---

## 코드 리뷰 체크리스트

각 PR에서 확인할 사항:

- [ ] FSD 아키텍처 준수
- [ ] 타입 안전성 (TypeScript 에러 없음)
- [ ] Import 경로 정확 (절대 경로 사용)
- [ ] 파일 네이밍 규칙 준수
- [ ] CSS 충돌 없음 (CSS Module 사용)
- [ ] 테스트 코드 작성 (필요 시)
- [ ] Public API만 export
- [ ] 린트 통과

---

## 마이그레이션 가이드

기존 코드베이스에 Design System Catalog를 추가할 때:

1. **디렉토리 생성**
   ```bash
   mkdir -p admin-ui/src/features/design-system/{ui,components,hooks,data,utils}
   ```

2. **타입 정의 먼저** (Phase 0)
   ```bash
   touch admin-ui/src/features/design-system/data/types.ts
   ```

3. **라우팅 추가** (Phase 2-A)
   - `app/routes/AppRoutes.tsx`에 라우트 추가

4. **점진적 추가**
   - 각 Phase별로 순차적으로 추가
   - 기존 코드와 충돌하지 않도록 주의

---

**작성일**: 2026-02-01  
**버전**: 1.0  
**상태**: Ready for Implementation
