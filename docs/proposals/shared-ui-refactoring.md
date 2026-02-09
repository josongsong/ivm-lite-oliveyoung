# RFC: Shared UI Component 리팩토링 제안서

> **Status**: ✅ **Implemented** (Phase 4 완료)
> **Author**: Claude Code
> **Created**: 2026-01-31
> **Priority**: P0 (필수 개선)
> **Type**: UI 레이어 SSOT 선언

---

## 선언

> **본 RFC 이후 신규 UI는 `shared/ui` 외 구현을 금지함.**
> **이 RFC는 UI 레이어의 Single Source of Truth(SSOT) 선언임.**

---

## 마이그레이션 현황 (2026-01-31 업데이트)

### 완료된 작업

| 항목 | Before | After | 상태 |
|------|--------|-------|------|
| 네이티브 `<button>` 사용 | 99개 | **49개** | ✅ 50개 마이그레이션 |
| Outbox 테이블 중복 | 4개 (289줄) | **1개 (Table 컴포넌트)** | ✅ 완료 |
| Table 컴포넌트 | 없음 | **신규 생성** | ✅ 완료 |
| IconButton 컴포넌트 | 없음 | **신규 생성** | ✅ 완료 |
| Tabs 컴포넌트 | 없음 | **신규 생성** | ✅ Phase 3 |
| Chip 컴포넌트 | 없음 | **신규 생성** | ✅ Phase 3 |
| Pagination 컴포넌트 | features 내 | **shared/ui로 승격** | ✅ Phase 4 |
| Select 컴포넌트 | 없음 | **신규 생성** | ✅ Phase 4 |
| Accordion 컴포넌트 | 없음 | **신규 생성** | ✅ Phase 4 |
| Modal 컴포넌트 개선 | 스타일 분산 | **SSOT 통합** | ✅ 완료 |
| ESLint 규칙 | 없음 | **warn 레벨 적용** | ✅ 완료 |
| 중복 CSS 정리 | @keyframes spin 4곳 | **Loading.css로 통합** | ✅ 완료 |

### 마이그레이션된 파일

**Phase 2 완료:**
- ✅ Outbox 4개 테이블 → Table + IconButton
- ✅ Outbox.tsx → Button 컴포넌트
- ✅ WebhooksPage.tsx → Button, IconButton, Modal
- ✅ Contracts.tsx, ContractDetail.tsx → IconButton, Button
- ✅ Alerts.tsx → Button 컴포넌트
- ✅ Backfill.tsx → Button, Modal 컴포넌트
- ✅ Explorer 컴포넌트들 → Button, IconButton
- ✅ OutboxDetailModal → IconButton
- ✅ EntityFlowSearch → Button
- ✅ PreviewPanel → Button
- ✅ TraceFilters → Button
- ✅ Layout.tsx → Button
- ✅ Pagination.tsx → IconButton

**Phase 3 완료:**
- ✅ Tabs 컴포넌트 신규 생성 (TabsList, TabsTrigger, TabsContent)
- ✅ Chip 컴포넌트 신규 생성 (Chip, ChipGroup)
- ✅ Outbox.tsx → Tabs 마이그레이션
- ✅ Alerts.tsx → Tabs 마이그레이션
- ✅ DataExplorer.tsx → Tabs, Button, IconButton 마이그레이션
- ✅ Contracts.tsx → Tabs, IconButton 마이그레이션
- ✅ PlaygroundPage.tsx → Tabs, Button, IconButton 마이그레이션
- ✅ PreviewPanel.tsx → Tabs 마이그레이션
- ✅ ContractEditorPage.tsx → Tabs 마이그레이션
- ✅ TraceFilters.tsx → Chip 마이그레이션
- ✅ SliceList.tsx → Chip 마이그레이션
- ✅ SchemaFieldsSection.tsx → Button 마이그레이션
- ✅ ContractGraph.tsx → Button 마이그레이션

