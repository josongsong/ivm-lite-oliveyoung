# Shared UI 컴포넌트 — 목록 및 복사 가이드

**목적**: 다른 레포지토리로 복사하여 사용할 수 있도록 컴포넌트 목록 및 의존성 정리

**작성일**: 2026-02-01  
**버전**: 1.0

---

## 🎯 빠른 답변: 복사해서 바로 쓸 수 있나요?

### ✅ 네, 가능합니다!

**즉시 복사 가능한 컴포넌트** (React만 사용):
- Button, Input, TextArea, Label, Loading, Switch, Table

**복사 방법**:
```bash
# 파일만 복사하면 끝!
cp admin-ui/src/shared/ui/Button.tsx 새레포/src/shared/ui/
cp admin-ui/src/shared/ui/Button.css 새레포/src/shared/ui/
```

**추가 작업 없음**:
- ✅ CSS 변수 기본값 포함되어 있음
- ✅ 외부 라이브러리 의존성 없음
- ✅ 프로젝트 특정 코드 의존 없음

### ⚠️ lucide-react 필요한 컴포넌트

다음 컴포넌트들은 `npm install lucide-react`만 하면 됩니다:
- Select, Modal, StatusBadge, ErrorBoundary, Alert, Toast 등

### ❌ 수정 필요한 컴포넌트

- EnvironmentSelector (프로젝트 특정 store 의존)

---

## 컴포넌트 목록

### Core UI Components (핵심 컴포넌트)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| Button | `Button.tsx` | `Button.css` | 다양한 variant, size, loading 상태 지원 |
| IconButton | `IconButton.tsx` | `IconButton.css` | 아이콘 전용 버튼 |
| Input | `Input.tsx` | `Input.css` | 텍스트 입력 필드 |
| TextArea | `TextArea.tsx` | `TextArea.css` | 다중 줄 텍스트 입력 |
| Label | `Label.tsx` | `Label.css` | 폼 레이블 |
| Select | `Select.tsx` | `Select.css` | 드롭다운 선택 |
| Switch | `Switch.tsx` | `Switch.css` | 토글 스위치 |

### Layout Components (레이아웃 컴포넌트)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| Card | `Card.tsx` | `Card.css` | 카드 컨테이너 (StatsCard, StatsGrid 포함) |
| Section | `Section.tsx` | `Section.css` | 섹션 헤더, 접을 수 있는 섹션 |
| InfoRow | `InfoRow.tsx` | `InfoRow.css` | 정보 행 표시 |
| Accordion | `Accordion.tsx` | `Accordion.css` | 아코디언 컴포넌트 |

### Navigation (네비게이션)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| Tabs | `Tabs.tsx` | `Tabs.css` | 탭 네비게이션 |
| Pagination | `Pagination.tsx` | `Pagination.css` | 페이지네이션 |

### Data Display (데이터 표시)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| Table | `Table.tsx` | `Table.css` | 테이블 컴포넌트 |
| StatusBadge | `StatusBadge.tsx` | `StatusBadge.css` | 상태 배지 |
| Chip | `Chip.tsx` | `Chip.css` | 칩/태그 컴포넌트 |
| YamlViewer | `YamlViewer.tsx` | `YamlViewer.css` | YAML 뷰어 |
| Skeleton | `Skeleton.tsx` | `Skeleton.css` | 로딩 스켈레톤 |

### Feedback (피드백)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| Modal | `Modal.tsx` | `Modal.css` | 모달 다이얼로그 |
| Loading | `Loading.tsx` | `Loading.css` | 로딩 인디케이터 |
| Alert | `Alert.tsx` | `Alert.css` | 알림 메시지 |
| EmptyState | `EmptyState.tsx` | `EmptyState.css` | 빈 상태 표시 |
| Tooltip | `Tooltip.tsx` | `Tooltip.css` | 툴팁 |
| Toast | `Toast.tsx` | `Toast.css` | 토스트 알림 |

### Utility (유틸리티)

| 컴포넌트 | 파일 | CSS | 설명 |
|---------|------|-----|------|
| PageHeader | `PageHeader.tsx` | - | 페이지 헤더 |
| EnvironmentSelector | `EnvironmentSelector.tsx` | `EnvironmentSelector.css` | 환경 선택기 |
| ErrorBoundary | `ErrorBoundary.tsx` | `ErrorBoundary.css` | 에러 바운더리 |
| ApiError | `ApiError.tsx` | `ApiError.css` | API 에러 표시 |

### Utils (유틸리티 함수)

| 파일 | 설명 |
|------|------|
| `formatters.ts` | 날짜/시간 포맷터 |
| `animations.ts` | 애니메이션 유틸리티 |

---

## 실제 Admin 앱에서 사용되는 패턴 (디자인 시스템에 빠진 것들)

