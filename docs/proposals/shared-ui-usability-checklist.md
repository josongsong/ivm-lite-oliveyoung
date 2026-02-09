# Shared UI 컴포넌트 사용성 체크리스트 평가

## 평가 기준
- ✅ **Pass**: 요구사항 충족
- ⚠️ **Warn**: 부분적으로 충족하거나 개선 필요
- ❌ **Fail**: 요구사항 미충족 (1개라도 있으면 "쓰기 불편한 라이브러리")

---

## 2-1. 발견 가능성 Discoverability

### 2-1-1. 엔트리포인트
- ✅ **Pass** - 단일 진입점 존재 (`shared/ui/index.ts`)
- ✅ **Pass** - Named export 규칙 일관됨 (Default export 없음)
- ⚠️ **Warn** - 컴포넌트 폴더 구조가 평면적 (Button.tsx, Input.tsx 등, Button/index.ts 구조 아님)
- ✅ **Pass** - 컴포넌트 이름과 파일명 1:1 매칭 (Alias 없음)

### 2-1-2. 검색성
- ✅ **Pass** - Props 이름이 업계 표준과 일치 (variant, size, disabled, loading 등)
- ✅ **Pass** - IDE 자동완성에서 의미 있게 노출됨 (타입/주석/엔트리 구성)

**점수: 9/10** (폴더 구조만 개선 필요)

---

## 2-2. API 직관성 API Ergonomics

### 2-2-1. Props 설계
- ✅ **Pass** - 필수/선택 Props가 명확함 (필수 최소화, 선택은 합리적 기본값)
- ⚠️ **Warn** - variant/size 값이 대부분 통일되지만 일부 불일치:
  - size: 대부분 `sm | md | lg` 통일 ✅
  - variant: 컴포넌트별로 다름 (Button: `primary | secondary | outline | ghost | danger | success | link`, Alert: `info | success | warning | error`, Card: `default | elevated | outlined | ghost | glass`)
- ✅ **Pass** - className가 항상 존재함
- ⚠️ **Warn** - style 지원 정책이 일관되지 않음 (일부만 HTMLAttributes 확장)
- ✅ **Pass** - 이벤트 핸들러 네이밍이 표준임 (onChange, onClose 등)
- ⚠️ **Warn** - boolean prop 네이밍이 일관되지 않음:
  - Modal: `isOpen` ✅
  - Select: `isOpen` (내부 상태) vs `disabled` (prop)
  - Switch: `checked` (표준) ✅
  - Tabs: `value` (표준) ✅

### 2-2-2. 제어/비제어 Controlled/Uncontrolled
- ⚠️ **Warn** - 입력형 컴포넌트의 controlled/uncontrolled 정책이 명확하지 않음:
  - **Input**: `value` prop만 있음 (uncontrolled 지원 안 함) ❌
  - **Select**: `value` + `onChange`만 있음 (uncontrolled 지원 안 함) ❌
  - **Switch**: `checked` + `defaultChecked` + `onChange` ✅ (양쪽 지원)
  - **Tabs**: `value` + `onValueChange`만 있음 (uncontrolled 지원 안 함) ❌
- ⚠️ **Warn** - Input/Select/Tabs에 `defaultValue` 없음
- ✅ **Pass** - 상태 소유권이 헷갈리는 prop 없음

### 2-2-3. 오류 방지 Guardrails
- ✅ **Pass** - 조합 불가능한 Props는 타입 레벨에서 막힘
- ✅ **Pass** - 위험한 기본값 없음
- ⚠️ **Warn** - runtime error 메시지가 "어떻게 고칠지"까지 안내하지 않음 (Tabs Context 에러만 있음)

**점수: 6/10** (controlled/uncontrolled, variant 통일성 개선 필요)

---

## 2-3. 타입 품질 Type Quality

