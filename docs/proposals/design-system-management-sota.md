# Design System Component Management - SOTA Best Practices

**작성일**: 2026-02-01  
**버전**: 1.0

---

## 1. 핵심 원칙 (Core Principles)

### 1-1. Single Source of Truth (SSOT)
- **Design Tokens**: Figma → JSON → CSS Variables 자동 동기화
- **Component Specs**: 코드에서 자동 추출 (TypeScript types)
- **Documentation**: 코드 주석 → 문서 자동 생성

### 1-2. Automation First
- 수동 작업 최소화
- CI/CD 파이프라인으로 자동화
- 변경사항 자동 감지 및 업데이트

### 1-3. Living Documentation
- 코드와 문서의 동기화
- Storybook/컴포넌트 카탈로그가 항상 최신 상태
- 버전 관리와 변경 이력 추적

---

## 2. SOTA 관리 도구 스택

### 2-1. 컴포넌트 카탈로그
```
┌─────────────────────────────────────────┐
│  Storybook / Custom Catalog (현재)     │
│  - 컴포넌트 전시                        │
│  - Props 문서화                         │
│  - 인터랙티브 예제                      │
└─────────────────────────────────────────┘
```

**추천 도구**:
- **Storybook**: 가장 널리 사용되는 표준 도구
- **Custom Catalog** (현재 구현): 프로젝트 특화 커스터마이징 가능
- **Radix UI Docs**: 최신 문서화 패턴 참고

### 2-2. Design Token 관리
```
Figma Tokens Plugin
    ↓ (자동 동기화)
GitHub Repository (tokens.json)
    ↓ (CI/CD)
Style Dictionary / Token Transformer
    ↓ (변환)
CSS Variables / Theme Files
    ↓ (배포)
Components (자동 적용)
```

**추천 도구**:
- **Figma Tokens Plugin**: Figma에서 토큰 정의
- **Style Dictionary**: 토큰을 다양한 형식으로 변환
- **Theo**: Airbnb의 토큰 관리 도구

### 2-3. 컴포넌트 상태 관리

**Component Lifecycle**:
```
Draft → Preview Candidate → Preview → Stable → Deprecated
```

**각 단계별 요구사항**:
- **Draft**: 기본 구현만 존재
- **Preview Candidate**: Props 타입 정의 완료
- **Preview**: 문서화 완료, 사용 가능하나 API 변경 가능
- **Stable**: API 안정화, Breaking Change 없음
- **Deprecated**: 대체 컴포넌트 존재, 제거 예정

---

## 3. 자동화 파이프라인

### 3-1. 컴포넌트 등록 자동화

**현재 방식 (수동)**:
```typescript
// ComponentCategory.tsx에 수동 추가
const CATEGORIES = {
  inputs: {
    components: [
      { name: 'Input', description: '...' },
      // 새 컴포넌트 추가 시 수동 작업 필요
    ]
  }
}
```

**SOTA 방식 (자동)**:
```typescript
// 컴포넌트 메타데이터 자동 추출
import { extractComponentMetadata } from '@/shared/ui'

// 모든 컴포넌트 자동 스캔
const components = await extractComponentMetadata('@/shared/ui')

// 카테고리 자동 분류
const categorized = autoCategorize(components)
```

**구현 방법**:
1. **TypeScript AST 파싱**: 컴포넌트 파일에서 메타데이터 추출
2. **JSDoc 주석 파싱**: `@category`, `@description` 태그 활용
3. **파일 구조 기반**: 디렉토리 구조로 카테고리 자동 분류

### 3-2. Props 문서 자동 생성

**현재**: 수동으로 Props 설명 작성

**SOTA**:
```typescript
/**
 * Button Component
 * 
 * @category Actions
 * @description 사용자 인터랙션을 트리거하는 기본 버튼
 * 
 * @example
 * ```tsx
 * <Button variant="primary">Click me</Button>
 * ```
 */
export interface ButtonProps {
  /** 버튼 스타일 변형 */
  variant?: 'primary' | 'secondary' | 'ghost'
  
  /** 버튼 크기 */
  size?: 'sm' | 'md' | 'lg'
  
  /** 로딩 상태 */
  loading?: boolean
}
```

**자동 추출 도구**:
- **react-docgen-typescript**: TypeScript에서 Props 추출
- **ts-morph**: TypeScript AST 조작
- **typedoc**: TypeScript 문서 생성

### 3-3. 예제 코드 자동 생성

**현재**: 미리보기 수동 작성