### Table Patterns (테이블 패턴)

실제 admin 앱에서 광범위하게 사용되는 테이블 관련 패턴들입니다:

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Table Container | `.table-container` | Outbox, Explorer | 테이블 래퍼 컨테이너 |
| Table Header | `.table-header` | DataTable | 테이블 헤더 영역 (제목 + 액션) |
| Table Title | `.table-title` | DataTable | 테이블 제목 (아이콘 + 텍스트 + 카운트) |
| Table Count | `.table-count` | DataTable | 항목 개수 배지 |
| Table Actions | `.table-actions` | DataTable | 액션 버튼 그룹 |
| Table Pagination | `.table-pagination` | DataTable | 페이지네이션 컨트롤 |
| Table Empty | `.table-empty` | DataTable | 빈 상태 표시 |
| Table Error | `.table-error` | DataTable | 에러 상태 표시 |
| Table Loading | `.table-loading` | DataTable | 로딩 상태 표시 |
| Empty Cell | `.empty-cell` | Outbox | 빈 셀 표시 |

**사용 예시**:
```tsx
<div className="table-container">
  <div className="table-header">
    <div className="table-title">
      <Database size={18} />
      <h3>RawData</h3>
      <span className="table-count">42</span>
    </div>
    <div className="table-actions">
      {/* 액션 버튼들 */}
    </div>
  </div>
  <table>...</table>
</div>
```

### Search & Filter Patterns (검색/필터 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Search Filter | `.search-filter` | DataTable, Contracts | 검색 입력 필드 (아이콘 포함) |
| Search Box | `.search-box` | Contracts | 검색 박스 (포커스 효과) |
| Traces Filters | `.traces-filters` | Traces | 필터 그룹 컨테이너 |

**사용 예시**:
```tsx
<div className="search-filter">
  <SearchIcon size={14} />
  <input type="text" placeholder="Entity ID로 필터..." />
</div>
```

### Action Button Patterns (액션 버튼 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Action Buttons | `.action-buttons` | Outbox, Tables | 액션 버튼 그룹 |
| Action Button | `.action-btn` | 여러 곳 | 기본 액션 버튼 |
| Action Button Primary | `.action-btn.primary` | 여러 곳 | 주요 액션 버튼 |
| Icon Button | `.btn-icon` | Outbox, Tables | 아이콘 전용 버튼 |
| Process Button | `.btn-icon.btn-process` | Outbox | 실행 버튼 (청록색) |

**사용 예시**:
```tsx
<div className="action-buttons">
  <button className="btn-icon btn-process">
    <Play size={16} />
  </button>
  <button className="btn-icon">
    <Eye size={16} />
  </button>
</div>
```

### Card & Panel Patterns (카드/패널 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Panel Header | `.panel-header` | Dashboard, Workflow | 패널 헤더 (아이콘 + 제목 + 링크) |
| Outbox Panel | `.outbox-panel` | Dashboard | Outbox 패널 컨테이너 |
| Actions Panel | `.actions-panel` | Dashboard | 액션 패널 컨테이너 |
| Action Card | `.action-card` | Dashboard | 빠른 액션 카드 |
| Contract Card | `.contract-card` | Contracts | 계약 카드 (색상 변형) |
| View All Link | `.view-all` | Dashboard | "전체보기" 링크 |

**사용 예시**:
```tsx
<div className="outbox-panel">
  <div className="panel-header">
    <Inbox size={18} />
    <h3>Outbox Queue</h3>
    <Link to="/outbox" className="view-all">
      상세보기 <ArrowRight size={14} />
    </Link>
  </div>
  {/* 패널 내용 */}
</div>
```

### Stats & Metrics Patterns (통계/메트릭 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Stat Card | `.stat-card` | Traces, Workflow | 통계 카드 (에러/경고/액센트 변형) |
| Outbox Stat | `.outbox-stat` | Dashboard | Outbox 통계 항목 (진행바 포함) |
| Stat Info | `.stat-info` | Dashboard | 통계 정보 컨테이너 |
| Stat Value | `.stat-value` | Dashboard, Traces | 통계 값 (mono 폰트) |
| Stat Label | `.stat-label` | Dashboard, Traces | 통계 라벨 |
| Stat Bar | `.stat-bar` | Dashboard | 진행바 (CSS 변수로 진행률 제어) |
| Stat Icon | `.stat-icon` | Traces | 통계 아이콘 |

**사용 예시**:
```tsx
<div className="outbox-stat pending">
  <Clock size={20} />
  <div className="stat-info">
    <span className="stat-value">42</span>
    <span className="stat-label">Pending</span>
  </div>
  <div className="stat-bar" style={{ '--progress': '75%' }} />
</div>
```

