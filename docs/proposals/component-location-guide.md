# 컴포넌트 위치 판단 가이드

이 문서는 각 컴포넌트를 `shared/ui`, `shared/ui/recipes`, `features/*/components` 중 어디에 둘지 판단하는 가이드입니다.

**작성일**: 2026-02-01  
**버전**: 1.0

---

## 판단 기준

### 1. `shared/ui` (범용 기본 컴포넌트)
**조건**:
- ✅ 여러 feature에서 사용 가능한 **기본 UI 컴포넌트**
- ✅ 도메인 로직이 없는 **순수 UI 컴포넌트**
- ✅ 다른 프로젝트에서도 바로 사용 가능한 **독립적인 컴포넌트**
- ✅ 예: Button, Input, Modal, Table, Card, StatusBadge 등

**예시**:
- `Button`, `Input`, `Modal` - 완전히 범용적
- `Table`, `Card`, `StatusBadge` - 범용적이지만 커스터마이징 가능
- `Form`, `FormGroup`, `FormRow` - 범용 폼 패턴
- `TableHeader`, `PanelHeader`, `ActionCard` - 범용 레이아웃 패턴

### 2. `shared/ui/recipes` (복합 패턴 컴포넌트)
**조건**:
- ✅ 여러 기본 컴포넌트를 조합한 **복합 패턴**
- ✅ 특정 도메인에 특화되었지만 **여러 곳에서 사용 가능**
- ✅ 비즈니스 로직은 없지만 **특정 용도에 최적화**된 패턴
- ✅ 예: JsonViewer, DiffViewer, YamlViewer 등

**예시**:
- `JsonViewer` - JSON 데이터 표시 패턴 (여러 feature에서 사용)
- `DiffViewer` - 버전 비교 패턴 (여러 feature에서 사용)
- `YamlViewer` - YAML 표시 패턴 (여러 feature에서 사용)

### 3. `features/*/components` (Feature 특화 컴포넌트)
**조건**:
- ✅ 특정 feature의 **도메인 로직을 포함**하는 컴포넌트
- ✅ 해당 feature의 **API/타입에 의존**하는 컴포넌트
- ✅ 다른 feature에서 사용할 수 있지만, **주로 해당 feature 내에서만 사용**
- ✅ 예: RawDataPanel, SliceList, TraceList 등

**예시**:
- `RawDataPanel` - RawData 도메인 특화 (explorerApi 사용)
- `SliceList` - Slice 도메인 특화 (explorerApi 사용)
- `TraceList` - Trace 도메인 특화 (tracesApi 사용)
- `WorkflowCanvas` - Workflow 도메인 특화

### 4. 단독으로 사용 (Feature 내부 컴포넌트)
**조건**:
- ✅ 해당 feature의 **내부에서만 사용**되는 컴포넌트
- ✅ export하지 않고 **내부 구현 세부사항**
- ✅ 예: 각 feature의 내부 헬퍼 컴포넌트들

---

## 현재 컴포넌트 분류

### ✅ `shared/ui`에 있는 컴포넌트들 (올바른 위치)

#### Core UI Components
- `Button` ✅ - 완전 범용
- `IconButton` ✅ - 완전 범용
- `Input` ✅ - 완전 범용
- `TextArea` ✅ - 완전 범용
- `Label` ✅ - 완전 범용
- `Select` ✅ - 완전 범용
- `Switch` ✅ - 완전 범용

#### Layout Components
- `Card`, `StatsCard`, `StatsGrid` ✅ - 범용 카드 패턴
- `Section`, `SectionHeader`, `CollapsibleSection` ✅ - 범용 섹션 패턴
- `TableHeader` ✅ - 범용 테이블 헤더 패턴
- `PanelHeader` ✅ - 범용 패널 헤더 패턴
- `ActionCard` ✅ - 범용 액션 카드 패턴
- `StatCard` ✅ - 범용 통계 카드 패턴

#### Form Components
- `Form`, `FormRow`, `FormGroup`, `FormInput`, `FormTextArea` ✅ - 범용 폼 패턴
- `FileUpload` ✅ - 범용 파일 업로드
- `SchemaSelector` ✅ - 범용 스키마 선택 (도메인 특화 없음)

#### Navigation
- `Tabs` ✅ - 완전 범용
- `Pagination` ✅ - 완전 범용

#### Data Display
- `Table` ✅ - 완전 범용
- `StatusBadge` ✅ - 완전 범용
- `Chip` ✅ - 완전 범용
- `Skeleton` ✅ - 완전 범용

