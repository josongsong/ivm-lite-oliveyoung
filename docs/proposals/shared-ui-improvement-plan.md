# Shared UI 컴포넌트 개선 계획

## 목표
"다른 개발자가 처음 와서도 실수 없이 빠르게 쓰게" 만드는 사용성 달성

## 현재 상태
- **판정: ❌ FAIL** (Fail 항목 6개 이상)
- **종합 점수: 5.1/10**

---

## Phase 1: Critical Fixes (즉시 수정 - Fail → Pass)

### 1-1. README 작성 ⭐⭐⭐ (최우선)

**목표**: 다른 개발자가 처음 와도 바로 시작할 수 있게

**작업 내용**:
```markdown
# Shared UI Components

## 시작하기
- 설치/import 방법
- 기본 사용 예제

## 컴포넌트 카탈로그
- 카테고리별 분류
- 각 컴포넌트 링크

## 대표 예제
- 폼 예제
- 모달 예제
- 테이블 예제
- 빈 상태 예제

## 스타일 가이드
- 디자인 토큰 (CSS variables)
- ui-* prefix 규칙
- 커스터마이징 방법

## 통합 가이드
- React Hook Form 통합
- 라우터 통합
- 테마 커스터마이징
```

**예상 시간**: 2-3시간

---

### 1-2. Modal 컴포넌트 개선 ⭐⭐⭐

**현재 문제점**:
- ❌ forwardRef 없음
- ❌ focus trap 없음
- ❌ body scroll lock 없음
- ❌ portal 사용 안 함
- ❌ JSDoc 없음
- ❌ 예제 코드 없음

**개선 작업**:

#### 1-2-1. forwardRef 추가
```tsx
export const Modal = forwardRef<HTMLDivElement, ModalProps>(...)
```

#### 1-2-2. Portal 사용
```tsx
import { createPortal } from 'react-dom'

// modal-content를 document.body에 렌더링
```

#### 1-2-3. Focus Trap 구현
```tsx
// 모달 열릴 때 첫 번째 포커스 가능 요소에 포커스
// Tab 키로 모달 내부만 순환
// Escape로 닫기
```

#### 1-2-4. Body Scroll Lock
```tsx
// 모달 열릴 때 body overflow: hidden
// 닫을 때 복원
```

