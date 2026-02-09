# UI 컴포넌트 위치 정리

이 문서는 `shared/ui` 외에 다른 곳에서 사용되는 뷰/UI 컴포넌트들을 정리합니다.

## 구조 개요

프로젝트는 Feature-Sliced Design (FSD) 아키텍처를 따르고 있으며, 다음과 같은 구조로 되어 있습니다:

```
admin-ui/src/
├── shared/ui/          # 공통 UI 컴포넌트 (재사용 가능한 범용 컴포넌트)
├── features/*/ui/      # Feature별 메인 페이지 컴포넌트
├── features/*/components/  # Feature별 특화 컴포넌트
└── widgets/*/ui/      # 위젯 레벨 컴포넌트
```

---

## 1. Features - UI 폴더 (메인 페이지 컴포넌트)

각 feature의 `ui/` 폴더에는 해당 기능의 메인 페이지 컴포넌트가 있습니다.

### 1.1 Explorer (`features/explorer/ui/`)
- **DataExplorer.tsx** - 데이터 탐색기 메인 페이지

### 1.2 Contracts (`features/contracts/ui/`)
- **Contracts.tsx** - 계약 목록 페이지
- **ContractDetail.tsx** - 계약 상세 페이지

### 1.3 Dashboard (`features/dashboard/ui/`)
- **Dashboard.tsx** - 대시보드 메인 페이지
- **HourlyChart.tsx** - 시간별 차트 컴포넌트

### 1.4 Design System (`features/design-system/ui/`)
- **DesignSystemPage.tsx** - 디자인 시스템 메인 페이지
- **ComponentDetail.tsx** - 컴포넌트 상세 페이지
- **ComponentCategory.tsx** - 컴포넌트 카테고리 페이지
- **FoundationsSection.tsx** - Foundations 섹션 페이지
- **PatternGuide.tsx** - 패턴 가이드 페이지
- **ResourcePage.tsx** - 리소스 페이지

### 1.5 Workflow (`features/workflow/ui/`)
- **Workflow.tsx** - 워크플로우 메인 페이지
- **WorkflowCanvas.tsx** - 워크플로우 캔버스
- **WorkflowDetailPanel.tsx** - 워크플로우 상세 패널

### 1.6 Outbox (`features/outbox/ui/`)
- **Outbox.tsx** - Outbox 메인 페이지

### 1.7 Pipeline (`features/pipeline/ui/`)
- **Pipeline.tsx** - 파이프라인 메인 페이지

### 1.8 Traces (`features/traces/ui/`)
- **TracesPage.tsx** - 트레이스 메인 페이지

### 1.9 기타 Features
- **alerts/ui/Alerts.tsx** - 알림 페이지
- **backfill/ui/Backfill.tsx** - 백필 페이지
- **environment/ui/Environment.tsx** - 환경 설정 페이지
- **health/ui/Health.tsx** - 헬스 체크 페이지
- **observability/ui/Observability.tsx** - 관찰성 페이지
- **webhooks/ui/WebhooksPage.tsx** - 웹훅 페이지
- **contract-editor/ui/ContractEditorPage.tsx** - 계약 에디터 페이지
- **playground/PlaygroundPage.tsx** - 플레이그라운드 페이지

---

## 2. Features - Components 폴더 (특화 컴포넌트)

각 feature의 `components/` 폴더에는 해당 기능에 특화된 재사용 가능한 컴포넌트들이 있습니다.

### 2.1 Explorer Components (`features/explorer/components/`)
**Export되는 컴포넌트들:**
- `SearchBar` - 검색 바
- `JsonViewer` - JSON 뷰어
- `DiffViewer` - Diff 뷰어
- `RawDataPanel` - RawData 패널
- `RawDataEditor` - RawData 에디터
- `SliceList` - 슬라이스 리스트
- `ViewPreview` - 뷰 미리보기
- `LineageGraph` - 계보 그래프
- `DataTable` - 데이터 테이블

**내부 컴포넌트들:**
- `editor/` - 에디터 관련 컴포넌트
  - `JsonInputSection.tsx`
  - `SchemaSelector.tsx`
  - `SubmitSection.tsx`
- `table-parts/` - 테이블 파트 컴포넌트
  - `DataTableHeader.tsx`
  - `DataTableRow.tsx`
- `rawdata/` - RawData 관련 컴포넌트들

### 2.2 Contracts Components (`features/contracts/components/`)
- `ContractDescription.tsx` - 계약 설명 컴포넌트 (export됨)
- `ContractGraph.tsx` - 계약 그래프
- `list/ContractGrid.tsx` - 계약 그리드
- `list/ContractStatsPanel.tsx` - 계약 통계 패널