**Phase 4 완료:**
- ✅ Pagination 컴포넌트 shared/ui로 승격 (기존 features 삭제)
- ✅ Select 컴포넌트 신규 생성 (dropdown 지원)
- ✅ Accordion 컴포넌트 신규 생성 (AccordionItem, AccordionTrigger, AccordionContent)
- ✅ ViewPreview.tsx → Select 마이그레이션
- ✅ Alerts.tsx → Accordion 마이그레이션

### 남은 작업 (49개 네이티브 button)

대부분 **특수 UI 패턴**으로 Button 컴포넌트 적용이 어려움:
- 자동완성 제안 (suggestion-item)
- motion.button (framer-motion 래퍼)
- 커스텀 인터랙션 버튼 (slice-item, data-cell 등)
- 에디터 내부 컨트롤 (복잡한 상태 관리)

**권장 사항**: 위 패턴들은 Autocomplete, MotionButton 컴포넌트로 추가 개발 검토 또는 현재 상태 유지.

---

## 1. 현황 분석

### 1.1 통계 요약

| 항목 | 수치 |
|------|------|
| 공용 컴포넌트 (shared/ui) | 14개 |
| features 내 `<button>` 사용 | **99개** (28개 파일) |
| shared `<Button>` import | **1개** (RawDataEditor.tsx만) |
| CSS 파일 총 개수 | 20+ |
| 중복 CSS 클래스 정의 | 15+ |

### 1.2 심각도별 문제 분류

| 심각도 | 문제 | 영향 |
|--------|------|------|
| **P0** | Button 컴포넌트 거의 미사용 (99개 중 1개만 shared) | 일관성 없음, 스타일 충돌 |
| **P0** | 테이블 컴포넌트 4개 중복 (Outbox) | 289줄 → 100줄 가능 |
| **P0** | Modal.css 없음 (스타일 3곳 분산) | 유지보수 불가 |
| **P1** | `@keyframes spin` 4곳 중복 정의 | 번들 크기 증가 |
| **P1** | DataTable vs ExplorerTable 이중화 | 혼란, 기술 부채 |
| **P2** | 로딩 컴포넌트 크기 옵션 없음 | 인라인 구현 유발 |

---

## 2. 중복/분산 상세 분석

### 2.1 Button 미활용 문제 (가장 심각)

**현재 상황**:
```
shared/ui/Button.tsx → 1곳에서만 사용
나머지 98개 <button> → 인라인 className으로 구현
```

**인라인 버튼 예시** (중복 패턴):
```tsx
// WebhooksPage.tsx
<button className="btn-primary" onClick={handleCreate}>

// Outbox 테이블들
<button className="btn-icon retry" onClick={...}>
<button className="btn-icon replay" onClick={...}>
<button className="btn-icon btn-process" onClick={...}>
```

**CSS 정의 위치 (분산)**:
| 파일 | 클래스 |
|------|--------|
| `shared/ui/Button.css` | `.ui-button--*` |
| `app/styles/App.css` | `.btn`, `.btn-primary`, `.btn-secondary` |
| `outbox/ui/Outbox.css` | `.btn-sm`, `.btn-icon`, `.btn-ghost` |

### 2.2 테이블 컴포넌트 중복

**Outbox 4개 테이블** (거의 동일한 구조):
```
RecentTable.tsx  (79줄) - Process 버튼
FailedTable.tsx  (77줄) - Retry 버튼
DlqTable.tsx     (73줄) - Replay 버튼
StaleTable.tsx   (60줄) - Alert 정보
────────────────────────
총 289줄 → 제네릭 통합 시 ~100줄
```

**ExplorerTable vs DataTable**:
```
ExplorerTable.tsx (105줄) - TanStack Table 사용, 제네릭 지원
DataTable.tsx     (206줄) - 수동 구현, 조건부 렌더링 복잡
```

### 2.3 Modal CSS 분산

**Modal.tsx**: 컴포넌트만 존재, CSS 없음!

**스타일 정의 위치**:
```css
/* Outbox.css (184-250줄) - 가장 완전 */
.modal-overlay { ... }
.modal-content { ... }
.modal-header { ... }
.modal-body { ... }

/* WebhooksPage.css - 추가 스타일 */
/* Backfill.css - 또 다른 버전 */
```