#### 1-2-5. JSDoc 및 예제 추가
```tsx
/**
 * Modal Component
 * 
 * 접근성 완비 모달:
 * - Focus trap (Tab 키로 모달 내부만 순환)
 * - Body scroll lock (배경 스크롤 방지)
 * - Portal 렌더링 (z-index 이슈 방지)
 * - Keyboard navigation (Escape로 닫기)
 * 
 * @example
 * ```tsx
 * const [isOpen, setIsOpen] = useState(false)
 * 
 * <Modal
 *   isOpen={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   title="Confirm Action"
 *   footer={
 *     <>
 *       <Button onClick={() => setIsOpen(false)}>Cancel</Button>
 *       <Button variant="primary" onClick={handleConfirm}>Confirm</Button>
 *     </>
 *   }
 * >
 *   Are you sure you want to proceed?
 * </Modal>
 * ```
 */
```

**예상 시간**: 4-5시간

---

### 1-3. 주요 컴포넌트 문서화 ⭐⭐

**대상 컴포넌트**:
1. Tabs
2. Select
3. Pagination
4. Accordion
5. Chip
6. Label
7. Form
8. Loading
9. StatusBadge

**작업 내용**:
각 컴포넌트에 최소:
- 한 줄 요약 JSDoc
- Props 주석
- 1개 이상 예제 코드
- 자주 하는 실수 안내 (있는 경우)

**예상 시간**: 3-4시간

---

### 1-4. 최소 Smoke Test 추가 ⭐⭐

**대상 컴포넌트**:
1. Modal (최우선)
2. Select
3. Tabs
4. Input
5. Button

**작업 내용**:
각 컴포넌트에 최소:
- 렌더링 테스트
- 클릭/인터랙션 테스트
- 키보드 네비게이션 테스트 (해당되는 경우)

**예상 시간**: 4-5시간

---

## Phase 2: Important Improvements (Warn → Pass)

### 2-1. Controlled/Uncontrolled 패턴 명확화 ⭐⭐

**현재 문제점**:
- Input: `value`만 있음 (uncontrolled 지원 안 함)
- Select: `value` + `onChange`만 있음 (uncontrolled 지원 안 함)
- Tabs: `value` + `onValueChange`만 있음 (uncontrolled 지원 안 함)

**개선 작업**:

#### Input 개선
```tsx
export interface InputProps {
  // Controlled
  value?: string
  onChange?: (e: ChangeEvent<HTMLInputElement>) => void
  
  // Uncontrolled
  defaultValue?: string
  
  // 기타 props...
}
```

#### Select 개선
```tsx
export interface SelectProps {
  // Controlled
  value?: string
  onChange?: (value: string) => void
  
  // Uncontrolled
  defaultValue?: string
  
  // 기타 props...
}
```

#### Tabs 개선
```tsx
export interface TabsProps {
  // Controlled
  value?: string
  onValueChange?: (value: string) => void
  
  // Uncontrolled
  defaultValue?: string
  
  // 기타 props...
}
```

**문서화 추가**:
각 컴포넌트에 controlled/uncontrolled 사용 예제 추가

**예상 시간**: 3-4시간

---

### 2-2. Variant Vocabulary 통일 ⭐

**현재 문제점**:
- Button: `primary | secondary | outline | ghost | danger | success | link`
- Alert: `info | success | warning | error`
- Card: `default | elevated | outlined | ghost | glass`

**개선 방안**:
1. **표준 variant 세트 정의**:
   ```tsx
   // 공통 variant 타입
   type CommonVariant = 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'ghost' | 'outline'
   ```

2. **컴포넌트별 variant 매핑**:
   - Button: 기존 유지 (많이 사용 중)
   - Alert: `info` → `default`로 변경 검토
   - Card: 기존 유지 (의미가 다름)

3. **문서화**:
   - 각 컴포넌트의 variant 의미 명시
   - 통일성 가이드라인 작성

**예상 시간**: 2-3시간 (문서화 중심)

---

### 2-3. Tabs 키보드 네비게이션 개선 ⭐

**현재 문제점**:
- Arrow 키 네비게이션 없음
- Enter/Space만 지원

**개선 작업**:
```tsx
// TabsTrigger에 키보드 핸들러 추가
const handleKeyDown = (e: React.KeyboardEvent) => {
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    e.preventDefault()
    // 다음/이전 탭으로 이동
  }
  // Enter/Space는 이미 onClick으로 처리됨
}
```

**예상 시간**: 1-2시간

---

### 2-4. Form 라이브러리 통합 패턴 문서화 ⭐

**작업 내용**:
README에 섹션 추가:
```markdown
## React Hook Form 통합

### Input
\`\`\`tsx
import { useForm } from 'react-hook-form'
import { Input } from '@/shared/ui'

const { register } = useForm()

<Input {...register('name')} />
\`\`\`

### Select
\`\`\`tsx
const { control } = useForm()
<Controller
  control={control}
  name="category"
  render={({ field }) => (
    <Select
      value={field.value}
      onChange={field.onChange}
      options={options}
    />
  )}
/>
\`\`\`
```

**예상 시간**: 1-2시간

---

## Phase 3: Polish (Warn → Pass)

### 3-1. className 합성 유틸 (cn) 추가 ⭐

**작업 내용**:
```tsx
// shared/utils/cn.ts
export function cn(...classes: (string | undefined | null | false)[]): string {
  return classes.filter(Boolean).join(' ')
}

// 사용 예시
className={cn('ui-button', `ui-button--${variant}`, className)}
```

**예상 시간**: 1시간

---

### 3-2. Variant 시스템 통일 (cva 검토) ⭐

**작업 내용**:
- `class-variance-authority` 라이브러리 도입 검토
- 또는 자체 variant 시스템 통일

**예상 시간**: 4-5시간 (도입 시)

---

## 우선순위별 작업 계획

### Week 1: Critical Fixes
1. ✅ README 작성 (2-3시간)
2. ✅ Modal 개선 (4-5시간)
3. ✅ 주요 컴포넌트 문서화 (3-4시간)
4. ✅ 최소 Smoke Test (4-5시간)

**총 예상 시간**: 13-17시간

### Week 2: Important Improvements
5. ✅ Controlled/Uncontrolled 패턴 (3-4시간)
6. ✅ Variant Vocabulary 통일 (2-3시간)
7. ✅ Tabs 키보드 네비게이션 (1-2시간)
8. ✅ Form 통합 패턴 문서화 (1-2시간)

**총 예상 시간**: 7-11시간

### Week 3: Polish
9. ✅ className 합성 유틸 (1시간)
10. ✅ Variant 시스템 통일 검토 (4-5시간)

**총 예상 시간**: 5-6시간

---

## 최종 목표 달성 체크리스트

### 최소 합격선 달성 후:
- ✅ README 존재
- ✅ 모든 컴포넌트에 1개 예제
- ✅ Modal/Select/Tabs 키보드/포커스 완비
- ✅ variant/size/vocabulary 통일
- ✅ forwardRef + controlled/uncontrolled 명확

### SOTA 합격선 달성 후:
- ✅ Storybook (또는 동급) 존재
- ✅ a11y 자동검사 통과
- ✅ 대표 시나리오 샘플 페이지
- ✅ 회귀 큰 컴포넌트 테스트 우선 구축
- ✅ 마이그레이션 가이드/Deprecation 정책 존재

---

## 즉시 시작 가능한 작업

### Top 3 (가장 빠른 효과)
1. **README 작성** - 다른 개발자 온보딩 즉시 개선
2. **Modal 개선** - 가장 많이 사용되는 컴포넌트 중 하나
3. **주요 컴포넌트 문서화** - 사용성 즉시 개선

이 작업들만 완료해도 **Fail → Warn** 수준으로 개선됩니다.