### Tab Patterns (탭 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Outbox Tabs | `.outbox-tabs` | Outbox | Outbox 탭 컨테이너 |
| Outbox Tab | `.outbox-tab` | Outbox | 개별 탭 (카운트 포함) |
| Kind Tabs | `.kind-tabs` | Contracts | 계약 종류 탭 그룹 |
| Kind Tab | `.kind-tab` | Contracts | 개별 계약 종류 탭 |

**사용 예시**:
```tsx
<div className="outbox-tabs">
  <button className="outbox-tab active">
    Recent
    <span className="tab-count">5</span>
  </button>
  <button className="outbox-tab">
    Failed
    <span className="tab-count">2</span>
  </button>
</div>
```

### Status & Health Patterns (상태/헬스 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Health Item | `.health-item` | Workflow | 헬스 상태 표시 (healthy/warning/error/inactive) |
| Worker Status | `.worker-status` | Dashboard | 워커 상태 표시 (running/stopped) |
| Tab Count | `.tab-count` | Outbox | 탭 내 카운트 배지 |

**사용 예시**:
```tsx
<div className="health-item healthy">Healthy</div>
<div className="worker-status running">Running</div>
```

### Text Utility Classes (텍스트 유틸리티 클래스)

| 클래스 | 설명 | 사용 위치 |
|--------|------|----------|
| `.mono` | Monospace 폰트 | 테이블 ID, 버전 등 |
| `.text-secondary` | 보조 텍스트 색상 | 여러 곳 |
| `.text-muted` | 흐린 텍스트 색상 | 여러 곳 |
| `.text-error` | 에러 텍스트 색상 | 에러 메시지 |
| `.text-warning` | 경고 텍스트 색상 | 경고 메시지 |
| `.text-orange` | 오렌지 텍스트 색상 | 특정 강조 |
| `.truncate` | 텍스트 잘라내기 (ellipsis) | 긴 텍스트 |

**사용 예시**:
```tsx
<td className="mono">{item.id.slice(0, 8)}...</td>
<td className="text-secondary">{item.createdAt}</td>
<span className="truncate" style={{ maxWidth: '200px' }}>긴 텍스트...</span>
```

### Cell Patterns (셀 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Entity Cell | `.entity-cell` | DataTable | 엔티티 ID 셀 (아이콘 + 텍스트) |
| Version Cell | `.version-cell` | DataTable | 버전 셀 |
| Version Badge | `.version-badge` | DataTable | 버전 배지 (mono 폰트) |
| Schema Cell | `.schema-cell` | DataTable | 스키마 셀 (mono 폰트) |
| Time Cell | `.time-cell` | DataTable | 시간 셀 (아이콘 + 텍스트) |
| Action Cell | `.action-cell` | DataTable | 액션 셀 (호버 시 표시) |

**사용 예시**:
```tsx
<td className="entity-cell">
  <Database size={14} />
  <span>{entry.entityId}</span>
</td>
<td className="version-cell">
  <span className="version-badge">v{entry.version}</span>
</td>
<td className="time-cell">
  <Clock size={12} />
  {entry.updatedAt}
</td>
```

### Layout Patterns (레이아웃 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Page Container | `.page-container` | 모든 페이지 | 페이지 컨테이너 |
| Dashboard Grid | `.dashboard-grid` | Dashboard | 대시보드 그리드 (2열) |
| Summary Row | `.summary-row` | Dashboard | 요약 행 (워커 상태) |
| Quick Actions | `.quick-actions` | Dashboard | 빠른 액션 그리드 (2열) |
| Worker Details | `.worker-details` | Dashboard | 워커 상세 정보 |

---

## 패턴 사용 가이드

### 1. 테이블 패턴 사용

```tsx
// 완전한 테이블 구조
<div className="data-table">
  <div className="table-header">
    <div className="table-title">
      <Icon size={18} />
      <h3>Title</h3>
      <span className="table-count">42</span>
    </div>
    <div className="table-actions">
      <div className="search-filter">
        <SearchIcon size={14} />
        <input placeholder="검색..." />
      </div>
      <button className="action-btn">새로고침</button>
      <button className="action-btn primary">New</button>
    </div>
  </div>
  
  <div className="table-content">
    <table>
      <thead>...</thead>
      <tbody>
        <tr>
          <td className="entity-cell">
            <Icon size={14} />
            <span>Entity ID</span>
          </td>
          <td className="version-cell">
            <span className="version-badge">v1</span>
          </td>
          <td className="time-cell">
            <Clock size={12} />
            {date}
          </td>
          <td className="action-cell">
            <ChevronRight size={14} />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  
  <div className="table-pagination">
    <button>이전</button>
    <span>Page 1</span>
    <button>다음</button>
  </div>
</div>
```

### 2. 통계 카드 패턴 사용