#### Feedback
- `Modal` ✅ - 완전 범용
- `Loading` ✅ - 완전 범용
- `Alert` ✅ - 완전 범용
- `EmptyState` ✅ - 완전 범용
- `Tooltip` ✅ - 완전 범용
- `Toast` ✅ - 완전 범용

#### Search
- `SearchFilter` ✅ - 범용 검색 필터 패턴

#### Utility
- `PageHeader` ✅ - 범용 페이지 헤더
- `ErrorBoundary` ✅ - 완전 범용
- `ApiError` ✅ - 범용 API 에러 표시

---

### ✅ `shared/ui/recipes`에 있는 컴포넌트들 (올바른 위치)

- `JsonViewer` ✅ - JSON 표시 복합 패턴 (여러 feature에서 사용)
- `DiffViewer` ✅ - 버전 비교 복합 패턴 (여러 feature에서 사용)
- `YamlViewer` ✅ - YAML 표시 복합 패턴 (여러 feature에서 사용)

---

### ⚠️ 판단이 필요한 컴포넌트들

#### Explorer Components (`features/explorer/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `SearchBar` | `features/explorer/components/` | **`shared/ui/recipes`** | 범용 검색 바 패턴, 다른 feature에서도 사용 가능 |
| `DataTable` | `features/explorer/components/` | **`shared/ui/recipes`** | 범용 데이터 테이블 패턴 (검색, 필터, 페이지네이션 포함) |
| `RawDataPanel` | `features/explorer/components/` | **`features/explorer/components/`** ✅ | RawData 도메인 특화 (explorerApi 의존) |
| `RawDataEditor` | `features/explorer/components/` | **`features/explorer/components/`** ✅ | RawData 도메인 특화 (explorerApi 의존) |
| `SliceList` | `features/explorer/components/` | **`features/explorer/components/`** ✅ | Slice 도메인 특화 (explorerApi 의존) |
| `ViewPreview` | `features/explorer/components/` | **`features/explorer/components/`** ✅ | View 도메인 특화 (explorerApi 의존) |
| `LineageGraph` | `features/explorer/components/` | **`features/explorer/components/`** ✅ | Lineage 도메인 특화 (explorerApi 의존) |

#### Outbox Components (`features/outbox/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `RecentTable` | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 (outbox 타입 의존) |
| `FailedTable` | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 |
| `DlqTable` | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 |
| `StaleTable` | `features/outbox/components/` | **`features/outbox/components/`** ✅ | Outbox 도메인 특화 |

#### Traces Components (`features/traces/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `TraceList` | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 (tracesApi 의존) |
| `TraceFilters` | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 |
| `WaterfallTimeline` | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 |
| `SpanDetails` | `features/traces/components/` | **`features/traces/components/`** ✅ | Trace 도메인 특화 |

#### Workflow Components (`features/workflow/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `RawDataNode`, `SliceNode` 등 | `features/workflow/components/` | **`features/workflow/components/`** ✅ | Workflow 도메인 특화 (react-flow 의존) |
| `WorkflowCanvas` | `features/workflow/ui/` | **`features/workflow/ui/`** ✅ | 페이지 레벨 컴포넌트 |
| `WorkflowDetailPanel` | `features/workflow/ui/` | **`features/workflow/ui/`** ✅ | 페이지 레벨 컴포넌트 |

#### Contracts Components (`features/contracts/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `ContractDescription` | `features/contracts/components/` | **`features/contracts/components/`** ✅ | Contract 도메인 특화 |
| `ContractGraph` | `features/contracts/components/` | **`features/contracts/components/`** ✅ | Contract 도메인 특화 |

#### Playground Components (`features/playground/components/`)

| 컴포넌트 | 현재 위치 | 제안 위치 | 이유 |
|---------|----------|----------|------|
| `YamlEditor` | `features/playground/components/` | **`shared/ui/recipes`** | 범용 YAML 에디터 패턴, 다른 곳에서도 사용 가능 |
| `SampleInput` | `features/playground/components/` | **`features/playground/components/`** ✅ | Playground 특화 |
| `PreviewPanel` | `features/playground/components/` | **`features/playground/components/`** ✅ | Playground 특화 |

---

## 이동이 필요한 컴포넌트들

### 1. `SearchBar` → `shared/ui/recipes`

**현재**: `features/explorer/components/SearchBar.tsx`  
**이동**: `shared/ui/recipes/SearchBar.tsx`

**이유**:
- 범용 검색 바 패턴 (tenant 선택 + entity ID 검색)
- 다른 feature에서도 사용 가능
- 도메인 로직이 거의 없음