### 2.3 Outbox Components (`features/outbox/components/`)
**Export되는 컴포넌트들:**
- `RecentTable` - 최근 테이블
- `FailedTable` - 실패 테이블
- `DlqTable` - DLQ 테이블
- `StaleTable` - 오래된 테이블

**내부 컴포넌트들:**
- `OutboxActions.tsx` - Outbox 액션
- `OutboxHeader.tsx` - Outbox 헤더
- `OutboxTabContent.tsx` - Outbox 탭 콘텐츠
- `OutboxDetailModal.tsx` - Outbox 상세 모달

### 2.4 Traces Components (`features/traces/components/`)
**Export되는 컴포넌트들:**
- `TraceList` - 트레이스 리스트
- `TraceFilters` - 트레이스 필터
- `WaterfallTimeline` - 워터폴 타임라인
- `SpanDetails` - 스팬 상세

**내부 컴포넌트들:**
- `TraceDetailPanel.tsx` - 트레이스 상세 패널
- `TracesStatsCards.tsx` - 트레이스 통계 카드
- `waterfall/` - 워터폴 관련 컴포넌트들
  - `Minimap.tsx`
  - `SpanRow.tsx`
  - `TimelineScale.tsx`
  - `WaterfallHeader.tsx`
- `span-details/` - 스팬 상세 관련

### 2.5 Workflow Components (`features/workflow/components/`)
- `nodes/` - 노드 컴포넌트들
  - `RawDataNode.tsx`
  - `SliceNode.tsx`
  - `ViewNode.tsx`
  - `SinkNode.tsx`
  - `RuleNode.tsx`
  - `ViewDefNode.tsx`
  - `SinkRuleNode.tsx`
  - `BaseNode.tsx`
- `edges/LabeledEdge.tsx` - 레이블된 엣지
- `detail/BasicInfoSection.tsx` - 기본 정보 섹션
- `WorkflowEmpty.tsx` - 빈 워크플로우
- `WorkflowToolbar.tsx` - 워크플로우 툴바

### 2.6 Pipeline Components (`features/pipeline/components/`)
- `PipelineStatsGrid.tsx` - 파이프라인 통계 그리드
- `PipelineStages.tsx` - 파이프라인 스테이지
- `RecentSlicesTable.tsx` - 최근 슬라이스 테이블
- `EntityFlowSearch.tsx` - 엔티티 플로우 검색

### 2.7 Design System Components (`features/design-system/components/`)

#### Layout Components (`components/layout/`)
**Export되는 컴포넌트들:**
- `Sidebar` - 사이드바
- `ContentArea` - 콘텐츠 영역
- `Header` - 헤더
- `PageHeader` - 페이지 헤더

#### Showcase Components (`components/showcase/`)
각 컴포넌트의 쇼케이스 버전들:
- `ButtonShowcase.tsx`
- `InputShowcase.tsx`
- `SelectShowcase.tsx`
- `TextAreaShowcase.tsx`
- `SwitchShowcase.tsx`
- `ModalShowcase.tsx`
- `AlertShowcase.tsx`
- `TableShowcase.tsx`
- `TabsShowcase.tsx`
- `PaginationShowcase.tsx`
- `StatusBadgeShowcase.tsx`
- `ChipShowcase.tsx`
- `IconButtonShowcase.tsx`
- `LabelShowcase.tsx`
- `LoadingShowcase.tsx`
- `TooltipShowcase.tsx`
- `AccordionShowcase.tsx`
- `CardShowcase.tsx`
- `LivePreview.tsx` - 라이브 프리뷰
- `ControlRenderer.tsx` - 컨트롤 렌더러

#### Foundations Components (`components/foundations/`)
- `ColorPalette.tsx` - 컬러 팔레트
- `TypographyScale.tsx` - 타이포그래피 스케일
- `SpacingScale.tsx` - 스페이싱 스케일
- `ShadowScale.tsx` - 그림자 스케일
- `MotionPreview.tsx` - 모션 프리뷰

### 2.8 Contract Editor Components (`features/contract-editor/components/`)
- `SimulationPanel.tsx` - 시뮬레이션 패널
- `ImpactGraph.tsx` - 영향 그래프
- `ExportPanel.tsx` - 내보내기 패널
- `WhyPanel.tsx` - 이유 패널
- `MeaningPanel.tsx` - 의미 패널
- `ChangeSummary.tsx` - 변경 요약
- `ContractSummarySection.tsx` - 계약 요약 섹션
- `DependencySection.tsx` - 의존성 섹션
- `CauseItem.tsx` - 원인 아이템
- `graph/` - 그래프 관련 컴포넌트들
  - `ContractNode.tsx`
  - `DependencyEdge.tsx`
  - `GraphControlPanel.tsx`
  - `GraphLegend.tsx`
- `editor/` - 에디터 관련 컴포넌트들
  - `EditorHeader.tsx`
  - `EditorLoading.tsx`
  - `RightPanelTabs.tsx`