```tsx
// Outbox 통계
<div className="outbox-stats">
  <div className="outbox-stat pending">
    <Clock size={20} />
    <div className="stat-info">
      <span className="stat-value">42</span>
      <span className="stat-label">Pending</span>
    </div>
    <div className="stat-bar" style={{ '--progress': '75%' }} />
  </div>
</div>

// 일반 통계 카드
<div className="stat-card error">
  <div className="stat-icon">
    <AlertTriangle size={20} />
  </div>
  <div className="stat-content">
    <span className="stat-value">5</span>
    <span className="stat-label">Errors</span>
  </div>
</div>
```

### 3. 패널 패턴 사용

```tsx
<div className="outbox-panel">
  <div className="panel-header">
    <Inbox size={18} />
    <h3>Outbox Queue</h3>
    <Link to="/outbox" className="view-all">
      상세보기 <ArrowRight size={14} />
    </Link>
  </div>
  {/* 패널 내용 */}
</div>
```

---

## 빠진 컴포넌트/패턴 정리

### ✅ 이미 shared/ui에 있는 것들
- Button, IconButton
- Table (기본)
- Card, StatsCard
- StatusBadge
- Loading, EmptyState

### ⚠️ 패턴으로만 존재하는 것들 (컴포넌트화 필요)
- **TableHeader** - `.table-header` 패턴
- **SearchFilter** - `.search-filter` 패턴
- **ActionButtons** - `.action-buttons` 패턴
- **StatCard** - `.stat-card` 패턴 (StatsCard와 다름)
- **PanelHeader** - `.panel-header` 패턴
- **ActionCard** - `.action-card` 패턴
- **TabGroup** - `.outbox-tabs`, `.kind-tabs` 패턴 (Tabs와 다름)
- **HealthBadge** - `.health-item` 패턴
- **WorkerStatus** - `.worker-status` 패턴

### Form Patterns (폼 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Editor Form | `.editor-form` | RawDataEditor | 에디터 폼 컨테이너 |
| Editor Header | `.editor-header` | RawDataEditor | 에디터 헤더 |
| Editor Title | `.editor-title` | RawDataEditor | 에디터 제목 (아이콘 + 텍스트) |
| Editor Actions | `.editor-actions` | RawDataEditor | 에디터 액션 버튼 그룹 |
| Form Row | `.form-row` | RawDataEditor | 폼 행 (여러 필드 가로 배치) |
| Form Group | `.form-group` | RawDataEditor, Webhooks | 폼 그룹 (라벨 + 입력) |
| Form Group Flex-2 | `.form-group.flex-2` | RawDataEditor | 2배 너비 폼 그룹 |
| Form Input | `.form-input` | RawDataEditor | 폼 입력 필드 |
| Form Label | `.form-label` | Backfill | 폼 라벨 |
| Form Actions | `.form-actions` | Webhooks | 폼 액션 버튼 그룹 |
| Form Submit | `.form-submit` | RawDataEditor | 폼 제출 버튼 |

**사용 예시**:
```tsx
<div className="editor-form">
  <div className="form-row">
    <div className="form-group">
      <label>Tenant</label>
      <input className="form-input" />
    </div>
    <div className="form-group flex-2">
      <label>Entity ID</label>
      <input className="form-input" />
    </div>
  </div>
</div>
```

### Event & Checkbox Patterns (이벤트/체크박스 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Events Selector | `.events-selector` | Webhooks | 이벤트 선택 컨테이너 |
| Event Category | `.event-category` | Webhooks | 이벤트 카테고리 그룹 |
| Category Label | `.category-label` | Webhooks | 카테고리 라벨 |
| Category Events | `.category-events` | Webhooks | 카테고리 내 이벤트 목록 |
| Event Checkbox | `.event-checkbox` | Webhooks | 이벤트 체크박스 라벨 |
| Event Name | `.event-name` | Webhooks | 이벤트 이름 |
| Event Desc | `.event-desc` | Webhooks | 이벤트 설명 |

**사용 예시**:
```tsx
<div className="events-selector">
  <div className="event-category">
    <div className="category-label">RawData</div>
    <div className="category-events">
      <label className="event-checkbox">
        <input type="checkbox" />
        <span className="event-name">rawdata.created</span>
        <span className="event-desc">RawData 생성 시</span>
      </label>
    </div>
  </div>
</div>
```

### File Upload Patterns (파일 업로드 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| File Upload Button | `.file-upload-btn` | RawDataEditor | 파일 업로드 버튼 |

### JSON Editor Patterns (JSON 에디터 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| JSON Textarea | `.json-textarea` | RawDataEditor | JSON 입력 텍스트 영역 |
| JSON Textarea Error | `.json-textarea.error` | RawDataEditor | 에러 상태 JSON 입력 |