### 2.4 CSS 키프레임 중복

**`@keyframes spin`** 정의 위치:
1. `Button.css:140-153`
2. `App.css:274-278`
3. `Outbox.css:315-322`
4. `Outbox.css:342-344` (같은 파일 내 재정의!)

---

## 3. 강제화 정책 (MANDATORY)

### 3.1 ESLint 규칙 추가

**Phase 1 완료 후 즉시 적용:**

```javascript
// eslint.config.js에 추가
{
  files: ['src/**/*.{ts,tsx}'],
  rules: {
    // Native <button> 사용 금지
    'no-restricted-syntax': [
      'error',
      {
        selector: "JSXOpeningElement[name.name='button']",
        message: '❌ Native <button> 금지. @/shared/ui의 Button 또는 IconButton을 사용하세요.',
      },
    ],
  },
},

// shared/ui 내부는 예외 (컴포넌트 구현용)
{
  files: ['src/shared/ui/**/*.tsx'],
  rules: {
    'no-restricted-syntax': 'off',
  },
},
```

### 3.2 PR 가드레일

**CI 파이프라인에 추가:**

```yaml
# .github/workflows/ui-guard.yml
name: UI Component Guard

on: [pull_request]

jobs:
  check-native-elements:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Check for native button usage
        run: |
          # shared/ui 제외하고 <button 사용 검사
          VIOLATIONS=$(grep -rn "<button" --include="*.tsx" src/features src/widgets src/app || true)
          if [ -n "$VIOLATIONS" ]; then
            echo "❌ Native <button> 사용 감지:"
            echo "$VIOLATIONS"
            echo ""
            echo "→ @/shared/ui의 Button 또는 IconButton을 사용하세요."
            exit 1
          fi

      - name: Check for inline modal styles
        run: |
          VIOLATIONS=$(grep -rn "\.modal-" --include="*.css" src/features || true)
          if [ -n "$VIOLATIONS" ]; then
            echo "❌ Feature 내 modal 스타일 정의 감지:"
            echo "$VIOLATIONS"
            echo ""
            echo "→ shared/ui/Modal.css를 사용하세요."
            exit 1
          fi
```

### 3.3 Deprecated 마킹

**마이그레이션 기간 중 기존 코드:**

```tsx
// DataTable.tsx 상단에 추가
/**
 * @deprecated ExplorerTable을 사용하세요.
 * 마이그레이션 가이드: docs/proposals/shared-ui-refactoring.md#migration-datatable
 */
```

---

## 4. 리스크 및 대응 (Risks & Mitigation)

### 4.1 이벤트 타입 차이

| 리스크 | 대응 |
|--------|------|
| 기존 `onClick` 시그니처 불일치 | Button이 `ButtonHTMLAttributes` 상속하므로 호환 |
| form submit 이벤트 | `type="submit"` prop 지원 확인 |

### 4.2 disabled/loading 중복 처리

| 리스크 | 대응 |
|--------|------|
| `disabled && loading` 중복 로직 | Button 내부에서 `disabled={disabled \|\| loading}` 처리 |
| 기존 코드 수정 시 로직 누락 | Codemod에서 자동 변환 |

### 4.3 CSS Specificity 역전

| 리스크 | 대응 |
|--------|------|
| App.css → CSS Module 전환 중 스타일 깨짐 | Phase별 순차 전환, 각 Phase 후 UI 테스트 |
| 전역 `.btn-*` vs 모듈 `.button` 충돌 | 전역 클래스는 globals.css로 이동, 컴포넌트용은 module.css로 분리 |

### 4.4 번들 크기 영향

| 리스크 | 대응 |
|--------|------|
| 공용 컴포넌트 증가로 초기 번들 증가 | Tree-shaking 확인, 개별 export 유지 |

---

## 5. Codemod 전략

### 5.1 자동 변환 범위

**Phase 2에서 실행:**

```bash
# jscodeshift 기반 codemod
npx jscodeshift -t codemods/button-migration.ts src/features/**/*.tsx
```

**변환 규칙:**