### 2.9 Dashboard Components (`features/dashboard/components/`)
- `DashboardContent.tsx` - 대시보드 콘텐츠
- `DashboardErrorState.tsx` - 대시보드 에러 상태

### 2.10 Playground Components (`features/playground/components/`)
- `Preview/PreviewPanel.tsx` - 프리뷰 패널
- `SampleInput/SampleInput.tsx` - 샘플 입력
- `EditorPanel.tsx` - 에디터 패널

### 2.11 Webhooks Components (`features/webhooks/components/`)
- `WebhookCard.tsx` - 웹훅 카드
- `WebhookStatsSection.tsx` - 웹훅 통계 섹션
- `DeliveriesTable.tsx` - 배송 테이블

### 2.12 Observability Components (`features/observability/components/`)
- `MetricsCards.tsx` - 메트릭 카드
- `StatusBanner.tsx` - 상태 배너
- `LatencySection.tsx` - 지연 시간 섹션
- `ThroughputSection.tsx` - 처리량 섹션
- `QueueStatusSection.tsx` - 큐 상태 섹션

---

## 3. Widgets - UI 폴더

### 3.1 Layout Widget (`widgets/layout/ui/`)
- **Layout.tsx** - 메인 레이아웃 컴포넌트 (사이드바 + 메인 콘텐츠)

---

## 4. 컴포넌트 분류 기준

### shared/ui에 속하는 컴포넌트
- **범용적이고 재사용 가능한** 기본 UI 컴포넌트
- 예: Button, Input, Modal, Table, Card 등
- **다른 feature에서도 사용 가능한** 컴포넌트

### features/*/components에 속하는 컴포넌트
- **특정 feature에 특화된** 컴포넌트
- 예: `SliceList`, `LineageGraph`, `TraceList` 등
- **해당 feature의 도메인 로직을 포함**하는 컴포넌트
- 다른 feature에서도 사용할 수 있지만, 주로 해당 feature 내에서 사용

### features/*/ui에 속하는 컴포넌트
- **페이지 레벨 컴포넌트**
- 라우팅과 직접 연결되는 메인 페이지 컴포넌트
- 예: `DataExplorer`, `Contracts`, `Dashboard` 등

### widgets/*/ui에 속하는 컴포넌트
- **레이아웃이나 복합 위젯** 레벨 컴포넌트
- 여러 feature를 아우르는 상위 레벨 컴포넌트
- 예: `Layout` (사이드바 + 메인 콘텐츠)

---

## 5. 컴포넌트 사용 패턴

### 다른 feature에서 사용되는 컴포넌트들

#### Explorer Components (다른 곳에서 사용 가능)
```typescript
// features/explorer/index.ts에서 export
import { 
  SearchBar, 
  JsonViewer, 
  DiffViewer,
  RawDataPanel,
  RawDataEditor,
  SliceList,
  ViewPreview,
  LineageGraph,
  DataTable
} from '@/features/explorer'
```

#### Design System Layout Components
```typescript
// features/design-system/index.ts에서 export
import { 
  Sidebar, 
  ContentArea, 
  Header, 
  PageHeader 
} from '@/features/design-system'
```

#### Contracts Components
```typescript
// features/contracts/index.ts에서 export
import { ContractDescription } from '@/features/contracts'
```

---

## 6. 정리 및 권장사항

### 현재 상태
- `shared/ui`: 범용 UI 컴포넌트 (Button, Input, Modal 등)
- `features/*/ui`: 페이지 레벨 컴포넌트
- `features/*/components`: Feature 특화 컴포넌트
- `widgets/*/ui`: 레이아웃/복합 위젯 컴포넌트

### 권장사항
1. **범용 컴포넌트는 `shared/ui`에**
   - 여러 feature에서 사용되는 기본 UI 컴포넌트
   
2. **Feature 특화 컴포넌트는 `features/*/components`에**
   - 해당 feature의 도메인 로직을 포함하는 컴포넌트
   - 다른 feature에서도 사용 가능하지만, 주로 해당 feature 내에서 사용
   
3. **페이지 컴포넌트는 `features/*/ui`에**
   - 라우팅과 직접 연결되는 메인 페이지 컴포넌트

4. **레이아웃/복합 위젯은 `widgets/*/ui`에**
   - 여러 feature를 아우르는 상위 레벨 컴포넌트

---

## 7. 참고사항

- 각 feature의 `index.ts` 파일에서 export되는 컴포넌트들은 다른 곳에서도 사용 가능
- 내부 컴포넌트들은 해당 feature 내에서만 사용
- Design System의 showcase 컴포넌트들은 문서화 목적이므로, 실제 사용 시에는 `shared/ui`의 컴포넌트를 사용