### Schema Selector Patterns (스키마 셀렉터 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Schema Selector | `.schema-selector` | RawDataEditor | 스키마 선택 컨테이너 |
| Schema Button | `.schema-btn` | RawDataEditor | 스키마 선택 버튼 |
| Schema Dropdown | `.schema-dropdown` | RawDataEditor | 스키마 드롭다운 메뉴 |
| Schema Item | `.schema-item` | RawDataEditor | 스키마 항목 |

**사용 예시**:
```tsx
<div className="schema-selector">
  <button className="schema-btn">
    <FileCode2 size={14} />
    <span>스키마 선택</span>
    <ChevronDown size={14} />
  </button>
  <div className="schema-dropdown">
    <div className="schema-item">Product</div>
    <div className="schema-item">Brand</div>
  </div>
</div>
```

### Section & Header Patterns (섹션/헤더 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Section Header | `.section-header` | Playground, 여러 곳 | 섹션 헤더 |
| Section Title | `.section-title` | Playground, Backfill, Workflow | 섹션 제목 |
| Section Actions | `.section-actions` | Playground | 섹션 액션 버튼 그룹 |
| Section Action | `.section-action` | Playground | 개별 섹션 액션 버튼 |
| Header Left | `.header-left` | Playground | 헤더 왼쪽 영역 |
| Header Title | `.header-title` | Playground | 헤더 제목 |
| Header Hint | `.header-hint` | Playground | 헤더 힌트 텍스트 |
| Header Tabs | `.header-tabs` | Playground | 헤더 탭 그룹 |
| Header Tab | `.header-tab` | Playground | 개별 헤더 탭 |
| Header Actions | `.header-actions` | Playground | 헤더 액션 버튼 그룹 |

**사용 예시**:
```tsx
<header className="playground-header">
  <div className="header-left">
    <div className="header-title">
      <Code2 size={18} />
      <span>Contract Playground</span>
    </div>
    <span className="header-hint">
      <Command size={12} />+Enter 실행
    </span>
  </div>
  <div className="header-tabs">
    <button className="header-tab active">RULESET</button>
  </div>
  <div className="header-actions">
    <button className="action-btn primary">실행</button>
  </div>
</header>
```

### Field Patterns (필드 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Field Chip | `.field-chip` | Preview | 필드 칩 표시 |
| Field Count | `.field-count` | Workflow | 필드 개수 배지 |
| Field Tag | `.field-tag` | Workflow | 필드 태그 |
| Field Tag Wildcard | `.field-tag.wildcard` | Workflow | 와일드카드 필드 태그 |
| Field Icon | `.field-icon` | Workflow | 필드 아이콘 |
| Field Name | `.field-name` | Workflow | 필드 이름 |

### Modal Patterns (모달 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Modal Overlay | `.modal-overlay` | Outbox, Webhooks | 모달 오버레이 |
| Modal Content | `.modal-content` | Outbox, Webhooks | 모달 콘텐츠 컨테이너 |
| Webhook Form Modal | `.webhook-form-modal` | Webhooks | 웹훅 폼 모달 |

**사용 예시**:
```tsx
<div className="modal-overlay" onClick={onClose}>
  <div className="modal-content" onClick={(e) => e.stopPropagation()}>
    {/* 모달 내용 */}
  </div>
</div>
```

### Status Badge Patterns (상태 배지 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Status Badge | `.status-badge` | Webhooks | 상태 배지 기본 |
| Status Badge Success | `.status-badge.success` | Webhooks | 성공 상태 |
| Status Badge Failed | `.status-badge.failed` | Webhooks | 실패 상태 |
| Status Badge Pending | `.status-badge.pending` | Webhooks | 대기 상태 |
| Status Badge Retrying | `.status-badge.retrying` | Webhooks | 재시도 중 상태 |
| Status Badge Circuit Open | `.status-badge.circuit_open` | Webhooks | Circuit Breaker 열림 |
| Status Badge Rate Limited | `.status-badge.rate_limited` | Webhooks | Rate Limit 상태 |

**참고**: `StatusBadge` 컴포넌트가 있지만, 일부 곳에서는 CSS 클래스로 직접 사용됨

### Layout Patterns (레이아웃 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Sidebar Header | `.sidebar-header` | Layout | 사이드바 헤더 |
| Sidebar Nav | `.sidebar-nav` | Layout | 사이드바 네비게이션 |
| Sidebar Footer | `.sidebar-footer` | Layout | 사이드바 푸터 |
| Main Content | `.main-content` | Layout | 메인 콘텐츠 영역 |
| Header Row | `.header-row` | Layout | 헤더 행 |

### Animation & Motion Patterns (애니메이션 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Spin | `.spin` | 여러 곳 | 회전 애니메이션 |
| Spinning | `.spinning` | Design System | 회전 중 상태 |