| AS-IS | TO-BE |
|-------|-------|
| `<button className="btn-primary">` | `<Button variant="primary">` |
| `<button className="btn-secondary">` | `<Button variant="secondary">` |
| `<button className="btn-danger">` | `<Button variant="danger">` |
| `<button className="btn-ghost">` | `<Button variant="ghost">` |
| `<button className="btn-icon">` + 단일 아이콘 | `<IconButton icon={...}>` |

### 5.2 Codemod 구현

```typescript
// codemods/button-migration.ts
import type { API, FileInfo } from 'jscodeshift'

const VARIANT_MAP: Record<string, string> = {
  'btn-primary': 'primary',
  'btn-secondary': 'secondary',
  'btn-danger': 'danger',
  'btn-ghost': 'ghost',
  'btn-outline': 'outline',
}

export default function transformer(file: FileInfo, api: API) {
  const j = api.jscodeshift
  const root = j(file.source)

  // <button className="btn-*"> → <Button variant="*">
  root
    .find(j.JSXElement, { openingElement: { name: { name: 'button' } } })
    .forEach((path) => {
      const classAttr = path.node.openingElement.attributes?.find(
        (attr) => attr.type === 'JSXAttribute' && attr.name.name === 'className'
      )

      if (classAttr?.value?.type === 'StringLiteral') {
        const classes = classAttr.value.value.split(' ')
        const variantClass = classes.find((c) => VARIANT_MAP[c])

        if (variantClass) {
          // className을 variant로 변환
          path.node.openingElement.name.name = 'Button'
          if (path.node.closingElement) {
            path.node.closingElement.name.name = 'Button'
          }

          // className 속성을 variant로 교체
          const newAttrs = path.node.openingElement.attributes?.filter(
            (attr) => !(attr.type === 'JSXAttribute' && attr.name.name === 'className')
          ) || []

          newAttrs.push(
            j.jsxAttribute(
              j.jsxIdentifier('variant'),
              j.stringLiteral(VARIANT_MAP[variantClass])
            )
          )

          path.node.openingElement.attributes = newAttrs
        }
      }
    })

  // import 추가
  const hasButtonImport = root.find(j.ImportDeclaration, {
    source: { value: '@/shared/ui' },
  }).length > 0

  if (!hasButtonImport) {
    const firstImport = root.find(j.ImportDeclaration).at(0)
    if (firstImport.length) {
      firstImport.insertAfter(
        j.importDeclaration(
          [j.importSpecifier(j.identifier('Button'))],
          j.literal('@/shared/ui')
        )
      )
    }
  }

  return root.toSource()
}
```

### 5.3 수동 변환 대상

Codemod로 처리 불가능한 케이스 (TODO 주석 삽입):

```tsx
// 복잡한 조건부 className
<button className={`btn-icon ${isActive ? 'active' : ''}`}>
// → /* TODO: Button 마이그레이션 필요 */

// 인라인 스타일 혼합
<button className="btn-primary" style={{ marginTop: 10 }}>
// → 수동 확인 필요
```

---

## 6. 권장 아키텍처

### 6.1 디렉토리 구조 (현재 구조 유지, 점진적 개선)

```
shared/ui/
├── Button.tsx              # forwardRef 추가
├── Button.css              # 모든 버튼 스타일 통합
├── IconButton.tsx          # NEW
├── IconButton.css          # NEW
├── Modal.tsx
├── Modal.css               # NEW (3곳 CSS 통합)
├── Loading.tsx             # size prop 추가
├── Table.tsx               # NEW: 제네릭 테이블
├── Table.css               # NEW
├── ... (기존 컴포넌트)
└── index.ts                # barrel export
```

> **Note**: 하위 디렉토리 구조 (`primitives/`, `feedback/` 등)는 P2에서 검토.
> 현재는 **flat 구조 유지**하며 기능 추가에 집중.

### 6.2 CSS 통합 전략

**AS-IS** (문제):
```
App.css          → .btn, .btn-primary, .badge-*
Button.css       → .ui-button--*
Outbox.css       → .btn-icon, .btn-sm, .spin
WebhooksPage.css → .btn-icon
```