**작업**:
```bash
# 파일 이동
mv admin-ui/src/features/explorer/components/SearchBar.tsx admin-ui/src/shared/ui/recipes/
mv admin-ui/src/features/explorer/components/SearchBar.css admin-ui/src/shared/ui/recipes/

# recipes/index.ts에 export 추가
# features/explorer/components/index.ts에서 제거
# features/explorer/components/SearchBar.tsx를 re-export로 변경 (backward compatibility)
```

### 2. `DataTable` → `shared/ui/recipes`

**현재**: `features/explorer/components/DataTable.tsx`  
**이동**: `shared/ui/recipes/DataTable.tsx`

**이유**:
- 범용 데이터 테이블 패턴 (검색, 필터, 페이지네이션 포함)
- 다른 feature에서도 사용 가능
- 도메인 로직이 거의 없음 (props로 받아서 사용)

**작업**:
```bash
# 파일 이동
mv admin-ui/src/features/explorer/components/DataTable.tsx admin-ui/src/shared/ui/recipes/
mv admin-ui/src/features/explorer/components/DataTable.css admin-ui/src/shared/ui/recipes/

# recipes/index.ts에 export 추가
# features/explorer/components/index.ts에서 제거
# features/explorer/components/DataTable.tsx를 re-export로 변경
```

### 3. `YamlEditor` → `shared/ui/recipes`

**현재**: `features/playground/components/YamlEditor/`  
**이동**: `shared/ui/recipes/YamlEditor.tsx`

**이유**:
- 범용 YAML 에디터 패턴
- Contract Editor, Playground 등 여러 곳에서 사용 가능
- 도메인 로직이 거의 없음

**작업**:
```bash
# 파일 이동
mv admin-ui/src/features/playground/components/YamlEditor admin-ui/src/shared/ui/recipes/

# recipes/index.ts에 export 추가
```

---

## 그대로 유지할 컴포넌트들

### Explorer Components (도메인 특화)
- ✅ `RawDataPanel` - RawData 도메인 특화 (explorerApi 의존)
- ✅ `RawDataEditor` - RawData 도메인 특화 (explorerApi 의존)
- ✅ `SliceList` - Slice 도메인 특화 (explorerApi 의존)
- ✅ `ViewPreview` - View 도메인 특화 (explorerApi 의존)
- ✅ `LineageGraph` - Lineage 도메인 특화 (explorerApi 의존)

### Outbox Components (도메인 특화)
- ✅ `RecentTable`, `FailedTable`, `DlqTable`, `StaleTable` - Outbox 도메인 특화

### Traces Components (도메인 특화)
- ✅ `TraceList`, `TraceFilters`, `WaterfallTimeline`, `SpanDetails` - Trace 도메인 특화

### Workflow Components (도메인 특화)
- ✅ 모든 노드 컴포넌트들 - Workflow 도메인 특화 (react-flow 의존)
- ✅ `WorkflowCanvas`, `WorkflowDetailPanel` - 페이지 레벨 컴포넌트

### Contracts Components (도메인 특화)
- ✅ `ContractDescription`, `ContractGraph` - Contract 도메인 특화

### Playground Components (Feature 특화)
- ✅ `SampleInput`, `PreviewPanel` - Playground 특화

---

## 작업 우선순위

### 높은 우선순위 (즉시 이동)
1. ✅ `SearchBar` → `shared/ui/recipes` (범용 패턴)
2. ✅ `DataTable` → `shared/ui/recipes` (범용 패턴)
3. ✅ `YamlEditor` → `shared/ui/recipes` (범용 패턴)

### 중간 우선순위 (검토 후 이동)
- 없음 (현재 구조가 적절함)

### 낮은 우선순위 (유지)
- 모든 도메인 특화 컴포넌트들은 현재 위치 유지

---

## 마이그레이션 체크리스트

각 컴포넌트 이동 시:

- [ ] 파일 이동 (`.tsx`, `.css`)
- [ ] `recipes/index.ts`에 export 추가
- [ ] 기존 import 경로를 re-export로 변경 (backward compatibility)
- [ ] 사용하는 곳에서 import 경로 업데이트
- [ ] 테스트 확인
- [ ] 문서 업데이트

---

## 참고사항

1. **Backward Compatibility**: 기존 코드가 깨지지 않도록 re-export 사용
2. **점진적 마이그레이션**: 한 번에 하나씩 이동하고 테스트
3. **도메인 로직 확인**: API 호출이나 특정 타입에 의존하는지 확인
4. **재사용성 평가**: 다른 feature에서도 사용 가능한지 평가