- ✅ **Pass** - any/unknown 남용 없음
- ✅ **Pass** - HTMLAttributes 확장이 필요한 곳에만 적용됨
- ✅ **Pass** - 컴포넌트가 실제 DOM element 타입과 ref 타입이 일치함
- ✅ **Pass** - 제네릭(Table<T>)은 타입 추론으로 편하게 사용 가능
- ✅ **Pass** - optional prop의 기본값이 타입/문서와 일치함
- ✅ **Pass** - 이벤트 타입이 정확함

**점수: 10/10** (완벽)

---

## 2-4. 문서 품질 Documentation UX

### 2-4-1. 컴포넌트 단위

**문서화가 있는 컴포넌트 (15개):**
- ✅ Button, Input, Table, Card, Tooltip, Alert, Switch, TextArea, IconButton, InfoRow, Section, Skeleton, EmptyState, ApiError, ErrorBoundary

**문서화가 없는 컴포넌트 (10개 이상):**
- ❌ **Modal** - JSDoc 없음, 예제 없음
- ❌ **Tabs** - JSDoc 없음, 예제 없음
- ❌ **Select** - JSDoc 없음, 예제 없음
- ❌ **Pagination** - JSDoc 없음
- ❌ **Accordion** - JSDoc 없음
- ❌ **Chip** - JSDoc 없음
- ❌ **Label** - JSDoc 없음
- ❌ **Form** - JSDoc 없음 (주석만 있음)
- ❌ **Loading** - JSDoc 없음
- ❌ **StatusBadge** - JSDoc 없음

- ⚠️ **Warn** - 자주 하는 실수(controlled vs uncontrolled, aria 연결)가 문서에 없음
- ❌ **Fail** - Breaking behavior(포커스 트랩, portal, body scroll lock 등)가 문서에 명시되지 않음

### 2-4-2. 패키지 단위
- ❌ **Fail** - README 없음
- ❌ **Fail** - 컴포넌트 카탈로그(목록/분류/링크) 없음
- ❌ **Fail** - Copy-paste 가능한 대표 예제(폼, 모달, 테이블, 빈상태) 없음
- ⚠️ **Warn** - 디자인 토큰/스타일 규칙(ui-* prefix 등)이 설명되지 않음
- ❌ **Fail** - 마이그레이션 가이드(변경 시) 템플릿 없음

**점수: 3/10** (매우 부족)

---

## 2-5. 접근성 A11y Usability

- ✅ **Pass** - 기본 role/aria가 올바름 (Button/Tab/Listbox 등)
- ⚠️ **Warn** - 키보드 네비게이션이 대부분 완전하지만:
  - Select: ✅ 완전함 (ArrowUp/Down, Enter, Escape)
  - Tabs: ⚠️ Arrow 키 네비게이션 없음 (Enter/Space만)
  - Modal: ❌ Tab 키 트랩 없음, Escape만 있음
- ✅ **Pass** - focus visible 스타일이 모든 인터랙티브에 적용됨
- ❌ **Fail** - Modal에 focus trap 없음
- ❌ **Fail** - Modal에 restore focus 없음
- ❌ **Fail** - Modal에 body scroll lock 없음
- ✅ **Pass** - aria-describedby / aria-errormessage 연결 패턴 제공됨 (Input)
- ⚠️ **Warn** - 스크린리더 텍스트(아이콘 버튼 label 등) 강제/가이드가 일부만 있음

**점수: 5/10** (Modal 접근성 개선 필요)

---

## 2-6. Ref/통합성 Integration Friendliness

- ⚠️ **Warn** - forwardRef 지원이 일부 컴포넌트에만 있음:
  - ✅ Button, Input, Select, Card, Tooltip, Alert, Switch, TextArea, IconButton, Label
  - ❌ Modal, Tabs, Pagination, Accordion
- ⚠️ **Warn** - Portal 기반 컴포넌트가 SSR/테스트 환경에서 안전한지 불명확:
  - Tooltip: createPortal 사용 ✅
  - Modal: Portal 사용 안 함 ❌
- ⚠️ **Warn** - Form 라이브러리 통합 패턴이 명확하지 않음:
  - Input: forwardRef 있음 ✅
  - Form 컴포넌트는 있지만 React Hook Form 통합 예제 없음