**TO-BE** (통합):
```
shared/ui/
├── Button.css        # 모든 버튼 스타일 (ui-button--, btn-* 통합)
├── IconButton.css    # 아이콘 버튼 전용
├── Modal.css         # 모든 모달 스타일
└── Loading.css       # spin 애니메이션 포함

app/styles/
└── App.css           # 전역 CSS 변수, 리셋, 레이아웃만
```

### 6.3 컴포넌트 설계 원칙

#### Button API

```tsx
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  icon?: ReactNode
  iconPosition?: 'left' | 'right'
  fullWidth?: boolean           // 테이블/모달 footer용
  asChild?: boolean             // Radix 패턴 (P2)
}

// forwardRef 필수
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(...)
```

#### IconButton API

```tsx
export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: LucideIcon
  loadingIcon?: LucideIcon      // 기본값: Loader2
  loading?: boolean
  variant?: 'default' | 'danger' | 'success'
  size?: 'sm' | 'md' | 'lg'
  tooltip?: string              // title 속성으로 렌더링
}
```

#### Table 컴포넌트 책임 범위

> **Table은 "표현 컴포넌트"임.**
> - 정렬, 필터, pagination은 상위에서 주입
> - TanStack Table은 ExplorerTable 전용으로 유지
> - 단순 데이터 표시는 Table 컴포넌트 사용

```tsx
export interface TableColumn<T> {
  key: keyof T | string
  header: string
  width?: string
  render?: (item: T) => ReactNode
  className?: string
}

export interface TableProps<T> {
  data: T[]
  columns: TableColumn<T>[]
  keyExtractor: (item: T) => string
  loading?: boolean
  emptyMessage?: string
  onRowClick?: (item: T) => void
  rowActions?: (item: T) => ReactNode   // 우측 액션 영역
}
```

#### Loading 컴포넌트 확장

```tsx
export interface LoadingProps {
  size?: 'sm' | 'md' | 'lg' | 'full'  // sm: 인라인, full: 페이지 전체
  text?: string
}
```

---

## 7. 마이그레이션 계획

### Phase 1: 기반 정비 (P0) - 즉시 실행

| 작업 | 파일 | 예상 변경 | 담당 |
|------|------|----------|------|
| Modal.css 생성 | `shared/ui/Modal.css` | 신규 생성 | - |
| Modal.tsx에 import 추가 | `shared/ui/Modal.tsx` | 1줄 | - |
| Button forwardRef 추가 | `shared/ui/Button.tsx` | 15줄 수정 | - |
| IconButton 생성 | `shared/ui/IconButton.tsx` | 신규 (~60줄) | - |
| Loading size prop 추가 | `shared/ui/Loading.tsx` | 20줄 수정 | - |
| Table 컴포넌트 생성 | `shared/ui/Table.tsx` | 신규 (~80줄) | - |
| index.ts export 추가 | `shared/ui/index.ts` | 5줄 | - |

**Phase 1 완료 기준:**
- [ ] 모든 새 컴포넌트 생성 완료
- [ ] 기존 컴포넌트 개선 완료
- [ ] 테스트 통과

### Phase 2: 컴포넌트 마이그레이션 (P1)

| 작업 | 대상 파일 | 예상 변경 |
|------|----------|----------|
| ESLint 규칙 추가 | `eslint.config.js` | 15줄 |
| Codemod 실행 | 28개 파일 | 자동 변환 |
| 수동 마이그레이션 | 잔여 케이스 | TODO 기반 |
| Outbox 테이블 통합 | 4개 → 1개 | -189줄 |
| DataTable deprecated | `DataTable.tsx` | 주석 추가 |
| spin 키프레임 통합 | 4개 파일 | 3개 제거 |
| Feature CSS 정리 | Outbox.css 등 | 중복 제거 |

**Phase 2 완료 기준:**
- [ ] ESLint 규칙 통과 (native button 0개)
- [ ] CI 가드레일 통과
- [ ] Outbox 테이블 1개로 통합

### Phase 3: 품질 강화 (P2)