**사용 예시**:
```tsx
<Loader2 size={16} className="spin" />
```

### Empty & Error States (빈/에러 상태 패턴)

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Empty State | `.empty-state` | Webhooks | 빈 상태 컨테이너 |

### Design System Showcase Patterns (디자인 시스템 쇼케이스 패턴)

디자인 시스템 페이지에서 사용되는 패턴들:

| 패턴 | CSS 클래스 | 사용 위치 | 설명 |
|------|-----------|----------|------|
| Showcase | `.showcase` | 여러 Showcase | 쇼케이스 컨테이너 |
| Showcase Section | `.showcase-section` | 여러 Showcase | 쇼케이스 섹션 |
| Showcase Playground | `.showcase-playground` | 여러 Showcase | 쇼케이스 플레이그라운드 |
| Showcase Preview | `.showcase-preview` | 여러 Showcase | 쇼케이스 미리보기 |
| Showcase Controls | `.showcase-controls` | 여러 Showcase | 쇼케이스 컨트롤 |
| Control Group | `.control-group` | 여러 Showcase | 컨트롤 그룹 |
| Control Row | `.control-row` | 여러 Showcase | 컨트롤 행 |
| Control Label | `.control-label` | 여러 Showcase | 컨트롤 라벨 |
| Control Options | `.control-options` | 여러 Showcase | 컨트롤 옵션 |
| Example Grid | `.example-grid` | 여러 Showcase | 예제 그리드 |
| Example Card | `.example-card` | 여러 Showcase | 예제 카드 |
| Pattern Card | `.pattern-card` | PatternGuide | 패턴 카드 |
| Pattern Preview | `.pattern-preview` | PatternGuide | 패턴 미리보기 |
| DS Section | `.ds-section` | Design System | 디자인 시스템 섹션 |
| DS Section Header | `.ds-section-header` | Design System | 섹션 헤더 |
| DS Section Title | `.ds-section-title` | Design System | 섹션 제목 |
| DS Placeholder | `.ds-placeholder` | Design System | 플레이스홀더 |
| DS Component Grid | `.ds-component-grid` | Design System | 컴포넌트 그리드 |
| DS Component Card | `.ds-component-card` | Design System | 컴포넌트 카드 |

---

## 추가로 발견된 빠진 것들

### 1. **Form 컴포넌트 부재**
- `.form-group`, `.form-row`, `.form-input` 패턴이 있지만 재사용 가능한 `Form`, `FormGroup`, `FormRow` 컴포넌트가 없음
- 각 feature에서 직접 구현하고 있음

### 2. **SchemaSelector 컴포넌트 부재**
- `.schema-selector` 패턴이 있지만 컴포넌트로 추출되지 않음

### 3. **EventSelector 컴포넌트 부재**
- `.events-selector` 패턴이 있지만 컴포넌트로 추출되지 않음

### 4. **FileUpload 컴포넌트 부재**
- `.file-upload-btn` 패턴이 있지만 컴포넌트로 추출되지 않음

### 5. **JSONEditor 컴포넌트 부재**
- `.json-textarea` 패턴이 있지만 컴포넌트로 추출되지 않음

### 6. **SectionHeader 컴포넌트 부재**
- `.section-header`, `.section-title`, `.section-actions` 패턴이 있지만 컴포넌트로 추출되지 않음

### 7. **StatusBadge 변형들**
- Webhooks에서 사용하는 `.status-badge.success`, `.status-badge.failed` 등이 `StatusBadge` 컴포넌트와 일치하지 않을 수 있음

---

## 📝 권장사항

1. **패턴을 컴포넌트로 추출**: 자주 사용되는 패턴들을 재사용 가능한 컴포넌트로 만들기
   - `Form`, `FormGroup`, `FormRow` 컴포넌트
   - `SchemaSelector` 컴포넌트
   - `EventSelector` 컴포넌트
   - `FileUpload` 컴포넌트
   - `JSONEditor` 컴포넌트
   - `SectionHeader` 컴포넌트

2. **CSS 클래스 문서화**: 현재 패턴 클래스들을 디자인 시스템에 명시적으로 문서화

3. **일관성 확보**: 비슷한 패턴들을 통일된 API로 제공
   - 모든 폼은 `Form` 컴포넌트 사용
   - 모든 섹션 헤더는 `SectionHeader` 컴포넌트 사용
   - 모든 상태 배지는 `StatusBadge` 컴포넌트 사용

4. **유틸리티 클래스 정리**: `.mono`, `.text-secondary`, `.truncate` 등을 공통 유틸리티로 정리

5. **애니메이션 패턴 표준화**: `.spin`, `.spinning` 등을 공통 애니메이션 유틸리티로 정리

---

## 복사 가이드

### 1. 필수 파일 복사

