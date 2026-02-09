# 컴포넌트 위치 판단 테이블

**작성일**: 2026-02-01  
**버전**: 1.0

---

## 판단 기준

| 위치 | 조건 | 예시 |
|------|------|------|
| **`shared/ui`** | ✅ 여러 feature에서 사용 가능한 기본 UI 컴포넌트<br>✅ 도메인 로직 없는 순수 UI<br>✅ 다른 프로젝트에서도 바로 사용 가능 | Button, Input, Modal, Table, Card |
| **`shared/ui/recipes`** | ✅ 여러 기본 컴포넌트를 조합한 복합 패턴<br>✅ 특정 도메인에 특화되었지만 여러 곳에서 사용 가능<br>✅ 비즈니스 로직은 없지만 특정 용도에 최적화 | JsonViewer, DiffViewer, YamlViewer |
| **`features/*/components`** | ✅ 특정 feature의 도메인 로직을 포함<br>✅ 해당 feature의 API/타입에 의존<br>✅ 주로 해당 feature 내에서만 사용 | RawDataPanel, SliceList, TraceList |
| **단독 사용** | ✅ 해당 feature의 내부에서만 사용<br>✅ export하지 않는 내부 구현 | 각 feature의 내부 헬퍼 컴포넌트 |

---

## 전체 컴포넌트 위치 판단 테이블

### 현재 `shared/ui`에 있는 컴포넌트들

| 컴포넌트 | 현재 위치 | 판단 | 이유 | 상태 |
|---------|----------|------|------|------|
| **Core UI** |
| Button | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| IconButton | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| Input | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| TextArea | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| Label | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| Select | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| Switch | `shared/ui` | ✅ **유지** | 완전 범용, 도메인 로직 없음 | ✅ |
| **Layout** |
| Card, StatsCard, StatsGrid | `shared/ui` | ✅ **유지** | 범용 카드 패턴 | ✅ |
| Section, SectionHeader | `shared/ui` | ✅ **유지** | 범용 섹션 패턴 | ✅ |
| TableHeader | `shared/ui` | ✅ **유지** | 범용 테이블 헤더 패턴 | ✅ |
| PanelHeader | `shared/ui` | ✅ **유지** | 범용 패널 헤더 패턴 | ✅ |
| ActionCard | `shared/ui` | ✅ **유지** | 범용 액션 카드 패턴 | ✅ |
| StatCard | `shared/ui` | ✅ **유지** | 범용 통계 카드 패턴 | ✅ |
| **Form** |
| Form, FormRow, FormGroup | `shared/ui` | ✅ **유지** | 범용 폼 패턴 | ✅ |
| FormInput, FormTextArea | `shared/ui` | ✅ **유지** | 범용 폼 입력 패턴 | ✅ |
| FileUpload | `shared/ui` | ✅ **유지** | 범용 파일 업로드 | ✅ |
| SchemaSelector | `shared/ui` | ✅ **유지** | 범용 스키마 선택 (도메인 특화 없음) | ✅ |
| **Navigation** |
| Tabs | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Pagination | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| **Data Display** |
| Table | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| StatusBadge | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Chip | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Skeleton | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| **Feedback** |
| Modal | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Loading | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Alert | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| EmptyState | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Tooltip | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| Toast | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| **Search** |
| SearchFilter | `shared/ui` | ✅ **유지** | 범용 검색 필터 패턴 | ✅ |
| **Utility** |
| PageHeader | `shared/ui` | ✅ **유지** | 범용 페이지 헤더 | ✅ |
| ErrorBoundary | `shared/ui` | ✅ **유지** | 완전 범용 | ✅ |
| ApiError | `shared/ui` | ✅ **유지** | 범용 API 에러 표시 | ✅ |
| EnvironmentSelector | `shared/ui` | ⚠️ **검토 필요** | 프로젝트 특정 store 의존 | ⚠️ |

---

### 현재 `shared/ui/recipes`에 있는 컴포넌트들

| 컴포넌트 | 현재 위치 | 판단 | 이유 | 상태 |
|---------|----------|------|------|------|
| JsonViewer | `shared/ui/recipes` | ✅ **유지** | JSON 표시 복합 패턴, 여러 feature에서 사용 | ✅ |
| DiffViewer | `shared/ui/recipes` | ✅ **유지** | 버전 비교 복합 패턴, 여러 feature에서 사용 | ✅ |
| YamlViewer | `shared/ui` | ⚠️ **이동 검토** | `recipes`로 이동 고려 (복합 패턴) | ⚠️ |