| 작업 | 산출물 |
|------|--------|
| Storybook 도입 | 모든 컴포넌트 문서화 |
| 접근성(a11y) 강화 | aria-* 속성 추가 |
| 테스트 보완 | Button, Modal, Table 테스트 |
| CSS Modules 전환 검토 | 필요 시 적용 |
| 디렉토리 구조 개선 검토 | primitives/, feedback/ 등 |

---

## 8. 예상 효과

### 8.1 코드 감소

| 항목 | Before | After | 감소량 |
|------|--------|-------|--------|
| Outbox 테이블 | 289줄 | 100줄 | **-189줄** |
| CSS 중복 | ~500줄 | ~200줄 | **-300줄** |
| 인라인 버튼 | 99개 | 0개 | **-99개** |

### 8.2 유지보수성

- **버튼 스타일 변경**: 1개 파일만 수정 (현재 4개)
- **모달 스타일 변경**: 1개 파일만 수정 (현재 3개)
- **테이블 로직 변경**: 1개 컴포넌트만 수정 (현재 4개)

### 8.3 개발 생산성

- **새 기능 개발 시**: 컴포넌트 재사용으로 ~30% 시간 단축
- **디버깅**: 일관된 구조로 문제 위치 파악 용이
- **온보딩**: 문서화된 컴포넌트로 이해도 향상

---

## 9. 결론

현재 shared/ui 구조는 **컴포넌트는 있으나 활용되지 않는 상태**.
특히 Button은 99개 사용 중 1개만 공용 컴포넌트 사용.

**핵심 개선 사항**:
1. **Button 마이그레이션 강제화** - ESLint 규칙 + CI 가드레일
2. **Modal.css 즉시 생성** - 현재 스타일 없음
3. **Outbox 테이블 통합** - 가장 큰 중복 해소
4. **Codemod로 자동 마이그레이션** - 수작업 최소화

**이 RFC는 UI 레이어의 SSOT 선언이며, 이후 신규 UI는 shared/ui 외 구현을 금지함.**

---

## 10. 부록

### A. ESLint 규칙 전체 코드

```javascript
// eslint.config.js에 추가할 전체 설정

// 메인 소스 파일 규칙에 추가
{
  files: ['src/**/*.{ts,tsx}'],
  ignores: ['src/shared/ui/**/*.tsx'],  // shared/ui는 제외
  rules: {
    'no-restricted-syntax': [
      'error',
      {
        selector: "JSXOpeningElement[name.name='button']",
        message: '❌ Native <button> 사용 금지. @/shared/ui의 Button 또는 IconButton을 사용하세요.',
      },
      {
        selector: "JSXOpeningElement[name.name='input'][attributes.length=0]",
        message: '⚠️ Native <input> 대신 @/shared/ui의 Input 사용을 권장합니다.',
      },
    ],
  },
},

// shared/ui 내부는 native 요소 사용 허용
{
  files: ['src/shared/ui/**/*.tsx'],
  rules: {
    'no-restricted-syntax': 'off',
  },
},
```

### B. Modal.css 완전 코드

```css
/* shared/ui/Modal.css */

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.modal-content {
  background: var(--bg-secondary);
  border-radius: 12px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--border-color);
}

.modal-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-body {
  padding: 1.25rem;
  overflow-y: auto;
  max-height: calc(80vh - 60px);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid var(--border-color);
}
```

### C. IconButton 구현 코드

```tsx
// shared/ui/IconButton.tsx
import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Loader2, type LucideIcon } from 'lucide-react'
import './IconButton.css'

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: LucideIcon
  loadingIcon?: LucideIcon
  loading?: boolean
  variant?: 'default' | 'danger' | 'success'
  size?: 'sm' | 'md' | 'lg'
  tooltip?: string
}

const ICON_SIZES = {
  sm: 14,
  md: 16,
  lg: 20,
} as const

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      icon: Icon,
      loadingIcon: LoadingIcon = Loader2,
      loading = false,
      variant = 'default',
      size = 'md',
      tooltip,
      className = '',
      disabled,
      ...props
    },
    ref
  ) => {
    const iconSize = ICON_SIZES[size]

    return (
      <button
        ref={ref}
        type="button"
        className={`icon-button icon-button--${variant} icon-button--${size} ${className}`}
        disabled={disabled || loading}
        title={tooltip}
        aria-label={tooltip}
        {...props}
      >
        {loading ? (
          <LoadingIcon size={iconSize} className="icon-button__spinner" />
        ) : (
          <Icon size={iconSize} />
        )}
      </button>
    )
  }
)

IconButton.displayName = 'IconButton'
```