각 컴포넌트를 복사할 때 다음 파일들을 함께 복사해야 합니다:

```
컴포넌트명.tsx          # 컴포넌트 소스 코드
컴포넌트명.css          # 스타일 파일 (있는 경우)
```

### 2. 전체 복사 명령어

```bash
# 전체 shared/ui 디렉토리 복사
cp -r admin-ui/src/shared/ui /path/to/new-repo/src/shared/

# 또는 특정 컴포넌트만 복사
cp admin-ui/src/shared/ui/Button.tsx /path/to/new-repo/src/shared/ui/
cp admin-ui/src/shared/ui/Button.css /path/to/new-repo/src/shared/ui/
```

### 3. 의존성 확인

#### ✅ 즉시 복사 가능한 컴포넌트 (React만 사용)

다음 컴포넌트들은 **React만** 사용하며, 복사만 하면 바로 사용 가능합니다:

- ✅ **Button** - 완전 독립적
- ✅ **Input** - 완전 독립적  
- ✅ **TextArea** - 완전 독립적
- ✅ **Label** - 완전 독립적
- ✅ **Loading** - 완전 독립적
- ✅ **Table** - 완전 독립적
- ✅ **Switch** - 완전 독립적

#### ⚠️ lucide-react 의존성 있는 컴포넌트

다음 컴포넌트들은 `lucide-react` 패키지가 필요합니다:

```bash
npm install lucide-react
```

- ⚠️ **Select** - `Check`, `ChevronDown` 아이콘
- ⚠️ **IconButton** - `LucideIcon` 타입
- ⚠️ **Modal** - `X` 아이콘
- ⚠️ **StatusBadge** - 여러 아이콘
- ⚠️ **ErrorBoundary** - `AlertTriangle`, `RefreshCw`
- ⚠️ **ApiError** - 여러 아이콘
- ⚠️ **EmptyState** - `Inbox` 아이콘
- ⚠️ **Alert** - 여러 아이콘
- ⚠️ **Card** - `TrendingUp`, `TrendingDown` 등
- ⚠️ **InfoRow** - `Check`, `Copy` 등
- ⚠️ **Section** - `ChevronDown`, `ChevronRight`
- ⚠️ **Accordion** - `ChevronDown`
- ⚠️ **Pagination** - 화살표 아이콘들
- ⚠️ **Toast** - 여러 아이콘

#### ❌ 프로젝트 특정 의존성 있는 컴포넌트

다음 컴포넌트들은 프로젝트 특정 코드에 의존하므로 수정 필요:

- ❌ **EnvironmentSelector** - `@/shared/store`, `@/shared/types` 의존 (수정 필요)
- ❌ **ZustandTest** - 테스트 파일 (복사 불필요)

#### 📦 필요한 패키지

```bash
# 필수
npm install react react-dom

# lucide-react 사용 컴포넌트를 사용할 경우
npm install lucide-react

# TypeScript 사용 시
npm install -D typescript @types/react @types/react-dom
```

#### CSS 변수 의존성

CSS는 CSS 변수를 사용하지만 **기본값이 포함**되어 있어서 복사만 해도 동작합니다:

```css
/* 예시: Button.css */
border-radius: var(--radius-sm, 6px);  /* 기본값 6px 있음 */
outline: 2px solid var(--accent-cyan, #00d4ff);  /* 기본값 있음 */
```

**선택사항**: 프로젝트에 맞게 CSS 변수를 재정의할 수 있습니다.

### 4. index.ts 업데이트

복사 후 `index.ts` 파일을 새 레포지토리에 맞게 업데이트:

```typescript
// 필요한 컴포넌트만 export
export { Button } from './Button'
export type { ButtonProps } from './Button'
export { Input } from './Input'
// ...
```

---

## 컴포넌트별 상세 정보

### Button

**파일**: `Button.tsx`, `Button.css`

**Props**:
- `variant`: 'primary' | 'secondary' | 'ghost' | 'danger'
- `size`: 'sm' | 'md' | 'lg'
- `loading`: boolean
- `icon`: ReactNode (왼쪽 아이콘)
- `iconRight`: ReactNode (오른쪽 아이콘)
- `fullWidth`: boolean

**의존성**: 없음 (React만 사용)

---

### Input

**파일**: `Input.tsx`, `Input.css`

**Props**:
- 표준 HTML input props
- `error`: boolean
- `helperText`: string

**의존성**: 없음

---

### Select

**파일**: `Select.tsx`, `Select.css`

**Props**:
- `options`: Array<{ value: string; label: string }>
- `value`: string
- `onChange`: (value: string) => void
- `placeholder`: string

**의존성**: 없음

---

### Modal

**파일**: `Modal.tsx`, `Modal.css`

**Props**:
- `isOpen`: boolean
- `onClose`: () => void
- `title`: string
- `children`: ReactNode