---

### 이동이 필요한 컴포넌트들

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 | 작업 필요 | 우선순위 |
|---------|----------|----------|------|----------|---------|
| **Explorer Components** |
| SearchBar | `features/explorer/components/` | **`shared/ui/recipes`** | 범용 검색 바 패턴, 다른 feature에서도 사용 가능<br>⚠️ explorerApi 의존성 제거 필요 (props로 받기) | 🔄 리팩토링 + 이동 | 🔴 높음 |
| DataTable | `features/explorer/components/` | **`shared/ui/recipes`** | 범용 데이터 테이블 패턴 (검색, 필터, 페이지네이션)<br>⚠️ explorerApi 의존성 제거 필요 (props로 받기) | 🔄 리팩토링 + 이동 | 🔴 높음 |
| RawDataPanel | `features/explorer/components/` | **`features/explorer/components/`** ✅ | RawData 도메인 특화 (explorerApi 의존) | - | - |
| RawDataEditor | `features/explorer/components/` | **`features/explorer/components/`** ✅ | RawData 도메인 특화 (explorerApi 의존) | - | - |
| SliceList | `features/explorer/components/` | **`features/explorer/components/`** ✅ | Slice 도메인 특화 (explorerApi 의존) | - | - |
| ViewPreview | `features/explorer/components/` | **`features/explorer/components/`** ✅ | View 도메인 특화 (explorerApi 의존) | - | - |
| LineageGraph | `features/explorer/components/` | **`features/explorer/components/`** ✅ | Lineage 도메인 특화 (explorerApi 의존) | - | - |
| **Playground Components** |
| YamlEditor | `features/playground/components/` | **`shared/ui/recipes`** | 범용 YAML 에디터 패턴, Contract Editor 등에서도 사용 가능 | 🔄 이동 | 🔴 높음 |
| SampleInput | `features/playground/components/` | **`features/playground/components/`** ✅ | Playground 특화 | - | - |
| PreviewPanel | `features/playground/components/` | **`features/playground/components/`** ✅ | Playground 특화 | - | - |
| **Outbox Components** |
| RecentTable | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 (outbox 타입 의존) | - | - |
| FailedTable | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 | - | - |
| DlqTable | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 | - | - |
| StaleTable | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 | - | - |
| **Traces Components** |
| TraceList | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 (tracesApi 의존) | - | - |
| TraceFilters | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 | - | - |
| WaterfallTimeline | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 | - | - |
| SpanDetails | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 | - | - |
| **Workflow Components** |
| RawDataNode, SliceNode 등 | `features/workflow/components/` | **`features/workflow/components/`** ✅ | Workflow 도메인 특화 (react-flow 의존) | - | - |
| WorkflowCanvas | `features/workflow/ui/` | **`features/workflow/ui/`** ✅ | 페이지 레벨 컴포넌트 | - | - |
| WorkflowDetailPanel | `features/workflow/ui/` | **`features/workflow/ui/`** ✅ | 페이지 레벨 컴포넌트 | - | - |
| **Contracts Components** |
| ContractDescription | `features/contracts/components/` | **`features/contracts/components/`** ✅ | Contract 도메인 특화 | - | - |
| ContractGraph | `features/contracts/components/` | **`features/contracts/components/`** ✅ | Contract 도메인 특화 | - | - |
| **Webhooks Components** |
| WebhookCard | `features/webhooks/components/` | **`features/webhooks/components/`** ✅ | Webhook 도메인 특화 | - | - |
| WebhookForm | `features/webhooks/components/` | **`features/webhooks/components/`** ✅ | Webhook 도메인 특화 | - | - |
| DeliveriesTable | `features/webhooks/components/` | **`features/webhooks/components/`** ✅ | Webhook 도메인 특화 | - | - |

---

## 이동 작업 상세

### 🔴 높은 우선순위 (즉시 작업)

#### 1. SearchBar → `shared/ui/recipes`

**현재**: `features/explorer/components/SearchBar.tsx`  
**이동**: `shared/ui/recipes/SearchBar.tsx`