**SOTA**:
```typescript
// 컴포넌트에서 예제 자동 생성
export const ButtonExamples = generateExamples(Button, {
  variants: ['primary', 'secondary', 'ghost'],
  sizes: ['sm', 'md', 'lg'],
  states: ['default', 'loading', 'disabled']
})
```

---

## 4. 버전 관리 및 변경 추적

### 4-1. Semantic Versioning
```
MAJOR.MINOR.PATCH
  ↓
Breaking Change → MAJOR++
New Feature → MINOR++
Bug Fix → PATCH++
```

### 4-2. Changelog 자동 생성
```markdown
## [1.2.0] - 2026-02-01

### Added
- Button: 새로운 `loading` prop 추가
- Input: `error` prop 지원

### Changed
- Card: padding 기본값 변경 (0.75rem → 1rem)

### Deprecated
- OldButton: Button으로 대체 예정
```

**자동화 도구**:
- **Conventional Commits**: 커밋 메시지에서 자동 추출
- **changesets**: 버전 관리 및 릴리즈 자동화
- **semantic-release**: 완전 자동화된 릴리즈

### 4-3. Breaking Change 감지
```typescript
// API 변경 자동 감지
const breakingChanges = detectBreakingChanges({
  before: previousVersion,
  after: currentVersion
})

if (breakingChanges.length > 0) {
  // MAJOR 버전 자동 증가
  bumpVersion('major')
}
```

---

## 5. 통합 워크플로우

### 5-1. 개발자 워크플로우
```
1. 컴포넌트 개발
   ↓
2. Storybook/Catalog에 자동 등록
   ↓
3. Props 문서 자동 생성
   ↓
4. 예제 코드 자동 생성
   ↓
5. PR 생성 시 자동 리뷰
   ↓
6. 머지 시 자동 배포
```

### 5-2. 디자이너 워크플로우
```
1. Figma에서 디자인 토큰 정의
   ↓
2. 자동 동기화 (Figma → GitHub)
   ↓
3. CSS Variables 자동 생성
   ↓
4. 컴포넌트에 자동 적용
   ↓
5. Storybook/Catalog 자동 업데이트
```

### 5-3. CI/CD 파이프라인
```yaml
# .github/workflows/design-system.yml
name: Design System CI

on:
  push:
    paths:
      - 'admin-ui/src/shared/ui/**'
      - 'tokens/**'

jobs:
  validate:
    - 컴포넌트 타입 체크
    - Props 문서 검증
    - 예제 코드 테스트
    
  build:
    - Storybook 빌드
    - 컴포넌트 카탈로그 빌드
    
  deploy:
    - Design System 사이트 배포
    - Figma 플러그인 업데이트 (선택)
```

---

## 6. 실제 구현 예시

### 6-1. 컴포넌트 메타데이터 자동 추출

```typescript
// tools/extract-component-metadata.ts
import { Project } from 'ts-morph'

export async function extractComponentMetadata(sourceDir: string) {
  const project = new Project()
  const sourceFiles = project.addSourceFilesAtPaths(`${sourceDir}/**/*.tsx`)
  
  const components = []
  
  for (const file of sourceFiles) {
    const exports = file.getExportedDeclarations()
    
    for (const [name, declarations] of exports) {
      const component = {
        name,
        filePath: file.getFilePath(),
        props: extractProps(declarations[0]),
        description: extractDescription(declarations[0]),
        category: extractCategory(declarations[0]),
        examples: extractExamples(declarations[0])
      }
      
      components.push(component)
    }
  }
  
  return components
}

function extractCategory(declaration: any): string {
  // JSDoc에서 @category 태그 추출
  const jsDoc = declaration.getJsDocs()[0]
  const categoryTag = jsDoc?.getTags().find(tag => tag.getTagName() === 'category')
  return categoryTag?.getComment() || 'uncategorized'
}
```

### 6-2. 카테고리 자동 분류

```typescript
// tools/auto-categorize.ts
export function autoCategorize(components: ComponentMetadata[]) {
  const categories = {
    actions: [],
    inputs: [],
    feedback: [],
    layout: [],
    'data-display': [],
    navigation: []
  }
  
  for (const component of components) {
    // 1. JSDoc @category 태그 우선
    if (component.category) {
      categories[component.category]?.push(component)
      continue
    }
    
    // 2. 파일 경로 기반 추론
    const category = inferCategoryFromPath(component.filePath)
    categories[category]?.push(component)
  }
  
  return categories
}

function inferCategoryFromPath(path: string): string {
  if (path.includes('Button') || path.includes('IconButton')) return 'actions'
  if (path.includes('Input') || path.includes('Select') || path.includes('Form')) return 'inputs'
  if (path.includes('Alert') || path.includes('Modal') || path.includes('Toast')) return 'feedback'
  if (path.includes('Card') || path.includes('Section') || path.includes('Layout')) return 'layout'
  if (path.includes('Table') || path.includes('Badge') || path.includes('Chip')) return 'data-display'
  if (path.includes('Tabs') || path.includes('Pagination')) return 'navigation'
  return 'uncategorized'
}
```