**의존성**: 없음

---

### Table

**파일**: `Table.tsx`, `Table.css`

**Props**:
- `columns`: Array<{ key: string; label: string }>
- `data`: Array<Record<string, any>>
- `onRowClick`: (row: any) => void

**의존성**: 없음

---

### Toast

**파일**: `Toast.tsx`, `Toast.css`

**사용법**:
```typescript
import { toast } from '@/shared/ui'

toast.success('성공했습니다!')
toast.error('에러가 발생했습니다!')
toast.info('정보입니다')
```

**의존성**: 없음

---

## CSS 변수 의존성

일부 컴포넌트는 CSS 변수를 사용할 수 있습니다. 새 레포지토리에서 다음 CSS 변수를 정의해야 할 수 있습니다:

```css
:root {
  --color-primary: #3b82f6;
  --color-secondary: #64748b;
  --color-danger: #ef4444;
  --color-success: #22c55e;
  --color-warning: #f59e0b;
  
  --spacing-xs: 0.25rem;
  --spacing-sm: 0.5rem;
  --spacing-md: 1rem;
  --spacing-lg: 1.5rem;
  --spacing-xl: 2rem;
  
  --border-radius-sm: 0.25rem;
  --border-radius-md: 0.5rem;
  --border-radius-lg: 0.75rem;
  
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.1);
}
```

---

## 빠른 시작 가이드

### ✅ 가장 쉬운 방법: 핵심 컴포넌트만 복사 (React만 사용)

다음 컴포넌트들은 **복사만 하면 바로 사용 가능**합니다:

```bash
# 새 레포지토리에서
mkdir -p src/shared/ui

# 핵심 컴포넌트 복사 (React만 사용, 외부 의존성 없음)
cp admin-ui/src/shared/ui/Button.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Button.css src/shared/ui/
cp admin-ui/src/shared/ui/Input.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Input.css src/shared/ui/
cp admin-ui/src/shared/ui/TextArea.tsx src/shared/ui/
cp admin-ui/src/shared/ui/TextArea.css src/shared/ui/
cp admin-ui/src/shared/ui/Label.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Label.css src/shared/ui/
cp admin-ui/src/shared/ui/Loading.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Loading.css src/shared/ui/
cp admin-ui/src/shared/ui/Switch.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Switch.css src/shared/ui/
cp admin-ui/src/shared/ui/Table.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Table.css src/shared/ui/
```

### ⚠️ lucide-react 필요한 컴포넌트 복사

```bash
# 먼저 패키지 설치
npm install lucide-react

# 컴포넌트 복사
cp admin-ui/src/shared/ui/Select.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Select.css src/shared/ui/
cp admin-ui/src/shared/ui/Modal.tsx src/shared/ui/
cp admin-ui/src/shared/ui/Modal.css src/shared/ui/
# ... 필요한 컴포넌트들
```

### 📝 index.ts 생성

```typescript
// src/shared/ui/index.ts
export { Button } from './Button'
export type { ButtonProps } from './Button'
export { Input } from './Input'
export { TextArea } from './TextArea'
export { Label } from './Label'
export { Loading } from './Loading'
export { Switch } from './Switch'
export { Table } from './Table'
// 필요한 컴포넌트만 추가
```

### 🚀 사용 예시

```typescript
import { Button, Input, Select } from '@/shared/ui'

function MyComponent() {
  return (
    <div>
      <Button variant="primary">Click me</Button>
      <Input placeholder="Enter text" />
      <Select 
        value="option1" 
        onChange={(v) => console.log(v)}
        options={[
          { value: 'option1', label: 'Option 1' },
          { value: 'option2', label: 'Option 2' }
        ]}
      />
    </div>
  )
}
```

### 2. index.ts 생성

```typescript
// src/shared/ui/index.ts
export { Button } from './Button'
export type { ButtonProps } from './Button'
export { Input } from './Input'
export { Select } from './Select'
export { Modal } from './Modal'
export { Loading } from './Loading'
// 필요한 컴포넌트만 추가
```

### 3. 사용

```typescript
import { Button, Input, Select } from '@/shared/ui'

function MyComponent() {
  return (
    <div>
      <Button variant="primary">Click me</Button>
      <Input placeholder="Enter text" />
      <Select options={[...]} />
    </div>
  )
}
```

---

## 체크리스트

복사 후 확인사항:

- [ ] 컴포넌트 파일 복사 완료
- [ ] CSS 파일 복사 완료
- [ ] index.ts 업데이트
- [ ] CSS 변수 정의 확인
- [ ] 타입 정의 확인 (TypeScript)
- [ ] 컴포넌트 동작 테스트
- [ ] 스타일 확인

---

**작성일**: 2026-02-01  
**버전**: 1.0