- ⚠️ **Warn** - 라우터/링크 통합 패턴 없음 (Button asChild, polymorphic 등)
- ✅ **Pass** - 애니메이션/트랜지션이 옵션화되어 있음 (Framer Motion 사용)

**점수: 6/10** (Modal forwardRef, Form 통합 패턴 개선 필요)

---

## 2-7. 스타일/테마 확장 Theme & Styling

- ⚠️ **Warn** - className 합성 규칙이 전부 동일하지 않음 (cn 유틸 없음, 직접 join 사용)
- ⚠️ **Warn** - variant 시스템이 통일되지 않음 (cva 등 사용 안 함, 각자 구현)
- ✅ **Pass** - 디자인 토큰이 외부에서 오버라이드 가능함 (CSS variables 사용)
- ✅ **Pass** - dark mode 대응됨
- ⚠️ **Warn** - high contrast 대응 불명확
- ✅ **Pass** - 임의 색상 하드코딩 최소화됨 (토큰 사용)

**점수: 7/10** (className 합성 유틸, variant 시스템 통일 필요)

---

## 2-8. 품질 보증 Quality & Maintenance

### 2-8-1. 테스트
- ❌ **Fail** - 최소 smoke test 없음 (StatusBadge, Toast만 테스트 있음)
- ❌ **Fail** - a11y 테스트 없음
- ❌ **Fail** - 회귀 위험 큰 컴포넌트(Modal/Tabs/Select) 테스트 없음

### 2-8-2. 안정성
- ❌ **Fail** - semver/변경 정책 문서화 없음
- ❌ **Fail** - deprecate 전략 없음
- ❌ **Fail** - 컴포넌트별 owner/리뷰 룰 없음

**점수: 1/10** (매우 부족)

---

## 종합 평가

### Fail 항목 (즉시 수정 필요)
1. ❌ **README 없음**
2. ❌ **Modal 접근성 (focus trap, scroll lock, portal)**
3. ❌ **Modal forwardRef 없음**
4. ❌ **Modal JSDoc 없음**
5. ❌ **테스트 부족 (smoke test, a11y test)**
6. ❌ **문서화 없는 컴포넌트 다수 (Modal, Tabs, Select 등)**

### Warn 항목 (개선 권장)
1. ⚠️ **controlled/uncontrolled 패턴 불명확 (Input, Select, Tabs)**
2. ⚠️ **variant vocabulary 통일성**
3. ⚠️ **Tabs 키보드 네비게이션 (Arrow 키)**
4. ⚠️ **Form 라이브러리 통합 패턴**
5. ⚠️ **className 합성 유틸 (cn)**
6. ⚠️ **variant 시스템 통일 (cva)**

---

## 판정

**현재 상태: ❌ FAIL**

Fail 항목이 6개 이상 존재하므로, 현재는 **"쓰기 불편한 라이브러리"**로 판정됩니다.

특히:
- README 없음 → 다른 개발자가 시작하기 어려움
- Modal 접근성 부족 → 사용성 문제
- 테스트 부족 → 신뢰성 문제
- 문서화 부족 → 학습 곡선 높음

---

## 최소 합격선 달성을 위한 우선 작업

### Phase 1: Critical Fixes (Fail → Pass)
1. **README 작성** (최우선)
2. **Modal 개선** (forwardRef, 접근성, 문서화)
3. **주요 컴포넌트 문서화** (Tabs, Select, Modal)
4. **최소 smoke test 추가** (Modal, Select, Tabs)

### Phase 2: Important Improvements (Warn → Pass)
5. **controlled/uncontrolled 패턴 명확화**
6. **variant vocabulary 통일**
7. **Tabs 키보드 네비게이션 개선**
8. **Form 통합 패턴 문서화**

### Phase 3: Polish (Warn → Pass)
9. **className 합성 유틸 (cn) 추가**
10. **variant 시스템 통일 (cva 검토)**