### 6-3. Design Token 자동 동기화

```typescript
// tools/sync-design-tokens.ts
import { readFileSync, writeFileSync } from 'fs'
import StyleDictionary from 'style-dictionary'

// Figma에서 export한 tokens.json 읽기
const tokens = JSON.parse(readFileSync('tokens/tokens.json', 'utf-8'))

// Style Dictionary로 CSS Variables 생성
const sd = new StyleDictionary({
  source: ['tokens/**/*.json'],
  platforms: {
    css: {
      transformGroup: 'css',
      buildPath: 'src/styles/',
      files: [{
        destination: 'design-tokens.css',
        format: 'css/variables'
      }]
    }
  }
})

sd.buildAllPlatforms()
```

---

## 7. 추천 도구 및 라이브러리

### 7-1. 컴포넌트 문서화
- **Storybook**: 표준 컴포넌트 카탈로그
- **Docusaurus**: 문서 사이트 구축
- **VitePress**: 빠른 문서 생성

### 7-2. 타입 추출 및 문서 생성
- **react-docgen-typescript**: React 컴포넌트 Props 추출
- **typedoc**: TypeScript 문서 생성
- **ts-morph**: TypeScript AST 조작

### 7-3. Design Token 관리
- **Style Dictionary**: Amazon의 토큰 관리 도구
- **Theo**: Airbnb의 토큰 관리 도구
- **Figma Tokens Plugin**: Figma 통합

### 7-4. 버전 관리
- **changesets**: 버전 관리 및 릴리즈
- **semantic-release**: 자동 버전 관리
- **lerna**: 모노레포 관리

### 7-5. 테스트
- **Chromatic**: Visual Regression Testing
- **axe-core**: 접근성 테스트
- **Playwright**: E2E 테스트

---

## 8. 현재 프로젝트 적용 방안

### 8-1. 단계별 마이그레이션

**Phase 1: 메타데이터 자동화** (1-2주)
- 컴포넌트 메타데이터 자동 추출 도구 구현
- JSDoc 표준화
- 카테고리 자동 분류

**Phase 2: 문서 자동 생성** (2-3주)
- Props 문서 자동 생성
- 예제 코드 자동 생성
- Changelog 자동 생성

**Phase 3: Design Token 통합** (3-4주)
- Figma Tokens 설정
- 자동 동기화 파이프라인 구축
- CSS Variables 자동 생성

**Phase 4: CI/CD 통합** (1-2주)
- 자동 빌드 및 배포
- 변경사항 자동 감지
- 버전 관리 자동화

### 8-2. 즉시 적용 가능한 개선

1. **JSDoc 표준화**
```typescript
/**
 * @category Actions
 * @description 기본 버튼 컴포넌트
 */
export interface ButtonProps {
  /** 버튼 스타일 변형 */
  variant?: 'primary' | 'secondary'
}
```

2. **컴포넌트 등록 자동화 스크립트**
```bash
# 컴포넌트 메타데이터 자동 추출
npm run extract-components

# 카테고리 자동 분류
npm run categorize-components

# 디자인 시스템 페이지 자동 업데이트
npm run update-design-system
```

3. **CI에서 자동 검증**
```yaml
- name: Validate Components
  run: |
    npm run extract-components
    npm run validate-component-registry
```

---

## 9. 참고 자료

- [Storybook Design Systems Tutorial](https://storybook.js.org/tutorials/design-systems-for-developers/)
- [Carbon Design System Component Checklist](https://carbondesignsystem.com/contributing/component-checklist)
- [Design Token Automation](https://matthewrea.com/blog/design-token-automation-from-figma-to-storybook/)
- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)

---

## 10. 결론

SOTA 수준의 디자인 시스템 관리는:

1. **자동화**: 수동 작업 최소화
2. **SSOT**: 단일 소스에서 모든 정보 추출
3. **Living Docs**: 코드와 문서의 동기화
4. **CI/CD**: 변경사항 자동 감지 및 배포
5. **버전 관리**: 체계적인 변경 이력 추적

현재 프로젝트는 **Phase 1 (메타데이터 자동화)**부터 시작하는 것을 권장합니다.