**작업 내용**:
- [ ] `explorerApi` 의존성 제거 → `onAutocomplete` prop으로 받기
- [ ] 파일 이동 (`.tsx`, `.css`)
- [ ] `recipes/index.ts`에 export 추가
- [ ] `features/explorer/components/index.ts`에서 제거
- [ ] `features/explorer/components/SearchBar.tsx`를 re-export로 변경 (backward compatibility)
- [ ] 사용하는 곳에서 import 경로 업데이트

**리팩토링 예시**:
```tsx
// Before
const { data: autocompleteData } = useQuery({
  queryKey: ['explorer-autocomplete', query, currentTenant],
  queryFn: () => explorerApi.getAutocomplete(query, currentTenant),
})

// After
interface SearchBarProps {
  onSearch: (tenant: string, entityId: string, version?: number | 'latest') => void
  onAutocomplete?: (query: string, tenant: string) => Promise<SearchSuggestion[]>
  defaultTenant?: string
}
```

---

#### 2. DataTable → `shared/ui/recipes`

**현재**: `features/explorer/components/DataTable.tsx`  
**이동**: `shared/ui/recipes/DataTable.tsx`

**작업 내용**:
- [ ] `explorerApi` 의존성 제거 → `onLoadData` prop으로 받기
- [ ] 파일 이동 (`.tsx`, `.css`)
- [ ] `recipes/index.ts`에 export 추가
- [ ] `features/explorer/components/index.ts`에서 제거
- [ ] `features/explorer/components/DataTable.tsx`를 re-export로 변경
- [ ] 사용하는 곳에서 import 경로 업데이트

**리팩토링 예시**:
```tsx
// Before
const { data: rawDataList } = useQuery({
  queryKey: ['rawdata-list', tenant, searchFilter, page],
  queryFn: () => explorerApi.listRawData(tenant, searchFilter || undefined, limit),
})

// After
interface DataTableProps<T> {
  type: string
  data: T[]
  total: number
  hasMore: boolean
  isLoading: boolean
  onLoadMore: (page: number, searchFilter?: string) => Promise<{ entries: T[], total: number, hasMore: boolean }>
  onSelect: (item: T) => void
  columns: ColumnDef<T>[]
  // ...
}
```

---

#### 3. YamlEditor → `shared/ui/recipes`

**현재**: `features/playground/components/YamlEditor/`  
**이동**: `shared/ui/recipes/YamlEditor.tsx`

**작업 내용**:
- [ ] 파일 이동 (`.tsx`, `.css`)
- [ ] `recipes/index.ts`에 export 추가
- [ ] 사용하는 곳에서 import 경로 업데이트

---

### ⚠️ 검토 필요

#### YamlViewer 위치 검토

**현재**: `shared/ui/YamlViewer.tsx`  
**검토**: `shared/ui/recipes/YamlViewer.tsx`로 이동 고려

**이유**: JsonViewer, DiffViewer와 같은 복합 패턴이므로 recipes에 있는 것이 일관성 있음

---

## 요약

### 이동 필요 (3개)
1. ✅ `SearchBar` → `shared/ui/recipes` (리팩토링 필요)
2. ✅ `DataTable` → `shared/ui/recipes` (리팩토링 필요)
3. ✅ `YamlEditor` → `shared/ui/recipes`

### 검토 필요 (1개)
- ⚠️ `YamlViewer` → `shared/ui/recipes`로 이동 고려

### 유지 (나머지 모두)
- ✅ `shared/ui`의 모든 컴포넌트 - 올바른 위치
- ✅ `shared/ui/recipes`의 JsonViewer, DiffViewer - 올바른 위치
- ✅ 모든 `features/*/components`의 도메인 특화 컴포넌트들 - 올바른 위치

---

## 마이그레이션 체크리스트

각 컴포넌트 이동 시:

- [ ] 파일 이동 (`.tsx`, `.css`)
- [ ] API 의존성 제거 (props로 받기)
- [ ] `recipes/index.ts`에 export 추가
- [ ] 기존 import 경로를 re-export로 변경 (backward compatibility)
- [ ] 사용하는 곳에서 import 경로 업데이트
- [ ] 테스트 확인
- [ ] 문서 업데이트