```css
/* shared/ui/IconButton.css */

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.15s ease;
}

.icon-button:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.icon-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Variants */
.icon-button--danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}

.icon-button--success:hover:not(:disabled) {
  background: rgba(34, 197, 94, 0.1);
  color: var(--color-success);
}

/* Sizes */
.icon-button--sm {
  padding: 0.25rem;
}

.icon-button--md {
  padding: 0.375rem;
}

.icon-button--lg {
  padding: 0.5rem;
}

/* Spinner */
.icon-button__spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
```

### D. Table 컴포넌트 구현 코드

```tsx
// shared/ui/Table.tsx
import { type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { Loading } from './Loading'
import './Table.css'

export interface TableColumn<T> {
  key: keyof T | string
  header: string
  width?: string
  render?: (item: T) => ReactNode
  className?: string
}

export interface TableProps<T> {
  data: T[]
  columns: TableColumn<T>[]
  keyExtractor: (item: T) => string
  loading?: boolean
  emptyMessage?: string
  onRowClick?: (item: T) => void
  rowActions?: (item: T) => ReactNode
}

export function Table<T>({
  data,
  columns,
  keyExtractor,
  loading = false,
  emptyMessage = '데이터가 없습니다',
  onRowClick,
  rowActions,
}: TableProps<T>) {
  if (loading) {
    return (
      <div className="table-loading">
        <Loading size="sm" />
      </div>
    )
  }

  const allColumns = rowActions
    ? [...columns, { key: '__actions__', header: '', width: 'auto' }]
    : columns

  return (
    <div className="table-container">
      <table className="table">
        <thead>
          <tr>
            {allColumns.map((col) => (
              <th
                key={String(col.key)}
                style={col.width ? { width: col.width } : undefined}
                className={col.className}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.length === 0 ? (
            <tr>
              <td colSpan={allColumns.length} className="table-empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((item) => (
              <motion.tr
                key={keyExtractor(item)}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                onClick={onRowClick ? () => onRowClick(item) : undefined}
                className={onRowClick ? 'table-row--clickable' : ''}
              >
                {columns.map((col) => (
                  <td key={String(col.key)} className={col.className}>
                    {col.render
                      ? col.render(item)
                      : String(item[col.key as keyof T] ?? '')}
                  </td>
                ))}
                {rowActions ? (
                  <td className="table-actions">{rowActions(item)}</td>
                ) : null}
              </motion.tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
```

### E. Button forwardRef 수정 코드

```tsx
// shared/ui/Button.tsx
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import './Button.css'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  icon?: ReactNode
  iconPosition?: 'left' | 'right'
  fullWidth?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      variant = 'secondary',
      size = 'md',
      loading = false,
      icon,
      iconPosition = 'left',
      fullWidth = false,
      className = '',
      disabled,
      ...props
    },
    ref
  ) => {
    const classes = [
      'ui-button',
      `ui-button--${variant}`,
      `ui-button--${size}`,
      fullWidth && 'ui-button--full',
      className,
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <button
        ref={ref}
        className={classes}
        disabled={disabled || loading}
        {...props}
      >
        {loading ? (
          <>
            <span className="spinner" />
            <span>{children}</span>
          </>
        ) : (
          <>
            {icon && iconPosition === 'left' ? (
              <span className="button-icon">{icon}</span>
            ) : null}
            {children}
            {icon && iconPosition === 'right' ? (
              <span className="button-icon">{icon}</span>
            ) : null}
          </>
        )}
      </button>
    )
  }
)

Button.displayName = 'Button'
```
