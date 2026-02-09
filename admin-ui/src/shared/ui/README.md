# Shared UI Components

공유 UI 컴포넌트 라이브러리입니다. 프로젝트 전반에서 재사용 가능한 범용 컴포넌트들을 제공합니다.

## 📚 목차

- [설치 및 사용](#설치-및-사용)
- [컴포넌트 목록](#컴포넌트-목록)
- [사용 예제](#사용-예제)
- [디자인 시스템](#디자인-시스템)
- [컨벤션](#컨벤션)

---

## 설치 및 사용

### Import

```tsx
import { Button, Input, Card } from '@/shared/ui'
```

### 기본 사용

```tsx
import { Button, Input, Label } from '@/shared/ui'

function MyForm() {
  const [value, setValue] = useState('')
  
  return (
    <form>
      <Label htmlFor="name" required>이름</Label>
      <Input
        id="name"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="이름을 입력하세요"
      />
      <Button variant="primary" type="submit">
        제출
      </Button>
    </form>
  )
}
```

---

## 컴포넌트 목록

### Actions (액션)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Button` | 기본 버튼 컴포넌트 | [상세 보기](/design-system/components/actions/button) |
| `IconButton` | 아이콘 전용 버튼 | [상세 보기](/design-system/components/actions/iconbutton) |

### Inputs (입력)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Input` | 텍스트 입력 필드 | [상세 보기](/design-system/components/inputs/input) |
| `TextArea` | 멀티라인 텍스트 입력 | [상세 보기](/design-system/components/inputs/textarea) |
| `Select` | 드롭다운 선택 | [상세 보기](/design-system/components/inputs/select) |
| `Switch` | 토글 스위치 | [상세 보기](/design-system/components/inputs/switch) |
| `ToggleGroup` | 토글 그룹 | - |
| `Form` | 폼 컨테이너 | - |
| `FormRow` | 폼 행 | - |
| `FormGroup` | 폼 그룹 | - |
| `FormInput` | 폼용 입력 필드 | - |
| `FormTextArea` | 폼용 텍스트 영역 | - |
| `FileUpload` | 파일 업로드 | - |
| `SchemaSelector` | 스키마 선택기 | - |
| `SearchFilter` | 검색 필터 | - |

### Feedback (피드백)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Loading` | 로딩 인디케이터 | [상세 보기](/design-system/components/feedback/loading) |
| `Modal` | 모달 다이얼로그 | [상세 보기](/design-system/components/feedback/modal) |
| `Alert` | 알림 메시지 | [상세 보기](/design-system/components/feedback/alert) |
| `Banner` | 배너 알림 | - |
| `InlineAlert` | 인라인 알림 | - |
| `Toast` | 토스트 알림 | - |
| `EmptyState` | 빈 상태 표시 | - |
| `NoResults` | 결과 없음 상태 | - |
| `NoData` | 데이터 없음 상태 | - |
| `ErrorState` | 에러 상태 | - |
| `LoadingState` | 로딩 상태 | - |
| `Skeleton` | 로딩 스켈레톤 | - |
| `Tooltip` | 툴팁 | [상세 보기](/design-system/components/feedback/tooltip) |

### Layout (레이아웃)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Card` | 카드 컨테이너 | [상세 보기](/design-system/components/layout/card) |
| `StatsCard` | 통계 카드 | - |
| `StatsGrid` | 통계 그리드 | - |
| `BreakdownItem` | 분해 항목 | - |
| `Section` | 섹션 컨테이너 | [상세 보기](/design-system/components/layout/section) |
| `SectionHeader` | 섹션 헤더 | - |
| `CollapsibleSection` | 접을 수 있는 섹션 | - |
| `GroupPanel` | 그룹 패널 | - |
| `Divider` | 구분선 | - |
| `Accordion` | 아코디언/확장 패널 | [상세 보기](/design-system/components/layout/accordion) |
| `InfoRow` | 정보 행 표시 | - |
| `PanelHeader` | 패널 헤더 | - |
| `ActionCard` | 액션 카드 | - |

### Data Display (데이터 표시)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Table` | 데이터 테이블 | [상세 보기](/design-system/components/data-display/table) |
| `TableHeader` | 테이블 헤더 | - |
| `StatusBadge` | 상태 배지 | [상세 보기](/design-system/components/data-display/statusbadge) |
| `Chip` | 태그/칩 | [상세 보기](/design-system/components/data-display/chip) |
| `ChipGroup` | 칩 그룹 | - |
| `Label` | 라벨 | [상세 보기](/design-system/components/data-display/label) |
| `YamlViewer` | YAML 뷰어 | - |
| `JsonViewer` | JSON 뷰어 | - |
| `DiffViewer` | Diff 뷰어 | - |
| `SearchBar` | 검색 바 | - |
| `LineageGraph` | 계보 그래프 | - |
| `StatCard` | 통계 카드 | - |

### Navigation (네비게이션)

| 컴포넌트 | 설명 | 문서 |
|---------|------|------|
| `Tabs` | 탭 네비게이션 | [상세 보기](/design-system/components/navigation/tabs) |
| `Pagination` | 페이지네이션 | [상세 보기](/design-system/components/navigation/pagination) |

---

## 사용 예제

### Button

```tsx
import { Button } from '@/shared/ui'

// 기본 사용
<Button variant="primary">Click me</Button>

// 로딩 상태
<Button loading={isLoading}>Saving...</Button>

// 아이콘과 함께
<Button icon={<Plus />}>Add Item</Button>
```

### Input with Error

```tsx
import { Input, Label, Alert } from '@/shared/ui'

<Label htmlFor="email" required>이메일</Label>
<Input
  id="email"
  type="email"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  error={errors.email}
/>
{errors.email && (
  <Alert variant="error" size="sm">{errors.email}</Alert>
)}
```

### Modal

```tsx
import { Modal, Button } from '@/shared/ui'

const [isOpen, setIsOpen] = useState(false)

<Modal
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title="Confirm Action"
  footer={
    <>
      <Button variant="ghost" onClick={() => setIsOpen(false)}>
        Cancel
      </Button>
      <Button variant="primary" onClick={handleConfirm}>
        Confirm
      </Button>
    </>
  }
>
  Are you sure you want to proceed?
</Modal>
```

### Tabs

```tsx
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui'

const [activeTab, setActiveTab] = useState('tab1')

<Tabs value={activeTab} onValueChange={setActiveTab}>
  <TabsList>
    <TabsTrigger value="tab1">Tab 1</TabsTrigger>
    <TabsTrigger value="tab2">Tab 2</TabsTrigger>
  </TabsList>
  <TabsContent value="tab1">Content 1</TabsContent>
  <TabsContent value="tab2">Content 2</TabsContent>
</Tabs>
```

#### Segmented (세그먼트/토글 스타일)

```tsx
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui'
import { Database, Layers, Eye } from 'lucide-react'

<Tabs value={listType} onValueChange={setListType}>
  <TabsList variant="segmented" responsive="iconsOnlyMd">
    <TabsTrigger value="rawdata" icon={<Database size={16} />}>RawData</TabsTrigger>
    <TabsTrigger value="slices" icon={<Layers size={16} />}>Slices</TabsTrigger>
    <TabsTrigger value="views" icon={<Eye size={16} />}>Views</TabsTrigger>
  </TabsList>
</Tabs>
```

### Table

```tsx
import { Table } from '@/shared/ui'

<Table
  columns={[
    { key: 'name', label: '이름' },
    { key: 'status', label: '상태' },
  ]}
  data={[
    { name: 'Item 1', status: 'Active' },
    { name: 'Item 2', status: 'Pending' },
  ]}
/>
```

---

## 디자인 시스템

모든 컴포넌트의 상세 문서와 예제는 **디자인 시스템**에서 확인할 수 있습니다:

👉 **[디자인 시스템 보기](/design-system)**

디자인 시스템에서 다음을 확인할 수 있습니다:
- 컴포넌트별 상세 문서
- Props 설명 및 예제
- 사용 패턴 및 베스트 프랙티스
- 라이브 예제 및 코드 생성

---

## 컨벤션

### Props 네이밍

- `variant`: 스타일 변형 (`primary`, `secondary`, `ghost` 등)
- `size`: 크기 (`sm`, `md`, `lg`)
- `disabled`: 비활성화 상태
- `loading`: 로딩 상태
- `error`: 에러 상태 (Input 등)

### forwardRef 지원

대부분의 컴포넌트는 `forwardRef`를 지원합니다:

```tsx
const inputRef = useRef<HTMLInputElement>(null)

<Input ref={inputRef} />
```

### 접근성

모든 컴포넌트는 접근성을 고려하여 설계되었습니다:
- ARIA 속성 자동 적용
- 키보드 네비게이션 지원
- 포커스 관리
- 스크린 리더 지원

#### 주요 컴포넌트별 접근성

- **Button**: `aria-label` 지원, 키보드 활성화
- **IconButton**: `aria-label` 또는 `tooltip` 필수 (아이콘만 있는 버튼)
- **Input**: `aria-invalid`, `aria-describedby` 연결
- **Select**: `role="listbox"`, Arrow 키 네비게이션
- **Tabs**: `role="tablist"`, `role="tab"`, `role="tabpanel"`, Arrow 키 네비게이션
- **Modal**: Focus trap, Body scroll lock, Escape 키로 닫기, Portal 렌더링

#### 접근성 체크리스트

컴포넌트를 사용할 때 다음을 확인하세요:

- ✅ **아이콘 버튼**: `aria-label` 또는 `tooltip` 제공
- ✅ **폼 필드**: `label`과 `htmlFor` 연결
- ✅ **에러 메시지**: `aria-describedby`로 연결
- ✅ **모달**: 열릴 때 포커스 관리 확인
- ✅ **키보드**: 모든 인터랙션이 키보드로 가능한지 확인

### 스타일링

컴포넌트는 CSS Variables를 사용하여 테마를 지원합니다:

```css
/* CSS Variables 사용 */
.ui-button--primary {
  background: var(--accent-cyan);
  color: var(--text-primary);
}
```

---

## 추가 리소스

- [디자인 시스템](/design-system) - 전체 컴포넌트 문서
- [패턴 가이드](/design-system/patterns) - 사용 패턴 및 베스트 프랙티스
- [Foundations](/design-system/foundations) - 디자인 토큰 및 스타일 가이드

---

## 테스트

주요 컴포넌트는 단위 테스트를 포함하고 있습니다:

### 테스트 완료된 컴포넌트
- ✅ Button.test.tsx
- ✅ Input.test.tsx
- ✅ Modal.test.tsx
- ✅ Select.test.tsx
- ✅ Tabs.test.tsx
- ✅ StatusBadge.test.tsx
- ✅ Toast.test.tsx
- ✅ Card.test.tsx
- ✅ Alert.test.tsx
- ✅ Switch.test.tsx
- ✅ Chip.test.tsx
- ✅ formatters.test.ts

### 테스트 실행
```bash
npm test
# 또는
pnpm test
```

### 테스트 커버리지
현재 주요 컴포넌트의 기본 기능에 대한 테스트가 완료되었습니다. 추가 테스트는 지속적으로 보완 중입니다.

---

## 기여하기

새로운 컴포넌트를 추가하거나 기존 컴포넌트를 개선할 때:

1. **JSDoc 주석 작성**: 모든 컴포넌트와 Props에 설명 추가
2. **예제 코드 포함**: 사용 예제를 JSDoc에 포함
3. **forwardRef 지원**: 가능한 경우 forwardRef 구현
4. **접근성 고려**: ARIA 속성 및 키보드 네비게이션 구현
5. **타입 안정성**: TypeScript 타입 정의 완전성 확인
6. **테스트 작성**: 컴포넌트 테스트 파일 추가 (ComponentName.test.tsx)

### 컴포넌트 추가 체크리스트

- [ ] TypeScript 인터페이스 정의
- [ ] JSDoc 주석 및 예제 코드
- [ ] forwardRef 지원 (가능한 경우)
- [ ] 접근성 (ARIA 속성, 키보드 네비게이션)
- [ ] CSS 파일 및 스타일링
- [ ] index.ts에 export 추가
- [ ] 단위 테스트 작성
- [ ] 디자인 시스템에 등록

---

## 품질 지표

현재 shared/ui 컴포넌트 라이브러리의 품질 점수:

| 항목 | 점수 | 상태 |
|------|------|------|
| 타입 안정성 | 9/10 | ✅ 매우 우수 |
| Props 명확성 | 8/10 | ✅ 우수 |
| 문서화 | 10/10 | ✅ 완벽 |
| 접근성 | 8/10 | ✅ 우수 |
| forwardRef | 9/10 | ✅ 매우 우수 |
| 확장 가능성 | 9/10 | ✅ 매우 우수 |
| 에러 처리 | 7/10 | ⚠️ 개선 중 |
| 테스트 | 6/10 | ⚠️ 개선 중 |
| 일관성 | 8/10 | ✅ 우수 |
| 사용 가이드 | 9/10 | ✅ 매우 우수 |

**종합 점수: 8.3/10** (SOTA급 수준)

자세한 품질 평가는 [품질 평가 보고서](../../../docs/proposals/shared-ui-quality-assessment.md)를 참고하세요.

---

**마지막 업데이트**: 2026-02-01
